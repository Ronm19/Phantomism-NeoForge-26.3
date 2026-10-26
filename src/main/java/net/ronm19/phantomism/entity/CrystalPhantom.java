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
import net.ronm19.phantomism.vfx.CrystalVfx;

public final class CrystalPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/crystal_phantom");
    public static final int DOMAIN_DURATION=240,DOMAIN_RADIUS=18,DOMAIN_COOLDOWN=1800;
    private static final EntityDataAccessor<Integer> VISUAL=SynchedEntityData.defineId(CrystalPhantom.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT=SynchedEntityData.defineId(CrystalPhantom.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> IMPACT=SynchedEntityData.defineId(CrystalPhantom.class,EntityDataSerializers.INT);
    private static final int[] INTERVALS={200,280,360,320,600,DOMAIN_COOLDOWN};
    private static final String[] CLIPS={"shard","burst","counter","barrier","shell","dominion","pulse","shatter","combat","recover"};
    private final int[] cooldowns=new int[6];
    private final Map<UUID,Integer> domainVictims=new LinkedHashMap<>();
    private UUID session=UUID.randomUUID(),orderedId,ownerContext;
    private Vec3 domainCenter;
    private int mountedShardTicks,meleeTicks,barrierTicks,shellTicks,counterTicks,domainTicks,domainPower,recoveryTicks,healTicks=100,impactRecovery,impactIdle,reflectionWindow,reflections,domainReflections;
    private boolean visualOpen;

    public CrystalPhantom(EntityType<? extends CrystalPhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.ATTACK_DAMAGE,8).add(Attributes.ARMOR,22).add(Attributes.ARMOR_TOUGHNESS,8).add(Attributes.KNOCKBACK_RESISTANCE,.75D).add(Attributes.FOLLOW_RANGE,48).add(Attributes.MOVEMENT_SPEED,.25D).add(Attributes.FLYING_SPEED,.35D);}
    public static boolean checkSpawnRules(EntityType<CrystalPhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){return accessor.getLevel().dimension()==Level.OVERWORLD&&accessor.getBiome(position).is(ModTags.Biomes.CRYSTAL_PHANTOM_SPAWNS)&&accessor.getFluidState(position).isEmpty()&&accessor.getBlockState(position).getCollisionShape(accessor,position).isEmpty();}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.CRYSTAL_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 1;}
    @Override protected int getMeleeAttackCooldownTicks(){return 40;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override protected boolean prefersRangedCombat(){return false;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(VISUAL,0);data.define(VARIANT,0);data.define(IMPACT,0);}
    @Override public void travel(Vec3 input){if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}}
    @Override public boolean isUltimateAnimationActive(){return visualState()==3||super.isUltimateAnimationActive();}
    public int visualState(){return entityData.get(VISUAL);}
    public int variant(){return entityData.get(VARIANT);}
    public void setVariant(int variant){entityData.set(VARIANT,Math.clamp(variant,0,2));}
    public int impact(){return entityData.get(IMPACT);}
    public UUID session(){return session;}
    public int domainTicks(){return domainTicks;}
    public int barrierTicks(){return barrierTicks;}
    public int shellTicks(){return shellTicks;}
    public Vec3 domainCenter(){return domainCenter;}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner() instanceof ServerPlayer owner&&owner.isAlive()&&owner.level()==level()&&level().getServer()!=null&&level().getServer().getPlayerList().getPlayer(owner.getUUID())==owner);}
    public boolean recipient(LivingEntity target){return target!=null&&target.isAlive()&&target.level()==level()&&(target==this||isTame()&&(target==getOwner()||getTeam()!=null&&getTeam().isAlliedTo(target.getTeam())||target instanceof OwnableEntity pet&&Objects.equals(getOwnerReference(),pet.getOwnerReference())));}
    public boolean ally(LivingEntity target){return target!=null&&(recipient(target)||target instanceof AbstractPhantom&&!PhantomDuels.pair(this,target)||isAlliedTo(target)&&!(target instanceof AbstractPhantom));}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return attacker!=null&&victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    public boolean ownerThreat(LivingEntity target){return recent(target,getOwner())||target instanceof Mob mob&&recipient(mob.getTarget())&&!PhantomDuels.pair(target,mob.getTarget());}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||recent(target,this)||recent(target,getOwner())||PhantomDuels.pair(this,target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null||target instanceof Player||target instanceof AbstractPhantom){return explicit;}return explicit||target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return enabled()&&validTarget(target)&&distanceToSqr(target)<=24*24&&line(getEyePosition(),target.getEyePosition())&&(ownerThreat(target)||!(target instanceof Mob mob&&GravityControl.movementImmune(mob)));}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return (ownerThreat(target)?-1000:0)+(target instanceof Mob mob&&recipient(mob.getTarget())?-100:0)+distanceToSqr(target)*.2D;}
    @Override public void setCombatTarget(LivingEntity target){if(target!=null&&!recipient(target)&&!PhantomDuels.excludesObserver(this,target)&&PhantomDuels.begin(this,target)&&safeEnemy(target)){orderedId=target.getUUID();super.setCombatTarget(target);}}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.position().distanceToSqr(center)<=radius*radius&&line(center.add(0,.7D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();}
    public float damage(ServerLevel level,LivingEntity target,DamageSource damage,float amount){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float before=target.getHealth()+target.getAbsorptionAmount();if(!target.hurtServer(level,damage,amount)){return 0;}float dealt=Math.max(0,before-target.getHealth()-target.getAbsorptionAmount());if(dealt>0&&!CrystalDefense.reflecting()){recoveryTicks=60;healTicks=Math.min(healTicks,60);}return dealt;}
    public void storedImpact(LivingEntity attacker,float healthDamage){if(!enabled()||CrystalDefense.reflecting()||!(attacker instanceof Enemy)||attacker instanceof OwnableEntity||!validTarget(attacker)||!Float.isFinite(healthDamage)||healthDamage<2||impactRecovery>0){return;}impactRecovery=40;impactIdle=200;entityData.set(IMPACT,Math.min(3,impact()+1));CrystalVfx.emit(this,CrystalVfx.IMPACT_CHARGE,this,position(),new Vec3(impact(),0,0),20,2);}
    private int spend(int maximum){int spent=Math.min(maximum,impact());entityData.set(IMPACT,impact()-spent);return spent;}
    public boolean shard(ServerLevel level,Vec3 aim,boolean mounted){if(!enabled()||(mounted?mountedShardTicks>0||!(getControllingPassenger() instanceof Player rider)||!isOwnedBy(rider):cooldowns[0]>0)||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.001D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.6D));if(!line(getEyePosition(),muzzle)){return false;}CrystalShard shot=PhantomismContent.CRYSTAL_SHARD.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,1.8F,0);if(!level.addFreshEntity(shot)){return false;}if(mounted){mountedShardTicks=6;}else{cooldowns[0]=200;}action("shard");CrystalVfx.emit(this,CrystalVfx.SHARD,shot,muzzle,direction,32,1);return true;}
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return shard(level,rider.getViewVector(1),true);}
    public boolean burst(ServerLevel level,LivingEntity primary){if(!enabled()||cooldowns[1]>0||!validTarget(primary)||distanceToSqr(primary)>8*8||!line(getEyePosition(),primary.getEyePosition())){return false;}Vec3 aim=primary.position().subtract(position()).normalize();var targets=enemies(level,position(),8,12).stream().filter(target->target==primary||aim.dot(target.position().subtract(position()).normalize())>.2D).limit(8).toList();if(targets.isEmpty()){return false;}int power=spend(1);cooldowns[1]=280;for(LivingEntity target:targets){if(damage(level,target,damageSources().mobAttack(this),6+power*2)>0){CrystalDefense.fracture(this,target,60);CrystalDefense.push(this,target,.35D);}}action("burst");CrystalVfx.emit(this,CrystalVfx.BURST,null,position(),aim,26,8);return true;}
    public boolean counter(ServerLevel level,LivingEntity target){if(!enabled()||cooldowns[2]>0||impact()==0||!validTarget(target)||distanceToSqr(target)>12*12||!line(getEyePosition(),target.getEyePosition())){return false;}int power=impact();float dealt=damage(level,target,damageSources().mobAttack(this),10+power*3);if(dealt<=0){return false;}spend(3);cooldowns[2]=360;counterTicks=40;CrystalDefense.push(this,target,.45D);action("counter");CrystalVfx.emit(this,CrystalVfx.COUNTER,target,target.getBoundingBox().getCenter(),position().subtract(target.position()),28,3+power);return true;}
    public boolean activateRiddenCounter(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),12,16).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.45D).findFirst().orElse(null);return target!=null&&(counter(level,target)||burst(level,target)||doHurtTarget(level,target));}
    public boolean barrier(ServerLevel level){if(!enabled()||cooldowns[3]>0||barrierTicks>0||domainTicks>0||enemies(level,position(),16,12).isEmpty()){return false;}barrierTicks=160;cooldowns[3]=320;action("barrier");CrystalVfx.emit(this,CrystalVfx.BARRIER,this,position(),Vec3.ZERO,160,12);return true;}
    public boolean shell(ServerLevel level,LivingEntity attacker,float incoming){if(!enabled()||cooldowns[4]>0||!validTarget(attacker)||incoming<6&&getHealth()-incoming>getMaxHealth()*.3F){return false;}shellTicks=60;cooldowns[4]=600;action("shell");CrystalVfx.emit(this,CrystalVfx.SHELL,this,position(),Vec3.ZERO,60,3);return true;}
    public float mitigation(LivingEntity target){if(!enabled()||!recipient(target)||!line(getEyePosition(),target.getEyePosition())){return 0;}float reduction=target==this?.1F:0;if(barrierTicks>0&&distanceToSqr(target)<=12*12||domainTicks>0&&inDomain(target)){reduction=Math.max(reduction,.35F);}if(target==this&&shellTicks>0){reduction=Math.max(reduction,.55F);}return reduction;}
    public boolean inDomain(LivingEntity target){return domainCenter!=null&&target.level()==level()&&target.position().distanceToSqr(domainCenter)<=18*18;}
    public boolean reflective(LivingEntity ward){return enabled()&&recipient(ward)&&(ward==this||barrierTicks>0&&distanceToSqr(ward)<=12*12||domainTicks>0&&inDomain(ward));}
    public boolean canReflect(){return reflections<4&&(domainTicks<=0||domainReflections<24);}
    public void commitReflection(){reflections++;if(domainTicks>0){domainReflections++;}visualOpen=true;}
    public float retaliation(){return shellTicks>0?.25F:counterTicks>0?.2F:0;}
    public boolean domain(ServerLevel level){if(!enabled()||cooldowns[5]>0||domainTicks>0||enemies(level,position(),18,12).size()<3||!level.getEntitiesOfClass(CrystalPhantom.class,getBoundingBox().inflate(36),other->other!=this&&other.enabled()&&other.domainTicks()>0&&(recipient(other)||other.recipient(this))).isEmpty()){return false;}domainPower=spend(3);domainTicks=240;domainCenter=position();domainVictims.clear();domainReflections=0;cooldowns[5]=1800;action("dominion");entityData.set(VISUAL,3);CrystalVfx.emit(this,CrystalVfx.DOMINION,null,domainCenter,Vec3.ZERO,240,18);return true;}
    private void tickDomain(ServerLevel level){if(domainTicks<=0){return;}int elapsed=240- --domainTicks;if(elapsed>=40&&elapsed<=200&&elapsed%40==0){for(LivingEntity target:enemies(level,domainCenter,18,16)){if(!domainVictims.containsKey(target.getUUID())&&domainVictims.size()>=12){continue;}int hits=domainVictims.getOrDefault(target.getUUID(),0);if(hits>=4){continue;}if(damage(level,target,damageSources().mobAttack(this),3)>0){domainVictims.put(target.getUUID(),hits+1);CrystalDefense.control(this,target,50);}}action("pulse");CrystalVfx.emit(this,CrystalVfx.REFRACTION,null,domainCenter,Vec3.ZERO,30,18);}if(domainTicks==0){for(LivingEntity target:enemies(level,domainCenter,18,16)){if(!domainVictims.containsKey(target.getUUID())&&domainVictims.size()>=12){continue;}if(damage(level,target,damageSources().mobAttack(this),12+domainPower*2)>0){domainVictims.putIfAbsent(target.getUUID(),0);CrystalDefense.push(this,target,.5D);}}CrystalDefense.releaseControl(this);action("shatter");CrystalVfx.emit(this,CrystalVfx.SHATTER,null,domainCenter,Vec3.ZERO,36,18);domainCenter=null;domainVictims.clear();domainPower=0;}}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.7D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))<=0){return false;}meleeTicks=40;action("combat");return true;}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}if(impact()>0&&counter(level,target)||doHurtTarget(level,target)){return true;}if(distanceToSqr(target)<=8*8&&burst(level,target)){return true;}return distanceToSqr(target)<=42*42&&line(getEyePosition(),target.getEyePosition())&&shard(level,target.getEyePosition().subtract(getEyePosition()),false);}
    @Override protected boolean prepareCombatApproach(ServerLevel level,LivingEntity target){if(!validTarget(target)){return true;}Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.1D){away=new Vec3(0,0,1);}flyToAssignment(target.position().add(away.scale(getHealth()<30?8:4)).add(0,2,0),1);return true;}
    @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(mountedShardTicks>0){mountedShardTicks--;}if(meleeTicks>0){meleeTicks--;}if(barrierTicks>0){barrierTicks--;}if(shellTicks>0){shellTicks--;}if(counterTicks>0){counterTicks--;}if(recoveryTicks>0){recoveryTicks--;}if(impactRecovery>0){impactRecovery--;}if(impactIdle>0&&--impactIdle==0&&impact()>0){entityData.set(IMPACT,impact()-1);impactIdle=100;}if(++reflectionWindow>=10){reflectionWindow=0;reflections=0;}UUID owner=getOwnerReference()==null?getUUID():getOwnerReference().getUUID();if(ownerContext!=null&&!ownerContext.equals(owner)){endCrystal();entityData.set(IMPACT,0);}ownerContext=owner;if(tickCount%10==0){CrystalDefense.maintain();}if(!enabled()){endCrystal();return;}if(--healTicks<=0){healTicks=recoveryTicks>0?60:100;if(getHealth()<getMaxHealth()){float before=getHealth();heal(1);if(getHealth()>before){action("recover");CrystalVfx.emit(this,CrystalVfx.RECOVER,this,position(),Vec3.ZERO,18,2);}}}tickDomain(level);entityData.set(VISUAL,domainTicks>0?3:barrierTicks>0||shellTicks>0?2:validTarget(getTarget())?1:0);if(tickCount%5==0){CrystalDefense.intercept(this,level);}if(!isNoAi()&&tickCount%20==0){LivingEntity target=getTarget();var choices=enemies(level,position(),24,12);LivingEntity threat=choices.stream().filter(this::ownerThreat).findFirst().orElse(null);if(threat!=null&&(orderedId==null||!(level.getEntity(orderedId) instanceof LivingEntity ordered)||!validTarget(ordered))){target=threat;setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);}if(!validTarget(target)&&!choices.isEmpty()){target=choices.getFirst();setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);}if(validTarget(target)){if(choices.size()>=3){if(!domain(level)){barrier(level);}}if(!isVehicle()){prepareSpeciesCombatAction(level,target);}}}}
    private void action(String clip){visualOpen=true;triggerAnim("crystal",clip);if(!clip.equals("pulse")&&!clip.equals("recover")){playSound(SoundEvents.AMETHYST_BLOCK_CHIME,.4F,1.1F);}}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<CrystalPhantom>("crystal",2,state->PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.crystal."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(!isTame()&&isAlive()&&!player.isSpectator()&&player.getItemInHand(hand).is(preferredTaming().item())){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}tame(player);clearCombatTarget(true);setOrder(Order.FOLLOW);level().broadcastEntityEvent(this,(byte)7);}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData group){SpawnGroupData result=super.finalizeSpawn(accessor,difficulty,reason,group);setVariant(getRandom().nextInt(3));return result;}
    @Override public AgeableMob getBreedOffspring(ServerLevel level,AgeableMob partner){AgeableMob child=super.getBreedOffspring(level,partner);if(child instanceof CrystalPhantom crystal){crystal.setVariant(variant());}return child;}
    public void endCrystal(){if(level() instanceof ServerLevel){CrystalDefense.release(this);if(visualOpen){CrystalVfx.cancel(this);session=UUID.randomUUID();visualOpen=false;}}barrierTicks=0;shellTicks=0;counterTicks=0;domainTicks=0;domainCenter=null;domainVictims.clear();domainPower=0;orderedId=null;entityData.set(VISUAL,0);}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endCrystal();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endCrystal();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endCrystal();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endCrystal();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);for(int index=0;index<cooldowns.length;index++){output.putInt("CrystalCooldown"+index,cooldowns[index]);}output.putInt("CrystalMountedShard",mountedShardTicks);output.putInt("CrystalMelee",meleeTicks);output.putInt("CrystalVariant",variant());output.putInt("CrystalImpact",impact());output.putInt("CrystalImpactRecovery",impactRecovery);output.putInt("CrystalImpactIdle",impactIdle);output.putInt("CrystalHeal",healTicks);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endCrystal();session=UUID.randomUUID();for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("CrystalCooldown"+index,0),0,INTERVALS[index]);}mountedShardTicks=Math.clamp(input.getIntOr("CrystalMountedShard",0),0,6);meleeTicks=Math.clamp(input.getIntOr("CrystalMelee",0),0,40);setVariant(input.getIntOr("CrystalVariant",0));entityData.set(IMPACT,Math.clamp(input.getIntOr("CrystalImpact",0),0,3));impactRecovery=Math.clamp(input.getIntOr("CrystalImpactRecovery",0),0,40);impactIdle=Math.clamp(input.getIntOr("CrystalImpactIdle",0),0,200);healTicks=Math.clamp(input.getIntOr("CrystalHeal",100),1,100);recoveryTicks=0;}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){List<CooldownDiscipline.Slot> slots=new ArrayList<>();for(int index=0;index<4;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("crystal_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.AMETHYST_CLUSTER, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Shard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Burst: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Counter: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Barrier: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Shell: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Dominion: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "shard" -> cooldowns[0];
            case "burst" -> cooldowns[1];
            case "counter" -> cooldowns[2];
            case "barrier" -> cooldowns[3];
            case "domain" -> cooldowns[5];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
