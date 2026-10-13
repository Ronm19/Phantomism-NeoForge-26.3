package net.ronm19.phantomism.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;

public final class BlackPhantom extends AbstractPhantom {
    private static final EntityDataAccessor<Boolean> SKY_KNIGHT_ACTIVE = SynchedEntityData.defineId(
            BlackPhantom.class, EntityDataSerializers.BOOLEAN);

    private static final int HUNTER_SENSE_DURATION = 20 * 20;
    private static final int HUNTER_SENSE_RADIUS = 30;
    private static final int HUNTER_SENSE_SCAN_INTERVAL = 20;
    private static final int SHIELD_DURATION = 10 * 20;
    private static final int SHIELD_COOLDOWN = 40 * 20;
    private static final int SKY_KNIGHT_DURATION = 12 * 20;
    private static final int SKY_KNIGHT_COOLDOWN = 30 * 20;

    private static final Identifier SKY_KNIGHT_SPEED = Identifier.fromNamespaceAndPath(
            Phantomism.MOD_ID, "sky_knight_speed");
    private static final Identifier SKY_KNIGHT_ATTACK = Identifier.fromNamespaceAndPath(
            Phantomism.MOD_ID, "sky_knight_attack");
    private static final Identifier DIVE_BONUS = Identifier.fromNamespaceAndPath(
            Phantomism.MOD_ID, "dive_bonus");

    private int hunterSenseTicks;
    private int shieldCooldown;
    private int skyKnightTicks;
    private int skyKnightCooldown;

