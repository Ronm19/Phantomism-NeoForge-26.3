package net.ronm19.phantomism.entity;

import net.ronm19.phantomism.animation.PhantomAnimationAction;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class FrostPhantom extends AbstractPhantom {
    private static final int ICE_PROJECTILE_COOLDOWN_TICKS = 12 * 20;
    private static final double ICE_PROJECTILE_RANGE = 24.0D;
    private static final int FROST_BITE_COOLDOWN_TICKS = 12 * 20;
    private static final int FREEZE_DURATION_TICKS = 8 * 20;
    private static final int RESISTANT_CONTROL_DURATION_TICKS = 2 * 20;
    private static final int THAW_RECOVERY_TICKS = 8 * 20;
    private static final int ICE_SHIELD_COOLDOWN_TICKS = 15 * 20;
    private static final int ICE_SHIELD_DURATION_TICKS = 10 * 20;
    private static final int ICE_SHIELD_STRENGTH_TICKS = 5 * 20;
    private static final int ICE_STORM_COOLDOWN_TICKS = 70 * 20;
    private static final int ICE_STORM_DURATION_TICKS = 12 * 20;
    private static final double ICE_STORM_RADIUS = 16.0D;
    private static final String CONTROL_IMMUNITY_KEY = "PhantomismFrostControlImmuneUntil";
    private static final Identifier COLD_MOVEMENT = id("frost_cold_movement");
    private static final Identifier COLD_FLIGHT = id("frost_cold_flight");
    private static final Identifier COLD_AWARENESS = id("frost_cold_awareness");
    private static final Identifier BITE_DAMAGE = id("frost_bite_damage");
    private static final Identifier STORM_DAMAGE = id("frost_storm_damage");

    private int iceProjectileCooldown;
    private int frostBiteCooldown;
    private int iceShieldCooldown;
    private int iceShieldTicks;
    private int iceStormCooldown;
    private int iceStormTicks;
    private UUID observedRider;
    private int riderHurtByTimestamp;

    public FrostPhantom(EntityType<? extends FrostPhantom> type, Level level) {
        super(type, level);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<FrostPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBiome(pos).is(ModTags.Biomes.FROST_PHANTOM_SPAWNS)
                && level.canSeeSky(pos)
                && level.getFluidState(pos).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.FROST_PHANTOM_STATS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.iceProjectileCooldown = Math.max(0, this.iceProjectileCooldown - 1);
        this.frostBiteCooldown = Math.max(0, this.frostBiteCooldown - 1);
        this.iceShieldCooldown = Math.max(0, this.iceShieldCooldown - 1);
        this.iceShieldTicks = Math.max(0, this.iceShieldTicks - 1);
        this.iceStormCooldown = Math.max(0, this.iceStormCooldown - 1);
        this.iceStormTicks = Math.max(0, this.iceStormTicks - 1);
        if (this.tickCount % 20 == 0) {
            this.updateColdAwareness();
        }
        this.observeRiderThreat();
        LivingEntity target = this.getTarget();
        if (this.isBaby() || !this.isValidFrostTarget(target)) {
            this.iceStormTicks = 0;
            this.updateStormDamage();
            return;
        }
        this.tickIceShield(target);
        if (this.getControllingPassenger() == null) {
            this.tickIceProjectile(level, target);
        }
        this.tickIceStorm(level);
        this.updateStormDamage();
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.launchIceProjectile(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        double range = this.isInColdBiome() ? 32.0D : 24.0D;
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD
                && owner != null && target instanceof Enemy && owner.distanceToSqr(target) <= range * range;
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        double priority = this.distanceToSqr(target);
        if (target.hasEffect(PhantomismContent.FROSTBOUND) || target.hasEffect(MobEffects.SLOWNESS)) {
            priority *= 0.55D;
        }
        if (!target.canFreeze() || target.is(ModTags.EntityTypes.FROST_RESISTANT)) {
            priority *= 1.5D;
        }
        return priority;
    }

    @Override
    protected int getMeleeAttackCooldownTicks() {
        return this.iceStormTicks > 0 ? 30 : super.getMeleeAttackCooldownTicks();
    }

    @Override
    protected int getAttackRecoveryTicks() {
        return this.iceStormTicks > 0 ? 9 : super.getAttackRecoveryTicks();
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void setIsInPowderSnow(boolean inPowderSnow) {
        super.setIsInPowderSnow(false);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        if (!state.is(Blocks.POWDER_SNOW)) {
            super.makeStuckInBlock(state, multiplier);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity victim) || !this.isValidFrostTarget(victim)) {
            return false;
        }
        boolean criticalBite = !this.isBaby() && this.isDivingStrike()
                && this.frostBiteCooldown == 0 && this.canApplyFrostControl(level, victim);
        boolean chilled = victim.hasEffect(PhantomismContent.FROSTBOUND) || victim.hasEffect(MobEffects.SLOWNESS);
        double bonusDamage = (criticalBite ? 4.0D : 0.0D) + (chilled ? 1.0D : 0.0D);
        if (bonusDamage > 0.0D) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                    BITE_DAMAGE, bonusDamage, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(BITE_DAMAGE);
        }
        if (hit && criticalBite) {
            this.frostBiteCooldown = FROST_BITE_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.CRITICAL_BITE);
            if (victim.isAlive()) {
                this.applyFrostControl(level, victim);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5D), victim.getZ(),
                    16, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        return hit;
    }

    boolean isValidFrostTarget(LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved()
                && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target)
                && !(target instanceof Player player && this.isOwnedBy(player))
                && !this.isTargetSuppressed(target);
    }

    private boolean canApplyFrostControl(ServerLevel level, LivingEntity target) {
        return this.isValidFrostTarget(target) && !target.hasEffect(PhantomismContent.FROSTBOUND)
                && target.getPersistentData().getLongOr(CONTROL_IMMUNITY_KEY, 0L) <= level.getGameTime();
    }

    boolean applyFrostControl(ServerLevel level, LivingEntity target) {
        if (!this.canApplyFrostControl(level, target)) {
            return false;
        }
        boolean resistant = !target.canFreeze() || target.is(ModTags.EntityTypes.FROST_RESISTANT) || target.getMaxHealth() >= 80.0F;
        int duration = resistant ? RESISTANT_CONTROL_DURATION_TICKS : FREEZE_DURATION_TICKS;
        MobEffectInstance effect = new MobEffectInstance(
                resistant ? MobEffects.SLOWNESS : PhantomismContent.FROSTBOUND, duration, 0);
        if (!target.addEffect(effect, this)) {
            return false;
        }
        target.getPersistentData().putLong(CONTROL_IMMUNITY_KEY,
                level.getGameTime() + duration + THAW_RECOVERY_TICKS);
        if (!resistant) {
            target.setTicksFrozen(target.getTicksRequiredToFreeze());
        }
        return true;
    }

    private boolean isInColdBiome() {
        return this.level().getBiome(this.blockPosition()).value().getBaseTemperature() <= 0.15F;
    }

    private void updateColdAwareness() {
        if (this.isInColdBiome()) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(new AttributeModifier(
                    COLD_MOVEMENT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            this.getAttribute(Attributes.FLYING_SPEED).addOrUpdateTransientModifier(new AttributeModifier(
                    COLD_FLIGHT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            this.getAttribute(Attributes.FOLLOW_RANGE).addOrUpdateTransientModifier(new AttributeModifier(
                    COLD_AWARENESS, 12.0D, AttributeModifier.Operation.ADD_VALUE));
        } else {
            this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(COLD_MOVEMENT);
            this.getAttribute(Attributes.FLYING_SPEED).removeModifier(COLD_FLIGHT);
            this.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(COLD_AWARENESS);
        }
    }

    private void observeRiderThreat() {
        if (!(this.getControllingPassenger() instanceof Player rider)) {
            this.observedRider = null;
            return;
        }
        if (!rider.getUUID().equals(this.observedRider)) {
            this.observedRider = rider.getUUID();
            this.riderHurtByTimestamp = rider.getLastHurtByMobTimestamp();
        } else if (rider.getLastHurtByMobTimestamp() != this.riderHurtByTimestamp) {
            this.riderHurtByTimestamp = rider.getLastHurtByMobTimestamp();
            if (this.isValidFrostTarget(rider.getLastHurtByMob())) {
                this.setCombatTarget(rider.getLastHurtByMob());
            }
        }
    }

    private void tickIceShield(LivingEntity target) {
        boolean threatened = this.getHealth() <= this.getMaxHealth() * 0.6F || this.distanceToSqr(target) <= 36.0D;
        if (this.getControllingPassenger() instanceof Player rider) {
            threatened |= rider.getHealth() <= rider.getMaxHealth() * 0.6F
                    || rider.getLastHurtByMob() != null && rider.tickCount - rider.getLastHurtByMobTimestamp() <= 60;
        }
        if (threatened && this.iceShieldCooldown == 0 && this.iceShieldTicks == 0) {
            this.iceShieldTicks = ICE_SHIELD_DURATION_TICKS;
            this.iceShieldCooldown = ICE_SHIELD_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.SHIELD);
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ICE_SHIELD_DURATION_TICKS, 0));
            this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ICE_SHIELD_STRENGTH_TICKS, 0));
            this.playSound(SoundEvents.GLASS_PLACE, 0.7F, 1.3F);
        }
    }

    private Vec3 getProjectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double offset = this.getBbWidth() * 0.6D + 0.15D;
        return this.position().add(-Math.sin(yaw) * offset, this.getBbHeight() * 0.6D, Math.cos(yaw) * offset);
    }

    private void tickIceProjectile(ServerLevel level, LivingEntity target) {
        if (this.iceProjectileCooldown > 0 || this.distanceToSqr(target) > ICE_PROJECTILE_RANGE * ICE_PROJECTILE_RANGE
                || !this.hasLineOfSight(target) || !this.canApplyFrostControl(level, target)) {
            return;
        }
        if (this.launchIceProjectile(level, target.getEyePosition().subtract(this.getProjectileOrigin()))) {
            this.iceProjectileCooldown = ICE_PROJECTILE_COOLDOWN_TICKS;
        }
    }

    private boolean launchIceProjectile(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        FrostProjectile projectile = new FrostProjectile(PhantomismContent.FROST_PROJECTILE.get(), level);
        projectile.setOwner(this);
        projectile.setPos(this.getProjectileOrigin());
        projectile.shoot(direction.x, direction.y, direction.z, 1.3F, 0.0F);
        if (!level.addFreshEntity(projectile)) {
            return false;
        }
        this.playSound(SoundEvents.SNOWBALL_THROW, 1.0F, 0.7F);
        this.animate(PhantomAnimationAction.PROJECTILE);
        return true;
    }

    private List<LivingEntity> findStormTargets(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(ICE_STORM_RADIUS),
                target -> target instanceof Enemy && this.isValidFrostTarget(target)
                        && this.distanceToSqr(target) <= ICE_STORM_RADIUS * ICE_STORM_RADIUS
                        && this.hasLineOfSight(target));
    }

    private void tickIceStorm(ServerLevel level) {
        if (this.iceStormTicks == 0 && this.iceStormCooldown == 0 && this.findStormTargets(level).size() >= 3) {
            this.iceStormTicks = ICE_STORM_DURATION_TICKS;
            this.iceStormCooldown = ICE_STORM_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.ULTIMATE);
            this.playSound(SoundEvents.GLASS_BREAK, 1.0F, 0.65F);
        }
        if (this.iceStormTicks > 0 && this.iceStormTicks % 40 == 0) {
            net.ronm19.phantomism.vfx.PhantomVisualEffects.shockwave(level,
                    net.ronm19.phantomism.vfx.PhantomVfxTheme.FROST, this.position(), 5.0F);
            net.ronm19.phantomism.vfx.PhantomVisualEffects.aura(level,
                    net.ronm19.phantomism.vfx.PhantomVfxTheme.FROST, this.position().add(0.0D, 0.5D, 0.0D));
            for (LivingEntity target : this.findStormTargets(level)) {
                if (target.hurtServer(level, this.damageSources().mobAttack(this), 2.0F)) {
                    this.applyFrostControl(level, target);
                }
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(0.5D), this.getZ(),
                    80, 6.0D, 2.5D, 6.0D, 0.04D);
        }
    }

    private void updateStormDamage() {
        if (this.iceStormTicks > 0) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                    STORM_DAMAGE, 2.0D, AttributeModifier.Operation.ADD_VALUE));
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(STORM_DAMAGE);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("IceProjectileCooldown", this.iceProjectileCooldown);
        output.putInt("FrostBiteCooldown", this.frostBiteCooldown);
        output.putInt("IceShieldCooldown", this.iceShieldCooldown);
        output.putInt("IceShieldTicks", this.iceShieldTicks);
        output.putInt("IceStormCooldown", this.iceStormCooldown);
        output.putInt("IceStormTicks", this.iceStormTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.iceProjectileCooldown = Math.max(0, input.getIntOr("IceProjectileCooldown", 0));
        this.frostBiteCooldown = Math.max(0, input.getIntOr("FrostBiteCooldown", 0));
        this.iceShieldCooldown = Math.max(0, input.getIntOr("IceShieldCooldown", 0));
        this.iceShieldTicks = Math.min(ICE_SHIELD_DURATION_TICKS, Math.max(0, input.getIntOr("IceShieldTicks", 0)));
        this.iceStormCooldown = Math.max(0, input.getIntOr("IceStormCooldown", 0));
        this.iceStormTicks = Math.min(ICE_STORM_DURATION_TICKS, Math.max(0, input.getIntOr("IceStormTicks", 0)));
    }
}
