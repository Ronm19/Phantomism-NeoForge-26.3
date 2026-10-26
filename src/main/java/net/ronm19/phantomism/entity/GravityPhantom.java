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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class GravityPhantom extends AbstractPhantom implements net.ronm19.phantomism.entity.ai.CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/gravity_phantom");
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(GravityPhantom.class, EntityDataSerializers.BYTE);
    private static final String[] CLIPS = {"cast", "crush", "lift", "throw", "lock", "field", "horizon", "bite"};
    private int wellCooldown;
    private int crushCooldown;
    private int liftCooldown;
    private int throwCooldown;
    private int lockCooldown;
    private int fieldCooldown;
    private int horizonCooldown;
    private int meleeCooldown;
    private int fieldTicks;
    private int wellTicks;
    private int horizonTicks;
    private int actionTicks;
    private int actionPriority;
    private int crushWindup;
    private int startedCombat;
    private int progressTick;
    private double closest = Double.MAX_VALUE;
    private UUID session = UUID.randomUUID();
    private @Nullable UUID crushing;
    private @Nullable UUID pursuit;
    private @Nullable Vec3 wellCenter;
    private @Nullable Vec3 horizonCenter;
    private static final java.util.Map<Projectile, Boolean> SLOWED = new java.util.WeakHashMap<>();

    public GravityPhantom(EntityType<? extends GravityPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 68).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.6D).add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FLYING_SPEED, 0.35D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<GravityPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.OVERWORLD && level.getBiome(position).is(ModTags.Biomes.GRAVITY_PHANTOM_SPAWNS)
                && position.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ())
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.GRAVITY_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override public void travel(Vec3 input) { if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * 0.09D)); } else { super.travel(input); } }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, (byte) 0); }
    private void state(int flag, boolean active) { int current = this.entityData.get(STATE); this.entityData.set(STATE, (byte) (active ? current | flag : current & ~flag)); }
    public boolean hasGravityField() { return (this.entityData.get(STATE) & 1) != 0; }
    public boolean hasEventHorizon() { return (this.entityData.get(STATE) & 2) != 0; }
    @Override public boolean isUltimateAnimationActive() { return this.hasEventHorizon() || super.isUltimateAnimationActive(); }
    public UUID session() { return this.session; }
    public boolean combatEnabled() { return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT); }
    public boolean controlsEnabled() { return this.combatEnabled() && (!this.isTame() || this.getOwner() != null && this.getOwner().isAlive() && this.getOwner().level() == this.level()); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        boolean hostile = target == this.getLastHurtByMob() || target instanceof Mob mob && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner());
        if ((target instanceof net.minecraft.world.entity.NeutralMob || target.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN) && !hostile) { return false; }
        return target instanceof Enemy && !(target instanceof Player) && !(target instanceof AbstractPhantom)
                && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null) && !this.isAlliedTo(target);
    }
    public boolean validTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level() && this.isSpeciesCombatTarget(target)
                && !(target instanceof Mob mob && net.ronm19.phantomism.entity.ai.VampireInfluence.isHypnotized(mob))
                && !this.isTargetSuppressed(target) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.02D)) && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return this.isTame() && this.getOrder() == Order.GUARD && this.getOwner() != null && target instanceof Enemy && this.getOwner().distanceToSqr(target) <= 24 * 24; }
    public boolean airborne(LivingEntity target) { return !target.onGround() && target.getY() > this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getBlockX(), target.getBlockZ()) + 1; }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return this.distanceToSqr(target) - (this.airborne(target) ? 600 : 0) + target.getHealth() * 0.1D; }
    public List<Mob> hostiles(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius), target -> this.validTarget(target)
                && target.position().distanceToSqr(center) <= radius * radius && this.getAssignments().permitsDefensivePursuit(target)
                && this.line(level, center.add(0, 0.6D, 0), target.getBoundingBox().getCenter()))
                .stream().sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList();
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!this.isTame() && stack.is(preferredTaming().item())) {
            if (!this.level().isClientSide()) { this.tame(player); this.clearCombatTarget(true); if (!player.isCreative()) { stack.shrink(1); } this.level().broadcastEntityEvent(this, (byte) 7); }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public float damage(ServerLevel level, LivingEntity target, float amount, DamageSource source) {
        if (!this.controlsEnabled() || !this.validTarget(target) || !this.getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player player
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(player), amount) : target.hurtServer(level, source, amount);
        float actual = hit ? Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
        if (actual > 0) { this.progressTick = this.tickCount; } return actual;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity enemy) || !this.validTarget(enemy) || this.meleeCooldown > 0 || !this.combatEnabled()
                || !this.getBoundingBox().inflate(1).intersects(enemy.getBoundingBox()) || !this.line(level, this.getEyePosition(), enemy.getEyePosition())) { return false; }
        boolean hit = super.doHurtTarget(level, target); this.meleeCooldown = 40; if (hit) { this.action("bite"); } return hit;
    }
    public boolean launchWell(ServerLevel level, Vec3 aim) {
        int interval = this.getControllingPassenger() instanceof Player ? 150 : 300;
        if (this.level() != level || !this.controlsEnabled() || this.wellCooldown > 300 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < 0.01D) { return false; }
        Vec3 direction = aim.normalize(); Vec3 muzzle = this.getEyePosition().add(direction.scale(0.7D));
        if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        GravityWellProjectile shot = PhantomismContent.GRAVITY_WELL.get().create(level, EntitySpawnReason.TRIGGERED);
        shot.setOwner(this); shot.configure(this.session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.2F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        this.wellCooldown = 300; this.action("cast"); PhantomVisualEffects.orbit(level, PhantomVfxTheme.GRAVITY, muzzle, 0.5F, 8); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 150; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.launchWell(level, rider.getViewVector(1)); }
    public boolean placeWell(ServerLevel level, Vec3 center) {
        if (!this.validCenter(level, center, 42, 6)) { return false; }
        this.wellCenter = center; this.wellTicks = 80; PhantomVisualEffects.implosion(level, PhantomVfxTheme.GRAVITY, center.add(0, 0.5D, 0), 3); return true;
    }
    private boolean validCenter(ServerLevel level, Vec3 center, double range, double radius) {
        return this.controlsEnabled() && Double.isFinite(center.lengthSqr()) && this.getEyePosition().distanceToSqr(center) <= range * range + 0.0001D
                && this.isLoadedFlightBox(new AABB(center, center).inflate(radius, 4, radius)) && level.getWorldBorder().isWithinBounds(new AABB(center, center).inflate(radius, 4, radius))
                && !GravityControl.dangerous(level, BlockPos.containing(center)) && level.noBlockCollision(this, new AABB(center, center).inflate(0.15D)) && this.line(level, this.getEyePosition(), center.add(0, 0.25D, 0));
    }
    public boolean gravityCrush(ServerLevel level, LivingEntity target) {
        if (this.crushCooldown > 0 || !this.controlsEnabled() || !this.validTarget(target) || this.distanceToSqr(target) > 24 * 24
                || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        this.crushing = target.getUUID(); this.crushWindup = 8; this.crushCooldown = 280; this.action("crush"); PhantomVisualEffects.distortion(level, PhantomVfxTheme.GRAVITY, target.getBoundingBox().getCenter(), 1); return true;
    }
    public boolean zeroLift(ServerLevel level, Mob target) {
        if (this.liftCooldown > 0 || !this.line(level, this.getEyePosition(), target.getEyePosition()) || !GravityControl.apply(this, target, GravityControl.Mode.LIFT, null)) { return false; }
        this.liftCooldown = 360; this.action("lift"); PhantomVisualEffects.orbit(level, PhantomVfxTheme.GRAVITY, target.getBoundingBox().getCenter(), 1, 10); return true;
    }
    public boolean orbitalThrow(ServerLevel level, Mob target) {
        if (this.throwCooldown > 0 || !this.controlsEnabled() || !this.validTarget(target) || this.distanceToSqr(target) > 20 * 20 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = this.wellCenter == null ? target.position().subtract(this.position()).multiply(1, 0, 1).normalize() : this.wellCenter.subtract(target.position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 0.1D) { return false; }
        Vec3 desired = target.position().add(direction.scale(this.wellCenter == null ? 6 : Math.min(6, target.position().distanceTo(this.wellCenter))));
        Vec3 landing = GravityControl.landing(this, target, desired);
        if (landing == null || !GravityControl.apply(this, target, GravityControl.Mode.THROW, landing)) { return false; }
        this.throwCooldown = 320; this.action("throw"); PhantomVisualEffects.orbit(level, PhantomVfxTheme.GRAVITY, target.getBoundingBox().getCenter(), 1.5F, 10); return true;
    }
    public boolean massLock(ServerLevel level, Mob target) {
        if (this.lockCooldown > 0 || !this.line(level, this.getEyePosition(), target.getEyePosition()) || !GravityControl.apply(this, target, GravityControl.Mode.LOCK, null)) { return false; }
        this.lockCooldown = 400; this.action("lock"); PhantomVisualEffects.distortion(level, PhantomVfxTheme.GRAVITY, target.getBoundingBox().getCenter(), 1.2F); return true;
    }
    private List<Projectile> incoming(ServerLevel level) {
        return level.getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(6), shot -> shot.getOwner() instanceof LivingEntity enemy && this.validTarget(enemy)
                && shot.getDeltaMovement().dot(this.getBoundingBox().getCenter().subtract(shot.position())) > 0
                && shot.getBoundingBox().expandTowards(shot.getDeltaMovement().scale(12)).intersects(this.getBoundingBox().inflate(1)))
                .stream().limit(8).toList();
    }
    public boolean gravityField(ServerLevel level) {
        if (!this.controlsEnabled() || this.fieldCooldown > 0 || this.incoming(level).isEmpty() && this.hostiles(level, this.position(), 8, 2).size() < 2
                && !(this.getLastHurtByMob() != null && this.validTarget(this.getLastHurtByMob()) && this.tickCount - this.getLastHurtByMobTimestamp() <= 60)) { return false; }
        this.fieldCooldown = 440; this.fieldTicks = 120; this.state(1, true); this.action("field"); PhantomVisualEffects.distortion(level, PhantomVfxTheme.GRAVITY, this.getBoundingBox().getCenter(), 2); return true;
    }
    public boolean eventHorizon(ServerLevel level, Vec3 center) {
        if (this.horizonCooldown > 0 || !this.validCenter(level, center, 24, 16)
                || this.hostiles(level, center, 16, 16).stream().filter(enemy -> enemy.getHealth() + enemy.getAbsorptionAmount() >= 6).limit(3).count() < 3) { return false; }
        this.horizonCooldown = 1600; this.horizonTicks = 200; this.horizonCenter = center; this.state(2, true); this.action("horizon");
        PhantomVisualEffects.gravityVortex(level, center, 4, true); this.playSound(SoundEvents.END_PORTAL_SPAWN, 0.6F, 1.2F); return true;
    }
    public boolean activateRiddenCrush(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider)) { return false; }
        Vec3 eye = rider.getEyePosition(), end = eye.add(rider.getViewVector(1).scale(24));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(0.5D).clip(eye, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && this.gravityCrush(level, target);
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.controlsEnabled() || !this.validTarget(target)) { this.clearCombatTarget(false); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuit)) { this.pursuit = target.getUUID(); this.startedCombat = this.tickCount; this.progressTick = this.tickCount; this.closest = distance; }
        if (distance < this.closest - 2) { this.closest = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.startedCombat > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        if (this.crushWindup > 0 || this.actionTicks > 8) { return true; }
        if (this.horizonCooldown == 0 && this.eventHorizon(level, target.position().add(0, 0.2D, 0))) { return true; }
        if (this.wellCooldown == 0 && distance <= 42 * 42 && (this.airborne(target) || this.hostiles(level, target.position(), 10, 2).size() >= 2)
                && this.launchWell(level, target.getBoundingBox().getCenter().subtract(this.getEyePosition()))) { return true; }
        if (target instanceof Mob mob && distance <= 24 * 24) {
            boolean caught = this.wellCenter != null && target.position().distanceToSqr(this.wellCenter) <= 36;
            boolean pressure = mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner();
            if (pressure && distance <= 10 * 10 && this.orbitalThrow(level, mob)) { return true; }
            if ((caught || target.getMaxHealth() >= 40 || this.airborne(target)) && this.gravityCrush(level, target)) { return true; }
            if ((this.airborne(target) || pressure && distance <= 12 * 12) && this.zeroLift(level, mob)) { return true; }
            if (target.getDeltaMovement().horizontalDistanceSqr() > 0.01D && this.massLock(level, mob)) { return true; }
            if (caught && target.distanceToSqr(this) < 100 && this.orbitalThrow(level, mob)) { return true; }
        }
        if (distance <= 9) { this.doHurtTarget(level, target); }
        else if (distance > 12 * 12 || this.meleeCooldown == 0) { this.flyToAssignment(target.position().add(0, 0.8D, 0), 0.8D); }
        return true;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isTame() && this.getTarget() == null && this.tickCount % 20 == 0 && this.combatEnabled()) {
            Player lure = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 16, entity -> entity instanceof Player player && !player.isSpectator() && (player.getMainHandItem().is(Items.HEAVY_CORE) || player.getOffhandItem().is(Items.HEAVY_CORE)));
            if (lure != null && this.distanceToSqr(lure) > 9 && this.line(level, this.getEyePosition(), lure.getEyePosition())) { this.flyToAssignment(lure.position().add(0, 1.6D, 0), 0.7D); }
        }
        if (this.isTame() && this.getOrder() == Order.GUARD && this.getTarget() == null && this.tickCount % 20 == 0) {
            this.hostiles(level, this.position(), 48, 12).stream().filter(this::airborne).filter(target -> this.shouldHuntAutomatically(target, level)).findFirst().ifPresent(this::setCombatTarget);
        }
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.wellCooldown = Math.max(0, this.wellCooldown - 1); this.crushCooldown = Math.max(0, this.crushCooldown - 1); this.liftCooldown = Math.max(0, this.liftCooldown - 1);
        this.throwCooldown = Math.max(0, this.throwCooldown - 1); this.lockCooldown = Math.max(0, this.lockCooldown - 1); this.fieldCooldown = Math.max(0, this.fieldCooldown - 1);
        this.horizonCooldown = Math.max(0, this.horizonCooldown - 1); this.meleeCooldown = Math.max(0, this.meleeCooldown - 1); this.actionTicks = Math.max(0, this.actionTicks - 1);
        if (!this.controlsEnabled()) { this.cancel(); return; }
        if (this.crushWindup > 0 && --this.crushWindup == 0 && this.crushing != null && level.getEntity(this.crushing) instanceof LivingEntity target) {
            if (this.distanceToSqr(target) <= 24 * 24 && this.line(level, this.getEyePosition(), target.getEyePosition()) && this.damage(level, target, 7, this.damageSources().mobAttack(this)) > 0) {
                if (target instanceof Mob mob && !GravityControl.movementImmune(mob)) { GravityControl.compress(this, mob, this.wellCenter != null && target.position().distanceToSqr(this.wellCenter) <= 36 ? 30 : 15); }
                PhantomVisualEffects.implosion(level, PhantomVfxTheme.GRAVITY, target.getBoundingBox().getCenter(), 1.6F);
            }
            this.crushing = null;
        }
        if (this.fieldTicks > 0) {
            if (--this.fieldTicks == 0) { this.state(1, false); }
            else { for (Projectile shot : this.incoming(level)) { if (!SLOWED.containsKey(shot)) { SLOWED.put(shot, true); shot.setDeltaMovement(shot.getDeltaMovement().scale(0.45D)); shot.needsSync = true; PhantomVisualEffects.distortion(level, PhantomVfxTheme.GRAVITY, shot.position(), 0.4F); } } }
        }
        if (this.tickCount % 4 == 0) {
            this.field(level, this.horizonCenter, this.horizonTicks, 16, true);
            this.field(level, this.wellCenter, this.wellTicks, 6, false);
            if (this.hasGravityField()) { PhantomVisualEffects.distortion(level, PhantomVfxTheme.GRAVITY, this.getBoundingBox().getCenter(), 1.8F); }
        }
        if (this.wellTicks > 0 && --this.wellTicks == 0) { this.wellCenter = null; }
        if (this.horizonTicks > 0 && --this.horizonTicks == 0) { this.horizonCenter = null; this.state(2, false); }
        if (this.tickCount % 10 == 0) { this.gravityField(level); this.mountedSupport(level); }
        if (this.getTarget() != null && this.tickCount % 12 == 0) { PhantomVisualEffects.orbit(level, PhantomVfxTheme.GRAVITY, this.getBoundingBox().getCenter(), 1.6F, 4); }
    }
    private void field(ServerLevel level, @Nullable Vec3 center, int ticks, double radius, boolean ultimate) {
        if (center == null || ticks <= 0) { return; }
        if (this.position().distanceToSqr(center) > 48 * 48 || !this.isLoadedFlightBox(new AABB(center, center).inflate(radius, 4, radius))) {
            if (ultimate) { this.horizonTicks = 0; this.horizonCenter = null; this.state(2, false); } else { this.wellTicks = 0; this.wellCenter = null; } return;
        }
        for (Mob target : this.hostiles(level, center, radius, ultimate ? 16 : 12)) {
            GravityControl.pull(this, target, center, ultimate ? 0.1D : 0.07D);
            if (ultimate && this.tickCount % 20 == 0) { this.damage(level, target, 1, this.damageSources().mobAttack(this)); }
        }
        if (this.tickCount % 8 == 0) { PhantomVisualEffects.gravityVortex(level, center, ultimate ? 4 : 2, ultimate); }
    }
    private void mountedSupport(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (!(this.getControllingPassenger() instanceof Player) || !this.validTarget(target) || this.distanceToSqr(target) > 24 * 24) { return; }
        if (this.eventHorizon(level, target.position().add(0, 0.2D, 0))) { return; }
        if (target instanceof Mob mob) {
            boolean pressure = mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner();
            if (pressure && this.distanceToSqr(mob) <= 100 && this.orbitalThrow(level, mob)) { return; }
            if ((this.airborne(mob) || pressure) && this.zeroLift(level, mob)) { return; }
            if (mob.getDeltaMovement().horizontalDistanceSqr() > 0.01D) { this.massLock(level, mob); }
        }
    }
    private void action(String name) {
        int priority = name.equals("horizon") ? 3 : name.equals("field") ? 2 : 1;
        if (this.level() instanceof ServerLevel && this.isAddedToLevel() && (this.actionTicks == 0 || priority >= this.actionPriority)) {
            this.actionTicks = name.equals("horizon") ? 36 : 16; this.actionPriority = priority; this.triggerAnim("gravity_action", name);
        }
    }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<GravityPhantom>("gravity_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.gravity." + clip)); }
        registrar.add(controller.additiveAnimations());
    }
    private void cancel() { GravityControl.release(this); this.session = UUID.randomUUID(); this.wellTicks = 0; this.horizonTicks = 0; this.fieldTicks = 0; this.crushWindup = 0; this.crushing = null; this.wellCenter = null; this.horizonCenter = null; this.state(1, false); this.state(2, false); }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); this.pursuit = null; if (suppress) { this.cancel(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void die(DamageSource source) { this.cancel(); super.die(source); }
    @Override public void remove(RemovalReason reason) { this.cancel(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("GravityWellCooldown", this.wellCooldown); output.putInt("GravityCrushCooldown", this.crushCooldown);
        output.putInt("GravityLiftCooldown", this.liftCooldown); output.putInt("GravityThrowCooldown", this.throwCooldown); output.putInt("GravityLockCooldown", this.lockCooldown);
        output.putInt("GravityFieldCooldown", this.fieldCooldown); output.putInt("GravityHorizonCooldown", this.horizonCooldown); output.putInt("GravityMeleeCooldown", this.meleeCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.cancel(); this.pursuit = null;
        this.wellCooldown = Math.clamp(input.getIntOr("GravityWellCooldown", 0), 0, 300); this.crushCooldown = Math.clamp(input.getIntOr("GravityCrushCooldown", 0), 0, 280);
        this.liftCooldown = Math.clamp(input.getIntOr("GravityLiftCooldown", 0), 0, 360); this.throwCooldown = Math.clamp(input.getIntOr("GravityThrowCooldown", 0), 0, 320);
        this.lockCooldown = Math.clamp(input.getIntOr("GravityLockCooldown", 0), 0, 400); this.fieldCooldown = Math.clamp(input.getIntOr("GravityFieldCooldown", 0), 0, 440);
        this.horizonCooldown = Math.clamp(input.getIntOr("GravityHorizonCooldown", 0), 0, 1600); this.meleeCooldown = Math.clamp(input.getIntOr("GravityMeleeCooldown", 0), 0, 40);
    }

    @Override public java.util.List<net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot> temporalCooldowns() { return java.util.List.of(
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("gravity_crush_cooldown", 280, () -> this.crushCooldown, value -> this.crushCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("gravity_lift_cooldown", 360, () -> this.liftCooldown, value -> this.liftCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("gravity_throw_cooldown", 320, () -> this.throwCooldown, value -> this.throwCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("gravity_lock_cooldown", 400, () -> this.lockCooldown, value -> this.lockCooldown = value)); }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.HEAVY_CORE, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "launch_well" -> this.wellCooldown;
            case "gravity_crush" -> this.crushCooldown;
            case "zero_lift" -> this.liftCooldown;
            case "orbital_throw" -> this.throwCooldown;
            case "mass_lock" -> this.lockCooldown;
            case "gravity_field" -> this.fieldCooldown;
            case "event_horizon" -> this.horizonCooldown;
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { this.wellCooldown = ticks; }
}
