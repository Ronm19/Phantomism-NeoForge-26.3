package net.ronm19.phantomism.entity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import org.jspecify.annotations.Nullable;

public final class StormPhantom extends AbstractPhantom {
    private static final EntityDataAccessor<Integer> STORM_CHARGE = SynchedEntityData.defineId(
            StormPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEATHER_POWER = SynchedEntityData.defineId(
            StormPhantom.class, EntityDataSerializers.INT);
    private static final Identifier WEATHER_MOVEMENT = id("storm_weather_movement");
    private static final Identifier WEATHER_FLIGHT = id("storm_weather_flight");
    private static final Identifier WEATHER_AWARENESS = id("storm_weather_awareness");
    private static final int PROJECTILE_COOLDOWN = 12 * 20;
    private static final int SHIELD_COOLDOWN = 20 * 20;
    private static final int ULTIMATE_COOLDOWN = 75 * 20;
    private static final int ULTIMATE_DURATION = 11 * 20;
    private static final double PROJECTILE_RANGE = StormProjectile.MAX_TRAVEL_DISTANCE;
    private static final double CHAIN_RADIUS = 4.0D;
    private static final double ULTIMATE_RADIUS = 24.0D;
    private int projectileCooldown;
    private int shieldCooldown;
    private int ultimateCooldown;
    private int ultimateTicks;
    private int ultimateCharge;
    private int recentDamageTicks;
    private int observedOwnerThreat;

    public StormPhantom(EntityType<? extends StormPhantom> type, Level level) {
        super(type, level);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 38.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 3.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<StormPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.STORM_PHANTOM_SPAWNS)
                && level.canSeeSky(position) && level.getFluidState(position).isEmpty()
                && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.STORM_PHANTOM_STATS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STORM_CHARGE, 0);
        builder.define(WEATHER_POWER, 0);
    }

    public int getStormCharge() {
        return this.entityData.get(STORM_CHARGE);
    }

    public int getWeatherPower() {
        return this.entityData.get(WEATHER_POWER);
    }

    private void setStormCharge(int charge) {
        this.entityData.set(STORM_CHARGE, Math.clamp(charge, 0, 3));
    }

    public static @Nullable StormPhantom findNaturalLightningAbsorber(ServerLevel level, LightningBolt lightning) {
        if (!level.isThundering() || lightning.getCause() != null
                || level.getBlockState(lightning.blockPosition().below()).is(BlockTags.LIGHTNING_RODS)) {
            return null;
        }
        return level.getEntitiesOfClass(StormPhantom.class, lightning.getBoundingBox().inflate(24.0D),
                        phantom -> phantom.isAlive() && !phantom.isBaby() && level.isRainingAt(phantom.blockPosition())
                                && phantom.distanceToSqr(lightning) <= 24.0D * 24.0D)
                .stream().min(Comparator.comparingDouble(phantom -> phantom.distanceToSqr(lightning))).orElse(null);
    }

    public void absorbNaturalLightning(LightningBolt lightning) {
        if (!(this.level() instanceof ServerLevel level) || this.isBaby()
                || lightning.getPersistentData().getBooleanOr("PhantomismStormAbsorbed", false)) {
            return;
        }
        lightning.getPersistentData().putBoolean("PhantomismStormAbsorbed", true);
        this.setStormCharge(this.getStormCharge() + 1);
        this.animate(PhantomAnimationAction.EMPOWER);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5D), this.getZ(),
                18, 0.6D, 0.4D, 0.6D, 0.03D);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && damage >= 2.0F) {
            this.recentDamageTicks = 60;
        }
        return hurt;
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.launchJolt(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD
                && owner != null && target instanceof Enemy && owner.distanceToSqr(target) <= range * range;
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        double distance = owner != null ? owner.distanceToSqr(target) : this.distanceToSqr(target);
        return target instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == this || mob.getTarget() == owner
                || mob.getTarget() == this.getControllingPassenger()) ? distance * 0.25D : distance;
    }

    @Override
    protected int getMeleeAttackCooldownTicks() {
        return this.getWeatherPower() == 2 ? 30 : this.getWeatherPower() == 1 ? 36 : super.getMeleeAttackCooldownTicks();
    }

    @Override
    protected int getAttackRecoveryTicks() {
        return this.getWeatherPower() == 2 ? 10 : this.getWeatherPower() == 1 ? 14 : super.getAttackRecoveryTicks();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.projectileCooldown = Math.max(0, this.projectileCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.ultimateCooldown = Math.max(0, this.ultimateCooldown - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (this.tickCount % 20 == 0) {
            this.updateWeatherPower(level);
            if (this.getStormCharge() > 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5D), this.getZ(),
                        this.getStormCharge(), 0.5D, 0.25D, 0.5D, 0.01D);
            }
        }
        this.observeOwnerThreat();
        LivingEntity target = this.getTarget();
        if (this.isBaby() || !this.isValidStormTarget(target)) {
            this.ultimateTicks = 0;
            return;
        }
        this.tickThunderShield(target);
        this.tickThunderstorm(level);
        if (this.getControllingPassenger() == null && this.ultimateTicks == 0 && this.projectileCooldown == 0
                && this.distanceToSqr(target) <= PROJECTILE_RANGE * PROJECTILE_RANGE && this.hasLineOfSight(target)
                && this.launchJolt(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
            this.projectileCooldown = PROJECTILE_COOLDOWN;
        }
    }

    private void updateWeatherPower(ServerLevel level) {
        int power = !this.isBaby() && level.isRainingAt(this.blockPosition()) ? level.isThundering() ? 2 : 1 : 0;
        if (power > this.getWeatherPower()) {
            this.animate(PhantomAnimationAction.SENSE);
        }
        this.entityData.set(WEATHER_POWER, power);
        this.weatherModifier(Attributes.MOVEMENT_SPEED, WEATHER_MOVEMENT, power * 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.weatherModifier(Attributes.FLYING_SPEED, WEATHER_FLIGHT, power * 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.weatherModifier(Attributes.FOLLOW_RANGE, WEATHER_AWARENESS, power * 8.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private void weatherModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            Identifier name, double amount, AttributeModifier.Operation operation) {
        var instance = this.getAttribute(attribute);
        if (amount == 0.0D) {
            instance.removeModifier(name);
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(name, amount, operation));
        }
    }

    private void observeOwnerThreat() {
        LivingEntity owner = this.getOwner();
        if (owner == null || owner.getLastHurtByMobTimestamp() == this.observedOwnerThreat) {
            return;
        }
        this.observedOwnerThreat = owner.getLastHurtByMobTimestamp();
        LivingEntity attacker = owner.getLastHurtByMob();
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (!this.isBaby() && this.isValidStormTarget(attacker) && this.distanceToSqr(attacker) <= range * range
                && this.getAssignments().permitsDefensivePursuit(attacker)) {
            this.setCombatTarget(attacker);
        }
    }

    boolean isValidStormTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target)
                && !this.isTargetSuppressed(target);
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double distance = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * distance, this.getBbHeight() * 0.6D, Math.cos(yaw) * distance);
    }

    private boolean launchJolt(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        StormProjectile projectile = new StormProjectile(PhantomismContent.STORM_PROJECTILE.get(), level);
        projectile.setOwner(this);
        projectile.setStormCharge(this.getStormCharge());
        projectile.setPos(this.projectileOrigin());
        projectile.shoot(direction.x, direction.y, direction.z, 1.5F, 0.0F);
        if (!level.addFreshEntity(projectile)) {
            return false;
        }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.TRIDENT_THROW.value(), 0.7F, 1.5F);
        return true;
    }

    void strikeJolt(ServerLevel level, LivingEntity primary, int charge, Entity projectile, Vec3 origin) {
        if (!this.isValidStormTarget(primary)) {
            return;
        }
        Set<UUID> struck = new HashSet<>();
        LivingEntity current = primary;
        Vec3 previous = origin;
        charge = Math.clamp(charge, 0, 3);
        int maximum = charge > 0 ? 5 : 4;
        for (int hop = 0; hop < maximum && current != null; hop++) {
            struck.add(current.getUUID());
            float damage = hop == 0 ? 5.0F + charge : 3.0F + charge * 0.5F;
            if (!current.hurtServer(level, this.damageSources().thrown(projectile, this), damage)) {
                break;
            }
            Vec3 end = current.position().add(0.0D, current.getBbHeight() * 0.5D, 0.0D);
            this.drawArc(level, previous, end);
            LivingEntity last = current;
            previous = end;
            current = level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(CHAIN_RADIUS),
                            candidate -> candidate instanceof Enemy && this.isValidStormTarget(candidate)
                                    && !struck.contains(candidate.getUUID()) && last.distanceToSqr(candidate) <= CHAIN_RADIUS * CHAIN_RADIUS
                                    && last.hasLineOfSight(candidate) && this.distanceToSqr(candidate) <= 36.0D * 36.0D)
                    .stream().min(Comparator.comparingDouble(candidate -> last.distanceToSqr(candidate))).orElse(null);
        }
    }

    private void tickThunderShield(LivingEntity target) {
        LivingEntity rider = this.getControllingPassenger();
        boolean threatened = this.recentDamageTicks > 0 || this.getHealth() <= this.getMaxHealth() * 0.6F
                || target instanceof Mob mob && mob.getTarget() != null && this.distanceToSqr(target) <= 36.0D
                        && (mob.getTarget() == this || mob.getTarget() == this.getOwner() || rider != null && mob.getTarget() == rider);
        if (rider != null) {
            threatened |= rider.getHealth() <= rider.getMaxHealth() * 0.6F
                    || rider.getLastHurtByMob() == target && rider.tickCount - rider.getLastHurtByMobTimestamp() <= 60;
        }
        if (!threatened || this.shieldCooldown > 0 || this.hasEffect(MobEffects.RESISTANCE)) {
            return;
        }
        boolean charged = this.getStormCharge() > 0;
        int duration = charged ? 200 : 160;
        int amplifier = charged ? 1 : 0;
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, amplifier));
        if (rider != null) {
            rider.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, amplifier), this);
        }
        this.shieldCooldown = SHIELD_COOLDOWN;
        this.animate(PhantomAnimationAction.SHIELD);
        this.playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.35F, 1.7F);
    }

    private List<LivingEntity> thunderstormTargets(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(ULTIMATE_RADIUS),
                        target -> target instanceof Enemy && this.isValidStormTarget(target)
                                && this.distanceToSqr(target) <= ULTIMATE_RADIUS * ULTIMATE_RADIUS && this.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(8).toList();
    }

    private void tickThunderstorm(ServerLevel level) {
        List<LivingEntity> targets = List.of();
        if (this.ultimateTicks == 0) {
            if (this.ultimateCooldown > 0 || this.tickCount % 10 != 0) {
                return;
            }
            targets = this.thunderstormTargets(level);
            if (targets.size() < 3) {
                return;
            }
            this.ultimateTicks = ULTIMATE_DURATION;
            this.ultimateCooldown = ULTIMATE_COOLDOWN;
            this.ultimateCharge = this.getStormCharge();
            this.animate(PhantomAnimationAction.ULTIMATE);
            this.playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.7F, 1.0F);
        }
        if (this.ultimateTicks % 20 == 0) {
            if (targets.isEmpty()) {
                targets = this.thunderstormTargets(level);
            }
            Vec3 origin = this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
            for (LivingEntity enemy : targets) {
                if (enemy.hurtServer(level, this.damageSources().mobAttack(this), 3.0F + this.ultimateCharge)) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
                    this.drawArc(level, origin, enemy.position().add(0.0D, enemy.getBbHeight() * 0.5D, 0.0D));
                }
            }
        }
        this.ultimateTicks--;
    }

    private void drawArc(ServerLevel level, Vec3 start, Vec3 end) {
        net.ronm19.phantomism.vfx.PhantomVisualEffects.beam(level,
                net.ronm19.phantomism.vfx.PhantomVfxTheme.STORM, start, end);
        Vec3 offset = end.subtract(start);
        int steps = Math.clamp((int) Math.ceil(offset.length() / 2.0D), 1, 12);
        for (int step = 0; step <= steps; step++) {
            Vec3 position = start.add(offset.scale((double) step / steps));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, position.x, position.y, position.z,
                    1, 0.07D, 0.07D, 0.07D, 0.01D);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StormCharge", this.getStormCharge());
        output.putInt("StormProjectileCooldown", this.projectileCooldown);
        output.putInt("StormShieldCooldown", this.shieldCooldown);
        output.putInt("StormUltimateCooldown", this.ultimateCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setStormCharge(input.getIntOr("StormCharge", 0));
        this.projectileCooldown = Math.clamp(input.getIntOr("StormProjectileCooldown", 0), 0, PROJECTILE_COOLDOWN);
        this.shieldCooldown = Math.clamp(input.getIntOr("StormShieldCooldown", 0), 0, SHIELD_COOLDOWN);
        this.ultimateCooldown = Math.clamp(input.getIntOr("StormUltimateCooldown", 0), 0, ULTIMATE_COOLDOWN);
        this.ultimateTicks = 0;
        this.ultimateCharge = 0;
    }
}
