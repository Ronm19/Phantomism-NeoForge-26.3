package net.ronm19.phantomism.entity;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
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
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class PhantomDragon extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/phantom_dragon");
    private static final Identifier WINGS_KNOCKBACK = id("dragon_wings_knockback");
    private static final Identifier DIVE_DAMAGE = id("draconic_dive_damage");
    private static final Identifier FURY_FLIGHT = id("dragon_fury_flight");
    private static final Identifier FURY_MOVEMENT = id("dragon_fury_movement");
    private static final EntityDataAccessor<Boolean> FURIOUS = SynchedEntityData.defineId(PhantomDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BREATHING = SynchedEntityData.defineId(PhantomDragon.class, EntityDataSerializers.BOOLEAN);
    private static final int BREATH_COOLDOWN = 16 * 20;
    private static final int FIREBALL_COOLDOWN = 11 * 20;
    private static final int SHIELD_COOLDOWN = 28 * 20;
    private static final int ROAR_COOLDOWN = 18 * 20;
    private static final int DIVE_COOLDOWN = 15 * 20;
    private static final int FURY_COOLDOWN = 70 * 20;
    private static final int FURY_DURATION = 12 * 20;
    private int breathCooldown;
    private int fireballCooldown;
    private int shieldCooldown;
    private int roarCooldown;
    private int diveCooldown;
    private int furyCooldown;
    private int furyTicks;
    private int breathTicks;
    private int recentDamageTicks;
    private int huntTicks;
    private @Nullable UUID huntTarget;

    public PhantomDragon(EntityType<? extends PhantomDragon> type, Level level) { super(type, level); }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 46.0D).add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 5.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<PhantomDragon> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.PHANTOM_DRAGON_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() { return 1; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.PHANTOM_DRAGON_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FURIOUS, false);
        builder.define(BREATHING, false);
    }

    public boolean isFurious() { return this.entityData.get(FURIOUS); }

    public boolean isBreathing() { return this.entityData.get(BREATHING); }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return !this.isBaby() && this.launchFireball(level, rider.getViewVector(1.0F));
    }

    @Override
    protected int getMeleeAttackCooldownTicks() { return this.isFurious() ? 28 : 55; }

    @Override
    protected int getAttackRecoveryTicks() { return this.isFurious() ? 12 : 24; }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 24.0D * 24.0D;
    }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()));
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target) * (this.threatensFamily(target) ? 0.2D : 1.0D);
    }

    @Override
    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (this.isBaby() || this.getControllingPassenger() != null || !this.isValidDragonTarget(target)) { return false; }
        if (this.isBreathing()) {
            this.moveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            return true;
        }
        if (this.isFurious() || this.threatensFamily(target) || this.distanceToSqr(target) <= 9.0D) { return false; }
        if (!target.getUUID().equals(this.huntTarget)) {
            this.huntTarget = target.getUUID();
            this.huntTicks = 24;
        }
        if (this.huntTicks <= 0) { return false; }
        Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(6.0D)).add(0.0D, 5.0D, 0.0D);
        this.moveControl.setWantedPosition(behind.x, behind.y, behind.z, 1.35D);
        return true;
    }

    boolean isValidDragonTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    boolean isAreaEnemy(LivingEntity target) {
        return this.isValidDragonTarget(target) && (target == this.getTarget() || target instanceof Enemy || this.threatensFamily(target));
    }

    boolean hasClearAttackLine(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.1D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private List<LivingEntity> nearbyEnemies(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), target ->
                this.isAreaEnemy(target) && this.distanceToSqr(target) <= radius * radius && this.hasLineOfSight(target))
                .stream().sorted(java.util.Comparator.comparingDouble(this::distanceToSqr)).limit(12).toList();
    }

    boolean hurtDragonTarget(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
        if (!this.isValidDragonTarget(target)) { return false; }
        DamageSource credited = target instanceof EnderDragon && this.getOwner() instanceof Player player
                ? this.damageSources().playerAttack(player) : source;
        return target instanceof EnderDragon dragon ? dragon.head.hurtServer(level, credited, damage)
                : target.hurtServer(level, source, damage);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) { this.recentDamageTicks = 60; }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || !(target instanceof LivingEntity victim) || !this.isValidDragonTarget(victim)) { return false; }
        boolean dive = this.isDivingStrike() && this.diveCooldown == 0;
        if (dive) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                    new AttributeModifier(DIVE_DAMAGE, 6.0D, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean hit;
        try {
            hit = victim instanceof EnderDragon ? this.hurtDragonTarget(level, victim, this.damageSources().mobAttack(this),
                    (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE)) : super.doHurtTarget(level, target);
        } finally {
            if (dive) { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_DAMAGE); }
        }
        if (hit && dive) {
            this.diveCooldown = DIVE_COOLDOWN;
            this.animate(PhantomAnimationAction.CHARGE);
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.DRAGON, victim.position(), 2.5F);
            this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 0.65F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        int recovery = this.isFurious() ? 2 : 1;
        this.breathCooldown = Math.max(0, this.breathCooldown - recovery);
        this.fireballCooldown = Math.max(0, this.fireballCooldown - recovery);
        this.diveCooldown = Math.max(0, this.diveCooldown - recovery);
        this.roarCooldown = Math.max(0, this.roarCooldown - recovery);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.furyCooldown = Math.max(0, this.furyCooldown - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        this.huntTicks = Math.max(0, this.huntTicks - 1);
        this.updateWings();
        if (this.isBaby() || !this.isAlive()) {
            this.cancelBreath();
            this.endFury();
            return;
        }
        if (this.furyTicks > 0) {
            if (this.tickCount % 10 == 0) {
                Vec3 center = this.position().add(0.0D, 0.5D, 0.0D);
                Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot());
                Vec3 side = new Vec3(-facing.z, 0.0D, facing.x).scale(1.5D);
                PhantomVisualEffects.aura(level, PhantomVfxTheme.DRAGON, center);
                PhantomVisualEffects.trail(level, PhantomVfxTheme.DRAGON, center.add(side));
                PhantomVisualEffects.trail(level, PhantomVfxTheme.DRAGON, center.subtract(side));
            }
            if (--this.furyTicks == 0) { this.endFury(); }
        }
        LivingEntity target = this.getTarget();
        if (this.breathTicks > 0) { this.tickBreath(level, target); }
        if (!this.isValidDragonTarget(target)) {
            this.huntTarget = null;
            this.huntTicks = 0;
            return;
        }
        if (this.tickCount % 10 == 0) { this.defend(level, target); }
        if (this.getControllingPassenger() != null || this.isBreathing() || !this.hasLineOfSight(target)) { return; }
        if (this.breathCooldown == 0 && this.distanceToSqr(target) <= 144.0D && this.distanceToSqr(target) > 4.0D) {
            this.breathCooldown = BREATH_COOLDOWN;
            this.breathTicks = 80;
            this.entityData.set(BREATHING, true);
            this.animate(PhantomAnimationAction.SENSE);
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 0.65F, 0.7F);
        } else if (this.fireballCooldown == 0 && this.distanceToSqr(target) <= 48.0D * 48.0D
                && this.launchFireball(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
            this.fireballCooldown = FIREBALL_COOLDOWN;
        }
    }

    private void updateWings() {
        var resistance = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (!this.isBaby() && this.isAlive() && !this.onGround() && !this.isInWater()) {
            resistance.addOrUpdateTransientModifier(new AttributeModifier(WINGS_KNOCKBACK, 0.35D, AttributeModifier.Operation.ADD_VALUE));
        } else { resistance.removeModifier(WINGS_KNOCKBACK); }
    }

    private void defend(ServerLevel level, LivingEntity target) {
        if (this.shieldCooldown == 0 && (this.recentDamageTicks > 0 && this.getHealth() <= this.getMaxHealth() * 0.75F
                || this.getHealth() <= this.getMaxHealth() * 0.5F || this.threatensFamily(target) && target.getMaxHealth() >= 40.0F)
                && (!this.hasEffect(MobEffects.RESISTANCE) || this.getEffect(MobEffects.RESISTANCE).getAmplifier() < 1)) {
            this.shieldCooldown = SHIELD_COOLDOWN;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 180, 1), this);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), this);
            this.animate(PhantomAnimationAction.SHIELD);
        }
        List<LivingEntity> enemies = this.nearbyEnemies(level, 8.0D);
        if (this.roarCooldown == 0 && (enemies.size() >= 2 || this.recentDamageTicks > 0 && this.distanceToSqr(target) <= 9.0D)) {
            this.roarCooldown = ROAR_COOLDOWN;
            for (LivingEntity enemy : enemies) {
                enemy.knockback(enemy.getMaxHealth() >= 80.0F ? 0.25D : 1.1D,
                        this.getX() - enemy.getX(), this.getZ() - enemy.getZ(), this.damageSources().mobAttack(this), 0.0F);
                enemy.syncVelocity = true;
            }
            this.animate(PhantomAnimationAction.EMPOWER);
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.DRAGON, this.position().add(0.0D, 0.05D, 0.0D), 4.5F);
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.1F, 0.55F);
        }
        if (this.furyCooldown == 0 && !this.isFurious() && (enemies.size() >= 3
                || target.getMaxHealth() >= 60.0F && target.getHealth() > 20.0F)) { this.activateFury(); }
    }

    private void activateFury() {
        this.furyCooldown = FURY_COOLDOWN;
        this.furyTicks = FURY_DURATION;
        this.entityData.set(FURIOUS, true);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FURY_DURATION, 0), this);
        this.applyFuryMovement();
        this.animate(PhantomAnimationAction.ULTIMATE);
        this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.6F);
    }

    private void endFury() {
        this.furyTicks = 0;
        this.entityData.set(FURIOUS, false);
        this.getAttribute(Attributes.FLYING_SPEED).removeModifier(FURY_FLIGHT);
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FURY_MOVEMENT);
    }

    private void applyFuryMovement() {
        this.getAttribute(Attributes.FLYING_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(FURY_FLIGHT, 0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        this.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(FURY_MOVEMENT, 0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void tickBreath(ServerLevel level, @Nullable LivingEntity target) {
        if (this.getControllingPassenger() != null || !this.isValidDragonTarget(target) || this.distanceToSqr(target) > 225.0D
                || !this.hasLineOfSight(target)) { this.cancelBreath(); return; }
        Vec3 origin = this.projectileOrigin();
        Vec3 direction = target.getEyePosition().subtract(origin).normalize();
        Vec3 end = origin.add(direction.scale(12.0D));
        if (!this.isLoadedFlightBox(new AABB(origin, end).inflate(1.0D))) { this.cancelBreath(); return; }
        HitResult obstruction = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (obstruction.getType() != HitResult.Type.MISS) { end = obstruction.getLocation(); }
        if (this.breathTicks % 4 == 0) { PhantomVisualEffects.beam(level, PhantomVfxTheme.DRAGON, origin, end); }
        if (this.breathTicks % 16 == 0) { this.animate(PhantomAnimationAction.SENSE); }
        if (this.breathTicks % 10 == 0) {
            int affected = 0;
            double length = origin.distanceTo(end);
            for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(3.5D), this::isAreaEnemy)) {
                Vec3 offset = enemy.getEyePosition().subtract(origin);
                double forward = offset.dot(direction);
                double lateral = Math.max(0.0D, offset.lengthSqr() - forward * forward);
                if (forward >= 0.0D && forward <= length && lateral <= Math.pow(0.7D + forward * 0.24D, 2.0D)
                        && this.hasClearAttackLine(level, origin, enemy.getEyePosition())) {
                    this.hurtDragonTarget(level, enemy, this.damageSources().mobAttack(this), this.isFurious() ? 3.0F : 2.0F);
                    if (++affected >= 12) { break; }
                }
            }
        }
        if (--this.breathTicks == 0) { this.cancelBreath(); }
    }

    private void cancelBreath() { this.breathTicks = 0; this.entityData.set(BREATHING, false); }

    Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double front = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * front, this.getBbHeight() * 0.6D, Math.cos(yaw) * front);
    }

    private boolean launchFireball(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) { return false; }
        var fireball = new DragonFireballProjectile(PhantomismContent.DRAGON_FIREBALL.get(), level);
        fireball.setOwner(this);
        fireball.setEmpowered(this.isFurious());
        fireball.setPos(this.projectileOrigin());
        fireball.shoot(direction.x, direction.y, direction.z, 1.35F, 0.0F);
        if (!level.addFreshEntity(fireball)) { return false; }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.FIRECHARGE_USE, 0.7F, 0.65F);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DragonBreathCooldown", this.breathCooldown);
        output.putInt("DragonFireballCooldown", this.fireballCooldown);
        output.putInt("DragonShieldCooldown", this.shieldCooldown);
        output.putInt("DragonRoarCooldown", this.roarCooldown);
        output.putInt("DraconicDiveCooldown", this.diveCooldown);
        output.putInt("DragonFuryCooldown", this.furyCooldown);
        output.putInt("DragonFuryTicks", this.furyTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.breathCooldown = Math.clamp(input.getIntOr("DragonBreathCooldown", 0), 0, BREATH_COOLDOWN);
        this.fireballCooldown = Math.clamp(input.getIntOr("DragonFireballCooldown", 0), 0, FIREBALL_COOLDOWN);
        this.shieldCooldown = Math.clamp(input.getIntOr("DragonShieldCooldown", 0), 0, SHIELD_COOLDOWN);
        this.roarCooldown = Math.clamp(input.getIntOr("DragonRoarCooldown", 0), 0, ROAR_COOLDOWN);
        this.diveCooldown = Math.clamp(input.getIntOr("DraconicDiveCooldown", 0), 0, DIVE_COOLDOWN);
        this.furyCooldown = Math.clamp(input.getIntOr("DragonFuryCooldown", 0), 0, FURY_COOLDOWN);
        this.cancelBreath();
        this.furyTicks = this.isBaby() ? 0 : Math.clamp(input.getIntOr("DragonFuryTicks", 0), 0, FURY_DURATION);
        if (this.furyTicks > 0) {
            this.entityData.set(FURIOUS, true);
            this.applyFuryMovement();
        } else { this.endFury(); }
        this.huntTarget = null;
        this.huntTicks = 0;
        this.recentDamageTicks = 0;
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(WINGS_KNOCKBACK);
    }
}
