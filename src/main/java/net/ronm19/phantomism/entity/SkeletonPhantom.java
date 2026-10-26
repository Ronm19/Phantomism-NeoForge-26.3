package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
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
import net.ronm19.phantomism.entity.ai.SkeletonAttackLedger;
import net.ronm19.phantomism.entity.ai.SkeletonPresence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class SkeletonPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/skeleton_phantom");
    private static final EntityDataAccessor<Boolean> EYE = SynchedEntityData.defineId(SkeletonPhantom.class, EntityDataSerializers.BOOLEAN);
    private final SkeletonAttackLedger attacks = new SkeletonAttackLedger();
    private int volleyCooldown;
    private int biteCooldown;
    private int eyeCooldown;
    private int eyeTicks;
    private int guardCooldown;
    private int guardTicks;
    private int rainCooldown;
    private int rainTicks;
    private int recentHurt;
    private @Nullable UUID rainAttack;

    public SkeletonPhantom(EntityType<? extends SkeletonPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.35D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D).add(Attributes.FLYING_SPEED, 0.36D).add(Attributes.FOLLOW_RANGE, 32);
    }
    public static boolean checkSpawnRules(EntityType<SkeletonPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.OVERWORLD && level.getLevel().isDarkOutside() && level.getBiome(position).is(ModTags.Biomes.SKELETON_PHANTOM_SPAWNS)
                && level.canSeeSky(position) && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SKELETON_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) { return !(target instanceof Player) && !SkeletonPresence.isCalmed(this, target); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(EYE, false); }
    public boolean isSkeletonEyeActive() { return this.entityData.get(EYE); }
    public SkeletonAttackLedger attacks() { return this.attacks; }
    public boolean isValidSkeletonTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && !(target instanceof AbstractPhantom) && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null)
                && !this.isAlliedTo(target) && this.isSpeciesCombatTarget(target) && !this.isTargetSuppressed(target)
                && (target instanceof Enemy || target == this.getTarget() || target == this.getLastHurtByMob())
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.05D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> arrowTargets(ServerLevel level, Vec3 forward, boolean rain) {
        if (forward.lengthSqr() < 0.01D) { return List.of(); }
        Vec3 facing = forward.normalize();
        return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(rain ? 24 : 42), this::isValidSkeletonTarget)
                .stream().filter(target -> this.distanceToSqr(target) <= (rain ? 24 * 24 : 42 * 42))
                .filter(target -> rain || target.getEyePosition().subtract(this.getEyePosition()).normalize().dot(facing) >= 0.5D)
                .filter(target -> this.line(level, this.getEyePosition(), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::targetPriority).thenComparingInt(Entity::getId)).toList();
    }
    private double targetPriority(LivingEntity target) {
        double priority = target == this.getTarget() ? -10000 : 0;
        if (target instanceof Mob mob && mob.getTarget() != null && this.isAlliedTo(mob.getTarget())) { priority -= 1000; }
        if (target instanceof RangedAttackMob || target.is(EntityTypeTags.SKELETONS)) { priority -= 100; }
        return priority + Math.min(80, target.getHealth() + target.getAbsorptionAmount()) + this.distanceToSqr(target) * 0.02D;
    }
    public SkeletonArrow.Kind adaptiveArrow(LivingEntity target, List<LivingEntity> nearby) {
        if (target.is(EntityTypeTags.UNDEAD) || target.isInvertedHealAndHarm()) { return SkeletonArrow.Kind.ANTI_UNDEAD; }
        if (nearby.stream().filter(other -> other != target && other.distanceToSqr(target) <= 6.25D).count() >= 2) { return SkeletonArrow.Kind.EXPLOSIVE; }
        if (!target.fireImmune() && !target.isInWater() && !target.level().isRainingAt(target.blockPosition()) && !target.isOnFire() && target.getHealth() > 12) { return SkeletonArrow.Kind.FIRE; }
        if (!target.hasEffect(MobEffects.SLOWNESS) && (target.getDeltaMovement().lengthSqr() > 0.015D || this.distanceToSqr(target) < 64)) { return SkeletonArrow.Kind.ICE; }
        return SkeletonArrow.Kind.PIERCING;
    }
    private float arrowDamage(SkeletonArrow.Kind kind) {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + (kind == SkeletonArrow.Kind.ANTI_UNDEAD ? 2 : 0) + (this.isSkeletonEyeActive() ? 3 : 0);
    }
    private float usefulDamage(LivingEntity target, float damage) {
        float estimate = CombatRules.getDamageAfterAbsorb(target, damage, this.damageSources().mobAttack(this), target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance = target.getEffect(MobEffects.RESISTANCE);
        return Math.max(0.1F, estimate * (resistance == null ? 1 : Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2F)));
    }
    public int fireAdaptiveVolley(ServerLevel level, Vec3 forward) {
        boolean ridden = this.getControllingPassenger() instanceof Player;
        if (this.level() != level || this.isBaby() || !this.isAlive() || this.rainTicks > 0 || this.volleyCooldown > 50 - (ridden ? this.getRiddenProjectileAbilityCooldownTicks() : 50)) { return 0; }
        List<LivingEntity> targets = this.arrowTargets(level, forward, false).stream().limit(SkeletonAttackLedger.VOLLEY_LIMIT).toList();
        if (targets.isEmpty()) { return 0; }
        UUID attack = this.attacks.begin(false, this.tickCount); int fired = 0;
        for (LivingEntity target : targets) {
            SkeletonArrow.Kind kind = this.adaptiveArrow(target, targets);
            Vec3 origin = this.getEyePosition().add(forward.normalize().scale(0.7D));
            if (this.spawnArrow(level, attack, target, origin, kind, false)) { fired++; }
        }
        if (fired > 0) { this.volleyCooldown = 50; this.showSkeletonAction(fired > 1 ? "volley" : "arrow"); this.playSound(SoundEvents.SKELETON_SHOOT, 0.7F, 1.1F); }
        return fired;
    }
    private boolean spawnArrow(ServerLevel level, UUID attack, LivingEntity target, Vec3 origin, SkeletonArrow.Kind kind, boolean rain) {
        if (!this.isValidSkeletonTarget(target) || !this.line(level, origin, target.getEyePosition())) { return false; }
        SkeletonArrow arrow = PhantomismContent.SKELETON_ARROW.get().create(level, EntitySpawnReason.TRIGGERED);
        float damage = this.arrowDamage(kind);
        if (!this.attacks.reserve(attack, arrow.getUUID(), target.getUUID(), this.usefulDamage(target, damage))) { return false; }
        arrow.configure(this, attack, target.getUUID(), kind, damage, rain); arrow.setPos(origin);
        Vec3 hitPoint = target.position().add(0, target.getBbHeight() * 0.5D, 0);
        if (!this.line(level, origin, hitPoint)) { hitPoint = target.getEyePosition(); }
        double distance = origin.distanceTo(hitPoint);
        double leadTicks = Math.min(8, distance / (rain ? 2.2D : 1.8D));
        if (target instanceof Mob mob && mob.isNoAi()) { leadTicks = Math.min(1.5D, leadTicks); }
        Vec3 lead = target.getDeltaMovement().scale(leadTicks);
        if (lead.lengthSqr() > 4) { lead = lead.normalize().scale(2); }
        Vec3 aim = hitPoint.add(lead).subtract(origin);
        arrow.shoot(aim.x, aim.y, aim.z, rain ? 2.2F : 1.8F, this.isSkeletonEyeActive() ? 0 : 0.1F);
        if (!level.addFreshEntity(arrow)) { this.attacks.failedSpawn(attack, arrow.getUUID()); return false; }
        return true;
    }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.fireAdaptiveVolley(level, rider.getViewVector(1)) > 0; }
    public boolean rainOfArrows(ServerLevel level) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.rainCooldown > 0 || this.rainTicks > 0
                || this.getOrder() == Order.STAY || this.getOrder() == Order.RETURN_HOME
                || this.arrowTargets(level, this.getViewVector(1), true).stream().noneMatch(target -> this.rainOrigin(level, target) != null)) { return false; }
        this.rainAttack = this.attacks.begin(true, this.tickCount); this.rainTicks = 100; this.rainCooldown = 1800;
        this.showSkeletonAction("rain"); PhantomVisualEffects.action(level, this, net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE); return true;
    }
    private @Nullable Vec3 rainOrigin(ServerLevel level, LivingEntity target) {
        Vec3 origin = target.getEyePosition().add(0.35D, 8, 0.35D);
        return origin.y < level.getMaxY() && this.line(level, origin, target.getEyePosition()) ? origin : null;
    }
    private void tickRain(ServerLevel level) {
        if (this.rainTicks <= 0 || this.rainAttack == null) { return; }
        if (this.getOrder() == Order.STAY || this.getOrder() == Order.RETURN_HOME || !this.attacks.active(this.rainAttack)) { this.rainTicks = 0; return; }
        this.rainTicks--;
        if (this.rainTicks % 12 != 0 || this.attacks.issued(this.rainAttack) >= SkeletonAttackLedger.RAIN_LIMIT) { return; }
        var targets = this.arrowTargets(level, this.getViewVector(1), true); int fired = 0;
        for (LivingEntity target : targets) {
            if (fired >= 6 || this.attacks.issued(this.rainAttack) >= SkeletonAttackLedger.RAIN_LIMIT) { break; }
            if (this.attacks.allocated(this.rainAttack, target.getUUID()) >= SkeletonAttackLedger.RAIN_TARGET_LIMIT
                    || target.getHealth() + target.getAbsorptionAmount() <= this.attacks.pending(this.rainAttack, target.getUUID())) { continue; }
            Vec3 origin = this.rainOrigin(level, target); if (origin == null) { continue; }
            SkeletonArrow.Kind kind = this.adaptiveArrow(target, targets); if (kind == SkeletonArrow.Kind.EXPLOSIVE) { kind = SkeletonArrow.Kind.PIERCING; }
            if (this.spawnArrow(level, this.rainAttack, target, origin, kind, true)) { fired++; }
        }
        if (targets.isEmpty()) { this.rainTicks = 0; }
    }
    public boolean eyeOfTheSkeleton(ServerLevel level) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.eyeCooldown > 0) { return false; }
        this.eyeTicks = 200; this.eyeCooldown = 700; this.entityData.set(EYE, true); this.showSkeletonAction("eye");
        PhantomVisualEffects.action(level, this, net.ronm19.phantomism.animation.PhantomAnimationAction.EMPOWER); return true;
    }
    public boolean boneGuard(ServerLevel level) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.guardCooldown > 0) { return false; }
        this.guardTicks = 120; this.guardCooldown = 500; this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 0)); this.showSkeletonAction("guard");
        PhantomVisualEffects.action(level, this, net.ronm19.phantomism.animation.PhantomAnimationAction.SHIELD); return true;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || this.isBaby() || !this.isAlive() || !this.isValidSkeletonTarget(target) || this.biteCooldown > 0
                || this.distanceToSqr(target) > 9 || !this.getBoundingBox().inflate(1).intersects(target.getBoundingBox()) || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean hit = super.doHurtTarget(level, target);
        if (hit && target.getHealth() + target.getAbsorptionAmount() < before) { this.biteCooldown = 160; this.fracture(target); this.showSkeletonAction("bite"); }
        return hit;
    }
    public void fracture(LivingEntity target) { if (this.isValidSkeletonTarget(target)) { target.addEffect(new MobEffectInstance(PhantomismContent.BONE_FRACTURE, 100, 0), this); } }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.isValidSkeletonTarget(target) || this.getOrder() == Order.STAY || this.getOrder() == Order.RETURN_HOME) { this.clearCombatTarget(true); return true; }
        this.getLookControl().setLookAt(target, 30, 30);
        if (this.recentHurt > 0) { this.boneGuard(level); }
        if (this.distanceToSqr(target) > 64 || target instanceof RangedAttackMob) { this.eyeOfTheSkeleton(level); }
        if (this.rainTicks > 0) { this.getNavigation().stop(); return true; }
        if (this.arrowTargets(level, this.getViewVector(1), true).size() >= 3 || target.getMaxHealth() >= 80) { this.rainOfArrows(level); }
        Vec3 aim = target.getEyePosition().subtract(this.getEyePosition());
        if (this.distanceToSqr(target) > 9 && this.fireAdaptiveVolley(level, this.getViewVector(1)) > 0) { return true; }
        if (this.distanceToSqr(target) <= 9) { return false; }
        double desired = target instanceof RangedAttackMob || target.is(EntityTypeTags.SKELETONS) ? 14 : 10;
        Vec3 side = new Vec3(-aim.z, 0, aim.x).normalize().scale(this.getId() % 2 == 0 ? 2.5D : -2.5D);
        Vec3 destination = target.position().subtract(aim.normalize().scale(desired)).add(side).add(0, 2, 0);
        Vec3 step = destination.subtract(this.position());
        if (this.line(level, this.position(), destination) && level.noBlockCollision(this, this.getBoundingBox().move(step)) && !level.containsAnyLiquid(this.getBoundingBox().move(step))) {
            this.flyToAssignment(destination, 1.1D);
        } else { this.flyToAssignment(target.position().add(0, 2, 0), 0.8D); }
        return true;
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && this.isValidSkeletonTarget(target)
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24 * 24;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (!this.isBaby()) { SkeletonPresence.pulse(this, level); }
        if (this.getTarget() != null && !this.isSpeciesCombatTarget(this.getTarget())) { this.clearCombatTarget(false); }
        if (!this.isBaby() && !this.isVehicle() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME
                && (this.getTarget() == null || this.getTarget() instanceof Mob mob && SkeletonPresence.eligible(mob))) {
            Mob intercept = SkeletonPresence.interceptionTarget(this, level);
            if (intercept != null && this.getAssignments().permitsDefensivePursuit(intercept)
                    && this.line(level, this.getEyePosition(), intercept.getEyePosition())) {
                if (this.getTarget() != null) { this.clearCombatTarget(false); }
                this.getConfiguredStats().apply(this); this.getLookControl().setLookAt(intercept);
                this.flyToAssignment(intercept.position().add(0, 2, 0), 1.1D); return;
            }
        }
        super.customServerAiStep(level);
        if (!this.isTame() && !this.isBaby() && this.getTarget() == null && this.tickCount % 20 == 0) {
            level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(12), player -> !player.isSpectator()
                    && (player.getMainHandItem().is(Items.BONE) || player.getOffhandItem().is(Items.BONE)))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).filter(player -> this.distanceToSqr(player) > 9)
                    .ifPresent(player -> this.flyToAssignment(player.position().add(0, 2, 0), 0.8D));
        }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float before = this.getHealth(); boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.getHealth() < before) { this.recentHurt = 60; }
        return hurt;
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level)) { return; }
        this.volleyCooldown = Math.max(0, this.volleyCooldown - 1); this.biteCooldown = Math.max(0, this.biteCooldown - 1);
        this.eyeCooldown = Math.max(0, this.eyeCooldown - 1); this.eyeTicks = Math.max(0, this.eyeTicks - 1); this.entityData.set(EYE, this.eyeTicks > 0);
        this.guardCooldown = Math.max(0, this.guardCooldown - 1); this.guardTicks = Math.max(0, this.guardTicks - 1);
        this.rainCooldown = Math.max(0, this.rainCooldown - 1); this.recentHurt = Math.max(0, this.recentHurt - 1); this.attacks.tick(this.tickCount);
        if (this.isBaby() || !this.isAlive()) { this.rainTicks = 0; this.attacks.clear(); return; }
        this.tickRain(level);
        if (this.guardTicks > 0 && this.tickCount % 10 == 0) {
            PhantomVisualEffects.aura(level, PhantomVfxTheme.SKELETON, this.getBoundingBox().getCenter());
            for (int fragment = 0; fragment < 8; fragment++) {
                double angle = fragment * Math.PI / 4 + this.tickCount * 0.04D;
                Vec3 point = this.getBoundingBox().getCenter().add(Math.cos(angle) * 1.4D, Math.sin(angle * 2) * 0.3D, Math.sin(angle) * 1.4D);
                level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, Items.BONE),
                        point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
    }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<SkeletonPhantom>("skeleton_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"arrow", "volley", "bite", "eye", "guard", "rain", "presence"}) { controller.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.skeleton." + name)); }
        registrar.add(controller.additiveAnimations());
    }
    public void showSkeletonAction(String name) { if (this.level() instanceof ServerLevel && this.isAddedToLevel()) { this.triggerAnim("skeleton_action", name); } }
    @Override public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation);
        if (suppressRetaliation) { this.rainTicks = 0; this.attacks.clear(); }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void remove(RemovalReason reason) { SkeletonPresence.release(this); this.attacks.clear(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("SkeletonVolleyCooldown", this.volleyCooldown); output.putInt("SkeletonBiteCooldown", this.biteCooldown);
        output.putInt("SkeletonEyeCooldown", this.eyeCooldown); output.putInt("SkeletonEyeTicks", this.eyeTicks); output.putInt("SkeletonGuardCooldown", this.guardCooldown);
        output.putInt("SkeletonGuardTicks", this.guardTicks); output.putInt("SkeletonRainCooldown", this.rainCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.volleyCooldown = Math.clamp(input.getIntOr("SkeletonVolleyCooldown", 0), 0, 50); this.biteCooldown = Math.clamp(input.getIntOr("SkeletonBiteCooldown", 0), 0, 160);
        this.eyeCooldown = Math.clamp(input.getIntOr("SkeletonEyeCooldown", 0), 0, 700); this.eyeTicks = Math.clamp(input.getIntOr("SkeletonEyeTicks", 0), 0, 200);
        this.guardCooldown = Math.clamp(input.getIntOr("SkeletonGuardCooldown", 0), 0, 500); this.guardTicks = Math.clamp(input.getIntOr("SkeletonGuardTicks", 0), 0, 120);
        this.rainCooldown = Math.clamp(input.getIntOr("SkeletonRainCooldown", 0), 0, 1800); this.rainTicks = 0; this.attacks.clear(); this.entityData.set(EYE, this.eyeTicks > 0);
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "rain_of_arrows" -> this.rainCooldown;
            case "eye_of_the_skeleton" -> this.eyeCooldown;
            case "bone_guard" -> this.guardCooldown;
            default -> -1;
        };
    }
}
