package net.ronm19.phantomism.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class WaterPhantom extends AbstractPhantom {
    private static final int PROJECTILE_COOLDOWN = 12 * 20;
    private static final int SHIELD_COOLDOWN = 12 * 20;
    private static final int SHIELD_DURATION = 8 * 20;
    private static final int HYDRO_PUMP_COOLDOWN = 60 * 20;
    private static final int HYDRO_PUMP_DURATION = 4 * 20;
    private static final double ABILITY_RANGE = WaterProjectile.MAX_TRAVEL_DISTANCE;
    private static final double BLESSING_RADIUS = 24.0D;
    private int projectileCooldown;
    private int shieldCooldown;
    private int hydroPumpCooldown;
    private int hydroPumpTicks;
    private int recentDamageTicks;
    private int ownerThreatTimestamp;

    public WaterPhantom(EntityType<? extends WaterPhantom> type, Level level) {
        super(type, level);
        this.moveControl = new AquaticMoveControl(this, this.getMoveControl());
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 38.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 4.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<WaterPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.WATER_PHANTOM_SPAWNS)
                && level.getWorldBorder().isWithinBounds(position)
                && level.getFluidState(position).is(FluidTags.WATER)
                && level.getFluidState(position.above()).is(FluidTags.WATER)
                && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty();
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this) && level.noBlockCollision(this, this.getBoundingBox());
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.WATER_PHANTOM_STATS;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean dismountsUnderwater() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected double getFlightAltitudeOffset(double offset) {
        return this.isInWater() ? 0.0D : super.getFlightAltitudeOffset(offset);
    }

    @Override
    protected boolean isSuitableIdleFlightPosition(Vec3 destination, AABB clearance) {
        return this.isInWater() ? this.level().getFluidState(BlockPos.containing(destination)).is(FluidTags.WATER)
                : super.isSuitableIdleFlightPosition(destination, clearance);
    }

    @Override
    protected double getRiddenVerticalInput(Player rider, Input input) {
        double buttons = super.getRiddenVerticalInput(rider, input);
        return this.isInWater() && !input.jump() && !input.sprint()
                ? -Mth.sin(rider.getXRot() * Mth.DEG_TO_RAD) * Mth.clamp(rider.zza, -1.0F, 1.0F) : buttons;
    }

    @Override
    protected double getRiddenMaximumVerticalSpeed() {
        return this.isInWater() ? 0.28D : super.getRiddenMaximumVerticalSpeed();
    }

    @Override
    protected void travelFlying(Vec3 input, float speed) {
        if (this.isInWater()) {
            this.moveRelative(speed * 1.5F, input);
            this.moveThroughWater(0.8D);
        } else {
            super.travelFlying(input, speed);
        }
    }

    @Override
    protected void travelInWater(Vec3 input, double gravity, boolean falling, double oldY) {
        this.moveThroughWater(0.9D);
    }

    private void moveThroughWater(double drag) {
        if (!this.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(drag));
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.launchProjectile(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= BLESSING_RADIUS * BLESSING_RADIUS;
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        double priority = owner != null ? owner.distanceToSqr(target) : this.distanceToSqr(target);
        if (target instanceof Mob mob && (mob.getTarget() == owner || mob.getTarget() == this)) {
            priority *= 0.25D;
        }
        if (target.getType() == EntityTypes.ELDER_GUARDIAN) {
            priority *= 0.5D;
        }
        return target.isInWater() && target instanceof Enemy ? priority * 0.6D : priority;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount >= 2.0F) {
            this.recentDamageTicks = 60;
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.projectileCooldown = Math.max(0, this.projectileCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.hydroPumpCooldown = Math.max(0, this.hydroPumpCooldown - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (this.tickCount % 20 == 0) {
            this.tickWaterBlessing();
        }
        this.observeOwnerThreat();
        LivingEntity target = this.getTarget();
        if (this.isBaby() || !this.isValidWaterTarget(target)) {
            this.hydroPumpTicks = 0;
            return;
        }
        this.tickWaterShield(target);
        this.tickHydroPump(level, target);
        if (this.getControllingPassenger() == null && this.hydroPumpTicks == 0 && this.projectileCooldown == 0
                && this.distanceToSqr(target) <= ABILITY_RANGE * ABILITY_RANGE && this.hasLineOfSight(target)
                && this.launchProjectile(level, target.getEyePosition().subtract(this.getProjectileOrigin()))) {
            this.projectileCooldown = PROJECTILE_COOLDOWN;
        }
    }

    private void tickWaterBlessing() {
        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive() && owner.level() == this.level()
                && this.distanceToSqr(owner) <= BLESSING_RADIUS * BLESSING_RADIUS) {
            this.bless(owner);
        }
        LivingEntity rider = this.getControllingPassenger();
        if (rider != null && rider != owner) {
            this.bless(rider);
        }
    }

    private void bless(LivingEntity recipient) {
        if (recipient.isEyeInFluid(FluidTags.WATER)) {
            if (!recipient.hasEffect(MobEffects.WATER_BREATHING) && this.level() instanceof ServerLevel level) {
                net.ronm19.phantomism.vfx.PhantomVisualEffects.aura(level,
                        net.ronm19.phantomism.vfx.PhantomVfxTheme.WATER, recipient.position().add(0.0D, 0.5D, 0.0D));
            }
            recipient.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, true), this);
        }
    }

    private void observeOwnerThreat() {
        LivingEntity owner = this.getOwner();
        if (owner != null && owner.getLastHurtByMobTimestamp() != this.ownerThreatTimestamp) {
            this.ownerThreatTimestamp = owner.getLastHurtByMobTimestamp();
            LivingEntity attacker = owner.getLastHurtByMob();
            if (!this.isBaby() && this.isValidWaterTarget(attacker) && this.distanceToSqr(attacker)
                    <= Math.pow(this.getAttributeValue(Attributes.FOLLOW_RANGE), 2.0D)
                    && this.getAssignments().permitsDefensivePursuit(attacker)) {
                this.setTarget(attacker);
            }
        }
    }

    private void tickWaterShield(LivingEntity target) {
        LivingEntity rider = this.getControllingPassenger();
        boolean threatened = this.recentDamageTicks > 0 || this.getHealth() <= this.getMaxHealth() * 0.6F
                || target instanceof Mob mob && this.distanceToSqr(target) <= 36.0D
                        && (mob.getTarget() == this || rider != null && mob.getTarget() == rider);
        if (rider != null) {
            threatened |= rider.getHealth() <= rider.getMaxHealth() * 0.6F
                    || rider.getLastHurtByMob() == target && rider.tickCount - rider.getLastHurtByMobTimestamp() <= 60;
        }
        if (threatened && this.shieldCooldown == 0 && !this.hasEffect(MobEffects.RESISTANCE)) {
            this.shieldCooldown = SHIELD_COOLDOWN;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_DURATION, 0));
            if (rider != null) {
                rider.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_DURATION, 0), this);
            }
            this.animate(PhantomAnimationAction.SHIELD);
            this.playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.65F, 0.8F);
        }
    }

    boolean isValidWaterTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level() && target != this.getOwner()
                && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target)
                && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null
                        && owned.getOwnerReference().equals(this.getOwnerReference()))
                && !this.isTargetSuppressed(target);
    }

    private Vec3 getProjectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double offset = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * offset, this.getBbHeight() * 0.6D, Math.cos(yaw) * offset);
    }

    private boolean launchProjectile(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        WaterProjectile projectile = new WaterProjectile(PhantomismContent.WATER_PROJECTILE.get(), level);
        projectile.setOwner(this);
        projectile.setPos(this.getProjectileOrigin());
        projectile.shoot(direction.x, direction.y, direction.z, 1.3F, 0.0F);
        if (!level.addFreshEntity(projectile)) {
            return false;
        }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.6F, 1.3F);
        return true;
    }

    private Vec3 getBeamEnd(ServerLevel level, Vec3 origin, Vec3 direction) {
        Vec3 offset = direction.scale(ABILITY_RANGE);
        while (offset.lengthSqr() > 1.0D && !this.isLoadedFlightBox(new AABB(origin, origin.add(offset)).inflate(0.75D))) {
            offset = offset.scale(0.5D);
        }
        if (!this.isLoadedFlightBox(new AABB(origin, origin.add(offset)).inflate(0.75D))) {
            return origin;
        }
        var hit = level.clip(new ClipContext(origin, origin.add(offset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? origin.add(offset) : hit.getLocation();
    }

    private List<LivingEntity> findBeamTargets(ServerLevel level, Vec3 start, Vec3 end) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(0.75D),
                target -> this.isValidWaterTarget(target) && (target instanceof Enemy || target == this.getTarget())
                        && this.hasLineOfSight(target)
                        && target.getBoundingBox().inflate(0.65D).clip(start, end).isPresent());
    }

    private void tickHydroPump(ServerLevel level, LivingEntity target) {
        Vec3 origin = this.getProjectileOrigin();
        Vec3 direction = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin).normalize();
        if (this.hydroPumpTicks == 0) {
            if (this.hydroPumpCooldown > 0 || this.tickCount % 10 != 0 || !(target instanceof Enemy)
                    || this.distanceToSqr(target) > ABILITY_RANGE * ABILITY_RANGE || !this.hasLineOfSight(target)) {
                return;
            }
            Vec3 end = this.getBeamEnd(level, origin, direction);
            if (target.getMaxHealth() < 60.0F && this.findBeamTargets(level, origin, end).size() < 2) {
                return;
            }
            this.hydroPumpTicks = HYDRO_PUMP_DURATION;
            this.hydroPumpCooldown = HYDRO_PUMP_COOLDOWN;
            this.animate(PhantomAnimationAction.ULTIMATE);
            this.playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.65F);
        }
        Vec3 end = this.getBeamEnd(level, origin, direction);
        if (this.hydroPumpTicks % 10 == 0) {
            net.ronm19.phantomism.vfx.PhantomVisualEffects.beam(level,
                    net.ronm19.phantomism.vfx.PhantomVfxTheme.WATER, origin, end);
            for (LivingEntity victim : this.findBeamTargets(level, origin, end)) {
                if (victim.hurtServer(level, this.damageSources().mobAttack(this), 3.0F)) {
                    this.applyWaterKnockback(victim, direction, 0.85D);
                }
            }
        }
        if (this.hydroPumpTicks % 4 == 0) {
            Vec3 beam = end.subtract(origin);
            int segments = Math.max(1, (int) Math.ceil(beam.length() / 2.0D));
            for (int segment = 0; segment <= segments; segment++) {
                Vec3 point = origin.add(beam.scale((double) segment / segments));
                level.sendParticles(this.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH,
                        point.x, point.y, point.z, 3, 0.12D, 0.12D, 0.12D, 0.01D);
            }
        }
        this.hydroPumpTicks--;
    }

    void applyWaterKnockback(LivingEntity target, Vec3 direction, double strength) {
        double force = strength * (1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        target.push(direction.x * force, Mth.clamp(direction.y * force, -0.25D, 0.25D), direction.z * force);
        target.syncVelocity = true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("WaterProjectileCooldown", this.projectileCooldown);
        output.putInt("WaterShieldCooldown", this.shieldCooldown);
        output.putInt("HydroPumpCooldown", this.hydroPumpCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.projectileCooldown = Math.max(0, input.getIntOr("WaterProjectileCooldown", 0));
        this.shieldCooldown = Math.max(0, input.getIntOr("WaterShieldCooldown", 0));
        this.hydroPumpCooldown = Math.max(0, input.getIntOr("HydroPumpCooldown", 0));
        this.hydroPumpTicks = 0;
    }

    private static final class AquaticMoveControl extends MoveControl<WaterPhantom> {
        private final MoveControl<?> flight;

        private AquaticMoveControl(WaterPhantom phantom, MoveControl<?> flight) {
            super(phantom);
            this.flight = flight;
        }

        @Override
        public void setWantedPosition(double destinationX, double destinationY, double destinationZ, double speed) {
            super.setWantedPosition(destinationX, destinationY, destinationZ, speed);
            if (!this.mob.isInWater()) {
                this.flight.setWantedPosition(destinationX, destinationY, destinationZ, speed);
            }
        }

        @Override
        public void tick() {
            if (!this.mob.isInWater()) {
                this.flight.tick();
                return;
            }
            if (this.mob.isVehicle()) {
                return;
            }
            this.mob.setSpeed(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
            Vec3 desired = Vec3.ZERO;
            if (this.operation == Operation.MOVE_TO && this.speedModifier > 0.0D) {
                Vec3 offset = new Vec3(this.wantedX, this.wantedY, this.wantedZ).subtract(this.mob.position());
                double distance = offset.length();
                if (distance > 0.1D) {
                    Vec3 step = offset.scale(Math.min(3.0D, distance) / distance);
                    if (!this.mob.hasClearFlightCorridor(step)) {
                        Vec3 side = new Vec3(-step.z, 0.0D, step.x).normalize().scale(2.0D);
                        Vec3[] detours = {side, side.scale(-1.0D), new Vec3(0.0D, -1.5D, 0.0D), new Vec3(0.0D, 1.5D, 0.0D)};
                        step = Vec3.ZERO;
                        double closest = Double.MAX_VALUE;
                        for (Vec3 detour : detours) {
                            double remaining = offset.subtract(detour).lengthSqr();
                            if (remaining < closest && this.mob.hasClearFlightCorridor(detour)) {
                                step = detour;
                                closest = remaining;
                            }
                        }
                    }
                    if (step.lengthSqr() > 1.0E-6D) {
                        double speed = Math.min(distance * 0.25D,
                                this.speedModifier * this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 1.5D);
                        desired = step.normalize().scale(speed);
                        float yaw = (float) (Mth.atan2(desired.z, desired.x) * Mth.RAD_TO_DEG) - 90.0F;
                        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 12.0F));
                        this.mob.setXRot(0.0F);
                    }
                }
            }
            this.operation = Operation.WAIT;
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.add(desired.subtract(movement).scale(0.2D)));
        }
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Projectile Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(PROJECTILE_COOLDOWN), "Shield Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(SHIELD_COOLDOWN), "Hydro Pump Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(HYDRO_PUMP_COOLDOWN));
    }
}
