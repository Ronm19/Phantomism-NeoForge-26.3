package net.ronm19.phantomism.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

public final class ToxicPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/toxic_phantom");
    private static final Identifier DIVE_DAMAGE = id("acid_dive_damage");
    private static final EntityDataAccessor<Boolean> RAINING = SynchedEntityData.defineId(ToxicPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int PROJECTILE_COOLDOWN = 11 * 20;
    private static final int PULSE_COOLDOWN = 12 * 20;
    private static final int DIVE_COOLDOWN = 10 * 20;
    private static final int AREA_COOLDOWN = 24 * 20;
    private static final int AREA_DURATION = 8 * 20;
    private static final int SHIELD_COOLDOWN = 25 * 20;
    private static final int SHIELD_DURATION = 8 * 20;
    private static final int RAIN_COOLDOWN = 50 * 20;
    private static final int RAIN_DURATION = 10 * 20;
    private int projectileCooldown;
    private int pulseCooldown;
    private int diveCooldown;
    private int areaCooldown;
    private int areaTicks;
    private int shieldCooldown;
    private int shieldTicks;
    private int rainCooldown;
    private int rainTicks;
    private int abilityRecovery;
    private int recentDamageTicks;
    private boolean reflecting;
    private @Nullable Vec3 areaCenter;
    private @Nullable Vec3 rainCenter;

    public ToxicPhantom(EntityType<? extends ToxicPhantom> type, Level level) { super(type, level); }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64.0D).add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 8.0D).add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static boolean checkSpawnRules(EntityType<ToxicPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.TOXIC_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && (level.getLevel().isDarkOutside() || random.nextInt(4) == 0);
    }

    @Override
    public int getMaxSpawnClusterSize() { return 2; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.TOXIC_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RAINING, false);
    }

    public boolean isAcidRainActive() { return this.entityData.get(RAINING); }

    public boolean isAcidShieldActive() { return this.shieldTicks > 0 && this.hasEffect(MobEffects.RESISTANCE); }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect().value() != MobEffects.POISON.value()
                && effect.getEffect().value() != PhantomismContent.ARMOR_FRACTURE.value() && super.canBeAffected(effect);
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return !this.isBaby() && this.launchAcid(level, rider.getViewVector(1.0F));
    }

    @Override
    protected int getMeleeAttackCooldownTicks() { return 65; }

    @Override
    protected int getAttackRecoveryTicks() { return 26; }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 32.0D * 32.0D;
    }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()));
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        double priority = this.distanceToSqr(target) * (this.threatensFamily(target) ? 0.2D : 1.0D);
        return target.hasEffect(PhantomismContent.ARMOR_FRACTURE) ? priority * 0.8D : priority;
    }

    boolean isValidAcidTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean isAreaEnemy(LivingEntity target) {
        return this.isValidAcidTarget(target) && (target == this.getTarget() || target instanceof Enemy || this.threatensFamily(target));
    }

    private List<LivingEntity> enemiesAt(ServerLevel level, Vec3 center, double radius, double height) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, height, radius), target ->
                this.isAreaEnemy(target) && target.position().subtract(center).horizontalDistanceSqr() <= radius * radius
                        && Math.abs(target.getY() - center.y) <= height && this.clearAttackLine(level, center.add(0.0D, 0.6D, 0.0D), target.getEyePosition()))
                .stream().sorted(Comparator.comparingDouble(target -> target.distanceToSqr(center))).limit(16).toList();
    }

    boolean clearAttackLine(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.1D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    boolean hurtAcidTarget(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
        if (this.isBaby() || !this.isAlive() || !this.isValidAcidTarget(target)) { return false; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), damage) : target.hurtServer(level, source, damage);
        return hit && target.getHealth() + target.getAbsorptionAmount() < previous;
    }

    void applyCorrosion(LivingEntity target, boolean weakening) {
        if (this.isBaby() || !this.isAlive() || !this.isValidAcidTarget(target)) { return; }
        boolean resistant = target.is(ModTags.EntityTypes.TOXIC_CONTROL_RESISTANT) || target.getMaxHealth() >= 80.0F;
        target.addEffect(new MobEffectInstance(PhantomismContent.ARMOR_FRACTURE, resistant ? 80 : 120, 0), this);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, resistant ? 60 : 80, 0), this);
        if (weakening) { target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, resistant ? 40 : 60, 0), this); }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || !(target instanceof LivingEntity victim) || !this.isValidAcidTarget(victim)) { return false; }
        boolean dive = this.isDivingStrike() && this.diveCooldown == 0;
        if (dive) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                    new AttributeModifier(DIVE_DAMAGE, 3.0D, AttributeModifier.Operation.ADD_VALUE));
        }
        float previous = victim.getHealth() + victim.getAbsorptionAmount();
        boolean hit;
        try {
            hit = victim instanceof EnderDragon ? this.hurtAcidTarget(level, victim, this.damageSources().mobAttack(this),
                    (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE)) : super.doHurtTarget(level, target);
        } finally { if (dive) { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_DAMAGE); } }
        hit &= victim.getHealth() + victim.getAbsorptionAmount() < previous;
        if (hit) {
            this.applyCorrosion(victim, dive);
            if (dive) {
                this.diveCooldown = DIVE_COOLDOWN;
                this.animate(PhantomAnimationAction.CHARGE);
                PhantomVisualEffects.impact(level, PhantomVfxTheme.TOXIC, victim.position().add(0.0D, 0.5D, 0.0D));
                PhantomVisualEffects.shockwave(level, PhantomVfxTheme.TOXIC, victim.position(), 2.4F);
            }
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float previous = this.getHealth() + this.getAbsorptionAmount();
        boolean hurt = super.hurtServer(level, source, damage);
        float received = previous - this.getHealth() - this.getAbsorptionAmount();
        if (hurt && received > 0.0F) {
            this.recentDamageTicks = 60;
            if (!this.reflecting && !source.is(DamageTypes.THORNS) && this.isAlive() && !this.isBaby() && this.isAcidShieldActive()
                    && source.getEntity() instanceof LivingEntity attacker && this.isValidAcidTarget(attacker)) {
                this.reflecting = true;
                try {
                    if (this.hurtAcidTarget(level, attacker, this.damageSources().thorns(this), Math.min(4.0F, received * 0.25F))) {
                        PhantomVisualEffects.impact(level, PhantomVfxTheme.TOXIC, attacker.position().add(0.0D, 0.5D, 0.0D));
                    }
                } finally { this.reflecting = false; }
            }
        }
        return hurt;
    }

    @Override
    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (this.diveCooldown == 0 || !this.isValidAcidTarget(target) || this.getControllingPassenger() != null || this.threatensFamily(target)) { return false; }
        Vec3 direction = target.position().subtract(this.position()).normalize();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x).scale(this.getId() % 2 == 0 ? 3.0D : -3.0D);
        Vec3 position = target.position().subtract(direction.scale(7.0D)).add(side).add(0.0D, 4.0D, 0.0D);
        this.moveControl.setWantedPosition(position.x, position.y, position.z, 1.2D);
        return true;
    }

    @Override
    public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation);
        this.cancelAreas();
    }

    private void cancelAreas() {
        this.areaTicks = 0;
        this.rainTicks = 0;
        this.areaCenter = null;
        this.rainCenter = null;
        this.entityData.set(RAINING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        this.projectileCooldown = Math.max(0, this.projectileCooldown - 1);
        this.pulseCooldown = Math.max(0, this.pulseCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1);
        this.areaCooldown = Math.max(0, this.areaCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.shieldTicks = Math.max(0, this.shieldTicks - 1);
        this.rainCooldown = Math.max(0, this.rainCooldown - 1);
        this.abilityRecovery = Math.max(0, this.abilityRecovery - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (this.isBaby() || !this.isAlive()) { this.cancelAreas(); this.shieldTicks = 0; return; }
        this.tickAreas(level);
        LivingEntity target = this.getTarget();
        if (!this.isValidAcidTarget(target) || !this.hasLineOfSight(target)) { return; }
        if (this.tickCount % 10 == 0) { this.planAbilities(level, target); }
        if (this.getControllingPassenger() == null && this.abilityRecovery == 0 && this.projectileCooldown == 0
                && this.distanceToSqr(target) <= 32.0D * 32.0D && this.distanceToSqr(target) > 9.0D
                && this.launchAcid(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
            this.projectileCooldown = PROJECTILE_COOLDOWN;
            this.abilityRecovery = 6;
        }
    }

    private void planAbilities(ServerLevel level, LivingEntity target) {
        if (this.shieldCooldown == 0 && (this.recentDamageTicks > 0 && this.getHealth() <= this.getMaxHealth() * 0.8F
                || this.getHealth() <= this.getMaxHealth() * 0.55F || this.threatensFamily(target) && target.getMaxHealth() >= 60.0F)
                && (!this.hasEffect(MobEffects.RESISTANCE) || this.getEffect(MobEffects.RESISTANCE).getAmplifier() < 1)) {
            this.shieldCooldown = SHIELD_COOLDOWN;
            this.shieldTicks = SHIELD_DURATION;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_DURATION, 1), this);
            this.animate(PhantomAnimationAction.SHIELD);
        }
        if (this.abilityRecovery > 0) { return; }
        boolean fractured = target.hasEffect(PhantomismContent.ARMOR_FRACTURE);
        boolean corrosionReady = fractured || target.getMaxHealth() >= 80.0F
                && (target.is(ModTags.EntityTypes.TOXIC_CONTROL_RESISTANT)
                        || !target.canBeAffected(new MobEffectInstance(PhantomismContent.ARMOR_FRACTURE, 80, 0)));
        if (this.rainCooldown == 0 && this.rainTicks == 0 && this.distanceToSqr(target) <= 400.0D && corrosionReady
                && (this.enemiesAt(level, target.position(), 12.0D, 5.0D).size() >= 3
                        || target.getMaxHealth() >= 60.0F && target.getHealth() > 20.0F)) {
            this.startRain(target.position());
            return;
        }
        if (this.areaCooldown == 0 && this.areaTicks == 0 && this.distanceToSqr(target) <= 24.0D * 24.0D && corrosionReady
                && (this.enemiesAt(level, target.position(), 10.0D, 4.0D).size() >= 2
                        || target.getMaxHealth() >= 40.0F && target.getHealth() > 20.0F)) {
            this.startArea(target.position());
            return;
        }
        if (this.pulseCooldown == 0 && (this.enemiesAt(level, this.position(), 6.0D, 4.0D).size() >= 2
                || this.recentDamageTicks > 0 && this.distanceToSqr(target) <= 16.0D)) { this.acidPulse(level); }
    }

    private boolean acidPulse(ServerLevel level) {
        boolean hit = false;
        for (LivingEntity enemy : this.enemiesAt(level, this.position(), 6.0D, 4.0D)) {
            if (this.hurtAcidTarget(level, enemy, this.damageSources().mobAttack(this), 3.0F)) {
                this.applyCorrosion(enemy, true);
                hit = true;
            }
        }
        if (hit) {
            this.pulseCooldown = PULSE_COOLDOWN;
            this.abilityRecovery = 12;
            this.animate(PhantomAnimationAction.EMPOWER);
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.TOXIC, this.position().add(0.0D, 0.05D, 0.0D), 3.8F);
            this.playSound(SoundEvents.SLIME_BLOCK_BREAK, 0.9F, 0.65F);
        }
        return hit;
    }

    private void startArea(Vec3 center) {
        this.areaCenter = center;
        this.areaTicks = AREA_DURATION;
        this.areaCooldown = AREA_COOLDOWN;
        this.abilityRecovery = 12;
        this.animate(PhantomAnimationAction.BARRIER);
    }

    private void startRain(Vec3 center) {
        this.rainCenter = center;
        this.rainTicks = RAIN_DURATION;
        this.rainCooldown = RAIN_COOLDOWN;
        this.abilityRecovery = 20;
        this.entityData.set(RAINING, true);
        this.animate(PhantomAnimationAction.ULTIMATE);
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.85F, 0.7F);
    }

    private boolean validCenter(@Nullable Vec3 center, double radius) {
        return center != null && Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
                && Math.abs(center.x) < 30_000_000 && Math.abs(center.z) < 30_000_000 && this.distanceToSqr(center) <= 64.0D * 64.0D
                && this.isLoadedFlightBox(new AABB(center, center).inflate(radius, 6.0D, radius));
    }

    private void tickAreas(ServerLevel level) {
        if (this.areaTicks > 0) {
            if (!this.validCenter(this.areaCenter, 10.0D)) { this.areaTicks = 0; this.areaCenter = null; }
            else {
                if (this.areaTicks % 20 == 0) {
                    this.zonePulse(level, this.areaCenter, 10.0D, false);
                    PhantomVisualEffects.toxicMist(level, this.areaCenter.add(0.0D, 0.4D, 0.0D), 5.5F);
                }
                if (--this.areaTicks == 0) { this.areaCenter = null; }
            }
        }
        if (this.rainTicks > 0) {
            if (!this.validCenter(this.rainCenter, 12.0D)) { this.rainTicks = 0; this.rainCenter = null; this.entityData.set(RAINING, false); }
            else {
                if (this.rainTicks % 5 == 0) { PhantomVisualEffects.acidRain(level, this.rainCenter, 12.0F); }
                if (this.rainTicks % 20 == 0) {
                    this.zonePulse(level, this.rainCenter, 12.0D, true);
                    PhantomVisualEffects.toxicMist(level, this.rainCenter.add(0.0D, 0.35D, 0.0D), 6.5F);
                }
                if (--this.rainTicks == 0) { this.rainCenter = null; this.entityData.set(RAINING, false); }
            }
        }
    }

    private void zonePulse(ServerLevel level, Vec3 center, double radius, boolean rain) {
        for (LivingEntity enemy : this.enemiesAt(level, center, radius, rain ? 5.0D : 4.0D)) {
            if (rain && !this.clearAttackLine(level, new Vec3(enemy.getX(), center.y + 6.0D, enemy.getZ()), enemy.getEyePosition())) { continue; }
            if (this.hurtAcidTarget(level, enemy, this.damageSources().mobAttack(this), rain ? 2.0F : 1.0F)) {
                this.applyCorrosion(enemy, rain);
                if (!rain && !enemy.is(ModTags.EntityTypes.TOXIC_CONTROL_RESISTANT) && enemy.getMaxHealth() < 80.0F) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this);
                }
            }
        }
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double front = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * front, this.getBbHeight() * 0.6D, Math.cos(yaw) * front);
    }

    private boolean launchAcid(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) { return false; }
        var acid = new AcidProjectile(PhantomismContent.ACID_PROJECTILE.get(), level);
        acid.setOwner(this);
        acid.setPos(this.projectileOrigin());
        acid.shoot(direction.x, direction.y, direction.z, 1.4F, 0.0F);
        if (!level.addFreshEntity(acid)) { return false; }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.SLIME_BLOCK_HIT, 0.65F, 1.5F);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("AcidProjectileCooldown", this.projectileCooldown);
        output.putInt("AcidPulseCooldown", this.pulseCooldown);
        output.putInt("AcidDiveCooldown", this.diveCooldown);
        output.putInt("ToxicAreaCooldown", this.areaCooldown);
        output.putInt("ToxicAreaTicks", this.areaTicks);
        output.putInt("AcidShieldCooldown", this.shieldCooldown);
        output.putInt("AcidShieldTicks", this.shieldTicks);
        output.putInt("AcidRainCooldown", this.rainCooldown);
        output.putInt("AcidRainTicks", this.rainTicks);
        output.storeNullable("ToxicAreaCenter", Vec3.CODEC, this.areaCenter);
        output.storeNullable("AcidRainCenter", Vec3.CODEC, this.rainCenter);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.projectileCooldown = Math.clamp(input.getIntOr("AcidProjectileCooldown", 0), 0, PROJECTILE_COOLDOWN);
        this.pulseCooldown = Math.clamp(input.getIntOr("AcidPulseCooldown", 0), 0, PULSE_COOLDOWN);
        this.diveCooldown = Math.clamp(input.getIntOr("AcidDiveCooldown", 0), 0, DIVE_COOLDOWN);
        this.areaCooldown = Math.clamp(input.getIntOr("ToxicAreaCooldown", 0), 0, AREA_COOLDOWN);
        this.shieldCooldown = Math.clamp(input.getIntOr("AcidShieldCooldown", 0), 0, SHIELD_COOLDOWN);
        this.rainCooldown = Math.clamp(input.getIntOr("AcidRainCooldown", 0), 0, RAIN_COOLDOWN);
        this.shieldTicks = this.isBaby() ? 0 : Math.clamp(input.getIntOr("AcidShieldTicks", 0), 0, SHIELD_DURATION);
        this.areaCenter = input.read("ToxicAreaCenter", Vec3.CODEC).orElse(null);
        this.rainCenter = input.read("AcidRainCenter", Vec3.CODEC).orElse(null);
        this.areaTicks = this.isBaby() || !this.validCenter(this.areaCenter, 10.0D) ? 0 : Math.clamp(input.getIntOr("ToxicAreaTicks", 0), 0, AREA_DURATION);
        this.rainTicks = this.isBaby() || !this.validCenter(this.rainCenter, 12.0D) ? 0 : Math.clamp(input.getIntOr("AcidRainTicks", 0), 0, RAIN_DURATION);
        this.entityData.set(RAINING, this.rainTicks > 0);
    }
}
