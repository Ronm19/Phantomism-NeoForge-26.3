package net.ronm19.phantomism.entity;

import java.util.Comparator;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class VioletPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/violet_phantom");
    private static final Identifier DIVE_DAMAGE = id("violet_dive_damage");
    private static final Identifier OVERDRIVE_FLIGHT = id("violet_overdrive_flight");
    private static final Identifier OVERDRIVE_MOVEMENT = id("violet_overdrive_movement");
    private static final EntityDataAccessor<Integer> MOMENTUM = SynchedEntityData.defineId(VioletPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OVERDRIVE = SynchedEntityData.defineId(VioletPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int LANCE_COOLDOWN = 8 * 20;
    private static final int DIVE_COOLDOWN = 8 * 20;
    private static final int CROSSCUT_COOLDOWN = 18 * 20;
    private static final int RUPTURE_COOLDOWN = 12 * 20;
    private static final int GUARD_COOLDOWN = 20 * 20;
    private static final int OVERDRIVE_COOLDOWN = 70 * 20;
    private static final int OVERDRIVE_DURATION = 12 * 20;
    private static final int COMBO_WINDOW = 12 * 20;
    private int lanceCooldown;
    private int diveCooldown;
    private int crosscutCooldown;
    private int ruptureCooldown;
    private int guardCooldown;
    private int overdriveCooldown;
    private int overdriveTicks;
    private int comboTicks;
    private int comboMoves;
    private int recentDamageTicks;
    private @Nullable UUID comboTarget;

    public VioletPhantom(EntityType<? extends VioletPhantom> type, Level level) { super(type, level); }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 56.0D).add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 6.0D).add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<VioletPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.VIOLET_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() { return 2; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.VIOLET_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOMENTUM, 0);
        builder.define(OVERDRIVE, false);
    }

    public int getVioletMomentum() { return this.entityData.get(MOMENTUM); }

    public boolean isOverdriveActive() { return this.entityData.get(OVERDRIVE); }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return !this.isBaby() && this.launchLance(level, rider.getViewVector(1.0F));
    }

    @Override
    protected int getMeleeAttackCooldownTicks() { return this.isOverdriveActive() ? 24 : 48; }

    @Override
    protected int getAttackRecoveryTicks() { return this.isOverdriveActive() ? 10 : 20; }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 24.0D * 24.0D;
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target) * (this.threatensFamily(target) ? 0.2D : 1.0D);
    }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()));
    }

    boolean isValidVioletTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean isAreaEnemy(LivingEntity target) {
        return this.isValidVioletTarget(target) && (target == this.getTarget() || target instanceof Enemy || this.threatensFamily(target));
    }

    private List<LivingEntity> nearbyEnemies(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), target ->
                this.isAreaEnemy(target) && this.distanceToSqr(target) <= radius * radius && this.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(this::distanceToSqr)).limit(6).toList();
    }

    boolean hurtVioletTarget(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
        if (!this.isValidVioletTarget(target)) { return false; }
        float previousHealth = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), damage) : target.hurtServer(level, source, damage);
        return hit && target.getHealth() + target.getAbsorptionAmount() < previousHealth;
    }

    void recordLanceHit(LivingEntity target) { this.recordComboHit(1, target); }

    private void recordComboHit(int move, LivingEntity target) {
        if (this.isBaby() || !this.isAlive() || this.isTargetSuppressed(target) || this.isAlliedTo(target)) { return; }
        LivingEntity selected = this.getTarget();
        if (selected != null && selected != target) { return; }
        if (!target.getUUID().equals(this.comboTarget)) { this.resetCombo(); this.comboTarget = target.getUUID(); }
        if ((this.comboMoves & move) != 0) { return; }
        this.comboMoves |= move;
        this.comboTicks = COMBO_WINDOW;
        this.entityData.set(MOMENTUM, Math.min(3, Integer.bitCount(this.comboMoves)));
        if (this.level() instanceof ServerLevel level) {
            PhantomVisualEffects.trail(level, PhantomVfxTheme.VIOLET, this.position().add(0.0D, 0.6D, 0.0D));
        }
    }

    private void resetCombo() {
        this.comboMoves = 0;
        this.comboTicks = 0;
        this.comboTarget = null;
        this.entityData.set(MOMENTUM, 0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) { this.recentDamageTicks = 60; }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || !(target instanceof LivingEntity victim) || !this.isValidVioletTarget(victim)) { return false; }
        boolean dive = this.isDivingStrike() && this.diveCooldown == 0;
        double bonus = (dive ? 4.0D : 0.0D) + this.getVioletMomentum() * 0.75D;
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(DIVE_DAMAGE, bonus, AttributeModifier.Operation.ADD_VALUE));
        boolean hit;
        float previousHealth = victim.getHealth() + victim.getAbsorptionAmount();
        try {
            hit = victim instanceof EnderDragon ? this.hurtVioletTarget(level, victim, this.damageSources().mobAttack(this),
                    (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE)) : super.doHurtTarget(level, target);
        } finally { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_DAMAGE); }
        hit &= victim.getHealth() + victim.getAbsorptionAmount() < previousHealth;
        if (hit && dive) {
            this.diveCooldown = DIVE_COOLDOWN;
            this.recordComboHit(2, victim);
            PhantomVisualEffects.impact(level, PhantomVfxTheme.VIOLET, victim.position().add(0.0D, 0.5D, 0.0D));
        }
        return hit;
    }

    @Override
    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (!this.isValidVioletTarget(target) || this.getControllingPassenger() != null) { return false; }
        if (this.getVioletMomentum() == 3 && this.ruptureCooldown == 0
                || this.getVioletMomentum() > 0 && (this.comboMoves & 4) == 0 && this.crosscutCooldown == 0) {
            this.moveControl.setWantedPosition(target.getX(), target.getY(0.5D), target.getZ(), 1.4D);
            return true;
        }
        if (this.diveCooldown == 0) { return false; }
        if (this.distanceToSqr(target) <= 9.0D || this.threatensFamily(target)) { return false; }
        Vec3 forward = target.position().subtract(this.position()).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).scale(this.getId() % 2 == 0 ? 4.0D : -4.0D);
        Vec3 position = target.position().subtract(forward.scale(6.0D)).add(side).add(0.0D, 3.0D, 0.0D);
        this.moveControl.setWantedPosition(position.x, position.y, position.z, this.isOverdriveActive() ? 1.6D : 1.25D);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        int recovery = this.isOverdriveActive() ? 2 : 1;
        this.lanceCooldown = Math.max(0, this.lanceCooldown - recovery);
        this.diveCooldown = Math.max(0, this.diveCooldown - recovery);
        this.crosscutCooldown = Math.max(0, this.crosscutCooldown - recovery);
        this.ruptureCooldown = Math.max(0, this.ruptureCooldown - recovery);
        this.guardCooldown = Math.max(0, this.guardCooldown - 1);
        this.overdriveCooldown = Math.max(0, this.overdriveCooldown - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (this.isBaby() || !this.isAlive()) { this.resetCombo(); this.endOverdrive(); return; }
        LivingEntity target = this.getTarget();
        if (this.comboTicks > 0 && (--this.comboTicks == 0 || target != null && !target.getUUID().equals(this.comboTarget)
                || target != null && !this.isValidVioletTarget(target))) { this.resetCombo(); }
        if (this.overdriveTicks > 0) {
            if (this.tickCount % 10 == 0) {
                PhantomVisualEffects.aura(level, PhantomVfxTheme.VIOLET, this.position().add(0.0D, 0.5D, 0.0D));
                PhantomVisualEffects.trail(level, PhantomVfxTheme.VIOLET, this.position());
            }
            if (--this.overdriveTicks == 0) { this.endOverdrive(); }
        }
        if (!this.isValidVioletTarget(target)) { return; }
        if (this.tickCount % 10 == 0) { this.defend(level, target); }
        if (this.getControllingPassenger() != null || !this.hasLineOfSight(target) || this.tickCount % 4 != 0) { return; }
        if (this.getVioletMomentum() == 3 && this.ruptureCooldown == 0 && this.distanceToSqr(target) <= 25.0D) {
            this.tryOverdrive(level, target);
            if (this.rupture(level, target)) { return; }
        }
        if (this.crosscutCooldown == 0 && this.distanceToSqr(target) <= 20.25D && this.crosscut(level, target)) { return; }
        if (this.lanceCooldown == 0 && this.distanceToSqr(target) > 16.0D && this.distanceToSqr(target) <= VioletLanceProjectile.MAX_TRAVEL_DISTANCE * VioletLanceProjectile.MAX_TRAVEL_DISTANCE
                && this.launchLance(level, target.getEyePosition().subtract(this.projectileOrigin()))) { this.lanceCooldown = LANCE_COOLDOWN; }
    }

    private boolean crosscut(ServerLevel level, LivingEntity target) {
        if (!this.isValidVioletTarget(target) || this.distanceToSqr(target) > 20.25D || !this.hasLineOfSight(target)) { return false; }
        float damage = 6.0F + this.getVioletMomentum() * 0.75F;
        if (!this.hurtVioletTarget(level, target, this.damageSources().mobAttack(this), damage)) { return false; }
        Vec3 direction = target.position().subtract(this.position()).normalize();
        int affected = 0;
        for (LivingEntity enemy : this.nearbyEnemies(level, 4.5D)) {
            if (enemy != target && enemy.position().subtract(this.position()).normalize().dot(direction) >= 0.25D) {
                this.hurtVioletTarget(level, enemy, this.damageSources().mobAttack(this), damage * 0.5F);
                if (++affected >= 2) { break; }
            }
        }
        this.crosscutCooldown = CROSSCUT_COOLDOWN;
        this.recordComboHit(4, target);
        this.animate(PhantomAnimationAction.CHARGE);
        PhantomVisualEffects.crosscut(level, PhantomVfxTheme.VIOLET, target.position().add(0.0D, 0.7D, 0.0D));
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.45F);
        return true;
    }

    private boolean rupture(ServerLevel level, LivingEntity target) {
        if (this.getVioletMomentum() < 3 || !this.isValidVioletTarget(target) || !target.getUUID().equals(this.comboTarget)
                || this.distanceToSqr(target) > 25.0D || !this.hasLineOfSight(target)) { return false; }
        float damage = 8.0F + this.getVioletMomentum() * 2.0F + (this.isOverdriveActive() ? 2.0F : 0.0F);
        if (!this.hurtVioletTarget(level, target, this.damageSources().mobAttack(this), damage)) { return false; }
        int affected = 0;
        for (LivingEntity enemy : this.nearbyEnemies(level, 8.0D)) {
            if (enemy != target && enemy.distanceToSqr(target) <= 12.25D && target.hasLineOfSight(enemy)) {
                this.hurtVioletTarget(level, enemy, this.damageSources().mobAttack(this), damage * 0.4F);
                if (++affected >= 3) { break; }
            }
        }
        this.ruptureCooldown = RUPTURE_COOLDOWN;
        this.resetCombo();
        this.animate(PhantomAnimationAction.EMPOWER);
        PhantomVisualEffects.impact(level, PhantomVfxTheme.VIOLET, target.position().add(0.0D, 0.6D, 0.0D));
        PhantomVisualEffects.shockwave(level, PhantomVfxTheme.VIOLET, target.position().add(0.0D, 0.05D, 0.0D), 3.0F);
        this.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.85F, 0.7F);
        return true;
    }

    private void defend(ServerLevel level, LivingEntity target) {
        if (this.guardCooldown == 0 && !this.hasEffect(MobEffects.RESISTANCE)
                && (this.getHealth() <= this.getMaxHealth() * 0.7F || this.recentDamageTicks > 0 && this.distanceToSqr(target) <= 36.0D)) {
            this.guardCooldown = GUARD_COOLDOWN;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 0), this);
            this.animate(PhantomAnimationAction.SHIELD);
        }
        this.tryOverdrive(level, target);
    }

    private void tryOverdrive(ServerLevel level, LivingEntity target) {
        if (this.overdriveCooldown == 0 && !this.isOverdriveActive() && this.getVioletMomentum() == 3
                && (this.nearbyEnemies(level, 10.0D).size() >= 2 || target.getMaxHealth() >= 40.0F && target.getHealth() > 20.0F)) {
            this.activateOverdrive();
        }
    }

    private void activateOverdrive() {
        this.overdriveTicks = OVERDRIVE_DURATION;
        this.overdriveCooldown = OVERDRIVE_COOLDOWN;
        this.entityData.set(OVERDRIVE, true);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, OVERDRIVE_DURATION, 0), this);
        this.applyOverdriveMovement();
        this.animate(PhantomAnimationAction.ULTIMATE);
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.15F);
    }

    private void applyOverdriveMovement() {
        this.getAttribute(Attributes.FLYING_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(OVERDRIVE_FLIGHT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        this.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(OVERDRIVE_MOVEMENT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void endOverdrive() {
        this.overdriveTicks = 0;
        this.entityData.set(OVERDRIVE, false);
        this.getAttribute(Attributes.FLYING_SPEED).removeModifier(OVERDRIVE_FLIGHT);
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(OVERDRIVE_MOVEMENT);
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double front = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * front, this.getBbHeight() * 0.6D, Math.cos(yaw) * front);
    }

    private boolean launchLance(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) { return false; }
        var lance = new VioletLanceProjectile(PhantomismContent.VIOLET_LANCE.get(), level);
        lance.setOwner(this);
        lance.setDamage(5.0F + this.getVioletMomentum() * 0.75F + (this.isOverdriveActive() ? 1.5F : 0.0F));
        lance.setPos(this.projectileOrigin());
        lance.shoot(direction.x, direction.y, direction.z, 1.65F, 0.0F);
        if (!level.addFreshEntity(lance)) { return false; }
        this.animate(PhantomAnimationAction.PROJECTILE);
        PhantomVisualEffects.beam(level, PhantomVfxTheme.VIOLET, lance.position(), lance.position().add(direction.normalize().scale(2.0D)));
        this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.65F, 1.65F);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("VioletLanceCooldown", this.lanceCooldown);
        output.putInt("VioletDiveCooldown", this.diveCooldown);
        output.putInt("VioletCrosscutCooldown", this.crosscutCooldown);
        output.putInt("VioletRuptureCooldown", this.ruptureCooldown);
        output.putInt("VioletGuardCooldown", this.guardCooldown);
        output.putInt("VioletOverdriveCooldown", this.overdriveCooldown);
        output.putInt("VioletOverdriveTicks", this.overdriveTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lanceCooldown = Math.clamp(input.getIntOr("VioletLanceCooldown", 0), 0, LANCE_COOLDOWN);
        this.diveCooldown = Math.clamp(input.getIntOr("VioletDiveCooldown", 0), 0, DIVE_COOLDOWN);
        this.crosscutCooldown = Math.clamp(input.getIntOr("VioletCrosscutCooldown", 0), 0, CROSSCUT_COOLDOWN);
        this.ruptureCooldown = Math.clamp(input.getIntOr("VioletRuptureCooldown", 0), 0, RUPTURE_COOLDOWN);
        this.guardCooldown = Math.clamp(input.getIntOr("VioletGuardCooldown", 0), 0, GUARD_COOLDOWN);
        this.overdriveCooldown = Math.clamp(input.getIntOr("VioletOverdriveCooldown", 0), 0, OVERDRIVE_COOLDOWN);
        this.resetCombo();
        this.overdriveTicks = this.isBaby() ? 0 : Math.clamp(input.getIntOr("VioletOverdriveTicks", 0), 0, OVERDRIVE_DURATION);
        this.entityData.set(OVERDRIVE, this.overdriveTicks > 0);
        if (this.overdriveTicks > 0) { this.applyOverdriveMovement(); } else { this.endOverdrive(); }
    }
}
