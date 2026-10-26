package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PlagueVfx;

public final class PlaguePhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/plague_phantom");
    public static final int PANDEMIC_DURATION=240,PANDEMIC_RADIUS=20,PANDEMIC_COOLDOWN=1800;
    private static final EntityDataAccessor<Integer> VISUAL=SynchedEntityData.defineId(PlaguePhantom.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DIVING=SynchedEntityData.defineId(PlaguePhantom.class,EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS={200,240,400,600,PANDEMIC_COOLDOWN};
    private static final String[] CLIPS={"spit","dive","cloud","husk","pandemic","pulse","collapse","combat"};
    private record Cluster(long until,int size) {}
    private final int[] cooldowns=new int[5];
    private final Map<UUID,Cluster> clusters=new HashMap<>();
    private final PlagueOutbreak outbreak=new PlagueOutbreak(this);
    private UUID session=UUID.randomUUID(),orderedId,diveId,diveRider,ownerContext,cloudLineage;
    private Vec3 liftPoint,cloudCenter,pandemicCenter;
    private int mountedSpitTicks,meleeTicks,diveTicks,liftTicks,cloudTicks,huskTicks,pandemicTicks,pulseTicks,tameAttempts,clusterScans;
    private long clusterWindow=Long.MIN_VALUE;
    private boolean visualOpen;
    public PlaguePhantom(EntityType<? extends PlaguePhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,68).add(Attributes.ATTACK_DAMAGE,6).add(Attributes.ARMOR,8).add(Attributes.ARMOR_TOUGHNESS,2).add(Attributes.KNOCKBACK_RESISTANCE,.35D).add(Attributes.FOLLOW_RANGE,48).add(Attributes.MOVEMENT_SPEED,.25D).add(Attributes.FLYING_SPEED,.4D);}
    public static boolean checkSpawnRules(EntityType<PlaguePhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){long time=Math.floorMod(accessor.getLevel().getOverworldClockTime(),24000L);return accessor.getLevel().dimension()==Level.OVERWORLD&&time>=13000&&time<23000&&accessor.getBiome(position).is(ModTags.Biomes.PLAGUE_PHANTOM_SPAWNS)&&accessor.getFluidState(position).isEmpty()&&accessor.getBlockState(position).getCollisionShape(accessor,position).isEmpty();}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.PLAGUE_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 1;}
    @Override protected int getMeleeAttackCooldownTicks(){return 36;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override protected boolean prefersRangedCombat(){return false;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(VISUAL,0);data.define(DIVING,false);}
    @Override public boolean isClientAuthoritative(){return !entityData.get(DIVING)&&super.isClientAuthoritative();}
    @Override protected boolean isLocalClientAuthoritative(){return !entityData.get(DIVING)&&super.isLocalClientAuthoritative();}
    @Override public void travel(Vec3 input){if(entityData.get(DIVING)){setDeltaMovement(Vec3.ZERO);}else if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}}
    @Override public boolean isUltimateAnimationActive(){return visualState()==3||super.isUltimateAnimationActive();}
    public int visualState(){return entityData.get(VISUAL);}
    public UUID session(){return session;}
    public PlagueOutbreak outbreak(){return outbreak;}
    public UUID ownerKey(){return getOwnerReference()==null?getUUID():getOwnerReference().getUUID();}
    public int pandemicTicks(){return pandemicTicks;}
    public int cloudTicks(){return cloudTicks;}
    public boolean pandemicActive(){return pandemicTicks>0&&enabled();}
    public boolean inPandemic(LivingEntity target){return pandemicCenter!=null&&target.level()==level()&&target.position().distanceToSqr(pandemicCenter)<=PANDEMIC_RADIUS*PANDEMIC_RADIUS;}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner() instanceof ServerPlayer owner&&owner.isAlive()&&owner.level()==level()&&level().getServer()!=null&&level().getServer().getPlayerList().getPlayer(owner.getUUID())==owner);}
    public boolean recipient(LivingEntity target){return target!=null&&target.isAlive()&&target.level()==level()&&(target==this||isTame()&&(target==getOwner()||getTeam()!=null&&getTeam().isAlliedTo(target.getTeam())||target instanceof AbstractPhantom phantom&&phantom.isTame()&&Objects.equals(getOwnerReference(),phantom.getOwnerReference())));}
    public boolean ally(LivingEntity target){return target!=null&&(recipient(target)||target instanceof AbstractPhantom&&!PhantomDuels.pair(this,target)||isAlliedTo(target)&&!(target instanceof AbstractPhantom)||isTame()&&target instanceof OwnableEntity pet&&Objects.equals(getOwnerReference(),pet.getOwnerReference()));}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return attacker!=null&&victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    public boolean ownerThreat(LivingEntity target){return recent(target,getOwner())||target instanceof Mob mob&&recipient(mob.getTarget())&&!PhantomDuels.pair(target,mob.getTarget());}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||recent(target,this)||recent(target,getOwner())||PhantomDuels.pair(this,target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null||target instanceof Player||target instanceof AbstractPhantom){return explicit;}return explicit||target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    public boolean canInfect(LivingEntity target){return enabled()&&validTarget(target)&&target instanceof Mob&&target instanceof Enemy&&!(target instanceof OwnableEntity)&&!(target instanceof AbstractPhantom)&&!PhantomDuels.pair(this,target)&&distanceToSqr(target)<=48*48;}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return enabled()&&validTarget(target)&&distanceToSqr(target)<=24*24&&line(getEyePosition(),target.getEyePosition())&&(ownerThreat(target)||!(target instanceof Mob mob&&GravityControl.movementImmune(mob)));}
    public int cluster(LivingEntity target){if(!canInfect(target)||!(level() instanceof ServerLevel level)){return 0;}long now=level.getGameTime();Cluster cached=clusters.get(target.getUUID());if(cached!=null&&cached.until()>now){return cached.size();}if(clusterWindow!=now/20){clusterWindow=now/20;clusterScans=0;clusters.entrySet().removeIf(entry->entry.getValue().until()<=now);}if(clusterScans++>=8||clusters.size()>=16){return 0;}int size=(int)level.getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(3.5D),this::canInfect).stream().filter(other->other.distanceToSqr(target)<=12.25D&&line(target.getEyePosition(),other.getEyePosition())).limit(12).count();clusters.put(target.getUUID(),new Cluster(now+20,size));return size;}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return (ownerThreat(target)?-1000:0)-cluster(target)*12-(outbreak.infected(target)?12:0)-(outbreak.useful(target)?12:0)+distanceToSqr(target)*.1D;}
    @Override public void setCombatTarget(LivingEntity target){if(target!=null&&!recipient(target)&&!PhantomDuels.excludesObserver(this,target)&&PhantomDuels.begin(this,target)&&safeEnemy(target)){orderedId=target.getUUID();super.setCombatTarget(target);}}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.position().distanceToSqr(center)<=radius*radius&&line(center.add(0,.7D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();}
    public float damage(ServerLevel level,LivingEntity target,DamageSource source,float amount,boolean contagion){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float before=target.getHealth()+target.getAbsorptionAmount();if(!target.hurtServer(level,source,amount)){return 0;}float dealt=Math.max(0,before-target.getHealth()-target.getAbsorptionAmount());if(dealt>0&&contagion&&target.isAlive()){outbreak.infect(target);}return dealt;}
    public void infectionPulse(){if(pulseTicks==0){action("pulse");}pulseTicks=12;}
    public boolean spit(ServerLevel level,Vec3 aim,boolean mounted){if(!enabled()||(mounted?mountedSpitTicks>0||!(getControllingPassenger() instanceof Player rider)||!isOwnedBy(rider):cooldowns[0]>0)||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.001D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.6D));if(!line(getEyePosition(),muzzle)){return false;}PlagueSpit shot=PhantomismContent.PLAGUE_SPIT.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,1.8F,0);if(!level.addFreshEntity(shot)){return false;}if(mounted){mountedSpitTicks=6;}else{cooldowns[0]=INTERVALS[0];}action("spit");PlagueVfx.emit(this,PlagueVfx.SPIT,shot,muzzle,direction,32,.65F);return true;}
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return spit(level,rider.getViewVector(1),true);}
    public boolean safeTravel(Vec3 destination){Vec3 delta=destination.subtract(position());if(!Double.isFinite(delta.lengthSqr())||delta.lengthSqr()>64){return false;}for(Entity member:getSelfAndPassengers().limit(9).toList()){AABB swept=member.getBoundingBox().expandTowards(delta).inflate(.03D);if(member.isPassenger()){Entity parent=member.getVehicle();Vec3 seat=parent.getPassengerRidingPosition(member).subtract(member.getVehicleAttachmentPoint(parent));swept=swept.minmax(member.getBoundingBox().move(seat.subtract(member.position())).expandTowards(delta).inflate(.03D));}if(!isLoadedFlightBox(swept)||!level().getWorldBorder().isWithinBounds(swept)||!level().noBlockCollision(member,swept)||level().containsAnyLiquid(swept)){return false;}if(level() instanceof ServerLevel level){for(BlockPos point:BlockPos.betweenClosed(BlockPos.containing(swept.minX,swept.minY-.2D,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))){if(GravityControl.dangerous(level,point)){return false;}}}}return true;}
    public boolean activateRiddenDive(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),14,16).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.45D).findFirst().orElse(null);return target!=null&&dive(level,target);}
    public boolean dive(ServerLevel level,LivingEntity target){if(!enabled()||cooldowns[1]>0||diveTicks>0||!validTarget(target)||distanceToSqr(target)>14*14||!line(getEyePosition(),target.getEyePosition())){return false;}Vec3 lift=position().add(0,2,0);liftPoint=safeTravel(lift)?lift:position();liftTicks=liftPoint.equals(position())?0:5;diveTicks=26;diveId=target.getUUID();diveRider=getControllingPassenger()==null?null:getControllingPassenger().getUUID();cooldowns[1]=INTERVALS[1];entityData.set(DIVING,true);action("dive");syncRider(false);PlagueVfx.emit(this,PlagueVfx.DIVE,this,position(),target.position().subtract(position()).normalize(),26,1);return true;}
    private void tickDive(ServerLevel level){if(diveTicks<=0){return;}if(--diveTicks<=0||diveRider!=null&&(getControllingPassenger()==null||!diveRider.equals(getControllingPassenger().getUUID()))||!(level.getEntity(diveId) instanceof LivingEntity target)||!validTarget(target)||!line(getEyePosition(),target.getEyePosition())){endDive();return;}if(liftTicks>0){liftTicks--;Vec3 lift=liftPoint.subtract(position());if(lift.length()>.45D){lift=lift.normalize().scale(.45D);}if(safeTravel(position().add(lift))){move(MoverType.SELF,lift);setDeltaMovement(Vec3.ZERO);syncRider(false);return;}liftTicks=0;}if(getBoundingBox().inflate(.65D).intersects(target.getBoundingBox())){damage(level,target,damageSources().mobAttack(this),9,true);for(LivingEntity enemy:enemies(level,target.position(),3.5D,6)){if(enemy!=target){damage(level,enemy,damageSources().mobAttack(this),4,true);}}meleeTicks=36;PlagueVfx.emit(this,PlagueVfx.IMPACT,null,target.position(),Vec3.ZERO,24,4);endDive();return;}Vec3 step=target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.8D);if(!safeTravel(position().add(step))){endDive();return;}move(MoverType.SELF,step);setDeltaMovement(Vec3.ZERO);syncRider(false);}
    private void syncRider(boolean finish){if(!(level() instanceof ServerLevel)||!(getControllingPassenger() instanceof ServerPlayer rider)||rider.connection==null){return;}positionRider(rider);needsSync=true;rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(),List.of(SynchedEntityData.DataValue.create(DIVING,entityData.get(DIVING)))));if(finish){rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this));}}
    private void endDive(){boolean active=entityData.get(DIVING);diveTicks=0;liftTicks=0;liftPoint=null;diveId=null;diveRider=null;entityData.set(DIVING,false);if(active){setDeltaMovement(Vec3.ZERO);syncRider(true);}}
    public boolean cloud(ServerLevel level,Vec3 center){if(!enabled()||cooldowns[2]>0||cloudTicks>0||!Double.isFinite(center.lengthSqr())||center.distanceToSqr(position())>18*18||!line(getEyePosition(),center.add(0,.7D,0))){return false;}var targets=enemies(level,center,5,8);if(targets.stream().noneMatch(outbreak::useful)||!outbreak.ensure()){return false;}cloudCenter=center;cloudLineage=outbreak.id();cloudTicks=160;cooldowns[2]=INTERVALS[2];action("cloud");PlagueVfx.emit(this,PlagueVfx.CLOUD,null,center,Vec3.ZERO,160,5);PlagueVfx.emit(this,PlagueVfx.HERD,this,position(),Vec3.ZERO,18,2);return true;}
    private void tickCloud(ServerLevel level){if(cloudTicks<=0){return;}cloudTicks--;if(cloudCenter==null||cloudCenter.distanceToSqr(position())>32*32||!outbreak.live()||!Objects.equals(cloudLineage,outbreak.id())){cloudTicks=0;cloudCenter=null;return;}if(cloudTicks%20==0){for(LivingEntity target:enemies(level,cloudCenter,5,8)){outbreak.infect(target);}}if(cloudTicks==0){cloudCenter=null;cloudLineage=null;}}
    public boolean husk(ServerLevel level,LivingEntity attacker,float incoming){if(!enabled()||cooldowns[3]>0||!validTarget(attacker)||!Float.isFinite(incoming)||incoming<4&&getHealth()-incoming>getMaxHealth()*.35F){return false;}cooldowns[3]=INTERVALS[3];huskTicks=60;outbreak.infect(attacker);action("husk");PlagueVfx.emit(this,PlagueVfx.HUSK,this,position(),Vec3.ZERO,60,2);return true;}
    public float huskMitigation(){return enabled()&&huskTicks>0?.35F:0;}
    public boolean pandemic(ServerLevel level){if(!enabled()||cooldowns[4]>0||pandemicTicks>0){return false;}var targets=enemies(level,position(),20,12);if(targets.stream().filter(this::canInfect).count()<3||targets.stream().noneMatch(target->outbreak.infected(target)||outbreak.useful(target))||!outbreak.ensure()){return false;}pandemicTicks=PANDEMIC_DURATION;pandemicCenter=position();cooldowns[4]=PANDEMIC_COOLDOWN;for(LivingEntity target:targets.stream().filter(outbreak::useful).limit(3).toList()){outbreak.infect(target);}action("pandemic");entityData.set(VISUAL,3);PlagueVfx.emit(this,PlagueVfx.PANDEMIC,null,pandemicCenter,Vec3.ZERO,PANDEMIC_DURATION,PANDEMIC_RADIUS);return true;}
