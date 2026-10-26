package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
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
import net.ronm19.phantomism.vfx.GaleVfx;

public final class GalePhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/gale_phantom");
    public static final int DOMAIN_DURATION=240,DOMAIN_RADIUS=24,DOMAIN_COOLDOWN=1800;
    private static final Identifier SLIP=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"gale_slipstream");
    private static final EntityDataAccessor<Integer> VISUAL=SynchedEntityData.defineId(GalePhantom.class,EntityDataSerializers.INT),VARIANT=SynchedEntityData.defineId(GalePhantom.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUSHING=SynchedEntityData.defineId(GalePhantom.class,EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS={200,200,240,280,160,220,400,40,DOMAIN_COOLDOWN};
    private static final String[] CLIPS={"bolt","slipstream","updraft","crosswind","rush","blade","vortex","rescue","dominion","tailwind","tempest","judgment","combat","fast","hover"};
    private final int[] cooldowns=new int[9];
    private final Map<UUID,Integer> domainVictims=new LinkedHashMap<>();
    private final Set<UUID> vortexVictims=new LinkedHashSet<>();
    private UUID session=UUID.randomUUID(),orderedId,ownerContext,rushTarget;
    private Vec3 domainCenter,vortexCenter,rushDirection;
    private int mountedBoltTicks,meleeTicks,slipTicks,crosswindTicks,rushTicks,vortexTicks,domainTicks,finalPulseTicks,tameAttempts,threatTicks,deflectionWindow,deflections,domainDeflections;
    private boolean visualOpen,rushMounted;

    public GalePhantom(EntityType<? extends GalePhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,56).add(Attributes.ATTACK_DAMAGE,7).add(Attributes.ARMOR,6).add(Attributes.ARMOR_TOUGHNESS,2).add(Attributes.KNOCKBACK_RESISTANCE,.4D).add(Attributes.FOLLOW_RANGE,48).add(Attributes.MOVEMENT_SPEED,.3D).add(Attributes.FLYING_SPEED,.6D);}
    public static boolean checkSpawnRules(EntityType<GalePhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){AABB box=type.getSpawnAABB(position.getX()+.5D,position.getY(),position.getZ()+.5D).inflate(.05D);return accessor.getLevel().dimension()==Level.OVERWORLD&&box.minY>=accessor.getMinY()+1&&box.maxY<accessor.getMaxY()&&accessor.getBiome(position).is(ModTags.Biomes.GALE_PHANTOM_SPAWNS)&&accessor.hasChunksAt(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY+1,box.maxZ))&&!accessor.containsAnyLiquid(box)&&accessor.noBlockCollision(null,box);}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.GALE_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 2;}
    @Override protected int getMeleeAttackCooldownTicks(){return 30;}
    @Override protected int getFlightTurnRate(){return 32;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(VISUAL,0);data.define(VARIANT,0);data.define(RUSHING,false);}
    @Override public void travel(Vec3 input){if(entityData.get(RUSHING)&&getControllingPassenger()==null){setDeltaMovement(Vec3.ZERO);return;}if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.12D));Vec3 movement=getDeltaMovement();double limit=.6D*Math.min(1.5D,getAttributeValue(Attributes.FLYING_SPEED)/.6D);if(movement.lengthSqr()>limit*limit){setDeltaMovement(movement.normalize().scale(limit));}}else{super.travel(input);}}
    @Override public boolean isUltimateAnimationActive(){return visualState()==3||super.isUltimateAnimationActive();}
    public int visualState(){return entityData.get(VISUAL);}
    public int variant(){return entityData.get(VARIANT);}
    public void setVariant(int variant){entityData.set(VARIANT,Math.clamp(variant,0,2));}
    public UUID session(){return session;}
    public void visualStarted(){visualOpen=true;}
    public int domainTicks(){return domainTicks;}
    public int crosswindTicks(){return crosswindTicks;}
    public int vortexTicks(){return vortexTicks;}
    public Vec3 domainCenter(){return domainCenter;}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner() instanceof ServerPlayer owner&&owner.isAlive()&&owner.level()==level()&&level().getServer()!=null&&level().getServer().getPlayerList().getPlayer(owner.getUUID())==owner);}
    public boolean recipient(LivingEntity target){return target!=null&&target.isAlive()&&target.level()==level()&&(target==this||isTame()&&(target==getOwner()||getTeam()!=null&&getTeam().isAlliedTo(target.getTeam())||target instanceof OwnableEntity pet&&Objects.equals(getOwnerReference(),pet.getOwnerReference())));}
    public boolean ally(LivingEntity target){return target!=null&&(recipient(target)||target instanceof AbstractPhantom&&!PhantomDuels.pair(this,target)||isAlliedTo(target)&&!(target instanceof AbstractPhantom));}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return attacker!=null&&victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    public boolean ownerThreat(LivingEntity target){return recent(target,getOwner())||target instanceof Mob mob&&recipient(mob.getTarget())&&!PhantomDuels.pair(target,mob.getTarget());}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||recent(target,this)||recent(target,getOwner())||ownerThreat(target)||PhantomDuels.pair(this,target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null||target instanceof Player||target instanceof AbstractPhantom){return explicit;}return explicit||target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return enabled()&&validTarget(target)&&distanceToSqr(target)<=32*32&&line(getEyePosition(),target.getEyePosition())&&(isTame()||recent(target,this));}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return(ownerThreat(target)?-1000:0)+(target.isNoGravity()||!target.onGround()?-50:0)+distanceToSqr(target)*.2D;}
    @Override public void setCombatTarget(LivingEntity target){if(target!=null&&!recipient(target)&&!PhantomDuels.excludesObserver(this,target)&&PhantomDuels.begin(this,target)&&safeEnemy(target)){orderedId=target.getUUID();super.setCombatTarget(target);}}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.position().distanceToSqr(center)<=radius*radius&&line(center.add(0,.7D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();}
    public float damage(ServerLevel level,LivingEntity target,DamageSource damage,float amount){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float before=target.getHealth()+target.getAbsorptionAmount();return target.hurtServer(level,damage,amount)?Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()):0;}
    public boolean bolt(ServerLevel level,Vec3 aim,boolean mounted){
        if(!enabled()||(mounted?mountedBoltTicks>0||!(getControllingPassenger() instanceof Player rider)||!isOwnedBy(rider):cooldowns[0]>0)||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.001D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.6D));if(!line(getEyePosition(),muzzle)){return false;}WindBolt shot=PhantomismContent.WIND_BOLT.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,1.8F,0);if(!level.addFreshEntity(shot)){return false;}if(mounted){mountedBoltTicks=6;}else{cooldowns[0]=200;}action("bolt");GaleVfx.emit(this,GaleVfx.BOLT,shot,muzzle,direction,32,1);return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return bolt(level,rider.getViewVector(1),true);}
    public boolean slipstream(ServerLevel level){if(!enabled()||cooldowns[1]>0||slipTicks>0){return false;}slipTicks=40;cooldowns[1]=200;var flight=getAttribute(Attributes.FLYING_SPEED);flight.removeModifier(SLIP);flight.addTransientModifier(new AttributeModifier(SLIP,.2D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));action("slipstream");GaleVfx.emit(this,GaleVfx.SLIPSTREAM,this,position(),getViewVector(1),40,3);return true;}
    public boolean rescue(ServerLevel level,LivingEntity ward){if(!enabled()||cooldowns[7]>0||!GaleWind.rescue(this,ward)){return false;}cooldowns[7]=40;if(!isVehicle()){flyToAssignment(ward.position().add(0,2,0),1.5D);}action("rescue");GaleVfx.emit(this,GaleVfx.RESCUE,ward,ward.position(),Vec3.ZERO,35,3);return true;}
    public boolean updraft(ServerLevel level){
        if(!enabled()||cooldowns[2]>0){return false;}var enemies=enemies(level,position(),8,8);var wards=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(8),this::recipient).stream().filter(ward->line(getEyePosition(),ward.getEyePosition())).limit(8).toList();boolean used=false;
        for(LivingEntity target:enemies){used|=GaleWind.push(this,target,new Vec3(0,.28D,0));GaleWind.slow(this,target,30);}
        for(LivingEntity ward:wards){used|=GaleWind.assist(this,ward);}
        if(!used){return false;}cooldowns[2]=240;action("updraft");GaleVfx.emit(this,GaleVfx.UPDRAFT,null,position(),Vec3.ZERO,45,8);return true;
    }
    public boolean crosswind(ServerLevel level){
        if(!enabled()||cooldowns[3]>0||threatTicks==0&&enemies(level,position(),12,12).isEmpty()){return false;}cooldowns[3]=280;crosswindTicks=40;Vec3 ward=getOwner()!=null?getOwner().position():position();for(LivingEntity target:enemies(level,position(),12,12)){Vec3 away=target.position().subtract(ward).multiply(1,0,1).normalize();GaleWind.push(this,target,away.scale(.32D));GaleWind.slow(this,target,30);}action("crosswind");GaleVfx.emit(this,GaleVfx.CROSSWIND,this,position(),getViewVector(1),40,12);GaleWind.deflect(this,level);return true;
    }
    public void incomingThreat(LivingEntity attacker,LivingEntity ward,float amount){if(!enabled()||!recipient(ward)||attacker==null||!safeEnemy(attacker)||PhantomDuels.pair(attacker,ward)){return;}threatTicks=80;if(amount>=5||ward.getHealth()<ward.getMaxHealth()*.35F){crosswind((ServerLevel)level());}if(validTarget(attacker)&&line(getEyePosition(),attacker.getEyePosition())&&(getTarget()==null||ownerThreat(attacker))){setTarget(attacker);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,attacker);}}
    public float mitigation(LivingEntity ward){if(!enabled()||!recipient(ward)||!line(getEyePosition(),ward.getEyePosition())){return 0;}return crosswindTicks>0&&distanceToSqr(ward)<=144||domainCenter!=null&&(domainTicks>0||finalPulseTicks>0)&&ward.position().distanceToSqr(domainCenter)<=576?.2F:0;}
    public boolean canDeflect(){return deflections<4&&(domainTicks==0||domainDeflections<32);}
    public void deflected(){deflections++;if(domainTicks>0){domainDeflections++;}visualOpen=true;}
    public boolean rush(ServerLevel level,LivingEntity target,boolean mounted){
        if(!enabled()||cooldowns[4]>0||rushTicks>0||!validTarget(target)||distanceToSqr(target)>144||!line(getEyePosition(),target.getEyePosition())||mounted&&(!(getControllingPassenger() instanceof Player rider)||!isOwnedBy(rider))){return false;}
        rushDirection=(mounted?((Player)getControllingPassenger()).getViewVector(1):target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter())).normalize();if(rushDirection.lengthSqr()<.01D){return false;}rushTarget=target.getUUID();rushMounted=mounted;rushTicks=14;cooldowns[4]=160;entityData.set(RUSHING,true);if(mounted){slipstream(level);}else{setDeltaMovement(Vec3.ZERO);}action("rush");GaleVfx.emit(this,GaleVfx.RUSH,this,position(),rushDirection,14,2);return true;
    }
    private void stopRush(){boolean forced=!rushMounted;rushTicks=0;rushTarget=null;rushDirection=null;rushMounted=false;entityData.set(RUSHING,false);if(forced){setDeltaMovement(Vec3.ZERO);}}
    private void tickRush(ServerLevel level){
        if(rushTicks<=0){return;}LivingEntity target=level.getEntity(rushTarget) instanceof LivingEntity living?living:null;if(!validTarget(target)||--rushTicks<=0||rushMounted&&!(getControllingPassenger() instanceof Player rider&&isOwnedBy(rider))){stopRush();return;}
        if(!rushMounted){Vec3 step=rushDirection.scale(.85D);AABB sweep=getBoundingBox().expandTowards(step);if(!isLoadedFlightBox(sweep)||!level.noBlockCollision(this,sweep)||level.containsAnyLiquid(sweep)){stopRush();return;}move(MoverType.SELF,step);needsSync=true;}
        if(getBoundingBox().inflate(1).intersects(target.getBoundingBox())&&line(getEyePosition(),target.getEyePosition())){if(damage(level,target,damageSources().mobAttack(this),12)>0){GaleWind.push(this,target,target.position().subtract(position()).normalize().scale(.3D));}GaleVfx.emit(this,GaleVfx.IMPACT,null,target.position(),rushDirection,22,3);stopRush();}
    }
    public boolean activateRiddenRush(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),12,16).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.45D).findFirst().orElse(null);return target!=null&&rush(level,target,true);}
    public boolean blade(ServerLevel level,LivingEntity primary){if(!enabled()||cooldowns[5]>0||!validTarget(primary)||distanceToSqr(primary)>14*14||!line(getEyePosition(),primary.getEyePosition())){return false;}Vec3 aim=primary.getEyePosition().subtract(getEyePosition()).normalize();var targets=enemies(level,position(),14,16).stream().filter(target->aim.dot(target.getEyePosition().subtract(getEyePosition()).normalize())>.82D).limit(4).toList();if(targets.isEmpty()){return false;}cooldowns[5]=220;for(LivingEntity target:targets){damage(level,target,damageSources().mobAttack(this),9);}action("blade");GaleVfx.emit(this,GaleVfx.BLADE,null,getEyePosition(),aim,24,8);return true;}
    public boolean vortex(ServerLevel level,LivingEntity primary){if(!enabled()||cooldowns[6]>0||vortexTicks>0||!validTarget(primary)||distanceToSqr(primary)>16*16||!line(getEyePosition(),primary.getEyePosition())){return false;}vortexTicks=80;vortexCenter=primary.position();vortexVictims.clear();cooldowns[6]=400;action("vortex");GaleVfx.emit(this,GaleVfx.VORTEX,null,vortexCenter,Vec3.ZERO,80,8);return true;}
    private void tickVortex(ServerLevel level){if(vortexTicks<=0){return;}if(--vortexTicks%10==0){for(LivingEntity target:enemies(level,vortexCenter,8,12)){if(!vortexVictims.contains(target.getUUID())&&vortexVictims.size()>=8){continue;}vortexVictims.add(target.getUUID());Vec3 toward=vortexCenter.subtract(target.position()).multiply(1,0,1);if(toward.lengthSqr()>2.25D){GaleWind.push(this,target,toward.normalize().scale(.2D));}GaleWind.slow(this,target,25);}}if(vortexTicks==0){vortexCenter=null;vortexVictims.clear();}}
    public boolean dominion(ServerLevel level){if(!enabled()||cooldowns[8]>0||domainTicks>0||enemies(level,position(),24,12).size()<3||!level.getEntitiesOfClass(GalePhantom.class,getBoundingBox().inflate(48),other->other!=this&&other.enabled()&&other.domainTicks()>0&&(recipient(other)||other.recipient(this))).isEmpty()){return false;}domainTicks=240;domainCenter=position();domainVictims.clear();domainDeflections=0;cooldowns[8]=1800;action("dominion");entityData.set(VISUAL,3);GaleWind.aura(this,level);for(LivingEntity target:enemies(level,domainCenter,24,12)){GaleWind.push(this,target,new Vec3(0,.2D,0));}GaleVfx.emit(this,GaleVfx.DOMINION,null,domainCenter,Vec3.ZERO,240,24);return true;}
    private void tickDomain(ServerLevel level){
        if(domainTicks<=0){return;}if(domainCenter==null||position().distanceToSqr(domainCenter)>48*48){endGale();return;}int elapsed=240- --domainTicks;
        if(elapsed>=40&&elapsed<=200&&elapsed%40==0){for(LivingEntity target:enemies(level,domainCenter,24,16)){if(!domainVictims.containsKey(target.getUUID())&&domainVictims.size()>=12){continue;}int hits=domainVictims.getOrDefault(target.getUUID(),0);if(hits>=4){continue;}if(damage(level,target,damageSources().mobAttack(this),2)>0){domainVictims.put(target.getUUID(),hits+1);GaleWind.slow(this,target,45);GaleWind.push(this,target,new Vec3(0,.16D,0));}}action("tempest");GaleVfx.emit(this,GaleVfx.TEMPEST,null,domainCenter,Vec3.ZERO,28,24);}
        if(domainTicks==0){for(LivingEntity target:enemies(level,domainCenter,24,16)){if(!domainVictims.containsKey(target.getUUID())&&domainVictims.size()>=12){continue;}if(damage(level,target,damageSources().mobAttack(this),16)>0){domainVictims.putIfAbsent(target.getUUID(),0);GaleWind.push(this,target,target.position().subtract(domainCenter).normalize().scale(.35D));}}GaleWind.releaseControl(this);finalPulseTicks=20;action("judgment");GaleVfx.emit(this,GaleVfx.JUDGMENT,null,domainCenter,Vec3.ZERO,35,24);domainVictims.clear();}
    }
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.7D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))<=0){return false;}meleeTicks=30;action("combat");return true;}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}if(doHurtTarget(level,target)||blade(level,target)){return true;}if(!isVehicle()&&distanceToSqr(target)<=144&&rush(level,target,false)){return true;}return distanceToSqr(target)<=42*42&&line(getEyePosition(),target.getEyePosition())&&bolt(level,target.getEyePosition().subtract(getEyePosition()),false);}
    @Override protected boolean prepareCombatApproach(ServerLevel level,LivingEntity target){if(rushTicks>0||!validTarget(target)){return true;}double angle=tickCount*.04D;Vec3 point=target.position().add(Math.cos(angle)*8,3+Math.sin(angle)*1.5D,Math.sin(angle)*8);flyToAssignment(point,1.35D);return true;}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(mountedBoltTicks>0){mountedBoltTicks--;}if(meleeTicks>0){meleeTicks--;}if(crosswindTicks>0){crosswindTicks--;}if(threatTicks>0){threatTicks--;}if(slipTicks>0&&--slipTicks==0){getAttribute(Attributes.FLYING_SPEED).removeModifier(SLIP);}if(finalPulseTicks>0&&--finalPulseTicks==0&&domainTicks==0){domainCenter=null;}if(++deflectionWindow>=10){deflectionWindow=0;deflections=0;}
        UUID owner=getOwnerReference()==null?getUUID():getOwnerReference().getUUID();if(ownerContext!=null&&!ownerContext.equals(owner)){endGale();}ownerContext=owner;if(!enabled()){endGale();return;}
        tickRush(level);tickVortex(level);tickDomain(level);int visual=domainTicks>0?3:validTarget(getTarget())?2:slipTicks>0||getDeltaMovement().lengthSqr()>.16D?1:0;if(visual!=visualState()&&visual<2&&slipTicks==0){action(visual==1?"fast":"hover");}entityData.set(VISUAL,visual);
        if(tickCount%10==0){GaleWind.maintain();GaleWind.aura(this,level);var wards=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(24),this::recipient).stream().sorted(Comparator.comparingDouble(target->target==getOwner()?-1000:distanceToSqr(target))).limit(16).toList();for(LivingEntity ward:wards){if(rescue(level,ward)){break;}}}
        if(tickCount%5==0){GaleWind.deflect(this,level);}
        if(!isNoAi()&&tickCount%20==0){var choices=enemies(level,position(),32,16);LivingEntity target=getTarget(),threat=choices.stream().filter(this::ownerThreat).findFirst().orElse(null);if(threat!=null&&(orderedId==null||!(level.getEntity(orderedId) instanceof LivingEntity ordered)||!validTarget(ordered))){target=threat;setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);}if(validTarget(target)){if(choices.size()>=3){dominion(level);vortex(level,target);updraft(level);}if(distanceToSqr(target)>16*16){slipstream(level);}if(distanceToSqr(target)<5*5&&threatTicks>0){crosswind(level);}if(!isVehicle()){prepareSpeciesCombatAction(level,target);}}else if(getOwner()!=null&&distanceToSqr(getOwner())>24*24){slipstream(level);}}
    }
    private void action(String clip){visualOpen=true;triggerAnim("gale",clip);if(!clip.equals("tempest")&&!clip.equals("tailwind")&&!clip.equals("fast")&&!clip.equals("hover")){playSound(SoundEvents.BREEZE_WHIRL,.45F,1.2F);}}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<GalePhantom>("gale",2,state->PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.gale."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(!isTame()&&isAlive()&&!player.isSpectator()&&player.getItemInHand(hand).is(preferredTaming().item())){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}if(++tameAttempts>=preferredTaming().guaranteedAttempt()||getRandom().nextFloat()<preferredTaming().chance()){tame(player);clearCombatTarget(true);setOrder(Order.FOLLOW);level().broadcastEntityEvent(this,(byte)7);}else{level().broadcastEntityEvent(this,(byte)6);}}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData group){SpawnGroupData result=super.finalizeSpawn(accessor,difficulty,reason,group);setVariant(getRandom().nextInt(3));return result;}
    @Override public AgeableMob getBreedOffspring(ServerLevel level,AgeableMob partner){AgeableMob child=super.getBreedOffspring(level,partner);if(child instanceof GalePhantom gale){gale.setVariant(variant());}return child;}
    public void endGale(){if(level() instanceof ServerLevel){GaleWind.release(this);if(visualOpen){GaleVfx.cancel(this);session=UUID.randomUUID();visualOpen=false;}}if(getAttribute(Attributes.FLYING_SPEED)!=null){getAttribute(Attributes.FLYING_SPEED).removeModifier(SLIP);}slipTicks=0;crosswindTicks=0;vortexTicks=0;vortexCenter=null;vortexVictims.clear();domainTicks=0;finalPulseTicks=0;domainCenter=null;domainVictims.clear();orderedId=null;threatTicks=0;stopRush();entityData.set(VISUAL,0);}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endGale();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endGale();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endGale();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endGale();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);for(int index=0;index<cooldowns.length;index++){output.putInt("GaleCooldown"+index,cooldowns[index]);}output.putInt("GaleMountedBolt",mountedBoltTicks);output.putInt("GaleMelee",meleeTicks);output.putInt("GaleVariant",variant());output.putInt("GaleTamingAttempts",tameAttempts);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endGale();session=UUID.randomUUID();for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("GaleCooldown"+index,0),0,INTERVALS[index]);}mountedBoltTicks=Math.clamp(input.getIntOr("GaleMountedBolt",0),0,6);meleeTicks=Math.clamp(input.getIntOr("GaleMelee",0),0,30);setVariant(input.getIntOr("GaleVariant",0));tameAttempts=Math.clamp(input.getIntOr("GaleTamingAttempts",0),0,2);}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){List<CooldownDiscipline.Slot> slots=new ArrayList<>();for(int index=0;index<7;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("gale_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.WIND_CHARGE, .75F, 2, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Bolt: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Slipstream: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Updraft: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Crosswind: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Rush: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Blade: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Vortex: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Rescue: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]), "Dominion: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[8]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "bolt" -> cooldowns[0];
            case "slipstream" -> cooldowns[1];
            case "updraft" -> cooldowns[2];
            case "crosswind" -> cooldowns[3];
            case "vortex" -> cooldowns[6];
            case "dominion" -> cooldowns[8];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
