package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.VoidEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.VoidVfx;

public final class VoidPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/void_phantom");
    private static final String[] KEYS={"boltCooldown","graspCooldown","anchorCooldown","pulseCooldown","sealCooldown","stepCooldown","shieldCooldown"};
    private static final String[] CLIPS={"bolt","grasp","anchor","pulse","seal","step","shield","horizon","wave","collapse","strike"};
    private final int[] cooldowns=new int[8];
    private final VoidRescue rescue=new VoidRescue(this);
    private UUID session=UUID.randomUUID(),orderedId,shieldTarget,sealTarget;
    private int meleeTicks,actionTicks,shieldTicks,horizonTicks;
    private Vec3 horizonCenter;
    private boolean sessionOpen;
    public VoidPhantom(EntityType<? extends VoidPhantom> type,Level level){super(type,level);}
    public static int tuning(String key){return (int)Math.round(Config.voidValue(key));}
    public static int interval(int slot){return slot==7?2000:tuning(KEYS[slot]);}
    public VoidRescue rescue(){return rescue;}
    public boolean ready(int slot){return cooldowns[slot]==0;}
    public void consume(int slot){cooldowns[slot]=interval(slot);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,80).add(Attributes.ATTACK_DAMAGE,7).add(Attributes.ARMOR,12).add(Attributes.ARMOR_TOUGHNESS,4).add(Attributes.KNOCKBACK_RESISTANCE,.55D).add(Attributes.FOLLOW_RANGE,56).add(Attributes.MOVEMENT_SPEED,.25D).add(Attributes.FLYING_SPEED,.45D);}
    public static boolean checkSpawnRules(EntityType<VoidPhantom> type,ServerLevelAccessor accessor,EntitySpawnReason reason,BlockPos position,RandomSource random){ServerLevel level=accessor.getLevel();return VoidEncounterSpawner.habitat(level,position)&&level.getFluidState(position).isEmpty()&&level.getBlockState(position).getCollisionShape(level,position).isEmpty();}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.VOID_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 1;}
    @Override protected int getMeleeAttackCooldownTicks(){return 36;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override public boolean isUltimateAnimationActive(){return horizonTicks>0||super.isUltimateAnimationActive();}
    @Override public void travel(Vec3 input){if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}}
    public UUID session(){return session;}
    public void visualStarted(){sessionOpen=true;}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner()!=null&&getOwner().isAlive()&&getOwner().level()==level());}
    public boolean ally(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&(target==this||isAlliedTo(target)||isTame()&&(target==getOwner()||target instanceof OwnableEntity owned&&Objects.equals(getOwnerReference(),owned.getOwnerReference())));}
    public boolean recipient(LivingEntity target){return enabled()&&ally(target)&&(target==this||isTame()&&target==getOwner()||target instanceof AbstractPhantom phantom&&phantom.isTame()&&Objects.equals(getOwnerReference(),phantom.getOwnerReference()));}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker.getLastHurtMob()==victim&&attacker.tickCount-attacker.getLastHurtMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    private boolean provoked(LivingEntity target){return recent(target,this)||recent(target,getOwner());}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!(target instanceof AbstractPhantom)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){if(!safeEnemy(target)){return false;}boolean explicit=target.getUUID().equals(orderedId)||PhantomDuels.pair(this,target)||provoked(target);if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null){return explicit;}return explicit||target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;}
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return validTarget(target);}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return(target.getType()==EntityTypes.EVOKER?-300:0)-Math.min(100,target.getMaxHealth())+distanceToSqr(target)*.01D;}
    @Override public void setCombatTarget(LivingEntity target){if(!safeEnemy(target)||!PhantomDuels.begin(this,target)){return;}orderedId=target.getUUID();super.setCombatTarget(target);}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.distanceToSqr(center)<=radius*radius&&line(center.add(0,.4D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();}
    public float damage(ServerLevel level,LivingEntity target,DamageSource source,float amount){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float before=target.getHealth()+target.getAbsorptionAmount();return target.hurtServer(level,source,amount)?Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()):0;}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))<=0){return false;}meleeTicks=36;action("strike",10);VoidVfx.emit(this,VoidVfx.IMPACT,null,enemy.position(),Vec3.ZERO,16,1);return true;}
    public boolean bolt(ServerLevel level,Vec3 aim){int interval=getControllingPassenger() instanceof Player?getRiddenProjectileAbilityCooldownTicks():interval(0);if(!enabled()||cooldowns[0]>interval(0)-interval||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.01D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.8D));if(!line(getEyePosition(),muzzle)){return false;}VoidBolt bolt=PhantomismContent.VOID_BOLT.get().create(level,EntitySpawnReason.TRIGGERED);if(bolt==null){return false;}bolt.setOwner(this);bolt.configure(session);bolt.setPos(muzzle);bolt.shoot(direction.x,direction.y,direction.z,2F,0);if(!level.addFreshEntity(bolt)){return false;}consume(0);action("bolt",12);VoidVfx.emit(this,VoidVfx.BOLT,bolt,muzzle,direction,36,1);return true;}
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return Math.min(tuning("mountedCooldown"),interval(0));}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return bolt(level,rider.getViewVector(1));}
    public boolean grasp(ServerLevel level,LivingEntity target){if(!enabled()||!ready(1)||!validTarget(target)||distanceToSqr(target)>Math.pow(Config.voidValue("graspRange"),2)||!line(getEyePosition(),target.getEyePosition())||!VoidSuppression.apply(this,target,VoidSuppression.Kind.GRASP,tuning("graspDuration"))){return false;}consume(1);action("grasp",20);VoidVfx.emit(this,VoidVfx.GRASP,target,target.position(),Vec3.ZERO,tuning("graspDuration"),2);return true;}
    public boolean antiCasterPulse(ServerLevel level){if(!enabled()||!ready(3)){return false;}var crowd=enemies(level,position(),Config.voidValue("pulseRadius"),16);if(crowd.isEmpty()||crowd.size()<2&&crowd.stream().noneMatch(VoidSuppression::compatible)){return false;}int changed=0;for(var target:crowd){if(VoidSuppression.apply(this,target,VoidSuppression.Kind.PULSE,40)){changed++;}}if(changed==0){return false;}consume(3);action("pulse",18);VoidVfx.emit(this,VoidVfx.PULSE,null,position(),Vec3.ZERO,28,(float)Config.voidValue("pulseRadius"));return true;}
    public boolean seal(ServerLevel level,LivingEntity target){if(sealTarget!=null&&level.getEntity(sealTarget) instanceof LivingEntity previous&&VoidSuppression.sealedBy(this,previous)){return false;}if(!enabled()||!ready(4)||!validTarget(target)||distanceToSqr(target)>24*24||!line(getEyePosition(),target.getEyePosition())||!VoidSuppression.apply(this,target,VoidSuppression.Kind.SEAL,tuning("sealDuration"))){return false;}sealTarget=target.getUUID();consume(4);action("seal",22);VoidVfx.emit(this,VoidVfx.SEAL,target,target.position(),Vec3.ZERO,tuning("sealDuration"),2);return true;}
    public boolean voidStep(ServerLevel level,LivingEntity target){if(!enabled()||!ready(5)||!validTarget(target)||distanceToSqr(target)>Math.pow(Config.voidValue("stepRange"),2)||!line(getEyePosition(),target.getEyePosition())){return false;}Vec3 previous=position();boolean moved=false;if(distanceToSqr(target)>3.5D*3.5D){double facing=Math.atan2(getZ()-target.getZ(),getX()-target.getX());for(int attempt=0;attempt<8;attempt++){double angle=facing+(attempt%2==0?1:-1)*(attempt+1)*Math.PI/8;Vec3 destination=target.position().add(Math.cos(angle)*2,1,Math.sin(angle)*2);if(position().distanceToSqr(destination)>Math.pow(Config.voidValue("stepRange"),2)||!VoidRescue.corridor(level,this,destination)||!rescue.move(level,this,destination,false)){continue;}moved=true;break;}if(!moved){return false;}}consume(5);meleeTicks=36;action("step",18);if(validTarget(target)&&distanceToSqr(target)<=3.5D*3.5D&&line(getEyePosition(),target.getEyePosition())){damage(level,target,damageSources().mobAttack(this),(float)(getAttributeValue(Attributes.ATTACK_DAMAGE)*Config.voidValue("stepMultiplier")));}VoidVfx.emit(this,VoidVfx.STEP,null,previous,position().subtract(previous),24,3);VoidVfx.emit(this,VoidVfx.ARRIVAL,this,position(),Vec3.ZERO,20,2);return true;}
    public boolean activateRiddenDive(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}var target=enemies(level,position(),Config.voidValue("stepRange"),16).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.3D).findFirst().orElse(null);return target!=null&&voidStep(level,target);}
    public boolean shield(ServerLevel level,LivingEntity ally){if(!enabled()||!ready(6)||!recipient(ally)||distanceToSqr(ally)>Math.pow(Config.voidValue("shieldRange"),2)||!line(getEyePosition(),ally.getEyePosition())||enemies(level,ally.position(),12,1).isEmpty()){return false;}shieldTarget=ally.getUUID();shieldTicks=tuning("shieldDuration");consume(6);action("shield",20);VoidVfx.emit(this,VoidVfx.SHIELD,ally,ally.position(),Vec3.ZERO,shieldTicks,2.5F);return true;}
    public float mitigation(LivingEntity ally){return enabled()&&shieldTicks>0&&ally.getUUID().equals(shieldTarget)&&recipient(ally)&&distanceToSqr(ally)<=32*32&&line(getEyePosition(),ally.getEyePosition())?(float)Config.voidValue("shieldMitigation"):0;}
    public int horizonTicks(){return horizonTicks;}
    public Vec3 horizonCenter(){return horizonCenter;}
    public boolean nullHorizon(ServerLevel level){if(!enabled()||!ready(7)||horizonTicks>0||enemies(level,position(),24,3).size()<3){return false;}horizonCenter=position();horizonTicks=240;consume(7);action("horizon",40);VoidVfx.emit(this,VoidVfx.HORIZON,null,horizonCenter,Vec3.ZERO,240,24);return true;}
    private void wave(ServerLevel level){for(var target:enemies(level,horizonCenter,24,32)){if(damage(level,target,damageSources().mobAttack(this),4)>0){VoidVfx.emit(this,VoidVfx.MARK,target,target.position(),Vec3.ZERO,24,1);}}action("wave",18);VoidVfx.emit(this,VoidVfx.WAVE,null,horizonCenter,Vec3.ZERO,28,24);}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}return doHurtTarget(level,target);}
    @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(meleeTicks>0){meleeTicks--;}if(actionTicks>0){actionTicks--;}if(shieldTicks>0&&--shieldTicks==0){shieldTarget=null;}if(!enabled()){if(sessionOpen||horizonTicks>0){endVoid();}return;}
        if(horizonTicks>0){if(distanceToSqr(horizonCenter)>64*64||!isLoadedFlightBox(new AABB(horizonCenter,horizonCenter).inflate(1))){endVoid();return;}int elapsed=240- --horizonTicks;if(elapsed==100||elapsed==160||elapsed==220){wave(level);}if(horizonTicks==0){VoidVfx.emit(this,VoidVfx.COLLAPSE,null,horizonCenter,Vec3.ZERO,32,24);action("collapse",24);horizonCenter=null;}}
        if(tickCount%20==0){VoidSuppression.maintain();if(validTarget(getTarget())){nullHorizon(level);antiCasterPulse(level);if(!seal(level,getTarget())){grasp(level,getTarget());}if(!isVehicle()&&distanceToSqr(getTarget())>3.5D*3.5D){voidStep(level,getTarget());}}if(horizonTicks>0){for(var target:enemies(level,horizonCenter,24,32)){VoidSuppression.apply(this,target,VoidSuppression.Kind.HORIZON,40);if(240-horizonTicks>=45){VoidSuppression.pull(this,target,horizonCenter);}}}else if(tickCount%40==0&&!enemies(level,position(),Config.voidValue("fieldRadius"),1).isEmpty()){for(var target:enemies(level,position(),Config.voidValue("fieldRadius"),16)){VoidSuppression.apply(this,target,VoidSuppression.Kind.FIELD,20);}VoidVfx.emit(this,VoidVfx.FIELD,this,position(),Vec3.ZERO,44,(float)Config.voidValue("fieldRadius"));}}
        if(!isVehicle()&&tickCount%5==0&&validTarget(getTarget())&&distanceToSqr(getTarget())<=Math.pow(Config.voidValue("boltRange"),2)&&line(getEyePosition(),getTarget().getEyePosition())){bolt(level,getTarget().getEyePosition().subtract(getEyePosition()));}
    }
    public void action(String clip,int ticks){actionTicks=ticks;visualStarted();triggerAnim("void",clip);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<VoidPhantom>("void",3,state->actionTicks>0?PlayState.CONTINUE:PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.void."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(isAlive()&&!isTame()&&!player.isSpectator()&&player.getItemInHand(hand).is(net.minecraft.world.item.Items.END_CRYSTAL)){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}tame(player);clearCombatTarget(true);level().broadcastEntityEvent(this,(byte)7);}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    public void endVoid(){if(sessionOpen&&level() instanceof ServerLevel){VoidVfx.cancel(this);session=UUID.randomUUID();sessionOpen=false;}VoidSuppression.release(this);rescue.clearAnchor();orderedId=null;shieldTarget=null;sealTarget=null;shieldTicks=0;horizonTicks=0;actionTicks=0;horizonCenter=null;}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endVoid();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endVoid();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endVoid();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endVoid();rescue.reset();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.putInt("VoidMelee",meleeTicks);for(int index=0;index<cooldowns.length;index++){output.putInt("VoidCooldown"+index,cooldowns[index]);}}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endVoid();rescue.reset();session=UUID.randomUUID();meleeTicks=Math.clamp(input.getIntOr("VoidMelee",0),0,36);for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("VoidCooldown"+index,0),0,4800);}}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){var slots=new ArrayList<CooldownDiscipline.Slot>();for(int index:new int[]{0,1,3,4,5,6}){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("void_"+CLIPS[index],interval(index),()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}
}
