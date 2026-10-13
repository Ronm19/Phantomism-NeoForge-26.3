package net.ronm19.phantomism.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

public final class CherryPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/cherry_phantom");
    private static final int HEAL_COOLDOWN = 20 * 20;
    private static final int BARRAGE_COOLDOWN = 12 * 20;
    private static final int VEIL_COOLDOWN = 25 * 20;
    private static final int SANCTUARY_COOLDOWN = 75 * 20;
    private static final int SANCTUARY_DURATION = 10 * 20;
    private int healCooldown;
    private int barrageCooldown;
    private int veilCooldown;
    private int sanctuaryCooldown;
    private int sanctuaryTicks;
    private int auraCooldown;
    private int recentDamageTicks;

    public CherryPhantom(EntityType<? extends CherryPhantom> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 34.0D).add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 3.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<CherryPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.CHERRY_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() { return 2; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.CHERRY_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected boolean prefersRangedCombat() { return true; }

    @Override
    protected int getMeleeAttackCooldownTicks() { return 80; }

    @Override
    protected int getAttackRecoveryTicks() { return 40; }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return !this.isBaby() && this.launchPetals(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 24.0D * 24.0D;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) { this.recentDamageTicks = 60; }
        return hurt;
    }

    boolean isValidPetalTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.isBaby() && target instanceof LivingEntity victim && this.isValidPetalTarget(victim)
                && super.doHurtTarget(level, target);
    }

    private boolean isHealingFamily(LivingEntity target) {
        return target == this || this.isTame() && this.getOwnerReference() != null
                && (target == this.getOwner() || target instanceof OwnableEntity companion
                        && this.getOwnerReference().equals(companion.getOwnerReference()));
    }

    private List<LivingEntity> allies(ServerLevel level, double radius) {
        LivingEntity owner = this.getOwner();
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
                        target -> target.isAlive() && !target.isRemoved() && !target.isSpectator()
                                && this.isHealingFamily(target) && this.distanceToSqr(target) <= radius * radius
                                && (target == this || this.hasLineOfSight(target)))
                .stream().sorted(Comparator.<LivingEntity>comparingInt(target -> target == owner ? 0 : 1)
                        .thenComparingDouble(target -> target.getHealth() / target.getMaxHealth())).limit(12).toList();
    }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getTarget() || target == this.getLastHurtByMob()
                || target instanceof Mob mob && mob.getTarget() != null && this.isHealingFamily(mob.getTarget());
    }

    private List<LivingEntity> threats(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(12.0D),
                        target -> (target instanceof Enemy || target instanceof Player player && !player.isCreative() && !player.isSpectator())
                                && this.isValidPetalTarget(target)
                                && this.threatensFamily(target) && this.distanceToSqr(target) <= 144.0D && this.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(this::distanceToSqr)).limit(8).toList();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        this.healCooldown = Math.max(0, this.healCooldown - 1);
        this.barrageCooldown = Math.max(0, this.barrageCooldown - 1);
        this.veilCooldown = Math.max(0, this.veilCooldown - 1);
        this.sanctuaryCooldown = Math.max(0, this.sanctuaryCooldown - 1);
        this.auraCooldown = Math.max(0, this.auraCooldown - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        this.ambientPetals(level);
        if (this.isBaby()) { this.sanctuaryTicks = 0; return; }
        if (this.tickCount % 20 == 0) { this.supportFamily(level); }
        if (this.sanctuaryTicks > 0) {
            if (this.sanctuaryTicks % 20 == 0) {
                PhantomVisualEffects.blossomStorm(level, this.position().add(0.0D, 0.6D, 0.0D));
            }
            if (this.sanctuaryTicks % 40 == 0) { this.pulseSanctuary(level); }
            this.sanctuaryTicks--;
        }
        LivingEntity target = this.getTarget();
        if (this.getControllingPassenger() == null && this.sanctuaryTicks == 0 && this.barrageCooldown == 0
                && this.isValidPetalTarget(target) && this.distanceToSqr(target) <= 28.0D * 28.0D
                && this.hasLineOfSight(target) && this.launchPetals(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
            this.barrageCooldown = BARRAGE_COOLDOWN;
        }
    }

    private void ambientPetals(ServerLevel level) {
        if (!this.isAddedToLevel()) { return; }
        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 0.004D;
        int interval = moving ? 12 : 80;
        if (this.tickCount % interval == 0) {
            Vec3 behind = this.position().subtract(this.getViewVector(1.0F).scale(0.55D)).add(0.0D, 0.6D, 0.0D);
            PhantomVisualEffects.petals(level, behind, this.isBaby() ? 1 : moving ? 2 : 1, this.isBaby() ? 0.1F : 0.16F);
        }
    }

    private void supportFamily(ServerLevel level) {
        List<LivingEntity> family = this.allies(level, 12.0D);
        List<LivingEntity> attackers = this.threats(level);
        if (this.healCooldown == 0 && family.stream().anyMatch(target ->
                target.getHealth() <= target.getMaxHealth() * 0.65F || target.getMaxHealth() - target.getHealth() >= 6.0F)) {
            this.healCooldown = HEAL_COOLDOWN;
            for (LivingEntity target : family) {
                if (target.getHealth() < target.getMaxHealth()) {
                    target.heal(6.0F);
                    PhantomVisualEffects.healingBlossom(level, target.position().add(0.0D, 0.5D, 0.0D));
                }
            }
            this.animate(PhantomAnimationAction.BLESSING);
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.65F, 1.35F);
        }
        if (this.auraCooldown == 0) {
            this.auraCooldown = 100;
            for (LivingEntity target : family) {
                if (this.distanceToSqr(target) <= 64.0D && target.getHealth() < target.getMaxHealth()
                        && !target.hasEffect(MobEffects.REGENERATION)
                        && target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true), this)) {
                    PhantomVisualEffects.petals(level, target.position().add(0.0D, 0.6D, 0.0D), 2, 0.13F);
                }
            }
        }
        boolean danger = !attackers.isEmpty() && (this.recentDamageTicks > 0
                || family.stream().anyMatch(target -> target.getHealth() <= target.getMaxHealth() * 0.7F)
                || attackers.stream().anyMatch(target -> target instanceof Mob mob && mob.getTarget() != null && this.isHealingFamily(mob.getTarget())
                        || family.stream().anyMatch(ally -> ally.getLastHurtByMob() == target
                                && ally.tickCount - ally.getLastHurtByMobTimestamp() <= 60)));
        if (danger && this.veilCooldown == 0) {
            boolean protectedAlly = false;
            for (LivingEntity target : family) {
                if (this.distanceToSqr(target) <= 100.0D && !target.hasEffect(MobEffects.RESISTANCE)) {
                    protectedAlly |= target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 0, true, false, true), this);
                }
            }
            if (protectedAlly) {
                this.veilCooldown = VEIL_COOLDOWN;
                this.animate(PhantomAnimationAction.SHIELD);
                PhantomVisualEffects.petals(level, this.position().add(0.0D, 0.7D, 0.0D), 12, 0.2F);
            }
        }
        long seriouslyHurt = family.stream().filter(target -> target.getHealth() <= target.getMaxHealth() * 0.6F).count();
        if (this.sanctuaryTicks == 0 && this.sanctuaryCooldown == 0 && (seriouslyHurt >= 2 || attackers.size() >= 3)) {
            this.sanctuaryTicks = SANCTUARY_DURATION;
            this.sanctuaryCooldown = SANCTUARY_COOLDOWN;
            this.animate(PhantomAnimationAction.ULTIMATE);
            this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F);
        }
    }

    private void pulseSanctuary(ServerLevel level) {
        for (LivingEntity target : this.allies(level, 12.0D)) {
            if (target.getHealth() < target.getMaxHealth()) {
                target.heal(2.0F);
                PhantomVisualEffects.petals(level, target.position().add(0.0D, 0.5D, 0.0D), 3, 0.14F);
            }
        }
        for (LivingEntity target : this.threats(level)) {
            if (target.hurtServer(level, this.damageSources().mobAttack(this), 1.0F)) {
                boolean resistant = target.is(ModTags.EntityTypes.CHERRY_CONTROL_RESISTANT) || target.getMaxHealth() >= 80.0F;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, resistant ? 20 : 40, 0), this);
            }
        }
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double front = this.getBbWidth() * 0.6D + 0.2D;
        return this.position().add(-Math.sin(yaw) * front, this.getBbHeight() * 0.6D, Math.cos(yaw) * front);
    }

    private boolean launchPetals(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) { return false; }
        var petals = new PetalProjectile(PhantomismContent.PETAL_PROJECTILE.get(), level);
        petals.setOwner(this);
        petals.setPos(this.projectileOrigin());
        petals.shoot(direction.x, direction.y, direction.z, 1.4F, 0.0F);
        if (!level.addFreshEntity(petals)) { return false; }
        this.animate(PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.SNOWBALL_THROW, 0.55F, 1.3F);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("HealingBlossomCooldown", this.healCooldown);
        output.putInt("PetalBarrageCooldown", this.barrageCooldown);
        output.putInt("SakuraVeilCooldown", this.veilCooldown);
        output.putInt("EternalBlossomCooldown", this.sanctuaryCooldown);
        output.putInt("BlossomAuraCooldown", this.auraCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.healCooldown = Math.clamp(input.getIntOr("HealingBlossomCooldown", 0), 0, HEAL_COOLDOWN);
        this.barrageCooldown = Math.clamp(input.getIntOr("PetalBarrageCooldown", 0), 0, BARRAGE_COOLDOWN);
        this.veilCooldown = Math.clamp(input.getIntOr("SakuraVeilCooldown", 0), 0, VEIL_COOLDOWN);
        this.sanctuaryCooldown = Math.clamp(input.getIntOr("EternalBlossomCooldown", 0), 0, SANCTUARY_COOLDOWN);
        this.auraCooldown = Math.clamp(input.getIntOr("BlossomAuraCooldown", 0), 0, 100);
        this.sanctuaryTicks = 0;
        this.recentDamageTicks = 0;
    }
}
