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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.AncientEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.AncientVfx;

public final class AncientPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"entity/ancient_phantom");
    private static final int[] INTERVALS={200,240,500,320,800,2400};
    private static final String[] CLIPS={"strike","stone","shield","dive","sanctuary","forgotten","judgment","conclusion","mark"};
    private final int[] cooldowns=new int[6];
    private UUID session=UUID.randomUUID(),markedId,orderedId;
    private int markTicks,shieldTicks,sanctuaryTicks,forgottenTicks,diveTicks,meleeTicks,actionTicks;
    private Vec3 sanctuaryCenter,forgottenCenter,divePoint;
    private boolean sessionOpen;
    public AncientPhantom(EntityType<? extends AncientPhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,82).add(Attributes.ATTACK_DAMAGE,9).add(Attributes.ARMOR,12).add(Attributes.ARMOR_TOUGHNESS,6).add(Attributes.KNOCKBACK_RESISTANCE,.5D).add(Attributes.FOLLOW_RANGE,64).add(Attributes.MOVEMENT_SPEED,.25D).add(Attributes.FLYING_SPEED,.4D);}
    public static boolean checkSpawnRules(EntityType<AncientPhantom> type,ServerLevelAccessor level,EntitySpawnReason reason,BlockPos position,RandomSource random){return AncientEncounterSpawner.night(level.getLevel())&&AncientEncounterSpawner.habitat(level.getLevel(),position)&&level.getFluidState(position).isEmpty()&&level.getBlockState(position).getCollisionShape(level,position).isEmpty();}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.ANCIENT_PHANTOM_STATS;}
    @Override public Identifier getPhantomAnimationResource(){return ANIMATIONS;}
    @Override public int getMaxSpawnClusterSize(){return 2;}
    @Override protected int getMeleeAttackCooldownTicks(){return 38;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override public boolean isUltimateAnimationActive(){return forgottenTicks>0||super.isUltimateAnimationActive();}
    @Override public void travel(Vec3 input){if(getControllingPassenger()==null){travelFlying(input,(float)(getSpeed()*.09D));}else{super.travel(input);}}
    public UUID session(){return session;}
    public boolean enabled(){return isAlive()&&!isRemoved()&&!isBaby()&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT)&&(!isTame()||getOwner()!=null&&getOwner().isAlive()&&getOwner().level()==level());}
    public boolean ally(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&(target==this||isAlliedTo(target)||isTame()&&(target==getOwner()||target instanceof OwnableEntity owned&&Objects.equals(getOwnerReference(),owned.getOwnerReference())));}
    public boolean crew(AbstractPhantom target){return isTame()&&target.isTame()&&ally(target);}
    private boolean recent(LivingEntity attacker,LivingEntity victim){return victim!=null&&(victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100||attacker.getLastHurtMob()==victim&&attacker.tickCount-attacker.getLastHurtMobTimestamp()<100||attacker instanceof Mob mob&&mob.getTarget()==victim);}
    private boolean provoked(LivingEntity target){return recent(target,this)||recent(target,getOwner());}
    public boolean safeEnemy(LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&!target.isInvulnerable()&&!ally(target)&&!(target instanceof AbstractPhantom)&&!hasPassenger(target)&&!target.isPassengerOfSameVehicle(this)&&!isTargetSuppressed(target)&&!PhantomDuels.excludesObserver(this,target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))&&!(target instanceof Mob mob&&VampireInfluence.isHypnotized(mob))&&!(target instanceof Creeper creeper&&(creeper.isIgnited()||creeper.getSwellDir()>0));}
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target){
        if(!safeEnemy(target)){return false;}
        boolean explicit=target.getUUID().equals(orderedId)||PhantomDuels.pair(this,target)||provoked(target);
        if(target instanceof OwnableEntity owned&&owned.getOwnerReference()!=null){return explicit;}
        return explicit||isTame()&&target instanceof Enemy&&!(target instanceof NeutralMob)&&target.getType()!=EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target){return isSpeciesCombatTarget(target)&&getAssignments().permitsDefensivePursuit(target);}
    @Override protected boolean shouldHuntAutomatically(LivingEntity target,ServerLevel level){return validTarget(target)&&provoked(target);}
    @Override protected double getAutomaticTargetPriority(LivingEntity target){return priority(target);}
    private double priority(LivingEntity target){return(target.getUUID().equals(markedId)?-10000:0)-(target.getType()==EntityTypes.EVOKER?500:0)-Math.min(300,target.getMaxHealth())+distanceToSqr(target)*.01D;}
    @Override public void setCombatTarget(LivingEntity target){if(!safeEnemy(target)||!PhantomDuels.begin(this,target)){return;}orderedId=target.getUUID();super.setCombatTarget(target);}
    public boolean line(Vec3 start,Vec3 end){return Double.isFinite(start.lengthSqr())&&Double.isFinite(end.lengthSqr())&&isLoadedFlightBox(new AABB(start,end).inflate(.04D))&&level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    public List<LivingEntity> enemies(ServerLevel level,Vec3 center,double radius,int maximum){return level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),this::validTarget).stream().filter(target->target.distanceToSqr(center)<=radius*radius&&line(center.add(0,.4D,0),target.getEyePosition())).sorted(Comparator.comparingDouble(this::priority)).limit(maximum).toList();}
    public LivingEntity marked(){if(!(level() instanceof ServerLevel level)||markedId==null){return null;}if(markTicks<=0||!(level.getEntity(markedId) instanceof LivingEntity target)||!validTarget(target)||distanceToSqr(target)>64*64){AncientVfx.emit(this,AncientVfx.CLEAR_MARK,null,position(),Vec3.ZERO,1,1);markedId=null;markTicks=0;return null;}return target;}
    public boolean mark(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)||distanceToSqr(target)>64*64||!line(getEyePosition(),target.getEyePosition())){return false;}if(marked()!=null){return false;}markedId=target.getUUID();markTicks=240;action("mark",14);AncientVfx.emit(this,AncientVfx.MARK,target,target.position(),Vec3.ZERO,240,1);return true;}
    public float damage(ServerLevel level,LivingEntity target,DamageSource source,float amount,boolean runic){if(!enabled()||!validTarget(target)||!Float.isFinite(amount)||amount<=0){return 0;}float applied=amount*(runic&&target.getUUID().equals(markedId)&&markTicks>0?1.15F:1),before=target.getHealth()+target.getAbsorptionAmount();return target.hurtServer(level,source,applied)?Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()):0;}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){if(meleeTicks>0||!(target instanceof LivingEntity enemy)||!getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox())||!line(getEyePosition(),enemy.getEyePosition())||damage(level,enemy,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE),false)<=0){return false;}meleeTicks=38;action("strike",10);AncientVfx.emit(this,AncientVfx.IMPACT,null,enemy.position(),Vec3.ZERO,16,1);return true;}
    public boolean ruinStrike(ServerLevel level,LivingEntity target){
        if(!enabled()||cooldowns[0]>0||!validTarget(target)||!getBoundingBox().inflate(1).intersects(target.getBoundingBox())||!line(getEyePosition(),target.getEyePosition())||damage(level,target,damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*1.8F,true)<=0){return false;}
        cooldowns[0]=200;meleeTicks=38;action("strike",14);if(!AncientControl.controlImmune(target)){target.knockback(.8D,getX()-target.getX(),getZ()-target.getZ(),damageSources().mobAttack(this),0);}AncientVfx.emit(this,AncientVfx.STRIKE,null,target.position(),target.position().subtract(position()),22,3);
        if(getRandom().nextInt(4)==0){for(LivingEntity enemy:enemies(level,target.position(),2,5)){if(enemy!=target){damage(level,enemy,damageSources().mobAttack(this),3,true);}}AncientVfx.emit(this,AncientVfx.FRAGMENTS,null,target.position(),Vec3.ZERO,20,2);}return true;
    }
    public boolean stone(ServerLevel level,Vec3 aim){
        int interval=getControllingPassenger() instanceof Player?6:240;
        if(!enabled()||cooldowns[1]>240-interval||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.01D){return false;}Vec3 direction=aim.normalize(),muzzle=getEyePosition().add(direction.scale(.8D));if(!line(getEyePosition(),muzzle)){return false;}
        AncientStone shot=PhantomismContent.ANCIENT_STONE.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null){return false;}shot.setOwner(this);shot.configure(session);shot.setPos(muzzle);shot.shoot(direction.x,direction.y,direction.z,2F,0);if(!level.addFreshEntity(shot)){return false;}
        cooldowns[1]=240;action("stone",12);AncientVfx.emit(this,AncientVfx.STONE,shot,muzzle,direction,36,.7F);return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return 6;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){return stone(level,rider.getViewVector(1));}
    public void stoneImpact(ServerLevel level,Vec3 center){for(LivingEntity target:enemies(level,center,3,12)){if(damage(level,target,damageSources().mobAttack(this),10,true)>0&&!AncientControl.controlImmune(target)){target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,40,0),this);}}AncientVfx.emit(this,AncientVfx.IMPACT,null,center,Vec3.ZERO,24,3);}
    public boolean ruinShield(ServerLevel level){if(!enabled()||cooldowns[2]>0||shieldTicks>0||!isTame()||enemies(level,position(),24,1).isEmpty()){return false;}shieldTicks=240;cooldowns[2]=500;action("shield",22);AncientVfx.emit(this,AncientVfx.SHIELD,this,position(),Vec3.ZERO,240,16);return true;}
    public boolean sanctuary(ServerLevel level){if(!enabled()||cooldowns[4]>0||sanctuaryTicks>0||!isTame()||level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(18),target->ally(target)&&target.getHealth()<target.getMaxHealth()).isEmpty()){return false;}sanctuaryCenter=position();sanctuaryTicks=300;cooldowns[4]=800;action("sanctuary",24);AncientVfx.emit(this,AncientVfx.SANCTUARY,null,sanctuaryCenter,Vec3.ZERO,300,18);return true;}
    public boolean protects(LivingEntity target){return enabled()&&isTame()&&(target==getOwner()||target==this||target instanceof AbstractPhantom phantom&&crew(phantom))&&(shieldTicks>0&&distanceToSqr(target)<=16*16&&line(getEyePosition(),target.getEyePosition())||sanctuaryTicks>0&&target.distanceToSqr(sanctuaryCenter)<=18*18&&distanceToSqr(sanctuaryCenter)<=64*64&&line(sanctuaryCenter.add(0,1,0),target.getEyePosition())||forgottenTicks>0&&target.distanceToSqr(forgottenCenter)<=35*35&&line(forgottenCenter.add(0,1,0),target.getEyePosition()));}
    public boolean empowers(AbstractPhantom ally,LivingEntity target){return enabled()&&forgottenTicks>0&&ally!=this&&crew(ally)&&validTarget(target)&&ally.distanceToSqr(forgottenCenter)<=35*35&&target.distanceToSqr(forgottenCenter)<=35*35&&line(forgottenCenter.add(0,1,0),ally.getEyePosition());}
    public boolean dive(ServerLevel level,LivingEntity target){
        if(!enabled()||cooldowns[3]>0||!validTarget(target)||distanceToSqr(target)>16*16||!line(getEyePosition(),target.getEyePosition())){return false;}
        Vec3 from=target.position().add(0,3,0),to=target.position().add(0,-24,0);if(!isLoadedFlightBox(new AABB(from,to))){return false;}BlockHitResult floor=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(floor.getType()!=HitResult.Type.BLOCK||floor.getDirection()!=net.minecraft.core.Direction.UP||!level.getFluidState(floor.getBlockPos()).isEmpty()){return false;}
        divePoint=floor.getLocation().add(0,.2D,0);diveTicks=30;cooldowns[3]=320;action("dive",24);AncientVfx.emit(this,AncientVfx.DIVE,this,position(),divePoint.subtract(position()),30,3);return true;
    }
    public boolean activateRiddenDive(Player rider){if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}LivingEntity target=enemies(level,position(),16,32).stream().filter(enemy->rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize())>.5D).findFirst().orElse(null);return target!=null&&(ruinStrike(level,target)||dive(level,target));}
    private void tickDive(ServerLevel level){
        if(diveTicks<=0){return;}if(--diveTicks==0||!isLoadedFlightBox(new AABB(divePoint,divePoint).inflate(1))){diveTicks=0;divePoint=null;return;}
        Vec3 direction=divePoint.subtract(position()),step=direction.normalize().scale(Math.min(.9D,direction.length()));
        if(direction.lengthSqr()<=2.25D||verticalCollisionBelow&&Math.abs(direction.x)<2&&Math.abs(direction.z)<2){
            Vec3 center=position();for(LivingEntity target:enemies(level,center,7,12)){if(damage(level,target,damageSources().mobAttack(this),12,true)>0){AncientControl.stun(this,target,20);}}AncientVfx.emit(this,AncientVfx.QUAKE,null,center,Vec3.ZERO,30,7);diveTicks=0;divePoint=null;
        }else if(!isVehicle()){AABB swept=getBoundingBox().expandTowards(step);if(isLoadedFlightBox(swept)&&level.noCollision(this,swept)&&!level.containsAnyLiquid(swept)){setDeltaMovement(step);}else{diveTicks=0;divePoint=null;}}
    }
    public int forgottenTicks(){return forgottenTicks;}
    public Vec3 forgottenCenter(){return forgottenCenter;}
    public boolean forgottenLand(ServerLevel level){if(!enabled()||cooldowns[5]>0||forgottenTicks>0||enemies(level,position(),35,4).size()<4){return false;}forgottenCenter=position();forgottenTicks=280;cooldowns[5]=2400;action("forgotten",36);AncientVfx.emit(this,AncientVfx.FORGOTTEN,null,forgottenCenter,Vec3.ZERO,280,35);return true;}
    private void judgment(ServerLevel level){for(LivingEntity target:enemies(level,forgottenCenter,35,12)){if(damage(level,target,damageSources().mobAttack(this),20,true)>0){AncientControl.stun(this,target,20);AncientVfx.emit(this,AncientVfx.JUDGMENT,target,target.position(),Vec3.ZERO,28,2);}}action("judgment",16);}
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){if(!enabled()||!validTarget(target)){return true;}if(diveTicks>0||ruinStrike(level,target)||distanceToSqr(target)<=16*16&&dive(level,target)){return true;}if(doHurtTarget(level,target)){return true;}return false;}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level)){return;}for(int index=0;index<cooldowns.length;index++){if(cooldowns[index]>0){cooldowns[index]--;}}if(meleeTicks>0){meleeTicks--;}if(actionTicks>0){actionTicks--;}
        if(!enabled()){endAncient();return;}if(markTicks>0){markTicks--;}marked();if(shieldTicks>0){shieldTicks--;}
        if(sanctuaryTicks>0&&(--sanctuaryTicks==0||distanceToSqr(sanctuaryCenter)>64*64)){sanctuaryTicks=0;sanctuaryCenter=null;}
        if(forgottenTicks>0){if(distanceToSqr(forgottenCenter)>64*64||!isLoadedFlightBox(new AABB(forgottenCenter,forgottenCenter).inflate(1))){endAncient();return;}int elapsed=280- --forgottenTicks;if(elapsed==100||elapsed==160||elapsed==220){judgment(level);}if(forgottenTicks==0){AncientVfx.emit(this,AncientVfx.CONCLUSION,null,forgottenCenter,Vec3.ZERO,30,35);action("conclusion",20);forgottenCenter=null;}}
        tickDive(level);
        if(tickCount%20==0){
            AncientControl.maintain();if(marked()==null){List<LivingEntity> threats=enemies(level,position(),64,1);if(!threats.isEmpty()){mark(level,threats.getFirst());}}AncientControl.coordinate(this,marked());
            if(validTarget(getTarget())){ruinShield(level);forgottenLand(level);}sanctuary(level);
            if(sanctuaryTicks>0){for(LivingEntity friend:level.getEntitiesOfClass(LivingEntity.class,new AABB(sanctuaryCenter,sanctuaryCenter).inflate(18),this::ally).stream().filter(friend->friend.distanceToSqr(sanctuaryCenter)<=18*18&&line(sanctuaryCenter.add(0,1,0),friend.getEyePosition())).limit(32).toList()){AncientControl.heal(this,friend);}for(LivingEntity target:enemies(level,sanctuaryCenter,18,24)){if(!AncientControl.controlImmune(target)){target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,40,0),this);}}}
        }
        if(!isVehicle()&&tickCount%5==0&&validTarget(getTarget())&&distanceToSqr(getTarget())<=42*42&&line(getEyePosition(),getTarget().getEyePosition())){stone(level,getTarget().getEyePosition().subtract(getEyePosition()));}
    }
    private void action(String clip,int ticks){actionTicks=ticks;sessionOpen=true;triggerAnim("ancient",clip);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){super.registerControllers(controllers);var controller=new AnimationController<AncientPhantom>("ancient",3,state->actionTicks>0?PlayState.CONTINUE:PlayState.STOP);for(String clip:CLIPS){controller.triggerableAnim(clip,RawAnimation.begin().thenPlay("animation.ancient."+clip));}controllers.add(controller);}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){if(isAlive()&&!isTame()&&!player.isSpectator()&&player.getItemInHand(hand).is(preferredTaming().item())){if(!level().isClientSide()){if(!player.isCreative()){player.getItemInHand(hand).shrink(1);}tame(player);clearCombatTarget(true);level().broadcastEntityEvent(this,(byte)7);}return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    public void endAncient(){if(sessionOpen&&level() instanceof ServerLevel){AncientVfx.cancel(this);session=UUID.randomUUID();sessionOpen=false;}AncientControl.release(this);markedId=null;orderedId=null;markTicks=0;shieldTicks=0;sanctuaryTicks=0;forgottenTicks=0;diveTicks=0;actionTicks=0;sanctuaryCenter=null;forgottenCenter=null;divePoint=null;}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){endAncient();}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){endAncient();}}
    @Override public void setTask(PhantomAssignments.Task task){super.setTask(task);if(cooldowns!=null&&task!=PhantomAssignments.Task.NONE&&task!=PhantomAssignments.Task.HUNT){endAncient();}}
    @Override public void remove(RemovalReason reason){if(cooldowns!=null){endAncient();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.putInt("AncientMelee",meleeTicks);for(int index=0;index<cooldowns.length;index++){output.putInt("AncientCooldown"+index,cooldowns[index]);}}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);endAncient();session=UUID.randomUUID();meleeTicks=Math.clamp(input.getIntOr("AncientMelee",0),0,38);for(int index=0;index<cooldowns.length;index++){cooldowns[index]=Math.clamp(input.getIntOr("AncientCooldown"+index,0),0,INTERVALS[index]);}}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){var slots=new ArrayList<CooldownDiscipline.Slot>();for(int index=0;index<5;index++){final int slot=index;slots.add(CooldownDiscipline.Slot.normal("ancient_"+CLIPS[index],INTERVALS[index],()->cooldowns[slot],value->cooldowns[slot]=value));}return List.copyOf(slots);}

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(PhantomismContent.ANCIENT_RELIC.get(), 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Strike: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Stone: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Shield: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Dive: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Sanctuary: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Forgotten: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "ruin_strike" -> cooldowns[0];
            case "stone" -> cooldowns[1];
            case "ruin_shield" -> cooldowns[2];
            case "sanctuary" -> cooldowns[4];
            case "dive" -> cooldowns[3];
            case "forgotten_land" -> cooldowns[5];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[1] = ticks; }
}
