package net.ronm19.phantomism.entity;

import net.ronm19.phantomism.animation.PhantomAnimationAction;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;

public final class FirePhantom extends AbstractPhantom {
    // Mounted G: one faster projectile per key press, with a server-enforced 0.4s cooldown.
    private static final int MOUNTED_FIREBALL_COOLDOWN_TICKS = 8;
    private static final double MOUNTED_FIREBALL_INITIAL_SPEED = 0.7D;
    private static final double MOUNTED_FIREBALL_ACCELERATION = 0.18D;
    private static final int IGNITION_BURN_SECONDS = 5;
    private static final int FIRE_CHARGE_COOLDOWN_TICKS = 8 * 20;
    private static final float FIRE_CHARGE_BONUS_DAMAGE = 4.0F;
    private static final Identifier FIRE_CHARGE_DAMAGE = Identifier.fromNamespaceAndPath(
            Phantomism.MOD_ID, "fire_charge_damage");
    private static final int FIRE_VEIL_COOLDOWN_TICKS = 25 * 20;
    private static final int FIRE_VEIL_DURATION_TICKS = 8 * 20;
    private static final double FIRE_VEIL_SENSE_RADIUS = 12.0D;
    private static final int FIRE_PROJECTILE_COOLDOWN_TICKS = 10 * 20;
    private static final double FIRE_PROJECTILE_RANGE = 28.0D;
    private static final int FIRE_SHIELD_COOLDOWN_TICKS = 15 * 20;
    private static final int FIRE_SHIELD_DURATION_TICKS = 12 * 20;
    private static final int FIRE_SHIELD_STRENGTH_TICKS = 8 * 20;
    private static final int FIRESTORM_COOLDOWN_TICKS = 75 * 20;
    private static final int FIRESTORM_DURATION_TICKS = 12 * 20;
    private static final int FIRESTORM_PULSE_INTERVAL = 48;
    private static final double FIRESTORM_RADIUS = 8.0D;
    private static final int FIRESTORM_MINIMUM_TARGETS = 3;
    private static final int FIRESTORM_BURN_SECONDS = 5;
    private static final float FIRESTORM_PULSE_DAMAGE = 3.0F;
    private static final int THERMAL_MELT_INTERVAL_TICKS = 10;
    private static final int THERMAL_MELT_RADIUS = 3;

    private int fireChargeCooldown;
    private int fireVeilCooldown;
    private int fireVeilTicks;
    private int fireProjectileCooldown;
    private int fireShieldCooldown;
    private int fireShieldTicks;
    private int firestormCooldown;
    private int firestormTicks;
    private UUID observedRider;
    private int riderHurtTargetTimestamp;
    private int riderHurtByTimestamp;

    public FirePhantom(EntityType<? extends FirePhantom> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.FIRE_PHANTOM_STATS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.tickCooldowns();
        // Preserve the original environmental mechanic and rider protection hooks.
        this.tickThermalMelt(serverLevel);
        this.updateRiderTarget();

        LivingEntity target = this.getTarget();
        if (!this.isValidFireTarget(target)) {
            // An explicit Staff Clear Target must not leave a damaging area running.
            this.firestormTicks = 0;
            this.fireVeilTicks = 0;
            return;
        }

        this.tickFireVeil(serverLevel, target);
        if (this.getControllingPassenger() == null) {
            this.tickFireProjectile(serverLevel, target);
        }
        this.tickFireShield(serverLevel, target);
        this.tickFirestorm(serverLevel, target);
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected int getRiddenProjectileAbilityCooldownTicks() {
        return MOUNTED_FIREBALL_COOLDOWN_TICKS;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel serverLevel, Player rider) {
        return this.launchFireProjectile(serverLevel, rider.getViewVector(1.0F), true);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void setIsInPowderSnow(boolean isInPowderSnow) {
        super.setIsInPowderSnow(false);
    }

    @Override
    public void makeStuckInBlock(BlockState blockState, Vec3 speedMultiplier) {
        if (!blockState.is(Blocks.POWDER_SNOW)) {
            super.makeStuckInBlock(blockState, speedMultiplier);
        }
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel serverLevel) {
        if (!(target instanceof Enemy)) {
            return false;
        }

        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            return this.getOrder() == Order.GUARD
                    && owner != null
                    && owner.distanceToSqr(target) <= 576.0D;
        }

        return serverLevel.isDarkOutside() && this.distanceToSqr(target) <= 576.0D;
    }

    @Override
    public boolean doHurtTarget(ServerLevel serverLevel, Entity target) {
        if (!(target instanceof LivingEntity livingTarget) || !this.isValidFireTarget(livingTarget)) {
            return false;
        }

        boolean divingCharge = this.isDivingStrike() && this.fireChargeCooldown == 0;
        if (divingCharge) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                    FIRE_CHARGE_DAMAGE, FIRE_CHARGE_BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean hit;
        try {
            hit = super.doHurtTarget(serverLevel, target);
        } finally {
            if (divingCharge) {
                this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(FIRE_CHARGE_DAMAGE);
            }
        }
        if (!hit) {
            return false;
        }

        // Ignition is an actual bite passive rather than an unrelated free ranged hit.
        livingTarget.igniteForSeconds(IGNITION_BURN_SECONDS);
        if (divingCharge) {
            this.fireChargeCooldown = FIRE_CHARGE_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.CHARGE);
            serverLevel.sendParticles(ParticleTypes.FLAME, livingTarget.getX(),
                    livingTarget.getY(0.5D), livingTarget.getZ(), 16,
                    0.4D, 0.4D, 0.4D, 0.05D);
            this.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
        }
        return true;
    }

