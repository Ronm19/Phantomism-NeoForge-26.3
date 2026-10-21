package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.ReaperEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.ReaperVfx;

public final class ReaperPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/reaper_phantom");
    private static final EntityDataAccessor<Integer> SOUL_FRAGMENTS=SynchedEntityData.defineId(ReaperPhantom.class,EntityDataSerializers.INT);
    private static final int[] INTERVALS={240,120,240,320,240,1200,2000};
    private static final String[] CLIPS={"mark","cut","sever","passage","shard","bargain","bell","toll","judgment","conclusion","strike"};
    private final int[] cooldowns=new int[7];
    private UUID session=UUID.randomUUID(),markedId,orderedId;
    private int markTicks,meleeTicks,actionTicks,bargainTicks,bellTicks;
    private Vec3 bellCenter;
    private boolean sessionOpen;
    public ReaperPhantom(EntityType<? extends ReaperPhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,72).add(Attributes.ATTACK_DAMAGE,9).add(Attributes.ARMOR,10).add(Attributes.ARMOR_TOUGHNESS,4).add(Attributes.KNOCKBACK_RESISTANCE,.4D).add(Attributes.FOLLOW_RANGE,48).add(Attributes.MOVEMENT_SPEED,.25D).add(Attributes.FLYING_SPEED,.45D);}
    public static boolean checkSpawnRules(EntityType<ReaperPhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){ServerLevel level=accessor.getLevel();return ReaperEncounterSpawner.habitat(level,position)&&level.getFluidState(position).isEmpty()&&level.getBlockState(position).getCollisionShape(level,position).isEmpty();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(SOUL_FRAGMENTS,0);}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.REAPER_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 1;}
    @Override protected int getMeleeAttackCooldownTicks(){return 32;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override public boolean isUltimateAnimationActive(){return bellTicks>0||super.isUltimateAnimationActive();}
    @Override public void travel(Vec3 input){if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}}
    public UUID session(){return session;}
    public void visualStarted(){sessionOpen=true;}
    public int souls(){return entityData.get(SOUL_FRAGMENTS);}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner()!=null&&getOwner().isAlive()&&getOwner().level()==level());}
    public boolean ally(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&(target==this||isAlliedTo(target)||isTame()&&(target==getOwner()||target instanceof OwnableEntity owned&&Objects.equals(getOwnerReference(),owned.getOwnerReference())));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker.getLastHurtMob()==victim&&attacker.tickCount-attacker.getLastHurtMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    private boolean provoked(LivingEntity target){return recent(target,this)||recent(target,getOwner());}
    public boolean hostileKind(LivingEntity target){return target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN&&!(target instanceof OwnableEntity)&&!(target instanceof AbstractPhantom);}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!(target instanceof AbstractPhantom)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||PhantomDuels.pair(this,target)||provoked(target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null){return explicit;}return explicit||hostileKind(target);}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return validTarget(target)&&(hostileKind(target)||provoked(target));}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return priority(target);}
    private double priority(LivingEntity target){return(target.getUUID().equals(markedId)?-10000:0)-(wounded(target)?1000:0)-(target.getType()==EntityTypes.EVOKER?300:0)+target.getHealth()/Math.max(1,target.getMaxHealth())*100+distanceToSqr(target)*.01D;}
    public static boolean wounded(LivingEntity target){return target.getHealth()>0&&target.getHealth()<target.getMaxHealth()*.3F;}
    @Override public void setCombatTarget(LivingEntity target){if(!safeEnemy(target)||!PhantomDuels.begin(this,target)){return;}orderedId=target.getUUID();super.setCombatTarget(target);}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.distanceToSqr(center)<=radius*radius&&line(center.add(0,.4D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::priority)).limit(maximum).toList();}
    public LivingEntity marked(){if(!(level() instanceof ServerLevel level)||markedId==null){return null;}if(markTicks<=0||!(level.getEntity(markedId) instanceof LivingEntity target)||!validTarget(target)||distanceToSqr(target)>48*48){ReaperVfx.emit(this,ReaperVfx.CLEAR_MARK,null,position(),Vec3.ZERO,1,1);markedId=null;markTicks=0;return null;}return target;}
    public boolean mark(ServerLevel level,LivingEntity target){if(!enabled()||cooldowns[0]>0||!validTarget(target)||distanceToSqr(target)>48*48||!line(getEyePosition(),target.getEyePosition())){return false;}ReaperVfx.emit(this,ReaperVfx.CLEAR_MARK,null,position(),Vec3.ZERO,1,1);markedId=target.getUUID();markTicks=200;cooldowns[0]=240;action("mark",14);ReaperVfx.emit(this,ReaperVfx.MARK,target,target.position(),Vec3.ZERO,200,1);return true;}
    public float damage(ServerLevel level,LivingEntity target,DamageSource source,float amount){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float applied=amount*(wounded(target)?1.25F:1),before=target.getHealth()+target.getAbsorptionAmount();return target.hurtServer(level,source,applied)?Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()):0;}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))<=0){return false;}meleeTicks=32;action("strike",10);ReaperVfx.emit(this,ReaperVfx.IMPACT,null,enemy.position(),Vec3.ZERO,16,1);return true;}
    public boolean soulCut(ServerLevel level,LivingEntity target){
        if(!enabled()||cooldowns[1]>0||!validTarget(target)||distanceToSqr(target)>16||!line(getEyePosition(),target.getEyePosition())){return false;}Vec3 heading=target.position().subtract(position()).multiply(1,0,1).normalize();List<LivingEntity> crowd=enemies(level,position(),4,16).stream().filter(enemy->enemy==target||heading.dot(enemy.position().subtract(position()).multiply(1,0,1).normalize())>.25D).limit(4).toList();boolean hit=false;
        for(LivingEntity enemy:crowd){hit|=damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*1.2F)>0;}if(!hit){return false;}cooldowns[1]=120;meleeTicks=32;action("cut",14);ReaperVfx.emit(this,ReaperVfx.CUT,null,position(),heading,22,4);return true;
    }
    public boolean mortalSever(ServerLevel level,LivingEntity target){if(!enabled()||cooldowns[2]>0||!validTarget(target)||target.getHealth()>target.getMaxHealth()*.5F&&!ReaperSouls.regenerating(target)||!getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox())||!line(getEyePosition(),target.getEyePosition())||damage(level,target,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*1.8F)<=0){return false;}cooldowns[2]=240;meleeTicks=32;ReaperSouls.finality(this,target,true);action("sever",18);ReaperVfx.emit(this,ReaperVfx.SEVER,null,target.position(),target.position().subtract(position()),26,3);return true;}
    public boolean passage(ServerLevel level,LivingEntity target){
        if(!enabled()||cooldowns[3]>0||isVehicle()||isPassenger()||!validTarget(target)||distanceToSqr(target)>12*12||distanceToSqr(target)<4*4||!line(getEyePosition(),target.getEyePosition())){return false;}
        double start=Math.atan2(target.getViewVector(1).z,target.getViewVector(1).x)+Math.PI;
        for(int attempt=0;attempt<8;attempt++){double angle=start+attempt*Math.PI/4;Vec3 destination=target.position().add(Math.cos(angle)*2.2D,.3D,Math.sin(angle)*2.2D),delta=destination.subtract(position());AABB box=getBoundingBox().move(delta),swept=getBoundingBox().expandTowards(delta);
            if(box.minY<level.getMinY()+1||box.maxY>level.getMaxY()-1||!level.getWorldBorder().isWithinBounds(box)||!isLoadedFlightBox(swept)||!level.noCollision(this,swept)||level.containsAnyLiquid(swept)||!line(destination.add(0,getEyeHeight(),0),target.getEyePosition())){continue;}
            Vec3 previous=position();teleportTo(destination.x,destination.y,destination.z);setDeltaMovement(Vec3.ZERO);getNavigation().stop();cooldowns[3]=320;action("passage",16);ReaperVfx.emit(this,ReaperVfx.PASSAGE,null,previous,delta,22,3);ReaperVfx.emit(this,ReaperVfx.ARRIVAL,this,position(),Vec3.ZERO,22,2);if(!mortalSever(level,target)){soulCut(level,target);}return true;
        }return false;
    }
    public boolean shard(ServerLevel level,Vec3 aim){int interval=getControllingPassenger() instanceof Player?6:240;if(!enabled()||cooldowns[4]>240-interval||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.01D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.7D));if(!line(getEyePosition(),muzzle)){return false;}ReaperShard shot=PhantomismContent.REAPER_SHARD.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,2.1F,0);if(!level.addFreshEntity(shot)){return false;}cooldowns[4]=240;action("shard",12);ReaperVfx.emit(this,ReaperVfx.SHARD,shot,muzzle,direction,36,.7F);return true;}
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return shard(level,rider.getViewVector(1));}
    public boolean activateRiddenDive(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),4,24).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.25D).findFirst().orElse(null);return target!=null&&(mortalSever(level,target)||soulCut(level,target));}
    public boolean eligibleSoul(LivingEntity victim){return enabled()&&victim!=null&&victim.level()==level()&&hostileKind(victim)&&victim.getMaxHealth()>=8&&!isAlliedTo(victim)&&distanceToSqr(victim)<=48*48;}
    public boolean harvest(LivingEntity victim){if(!eligibleSoul(victim)||!victim.isDeadOrDying()||souls()>=3){return false;}entityData.set(SOUL_FRAGMENTS,souls()+1);sessionOpen=true;ReaperVfx.emit(this,ReaperVfx.HARVEST,null,victim.position(),position().subtract(victim.position()),30,2);soulVisual();notifySouls();return true;}
    private void soulVisual(){ReaperVfx.emit(this,ReaperVfx.SOULS,this,position(),new Vec3(souls(),0,0),souls()>0?100:1,2);}
    private void notifySouls(){if(getOwner() instanceof Player owner){owner.sendOverlayMessage(Component.translatable("message.phantomism.reaper_souls",souls()));}}
    public boolean bargain(ServerLevel level,LivingEntity attacker){if(!enabled()||cooldowns[5]>0||souls()<=0||getHealth()>getMaxHealth()*.25F||!validTarget(attacker)){return false;}int fragments=souls();float before=getHealth();heal(fragments*4);if(getHealth()<=before){return false;}entityData.set(SOUL_FRAGMENTS,0);bargainTicks=60;cooldowns[5]=1200;action("bargain",24);soulVisual();notifySouls();ReaperVfx.emit(this,ReaperVfx.BARGAIN,this,position(),Vec3.ZERO,60,2);return true;}
    public boolean bargainActive(){return enabled()&&bargainTicks>0;}
    public int bellTicks(){return bellTicks;}
    public Vec3 bellCenter(){return bellCenter;}
    public boolean lastBell(ServerLevel level){if(!enabled()||cooldowns[6]>0||bellTicks>0){return false;}List<LivingEntity> crowd=enemies(level,position(),24,32);if(crowd.size()<4&&crowd.stream().filter(ReaperPhantom::wounded).count()<2){return false;}LivingEntity mark=marked();if(mark!=null){markTicks=Math.max(markTicks,240);ReaperVfx.emit(this,ReaperVfx.CLEAR_MARK,null,position(),Vec3.ZERO,1,1);ReaperVfx.emit(this,ReaperVfx.MARK,mark,mark.position(),Vec3.ZERO,markTicks,1);}bellCenter=position();bellTicks=240;cooldowns[6]=2000;action("bell",36);ReaperVfx.emit(this,ReaperVfx.BELL,null,bellCenter,Vec3.ZERO,240,24);return true;}
    private void toll(ServerLevel level){for(LivingEntity target:enemies(level,bellCenter,24,12)){if(damage(level,target,damageSources().mobAttack(this),4)>0){ReaperSouls.finality(this,target,true);}}level.playSound(null,bellCenter.x,bellCenter.y+12,bellCenter.z,SoundEvents.BELL_BLOCK,SoundSource.HOSTILE,1.5F,.6F);action("toll",16);}
    private void judgment(ServerLevel level){for(LivingEntity target:enemies(level,bellCenter,24,32).stream().filter(target->target.getUUID().equals(markedId)||wounded(target)).limit(10).toList()){if(damage(level,target,damageSources().mobAttack(this),18)>0){ReaperVfx.emit(this,ReaperVfx.JUDGMENT,target,target.position(),Vec3.ZERO,28,2);}}action("judgment",20);}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}if(mortalSever(level,target)||soulCut(level,target)||distanceToSqr(target)>5*5&&passage(level,target)){return true;}if(doHurtTarget(level,target)){return true;}return false;}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(meleeTicks>0){meleeTicks--;}if(actionTicks>0){actionTicks--;}if(bargainTicks>0){bargainTicks--;}
        if(!enabled()){endReaper();return;}if(markTicks>0){markTicks--;}marked();
        if(bellTicks>0){if(distanceToSqr(bellCenter)>48*48||!isLoadedFlightBox(new AABB(bellCenter,bellCenter).inflate(1))){endReaper();return;}int elapsed=240- --bellTicks;if(elapsed==60||elapsed==110||elapsed==160){toll(level);}if(elapsed==200){judgment(level);}if(bellTicks==0){ReaperVfx.emit(this,ReaperVfx.CONCLUSION,null,bellCenter,Vec3.ZERO,30,24);action("conclusion",20);bellCenter=null;}}
        if(tickCount%20==0){ReaperSouls.maintain();List<LivingEntity> crowd=enemies(level,position(),48,16);if(marked()==null&&!crowd.isEmpty()){mark(level,crowd.getFirst());}if(validTarget(getTarget())){lastBell(level);}if(tickCount%40==0){for(LivingEntity target:crowd.stream().filter(enemy->enemy.getHealth()<enemy.getMaxHealth()*.5F).limit(3).toList()){ReaperVfx.emit(this,ReaperVfx.SENSE,target,target.position(),Vec3.ZERO,38,1);}if(souls()>0){soulVisual();}}}
        if(!isVehicle()&&tickCount%5==0&&validTarget(getTarget())&&distanceToSqr(getTarget())<=39*39&&line(getEyePosition(),getTarget().getEyePosition())){shard(level,getTarget().getEyePosition().subtract(getEyePosition()));}
    }
    private void action(String clip,int ticks){actionTicks=ticks;sessionOpen=true;triggerAnim("reaper",clip);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<ReaperPhantom>("reaper",3,state->actionTicks>0?PlayState.CONTINUE:PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.reaper."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(isAlive()&&!isTame()&&!player.isSpectator()&&player.getItemInHand(hand).is(Items.WITHER_SKELETON_SKULL)){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}tame(player);clearCombatTarget(true);level().broadcastEntityEvent(this,(byte)7);}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    public void endReaper(){if(sessionOpen&&level() instanceof ServerLevel){ReaperVfx.cancel(this);session=UUID.randomUUID();sessionOpen=false;}ReaperSouls.release(this);markedId=null;orderedId=null;markTicks=0;bargainTicks=0;bellTicks=0;actionTicks=0;bellCenter=null;}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endReaper();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endReaper();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endReaper();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endReaper();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.putInt("ReaperMelee",meleeTicks);output.putInt("ReaperFragments",souls());for(int index=0;index<cooldowns.length;index++){output.putInt("ReaperCooldown"+index,cooldowns[index]);}}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endReaper();session=UUID.randomUUID();entityData.set(SOUL_FRAGMENTS,Math.clamp(input.getIntOr("ReaperFragments",0),0,3));meleeTicks=Math.clamp(input.getIntOr("ReaperMelee",0),0,32);for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("ReaperCooldown"+index,0),0,INTERVALS[index]);}}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){var slots=new ArrayList<CooldownDiscipline.Slot>();for(int index=0;index<5;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("reaper_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}
}
