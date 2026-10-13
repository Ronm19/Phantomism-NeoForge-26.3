package net.ronm19.phantomism.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

public final class GoldPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/gold_phantom");
    private static final EntityDataAccessor<Boolean> EYE_CHARGING = SynchedEntityData.defineId(GoldPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final Identifier RUSH_DAMAGE = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "gold_rush_damage");
    private static final Identifier SHIELD_KNOCKBACK = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "gold_shield_knockback");
    private static final String BLINDNESS_IMMUNITY = "PhantomismGoldBlindnessUntil";
    private static final String REWARD_COOLDOWN = "PhantomismGoldRewardUntil";
    private static final String REWARDED = "PhantomismGoldEyeRewarded";
    private static final int DUST_COOLDOWN = 8 * 20;
    private static final int RUSH_COOLDOWN = 14 * 20;
    private static final int SHIELD_COOLDOWN = 12 * 20;
    private static final int EYE_COOLDOWN = 60 * 20;
    private static final float EYE_DAMAGE = 14.0F;
    private int dustCooldown;
    private int rushCooldown;
    private int shieldCooldown;
    private int shieldTicks;
    private int eyeCooldown;
    private int eyeWindup;
    private @Nullable UUID eyeTarget;
    private @Nullable BlockPos caveHint;

    public GoldPhantom(EntityType<? extends GoldPhantom> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 36.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<GoldPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.GOLD_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() { return 1; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.GOLD_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EYE_CHARGING, false);
    }

    public boolean isGoldEyeCharging() { return this.entityData.get(EYE_CHARGING); }

    public @Nullable BlockPos getCaveHint() { return this.caveHint; }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return !this.isBaby() && this.launchGoldDust(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 24.0D * 24.0D;
    }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()));
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target) * (this.threatensFamily(target) ? 0.25D : 1.0D);
    }

    boolean isValidGoldTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target);
    }

    void applyGoldDust(ServerLevel level, LivingEntity target) {
        if (!this.isValidGoldTarget(target) || target.is(ModTags.EntityTypes.GOLD_CONTROL_RESISTANT) || target.getMaxHealth() >= 80.0F
                || target.getPersistentData().getLongOr(BLINDNESS_IMMUNITY, 0L) > level.getGameTime()) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100), this);
        target.getPersistentData().putLong(BLINDNESS_IMMUNITY, level.getGameTime() + 160L);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || this.isGoldEyeCharging() || !(target instanceof LivingEntity victim) || !this.isValidGoldTarget(victim)) {
            return false;
        }
        boolean rush = this.isDivingStrike() && this.rushCooldown == 0
                && (victim.hasEffect(MobEffects.BLINDNESS) || this.threatensFamily(victim));
        if (rush) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                    new AttributeModifier(RUSH_DAMAGE, 5.0D, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (rush) { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(RUSH_DAMAGE); }
        }
        if (hit && rush) {
            this.rushCooldown = RUSH_COOLDOWN;
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120), this);
            victim.knockback(0.8D, this.getX() - victim.getX(), this.getZ() - victim.getZ(),
                    this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + 5.0F);
            victim.syncVelocity = true;
            this.animate(PhantomAnimationAction.CHARGE);
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.GOLD, victim.position(), 2.0F);
            this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.3F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        this.dustCooldown = Math.max(0, this.dustCooldown - 1);
        this.rushCooldown = Math.max(0, this.rushCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.eyeCooldown = Math.max(0, this.eyeCooldown - 1);
        if (this.shieldTicks > 0 && --this.shieldTicks == 0) {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(SHIELD_KNOCKBACK);
        }
        if (this.isBaby()) { this.cancelGoldEye(); return; }
        LivingEntity target = this.getTarget();
        if (this.eyeWindup > 0) {
            this.tickGoldEye(level, target);
        } else if (this.isValidGoldTarget(target)) {
            if (this.shieldCooldown == 0 && this.distanceToSqr(target) <= 64.0D
                    && (this.getHealth() <= this.getMaxHealth() * 0.6F || this.threatensFamily(target))) {
                this.activateGoldenShield();
            }
            if (this.eyeCooldown == 0 && this.isTame() && target instanceof Enemy && target.getMaxHealth() >= 20.0F
                    && target.getHealth() <= EYE_DAMAGE && this.distanceToSqr(target) <= 24.0D * 24.0D && this.hasLineOfSight(target)) {
                this.eyeCooldown = EYE_COOLDOWN;
                this.eyeWindup = 12;
                this.eyeTarget = target.getUUID();
                this.entityData.set(EYE_CHARGING, true);
                this.animate(PhantomAnimationAction.ULTIMATE);
                this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
            } else if (this.getControllingPassenger() == null && this.dustCooldown == 0
                    && this.distanceToSqr(target) <= 28.0D * 28.0D && this.hasLineOfSight(target)
                    && this.launchGoldDust(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
                this.dustCooldown = DUST_COOLDOWN;
            }
        } else if (this.tickCount % 100 == 0) {
            this.scanForCave(level);
        }
    }

    private void activateGoldenShield() {
        this.shieldTicks = 100;
        this.shieldCooldown = SHIELD_COOLDOWN;
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0), this);
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrUpdateTransientModifier(
                new AttributeModifier(SHIELD_KNOCKBACK, 0.7D, AttributeModifier.Operation.ADD_VALUE));
        this.animate(PhantomAnimationAction.SHIELD);
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
    }

    private void tickGoldEye(ServerLevel level, @Nullable LivingEntity target) {
        if (!this.isValidGoldTarget(target) || !target.getUUID().equals(this.eyeTarget)
                || this.distanceToSqr(target) > 24.0D * 24.0D || !this.hasLineOfSight(target)) {
            this.cancelGoldEye();
            return;
        }
        if (this.eyeWindup % 4 == 0) {
            PhantomVisualEffects.aura(level, PhantomVfxTheme.GOLD, this.projectileOrigin());
        }
        if (--this.eyeWindup > 0) { return; }
        this.cancelGoldEye();
        Vec3 impact = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        PhantomVisualEffects.beam(level, PhantomVfxTheme.GOLD, this.projectileOrigin(), impact);
        PhantomVisualEffects.impact(level, PhantomVfxTheme.GOLD, impact);
        this.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.6F);
        if (target.hurtServer(level, this.damageSources().mobAttack(this), EYE_DAMAGE) && !target.isAlive()) {
            this.rewardGoldEye(level, target);
        }
    }

    private void cancelGoldEye() {
        this.eyeWindup = 0;
        this.eyeTarget = null;
        this.entityData.set(EYE_CHARGING, false);
    }

    private void rewardGoldEye(ServerLevel level, LivingEntity target) {
        if (target.isAlive() || !(target instanceof Enemy) || !(this.getOwner() instanceof ServerPlayer owner)
                || target.getPersistentData().getBooleanOr(REWARDED, false)
                || owner.getPersistentData().getLongOr(REWARD_COOLDOWN, 0L) > level.getGameTime()) {
            return;
        }
        int nuggets = Math.clamp((int) Math.ceil(target.getMaxHealth() / 40.0D), 1, 3);
        var reward = new ItemEntity(level, target.getX(), target.getY() + 0.2D, target.getZ(), new ItemStack(Items.GOLD_NUGGET, nuggets));
        reward.setDefaultPickUpDelay();
        if (level.addFreshEntity(reward)) {
            target.getPersistentData().putBoolean(REWARDED, true);
            owner.getPersistentData().putLong(REWARD_COOLDOWN, level.getGameTime() + EYE_COOLDOWN);
        }
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double front = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * front, this.getBbHeight() * 0.6D, Math.cos(yaw) * front);
    }

    private boolean launchGoldDust(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) { return false; }
        GoldDustProjectile dust = new GoldDustProjectile(PhantomismContent.GOLD_DUST.get(), level);
        dust.setOwner(this);
        dust.setPos(this.projectileOrigin());
        dust.shoot(direction.x, direction.y, direction.z, 1.5F, 0.0F);
        if (!level.addFreshEntity(dust)) { return false; }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.SNOWBALL_THROW, 0.8F, 1.25F);
        return true;
    }

    private void scanForCave(ServerLevel level) {
        this.caveHint = null;
        LivingEntity owner = this.getOwner();
        if (!this.isTame() || owner == null || !owner.isAlive() || owner.distanceToSqr(this) > 32.0D * 32.0D
                || this.getOrder() == Order.STAY || this.isInLove()) { return; }
        Vec3 start = owner.getEyePosition();
        BlockPos origin = owner.blockPosition();
        double best = 25.0D * 25.0D;
        for (int radius = 4; radius <= 24; radius += 4) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4.0D;
                for (int height = -4; height <= 2; height += 2) {
                    BlockPos candidate = origin.offset((int) Math.round(Math.cos(angle) * radius), height, (int) Math.round(Math.sin(angle) * radius));
                    double distance = start.distanceToSqr(Vec3.atCenterOf(candidate).add(0.0D, 0.5D, 0.0D));
                    if (distance >= best || !this.isLoadedFlightBox(new AABB(candidate).inflate(1.0D, 4.0D, 1.0D))
                            || !this.isCaveOpening(level, candidate)) { continue; }
                    Vec3 opening = Vec3.atCenterOf(candidate).add(0.0D, 0.5D, 0.0D);
                    if (this.isLoadedFlightBox(new AABB(start, opening).inflate(0.1D))
                            && level.clip(new ClipContext(start, opening, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS) {
                        this.caveHint = candidate.immutable();
                        best = distance;
                    }
                }
            }
        }
        if (this.caveHint != null) {
            Vec3 opening = Vec3.atCenterOf(this.caveHint);
            Vec3 cue = this.projectileOrigin();
            PhantomVisualEffects.beam(level, PhantomVfxTheme.GOLD, cue, cue.add(opening.subtract(cue).normalize().scale(Math.min(5.0D, opening.distanceTo(cue)))));
        }
    }

    private boolean isCaveOpening(ServerLevel level, BlockPos position) {
        for (int height = 0; height < 2; height++) {
            BlockPos space = position.above(height);
            if (!level.getFluidState(space).isEmpty() || !level.getBlockState(space).getCollisionShape(level, space).isEmpty()) { return false; }
        }
        boolean ceiling = false;
        for (int height = 2; height <= 4; height++) {
            if (level.getBlockState(position.above(height)).is(ModTags.Blocks.GOLD_CAVE_CEILINGS)) { ceiling = true; break; }
        }
        return ceiling && (!level.getBlockState(position.below()).getCollisionShape(level, position.below()).isEmpty()
                || !level.getBlockState(position.below(2)).getCollisionShape(level, position.below(2)).isEmpty());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("GoldDustCooldown", this.dustCooldown);
        output.putInt("GoldRushCooldown", this.rushCooldown);
        output.putInt("GoldenShieldCooldown", this.shieldCooldown);
        output.putInt("GoldenShieldTicks", this.shieldTicks);
        output.putInt("GoldEyeCooldown", this.eyeCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.dustCooldown = Math.clamp(input.getIntOr("GoldDustCooldown", 0), 0, DUST_COOLDOWN);
        this.rushCooldown = Math.clamp(input.getIntOr("GoldRushCooldown", 0), 0, RUSH_COOLDOWN);
        this.shieldCooldown = Math.clamp(input.getIntOr("GoldenShieldCooldown", 0), 0, SHIELD_COOLDOWN);
        this.shieldTicks = Math.clamp(input.getIntOr("GoldenShieldTicks", 0), 0, 100);
        this.eyeCooldown = Math.clamp(input.getIntOr("GoldEyeCooldown", 0), 0, EYE_COOLDOWN);
        this.cancelGoldEye();
        this.caveHint = null;
        if (this.shieldTicks > 0) {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrUpdateTransientModifier(
                    new AttributeModifier(SHIELD_KNOCKBACK, 0.7D, AttributeModifier.Operation.ADD_VALUE));
        } else {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(SHIELD_KNOCKBACK);
        }
    }
}
