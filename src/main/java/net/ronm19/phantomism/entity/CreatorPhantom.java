package net.ronm19.phantomism.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.PhantomHornRally;
import net.ronm19.phantomism.entity.ai.CreatorSupport;
import net.ronm19.phantomism.entity.ai.CreatorAdaptiveBeam;
import net.ronm19.phantomism.entity.ai.VampireInfluence;

import net.ronm19.phantomism.entity.spawn.HalloweenEncounterSpawner;

import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.CreatorVfx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
public final class CreatorPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final int BEAM_DURATION=60, MOUNTED_BEAM_INTERVAL=4, EVOLUTION_DURATION=240;
    private static final int[] INTERVALS={120,160,500,240,160,600,2400};
    private static final String[] KEYS={"pulse","step","aegis","genesis","beam","instinct","evolution"};
    private static final EntityDataAccessor<Boolean> SCRIPTED=SynchedEntityData.defineId(CreatorPhantom.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DASHING=SynchedEntityData.defineId(CreatorPhantom.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEFEATED=SynchedEntityData.defineId(CreatorPhantom.class,EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns=new int[7];
    private final CreatorAdaptiveBeam beams=new CreatorAdaptiveBeam(this);
    private final Set<UUID> dashHits=new HashSet<>();
    private final Map<UUID,Float> evolutionDamage=new HashMap<>(),evolutionHealing=new HashMap<>();
    private UUID session=UUID.randomUUID(),orderedTarget,sequenceRider,ownerContext,manifestedFor;
    private Vec3 dashDirection=Vec3.ZERO,evolutionCenter,safeAnchor;
    private int dashTicks,aegisTicks,instinctTicks,evolutionTicks,recoveryTicks;
    private long nextCombatDecision,defeatedAtTick;
    private double dashTravelled;
    private boolean explicitSelection,previousNoGravity;
    private String lastDecision="idle";
    public CreatorPhantom(EntityType<? extends CreatorPhantom> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,360).add(Attributes.ATTACK_DAMAGE,24).add(Attributes.ARMOR,20).add(Attributes.ARMOR_TOUGHNESS,8).add(Attributes.KNOCKBACK_RESISTANCE,.65D).add(Attributes.FOLLOW_RANGE,64).add(Attributes.MOVEMENT_SPEED,.3D).add(Attributes.FLYING_SPEED,.52D);}
    public static boolean checkSpawnRules(EntityType<CreatorPhantom> type,ServerLevelAccessor level,EntitySpawnReason reason,BlockPos pos,RandomSource random){return reason!=EntitySpawnReason.NATURAL&&reason!=EntitySpawnReason.CHUNK_GENERATION;}
    @Override protected Config.PhantomStats getConfiguredStats(){return Config.CREATOR_PHANTOM_STATS;}
    @Override protected boolean preservesCombatTargetWhileRidden(){return true;}
    @Override public byte getPhantomAnimationFlags(){return (byte)(super.getPhantomAnimationFlags()|(entityData.get(DASHING)?1:0));}
    @Override public boolean isUltimateAnimationActive(){return evolutionTicks>0||super.isUltimateAnimationActive();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(SCRIPTED,false);data.define(DASHING,false);data.define(DEFEATED,false);}
    public boolean isRecovering(){return entityData.get(DEFEATED);}
    public int recoveryTicks(){return recoveryTicks;}
    @Override public boolean isClientAuthoritative(){return !isRecovering()&&!entityData.get(SCRIPTED)&&super.isClientAuthoritative();}
    @Override protected boolean isLocalClientAuthoritative(){return !isRecovering()&&!entityData.get(SCRIPTED)&&super.isLocalClientAuthoritative();}
    @Override public void travel(Vec3 input){if(isRecovering()||entityData.get(SCRIPTED)){setDeltaMovement(Vec3.ZERO);return;}super.travel(input);}
    @Override public boolean canAddPassenger(Entity entity){return !isRecovering()&&super.canAddPassenger(entity);}
    @Override public boolean isFood(net.minecraft.world.item.ItemStack stack){return false;}
    @Override public boolean canMate(net.minecraft.world.entity.animal.Animal partner){return false;}
    @Override public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel level,net.minecraft.world.entity.AgeableMob partner){return null;}
    @Override public net.ronm19.phantomism.codex.TamingProfile preferredTaming(){return new net.ronm19.phantomism.codex.TamingProfile(PhantomismContent.EYE_OF_CREATION.get(),1,1,"Eye of Creation: End dimension after Free the End. One active Aethar per player.");}
    @Override public java.util.Optional<net.minecraft.world.item.Item> preferredLure(){return java.util.Optional.empty();}
    public UUID manifestedFor(){return manifestedFor;}
    public void manifestedFor(UUID player){manifestedFor=player;}
    @Override public net.minecraft.network.chat.Component getName(){return !level().isClientSide()&&hasCustomName()?super.getName():net.ronm19.phantomism.progression.CreatorRevelation.entityName(level());}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!isTame()&&player.getItemInHand(hand).is(PhantomismContent.EYE_OF_CREATION.get())){
            return player instanceof ServerPlayer server?net.ronm19.phantomism.progression.CreatorSummoning.bind(server,this,hand):InteractionResult.SUCCESS;
        }
        return !isTame()||isRecovering()?InteractionResult.PASS:super.mobInteract(player,hand);
    }
    public UUID session(){return session;}
    public int cooldown(int slot){return cooldowns[slot];}
    public int dashTicks(){return dashTicks;}
    public int aegisTicks(){return aegisTicks;}
    public int evolutionTicks(){return evolutionTicks;}
    public int instinctTicks(){return instinctTicks;}
    public CreatorAdaptiveBeam beams(){return beams;}
    public String lastDecision(){return lastDecision;}
    public boolean companionAvailable(){return isAlive()&&!isRemoved()&&!isBaby()&&!isRecovering()&&(!isTame()||getOwner() instanceof ServerPlayer owner&&owner.isAlive()&&owner.level()==level()&&level().getServer().getPlayerList().getPlayer(owner.getUUID())==owner);}
    public boolean enabled(){return companionAvailable()&&!PhantomHornRally.returning(this)&&getOrder()!=Order.STAY&&getOrder()!=Order.RETURN_HOME&&(getTask()==PhantomAssignments.Task.NONE||getTask()==PhantomAssignments.Task.HUNT);}

    public boolean ally(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && (target == this || isTame() && (target == getOwner()
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())
                || CelestialBond.authorizedAlly(this, target) || !(target instanceof Enemy) && isAlliedTo(target)));
    }
    public List<LivingEntity> allies(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::ally).stream()
                .filter(target -> distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition()))
                .sorted(Comparator.comparingDouble((LivingEntity target) -> target.getHealth() / Math.max(1, target.getMaxHealth()) - (target == getOwner() ? .1D : 0)))
                .limit(16).toList();
    }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !ally(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
                && (explicitSelection || !isTargetSuppressed(target)) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean attacking(LivingEntity target, LivingEntity ward) {
        return ward != null && (ward.getLastHurtByMob() == target && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100
                || target instanceof Mob mob && mob.getTarget() == ward);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (target instanceof Enemy || target.getUUID().equals(orderedTarget)
                || attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && ally(mob.getTarget()));
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override public void setCombatTarget(LivingEntity target) {
        explicitSelection = true;
        try { if (safeEnemy(target)) { orderedTarget = target.getUUID(); super.setCombatTarget(target); } }
        finally { explicitSelection = false; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return enabled() && validTarget(target) && (attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && ally(mob.getTarget()))
                && line(getEyePosition(), target.getEyePosition());
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.1D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .5D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble((LivingEntity target) -> (attacking(target, getOwner()) ? -10000D : target == getTarget() ? -5000D : 0) + target.distanceToSqr(center))).limit(128).toList();
    }
    @Override public void die(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            if (getOwnerReference() != null && level() instanceof ServerLevel world) { net.ronm19.phantomism.progression.CreatorBonds.get(world.getServer()).release(getOwnerReference().getUUID(), getUUID()); }
            recoveryTicks = 0;
            entityData.set(DEFEATED, false);
            onDefeated();
            super.die(source);
            return;
        }
        if (isRemoved() || !(level() instanceof ServerLevel level)) { return; }
        if (isRecovering()) { setHealth(1); return; }
        previousNoGravity = isNoGravity();
        recoveryTicks = 40;
        defeatedAtTick = level.getGameTime();
        entityData.set(DEFEATED, true);
        syncRecovery(true);
        setHealth(1);
        deathTime = 0;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        onDefeated();
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + .4D, getZ(), 10, .7D, .3D, .7D, .02D);
    }

    private void tickRecovery() {
        if (level() instanceof ServerLevel level && isRecovering()) {
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
            if (level.getGameTime() > defeatedAtTick && recoveryTicks > 0 && --recoveryTicks == 0) {
                if (getY() < level.getMinY() + 2 && safeAnchor != null) {
                    AABB destination = getBoundingBox().move(safeAnchor.subtract(position()));
                    if (isLoadedFlightBox(destination) && level.noCollision(this, destination) && !level.containsAnyLiquid(destination)) { setPos(safeAnchor); needsSync = true; }
                }
                setHealth(getMaxHealth());
                clearFire();
                setNoGravity(previousNoGravity);
                entityData.set(DEFEATED, false);
                syncRecovery(false);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + .5D, getZ(), 12, .7D, .4D, .7D, .02D);
            }
        }

        if (level() instanceof ServerLevel level && !isRecovering() && isAlive() && tickCount % 20 == 0
                && getY() > level.getMinY() + 4 && getY() < level.getMaxY() - 4 && isLoadedFlightBox(getBoundingBox())
                && level.noBlockCollision(this, getBoundingBox()) && !level.containsAnyLiquid(getBoundingBox())) { safeAnchor = position(); }
    }

    public boolean strike(ServerLevel level,LivingEntity target,float amount){return enabled()&&validTarget(target)&&line(getEyePosition(),target.getEyePosition())&&target.hurtServer(level,damageSources().indirectMagic(this,this),amount);}
    private boolean busy(){return dashTicks>0||evolutionTicks>0||beams.active();}
    public boolean pulse(ServerLevel level){
        if(!enabled()||cooldowns[0]>0||busy()){return false;}var targets=enemies(level,position(),8);if(targets.isEmpty()){return false;}
        cooldowns[0]=INTERVALS[0];for(var target:targets){if(strike(level,target,12)){target.knockback(.45D,getX()-target.getX(),getZ()-target.getZ(),damageSources().indirectMagic(this,this),12);CreatorSupport.interrupt(this,target,20);}}
        animate(PhantomAnimationAction.EMPOWER);CreatorVfx.emit(this,CreatorVfx.PULSE,null,position(),Vec3.ZERO,28,8);playSound(SoundEvents.BEACON_ACTIVATE,.5F,1.4F);return true;
    }
    public boolean genesis(ServerLevel level,LivingEntity target){
        if(!enabled()||cooldowns[3]>0||busy()||!validTarget(target)||distanceToSqr(target)>1024||!line(getEyePosition(),target.getEyePosition())){return false;}
        cooldowns[3]=INTERVALS[3];strike(level,target,28);target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,30,0),this);animate(PhantomAnimationAction.PROJECTILE);
        CreatorVfx.emit(this,CreatorVfx.GENESIS,target,target.position(),getEyePosition().subtract(target.position()),24,2);playSound(SoundEvents.BEACON_POWER_SELECT,.45F,1.2F);return true;
    }
    public boolean aegis(ServerLevel level){
        if(!enabled()||cooldowns[2]>0||aegisTicks>0||allies(12).isEmpty()){return false;}cooldowns[2]=INTERVALS[2];aegisTicks=160;refreshAegis();animate(PhantomAnimationAction.BLESSING);CreatorVfx.emit(this,CreatorVfx.AEGIS,this,position(),Vec3.ZERO,160,12);return true;
    }
    private void refreshAegis(){for(var friend:allies(12)){CreatorSupport.protect(this,friend);}}
    public boolean instinct(ServerLevel level){
        if(!enabled()||cooldowns[5]>0||instinctTicks>0){return false;}cooldowns[5]=INTERVALS[5];instinctTicks=160;nextCombatDecision=0;coordinate(level);CreatorVfx.emit(this,CreatorVfx.INSTINCT,this,position(),Vec3.ZERO,60,4);return true;
    }
    public boolean adaptiveBeam(ServerLevel level){
        if(!enabled()||cooldowns[4]>0||busy()){return false;}var selected=enemies(level,position(),Config.creatorBeamRange()).stream().limit(Config.creatorBeamLimit()).toList();if(selected.isEmpty()){return false;}
        if(!beams.start(level,selected)){return false;}cooldowns[4]=INTERVALS[4];animate(PhantomAnimationAction.PROJECTILE);playSound(SoundEvents.BEACON_AMBIENT,.4F,1.45F);return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks(){return MOUNTED_BEAM_INTERVAL;}
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level,Player rider){
        if(getControllingPassenger()!=rider||!isOwnedBy(rider)||!enabled()||cooldowns[4]>0||dashTicks>0||evolutionTicks>0){return false;}
        var selected=enemies(level,position(),Config.creatorBeamRange()).stream().limit(Config.creatorBeamLimit()).toList();
        boolean continuing=beams.active();
        // Held G refreshes one channel. It must never restart the damage clock or stack channels.
        if(!(continuing?beams.refresh(level,selected):beams.start(level,selected))){return false;}
        cooldowns[4]=MOUNTED_BEAM_INTERVAL;
        if(!continuing){animate(PhantomAnimationAction.PROJECTILE);playSound(SoundEvents.BEACON_AMBIENT,.4F,1.45F);}
        return true;
    }
    public boolean activateRiddenDash(Player rider){
        if(!(level() instanceof ServerLevel level)||getControllingPassenger()!=rider||!isOwnedBy(rider)){return false;}
        LivingEntity target=enemies(level,position(),18).stream().filter(enemy->enemy.getEyePosition().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1))>.65D).findFirst().orElse(null);
        return startStep(level,target,rider.getViewVector(1));
    }
    public boolean step(ServerLevel level,LivingEntity target){return startStep(level,target,target==null?getViewVector(1):target.position().subtract(position()));}
    private boolean startStep(ServerLevel level,LivingEntity target,Vec3 aim){
        if(!enabled()||cooldowns[1]>0||busy()||!Double.isFinite(aim.lengthSqr())||aim.lengthSqr()<.01D||target!=null&&(!validTarget(target)||distanceToSqr(target)>400||!line(getEyePosition(),target.getEyePosition()))){return false;}
        Vec3 direction=aim.normalize();if(!safeStep(level,direction.scale(.9D),false)){return false;}
        cooldowns[1]=INTERVALS[1];dashTicks=12;dashTravelled=0;dashDirection=direction;dashHits.clear();sequenceRider=getControllingPassenger()==null?null:getControllingPassenger().getUUID();scripted(true);entityData.set(DASHING,true);animate(PhantomAnimationAction.CHARGE);
        CreatorVfx.emit(this,CreatorVfx.STEP,this,position(),Vec3.ZERO,22,3);return true;
    }
    private void scripted(boolean active){
        boolean before=entityData.get(SCRIPTED);entityData.set(SCRIPTED,active);if(before==active){return;}setDeltaMovement(Vec3.ZERO);needsSync=true;
        if(level() instanceof ServerLevel&&getControllingPassenger() instanceof ServerPlayer rider&&rider.connection!=null){rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(),List.of(SynchedEntityData.DataValue.create(SCRIPTED,active))));if(!active){rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this));}}
    }
    private boolean riderChanged(){return !Objects.equals(sequenceRider,getControllingPassenger()==null?null:getControllingPassenger().getUUID());}
    private boolean safeStep(ServerLevel level,Vec3 delta,boolean move){
        AABB path=getBoundingBox().expandTowards(delta),destination=getBoundingBox().move(delta);
        if(!isLoadedFlightBox(path)||!level.getWorldBorder().isWithinBounds(destination)||destination.minY<=level.getMinY()||destination.maxY>=level.getMaxY()||!level.noBlockCollision(this,path)||level.containsAnyLiquid(path)){return false;}
        for(Entity passenger:getPassengers()){AABB box=passenger.getBoundingBox().expandTowards(delta);if(!level.noBlockCollision(passenger,box)||level.containsAnyLiquid(box)){return false;}}
        if(move){setDeltaMovement(Vec3.ZERO);move(MoverType.SELF,delta);needsSync=true;for(Entity passenger:getPassengers()){positionRider(passenger);}}return true;
    }
    private void tickStep(ServerLevel level){
        if(dashTicks<=0){return;}if(riderChanged()||dashTravelled>=16||!safeStep(level,dashDirection.scale(1.2D),true)){stopStep();return;}dashTravelled+=1.2D;
        for(var target:enemies(level,position(),2)){if(dashHits.size()<12&&dashHits.add(target.getUUID())){strike(level,target,16);}}
        if(--dashTicks==0){stopStep();}
    }
    private void stopStep(){dashTicks=0;dashHits.clear();entityData.set(DASHING,false);if(evolutionTicks==0){scripted(false);}}
    public boolean evolution(ServerLevel level){
        if(!enabled()||cooldowns[6]>0||busy()||enemies(level,position(),24).isEmpty()){return false;}
        cooldowns[6]=INTERVALS[6];evolutionTicks=EVOLUTION_DURATION;evolutionCenter=position();evolutionDamage.clear();evolutionHealing.clear();sequenceRider=getControllingPassenger()==null?null:getControllingPassenger().getUUID();scripted(true);animate(PhantomAnimationAction.ULTIMATE);
        CreatorVfx.emit(this,CreatorVfx.EVOLUTION,this,position(),Vec3.ZERO,260,24);playSound(SoundEvents.BEACON_ACTIVATE,.6F,.75F);return true;
    }
    private void tickEvolution(ServerLevel level){
        if(evolutionTicks<=0){return;}if(riderChanged()||evolutionCenter==null||distanceToSqr(evolutionCenter)>1024){cancelCombat();return;}
        int elapsed=EVOLUTION_DURATION-evolutionTicks;if(elapsed<16){safeStep(level,new Vec3(0,.06D,0),true);}
        if(elapsed>=35&&(elapsed-35)%40==0||elapsed==235){
            for(var target:enemies(level,evolutionCenter,24).stream().limit(32).toList()){
                float amount=Math.min(elapsed==235?12:8,52-evolutionDamage.getOrDefault(target.getUUID(),0F));
                if(amount>0&&strike(level,target,amount)){evolutionDamage.merge(target.getUUID(),amount,Float::sum);target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,40,0),this);}
            }
            for(var friend:allies(24)){float amount=Math.min(4,16-evolutionHealing.getOrDefault(friend.getUUID(),0F));if(amount>0&&CreatorSupport.heal(this,friend,amount)){evolutionHealing.merge(friend.getUUID(),amount,Float::sum);}}
            CreatorVfx.emit(this,elapsed==235?CreatorVfx.FINAL:CreatorVfx.WAVE,null,evolutionCenter,Vec3.ZERO,35,24);
        }
        if(evolutionTicks%10==0){for(var friend:allies(24)){CreatorSupport.protect(this,friend);}}
        if(--evolutionTicks==0){evolutionCenter=null;evolutionDamage.clear();evolutionHealing.clear();scripted(false);}
    }
    private void coordinate(ServerLevel level){
        if(!isTame()){return;}LivingEntity threat=enemies(level,position(),24).stream().filter(t->attacking(t,getOwner())).findFirst().orElse(null);if(threat==null){return;}
        for(var friend:allies(16)){if(friend instanceof AbstractPhantom phantom&&phantom!=this&&!phantom.isBaby()&&!phantom.isVehicle()&&phantom.getOrder()==Order.FOLLOW&&phantom.getTask()==PhantomAssignments.Task.NONE&&phantom.getTarget()==null&&phantom.canAttack(threat)){phantom.setCombatTarget(threat);}}
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level,LivingEntity target){
        if(!enabled()||busy()){return busy();}if(level.getGameTime()<nextCombatDecision){return false;}nextCombatDecision=level.getGameTime()+(instinctTicks>0?5:10);
        var threats=enemies(level,position(),Config.creatorBeamRange());var owner=getOwner();boolean danger=owner!=null&&(owner.getHealth()<owner.getMaxHealth()*.4F||threats.stream().anyMatch(t->attacking(t,owner)));
        if(danger){coordinate(level);if(aegis(level)){lastDecision="owner_rescue_aegis";return true;}if(owner!=null&&distanceToSqr(owner)>144&&startStep(level,null,owner.position().subtract(position()))){lastDecision="owner_rescue_step";return true;}}
        if(getHealth()<getMaxHealth()*.3F&&!threats.isEmpty()&&startStep(level,null,position().subtract(threats.getFirst().position()))){lastDecision="defensive_step";return true;}
        if(threats.size()>=3&&instinct(level)){lastDecision="instinct_coordination";return true;}
        if((threats.size()>=6||danger&&threats.size()>=2)&&evolution(level)){lastDecision="crowd_evolution";return true;}
        if(threats.size()>=2&&adaptiveBeam(level)){lastDecision="multiplying_beams";return true;}
        if(enemies(level,position(),8).size()>=2&&pulse(level)){lastDecision="close_pulse";return true;}
        if(!validTarget(target)){return false;}if(distanceToSqr(target)>1024&&step(level,target)){lastDecision="approach_step";return true;}
        if(genesis(level,target)){lastDecision="priority_genesis";return true;}if(adaptiveBeam(level)){lastDecision="precision_beam";return true;}return false;
    }
    @Override protected void customServerAiStep(ServerLevel level){if(isRecovering()){getNavigation().stop();setDeltaMovement(Vec3.ZERO);return;}super.customServerAiStep(level);}
    @Override public void tick(){
        tickRecovery();super.tick();if(!(level() instanceof ServerLevel level)){return;}
        for(int i=0;i<cooldowns.length;i++){cooldowns[i]=Math.max(0,cooldowns[i]-1);}UUID context=getOwnerReference()==null?getUUID():getOwnerReference().getUUID();if(ownerContext!=null&&!ownerContext.equals(context)){cancelCombat();}ownerContext=context;
        if(isAlive()&&!isRemoved()&&isTame()&&tickCount%20==0&&!net.ronm19.phantomism.progression.CreatorBonds.get(level.getServer()).claim(context,getUUID())){discard();return;}
        if(isAlive()&&!isRemoved()&&isTame()&&tickCount%20==0&&getOwner() instanceof ServerPlayer owner&&!net.ronm19.phantomism.progression.CreatorRevelation.revealed(owner)){net.ronm19.phantomism.progression.CreatorRevelation.reveal(owner);}
        if(!companionAvailable()){CreatorSupport.release(this);}else if(isTame()&&tickCount%20==0){for(var friend:allies(12)){if(friend instanceof AbstractPhantom){CreatorSupport.presence(this,friend);}}if(tickCount%100==0){CreatorVfx.emit(this,CreatorVfx.PRESENCE,this,position(),Vec3.ZERO,24,1);}}
        if(!enabled()){if(busy()||aegisTicks>0||instinctTicks>0){cancelCombat();}return;}
        tickStep(level);beams.tick(level);tickEvolution(level);if(instinctTicks>0){instinctTicks--;if(tickCount%20==0){coordinate(level);}}
        if(aegisTicks>0){if(aegisTicks%10==0){refreshAegis();}if(--aegisTicks==0){CreatorSupport.releaseAegis(this);}}
        if(!isNoAi()&&!isVehicle()&&getTarget()==null&&tickCount%20==0&&getOwner()!=null&&getOwner().getHealth()<getOwner().getMaxHealth()*.4F&&!enemies(level,position(),16).isEmpty()){aegis(level);}
    }
    public void cancelCombat(){if(level() instanceof ServerLevel){CreatorVfx.cancel(this);}beams.stop();CreatorSupport.release(this);session=UUID.randomUUID();dashTicks=aegisTicks=evolutionTicks=instinctTicks=0;dashHits.clear();evolutionDamage.clear();evolutionHealing.clear();evolutionCenter=null;entityData.set(DASHING,false);scripted(false);}
    private void onDefeated(){cancelCombat();if(level() instanceof ServerLevel){CreatorVfx.emit(this,CreatorVfx.DEFEAT,this,position(),Vec3.ZERO,40,2);}}
    @Override public boolean doHurtTarget(ServerLevel level,Entity target){return !isRecovering()&&super.doHurtTarget(level,target);}
    @Override public boolean hurtServer(ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){return (!isRecovering()||source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))&&super.hurtServer(level,source,amount);}
    private void syncRecovery(boolean recovering){if(level() instanceof ServerLevel&&getControllingPassenger() instanceof ServerPlayer rider&&rider.connection!=null){rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(),List.of(SynchedEntityData.DataValue.create(DEFEATED,recovering))));if(!recovering){rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this));}}}
    @Override public void clearCombatTarget(boolean suppress){super.clearCombatTarget(suppress);if(suppress&&cooldowns!=null){cancelCombat();orderedTarget=null;}}
    @Override public void setOrder(Order order){super.setOrder(order);if(cooldowns!=null&&(order==Order.STAY||order==Order.RETURN_HOME)){cancelCombat();orderedTarget=null;}}
    @Override public void remove(RemovalReason reason){if(manifestedFor!=null&&(reason==RemovalReason.KILLED||reason==RemovalReason.DISCARDED)&&level() instanceof ServerLevel level){net.ronm19.phantomism.progression.CreatorBonds.get(level.getServer()).releaseManifestation(manifestedFor,getUUID());}if(cooldowns!=null){cancelCombat();}super.remove(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);if(manifestedFor!=null){output.putString("CreatorManifestedFor",manifestedFor.toString());}for(int slot=0;slot<cooldowns.length;slot++){output.putInt("CreatorCooldown"+slot,cooldowns[slot]);}output.putInt("CreatorRecovery",recoveryTicks);output.putBoolean("CreatorNoGravity",previousNoGravity);if(safeAnchor!=null){output.putDouble("CreatorSafeX",safeAnchor.x);output.putDouble("CreatorSafeY",safeAnchor.y);output.putDouble("CreatorSafeZ",safeAnchor.z);}}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);cancelCombat();try{manifestedFor=UUID.fromString(input.getStringOr("CreatorManifestedFor",""));}catch(IllegalArgumentException error){manifestedFor=null;}for(int slot=0;slot<cooldowns.length;slot++){cooldowns[slot]=Math.clamp(input.getIntOr("CreatorCooldown"+slot,0),0,INTERVALS[slot]);}recoveryTicks=Math.clamp(input.getIntOr("CreatorRecovery",0),0,40);previousNoGravity=input.getBooleanOr("CreatorNoGravity",false);defeatedAtTick=level().getGameTime();entityData.set(DEFEATED,recoveryTicks>0);if(recoveryTicks>0){setHealth(1);setNoGravity(true);}double x=input.getDoubleOr("CreatorSafeX",Double.NaN),y=input.getDoubleOr("CreatorSafeY",Double.NaN),z=input.getDoubleOr("CreatorSafeZ",Double.NaN);safeAnchor=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)?new Vec3(x,y,z):null;}
    @Override public int staffAbilityCooldown(String key){for(int slot=0;slot<KEYS.length;slot++){if(KEYS[slot].equals(key)){return cooldowns[slot];}}return -1;}
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns(){List<CooldownDiscipline.Slot> list=new ArrayList<>();for(int i=0;i<6;i++){final int slot=i;list.add(CooldownDiscipline.Slot.normal("creator_"+KEYS[i],INTERVALS[i],()->cooldowns[slot],v->cooldowns[slot]=v));}return List.copyOf(list);}
    @Override public List<String> codexCooldowns(){return List.of("Adaptive Beam: one tracking branch per hostile, 42-block default, 3-second sequence; mounted G: 0.2s (4 ticks); AI/Staff: 8s cooldown.","Creation Pulse 6s; Shadowstep 8s; Aegis 8s active / 25s; Genesis Strike 12s; Perfected Instinct 8s active / 30s.","Perfected Evolution: 12s / 120s / 24 blocks.");}
}
