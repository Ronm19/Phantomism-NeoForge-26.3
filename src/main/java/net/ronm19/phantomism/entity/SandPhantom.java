package net.ronm19.phantomism.entity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import org.jspecify.annotations.Nullable;

public final class SandPhantom extends AbstractPhantom {
    private static final Identifier DESERT_MOVEMENT = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sand_desert_movement");
    private static final Identifier DESERT_FLIGHT = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sand_desert_flight");
    private static final Identifier DIVE_DAMAGE = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sand_dive_damage");
    private static final String BLINDNESS_IMMUNITY = "PhantomismSandBlindnessUntil";
    private static final int BOLT_COOLDOWN = 12 * 20;
    private static final int DIVE_COOLDOWN = 15 * 20;
    private static final int SANDSTORM_COOLDOWN = 70 * 20;
    private static final int SANDSTORM_DURATION = 10 * 20;
    private static final double BOLT_RANGE = SandBolt.MAX_TRAVEL_DISTANCE;
    private static final double SANDSTORM_RADIUS = 12.0D;
    private final Set<UUID> stormDustedTargets = new HashSet<>();
    private int boltCooldown;
    private int diveCooldown;
    private int sandstormCooldown;
    private int sandstormTicks;
    private boolean desertAdapted;

    public SandPhantom(EntityType<? extends SandPhantom> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 3.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<SandPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.SAND_PHANTOM_SPAWNS)
                && level.canSeeSky(position) && level.getFluidState(position).isEmpty()
                && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.SAND_PHANTOM_STATS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypes.CACTUS)) {
            return false;
        }
        return super.hurtServer(level, source,
                source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.HOT_FLOOR) ? damage * 0.5F : damage);
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.launchSandBolt(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        double awareness = Math.min(24.0D, this.getAttributeValue(Attributes.FOLLOW_RANGE));
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD
                && owner != null && target instanceof Enemy && owner.distanceToSqr(target) <= awareness * awareness;
    }

    private boolean threatensFamily(LivingEntity target) {
        return target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()))
                || target == this.getLastHurtByMob();
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        double distance = this.getOwner() != null ? this.getOwner().distanceToSqr(target) : this.distanceToSqr(target);
        return this.threatensFamily(target) ? distance * 0.25D : distance;
    }

    @Override
    protected double getFlightAltitudeOffset(double offset) {
        return this.desertAdapted ? Math.max(1.5D, offset * 0.65D) : offset;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || !(target instanceof LivingEntity victim) || !this.isValidSandTarget(victim)) {
            return false;
        }
        boolean sandDive = this.isDivingStrike() && this.diveCooldown == 0
                && (victim.hasEffect(MobEffects.SLOWNESS) || this.threatensFamily(victim)
                        || victim.getHealth() <= victim.getMaxHealth() * 0.5F);
        if (sandDive) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                    DIVE_DAMAGE, 5.0D, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (sandDive) {
                this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_DAMAGE);
            }
        }
        if (hit && sandDive) {
            this.diveCooldown = DIVE_COOLDOWN;
            this.applySandDisruption(level, victim, 60, 20);
            this.animate(PhantomAnimationAction.CHARGE);
            this.sandParticles(level, victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), 24, 0.6D);
            this.playSound(SoundEvents.SAND_BREAK, 1.0F, 0.65F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.boltCooldown = Math.max(0, this.boltCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1);
        this.sandstormCooldown = Math.max(0, this.sandstormCooldown - 1);
        if (this.tickCount % 20 == 0) {
            this.updateDesertAdaptation(level);
        }
        LivingEntity target = this.getTarget();
        if (this.isBaby() || !this.isValidSandTarget(target)) {
            this.sandstormTicks = 0;
            this.stormDustedTargets.clear();
            return;
        }
        this.tickSandstorm(level);
        if (this.getControllingPassenger() == null && this.sandstormTicks == 0 && this.boltCooldown == 0
                && this.distanceToSqr(target) <= BOLT_RANGE * BOLT_RANGE && this.hasLineOfSight(target)
                && this.launchSandBolt(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
            this.boltCooldown = BOLT_COOLDOWN;
        }
    }

    private void updateDesertAdaptation(ServerLevel level) {
        this.desertAdapted = false;
        if (level.getBiome(this.blockPosition()).is(ModTags.Biomes.SAND_PHANTOM_SPAWNS)) {
            for (int depth = 1; depth <= 5; depth++) {
                BlockPos ground = this.blockPosition().below(depth);
                if (!this.isLoadedFlightBox(new AABB(ground))) {
                    break;
                }
                if (level.getBlockState(ground).is(BlockTags.SAND)) {
                    this.desertAdapted = true;
                    break;
                }
                if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
                    break;
                }
            }
        }
        this.desertModifier(Attributes.MOVEMENT_SPEED, DESERT_MOVEMENT);
        this.desertModifier(Attributes.FLYING_SPEED, DESERT_FLIGHT);
    }

    private void desertModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
        var instance = this.getAttribute(attribute);
        if (this.desertAdapted) {
            if (!instance.hasModifier(id)) {
                instance.addTransientModifier(new AttributeModifier(id, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            instance.removeModifier(id);
        }
    }

    boolean isValidSandTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target)
                && !this.isTargetSuppressed(target);
    }

    void applySandDisruption(ServerLevel level, LivingEntity target, int slowTicks, int blindTicks) {
        if (!this.isValidSandTarget(target)) {
            return;
        }
        boolean resistant = target.is(ModTags.EntityTypes.SAND_CONTROL_RESISTANT) || target.getMaxHealth() >= 80.0F;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, resistant ? Math.min(20, slowTicks) : slowTicks, 0), this);
        if (!resistant && blindTicks > 0 && target.getPersistentData().getLongOr(BLINDNESS_IMMUNITY, 0L) <= level.getGameTime()) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0), this);
            target.getPersistentData().putLong(BLINDNESS_IMMUNITY, level.getGameTime() + 120L);
        }
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double distance = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * distance, this.getBbHeight() * 0.6D, Math.cos(yaw) * distance);
    }

    private boolean launchSandBolt(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        SandBolt bolt = new SandBolt(PhantomismContent.SAND_BOLT.get(), level);
        bolt.setOwner(this);
        bolt.setPos(this.projectileOrigin());
        bolt.shoot(direction.x, direction.y, direction.z, 1.4F, 0.0F);
        if (!level.addFreshEntity(bolt)) {
            return false;
        }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.SNOWBALL_THROW, 0.8F, 0.7F);
        return true;
    }

    private List<LivingEntity> sandstormTargets(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SANDSTORM_RADIUS),
                        target -> target instanceof Enemy && this.isValidSandTarget(target)
                                && this.distanceToSqr(target) <= SANDSTORM_RADIUS * SANDSTORM_RADIUS && this.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(8).toList();
    }

    private void tickSandstorm(ServerLevel level) {
        List<LivingEntity> targets = List.of();
        if (this.sandstormTicks == 0) {
            if (this.sandstormCooldown > 0 || this.tickCount % 10 != 0) {
                return;
            }
            targets = this.sandstormTargets(level);
            if (targets.size() < 3 && !(targets.size() >= 2 && this.getHealth() <= this.getMaxHealth() * 0.5F)) {
                return;
            }
            this.sandstormTicks = SANDSTORM_DURATION;
            this.sandstormCooldown = SANDSTORM_COOLDOWN;
            this.stormDustedTargets.clear();
            this.animate(PhantomAnimationAction.ULTIMATE);
            this.playSound(SoundEvents.SAND_BREAK, 1.0F, 0.4F);
        }
        if (this.sandstormTicks % 20 == 0) {
            if (targets.isEmpty()) {
                targets = this.sandstormTargets(level);
            }
            for (LivingEntity enemy : targets) {
                if (enemy.hurtServer(level, this.damageSources().mobAttack(this), 2.0F)) {
                    this.applySandDisruption(level, enemy, 40, this.stormDustedTargets.add(enemy.getUUID()) ? 40 : 0);
                    this.sandParticles(level, enemy.position().add(0.0D, enemy.getBbHeight() * 0.5D, 0.0D), 12, 0.6D);
                }
            }
        }
        if (this.sandstormTicks % 20 == 0) {
            PhantomVisualEffects.sandstormCloud(level, this.position().add(0.0D, 0.4D, 0.0D));
        }
        if (--this.sandstormTicks == 0) {
            this.stormDustedTargets.clear();
        }
    }

    private void sandParticles(ServerLevel level, Vec3 position, int count, double spread) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                position.x, position.y, position.z, count, spread, spread * 0.6D, spread, 0.03D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SandBoltCooldown", this.boltCooldown);
        output.putInt("SandDiveCooldown", this.diveCooldown);
        output.putInt("SandstormCooldown", this.sandstormCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.boltCooldown = Math.clamp(input.getIntOr("SandBoltCooldown", 0), 0, BOLT_COOLDOWN);
        this.diveCooldown = Math.clamp(input.getIntOr("SandDiveCooldown", 0), 0, DIVE_COOLDOWN);
        this.sandstormCooldown = Math.clamp(input.getIntOr("SandstormCooldown", 0), 0, SANDSTORM_COOLDOWN);
        this.sandstormTicks = 0;
        this.stormDustedTargets.clear();
    }
}