    public BlackPhantom(EntityType<? extends BlackPhantom> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Keep the existing registered attribute values; the redesign is ability-focused.
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 32.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKY_KNIGHT_ACTIVE, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.skyKnightCooldown = Math.max(0, this.skyKnightCooldown - 1);

        LivingEntity target = this.getTarget();
        boolean fighting = this.isValidBlackTarget(target);
        if (fighting) {
            // Hunter Sense remains a temporary radar, not permanent wall-hacking.
            if (this.hunterSenseTicks == 0) {
                this.animate(PhantomAnimationAction.SENSE);
            }
            this.hunterSenseTicks = HUNTER_SENSE_DURATION;

            if (this.skyKnightTicks == 0 && this.skyKnightCooldown == 0
                    && this.distanceToSqr(target) <= 24.0D * 24.0D
                    && (target.getHealth() <= target.getMaxHealth() * 0.5F
                    || this.isFlyingPrey(target))) {
                this.activateSkyKnight();
            }

            if (this.shieldCooldown == 0 && this.getHealth() <= this.getMaxHealth() * 0.5F
                    && this.distanceToSqr(target) <= 12.0D * 12.0D) {
                this.activateShield();
            }
        } else {
            this.hunterSenseTicks = Math.max(0, this.hunterSenseTicks - 1);
        }

        if (this.skyKnightTicks > 0) {
            this.skyKnightTicks--;
            if (this.tickCount % 4 == 0) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5D),
                        this.getZ(), 2, 0.35D, 0.35D, 0.35D, 0.02D);
            }
            if (this.skyKnightTicks == 0) {
                this.clearSkyKnight();
            }
        }

        if (this.hunterSenseTicks > 0 && this.tickCount % HUNTER_SENSE_SCAN_INTERVAL == 0) {
            this.showHunterSense(serverLevel);
        }
    }

    private void activateShield() {
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_DURATION, 0));
        this.shieldCooldown = SHIELD_COOLDOWN;
        this.animate(PhantomAnimationAction.SHIELD);
        this.playSound(SoundEvents.PHANTOM_FLAP, 0.8F, 0.8F);
    }

    private void activateSkyKnight() {
        this.skyKnightTicks = SKY_KNIGHT_DURATION;
        this.skyKnightCooldown = SKY_KNIGHT_COOLDOWN;
        this.animate(PhantomAnimationAction.EMPOWER);
        this.getEntityData().set(SKY_KNIGHT_ACTIVE, true);
        this.applySkyKnightModifiers();
        this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.15F);
    }

    private void applySkyKnightModifiers() {
        // Two temporary attribute modifiers keep the existing Config settings useful.
        this.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(new AttributeModifier(
                SKY_KNIGHT_SPEED, Config.BLACK_PHANTOM_SKY_KNIGHT_SPEED_BONUS.get(),
                Operation.ADD_MULTIPLIED_TOTAL));
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                SKY_KNIGHT_ATTACK, Config.BLACK_PHANTOM_SKY_KNIGHT_ATTACK_BONUS.get(),
                Operation.ADD_VALUE));
    }

    private void clearSkyKnight() {
        this.skyKnightTicks = 0;
        this.getEntityData().set(SKY_KNIGHT_ACTIVE, false);
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SKY_KNIGHT_SPEED);
        this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(SKY_KNIGHT_ATTACK);
    }

    private void showHunterSense(ServerLevel serverLevel) {
        AABB scanArea = this.getBoundingBox().inflate(HUNTER_SENSE_RADIUS);
        for (LivingEntity sensed : serverLevel.getEntitiesOfClass(LivingEntity.class, scanArea,
                entity -> this.isValidBlackTarget(entity) && this.hasLineOfSight(entity)
                        && this.distanceToSqr(entity) <= HUNTER_SENSE_RADIUS * HUNTER_SENSE_RADIUS)) {
            serverLevel.sendParticles(ParticleTypes.WITCH, sensed.getX(), sensed.getY(0.5D),
                    sensed.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean isValidBlackTarget(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved()
                && entity instanceof Enemy
                && !this.isAlliedTo(entity)
                && !this.isTargetSuppressed(entity);
    }

    private boolean isFlyingPrey(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.monster.Phantom
                || (target instanceof Mob mob && mob.getMoveControl() instanceof FlyingMoveControl);
    }

    public boolean isSkyKnightActive() {
        return this.getEntityData().get(SKY_KNIGHT_ACTIVE);
    }

    @Override
    public boolean doHurtTarget(ServerLevel serverLevel, Entity target) {
        if (target instanceof AbstractPhantom || this.isAlliedTo(target)
                || (target instanceof LivingEntity living && this.isTargetSuppressed(living))) {
            return false;
        }

        double bonus = 0.0D;
        if (this.isDivingStrike() && target instanceof LivingEntity victim) {
            if (victim.typeHolder().is(EntityTypeTags.UNDEAD)) {
                bonus += Config.BLACK_PHANTOM_DIVE_UNDEAD_BONUS.get();
            }
            if (this.isFlyingPrey(victim)) {
                bonus += Config.BLACK_PHANTOM_DIVE_FLYING_BONUS.get();
            }
            if (victim instanceof net.minecraft.world.entity.monster.Phantom) {
                bonus += Config.BLACK_PHANTOM_DIVE_PHANTOM_BONUS.get();
            }
        }

        if (bonus > 0.0D) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(
                    DIVE_BONUS, bonus, Operation.ADD_VALUE));
        }
        try {
            return super.doHurtTarget(serverLevel, target);
        } finally {
            if (bonus > 0.0D) {
                this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_BONUS);
            }
        }
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel serverLevel) {
        if (!this.isValidBlackTarget(target) || this.distanceToSqr(target) > 24.0D * 24.0D) {
            return false;
        }
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            return this.getOrder() == Order.GUARD && owner != null
                    && owner.distanceToSqr(target) <= 24.0D * 24.0D;
        }
        // Black is a common daytime ambusher as well as a nighttime hunter.
        return true;
    }

    @Override
    protected void addAdditionalSaveData( ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BlackHunterSenseTicks", this.hunterSenseTicks);
        output.putInt("BlackShieldCooldown", this.shieldCooldown);
        output.putInt("BlackSkyKnightTicks", this.skyKnightTicks);
        output.putInt("BlackSkyKnightCooldown", this.skyKnightCooldown);
    }

    @Override
    protected void readAdditionalSaveData( ValueInput input) {
        super.readAdditionalSaveData(input);
        this.hunterSenseTicks = Math.max(0, input.read("BlackHunterSenseTicks", Codec.INT).orElse(0));
        this.shieldCooldown = Math.max(0, input.read("BlackShieldCooldown", Codec.INT).orElse(0));
        this.skyKnightTicks = Math.max(0, input.read("BlackSkyKnightTicks", Codec.INT).orElse(0));
        this.skyKnightCooldown = Math.max(0, input.read("BlackSkyKnightCooldown", Codec.INT).orElse(0));
        if (this.skyKnightTicks > 0) {
            this.getEntityData().set(SKY_KNIGHT_ACTIVE, true);
            this.applySkyKnightModifiers();
        } else {
            this.clearSkyKnight();
        }
    }
}