private void tickPandemic(ServerLevel level){if(pandemicTicks<=0){return;}int elapsed=PANDEMIC_DURATION- --pandemicTicks;if(elapsed%40==0&&elapsed<PANDEMIC_DURATION){for(LivingEntity target:enemies(level,pandemicCenter,20,12)){if(outbreak.infected(target)){outbreak.infect(target);}}action("pulse");PlagueVfx.emit(this,PlagueVfx.SURGE,null,pandemicCenter,Vec3.ZERO,24,20);}if(pandemicTicks==0){for(LivingEntity target:enemies(level,pandemicCenter,20,12)){if(outbreak.infected(target)){damage(level,target,damageSources().mobAttack(this),6,false);}}action("collapse");PlagueVfx.emit(this,PlagueVfx.COLLAPSE,null,pandemicCenter,Vec3.ZERO,30,20);pandemicCenter=null;}}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE),true)<=0){return false;}meleeTicks=36;action("combat");return true;}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)||diveTicks>0){return true;}if(doHurtTarget(level,target)){return true;}if(line(getEyePosition(),target.getEyePosition())&&distanceToSqr(target)<=39*39&&spit(level,target.getEyePosition().subtract(getEyePosition()),false)){return true;}if(getHealth()>getMaxHealth()*.5F&&distanceToSqr(target)>9&&cluster(target)<=2&&dive(level,target)){return true;}return false;}
    @Override protected boolean prepareCombatApproach(ServerLevel level,LivingEntity target){if(!validTarget(target)){return true;}Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.1D){away=new Vec3(0,0,1);}double spacing=getHealth()<getMaxHealth()*.35F?9:6;flyToAssignment(target.position().add(away.scale(spacing)).add(0,3,0),1);return true;}
    @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(mountedSpitTicks>0){mountedSpitTicks--;}if(meleeTicks>0){meleeTicks--;}if(huskTicks>0){huskTicks--;}if(pulseTicks>0){pulseTicks--;}UUID owner=ownerKey();if(ownerContext!=null&&!ownerContext.equals(owner)){endPlague();}ownerContext=owner;outbreak.tick();if(!enabled()){endPlague();return;}tickDive(level);tickCloud(level);tickPandemic(level);entityData.set(VISUAL,pandemicTicks>0?3:pulseTicks>0?2:validTarget(getTarget())?1:0);if(!isNoAi()&&tickCount%20==0){LivingEntity target=getTarget();var choices=enemies(level,position(),24,12);LivingEntity threat=choices.stream().filter(this::ownerThreat).findFirst().orElse(null);if(threat!=null&&(orderedId==null||!(level.getEntity(orderedId) instanceof LivingEntity ordered)||!validTarget(ordered))){target=threat;setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);}if(!validTarget(target)&&!choices.isEmpty()){target=choices.getFirst();setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);}if(validTarget(target)&&!isVehicle()){if(cluster(target)>=3){if(!pandemic(level)){cloud(level,target.position());}}if(diveTicks==0&&distanceToSqr(target)<=39*39&&line(getEyePosition(),target.getEyePosition())){spit(level,target.getEyePosition().subtract(getEyePosition()),false);}}}}
    private void action(String clip){visualOpen=true;triggerAnim("plague",clip);if(!clip.equals("pulse")){playSound(SoundEvents.SLIME_ATTACK,.35F,.7F);}}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<PlaguePhantom>("plague",2,state->PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.plague."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(!isTame()&&isAlive()&&!player.isSpectator()&&player.getItemInHand(hand).is(preferredTaming().item())){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}if(++tameAttempts>=preferredTaming().guaranteedAttempt()||getRandom().nextFloat()<preferredTaming().chance()){tame(player);clearCombatTarget(true);setOrder(Order.FOLLOW);level().broadcastEntityEvent(this,(byte)7);}else{level().broadcastEntityEvent(this,(byte)6);}}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    public void endPlague(){if(level() instanceof ServerLevel){outbreak.suspend();if(visualOpen){PlagueVfx.cancel(this);session=UUID.randomUUID();visualOpen=false;}}endDive();cloudTicks=0;huskTicks=0;pandemicTicks=0;cloudCenter=null;cloudLineage=null;pandemicCenter=null;orderedId=null;pulseTicks=0;clusters.clear();entityData.set(VISUAL,0);}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&outbreak!=null){endPlague();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(outbreak!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endPlague();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(outbreak!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endPlague();}}
    @Override public void remove(RemovalReason reason){if(outbreak!=null){endPlague();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);for(int index=0;index<cooldowns.length;index++){output.putInt("PlagueCooldown"+index,cooldowns[index]);}output.putInt("PlagueMountedSpit",mountedSpitTicks);output.putInt("PlagueMelee",meleeTicks);output.putInt("PlagueTameAttempts",tameAttempts);outbreak.save(output);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endPlague();session=UUID.randomUUID();for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("PlagueCooldown"+index,0),0,INTERVALS[index]);}mountedSpitTicks=Math.clamp(input.getIntOr("PlagueMountedSpit",0),0,6);meleeTicks=Math.clamp(input.getIntOr("PlagueMelee",0),0,36);tameAttempts=Math.clamp(input.getIntOr("PlagueTameAttempts",0),0,2);outbreak.load(input);}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){List<CooldownDiscipline.Slot> slots=new ArrayList<>();for(int index=0;index<3;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("plague_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.FERMENTED_SPIDER_EYE, .75F, 2, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Spit: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Dive: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Cloud: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Husk: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Pandemic: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "spit" -> cooldowns[0];
            case "dive" -> cooldowns[1];
            case "cloud" -> cooldowns[2];
            case "pandemic" -> cooldowns[4];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
