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
import net.ronm19.phantomism.vfx.ObsidianVfx;

public final class ObsidianPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/obsidian_phantom");
    public static final int CITADEL_DURATION=240,CITADEL_RADIUS=20,CITADEL_COOLDOWN=2000;
    private static final EntityDataAccessor<Integer> VISUAL=SynchedEntityData.defineId(ObsidianPhantom.class,EntityDataSerializers.INT),VARIANT=SynchedEntityData.defineId(ObsidianPhantom.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ANCHORED=SynchedEntityData.defineId(ObsidianPhantom.class,EntityDataSerializers.BOOLEAN),RAMMING=SynchedEntityData.defineId(ObsidianPhantom.class,EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS={240,280,280,360,320,600,CITADEL_COOLDOWN};
    private static final String[] CLIPS={"shard","ram","slam","shelter","fracture","guard","citadel","bastion","wall","judgment","combat"};
    private final int[] cooldowns=new int[7];
    private final Set<UUID> citadelVictims=new LinkedHashSet<>();
    private UUID session=UUID.randomUUID(),orderedId,ownerContext,ramTarget;
    private Vec3 anchor,citadelCenter,ramDirection;
    private int mountedShardTicks,meleeTicks,shelterTicks,guardTicks,citadelTicks,finalPulseTicks,ramTicks,bastionLock,tameAttempts,threatTicks,absorbWindow,absorbed;
    private boolean visualOpen,manualBastion;

    public ObsidianPhantom(EntityType<? extends ObsidianPhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,144).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.ARMOR,28).add(Attributes.ARMOR_TOUGHNESS,10).add(Attributes.KNOCKBACK_RESISTANCE,.95D).add(Attributes.FOLLOW_RANGE,48).add(Attributes.MOVEMENT_SPEED,.2D).add(Attributes.FLYING_SPEED,.3D);}
    public static boolean checkSpawnRules(EntityType<ObsidianPhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){
        AABB box=type.getSpawnAABB(position.getX()+.5D,position.getY(),position.getZ()+.5D).inflate(.05D);
        return accessor.getLevel().dimension()==Level.NETHER&&position.getY()<124&&box.minY>=accessor.getMinY()+1&&box.maxY<accessor.getMaxY()&&accessor.getBiome(position).is(ModTags.Biomes.OBSIDIAN_PHANTOM_SPAWNS)&&accessor.hasChunksAt(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY+1,box.maxZ))&&!accessor.containsAnyLiquid(box)&&accessor.noBlockCollision(null,box);
    }
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.OBSIDIAN_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 1;}
    @Override protected int getMeleeAttackCooldownTicks(){return 40;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(VISUAL,0);data.define(VARIANT,0);data.define(ANCHORED,false);data.define(RAMMING,false);}
    @Override public boolean isClientAuthoritative(){return !entityData.get(RAMMING)&&super.isClientAuthoritative();}
    @Override protected boolean isLocalClientAuthoritative(){return !entityData.get(RAMMING)&&super.isLocalClientAuthoritative();}
    @Override protected double getRiddenMaximumVerticalSpeed(){return .3D;}
    @Override public void move(MoverType type,Vec3 motion){if(type==MoverType.SELF&&!entityData.get(RAMMING)){if(anchored()){motion=Vec3.ZERO;}else if(motion.lengthSqr()>.09D){motion=motion.normalize().scale(.3D);}}super.move(type,motion);}
    @Override public void pushFromExplosion(Vec3 impulse){super.pushFromExplosion(impulse.scale(.05D));}
    @Override public void travel(Vec3 input){
        if(entityData.get(RAMMING)){setDeltaMovement(Vec3.ZERO);return;}
        if(anchored()){
            if(!level().isClientSide()&&citadelTicks==0&&getControllingPassenger() instanceof Player rider&&(Math.abs(rider.xxa)>.01F||Math.abs(rider.zza)>.01F)){exitBastion();}
            else{setDeltaMovement(Vec3.ZERO);return;}
        }
        if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}
        Vec3 motion=getDeltaMovement();if(motion.lengthSqr()>.3D*.3D){setDeltaMovement(motion.normalize().scale(.3D));}
    }
    @Override public boolean isUltimateAnimationActive(){return visualState()==3||super.isUltimateAnimationActive();}
    public int visualState(){return entityData.get(VISUAL);}
    public int variant(){return entityData.get(VARIANT);}
    public void setVariant(int variant){entityData.set(VARIANT,Math.clamp(variant,0,2));}
    public UUID session(){return session;}
    public boolean anchored(){return entityData.get(ANCHORED);}
    public int citadelTicks(){return citadelTicks;}
    public int shelterTicks(){return shelterTicks;}
    public int guardTicks(){return guardTicks;}
    public Vec3 defenseCenter(){return citadelTicks>0||finalPulseTicks>0?citadelCenter:anchor!=null?anchor:position();}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner() instanceof ServerPlayer owner&&owner.isAlive()&&owner.level()==level()&&level().getServer()!=null&&level().getServer().getPlayerList().getPlayer(owner.getUUID())==owner);}
    public boolean recipient(LivingEntity target){return target!=null&&target.isAlive()&&target.level()==level()&&(target==this||isTame()&&(target==getOwner()||getTeam()!=null&&getTeam().isAlliedTo(target.getTeam())||target instanceof OwnableEntity pet&&Objects.equals(getOwnerReference(),pet.getOwnerReference())));}
    public boolean ally(LivingEntity target){return target!=null&&(recipient(target)||target instanceof AbstractPhantom&&!PhantomDuels.pair(this,target)||isAlliedTo(target)&&!(target instanceof AbstractPhantom));}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return attacker!=null&&victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    public boolean ownerThreat(LivingEntity target){return recent(target,getOwner())||target instanceof Mob mob&&recipient(mob.getTarget())&&!PhantomDuels.pair(target,mob.getTarget());}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||recent(target,this)||recent(target,getOwner())||ownerThreat(target)||PhantomDuels.pair(this,target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null||target instanceof Player||target instanceof AbstractPhantom){return explicit;}return explicit||target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return enabled()&&validTarget(target)&&distanceToSqr(target)<=20*20&&line(getEyePosition(),target.getEyePosition())&&(ownerThreat(target)||!isTame()&&recent(target,this));}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return (ownerThreat(target)?-1000:0)+distanceToSqr(target)*.2D;}
    @Override public void setCombatTarget(LivingEntity target){if(target!=null&&!recipient(target)&&!PhantomDuels.excludesObserver(this,target)&&PhantomDuels.begin(this,target)&&safeEnemy(target)){if(anchored()){endObsidian();}orderedId=target.getUUID();super.setCombatTarget(target);}}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.position().distanceToSqr(center)<=radius*radius&&line(center.add(0,.7D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();}
    public float damage(ServerLevel level,LivingEntity target,DamageSource damage,float amount){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float before=target.getHealth()+target.getAbsorptionAmount();return target.hurtServer(level,damage,amount)?Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()):0;}
    public boolean shard(ServerLevel level,Vec3 aim,boolean mounted){
        if(!enabled()||(mounted?mountedShardTicks>0||!(getControllingPassenger() instanceof Player rider)||!isOwnedBy(rider):cooldowns[0]>0)||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.001D){return false;}
        Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.6D));if(!line(getEyePosition(),muzzle)){return false;}
        ObsidianShard shot=PhantomismContent.OBSIDIAN_SHARD.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,1.8F,0);if(!level.addFreshEntity(shot)){return false;}if(mounted){mountedShardTicks=6;}else{cooldowns[0]=240;}action("shard");ObsidianVfx.emit(this,ObsidianVfx.SHARD,shot,muzzle,direction,32,1);return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return shard(level,rider.getViewVector(1),true);}
    public boolean ram(ServerLevel level,LivingEntity target){
        if(!enabled()||anchored()||ramTicks>0||cooldowns[1]>0||!validTarget(target)||distanceToSqr(target)>12*12||!line(getEyePosition(),target.getEyePosition())){return false;}
        Vec3 direction=target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());if(direction.lengthSqr()<.01D){return false;}
        ramDirection=direction.normalize();ramTarget=target.getUUID();ramTicks=16;cooldowns[1]=280;entityData.set(RAMMING,true);setDeltaMovement(Vec3.ZERO);action("ram");ObsidianVfx.emit(this,ObsidianVfx.RAM,this,position(),ramDirection,16,2);return true;
    }
    private void stopRam(){boolean active=ramTicks>0||entityData.get(RAMMING);ramTicks=0;ramTarget=null;ramDirection=null;entityData.set(RAMMING,false);if(active){setDeltaMovement(Vec3.ZERO);needsSync=true;}}
    private void tickRam(ServerLevel level){
        if(ramTicks<=0){return;}LivingEntity target=level.getEntity(ramTarget) instanceof LivingEntity living?living:null;
        if(!validTarget(target)||--ramTicks<=0){stopRam();return;}Vec3 step=ramDirection.scale(.8D);AABB sweep=getBoundingBox().expandTowards(step);
        if(!isLoadedFlightBox(sweep)||!level.noBlockCollision(this,sweep)){stopRam();return;}
        move(MoverType.SELF,step);needsSync=true;
        if(getBoundingBox().inflate(.6D).intersects(target.getBoundingBox())){if(damage(level,target,damageSources().mobAttack(this),20)>0){ObsidianDefense.push(this,target,.85D);}ObsidianVfx.emit(this,ObsidianVfx.IMPACT,null,position(),ramDirection,24,4);stopRam();}
    }
    public boolean activateRiddenRam(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),12,16).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.4D).findFirst().orElse(null);return target!=null&&ram(level,target);}
    public boolean slam(ServerLevel level){
        if(!enabled()||ramTicks>0||cooldowns[2]>0){return false;}var targets=enemies(level,position(),7,12);if(targets.isEmpty()){return false;}cooldowns[2]=280;
        for(LivingEntity target:targets){if(damage(level,target,damageSources().mobAttack(this),12)>0){ObsidianDefense.push(this,target,.65D);ObsidianDefense.control(this,target,30);}}
        action("slam");ObsidianVfx.emit(this,ObsidianVfx.SLAM,null,position(),Vec3.ZERO,30,7);return true;
    }
    public boolean toggleBastion(){if(anchored()&&citadelTicks==0){exitBastion();bastionLock=20;return true;}return enterBastion(true);}
    private boolean enterBastion(boolean manual){
        if(!enabled()||ramTicks>0||anchored()||bastionLock>0||!isLoadedFlightBox(getBoundingBox().inflate(.2D))||!level().noBlockCollision(this,getBoundingBox())){return false;}
        anchor=position();manualBastion=manual;entityData.set(ANCHORED,true);getNavigation().stop();setDeltaMovement(Vec3.ZERO);action("bastion");ObsidianVfx.emit(this,ObsidianVfx.BASTION,null,anchor,Vec3.ZERO,80,6);return true;
    }
    public void exitBastion(){anchor=null;manualBastion=false;entityData.set(ANCHORED,false);}
    public boolean shelter(ServerLevel level){if(!enabled()||cooldowns[3]>0||shelterTicks>0||citadelTicks>0||threatTicks==0&&enemies(level,position(),18,12).isEmpty()){return false;}shelterTicks=160;cooldowns[3]=360;action("shelter");ObsidianVfx.emit(this,ObsidianVfx.SHELTER,this,position(),Vec3.ZERO,160,12);return true;}
    public boolean shatterArmor(ServerLevel level,LivingEntity target){if(!enabled()||cooldowns[4]>0||!validTarget(target)||distanceToSqr(target)>5*5||!line(getEyePosition(),target.getEyePosition())||damage(level,target,damageSources().mobAttack(this),14)<=0){return false;}cooldowns[4]=320;ObsidianDefense.fracture(this,target,60);action("fracture");ObsidianVfx.emit(this,ObsidianVfx.FRACTURE,target,target.position(),Vec3.ZERO,24,2);return true;}
    public boolean guard(ServerLevel level){if(!enabled()||cooldowns[5]>0){return false;}guardTicks=60;cooldowns[5]=600;action("guard");ObsidianVfx.emit(this,ObsidianVfx.GUARD,this,position(),Vec3.ZERO,60,10);return true;}
    public void incomingThreat(LivingEntity attacker,LivingEntity ward,float amount,boolean explosion){if(!enabled()||!recipient(ward)||attacker!=null&&(!safeEnemy(attacker)||PhantomDuels.pair(attacker,ward))){return;}threatTicks=100;if(amount>=6||ward.getHealth()<ward.getMaxHealth()*.35F||explosion){guard((ServerLevel)level());}if(attacker!=null&&validTarget(attacker)&&line(getEyePosition(),attacker.getEyePosition())&&(getTarget()==null||ownerThreat(attacker))){setTarget(attacker);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,attacker);}}
    public float mitigation(LivingEntity ward,boolean explosion){
        if(!enabled()||!recipient(ward)||!line(getEyePosition(),ward.getEyePosition())){return 0;}Vec3 center=defenseCenter();double distance=ward.position().distanceToSqr(center);float reduction=ward==this&&anchored()?.2F:0;
        if(shelterTicks>0&&distanceToSqr(ward)<=144||guardTicks>0&&distanceToSqr(ward)<=100){reduction=Math.max(reduction,explosion?.65F:.35F);}
        if(finalPulseTicks>0&&distance<=400){reduction=Math.max(reduction,explosion?.65F:.35F);}if(citadelTicks>0&&distance<=400){reduction=Math.max(reduction,explosion?.8F:.5F);}if(explosion&&ward==this){reduction=Math.max(reduction,.85F);}return reduction;
    }
    public boolean canAbsorb(){return absorbed<4;}
    public void absorbed(){absorbed++;visualOpen=true;}
    public boolean citadel(ServerLevel level){
        if(!enabled()||cooldowns[6]>0||citadelTicks>0||ramTicks>0||enemies(level,position(),20,12).size()<3||!level.getEntitiesOfClass(ObsidianPhantom.class,getBoundingBox().inflate(40),other->other!=this&&other.enabled()&&other.citadelTicks()>0&&(recipient(other)||other.recipient(this))).isEmpty()){return false;}
        if(!anchored()&&!enterBastion(false)){return false;}citadelTicks=240;citadelCenter=position();anchor=citadelCenter;manualBastion=false;citadelVictims.clear();cooldowns[6]=2000;
        for(LivingEntity target:enemies(level,citadelCenter,20,12)){ObsidianDefense.push(this,target,.5D);}action("citadel");entityData.set(VISUAL,3);ObsidianVfx.emit(this,ObsidianVfx.CITADEL,null,citadelCenter,Vec3.ZERO,240,20);return true;
    }
    private void tickCitadel(ServerLevel level){
        if(citadelTicks<=0){return;}int elapsed=240- --citadelTicks;
        if(elapsed>=40&&elapsed<=200&&elapsed%40==0){for(LivingEntity target:enemies(level,citadelCenter,20,16)){if(!citadelVictims.contains(target.getUUID())&&citadelVictims.size()>=12){continue;}citadelVictims.add(target.getUUID());ObsidianDefense.control(this,target,50);}action("wall");ObsidianVfx.emit(this,ObsidianVfx.WALL,null,citadelCenter,Vec3.ZERO,30,20);}
        if(citadelTicks==0){for(LivingEntity target:enemies(level,citadelCenter,20,16)){if(!citadelVictims.contains(target.getUUID())&&citadelVictims.size()>=12){continue;}citadelVictims.add(target.getUUID());if(damage(level,target,damageSources().mobAttack(this),22)>0){ObsidianDefense.push(this,target,.9D);}}guardTicks=Math.max(guardTicks,20);finalPulseTicks=20;ObsidianDefense.releaseControl(this);action("judgment");ObsidianVfx.emit(this,ObsidianVfx.JUDGMENT,null,citadelCenter,Vec3.ZERO,36,20);citadelVictims.clear();exitBastion();bastionLock=40;}
    }
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.7D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))<=0){return false;}meleeTicks=40;action("combat");return true;}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}if(shatterArmor(level,target)||doHurtTarget(level,target)){return true;}if(!anchored()&&distanceToSqr(target)<=144&&ram(level,target)){return true;}return distanceToSqr(target)<=42*42&&line(getEyePosition(),target.getEyePosition())&&shard(level,target.getEyePosition().subtract(getEyePosition()),false);}
    @Override protected boolean prepareCombatApproach(ServerLevel level,LivingEntity target){if(anchored()||ramTicks>0||!validTarget(target)){return true;}Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.1D){away=new Vec3(0,0,1);}flyToAssignment(target.position().add(away.scale(5)).add(0,2,0),.7D);return true;}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(mountedShardTicks>0){mountedShardTicks--;}if(meleeTicks>0){meleeTicks--;}if(shelterTicks>0){shelterTicks--;}if(guardTicks>0){guardTicks--;}if(finalPulseTicks>0&&--finalPulseTicks==0&&citadelTicks==0){citadelCenter=null;}if(bastionLock>0){bastionLock--;}if(threatTicks>0){threatTicks--;}if(++absorbWindow>=10){absorbWindow=0;absorbed=0;}
        UUID owner=getOwnerReference()==null?getUUID():getOwnerReference().getUUID();if(ownerContext!=null&&!ownerContext.equals(owner)){endObsidian();}ownerContext=owner;
        if(!enabled()){endObsidian();return;}if(tickCount%10==0){ObsidianDefense.maintain();}
        if(anchored()&&(anchor==null||position().distanceToSqr(anchor)>9||getOwner()!=null&&getOwner().distanceToSqr(anchor)>24*24||!isLoadedFlightBox(getBoundingBox().inflate(.2D)))){endObsidian();}
        if(anchored()){getNavigation().stop();if(citadelTicks==0&&!manualBastion&&threatTicks==0&&!validTarget(getTarget())){exitBastion();bastionLock=40;}else if(tickCount%60==0){ObsidianVfx.emit(this,ObsidianVfx.BASTION,null,anchor,Vec3.ZERO,65,6);}}
        tickRam(level);tickCitadel(level);entityData.set(VISUAL,citadelTicks>0?3:anchored()||shelterTicks>0||guardTicks>0?2:validTarget(getTarget())?1:0);
        if(tickCount%5==0){ObsidianDefense.intercept(this,level);}
        if(!isNoAi()&&tickCount%20==0){
            var choices=enemies(level,position(),24,12);LivingEntity threat=choices.stream().filter(this::ownerThreat).findFirst().orElse(null);LivingEntity target=getTarget();
            if(threat!=null&&(orderedId==null||!(level.getEntity(orderedId) instanceof LivingEntity ordered)||!validTarget(ordered))){target=threat;setTarget(target);getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);threatTicks=100;}
            if(threatTicks>0||choices.stream().anyMatch(enemy->enemy instanceof Creeper creeper&&creeper.getSwellDir()>0)){shelter(level);if(choices.size()>=3){enterBastion(false);citadel(level);}}
            if(choices.size()>=3){slam(level);}if(validTarget(target)&&!isVehicle()){prepareSpeciesCombatAction(level,target);}
        }
    }
    private void action(String clip){visualOpen=true;triggerAnim("obsidian",clip);if(!clip.equals("wall")){playSound(SoundEvents.AMETHYST_BLOCK_BREAK,.5F,.65F);}}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<ObsidianPhantom>("obsidian",2,state->PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.obsidian."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!isTame()&&isAlive()&&!player.isSpectator()&&player.getItemInHand(hand).is(preferredTaming().item())){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}if(++tameAttempts>=preferredTaming().guaranteedAttempt()||getRandom().nextFloat()<preferredTaming().chance()){tame(player);clearCombatTarget(true);setOrder(Order.FOLLOW);level().broadcastEntityEvent(this,(byte)7);}else{level().broadcastEntityEvent(this,(byte)6);}}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData group){SpawnGroupData result=super.finalizeSpawn(accessor,difficulty,reason,group);setVariant(getRandom().nextInt(3));return result;}
    @Override public AgeableMob getBreedOffspring(ServerLevel level,AgeableMob partner){AgeableMob child=super.getBreedOffspring(level,partner);if(child instanceof ObsidianPhantom obsidian){obsidian.setVariant(variant());}return child;}
    public void endObsidian(){if(level() instanceof ServerLevel){ObsidianDefense.release(this);if(visualOpen){ObsidianVfx.cancel(this);session=UUID.randomUUID();visualOpen=false;}}shelterTicks=0;guardTicks=0;citadelTicks=0;finalPulseTicks=0;citadelCenter=null;citadelVictims.clear();orderedId=null;threatTicks=0;exitBastion();stopRam();entityData.set(VISUAL,0);}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endObsidian();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null){endObsidian();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endObsidian();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endObsidian();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);for(int index=0;index<cooldowns.length;index++){output.putInt("ObsidianCooldown"+index,cooldowns[index]);}output.putInt("ObsidianMountedShard",mountedShardTicks);output.putInt("ObsidianMelee",meleeTicks);output.putInt("ObsidianVariant",variant());output.putInt("ObsidianTamingAttempts",tameAttempts);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endObsidian();setDeltaMovement(Vec3.ZERO);session=UUID.randomUUID();for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("ObsidianCooldown"+index,0),0,INTERVALS[index]);}mountedShardTicks=Math.clamp(input.getIntOr("ObsidianMountedShard",0),0,6);meleeTicks=Math.clamp(input.getIntOr("ObsidianMelee",0),0,40);setVariant(input.getIntOr("ObsidianVariant",0));tameAttempts=Math.clamp(input.getIntOr("ObsidianTamingAttempts",0),0,2);}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){List<CooldownDiscipline.Slot> slots=new ArrayList<>();for(int index=0;index<5;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("obsidian_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.OBSIDIAN, .75F, 2, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Shard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Ram: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Slam: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Shelter: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Fracture: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Citadel: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "shard" -> cooldowns[0];
            case "ram" -> cooldowns[1];
            case "slam" -> cooldowns[2];
            case "shelter" -> cooldowns[3];
            case "shatter_armor" -> cooldowns[4];
            case "citadel" -> cooldowns[6];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
