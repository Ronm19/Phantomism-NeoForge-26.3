package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.RavagerCrowdControl;
import net.ronm19.phantomism.entity.ai.RavagerSiegeBreaking;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import org.jspecify.annotations.Nullable;

public final class RavagerPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/ravager_phantom");
    private static final Identifier FURY_FLIGHT = id("ravager_fury_flight");
    private static final Identifier FURY_SPEED = id("ravager_fury_speed");
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(RavagerPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FURIOUS = SynchedEntityData.defineId(RavagerPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final String[] ACTIONS = {"charge_windup", "charge_impact", "bite", "dive_impact", "ram", "roar", "slam_windup", "slam_impact", "fury"};
    private final Set<UUID> chargeHits = new HashSet<>();
    private int chargeCooldown;
    private int diveCooldown;
    private int biteCooldown;
    private int ramCooldown;
    private int roarCooldown;
    private int slamCooldown;
    private int furyCooldown;
    private int furyTicks;
    private int windupTicks;
    private int chargeTicks;
    private int slamTicks;
    private int recoveryTicks;
    private int recentDamageTicks;
    private int chargeApproachTicks;
    private double momentum;
    private boolean siegeCharge;
    private boolean furyStrength;
    private boolean furyResistance;
    private @Nullable MobEffectInstance previousFuryStrength;
    private @Nullable MobEffectInstance previousFuryResistance;
    private int previousStrengthTicks;
    private int previousResistanceTicks;
    private Vec3 chargeDirection = Vec3.ZERO;
    private Vec3 previousChargePosition = Vec3.ZERO;
    private @Nullable UUID slamTarget;
    private @Nullable Vec3 slamOrigin;

    public RavagerPhantom(EntityType<? extends RavagerPhantom> type, Level level) { super(type, level); }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 76.0D).add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 10.0D).add(Attributes.ARMOR_TOUGHNESS, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.45D).add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FLYING_SPEED, 0.32D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<RavagerPhantom> type, net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.entity.EntitySpawnReason reason, BlockPos position, net.minecraft.util.RandomSource random) {
        return level instanceof ServerLevel server && net.ronm19.phantomism.entity.spawn.RavagerEncounterSpawner.encounterCenter(server, position) != null;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.RAVAGER_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<RavagerPhantom> actions = new AnimationController<>("ravager_action", 2, test -> PlayState.STOP);
        for (String action : ACTIONS) { actions.triggerableAnim(action, RawAnimation.begin().thenPlay("animation.ravager." + action)); }
        registrar.add(actions.additiveAnimations());
    }

    private void action(String action) {
        if (!this.level().isClientSide() && this.isAddedToLevel() && this.getId() >= 0) { this.triggerAnim("ravager_action", action); }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
        builder.define(FURIOUS, false);
    }

    public boolean isCharging() { return this.entityData.get(CHARGING); }

    public boolean isChargeMoving() { return this.isCharging() && this.windupTicks == 0 && this.chargeTicks > 0; }

    @Override
    public boolean isClientAuthoritative() { return !this.isCharging() && super.isClientAuthoritative(); }

    public boolean isFurious() { return this.entityData.get(FURIOUS); }

    @Override
    public RawAnimation getMovementAnimation(net.ronm19.phantomism.animation.PhantomAnimationMotion motion) {
        if (this.isCharging()) { return RawAnimation.begin().thenLoop("animation.ravager.charge_flight"); }
        if (this.isFurious() && motion != net.ronm19.phantomism.animation.PhantomAnimationMotion.SIT
                && motion != net.ronm19.phantomism.animation.PhantomAnimationMotion.SLEEP) {
            return RawAnimation.begin().thenLoop("animation.ravager.fury_flight");
        }
        return super.getMovementAnimation(motion);
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected int getFlightTurnRate() { return 6; }

    @Override
    protected int getMeleeAttackCooldownTicks() { return this.isFurious() ? 28 : 40; }

    @Override
    protected int getAttackRecoveryTicks() { return this.isFurious() ? 14 : 24; }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && target instanceof Enemy
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24.0D * 24.0D;
    }

    public boolean isValidRavagerTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    public boolean isAreaEnemy(LivingEntity target) {
        return this.isValidRavagerTarget(target) && (target == this.getTarget() || target instanceof Enemy
                || target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget())));
    }

    public boolean isEligibleKill(LivingEntity victim) {
        return victim != this && victim.level() == this.level() && !(victim instanceof AbstractPhantom) && !this.isAlliedTo(victim)
                && !this.isTargetSuppressed(victim) && (victim instanceof Enemy || victim == this.getTarget())
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), target -> this.isAreaEnemy(target)
                && target.position().distanceToSqr(center) <= radius * radius && this.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(16).toList();
    }

    private boolean damage(ServerLevel level, LivingEntity victim, float amount) {
        if (!this.isValidRavagerTarget(victim)) { return false; }
        DamageSource source = victim instanceof EnderDragon && this.getOwner() instanceof Player owner
                ? this.damageSources().playerAttack(owner) : this.damageSources().mobAttack(this);
        float strength = this.hasEffect(MobEffects.STRENGTH) ? 3.0F * (this.getEffect(MobEffects.STRENGTH).getAmplifier() + 1) : 0.0F;
        return victim instanceof EnderDragon dragon ? dragon.head.hurtServer(level, source, amount + strength)
                : victim.hurtServer(level, source, amount + strength);
    }

    private void repel(LivingEntity enemy, double strength, double lift) {
        boolean heavy = enemy.getMaxHealth() >= 80.0F;
        enemy.knockback(heavy ? strength * 0.35D : strength, this.getX() - enemy.getX(), this.getZ() - enemy.getZ(), this.damageSources().mobAttack(this), 0.0F);
        if (!heavy && lift > 0.0D) { enemy.setDeltaMovement(enemy.getDeltaMovement().add(0.0D, lift, 0.0D)); }
        enemy.syncVelocity = true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity victim) || this.isBaby() || this.isCharging() || this.slamTicks > 0
                || this.recoveryTicks > 0 || !this.isValidRavagerTarget(victim) || !this.hasLineOfSight(victim)
                || this.distanceToSqr(victim) > Math.pow(this.getBbWidth() + victim.getBbWidth() + 1.5D, 2)) { return false; }
        boolean dive = (this.isDivingStrike() || this.isDiveInProgress()) && this.diveCooldown == 0;
        if (!dive && this.biteCooldown > 0) { return false; }
        boolean hit = this.damage(level, victim, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + (dive ? 5.0F : 0.0F));
        if (!hit) { return false; }
        this.biteCooldown = this.isFurious() ? 28 : 40;
        this.action(dive ? "dive_impact" : "bite");
        PhantomVisualEffects.impact(level, PhantomVfxTheme.RAVAGER, victim.getBoundingBox().getCenter());
        this.playSound(SoundEvents.RAVAGER_ATTACK, 0.9F, 0.8F);
        if (dive) {
            this.diveCooldown = 280;
            for (LivingEntity enemy : this.enemies(level, victim.position(), 3.5D)) {
                if (enemy != victim && this.damage(level, enemy, 5.0F)) { this.repel(enemy, 0.8D, 0.2D); }
            }
            this.repel(victim, 0.9D, 0.2D);
            PhantomVisualEffects.physicalImpact(level, victim.position(), 3.5F);
            this.recoveryTicks = this.getAttackRecoveryTicks();
        }
        return true;
    }

    public boolean useRam(ServerLevel level, LivingEntity target) {
        if (level != this.level() || this.isBaby() || this.ramCooldown > 0 || this.busy() || !this.isAreaEnemy(target)
                || this.distanceToSqr(target) > 12.25D || !this.hasLineOfSight(target)) { return false; }
        this.ramCooldown = 280;
        this.recoveryTicks = 12;
        this.action("ram");
        if (this.damage(level, target, 12.0F)) {
            this.repel(target, 1.35D, 0.1D);
            RavagerCrowdControl.stagger(this, target, 30);
        }
        for (LivingEntity enemy : this.enemies(level, target.position(), 2.5D)) {
            if (enemy != target && this.damage(level, enemy, 3.0F)) { this.repel(enemy, 0.6D, 0.05D); }
        }
        PhantomVisualEffects.physicalImpact(level, target.getBoundingBox().getCenter(), 2.5F);
        this.playSound(SoundEvents.RAVAGER_STUNNED, 1.0F, 0.6F);
        return true;
    }

    public boolean useRoar(ServerLevel level) {
        if (level != this.level() || this.isBaby() || this.roarCooldown > 0 || this.busy()) { return false; }
        List<LivingEntity> nearby = this.enemies(level, this.position(), 12.0D);
        if (nearby.stream().noneMatch(enemy -> !RavagerCrowdControl.fearImmune(enemy))) { return false; }
        this.roarCooldown = 500;
        this.recoveryTicks = 14;
        for (LivingEntity enemy : nearby) { RavagerCrowdControl.fear(this, enemy, 60); }
        this.action("roar");
        PhantomVisualEffects.roar(level, PhantomVfxTheme.RAVAGER, this.position().add(0.0D, 0.5D, 0.0D), 6.0F);
        this.playSound(SoundEvents.RAVAGER_ROAR, 1.25F, 0.7F);
        return true;
    }

    private @Nullable Vec3 ground(ServerLevel level) {
        Vec3 start = this.position().add(0.0D, 0.1D, 0.0D);
        Vec3 end = start.add(0.0D, -2.5D, 0.0D);
        if (!this.isLoadedFlightBox(new AABB(start, end).inflate(this.getBbWidth() * 0.5D))) { return null; }
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos().above()).isEmpty() ? hit.getLocation().add(0.0D, 0.05D, 0.0D) : null;
    }

    public boolean startSlam(ServerLevel level, LivingEntity target) {
        Vec3 ground = this.ground(level);
        if (level != this.level() || this.isBaby() || this.slamCooldown > 0 || this.busy() || ground == null || !this.isAreaEnemy(target)
                || target.position().distanceToSqr(ground) > 25.0D || !this.hasLineOfSight(target)) { return false; }
        this.slamCooldown = 400;
        this.slamTicks = 12;
        this.slamTarget = target.getUUID();
        this.slamOrigin = ground;
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.action("slam_windup");
        PhantomVisualEffects.shockwave(level, PhantomVfxTheme.RAVAGER, ground, 1.0F);
        return true;
    }

    private void finishSlam(ServerLevel level) {
        Vec3 center = this.ground(level);
        Entity target = this.slamTarget == null ? null : level.getEntity(this.slamTarget);
        if (center == null || this.slamOrigin == null || center.distanceToSqr(this.slamOrigin) > 4.0D
                || !(target instanceof LivingEntity enemy) || !this.isValidRavagerTarget(enemy)) { this.slamOrigin = null; return; }
        for (LivingEntity victim : this.enemies(level, center, 5.0D)) {
            if (this.damage(level, victim, 10.0F)) {
                this.repel(victim, 0.5D, 0.6D);
                RavagerCrowdControl.stagger(this, victim, 20);
            }
        }
        this.action("slam_impact");
        this.recoveryTicks = 18;
        PhantomVisualEffects.physicalImpact(level, center, 5.0F);
        this.playSound(SoundEvents.RAVAGER_STEP, 1.3F, 0.5F);
        this.slamOrigin = null;
        this.slamTarget = null;
    }

    public boolean startCharge(ServerLevel level, Vec3 direction, boolean siege) {
        if (level != this.level() || this.isBaby() || this.chargeCooldown > 0 || this.busy() || !Double.isFinite(direction.lengthSqr())
                || direction.horizontalDistanceSqr() < 1.0E-6D || siege && (!(this.getControllingPassenger() instanceof ServerPlayer rider) || !this.isOwnedBy(rider))) { return false; }
        this.chargeDirection = new Vec3(direction.x, 0.0D, direction.z).normalize();
        if (!this.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.chargeDirection.scale(3.0D)))
                || this.alliesInPath(level, this.chargeDirection.scale(3.0D))) { return false; }
        float heading = (float) Math.toDegrees(Math.atan2(-this.chargeDirection.x, this.chargeDirection.z));
        this.setYRot(heading);
        this.yBodyRot = heading;
        this.yHeadRot = heading;
        this.chargeCooldown = 320;
        this.windupTicks = this.isFurious() ? 8 : 12;
        this.chargeTicks = 30;
        this.momentum = 0.15D;
        this.siegeCharge = siege;
        this.chargeHits.clear();
        this.previousChargePosition = this.position();
        this.entityData.set(CHARGING, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.action("charge_windup");
        this.playSound(SoundEvents.RAVAGER_AMBIENT, 0.7F, 0.6F);
        return true;
    }

    public boolean activateSiegeCharge(Player rider) {
        return this.level() instanceof ServerLevel level && this.isAlive() && this.isOwnedBy(rider)
                && rider == this.getControllingPassenger() && this.startCharge(level, rider.getViewVector(1.0F), true);
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        Vec3 facing = rider.getViewVector(1.0F);
        LivingEntity target = this.enemies(level, this.position(), 3.5D).stream()
                .filter(enemy -> enemy.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize().dot(facing) > 0.35D)
                .findFirst().orElse(null);
        return target != null && (this.ramCooldown == 0 ? this.useRam(level, target) : this.doHurtTarget(level, target));
    }

    private boolean alliesInPath(ServerLevel level, Vec3 step) {
        return !level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().expandTowards(step).inflate(0.15D),
                entity -> entity != this && !this.hasPassenger(entity) && this.isAlliedTo(entity) && entity.isAlive()).isEmpty();
    }

    private void tickCharge(ServerLevel level) {
        if (this.siegeCharge && (!(this.getControllingPassenger() instanceof Player rider) || !this.isOwnedBy(rider))) { this.endCharge(); return; }
        if (this.windupTicks > 0) {
            this.windupTicks--;
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (!level.getEntitiesOfClass(Creeper.class, this.getBoundingBox().expandTowards(this.chargeDirection.scale(6.0D)).inflate(1.0D),
                creeper -> creeper.isIgnited() || creeper.getSwellDir() > 0).isEmpty()) { this.endCharge(); return; }
        Vec3 traveled = this.position().subtract(this.previousChargePosition);
        AABB swept = this.getBoundingBox().expandTowards(traveled.reverse()).inflate(0.2D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, swept, this::isAreaEnemy)) {
            if (this.chargeHits.size() >= 12) { break; }
            if (this.hasLineOfSight(victim) && this.chargeHits.add(victim.getUUID()) && this.damage(level, victim, (float) Math.min(18.0D, 8.0D + this.momentum * 8.0D))) {
                this.repel(victim, Math.min(1.6D, 0.5D + this.momentum), 0.1D);
                RavagerCrowdControl.stagger(this, victim, 20);
                this.action("charge_impact");
                PhantomVisualEffects.physicalImpact(level, victim.getBoundingBox().getCenter(), 2.0F);
            }
        }
        this.previousChargePosition = this.position();
        this.momentum = Math.min(this.isFurious() ? 1.25D : 1.1D, this.momentum + (this.isFurious() ? 0.08D : 0.06D));
        Vec3 step = this.chargeDirection.scale(this.momentum);
        AABB next = this.getBoundingBox().expandTowards(step);
        if (--this.chargeTicks <= 0 || !this.isLoadedFlightBox(next) || this.alliesInPath(level, step)) { this.endCharge(); return; }
        this.momentum = RavagerSiegeBreaking.clearForwardSlice(level, this, this.chargeDirection, this.momentum, this.siegeCharge);
        step = this.chargeDirection.scale(this.momentum);
        if (this.momentum < 0.12D || !this.hasClearFlightCorridor(step)) { this.endCharge(); return; }
        this.setDeltaMovement(step);
        if (this.tickCount % 4 == 0) {
            PhantomVisualEffects.trail(level, PhantomVfxTheme.RAVAGER, this.position().add(0.0D, 0.5D, 0.0D));
            this.syncVelocity = true;
        }
    }

    private void endCharge() {
        boolean active = this.isCharging();
        this.windupTicks = 0;
        this.chargeTicks = 0;
        this.momentum = 0.0D;
        this.siegeCharge = false;
        this.entityData.set(CHARGING, false);
        this.chargeHits.clear();
        if (active) { this.setDeltaMovement(this.getDeltaMovement().scale(0.2D)); this.recoveryTicks = this.isFurious() ? 12 : 24; }
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isCharging()) { this.move(MoverType.SELF, this.getDeltaMovement()); return; }
        if (this.slamTicks > 0 && !this.level().isClientSide()) { this.setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }

    private boolean busy() { return this.isCharging() || this.slamTicks > 0 || this.recoveryTicks > 0; }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.busy()) {
            if (this.tickCount % 20 == 0) { this.getConfiguredStats().apply(this); }
            this.getNavigation().stop();
            this.moveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            return;
        }
        super.customServerAiStep(level);
    }

    @Override
    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (this.getControllingPassenger() != null || !this.isValidRavagerTarget(target)) { return false; }
        double distance = this.distanceToSqr(target);
        if (this.diveCooldown > 0 || this.getY() <= target.getY() + 1.0D) {
            this.moveControl.setWantedPosition(target.getX(), target.getY(0.5D), target.getZ(), 1.1D);
            if (distance <= 9.0D) { this.doHurtTarget(level, target); }
            return true;
        }
        return false;
    }

    @Override
    protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (this.chargeCooldown > 0 || this.getControllingPassenger() != null || this.busy() || !this.isValidRavagerTarget(target)
                || !this.hasLineOfSight(target) || this.isDiveInProgress()
                || this.diveCooldown == 0 && Math.abs(this.getY() - target.getY()) > 2.0D) {
            this.chargeApproachTicks = 0;
            return false;
        }
        if (++this.chargeApproachTicks > 80) { this.chargeApproachTicks = 0; this.chargeCooldown = 40; return false; }
        Vec3 offset = target.position().subtract(this.position());
        if (this.ramCooldown == 0 && offset.lengthSqr() <= 12.25D && target.getMaxHealth() >= 40.0F
                && this.useRam(level, target)) { return true; }
        if (offset.horizontalDistanceSqr() >= 36.0D && offset.horizontalDistanceSqr() <= 256.0D && Math.abs(offset.y) <= 2.0D
                && this.hasClearFlightCorridor(offset) && this.startCharge(level, offset, false)) { this.chargeApproachTicks = 0; return true; }
        Vec3 away = this.position().subtract(target.position());
        away = away.horizontalDistanceSqr() < 0.1D ? new Vec3(0.0D, 0.0D, -1.0D) : new Vec3(away.x, 0.0D, away.z).normalize();
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        Vec3 destination = target.position().add(away.scale(8.0D)).add(side.scale(4.0D)).add(0.0D, 0.7D, 0.0D);
        if (!this.hasClearFlightCorridor(destination.subtract(this.position()))) { return false; }
        this.moveControl.setWantedPosition(destination.x, destination.y, destination.z, 1.0D);
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) { this.recentDamageTicks = 60; }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        this.chargeCooldown = Math.max(0, this.chargeCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1);
        this.biteCooldown = Math.max(0, this.biteCooldown - 1);
        this.ramCooldown = Math.max(0, this.ramCooldown - 1);
        this.roarCooldown = Math.max(0, this.roarCooldown - 1);
        this.slamCooldown = Math.max(0, this.slamCooldown - 1);
        this.furyCooldown = Math.max(0, this.furyCooldown - 1);
        this.recoveryTicks = Math.max(0, this.recoveryTicks - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (!this.isAlive() || this.isBaby()) { this.cancelCombatAbilities(); return; }
        if (this.furyTicks > 0) {
            this.maintainFuryEffect(false);
            this.maintainFuryEffect(true);
            if (--this.furyTicks == 0) { this.endFury(); }
            else if (this.tickCount % 8 == 0) {
                PhantomVisualEffects.aura(level, PhantomVfxTheme.RAVAGER, this.getBoundingBox().getCenter());
                PhantomVisualEffects.trail(level, PhantomVfxTheme.RAVAGER, this.position().add(this.getViewVector(1.0F).scale(-0.8D)));
            }
        }
        if (this.isTame() && this.tickCount % 40 == 0) { this.presence(level); }
        if (this.isCharging()) { this.tickCharge(level); return; }
        if (this.slamTicks > 0) { if (--this.slamTicks == 0) { this.finishSlam(level); } return; }
        LivingEntity target = this.getTarget();
        if (!this.isValidRavagerTarget(target) || this.busy() || this.tickCount % 5 != 0) { return; }
        List<LivingEntity> nearby = this.enemies(level, this.position(), 12.0D);
        if (this.furyCooldown == 0 && !this.isFurious() && (nearby.size() >= 3 || target.getMaxHealth() >= 70.0F
                || this.recentDamageTicks > 0 && this.getHealth() < this.getMaxHealth() * 0.45F)) { this.activateFury(); }
        if (this.getControllingPassenger() != null) { return; }
        if (this.isDiveInProgress()) { return; }
        if (this.roarCooldown == 0 && nearby.size() >= 3 && (this.recentDamageTicks > 0 || this.distanceToSqr(target) <= 16.0D)) {
            this.useRoar(level);
        } else if (this.slamCooldown == 0 && this.enemies(level, this.position(), 5.0D).size() >= 2 && this.startSlam(level, target)) {
            return;
        } else if (this.ramCooldown == 0 && this.distanceToSqr(target) <= 12.25D
                && (target.getMaxHealth() >= 40.0F || this.recentDamageTicks > 0 || this.biteCooldown > 0)) { this.useRam(level, target); }
    }

    private void presence(ServerLevel level) {
        boolean intimidated = false;
        for (LivingEntity enemy : this.enemies(level, this.position(), 8.0D)) {
            if (enemy instanceof Raider mob && mob.getTarget() != null && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()))) {
                RavagerCrowdControl.presence(this, mob, 8);
                intimidated = true;
            }
        }
        if (intimidated) { PhantomVisualEffects.shockwave(level, PhantomVfxTheme.RAVAGER, this.position().add(0.0D, 0.1D, 0.0D), 0.85F); }
    }

    public boolean activateFury() {
        if (!(this.level() instanceof ServerLevel level) || this.isBaby() || !this.isAlive() || this.isFurious() || this.furyCooldown > 0) { return false; }
        this.furyCooldown = 1200;
        this.furyTicks = 240;
        this.entityData.set(FURIOUS, true);
        this.maintainFuryEffect(false);
        this.maintainFuryEffect(true);
        this.getAttribute(Attributes.FLYING_SPEED).addOrUpdateTransientModifier(new AttributeModifier(FURY_FLIGHT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        this.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(new AttributeModifier(FURY_SPEED, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        this.action("fury");
        PhantomVisualEffects.roar(level, PhantomVfxTheme.RAVAGER, this.position(), 4.0F);
        return true;
    }

    public void rewardFuryKill(LivingEntity victim) {
        if (this.isFurious() && this.isEligibleKill(victim)) { this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), this); }
    }

    private void endFury() {
        this.restoreFuryEffect(false);
        this.restoreFuryEffect(true);
        this.furyTicks = 0;
        this.furyStrength = false;
        this.furyResistance = false;
        this.previousFuryStrength = null;
        this.previousFuryResistance = null;
        this.entityData.set(FURIOUS, false);
        this.getAttribute(Attributes.FLYING_SPEED).removeModifier(FURY_FLIGHT);
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FURY_SPEED);
    }

    private void maintainFuryEffect(boolean resistance) {
        var effect = resistance ? MobEffects.RESISTANCE : MobEffects.STRENGTH;
        int amplifier = resistance ? 1 : 0;
        MobEffectInstance current = this.getEffect(effect);
        if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier
                && (current.getDuration() < 0 || current.getDuration() + 1 >= this.furyTicks))) { return; }
        if (resistance && !this.furyResistance) {
            this.previousFuryResistance = current == null ? null : new MobEffectInstance(current);
            this.previousResistanceTicks = this.furyTicks;
        } else if (!resistance && !this.furyStrength) {
            this.previousFuryStrength = current == null ? null : new MobEffectInstance(current);
            this.previousStrengthTicks = this.furyTicks;
        }
        if (resistance) { this.furyResistance = true; } else { this.furyStrength = true; }
        this.addEffect(new MobEffectInstance(effect, this.furyTicks, amplifier), this);
    }

    private void restoreFuryEffect(boolean resistance) {
        boolean owned = resistance ? this.furyResistance : this.furyStrength;
        var effect = resistance ? MobEffects.RESISTANCE : MobEffects.STRENGTH;
        var current = this.getEffect(effect);
        int amplifier = resistance ? 1 : 0;
        if (!owned || current == null || current.getAmplifier() != amplifier || current.getDuration() < 0 || current.getDuration() > this.furyTicks + 1) { return; }
        this.removeEffect(effect);
        var previous = resistance ? this.previousFuryResistance : this.previousFuryStrength;
        if (previous == null) { return; }
        int captured = resistance ? this.previousResistanceTicks : this.previousStrengthTicks;
        int duration = previous.getDuration() < 0 ? -1 : previous.getDuration() - Math.max(0, captured - this.furyTicks);
        if (duration > 0 || duration == -1) {
            this.addEffect(new MobEffectInstance(effect, duration, previous.getAmplifier(), previous.isAmbient(), previous.isVisible(), previous.showIcon()), this);
        }
    }

    private void cancelCombatAbilities() {
        if (this.level().isClientSide()) { return; }
        this.endCharge();
        this.recoveryTicks = 0;
        this.chargeApproachTicks = 0;
        this.slamTicks = 0;
        this.slamTarget = null;
        this.slamOrigin = null;
        this.endFury();
        RavagerCrowdControl.release(this);
    }

    @Override
    public void clearCombatTarget(boolean suppressRetaliation) { super.clearCombatTarget(suppressRetaliation); this.cancelCombatAbilities(); }

    @Override
    public void setOrder(Order order) { super.setOrder(order); this.cancelCombatAbilities(); }

    @Override
    public void remove(RemovalReason reason) { this.cancelCombatAbilities(); super.remove(reason); }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("RavagerChargeCooldown", this.chargeCooldown);
        output.putInt("RavagerDiveCooldown", this.diveCooldown);
        output.putInt("RavagerBiteCooldown", this.biteCooldown);
        output.putInt("RavagerRamCooldown", this.ramCooldown);
        output.putInt("RavagerRoarCooldown", this.roarCooldown);
        output.putInt("RavagerSlamCooldown", this.slamCooldown);
        output.putInt("RavagerFuryCooldown", this.furyCooldown);
        output.putInt("RavagerFuryTicks", this.furyTicks);
        output.putBoolean("RavagerFuryStrength", this.furyStrength);
        output.putBoolean("RavagerFuryResistance", this.furyResistance);
        if (this.previousFuryStrength != null) { output.store("RavagerPreviousStrength", MobEffectInstance.CODEC, this.previousFuryStrength); }
        if (this.previousFuryResistance != null) { output.store("RavagerPreviousResistance", MobEffectInstance.CODEC, this.previousFuryResistance); }
        output.putInt("RavagerPreviousStrengthTicks", this.previousStrengthTicks);
        output.putInt("RavagerPreviousResistanceTicks", this.previousResistanceTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.chargeCooldown = Math.clamp(input.getIntOr("RavagerChargeCooldown", 0), 0, 320);
        this.diveCooldown = Math.clamp(input.getIntOr("RavagerDiveCooldown", 0), 0, 280);
        this.biteCooldown = Math.clamp(input.getIntOr("RavagerBiteCooldown", 0), 0, 40);
        this.ramCooldown = Math.clamp(input.getIntOr("RavagerRamCooldown", 0), 0, 280);
        this.roarCooldown = Math.clamp(input.getIntOr("RavagerRoarCooldown", 0), 0, 500);
        this.slamCooldown = Math.clamp(input.getIntOr("RavagerSlamCooldown", 0), 0, 400);
        this.furyCooldown = Math.clamp(input.getIntOr("RavagerFuryCooldown", 0), 0, 1200);
        this.furyTicks = Math.clamp(input.getIntOr("RavagerFuryTicks", 0), 0, 240);
        this.furyStrength = input.getBooleanOr("RavagerFuryStrength", false);
        this.furyResistance = input.getBooleanOr("RavagerFuryResistance", false);
        this.previousFuryStrength = input.read("RavagerPreviousStrength", MobEffectInstance.CODEC).orElse(null);
        this.previousFuryResistance = input.read("RavagerPreviousResistance", MobEffectInstance.CODEC).orElse(null);
        this.previousStrengthTicks = Math.clamp(input.getIntOr("RavagerPreviousStrengthTicks", 0), 0, 240);
        this.previousResistanceTicks = Math.clamp(input.getIntOr("RavagerPreviousResistanceTicks", 0), 0, 240);
        this.cancelCombatAbilities();
        this.recoveryTicks = 0;
    }
}
