package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationMotion;
import net.ronm19.phantomism.effect.ExhaustionEffect;
import net.ronm19.phantomism.entity.ai.HuskPresence;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class HuskPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/husk_phantom");
    private static final Identifier BITE_DAMAGE = id("husk_bite_damage");
    private static final Identifier RELENTLESS = id("husk_relentless");
    private static final Identifier PURSUIT = id("husk_pursuit");
    private static final Identifier GUARD = id("husk_guard");
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(HuskPhantom.class, EntityDataSerializers.BOOLEAN);
    private final Set<UUID> diveHits = new HashSet<>();
    private int biteCooldown;
    private int meleeCooldown;
    private int diveCooldown;
    private int stateCooldown;
    private int guardCooldown;
    private int landCooldown;
    private int stateTicks;
    private int guardTicks;
    private int landTicks;
    private int windupTicks;
    private int diveTicks;
    private int recoveryTicks;
    private int gripTicks;
    private int pursuitStarted;
    private int progressTick;
    private double closestDistance = Double.MAX_VALUE;
    private @Nullable UUID pursuitTarget;
    private @Nullable UUID lureTarget;
    private @Nullable Vec3 deadLandCenter;

    public HuskPhantom(EntityType<? extends HuskPhantom> type, Level level) { super(type, level); }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 88).add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.70D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D).add(Attributes.FLYING_SPEED, 0.35D).add(Attributes.FOLLOW_RANGE, 32);
    }

    public static boolean checkSpawnRules(EntityType<HuskPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000 && time < 23000
                && level.getBiome(position).is(ModTags.Biomes.HUSK_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override protected Config.PhantomStats getConfiguredStats() { return Config.HUSK_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected int getFlightTurnRate() { return 6; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(DIVING, false); }
    public boolean isHuskDiving() { return this.entityData.get(DIVING); }
    public boolean isRelentless() { return this.stateTicks > 0; }
    public boolean hasMummifiedGuard() { return this.guardTicks > 0; }
    public boolean hasDeadLand() { return this.landTicks > 0; }

    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var actions = new AnimationController<HuskPhantom>("husk_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"bite", "dive_windup", "dive_impact", "aura", "relentless", "guard", "dead_land", "presence"}) {
            actions.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.husk." + name));
        }
        registrar.add(actions.additiveAnimations());
    }

    public void showHuskAction(String name) { if (this.level() instanceof ServerLevel && this.isAddedToLevel()) { this.triggerAnim("husk_action", name); } }
    @Override public RawAnimation getMovementAnimation(PhantomAnimationMotion motion) {
        return this.isHuskDiving() ? RawAnimation.begin().thenLoop("animation.husk.dive_flight") : super.getMovementAnimation(motion);
    }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.REGENERATION) && super.canBeAffected(effect); }
    @Override public boolean canAddPassenger(Entity passenger) { return !this.isHuskDiving() && super.canAddPassenger(passenger); }
    @Override public @Nullable LivingEntity getControllingPassenger() { return this.isHuskDiving() ? null : super.getControllingPassenger(); }
    @Override public boolean isClientAuthoritative() { return !this.isHuskDiving() && super.isClientAuthoritative(); }
    @Override public void travel(Vec3 input) {
        if (this.isHuskDiving()) { this.move(MoverType.SELF, this.getDeltaMovement()); return; }
        super.travel(input);
    }

    public static boolean lure(Player player) { return player.getMainHandItem().is(Items.RABBIT_FOOT) || player.getOffhandItem().is(Items.RABBIT_FOOT); }
    private boolean provoked(Player player) { return this.getLastHurtByMob() == player && this.tickCount - this.getLastHurtByMobTimestamp() < 100; }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target instanceof Player player) { return !this.isTame() && !player.isCreative() && !player.isSpectator() && (!lure(player) || this.provoked(player)); }
        return !(target instanceof TamableAnimal pet && pet.isTame()) && !(target instanceof AbstractPhantom) && !HuskPresence.isCalmed(this, target);
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && target instanceof Enemy
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24 * 24;
    }
    public boolean isValidHuskTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && !this.isAlliedTo(target) && !this.isTargetSuppressed(target) && this.isSpeciesCombatTarget(target)
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean areaTarget(LivingEntity target) {
        return this.isValidHuskTarget(target) && (target instanceof Enemy || target == this.getTarget()
                || target instanceof Player || target instanceof Mob mob && (mob.getTarget() == this || mob.getTarget() == this.getOwner()));
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.05D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private boolean closeEnough(ServerLevel level, LivingEntity target) {
        return this.distanceToSqr(target) <= 9 && this.getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox())
                && this.line(level, this.getEyePosition(), target.getEyePosition());
    }
    private boolean combatPermitted() { return this.isAlive() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME; }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (this.isHuskDiving()) {
            if (this.tickCount % 20 == 0) { this.getConfiguredStats().apply(this); }
            LivingEntity target = this.getTarget();
            if (!this.combatPermitted() || !this.isValidHuskTarget(target) || !this.getAssignments().permitsDefensivePursuit(target)) { this.cancelDive(); }
            else { this.tickDive(level, target); }
            return;
        }
        if (!this.isTame() && !this.isBaby()) {
            if (this.getTarget() instanceof Player player && !this.isSpeciesCombatTarget(player)) { this.clearCombatTarget(false); }
            if (this.tickCount % 20 == 0 && this.combatPermitted() && this.getTarget() == null) {
                Player nearby = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(20), player -> player.isAlive()
                        && !player.isCreative() && !player.isSpectator() && !this.isTargetSuppressed(player)
                        && this.line(level, this.getEyePosition(), player.getEyePosition())).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if (nearby != null) {
                    if (lure(nearby) && !this.provoked(nearby)) { this.lureTarget = nearby.getUUID(); }
                    else { this.lureTarget = null; this.setCombatTarget(nearby); }
                }
            }
        }
        super.customServerAiStep(level);
        if (!this.isTame() && this.combatPermitted() && this.getTarget() == null && this.lureTarget != null
                && level.getEntity(this.lureTarget) instanceof Player player && player.isAlive() && !player.isSpectator()
                && lure(player) && !this.provoked(player) && this.distanceToSqr(player) <= 400 && this.line(level, this.getEyePosition(), player.getEyePosition())) {
            if (this.distanceToSqr(player) > 9) { this.flyToAssignment(player.position().add(0, 1.6D, 0), 0.75D); }
            else { this.getNavigation().stop(); this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0); }
        }
    }

    public boolean hurtHuskTarget(ServerLevel level, LivingEntity target, float damage) {
        if (this.level() != level || !this.combatPermitted() || !this.isValidHuskTarget(target)) { return false; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), damage)
                : target.hurtServer(level, this.damageSources().mobAttack(this), damage);
        if (hit && target.getHealth() + target.getAbsorptionAmount() < previous) { this.progressTick = this.tickCount; return true; }
        return false;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !this.combatPermitted() || !this.isValidHuskTarget(target)
                || this.isHuskDiving() || this.recoveryTicks > 0 || this.meleeCooldown > 0 || !this.closeEnough(level, target)) { return false; }
        boolean bite = this.biteCooldown == 0;
        if (bite) { this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(BITE_DAMAGE, 3, AttributeModifier.Operation.ADD_VALUE)); }
        boolean hit;
        try { hit = super.doHurtTarget(level, target); }
        finally { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(BITE_DAMAGE); }
        this.meleeCooldown = this.isRelentless() ? 24 : 36;
        if (hit) {
            this.progressTick = this.tickCount;
            if (bite) { this.biteCooldown = 120; ExhaustionEffect.apply(this, target); this.showHuskAction("bite"); PhantomVisualEffects.huskDust(level, target.position(), 1.3F, true); }
        }
        return hit;
    }

    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        Vec3 eye = rider.getEyePosition(); Vec3 direction = rider.getViewVector(1).normalize();
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, rider.getBoundingBox().expandTowards(direction.scale(24)).inflate(2), this::isValidHuskTarget)
                .stream().filter(candidate -> candidate.distanceToSqr(this) <= 24 * 24
                        && candidate.getBoundingBox().inflate(0.7D).clip(eye, eye.add(direction.scale(24))).isPresent()
                        && this.line(level, eye, candidate.getEyePosition()))
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(rider))).orElse(null);
        if (target == null) { return false; }
        if (this.closeEnough(level, target)) { return this.biteCooldown == 0 && this.doHurtTarget(level, target); }
        return this.beginDive(level, target);
    }

    public boolean beginDive(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.combatPermitted() || this.isPassenger() || this.isHuskDiving() || this.diveCooldown > 0
                || this.recoveryTicks > 0 || !this.isValidHuskTarget(target) || this.distanceToSqr(target) > 24 * 24 || this.distanceToSqr(target) < 9
                || this.getTask() != Task.NONE || !this.getAssignments().permitsDefensivePursuit(target)
                || !this.line(level, this.getEyePosition(), target.getEyePosition()) || !this.clearDiveStep(level, Vec3.ZERO)) { return false; }
        this.setCombatTarget(target); this.diveCooldown = 280; this.windupTicks = 16; this.diveTicks = 40; this.diveHits.clear();
        this.entityData.set(DIVING, true); this.showHuskAction("dive_windup"); this.getNavigation().stop(); this.setDeltaMovement(Vec3.ZERO); return true;
    }
    public boolean clearDiveStep(ServerLevel level, Vec3 step) {
        if (this.level() != level || !Double.isFinite(step.lengthSqr()) || step.lengthSqr() > 1) { return false; }
        return this.clearPassengerStep(level, this, this.position(), step, 0);
    }
    private boolean clearPassengerStep(ServerLevel level, Entity entity, Vec3 feet, Vec3 step, int depth) {
        if (depth > 8) { return false; }
        AABB swept = entity.getBoundingBox().move(feet.subtract(entity.position())).expandTowards(step).inflate(0.015D, 0, 0.015D);
        if (!this.isLoadedFlightBox(swept) || !level.noBlockCollision(entity, swept) || level.containsAnyLiquid(swept)) { return false; }
        for (Entity passenger : entity.getPassengers()) {
            Vec3 passengerFeet = entity.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(entity));
            Vec3 adjusted = feet.add(passengerFeet.subtract(entity.position()));
            if (!this.clearPassengerStep(level, passenger, adjusted, step, depth + 1)) { return false; }
        }
        return true;
    }
    private void tickDive(ServerLevel level, LivingEntity target) {
        this.getNavigation().stop();
        if (this.windupTicks > 0) { this.windupTicks--; this.setDeltaMovement(Vec3.ZERO); return; }
        Vec3 offset = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter());
        Vec3 step = offset.lengthSqr() > 0.64D ? offset.normalize().scale(0.8D) : offset;
        if (--this.diveTicks <= 0 || !this.clearDiveStep(level, step)) { this.cancelDive(); this.recoveryTicks = 20; return; }
        if (this.distanceToSqr(target) <= 9 && this.getBoundingBox().expandTowards(step).inflate(0.3D).intersects(target.getBoundingBox())
                && this.line(level, this.getEyePosition(), target.getEyePosition())) {
            Vec3 impact = target.position(); this.diveHit(level, target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + 5);
            for (LivingEntity nearby : this.nearbyEnemies(level, impact, 2.8D, 2)) { this.diveHit(level, nearby, 5); }
            this.showHuskAction("dive_impact"); PhantomVisualEffects.impact(level, PhantomVfxTheme.HUSK, impact.add(0, 0.5D, 0));
            PhantomVisualEffects.huskDust(level, impact, 3, true); this.cancelDive(); this.recoveryTicks = 20; return;
        }
        this.setDeltaMovement(step); this.syncVelocity = true;
        if (this.tickCount % 4 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.HUSK, this.position()); }
    }
    private void diveHit(ServerLevel level, LivingEntity target, float damage) {
        if (!this.diveHits.add(target.getUUID()) || !this.hurtHuskTarget(level, target, damage)) { return; }
        ExhaustionEffect.apply(this, target); Vec3 away = target.position().subtract(this.position());
        target.knockback(0.75D, -away.x, -away.z, this.damageSources().mobAttack(this), damage); target.push(0, 0.12D, 0); target.syncVelocity = true;
    }
    private void cancelDive() { this.windupTicks = 0; this.diveTicks = 0; this.entityData.set(DIVING, false); this.setDeltaMovement(Vec3.ZERO); }

    private List<LivingEntity> nearbyEnemies(ServerLevel level, Vec3 center, double radius, double height) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center.add(-radius, -1, -radius), center.add(radius, height, radius)), this::areaTarget)
                .stream().filter(target -> target.position().subtract(center).horizontalDistanceSqr() <= radius * radius
                        && this.line(level, center.add(0, 0.6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(16).toList();
    }
    public void pulseDrought(ServerLevel level) {
        if (this.level() != level || !this.combatPermitted() || !this.isValidHuskTarget(this.getTarget())
                || this.distanceToSqr(this.getTarget()) > 24 * 24 || this.getTask() != Task.NONE) { return; }
        List<LivingEntity> enemies = this.nearbyEnemies(level, this.position(), 6, 4);
        if (enemies.isEmpty()) { return; }
        enemies.forEach(target -> ExhaustionEffect.apply(this, target)); this.showHuskAction("aura");
        PhantomVisualEffects.huskDust(level, this.position(), 2.5F, false);
    }
    public boolean activateRelentless(ServerLevel level) {
        if (this.level() != level || !this.combatPermitted() || !this.isValidHuskTarget(this.getTarget()) || this.stateCooldown > 0 || this.stateTicks > 0) { return false; }
        this.stateCooldown = 900; this.stateTicks = 200;
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(RELENTLESS, 0.20D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            this.getAttribute(attribute).addOrUpdateTransientModifier(new AttributeModifier(RELENTLESS, 0.12D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        this.showHuskAction("relentless"); PhantomVisualEffects.huskDust(level, this.position(), 3, true); return true;
    }
    public boolean activateMummifiedGuard(ServerLevel level) {
        if (this.level() != level || !this.combatPermitted() || this.guardCooldown > 0 || this.guardTicks > 0) { return false; }
        this.guardCooldown = 600; this.guardTicks = 160; this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0));
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrUpdateTransientModifier(new AttributeModifier(GUARD, 0.25D, AttributeModifier.Operation.ADD_VALUE));
        this.showHuskAction("guard"); PhantomVisualEffects.huskDust(level, this.position(), 2.5F, true); return true;
    }
    public @Nullable Vec3 findDeadLandFloor(ServerLevel level, Vec3 position) {
        if (this.level() != level || !Double.isFinite(position.lengthSqr()) || this.position().distanceToSqr(position) > 24 * 24) { return null; }
        BlockPos start = BlockPos.containing(position);
        for (int down = 0; down <= 24; down++) {
            BlockPos air = start.below(down); BlockPos floor = air.below();
            if (floor.getY() < level.getMinY() || !level.hasChunkAt(air)) { return null; }
            var state = level.getBlockState(floor);
            if (state.getCollisionShape(level, floor).isEmpty()) { continue; }
            if (!state.isFaceSturdy(level, floor, net.minecraft.core.Direction.UP) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || !level.getFluidState(floor).isEmpty()) { return null; }
            Vec3 center = Vec3.atBottomCenterOf(air).add(0, 0.06D, 0);
            AABB box = new AABB(center.add(-0.3D, 0, -0.3D), center.add(0.3D, 1, 0.3D));
            return this.isLoadedFlightBox(box) && level.noBlockCollision(this, box) && !level.containsAnyLiquid(box) ? center : null;
        }
        return null;
    }
    public boolean activateDeadLand(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.combatPermitted() || !this.isValidHuskTarget(target) || this.landCooldown > 0 || this.landTicks > 0 || this.getTask() != Task.NONE) { return false; }
        Vec3 center = this.findDeadLandFloor(level, target.position());
        if (center == null || this.nearbyEnemies(level, center, 10, 3).isEmpty()) { return false; }
        this.deadLandCenter = center; this.landTicks = 200; this.landCooldown = 1400; this.showHuskAction("dead_land");
        PhantomVisualEffects.huskDust(level, center, 10, true); this.playSound(SoundEvents.HUSK_AMBIENT, 1, 0.6F); return true;
    }
    private void tickDeadLand(ServerLevel level) {
        if (this.deadLandCenter == null || this.landTicks <= 0) { return; }
        if (!this.combatPermitted() || this.getTask() != Task.NONE || this.position().distanceToSqr(this.deadLandCenter) > 40 * 40
                || !this.isLoadedFlightBox(new AABB(this.deadLandCenter.add(-10, -1, -10), this.deadLandCenter.add(10, 3, 10)))) { this.cancelDeadLand(); return; }
        if (this.landTicks % 20 == 0) {
            for (LivingEntity enemy : this.nearbyEnemies(level, this.deadLandCenter, 10, 3)) {
                if (this.hurtHuskTarget(level, enemy, 2)) { ExhaustionEffect.apply(this, enemy); }
            }
            PhantomVisualEffects.huskDust(level, this.deadLandCenter, 10, true);
        }
        if (--this.landTicks == 0) { this.deadLandCenter = null; }
    }
    private void cancelDeadLand() { this.landTicks = 0; this.deadLandCenter = null; }

    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatPermitted() || !this.isValidHuskTarget(target)) { this.clearCombatTarget(true); return true; }
        if (this.recoveryTicks > 0) { this.getNavigation().stop(); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuitTarget)) { this.pursuitTarget = target.getUUID(); this.pursuitStarted = this.tickCount; this.progressTick = this.tickCount; this.closestDistance = distance; }
        if (distance < this.closestDistance - 2) { this.closestDistance = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.pursuitStarted > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        boolean fleeing = target.getDeltaMovement().dot(target.position().subtract(this.position())) > 0.03D && distance > 16;
        if (fleeing) { this.getAttribute(Attributes.FOLLOW_RANGE).addOrUpdateTransientModifier(new AttributeModifier(PURSUIT, 8, AttributeModifier.Operation.ADD_VALUE)); }
        else { this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(PURSUIT); }
        if (this.tickCount % 20 == 0) {
            int enemies = this.nearbyEnemies(level, target.position(), 10, 4).size();
            if (enemies >= 3 || target.getMaxHealth() >= 60 || this.getHealth() < this.getMaxHealth() * 0.5F) { this.activateRelentless(level); }
            if (enemies >= 3 || target.getMaxHealth() >= 80) { this.activateDeadLand(level, target); }
            if (this.getHealth() < this.getMaxHealth() * 0.65F && distance < 144) { this.activateMummifiedGuard(level); }
        }
        if (distance > 16 && this.beginDive(level, target)) { return true; }
        if (this.closeEnough(level, target)) { this.doHurtTarget(level, target); return true; }
        Vec3 destination = target.position().add(0, 0.8D, 0);
        if (this.tickCount - this.progressTick > 60) { destination = destination.add(Math.sin(this.tickCount * 0.08D) * 2, 2, Math.cos(this.tickCount * 0.08D) * 2); }
        this.flyToAssignment(destination, this.isRelentless() || fleeing || this.gripTicks > 0 ? 1.12D : 0.90D); return true;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
            if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK)) { amount *= 0.8F; }
            else if (source.is(DamageTypes.LAVA)) { amount *= 0.8F; }
            else if (source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CACTUS)) { amount *= 0.5F; }
            else if (source.is(DamageTypeTags.IS_FIRE)) { amount *= 0.75F; }
        }
        float previous = this.getHealth(); boolean hit = super.hurtServer(level, source, amount);
        if (hit && this.isAlive() && this.getHealth() < previous) { this.gripTicks = 20; if (this.getHealth() < this.getMaxHealth() * 0.65F) { this.activateMummifiedGuard(level); } }
        return hit;
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.biteCooldown = Math.max(0, this.biteCooldown - 1); this.meleeCooldown = Math.max(0, this.meleeCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1); this.stateCooldown = Math.max(0, this.stateCooldown - 1);
        this.guardCooldown = Math.max(0, this.guardCooldown - 1); this.landCooldown = Math.max(0, this.landCooldown - 1);
        this.recoveryTicks = Math.max(0, this.recoveryTicks - 1); this.gripTicks = Math.max(0, this.gripTicks - 1);
        if (this.stateTicks > 0 && --this.stateTicks == 0) { this.clearRelentless(); }
        if (this.guardTicks > 0 && --this.guardTicks == 0) { this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(GUARD); }
        if (!this.combatPermitted()) { this.cancelDive(); this.cancelDeadLand(); this.clearRelentless(); this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(PURSUIT); }
        if (!this.isValidHuskTarget(this.getTarget())) { this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(PURSUIT); this.pursuitTarget = null; this.closestDistance = Double.MAX_VALUE; }
        this.tickDeadLand(level);
        if (this.tickCount % (this.isRelentless() ? 20 : 40) == 0) { this.pulseDrought(level); }
        if (!this.isBaby() && this.tickCount % 20 == 0) { HuskPresence.pulse(this, level); }
        if (this.guardTicks > 0 && this.tickCount % 10 == 0) { PhantomVisualEffects.aura(level, PhantomVfxTheme.HUSK, this.getBoundingBox().getCenter()); PhantomVisualEffects.huskDust(level, this.position(), 1.5F, true); }
    }
    private void clearRelentless() {
        this.stateTicks = 0;
        for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { this.getAttribute(attribute).removeModifier(RELENTLESS); }
    }
    @Override public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation); this.cancelDive(); this.pursuitTarget = null; this.closestDistance = Double.MAX_VALUE;
        this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(PURSUIT); if (suppressRetaliation) { this.cancelDeadLand(); this.clearRelentless(); }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void remove(RemovalReason reason) { HuskPresence.release(this); super.remove(reason); }
    @Override public void die(DamageSource source) { this.cancelDive(); this.cancelDeadLand(); this.clearRelentless(); HuskPresence.release(this); super.die(source); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("HuskBiteCooldown", this.biteCooldown); output.putInt("HuskMeleeCooldown", this.meleeCooldown);
        output.putInt("HuskDiveCooldown", this.diveCooldown); output.putInt("HuskStateCooldown", this.stateCooldown);
        output.putInt("HuskGuardCooldown", this.guardCooldown); output.putInt("HuskLandCooldown", this.landCooldown);
        output.putInt("HuskStateTicks", this.stateTicks); output.putInt("HuskGuardTicks", this.guardTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.cancelDive(); this.cancelDeadLand(); this.clearRelentless(); this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(PURSUIT);
        this.biteCooldown = Math.clamp(input.getIntOr("HuskBiteCooldown", 0), 0, 120); this.meleeCooldown = Math.clamp(input.getIntOr("HuskMeleeCooldown", 0), 0, 36);
        this.diveCooldown = Math.clamp(input.getIntOr("HuskDiveCooldown", 0), 0, 280); this.stateCooldown = Math.clamp(input.getIntOr("HuskStateCooldown", 0), 0, 900);
        this.guardCooldown = Math.clamp(input.getIntOr("HuskGuardCooldown", 0), 0, 600); this.landCooldown = Math.clamp(input.getIntOr("HuskLandCooldown", 0), 0, 1400);
        this.stateTicks = Math.clamp(input.getIntOr("HuskStateTicks", 0), 0, 200); this.guardTicks = Math.clamp(input.getIntOr("HuskGuardTicks", 0), 0, 160);
        if (this.stateTicks > 0) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(RELENTLESS, 0.20D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { this.getAttribute(attribute).addOrUpdateTransientModifier(new AttributeModifier(RELENTLESS, 0.12D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        }
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(GUARD);
        if (this.guardTicks > 0) { this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrUpdateTransientModifier(new AttributeModifier(GUARD, 0.25D, AttributeModifier.Operation.ADD_VALUE)); }
    }
}
