package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
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
import net.ronm19.phantomism.entity.ai.ZombiePresence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class ZombiePhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/zombie_phantom");
    private static final Identifier BITE_BONUS = id("zombie_bite_damage");
    private static final int SPIT_COOLDOWN_TICKS = 240;
    private static final EntityDataAccessor<Boolean> REVIVAL_USED = SynchedEntityData.defineId(ZombiePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVIVING = SynchedEntityData.defineId(ZombiePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(ZombiePhantom.class, EntityDataSerializers.BOOLEAN);
    private int spitCooldown;
    private int biteCooldown;
    private int diveCooldown;
    private int resilienceCooldown;
    private int revivalTicks;
    private int recoveryTicks;
    private int windupTicks;
    private int diveTicks;
    private int gripTicks;
    private int pursuitStarted;
    private int progressTick;
    private double closestDistance = Double.MAX_VALUE;
    private @Nullable UUID pursuitTarget;

    public ZombiePhantom(EntityType<? extends ZombiePhantom> type, Level level) { super(type, level); }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 76).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.55D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D).add(Attributes.FLYING_SPEED, 0.32D).add(Attributes.FOLLOW_RANGE, 32);
    }

    public static boolean checkSpawnRules(EntityType<ZombiePhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.OVERWORLD && level.getLevel().isDarkOutside()
                && level.getBiome(position).is(ModTags.Biomes.ZOMBIE_PHANTOM_SPAWNS) && level.canSeeSky(position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ZOMBIE_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 3; }
    @Override protected int getFlightTurnRate() { return 6; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(REVIVAL_USED, false); builder.define(REVIVING, false); builder.define(DIVING, false);
    }

    public boolean hasRevivalAvailable() { return !this.entityData.get(REVIVAL_USED); }
    public boolean isReviving() { return this.entityData.get(REVIVING); }
    public boolean isZombieDiving() { return this.entityData.get(DIVING); }

    @Override
    protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var actions = new AnimationController<ZombiePhantom>("zombie_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"spit", "bite", "dive_windup", "dive_impact", "resilience", "rise"}) {
            actions.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.zombie." + name));
        }
        registrar.add(actions.additiveAnimations());
    }

    private void action(String name) { if (this.level() instanceof ServerLevel && this.isAddedToLevel()) { this.triggerAnim("zombie_action", name); } }

    @Override
    public RawAnimation getMovementAnimation(net.ronm19.phantomism.animation.PhantomAnimationMotion motion) {
        if (this.isReviving()) { return RawAnimation.begin().thenLoop("animation.zombie.revival_recovery"); }
        if (this.isZombieDiving() && this.windupTicks == 0) { return RawAnimation.begin().thenLoop("animation.zombie.dive_flight"); }
        return super.getMovementAnimation(motion);
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) { return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.REGENERATION) && super.canBeAffected(effect); }
    @Override public boolean canAddPassenger(Entity passenger) { return !this.isReviving() && !this.isZombieDiving() && super.canAddPassenger(passenger); }
    @Override public @Nullable LivingEntity getControllingPassenger() { return this.isReviving() ? null : super.getControllingPassenger(); }
    @Override public boolean isClientAuthoritative() { return !this.isReviving() && !this.isZombieDiving() && super.isClientAuthoritative(); }

    @Override
    public void travel(Vec3 input) {
        if (this.isReviving() || this.isZombieDiving()) { this.move(MoverType.SELF, this.getDeltaMovement()); return; }
        super.travel(input);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.getTarget() instanceof Player) { this.clearCombatTarget(false); }
        if (this.isReviving()) { this.getNavigation().stop(); this.setDeltaMovement(Vec3.ZERO); return; }
        super.customServerAiStep(level);
        if (!this.isTame() && !this.isBaby() && this.tickCount % 20 == 0) { this.followLure(level); }
    }

    private static boolean lure(Player player) { return player.getMainHandItem().is(Items.ROTTEN_FLESH) || player.getOffhandItem().is(Items.ROTTEN_FLESH); }

    private void followLure(ServerLevel level) {
        if (this.getTarget() != null || this.isVehicle()) { return; }
        level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(20), player -> player.isAlive() && !player.isCreative()
                && !player.isSpectator() && lure(player) && this.line(level, this.getEyePosition(), player.getEyePosition()))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(player -> {
                    if (this.distanceToSqr(player) > 9) { this.flyToAssignment(player.position().add(0, 2, 0), 0.8D); }
                });
    }

    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) { return !(target instanceof Player); }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && target instanceof Enemy
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24 * 24;
    }

    public boolean isValidZombieTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level() && target != this
                && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && this.isSpeciesCombatTarget(target)
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.05D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public boolean hurtInfectionTarget(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
        if (this.isBaby() || !this.isAlive() || this.isReviving() || !this.isValidZombieTarget(target)) { return false; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), damage) : target.hurtServer(level, source, damage);
        if (hit && target.getHealth() + target.getAbsorptionAmount() < previous) { this.progressTick = this.tickCount; return true; }
        return false;
    }

    public void infect(LivingEntity target, int duration) {
        if (this.isValidZombieTarget(target)) { target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0), this); }
    }

    public boolean launchSpit(ServerLevel level, Vec3 direction) {
        boolean ridden = this.getControllingPassenger() instanceof Player;
        int interval = ridden ? this.getRiddenProjectileAbilityCooldownTicks() : SPIT_COOLDOWN_TICKS;
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.isReviving() || this.isZombieDiving() || (!ridden && this.recoveryTicks > 0)
                || this.spitCooldown > SPIT_COOLDOWN_TICKS - interval || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 0.01D) { return false; }
        Vec3 origin = this.getEyePosition().add(direction.normalize().scale(0.6D));
        if (!this.line(level, this.getEyePosition(), origin)) { return false; }
        var projectile = PhantomismContent.INFECTED_SPIT.get().create(level, EntitySpawnReason.TRIGGERED);
        projectile.setOwner(this); projectile.setPos(origin); projectile.shoot(direction.x, direction.y, direction.z, 1.15F, 0.25F);
        if (!level.addFreshEntity(projectile)) { return false; }
        this.spitCooldown = SPIT_COOLDOWN_TICKS; this.recoveryTicks = ridden ? 0 : 12; this.action("spit");
        PhantomVisualEffects.action(level, this, net.ronm19.phantomism.animation.PhantomAnimationAction.PROJECTILE);
        this.playSound(SoundEvents.ZOMBIE_INFECT, 0.5F, 1.35F); return true;
    }

    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.launchSpit(level, rider.getViewVector(1)); }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !this.isValidZombieTarget(target) || this.isBaby() || !this.isAlive()
                || this.isReviving() || this.isZombieDiving() || this.recoveryTicks > 0 || !this.closeEnough(level, target)) { return false; }
        boolean bite = this.biteCooldown == 0;
        if (bite) { this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(BITE_BONUS, 2, AttributeModifier.Operation.ADD_VALUE)); }
        boolean hit;
        try { hit = super.doHurtTarget(level, target); }
        finally { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(BITE_BONUS); }
        if (hit) {
            this.progressTick = this.tickCount;
            if (bite) { this.biteCooldown = 180; this.infect(target, 80); this.action("bite"); }
        }
        return hit;
    }

    private boolean closeEnough(ServerLevel level, LivingEntity target) {
        return this.distanceToSqr(target) <= 9 && this.getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox())
                && this.line(level, this.getEyePosition(), target.getEyePosition());
    }

    public boolean beginDive(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.isVehicle() || this.isPassenger() || this.isReviving()
                || this.isZombieDiving() || this.diveCooldown > 0 || this.recoveryTicks > 0 || this.getOrder() == Order.STAY
                || this.getOrder() == Order.RETURN_HOME || !this.isValidZombieTarget(target) || this.distanceToSqr(target) > 18 * 18
                || this.distanceToSqr(target) < 9 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        this.setCombatTarget(target); this.diveCooldown = 280; this.windupTicks = 16; this.diveTicks = 36;
        this.entityData.set(DIVING, true); this.action("dive_windup"); this.setDeltaMovement(Vec3.ZERO); return true;
    }

    private void tickDive(ServerLevel level, LivingEntity target) {
        this.getNavigation().stop();
        if (this.windupTicks > 0) { this.windupTicks--; this.setDeltaMovement(Vec3.ZERO); return; }
        Vec3 delta = target.position().add(0, target.getBbHeight() * 0.45D, 0).subtract(this.position());
        Vec3 step = delta.lengthSqr() > 0.75D * 0.75D ? delta.normalize().scale(0.75D) : delta;
        AABB swept = this.getBoundingBox().expandTowards(step).inflate(0.03D);
        if (--this.diveTicks <= 0 || !this.isLoadedFlightBox(swept) || !level.noBlockCollision(this, swept) || level.containsAnyLiquid(swept)) {
            this.cancelDive(); this.recoveryTicks = 22; return;
        }
        if (this.distanceToSqr(target) <= 9 && swept.intersects(target.getBoundingBox()) && this.line(level, this.getEyePosition(), target.getEyePosition())) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4;
            if (this.hurtInfectionTarget(level, target, this.damageSources().mobAttack(this), damage)) {
                Vec3 away = target.position().subtract(this.position()); target.knockback(0.9D, -away.x, -away.z, this.damageSources().mobAttack(this), damage);
                target.push(0, 0.18D, 0); target.syncVelocity = true; this.action("dive_impact");
                PhantomVisualEffects.impact(level, PhantomVfxTheme.ZOMBIE, target.position().add(0, 0.5D, 0));
                PhantomVisualEffects.shockwave(level, PhantomVfxTheme.ZOMBIE, target.position(), 2.8F);
            }
            this.cancelDive(); this.recoveryTicks = 22; return;
        }
        this.setDeltaMovement(step); this.syncVelocity = true;
        if (this.tickCount % 4 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.ZOMBIE, this.position()); }
    }

    @Override
    protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (this.getOrder() == Order.STAY || this.getOrder() == Order.RETURN_HOME || !this.isValidZombieTarget(target)) { this.clearCombatTarget(true); return true; }
        if (this.isReviving()) { return true; }
        if (this.isZombieDiving()) { this.tickDive(level, target); return true; }
        if (this.recoveryTicks > 0) { this.getNavigation().stop(); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuitTarget)) {
            this.pursuitTarget = target.getUUID(); this.pursuitStarted = this.tickCount; this.progressTick = this.tickCount; this.closestDistance = distance;
        }
        if (distance < this.closestDistance - 2) { this.closestDistance = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.pursuitStarted > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        if (distance > 36 && distance <= InfectedSpitProjectile.MAX_TRAVEL_DISTANCE * InfectedSpitProjectile.MAX_TRAVEL_DISTANCE && this.line(level, this.getEyePosition(), target.getEyePosition())
                && this.launchSpit(level, target.getEyePosition().subtract(this.getEyePosition()))) { return true; }
        if (distance > 16 && this.beginDive(level, target)) { return true; }
        if (this.closeEnough(level, target)) { return false; }
        this.flyToAssignment(target.position().add(0, 0.8D, 0), this.gripTicks > 0 ? 1.1D : 0.9D); return true;
    }

    public boolean rottenResilience(ServerLevel level) {
        if (this.level() != level || !this.isAlive() || this.isBaby() || this.isReviving() || this.resilienceCooldown > 0
                || this.getHealth() >= this.getMaxHealth() * 0.35F) { return false; }
        this.resilienceCooldown = 900; this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 1)); this.action("resilience");
        PhantomVisualEffects.action(level, this, net.ronm19.phantomism.animation.PhantomAnimationAction.SHIELD); return true;
    }

    public boolean tryRevive(ServerLevel level, DamageSource source) {
        if (this.level() != level || this.isRemoved() || this.getHealth() > 0 || !this.hasRevivalAvailable()
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) { return false; }
        this.entityData.set(REVIVAL_USED, true); this.setHealth(this.getMaxHealth() * 0.5F); this.deathTime = 0;
        this.clearCombatTarget(false); this.revivalTicks = 40; this.entityData.set(REVIVING, true);
        this.setDeltaMovement(Vec3.ZERO); this.syncVelocity = true; this.resetFallDistance();
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 1)); this.action("rise");
        PhantomVisualEffects.undeadRevival(level, this.getBoundingBox().getCenter()); this.playSound(SoundEvents.ZOMBIE_AMBIENT, 1.0F, 0.55F); return true;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.getHealth() <= 0) { this.revivalTicks = 0; this.entityData.set(REVIVING, false); this.cancelDive(); }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float previous = this.getHealth(); boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && !this.isReviving() && this.getHealth() < previous) { this.gripTicks = 20; this.rottenResilience(level); }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved() || !this.isAlive()) { return; }
        this.spitCooldown = Math.max(0, this.spitCooldown - 1); this.biteCooldown = Math.max(0, this.biteCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1); this.resilienceCooldown = Math.max(0, this.resilienceCooldown - 1);
        this.recoveryTicks = Math.max(0, this.recoveryTicks - 1); this.gripTicks = Math.max(0, this.gripTicks - 1);
        if (this.revivalTicks > 0) {
            this.revivalTicks--; this.setDeltaMovement(Vec3.ZERO);
            if (this.revivalTicks % 12 == 0) { PhantomVisualEffects.aura(level, PhantomVfxTheme.ZOMBIE, this.getBoundingBox().getCenter()); }
            if (this.revivalTicks == 0) { this.entityData.set(REVIVING, false); }
        }
        if (this.isZombieDiving() && (!this.isValidZombieTarget(this.getTarget()) || this.isVehicle() || this.getOrder() == Order.STAY || this.getOrder() == Order.RETURN_HOME)) { this.cancelDive(); }
        this.rottenResilience(level);
        if (!this.isBaby() && this.tickCount % 20 == 0) { ZombiePresence.pulse(this, level); }
        if (!this.isBaby() && !this.isReviving() && this.tickCount % 60 == 0) { PhantomVisualEffects.undeadMotes(level, this.getBoundingBox().getCenter()); }
    }

    private void cancelDive() { this.windupTicks = 0; this.diveTicks = 0; this.entityData.set(DIVING, false); this.setDeltaMovement(Vec3.ZERO); }

    @Override
    public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation); this.cancelDive(); this.pursuitTarget = null; this.closestDistance = Double.MAX_VALUE;
    }

    @Override
    public void setOrder(Order order) {
        super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); }
    }

    @Override
    public void remove(RemovalReason reason) { ZombiePresence.release(this); super.remove(reason); }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putBoolean("ZombieRevivalConsumed", !this.hasRevivalAvailable());
        output.putInt("ZombieRevivalRecovery", this.revivalTicks); output.putInt("ZombieSpitCooldown", this.spitCooldown);
        output.putInt("ZombieBiteCooldown", this.biteCooldown); output.putInt("ZombieDiveCooldown", this.diveCooldown);
        output.putInt("ZombieResilienceCooldown", this.resilienceCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(REVIVAL_USED, !this.hasRevivalAvailable() || input.getBooleanOr("ZombieRevivalConsumed", false));
        this.revivalTicks = Math.clamp(input.getIntOr("ZombieRevivalRecovery", 0), 0, 40);
        this.entityData.set(REVIVING, this.revivalTicks > 0 && this.isAlive() && !this.hasRevivalAvailable());
        this.spitCooldown = Math.clamp(input.getIntOr("ZombieSpitCooldown", 0), 0, SPIT_COOLDOWN_TICKS);
        this.biteCooldown = Math.clamp(input.getIntOr("ZombieBiteCooldown", 0), 0, 180);
        this.diveCooldown = Math.clamp(input.getIntOr("ZombieDiveCooldown", 0), 0, 280);
        this.resilienceCooldown = Math.clamp(input.getIntOr("ZombieResilienceCooldown", 0), 0, 900); this.cancelDive();
    }
}
