package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.EndRescue;
import net.ronm19.phantomism.entity.ai.EndTeleportSafety;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class EndPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/end_phantom");
    private static final EntityDataAccessor<Boolean> RESCUING = SynchedEntityData.defineId(EndPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OVERDRIVE = SynchedEntityData.defineId(EndPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final Identifier ADAPTATION = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "end_adaptation");
    private static final Identifier EYE_ATTACK = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "end_eye_attack");
    private static final Identifier EYE_FLIGHT = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "end_eye_flight");
    private static final String[] ACTIONS = {"rescue", "catch", "rescue_land", "blink", "teleport", "bite", "rift_strike", "shield", "eye", "evade"};
    private final EndRescue rescue = new EndRescue(this);
    private int blinkCooldown;
    private int teleportCooldown;
    private int biteCooldown;
    private int riftCooldown;
    private int shieldCooldown;
    private int eyeCooldown;
    private int eyeTicks;
    private int evasionCooldown;
    private int evadeTicks;
    private int recentDamageTicks;
    private @Nullable Vec3 evadeDestination;
    private @Nullable Vec3 lastSafePosition;

    public EndPhantom(EntityType<? extends EndPhantom> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 56).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 6).add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.25D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D).add(Attributes.FLYING_SPEED, 0.45D).add(Attributes.FOLLOW_RANGE, 32);
    }

    public static boolean checkSpawnRules(EntityType<EndPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason,
            BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.END && level.getBiome(position).is(ModTags.Biomes.END_PHANTOM_SPAWNS)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.END_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RESCUING, false);
        builder.define(OVERDRIVE, false);
    }

    public boolean isRescuing() { return this.entityData.get(RESCUING); }

    public void setRescuing(boolean active) { this.entityData.set(RESCUING, active); }

    public EndRescue rescue() { return this.rescue; }

    public boolean isEyeActive() { return this.entityData.get(OVERDRIVE); }

    @Override
    protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<EndPhantom> actions = new AnimationController<>("end_action", 2, test -> PlayState.STOP);
        for (String action : ACTIONS) { actions.triggerableAnim(action, RawAnimation.begin().thenPlay("animation.end." + action)); }
        registrar.add(actions.additiveAnimations());
    }

    public void showEndAction(String name) {
        if (this.level() instanceof ServerLevel level && this.isAddedToLevel()) {
            this.triggerAnim("end_action", name);
            if (name.equals("rescue") || name.equals("catch") || name.equals("rescue_land")) {
                PhantomVisualEffects.rift(level, this.getBoundingBox().getCenter(), 0.9F);
            }
        }
    }

    @Override
    public RawAnimation getMovementAnimation(net.ronm19.phantomism.animation.PhantomAnimationMotion motion) {
        if (this.isRescuing()) { return RawAnimation.begin().thenLoop("animation.end.rescue_glide"); }
        if (this.isEyeActive() && motion != net.ronm19.phantomism.animation.PhantomAnimationMotion.SIT
                && motion != net.ronm19.phantomism.animation.PhantomAnimationMotion.SLEEP) {
            return RawAnimation.begin().thenLoop("animation.end.eye_flight");
        }
        return super.getMovementAnimation(motion);
    }

    @Override
    public boolean canAddPassenger(Entity passenger) { return this.rescue.accepts(passenger) || super.canAddPassenger(passenger); }

    @Override
    public @Nullable LivingEntity getControllingPassenger() { return this.isRescuing() ? null : super.getControllingPassenger(); }

    @Override
    public boolean isClientAuthoritative() { return !this.isRescuing() && super.isClientAuthoritative(); }

    @Override
    public void travel(Vec3 input) {
        if (this.isRescuing()) { this.move(MoverType.SELF, this.getDeltaMovement()); return; }
        super.travel(input);
    }

    @Override
    protected int getFlightTurnRate() { return 12; }

    @Override
    public int getMaxSpawnClusterSize() { return 2; }

    @Override
    protected boolean isSuitableIdleFlightPosition(Vec3 destination, net.minecraft.world.phys.AABB clearance) {
        return super.isSuitableIdleFlightPosition(destination, clearance) && (!(this.level() instanceof ServerLevel level)
                || level.dimension() != Level.END || EndTeleportSafety.support(level, this, destination, 48).isPresent());
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.rescue.active() || this.tickCount % 4 == 0 && this.rescue.tryStart(level)) {
            if (this.tickCount % 20 == 0) { this.getConfiguredStats().apply(this); }
            this.getNavigation().stop();
            return;
        }
        if (this.evadeTicks > 0 && this.evadeDestination != null) {
            this.getNavigation().stop();
            this.flyToAssignment(this.evadeDestination, 1.65D);
            return;
        }
        super.customServerAiStep(level);
        if (!this.isTame() && this.getTarget() == null && this.getPassengers().isEmpty()) {
            level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(12.0D), player -> !player.isSpectator()
                    && (player.getMainHandItem().is(Items.ENDER_PEARL) || player.getOffhandItem().is(Items.ENDER_PEARL)))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(player -> {
                        Vec3 approach = player.position().add(0.0D, 2.5D, 0.0D);
                        if (this.distanceToSqr(player) > 9.0D && EndTeleportSafety.corridor(level, this, approach.subtract(this.position()), false)) {
                            this.flyToAssignment(approach, 1.0D);
                        }
                    });
        }
    }

    public boolean blink(ServerLevel level, Vec3 destination) {
        if (this.blinkCooldown > 0 || !this.teleportSafely(level, destination, 15, false, null)) { return false; }
        this.blinkCooldown = this.isEyeActive() ? 90 : 180;
        this.showEndAction("blink");
        return true;
    }

    public boolean endTeleport(ServerLevel level, Vec3 destination) {
        if (this.teleportCooldown > 0 || !this.teleportSafely(level, destination, 25, false, null)) { return false; }
        this.teleportCooldown = 340;
        this.showEndAction("teleport");
        return true;
    }

    public boolean teleportForRescue(ServerLevel level, Vec3 destination, LivingEntity victim) {
        if (!this.rescue.active() || !this.rescue.accepts(victim)) { return false; }
        if (this.blinkCooldown == 0 && this.teleportSafely(level, destination, 15, true, victim)) {
            this.blinkCooldown = 180;
            return true;
        }
        if (this.teleportCooldown == 0 && this.teleportSafely(level, destination, 25, true, victim)) {
            this.teleportCooldown = 340;
            return true;
        }
        return false;
    }

    private boolean teleportSafely(ServerLevel level, Vec3 destination, double range, boolean interception, @Nullable Entity ignored) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.isPassenger()
                || destination.distanceToSqr(this.position()) < 0.01D
                || destination.distanceToSqr(this.position()) > range * range
                || !EndTeleportSafety.destination(level, this, destination, interception, ignored)) { return false; }
        var event = EventHooks.onEnderTeleport(this, destination.x, destination.y, destination.z);
        destination = event.getTarget();
        if (event.isCanceled() || event.getTargetLevel() != level || destination.distanceToSqr(this.position()) > range * range
                || destination.distanceToSqr(this.position()) < 0.01D
                || !EndTeleportSafety.destination(level, this, destination, interception, ignored)) { return false; }
        Vec3 origin = this.position();
        if (this.teleport(new TeleportTransition(level, destination, Vec3.ZERO, this.getYRot(), this.getXRot(), TeleportTransition.DO_NOTHING)) != this) { return false; }
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFallDistance();
        for (Entity passenger : this.getPassengers()) {
            this.positionRider(passenger);
            passenger.resetFallDistance();
        }
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.1F);
        PhantomVisualEffects.rift(level, origin.add(0.0D, 0.4D, 0.0D), 0.9F);
        PhantomVisualEffects.rift(level, this.position().add(0.0D, 0.4D, 0.0D), 0.9F);
        return this.position().distanceToSqr(origin) > 0.01D;
    }

    public boolean isValidEndTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean combatEnemy(LivingEntity target) {
        return this.isValidEndTarget(target) && (target == this.getTarget() || target instanceof Enemy || target == this.getLastHurtByMob());
    }

    private float endBonus(LivingEntity target) {
        return target instanceof Enderman || target instanceof Endermite || target instanceof Shulker ? 3.0F : target instanceof EnderDragon ? 1.0F : 0;
    }

    private boolean damage(ServerLevel level, LivingEntity target, float base) {
        if (!this.isValidEndTarget(target)) { return false; }
        DamageSource source = target instanceof EnderDragon && this.getOwner() instanceof Player player
                ? this.damageSources().playerAttack(player) : this.damageSources().mobAttack(this);
        float amount = base + this.endBonus(target);
        return target instanceof EnderDragon dragon ? dragon.head.hurtServer(level, source, amount) : target.hurtServer(level, source, amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (level != this.level() || this.isBaby() || this.isRescuing() || this.biteCooldown > 0 || !(target instanceof LivingEntity victim)
                || !this.isValidEndTarget(victim) || !this.hasLineOfSight(victim)
                || this.distanceToSqr(victim) > Math.pow(this.getBbWidth() + victim.getBbWidth() + 1.5D, 2)) { return false; }
        if (!this.damage(level, victim, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) { return false; }
        this.biteCooldown = this.isEyeActive() ? 22 : 32;
        this.showEndAction("bite");
        PhantomVisualEffects.impact(level, PhantomVfxTheme.END, victim.getBoundingBox().getCenter());
        this.playSound(SoundEvents.PHANTOM_BITE, 0.7F, 1.2F);
        return true;
    }

    public boolean riftStrike(ServerLevel level, LivingEntity target) {
        if (level != this.level() || this.isBaby() || this.isRescuing() || this.riftCooldown > 0 || !this.combatEnemy(target)
                || this.distanceToSqr(target) > 15.0D * 15.0D || !this.hasLineOfSight(target)) { return false; }
        Vec3 origin = this.position();
        for (int sample = 0; sample < 8; sample++) {
            double angle = sample * Math.PI / 4.0D;
            Vec3 destination = target.position().add(Math.cos(angle) * 2.1D, EndTeleportSafety.passengerClearance(this), Math.sin(angle) * 2.1D);
            if (!this.teleportSafely(level, destination, 15, false, null)) { continue; }
            this.riftCooldown = this.isEyeActive() ? 120 : 240;
            this.biteCooldown = this.isEyeActive() ? 22 : 32;
            this.showEndAction("rift_strike");
            PhantomVisualEffects.beam(level, PhantomVfxTheme.END, origin.add(0.0D, 0.4D, 0.0D), this.getBoundingBox().getCenter());
            if (this.isValidEndTarget(target) && this.hasLineOfSight(target)
                    && this.distanceToSqr(target) <= Math.pow(this.getBbWidth() + target.getBbWidth() + 1.5D, 2)
                    && this.damage(level, target, 11.0F + (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            - this.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()))) {
                PhantomVisualEffects.crosscut(level, PhantomVfxTheme.END, target.getBoundingBox().getCenter());
            }
            return true;
        }
        return false;
    }

    public boolean endShield(ServerLevel level) {
        if (level != this.level() || !this.isAlive() || this.isBaby() || this.isRescuing() || this.shieldCooldown > 0) { return false; }
        this.shieldCooldown = 440;
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0), this);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0), this);
        this.showEndAction("shield");
        PhantomVisualEffects.aura(level, PhantomVfxTheme.END, this.getBoundingBox().getCenter());
        PhantomVisualEffects.rift(level, this.getBoundingBox().getCenter(), 1.4F);
        return true;
    }

    public boolean eyeOfEnder(ServerLevel level) {
        if (level != this.level() || !this.isAlive() || this.isBaby() || this.isRescuing() || this.eyeCooldown > 0 || this.isEyeActive()) { return false; }
        this.eyeTicks = 220;
        this.eyeCooldown = 1400;
        this.entityData.set(OVERDRIVE, true);
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(EYE_ATTACK, 2.0D, AttributeModifier.Operation.ADD_VALUE));
        this.getAttribute(Attributes.FLYING_SPEED).addOrUpdateTransientModifier(new AttributeModifier(EYE_FLIGHT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        this.blinkCooldown = Math.min(this.blinkCooldown, 90);
        this.riftCooldown = Math.min(this.riftCooldown, 120);
        this.evasionCooldown = Math.min(this.evasionCooldown, 12);
        this.showEndAction("eye");
        PhantomVisualEffects.impact(level, PhantomVfxTheme.END, this.getBoundingBox().getCenter());
        PhantomVisualEffects.shockwave(level, PhantomVfxTheme.END, this.position(), 3.0F);
        return true;
    }

    private void endEye() {
        this.eyeTicks = 0;
        this.entityData.set(OVERDRIVE, false);
        this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(EYE_ATTACK);
        this.getAttribute(Attributes.FLYING_SPEED).removeModifier(EYE_FLIGHT);
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && target instanceof Enemy
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24.0D * 24.0D;
    }

    @Override
    protected int getMeleeAttackCooldownTicks() { return this.isEyeActive() ? 22 : 32; }

    @Override
    protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        return this.isRescuing() || this.distanceToSqr(target) > 16.0D && this.riftStrike(level, target);
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        if (this.isRescuing() || this.isBaby()) { return false; }
        Vec3 facing = rider.getViewVector(1.0F);
        var target = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(15.0D), this::combatEnemy).stream()
                .filter(enemy -> enemy.getBoundingBox().getCenter().subtract(rider.getEyePosition()).normalize().dot(facing) > 0.75D)
                .min(Comparator.comparingDouble(this::distanceToSqr));
        if (target.isPresent()) { return this.riftStrike(level, target.get()); }
        Vec3 destination = this.position().add(new Vec3(facing.x, 0, facing.z).normalize().scale(23.0D));
        var landing = EndTeleportSafety.landing(level, this, destination, 4);
        return landing.isPresent() && this.endTeleport(level, landing.get());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            this.recentDamageTicks = 60;
            if (this.isEyeActive() && !this.isRescuing() && source.getEntity() instanceof LivingEntity enemy && this.combatEnemy(enemy)) {
                this.evade(level, this.position().subtract(enemy.position()));
            }
        }
        return hurt;
    }

    private boolean evade(ServerLevel level, Vec3 away) {
        if (this.evasionCooldown > 0 || this.isBaby() || this.isRescuing() || away.horizontalDistanceSqr() < 0.001D) { return false; }
        Vec3 side = new Vec3(-away.z, 0, away.x).normalize();
        for (double sign : new double[]{1.0D, -1.0D}) {
            Vec3 destination = this.position().add(side.scale(sign * (this.isEyeActive() ? 6 : 3)));
            if (!EndTeleportSafety.destination(level, this, destination, false, null)) { continue; }
            if ((this.isEyeActive() || this.hasEffect(MobEffects.RESISTANCE)) && this.blink(level, destination)) {
                this.evasionCooldown = this.isEyeActive() ? 12 : 30;
                this.showEndAction("evade");
                return true;
            }
            if (!this.getPassengers().isEmpty() || !EndTeleportSafety.corridor(level, this, destination.subtract(this.position()), false)) { continue; }
            this.evadeDestination = destination;
            this.evadeTicks = 8;
            this.evasionCooldown = this.isEyeActive() ? 12 : this.hasEffect(MobEffects.RESISTANCE) ? 24 : 40;
            this.setDeltaMovement(side.scale(sign * 0.55D));
            this.syncVelocity = true;
            this.showEndAction("evade");
            PhantomVisualEffects.trail(level, PhantomVfxTheme.END, this.position());
            return true;
        }
        return false;
    }

    private void projectileSense(ServerLevel level) {
        if (this.evasionCooldown > 0 || this.isBaby() || this.isRescuing() || this.tickCount % (this.isEyeActive() ? 2 : 4) != 0) { return; }
        var threats = level.getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(8.0D), projectile ->
                (projectile instanceof AbstractArrow || projectile instanceof AbstractHurtingProjectile || projectile instanceof ShulkerBullet)
                && projectile.getDeltaMovement().lengthSqr() > 0.01D
                && (projectile.getOwner() == null || projectile.getOwner() != this && !this.isAlliedTo(projectile.getOwner())))
                .stream().sorted(Comparator.comparingDouble(this::distanceToSqr)).limit(8).toList();
        for (Projectile projectile : threats) {
            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 offset = this.getBoundingBox().getCenter().subtract(projectile.position());
            double time = offset.dot(velocity) / velocity.lengthSqr();
            if (time < 0 || time > 8 || !this.hasLineOfSight(projectile)) { continue; }
            if (this.getBoundingBox().inflate(0.6D).contains(projectile.position().add(velocity.scale(time)))
                    && this.evade(level, velocity)) { return; }
        }
    }

    private void adaptation(ServerLevel level) {
        if (!this.isBaby() && level.dimension() == Level.END && !this.onGround()) {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrUpdateTransientModifier(
                    new AttributeModifier(ADAPTATION, 0.1D, AttributeModifier.Operation.ADD_VALUE));
        } else { this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(ADAPTATION); }
        if (this.tickCount % 20 != 0 || this.isRescuing() || this.isBaby()) { return; }
        if (EndTeleportSafety.support(level, this, this.position(), 32).isPresent()) { this.lastSafePosition = this.position(); }
        else if (this.lastSafePosition != null && level.dimension() == Level.END && this.getY() < level.getMinY() + 12
                && this.endTeleport(level, this.lastSafePosition)) { return; }
        if (this.getOrder() == Order.FOLLOW && this.getPassengers().isEmpty() && this.getTarget() == null
                && this.getOwner() instanceof LivingEntity owner && owner.level() == level && this.distanceToSqr(owner) > 400
                && this.distanceToSqr(owner) < 36.0D * 36.0D) {
            EndTeleportSafety.landing(level, this, owner.position(), 8).ifPresent(destination -> this.endTeleport(level, destination));
        }
        if (level.dimension() == Level.END && this.tickCount % 40 == 0) { PhantomVisualEffects.endMotes(level, this.position()); }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        if (this.blinkCooldown > 0) { this.blinkCooldown--; }
        if (this.teleportCooldown > 0) { this.teleportCooldown--; }
        if (this.biteCooldown > 0) { this.biteCooldown--; }
        if (this.riftCooldown > 0) { this.riftCooldown--; }
        if (this.shieldCooldown > 0) { this.shieldCooldown--; }
        if (this.eyeCooldown > 0) { this.eyeCooldown--; }
        if (this.evasionCooldown > 0) { this.evasionCooldown--; }
        if (this.evadeTicks > 0) { this.evadeTicks--; }
        if (this.recentDamageTicks > 0) { this.recentDamageTicks--; }
        if (this.eyeTicks > 0 && --this.eyeTicks == 0) { this.endEye(); }
        this.rescue.tick(level);
        this.adaptation(level);
        this.projectileSense(level);
        if (this.isEyeActive() && this.tickCount % 12 == 0) { PhantomVisualEffects.aura(level, PhantomVfxTheme.END, this.getBoundingBox().getCenter()); }
        LivingEntity target = this.getTarget();
        if (!this.isBaby() && !this.isRescuing() && this.isValidEndTarget(target) && this.tickCount % 5 == 0) {
            if (this.recentDamageTicks > 0 && this.getHealth() < this.getMaxHealth() * 0.75F) { this.endShield(level); }
            if (target.getMaxHealth() >= 80 || this.recentDamageTicks > 0 && this.getHealth() < this.getMaxHealth() * 0.6F) { this.eyeOfEnder(level); }
            if (this.getPassengers().isEmpty() && this.distanceToSqr(target) > 16) { this.riftStrike(level, target); }
        }
    }

    @Override
    public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation);
        if (!this.level().isClientSide()) { this.rescue.cancel(); this.endEye(); this.evadeTicks = 0; }
    }

    @Override
    public void setOrder(Order order) {
        super.setOrder(order);
        if (!this.level().isClientSide()) { this.rescue.cancel(); this.endEye(); this.evadeTicks = 0; }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) { this.rescue.cancel(); this.endEye(); this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(ADAPTATION); }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("EndBlinkCooldown", this.blinkCooldown);
        output.putInt("EndTeleportCooldown", this.teleportCooldown);
        output.putInt("EndRiftCooldown", this.riftCooldown);
        output.putInt("EndShieldCooldown", this.shieldCooldown);
        output.putInt("EndEyeCooldown", this.eyeCooldown);
        output.putInt("EndBiteCooldown", this.biteCooldown);
        output.putInt("EndEvasionCooldown", this.evasionCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.rescue.cancel();
        this.endEye();
        this.blinkCooldown = Math.clamp(input.getIntOr("EndBlinkCooldown", 0), 0, 180);
        this.teleportCooldown = Math.clamp(input.getIntOr("EndTeleportCooldown", 0), 0, 340);
        this.riftCooldown = Math.clamp(input.getIntOr("EndRiftCooldown", 0), 0, 240);
        this.shieldCooldown = Math.clamp(input.getIntOr("EndShieldCooldown", 0), 0, 440);
        this.eyeCooldown = Math.clamp(input.getIntOr("EndEyeCooldown", 0), 0, 1400);
        this.biteCooldown = Math.clamp(input.getIntOr("EndBiteCooldown", 0), 0, 32);
        this.evasionCooldown = Math.clamp(input.getIntOr("EndEvasionCooldown", 0), 0, 40);
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "blink" -> this.blinkCooldown;
            case "rift_strike" -> this.riftCooldown;
            case "end_shield" -> this.shieldCooldown;
            case "eye_of_ender" -> this.eyeCooldown;
            default -> -1;
        };
    }
}