    private void tickCooldowns() {
        this.fireChargeCooldown = Math.max(0, this.fireChargeCooldown - 1);
        this.fireVeilCooldown = Math.max(0, this.fireVeilCooldown - 1);
        this.fireVeilTicks = Math.max(0, this.fireVeilTicks - 1);
        this.fireProjectileCooldown = Math.max(0, this.fireProjectileCooldown - 1);
        this.fireShieldCooldown = Math.max(0, this.fireShieldCooldown - 1);
        this.fireShieldTicks = Math.max(0, this.fireShieldTicks - 1);
        this.firestormCooldown = Math.max(0, this.firestormCooldown - 1);
        this.firestormTicks = Math.max(0, this.firestormTicks - 1);
    }

    private void tickThermalMelt(ServerLevel serverLevel) {
        if (this.tickCount % THERMAL_MELT_INTERVAL_TICKS != 0
                || !serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }

        BlockPos origin = this.blockPosition();
        for (int offsetX = -THERMAL_MELT_RADIUS; offsetX <= THERMAL_MELT_RADIUS; offsetX++) {
            for (int offsetY = -THERMAL_MELT_RADIUS; offsetY <= THERMAL_MELT_RADIUS; offsetY++) {
                for (int offsetZ = -THERMAL_MELT_RADIUS; offsetZ <= THERMAL_MELT_RADIUS; offsetZ++) {
                    if (offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ
                            > THERMAL_MELT_RADIUS * THERMAL_MELT_RADIUS) {
                        continue;
                    }

                    BlockPos pos = origin.offset(offsetX, offsetY, offsetZ);
                    if ((this.isTame()
                            && this.getAssignments().isWithinHome(Vec3.atCenterOf(pos), 0.0D))
                            || !this.mayInteract(serverLevel, pos)) {
                        continue;
                    }

                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                        serverLevel.removeBlock(pos, false);
                        serverLevel.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
                    } else if (state.is(BlockTags.ICE)) {
                        this.meltIce(serverLevel, pos, state);
                    }
                }
            }
        }
    }

    private void meltIce(ServerLevel serverLevel, BlockPos pos, BlockState iceState) {
        if (serverLevel.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            serverLevel.removeBlock(pos, false);
        } else {
            BlockState waterState = IceBlock.meltsInto();
            serverLevel.setBlockAndUpdate(pos, waterState);
            serverLevel.neighborChanged(pos, waterState.getBlock(), null);
        }
        serverLevel.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(iceState));
    }

    private void updateRiderTarget() {
        if (!(this.getControllingPassenger() instanceof Player rider)) {
            this.observedRider = null;
            return;
        }

        if (!rider.getUUID().equals(this.observedRider)) {
            this.observedRider = rider.getUUID();
            this.riderHurtTargetTimestamp = rider.getLastHurtMobTimestamp();
            this.riderHurtByTimestamp = rider.getLastHurtByMobTimestamp();
            return;
        }

        if (rider.getLastHurtMobTimestamp() != this.riderHurtTargetTimestamp) {
            this.riderHurtTargetTimestamp = rider.getLastHurtMobTimestamp();
            LivingEntity riderTarget = rider.getLastHurtMob();
            if (this.isValidFireTarget(riderTarget)) {
                this.setCombatTarget(riderTarget);
            }
        }
        if (rider.getLastHurtByMobTimestamp() != this.riderHurtByTimestamp) {
            this.riderHurtByTimestamp = rider.getLastHurtByMobTimestamp();
            LivingEntity attacker = rider.getLastHurtByMob();
            if (this.isValidFireTarget(attacker)) {
                this.setCombatTarget(attacker);
            }
        }
    }

    private void tickFireVeil(ServerLevel serverLevel, LivingEntity combatTarget) {
        if (this.fireVeilTicks == 0 && this.fireVeilCooldown == 0
                && this.distanceToSqr(combatTarget) <= FIRE_VEIL_SENSE_RADIUS * FIRE_VEIL_SENSE_RADIUS) {
            this.fireVeilTicks = FIRE_VEIL_DURATION_TICKS;
            this.fireVeilCooldown = FIRE_VEIL_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.EMPOWER);
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, FIRE_VEIL_DURATION_TICKS, 0));
            this.playSound(SoundEvents.BLAZE_SHOOT, 0.65F, 1.4F);
        }

        if (this.fireVeilTicks > 0 && this.tickCount % 20 == 0) {
            net.ronm19.phantomism.vfx.PhantomVisualEffects.aura(serverLevel,
                    net.ronm19.phantomism.vfx.PhantomVfxTheme.FIRE, this.position().add(0.0D, 0.5D, 0.0D));
            // The veil now improves pursuit and awareness; it is not an AoE damage loop.
            serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5D),
                    this.getZ(), 12, 0.7D, 0.5D, 0.7D, 0.012D);
            for (LivingEntity nearby : serverLevel.getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(FIRE_VEIL_SENSE_RADIUS),
                    entity -> this.isValidFireTarget(entity) && this.hasLineOfSight(entity))) {
                if (this.distanceToSqr(nearby) <= FIRE_VEIL_SENSE_RADIUS * FIRE_VEIL_SENSE_RADIUS) {
                    serverLevel.sendParticles(ParticleTypes.FLAME, nearby.getX(),
                            nearby.getY(0.5D), nearby.getZ(), 1,
                            0.15D, 0.15D, 0.15D, 0.0D);
                }
            }
        }
    }

    private void tickFireProjectile(ServerLevel serverLevel, LivingEntity target) {
        if (this.fireProjectileCooldown > 0
                || this.distanceToSqr(target) > FIRE_PROJECTILE_RANGE * FIRE_PROJECTILE_RANGE
                || !this.hasLineOfSight(target)) {
            return;
        }

        Vec3 direction = target.getEyePosition().subtract(this.getX(), this.getY(0.5D), this.getZ());
        if (this.launchFireProjectile(serverLevel, direction, false)) {
            this.fireProjectileCooldown = FIRE_PROJECTILE_COOLDOWN_TICKS;
        }
    }

    private boolean launchFireProjectile(ServerLevel serverLevel, Vec3 direction, boolean mounted) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        // Wild/autonomous projectiles keep their previous behavior and speed.
        SmallFireball fireball = new PhantomFireball(serverLevel, this, direction);
        if (mounted) {
            fireball.accelerationPower = MOUNTED_FIREBALL_ACCELERATION;
            fireball.setDeltaMovement(direction.normalize().scale(MOUNTED_FIREBALL_INITIAL_SPEED));
        }
        float yawRadians = this.getYRot() * ((float) Math.PI / 180.0F);
        double forwardOffset = this.getBbWidth() * 0.6D + 0.15D;
        fireball.setPos(
                this.getX() - Math.sin(yawRadians) * forwardOffset,
                this.getY() + this.getBbHeight() * 0.6D,
                this.getZ() + Math.cos(yawRadians) * forwardOffset
        );
        if (!serverLevel.addFreshEntity(fireball)) {
            return false;
        }

        this.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
        this.animate(PhantomAnimationAction.PROJECTILE);
        return true;
    }

    public boolean canHitWithFireProjectile(Entity target) {
        return target instanceof LivingEntity livingTarget && this.isValidFireTarget(livingTarget);
    }

    private static final class PhantomFireball extends SmallFireball {
        private PhantomFireball(ServerLevel level, FirePhantom owner, Vec3 direction) {
            super(level, owner, direction);
        }

        @Override
        protected boolean canHitEntity(Entity entity) {
            if (!super.canHitEntity(entity)) {
                return false;
            }
            Entity owner = this.getOwner();
            return !(owner instanceof FirePhantom firePhantom)
                    || firePhantom.canHitWithFireProjectile(entity);
        }

    }

    private void tickFireShield(ServerLevel serverLevel, LivingEntity target) {
        if (target != null && this.getHealth() <= this.getMaxHealth() * 0.5F
                && this.fireShieldCooldown == 0) {
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                    FIRE_SHIELD_DURATION_TICKS, 0));
            this.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
                    FIRE_SHIELD_STRENGTH_TICKS, 0));
            this.fireShieldTicks = FIRE_SHIELD_DURATION_TICKS;
            this.fireShieldCooldown = FIRE_SHIELD_COOLDOWN_TICKS;
            this.animate(PhantomAnimationAction.SHIELD);
            this.playSound(SoundEvents.BLAZE_HURT, 1.0F, 0.75F);
        }

        if (this.fireShieldTicks > 0 && this.tickCount % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5D),
                    this.getZ(), 6, 0.55D, 0.45D, 0.55D, 0.01D);
        }
    }

    private void tickFirestorm(ServerLevel serverLevel, LivingEntity focusTarget) {
        if (this.firestormTicks > 0) {
            if (this.firestormTicks % FIRESTORM_PULSE_INTERVAL == 0) {
                this.pulseFirestorm(serverLevel);
            }
            return;
        }
        if (this.firestormCooldown > 0 || !this.isValidFireTarget(focusTarget)) {
            return;
        }

        List<LivingEntity> nearby = this.findFirestormTargets(serverLevel);
        if (nearby.size() < FIRESTORM_MINIMUM_TARGETS) {
            return;
        }

        this.firestormCooldown = FIRESTORM_COOLDOWN_TICKS;
        this.firestormTicks = FIRESTORM_DURATION_TICKS;
        this.animate(PhantomAnimationAction.ULTIMATE);
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.8F);
        this.pulseFirestorm(serverLevel);
    }

    private List<LivingEntity> findFirestormTargets(ServerLevel serverLevel) {
        return serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(FIRESTORM_RADIUS),
                target -> this.isValidFireTarget(target)
                        && this.distanceToSqr(target) <= FIRESTORM_RADIUS * FIRESTORM_RADIUS);
    }

    private void pulseFirestorm(ServerLevel serverLevel) {
        net.ronm19.phantomism.vfx.PhantomVisualEffects.shockwave(serverLevel,
                net.ronm19.phantomism.vfx.PhantomVfxTheme.FIRE, this.position(), 5.0F);
        net.ronm19.phantomism.vfx.PhantomVisualEffects.impact(serverLevel,
                net.ronm19.phantomism.vfx.PhantomVfxTheme.FIRE, this.position().add(0.0D, 0.5D, 0.0D));
        for (LivingEntity victim : this.findFirestormTargets(serverLevel)) {
            victim.igniteForSeconds(FIRESTORM_BURN_SECONDS);
            victim.hurtServer(serverLevel, this.damageSources().mobAttack(this),
                    FIRESTORM_PULSE_DAMAGE);
        }
        serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5D),
                this.getZ(), 35, FIRESTORM_RADIUS * 0.6D, 1.0D,
                FIRESTORM_RADIUS * 0.6D, 0.05D);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("FireChargeCooldown", this.fireChargeCooldown);
        output.putInt("FireVeilCooldown", this.fireVeilCooldown);
        output.putInt("FireVeilTicks", this.fireVeilTicks);
        output.putInt("FireProjectileCooldown", this.fireProjectileCooldown);
        output.putInt("FireShieldCooldown", this.fireShieldCooldown);
        output.putInt("FireShieldTicks", this.fireShieldTicks);
        output.putInt("FirestormCooldown", this.firestormCooldown);
        output.putInt("FirestormTicks", this.firestormTicks);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.fireChargeCooldown = Math.max(0, input.read("FireChargeCooldown", Codec.INT).orElse(0));
        this.fireVeilCooldown = Math.max(0, input.read("FireVeilCooldown", Codec.INT).orElse(0));
        this.fireVeilTicks = Math.max(0, input.read("FireVeilTicks", Codec.INT).orElse(0));
        this.fireProjectileCooldown = Math.max(0, input.read("FireProjectileCooldown", Codec.INT).orElse(0));
        this.fireShieldCooldown = Math.max(0, input.read("FireShieldCooldown", Codec.INT).orElse(0));
        this.fireShieldTicks = Math.max(0, input.read("FireShieldTicks", Codec.INT).orElse(0));
        this.firestormCooldown = Math.max(0, input.read("FirestormCooldown", Codec.INT).orElse(0));
        this.firestormTicks = Math.max(0, input.read("FirestormTicks", Codec.INT).orElse(0));
    }

    private boolean isValidFireTarget(LivingEntity target) {
        return target != null
                && target != this
                && target.isAlive()
                && !target.isRemoved()
                && !(target instanceof AbstractPhantom)
                && !this.isAlliedTo(target)
                && !this.isTargetSuppressed(target);
    }
}
