package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.EchoCombatEvents;
import net.ronm19.phantomism.entity.ai.EchoCombatEvents.Kind;
import net.ronm19.phantomism.entity.ai.EchoTeamSupport;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class EchoPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/echo_phantom");
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(EchoPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PERFECT = SynchedEntityData.defineId(EchoPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STRIKING = SynchedEntityData.defineId(EchoPhantom.class, EntityDataSerializers.BOOLEAN);
    private int pulseCooldown;
    private int strikeCooldown;
    private int meleeCooldown;
    private int aidCooldown;
    private int chainCooldown;
    private int guardCooldown;
    private int guardTicks;
    private int perfectCooldown;
    private int perfectTicks;
    private int gainCooldown;
    private int strikeTicks;
    private int recoveryTicks;
    private int feedbackCooldown;
    private UUID combatSession = UUID.randomUUID();
    private final java.util.Map<UUID, Long> seenEvents = new java.util.LinkedHashMap<>();
    private int combatStarted;
    private int progressTick;
    private double closestDistance = Double.MAX_VALUE;
    private @Nullable UUID pursuitTarget;
    private @Nullable UUID explicitTarget;
    private @Nullable UUID curiousPlayer;
    private @Nullable UUID comboTarget;
    private @Nullable UUID comboActor;
    private long comboTime;
    private @Nullable Pending pending;

    public EchoPhantom(EntityType<? extends EchoPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.35D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.425D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<EchoPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.OVERWORLD && level.getBiome(position).is(ModTags.Biomes.ECHO_PHANTOM_SPAWNS)
                && level.canSeeSky(position) && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ECHO_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(CHARGE, 0); builder.define(PERFECT, false); builder.define(STRIKING, false); }
    public int getEchoCharge() { return this.entityData.get(CHARGE); }
    public UUID combatSession() { return this.combatSession; }
    public int getEchoCapacity() { return this.isPerfectResonance() ? 5 : 3; }
    public boolean isPerfectResonance() { return this.entityData.get(PERFECT); }
    public boolean isEchoStriking() { return this.entityData.get(STRIKING); }
    public boolean hasEchoGuard() { return this.guardTicks > 0; }
    public void setEchoCharge(int charge) { if (this.level() instanceof ServerLevel) { this.entityData.set(CHARGE, Math.clamp(charge, 0, this.getEchoCapacity())); } }
    public boolean combatEnabled() { return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT); }
    public boolean acceptsAlly(LivingEntity actor) {
        return this.isTame() && this.getOwnerReference() != null && (this.getOwnerReference().matches(actor)
                || actor instanceof AbstractPhantom phantom && phantom.isTame() && this.getOwnerReference().equals(phantom.getOwnerReference()));
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target instanceof AbstractPhantom || target instanceof OwnableEntity owned && owned.getOwnerReference() != null || this.isAlliedTo(target)) { return false; }
        if (target instanceof Player player) { return !this.isTame() && !player.isCreative() && !player.isSpectator() && this.getLastHurtByMob() == player && this.tickCount - this.getLastHurtByMobTimestamp() <= 100; }
        boolean justified = target.getUUID().equals(this.explicitTarget) || target == this.getLastHurtByMob()
                || target instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == this || this.acceptsAlly(mob.getTarget()));
        if ((target instanceof net.minecraft.world.entity.NeutralMob || target.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN) && !justified) { return false; }
        return target instanceof Enemy || justified;
    }
    public boolean canObserveTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && !target.isRemoved() && this.isSpeciesCombatTarget(target)
                && !this.isTargetSuppressed(target) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    public boolean isValidEchoTarget(@Nullable LivingEntity target) { return this.canObserveTarget(target) && target.isAlive(); }
    @Override public void setCombatTarget(LivingEntity target) {
        UUID previous = this.explicitTarget; this.explicitTarget = target.getUUID(); super.setCombatTarget(target);
        if (this.getTarget() != target) { this.explicitTarget = previous; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && this.getOrder() == Order.GUARD && target instanceof Enemy && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24 * 24;
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return this.distanceToSqr(target) - (target instanceof Mob mob && mob.getTarget() != null && this.acceptsAlly(mob.getTarget()) ? 1000 : 0); }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) { return this.isLoadedFlightBox(new AABB(start, end).inflate(0.04D)) && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public boolean earnCharge(ServerLevel level) {
        if (this.level() != level || !this.combatEnabled() || this.gainCooldown > 0 || this.getEchoCharge() >= this.getEchoCapacity()) { return false; }
        this.gainCooldown = 20; this.setEchoCharge(this.getEchoCharge() + 1); return true;
    }
    private int availableCharge() { return this.getEchoCharge() - (this.pending == null ? 0 : this.pending.shots.size()) - (this.isEchoStriking() ? 1 : 0); }
    public float estimate(LivingEntity target, float damage) {
        float absorbed = CombatRules.getDamageAfterAbsorb(target, damage, this.damageSources().mobAttack(this), target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        return Math.max(0.05F, absorbed * (resistance == null ? 1 : Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2F)));
    }
    public boolean offerOpportunity(ServerLevel level, LivingEntity target, LivingEntity ally, UUID event, Kind kind) {
        this.seenEvents.values().removeIf(expires -> expires <= level.getGameTime());
        if (this.seenEvents.containsKey(event) || EchoCombatEvents.generated(kind) || kind == Kind.SUPPORT) { return false; }
        if (this.seenEvents.size() >= 64) { this.seenEvents.remove(this.seenEvents.keySet().iterator().next()); }
        this.seenEvents.put(event, level.getGameTime() + 100);
        if (!this.combatEnabled() || !this.acceptsAlly(ally) || !this.isValidEchoTarget(target) || this.distanceToSqr(target) > 48 * 48
                || !this.getAssignments().permitsDefensivePursuit(target) || !this.line(level, this.getEyePosition(), target.getBoundingBox().getCenter())) { return false; }
        if (this.distanceToSqr(target) > EchoPulseProjectile.MAX_TRAVEL_DISTANCE * EchoPulseProjectile.MAX_TRAVEL_DISTANCE) { if (!this.isValidEchoTarget(this.getTarget())) { this.setCombatTarget(target); this.showAction("sense"); } return false; }
        boolean chain = target.getUUID().equals(this.comboTarget) && !ally.getUUID().equals(this.comboActor) && level.getGameTime() - this.comboTime <= 60;
        this.comboTarget = target.getUUID(); this.comboActor = ally.getUUID(); this.comboTime = level.getGameTime();
        boolean chainReady = chain && this.chainCooldown == 0 && this.availableCharge() >= 2;
        if (this.pending != null || this.isEchoStriking() || this.aidCooldown > 0 && !chainReady || this.availableCharge() < 1
                || target.getHealth() + target.getAbsorptionAmount() - EchoCombatEvents.pending(level, target.getUUID()) <= this.estimate(target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) { return false; }
        int count = chainReady ? 2 : 1;
        Deque<UUID> shots = new ArrayDeque<>();
        for (int index = 0; index < count; index++) { UUID reservation = EchoCombatEvents.reserve(this, target, this.estimate(target, 4)); if (reservation != null) { shots.add(reservation); } }
        if (shots.isEmpty()) { return false; }
        this.pending = new Pending(target.getUUID(), ally.getUUID(), event, shots, level.getGameTime() + 12, level.getGameTime() + 55, chain && shots.size() > 1);
        if (shots.size() > 1) { this.chainCooldown = this.isPerfectResonance() ? 40 : 60; }
        this.setCombatTarget(target); this.showAction("sense"); return true;
    }
    private void tickOpportunity(ServerLevel level) {
        Pending action = this.pending; if (action == null) { return; }
        if (!this.combatEnabled() || level.getGameTime() > action.expires || !(level.getEntity(action.target) instanceof LivingEntity target)
                || !this.isValidEchoTarget(target) || !(level.getEntity(action.ally) instanceof LivingEntity ally) || !ally.isAlive() || !this.acceptsAlly(ally)
                || this.distanceToSqr(ally) > (this.isPerfectResonance() ? 24 * 24 : 48 * 48) || this.distanceToSqr(target) > EchoPulseProjectile.MAX_TRAVEL_DISTANCE * EchoPulseProjectile.MAX_TRAVEL_DISTANCE
                || !this.getAssignments().permitsDefensivePursuit(target) || !this.line(level, this.getEyePosition(), target.getBoundingBox().getCenter())
                || target.getHealth() + target.getAbsorptionAmount() <= this.estimate(target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) { this.cancelPending(level); return; }
        if (level.getGameTime() < action.due || target.damageCooldownTime > 10 || this.isEchoStriking()) { return; }
        UUID reservation = action.shots.peekFirst();
        if (reservation == null || this.getEchoCharge() < 1 || !EchoCombatEvents.active(level, reservation, this, target)) { this.cancelPending(level); return; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(this.getEyePosition());
        if (this.spawnPulse(level, direction, action.chain ? Kind.CHAIN : Kind.AID, action.event, target.getUUID(), reservation)) {
            action.shots.removeFirst(); this.setEchoCharge(this.getEchoCharge() - 1); action.due = level.getGameTime() + 12;
            PhantomVisualEffects.beam(level, PhantomVfxTheme.ECHO, ally.getBoundingBox().getCenter(), this.getBoundingBox().getCenter());
            this.showAction(action.chain ? "chain" : "aid"); this.aidCooldown = this.isPerfectResonance() ? 20 : 40;
            if (action.shots.isEmpty()) { this.pending = null; }
        } else { this.cancelPending(level); }
    }
    private void cancelPending(ServerLevel level) { if (this.pending != null) { this.pending.shots.forEach(identity -> EchoCombatEvents.release(level, identity)); this.pending = null; } }
    private boolean spawnPulse(ServerLevel level, Vec3 direction, Kind kind, UUID origin, @Nullable UUID target, @Nullable UUID reservation) {
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 0.01D) { return false; }
        Vec3 normalized = direction.normalize(); Vec3 muzzle = this.getEyePosition().add(normalized.scale(0.7D));
        if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        EchoPulseProjectile projectile = PhantomismContent.ECHO_PULSE.get().create(level, EntitySpawnReason.TRIGGERED);
        projectile.setOwner(this); projectile.configure(kind, origin, target, reservation); projectile.setPos(muzzle); projectile.shoot(normalized.x, normalized.y, normalized.z, 1.35F, 0);
        if (!level.addFreshEntity(projectile)) { return false; }
        PhantomVisualEffects.echoRings(level, muzzle, 0.6F, false); this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.35F, 1.8F); return true;
    }
    public boolean launchPulse(ServerLevel level, Vec3 direction) {
        int interval = this.getControllingPassenger() instanceof Player ? 100 : 200;
        if (this.level() != level || !this.combatEnabled() || this.isEchoStriking() || this.pulseCooldown > 200 - interval
                || !this.spawnPulse(level, direction, Kind.PULSE, UUID.randomUUID(), null, null)) { return false; }
        this.pulseCooldown = 200; this.showAction("pulse"); return true;
    }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { boolean fired = this.launchPulse(level, rider.getViewVector(1)); if (fired) { this.showCharge(rider); } return fired; }
    private void showCharge(Player player) { if (this.feedbackCooldown == 0 && player instanceof ServerPlayer server) { this.feedbackCooldown = 20; server.sendOverlayMessage(Component.translatable("message.phantomism.echo_charge", this.getEchoCharge(), this.getEchoCapacity())); } }
    public boolean hurtEchoTarget(ServerLevel level, LivingEntity target, DamageSource source, float damage, Kind kind, UUID event) {
        if (this.level() != level || !this.combatEnabled() || !this.isValidEchoTarget(target)) { return false; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, new EchoCombatEvents.EchoDamage(this.damageSources().playerAttack(owner), this, kind, event), damage)
                : EchoCombatEvents.hurt(this, target, source, damage, kind, event);
        if (hit && target.getHealth() + target.getAbsorptionAmount() < previous) {
            this.progressTick = this.tickCount;
            if (kind == Kind.PULSE && target.isAlive() && this.getControllingPassenger() instanceof Player) { this.setCombatTarget(target); }
            this.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.35F, 1.9F); this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.7F); return true;
        }
        return false;
    }
    private boolean closeEnough(ServerLevel level, LivingEntity target) { return this.distanceToSqr(target) <= 9 && this.getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox()) && this.line(level, this.getEyePosition(), target.getEyePosition()); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !this.combatEnabled() || !this.isValidEchoTarget(target) || this.isEchoStriking() || this.meleeCooldown > 0 || this.recoveryTicks > 0 || !this.closeEnough(level, target)) { return false; }
        this.meleeCooldown = 32; boolean hit = super.doHurtTarget(level, entity); if (hit) { this.progressTick = this.tickCount; } return hit;
    }
    public boolean activateRiddenStrike(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || rider != this.getControllingPassenger() || !this.isOwnedBy(rider)) { return false; }
        if (!this.combatEnabled() || this.strikeCooldown > 0 || this.recoveryTicks > 0 || this.availableCharge() < 1) { this.showCharge(rider); return false; }
        Vec3 start = rider.getEyePosition(); Vec3 end = start.add(rider.getViewVector(1).scale(10));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(11), this::isValidEchoTarget).stream()
                .filter(candidate -> candidate.getBoundingBox().inflate(0.65D).clip(start, end).isPresent())
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null || !this.beginStrike(level, target)) { this.showCharge(rider); return false; }
        this.showCharge(rider); return true;
    }
    public boolean beginStrike(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.combatEnabled() || this.isEchoStriking() || this.isPassenger() || this.recoveryTicks > 0 || this.strikeCooldown > 0
                || this.availableCharge() < 1 || !this.isValidEchoTarget(target) || this.distanceToSqr(target) > 100 || !this.getAssignments().permitsDefensivePursuit(target)
                || target.getHealth() + target.getAbsorptionAmount() <= this.estimate(target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                || !this.line(level, this.getEyePosition(), target.getEyePosition()) || !this.clearStrikeStep(level, this, this.position(), Vec3.ZERO, 0)) { return false; }
        this.setCombatTarget(target); this.strikeCooldown = 200; this.strikeTicks = 20; this.entityData.set(STRIKING, true); this.showAction("strike"); this.setDeltaMovement(Vec3.ZERO); return true;
    }
    private boolean clearStrikeStep(ServerLevel level, Entity entity, Vec3 feet, Vec3 step, int depth) {
        if (depth > 8 || !Double.isFinite(step.lengthSqr()) || step.lengthSqr() > 1) { return false; }
        AABB box = entity.getBoundingBox().move(feet.subtract(entity.position())).expandTowards(step).inflate(0.015D, 0, 0.015D);
        if (!this.isLoadedFlightBox(box) || !level.noBlockCollision(entity, box) || level.containsAnyLiquid(box)) { return false; }
        for (Entity passenger : entity.getPassengers()) {
            Vec3 ridingFeet = entity.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(entity));
            if (!this.clearStrikeStep(level, passenger, feet.add(ridingFeet.subtract(entity.position())), step, depth + 1)) { return false; }
        }
        return true;
    }
    private void tickStrike(ServerLevel level) {
        LivingEntity target = this.getTarget(); this.getNavigation().stop();
        if (!this.combatEnabled() || !this.isValidEchoTarget(target) || this.getEchoCharge() < 1 || !this.getAssignments().permitsDefensivePursuit(target) || --this.strikeTicks <= 0) { this.cancelStrike(); return; }
        if (target.getHealth() + target.getAbsorptionAmount() <= this.estimate(target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) { this.cancelStrike(); if (this.closeEnough(level, target)) { this.doHurtTarget(level, target); } return; }
        Vec3 offset = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()); Vec3 step = offset.lengthSqr() > 0.72D ? offset.normalize().scale(0.85D) : offset;
        if (!this.clearStrikeStep(level, this, this.position(), step, 0)) { this.cancelStrike(); return; }
        if (this.closeEnough(level, target)) {
            if (this.hurtEchoTarget(level, target, this.damageSources().mobAttack(this), 10, Kind.STRIKE, UUID.randomUUID())) {
                this.setEchoCharge(this.getEchoCharge() - 1); PhantomVisualEffects.impact(level, PhantomVfxTheme.ECHO, target.getBoundingBox().getCenter()); this.showAction("strike_impact");
            }
            this.cancelStrike(); this.recoveryTicks = 12; return;
        }
        this.setDeltaMovement(step); this.syncVelocity = true;
        if (this.tickCount % 3 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.ECHO, this.position()); }
    }
    private void cancelStrike() { this.entityData.set(STRIKING, false); this.strikeTicks = 0; this.setDeltaMovement(Vec3.ZERO); }
    @Override public @Nullable LivingEntity getControllingPassenger() { return this.isEchoStriking() ? null : super.getControllingPassenger(); }
    @Override public boolean isClientAuthoritative() { return !this.isEchoStriking() && super.isClientAuthoritative(); }
    @Override public boolean canAddPassenger(Entity passenger) { return !this.isEchoStriking() && super.canAddPassenger(passenger); }
    @Override public void travel(Vec3 input) {
        if (this.isEchoStriking()) { this.move(MoverType.SELF, this.getDeltaMovement()); return; }
        if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * 0.09D)); return; }
        super.travel(input);
    }
    public boolean activateEchoGuard(ServerLevel level) {
        if (this.level() != level || !this.combatEnabled() || this.guardCooldown > 0 || this.guardTicks > 0) { return false; }
        this.guardCooldown = 360; this.guardTicks = 80; this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0)); this.showAction("guard"); PhantomVisualEffects.echoRings(level, this.getBoundingBox().getCenter(), 2, true); return true;
    }
    public boolean activatePerfectResonance(ServerLevel level) {
        if (this.level() != level || !this.combatEnabled() || !this.isValidEchoTarget(this.getTarget()) || this.perfectCooldown > 0 || this.perfectTicks > 0) { return false; }
        this.perfectCooldown = 1600; this.perfectTicks = 240; this.entityData.set(PERFECT, true); this.showAction("perfect");
        PhantomVisualEffects.echoRings(level, this.getBoundingBox().getCenter(), 4, true); this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1F, 0.65F); return true;
    }
    private void endPerfect() { this.perfectTicks = 0; this.entityData.set(PERFECT, false); this.setEchoCharge(Math.min(3, this.getEchoCharge())); EchoTeamSupport.release(this); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hit = super.hurtServer(level, source, amount);
        if (hit && this.isAlive() && (amount >= 6 || this.getHealth() < this.getMaxHealth() * 0.65F)) { this.activateEchoGuard(level); }
        return hit;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (this.isEchoStriking()) { if (this.tickCount % 20 == 0) { this.getConfiguredStats().apply(this); } this.tickStrike(level); return; }
        super.customServerAiStep(level);
        if (!this.isTame() && !this.isBaby() && this.getTarget() == null && this.combatEnabled()) {
            if (this.tickCount % 20 == 0) {
                this.curiousPlayer = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16), player -> player.isAlive() && !player.isSpectator())
                        .stream().min(Comparator.comparingDouble(player -> this.distanceToSqr(player) - (player.getMainHandItem().is(Items.GOAT_HORN) || player.getOffhandItem().is(Items.GOAT_HORN) ? 200 : 0))).map(Entity::getUUID).orElse(null);
            }
            if (this.curiousPlayer != null && level.getEntity(this.curiousPlayer) instanceof Player player && player.isAlive() && this.distanceToSqr(player) <= 256 && this.line(level, this.getEyePosition(), player.getEyePosition())) {
                this.getLookControl().setLookAt(player); if (this.distanceToSqr(player) > 25) { this.flyToAssignment(player.position().add(0, 2, 0), 0.85D); }
            }
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || !this.isValidEchoTarget(target)) { this.clearCombatTarget(true); return true; }
        if (!target.getUUID().equals(this.pursuitTarget)) { this.pursuitTarget = target.getUUID(); this.combatStarted = this.tickCount; this.progressTick = this.tickCount; this.closestDistance = this.distanceToSqr(target); }
        double distance = this.distanceToSqr(target);
        if (distance < this.closestDistance - 2) { this.closestDistance = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.combatStarted > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        if (this.recoveryTicks > 0) { return true; }
        if (this.pending == null && target.getHealth() + target.getAbsorptionAmount() > this.estimate(target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE)) && this.beginStrike(level, target)) { return true; }
        if (this.closeEnough(level, target)) { this.doHurtTarget(level, target); return true; }
        if (distance <= EchoPulseProjectile.MAX_TRAVEL_DISTANCE * EchoPulseProjectile.MAX_TRAVEL_DISTANCE && this.line(level, this.getEyePosition(), target.getBoundingBox().getCenter()) && this.launchPulse(level, target.getBoundingBox().getCenter().subtract(this.getEyePosition()))) { return true; }
        this.flyToAssignment(target.position().add(0, 1.2D, 0), 1); return true;
    }
    private void scanEchoSense(ServerLevel level) {
        if (!this.isTame() || this.isValidEchoTarget(this.getTarget())) { return; }
        double range = Math.min(48, this.getAttributeValue(Attributes.FOLLOW_RANGE));
        LivingEntity engaged = level.getEntitiesOfClass(AbstractPhantom.class, this.getBoundingBox().inflate(range),
                ally -> ally != this && ally.isAlive() && this.acceptsAlly(ally) && this.distanceToSqr(ally) <= range * range)
                .stream().limit(8).map(AbstractPhantom::getTarget).filter(this::isValidEchoTarget)
                .filter(target -> this.distanceToSqr(target) <= range * range && this.line(level, this.getEyePosition(), target.getBoundingBox().getCenter()))
                .min(Comparator.comparingDouble(this::getAutomaticTargetPriority)).orElse(null);
        if (engaged == null && this.getOrder() == Order.GUARD && this.getOwner() != null) {
            engaged = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range), this::isValidEchoTarget).stream()
                    .filter(target -> this.distanceToSqr(target) <= range * range && this.getOwner().distanceToSqr(target) <= 24 * 24
                            && this.line(level, this.getEyePosition(), target.getEyePosition())).min(Comparator.comparingDouble(this::getAutomaticTargetPriority)).orElse(null);
        }
        if (engaged != null && this.getAssignments().permitsDefensivePursuit(engaged)) { this.setCombatTarget(engaged); this.showAction("sense"); }
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.pulseCooldown = Math.max(0, this.pulseCooldown - 1); this.strikeCooldown = Math.max(0, this.strikeCooldown - 1); this.meleeCooldown = Math.max(0, this.meleeCooldown - 1);
        this.aidCooldown = Math.max(0, this.aidCooldown - 1); this.guardCooldown = Math.max(0, this.guardCooldown - 1); this.perfectCooldown = Math.max(0, this.perfectCooldown - 1);
        this.chainCooldown = Math.max(0, this.chainCooldown - 1);
        this.gainCooldown = Math.max(0, this.gainCooldown - 1); this.guardTicks = Math.max(0, this.guardTicks - 1); this.recoveryTicks = Math.max(0, this.recoveryTicks - 1);
        this.feedbackCooldown = Math.max(0, this.feedbackCooldown - 1);
        if (!this.combatEnabled()) { this.cancelPending(level); this.cancelStrike(); if (this.isPerfectResonance()) { this.endPerfect(); } EchoCombatEvents.release(this); return; }
        if (this.perfectTicks > 0 && --this.perfectTicks == 0) { this.endPerfect(); }
        this.tickOpportunity(level);
        if (this.tickCount % 20 == 0) {
            this.scanEchoSense(level);
            LivingEntity target = this.getTarget();
            if (this.isValidEchoTarget(target) && this.distanceToSqr(target) <= 24 * 24) {
                long allies = level.getEntitiesOfClass(AbstractPhantom.class, this.getBoundingBox().inflate(24), ally -> ally != this && ally.isAlive() && this.acceptsAlly(ally) && this.distanceToSqr(ally) <= 24 * 24 && ally.getTarget() != null).stream().limit(3).count();
                if (allies >= 2 || target.getMaxHealth() >= 80 || this.getHealth() < 24) { this.activatePerfectResonance(level); }
            }
            if (this.isPerfectResonance()) { EchoTeamSupport.pulse(this, level); PhantomVisualEffects.echoRings(level, this.getBoundingBox().getCenter(), 2.6F, true); }
            else if (this.getEchoCharge() > 0) { PhantomVisualEffects.echoCharge(level, this.getBoundingBox().getCenter(), this.getEchoCharge()); }
            if (this.guardTicks > 0) { PhantomVisualEffects.aura(level, PhantomVfxTheme.ECHO, this.getBoundingBox().getCenter()); }
        }
    }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var actions = new AnimationController<EchoPhantom>("echo_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"sense", "pulse", "strike", "strike_impact", "aid", "chain", "guard", "perfect"}) { actions.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.echo." + name)); }
        registrar.add(actions.additiveAnimations());
    }
    private void showAction(String name) { if (this.level() instanceof ServerLevel && this.isAddedToLevel()) { this.triggerAnim("echo_action", name); } }
    @Override public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation); this.cancelStrike(); this.pursuitTarget = null; this.explicitTarget = null; this.closestDistance = Double.MAX_VALUE;
        if (suppressRetaliation && this.level() instanceof ServerLevel level) { this.combatSession = UUID.randomUUID(); this.cancelPending(level); EchoCombatEvents.release(this); this.endPerfect(); this.comboTarget = null; this.comboActor = null; }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void remove(RemovalReason reason) { if (this.level() instanceof ServerLevel level) { this.cancelPending(level); EchoCombatEvents.release(this); EchoTeamSupport.release(this); } super.remove(reason); }
    @Override public void die(DamageSource source) { if (this.level() instanceof ServerLevel level) { this.cancelPending(level); EchoCombatEvents.release(this); this.endPerfect(); } this.cancelStrike(); super.die(source); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("EchoCharge", this.getEchoCharge()); output.putInt("EchoPerfectTicks", this.perfectTicks); output.putInt("EchoPerfectCooldown", this.perfectCooldown);
        output.putInt("EchoPulseCooldown", this.pulseCooldown); output.putInt("EchoStrikeCooldown", this.strikeCooldown); output.putInt("EchoAidCooldown", this.aidCooldown);
        output.putInt("EchoChainCooldown", this.chainCooldown);
        output.putInt("EchoGuardCooldown", this.guardCooldown); output.putInt("EchoGuardTicks", this.guardTicks); output.putInt("EchoGainCooldown", this.gainCooldown); output.putInt("EchoMeleeCooldown", this.meleeCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.combatSession = UUID.randomUUID(); if (this.level() instanceof ServerLevel level) { this.cancelPending(level); EchoCombatEvents.release(this); } this.cancelStrike();
        this.perfectTicks = Math.clamp(input.getIntOr("EchoPerfectTicks", 0), 0, 240); this.entityData.set(PERFECT, this.perfectTicks > 0);
        this.setEchoCharge(input.getIntOr("EchoCharge", 0)); this.perfectCooldown = Math.clamp(input.getIntOr("EchoPerfectCooldown", 0), 0, 1600);
        this.pulseCooldown = Math.clamp(input.getIntOr("EchoPulseCooldown", 0), 0, 200); this.strikeCooldown = Math.clamp(input.getIntOr("EchoStrikeCooldown", 0), 0, 200);
        this.aidCooldown = Math.clamp(input.getIntOr("EchoAidCooldown", 0), 0, 40); this.guardCooldown = Math.clamp(input.getIntOr("EchoGuardCooldown", 0), 0, 360);
        this.chainCooldown = Math.clamp(input.getIntOr("EchoChainCooldown", 0), 0, 60);
        this.guardTicks = Math.clamp(input.getIntOr("EchoGuardTicks", 0), 0, 80); this.gainCooldown = Math.clamp(input.getIntOr("EchoGainCooldown", 0), 0, 20); this.meleeCooldown = Math.clamp(input.getIntOr("EchoMeleeCooldown", 0), 0, 32);
        this.comboTarget = null; this.comboActor = null; EchoTeamSupport.release(this);
    }
    private static final class Pending {
        private final UUID target; private final UUID ally; private final UUID event; private final Deque<UUID> shots; private final long expires; private final boolean chain; private long due;
        private Pending(UUID target, UUID ally, UUID event, Deque<UUID> shots, long due, long expires, boolean chain) { this.target = target; this.ally = ally; this.event = event; this.shots = shots; this.due = due; this.expires = expires; this.chain = chain; }
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "launch_pulse" -> this.pulseCooldown;
            case "begin_strike" -> this.strikeCooldown;
            case "activate_echo_guard" -> this.guardCooldown;
            case "activate_perfect_resonance" -> this.perfectCooldown;
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { this.pulseCooldown = ticks; }
}
