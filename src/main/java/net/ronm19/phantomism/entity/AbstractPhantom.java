package net.ronm19.phantomism.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.animation.PhantomAnimationMotion;
import net.ronm19.phantomism.animation.PhantomAnimations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;

public abstract class AbstractPhantom extends TamableAnimal implements GeoEntity {
    private static final EntityDataAccessor<Byte> DATA_ANIMATION_FLAGS = SynchedEntityData.defineId(AbstractPhantom.class, EntityDataSerializers.BYTE);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long animationPriorityUntil;
    private int animationPriority;
    private PhantomAnimationAction lastAnimationAction;
    private long lastAnimationTick = Long.MIN_VALUE;
    private static final EntityDataAccessor<Integer> DATA_ORDER = SynchedEntityData.defineId(AbstractPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TASK = SynchedEntityData.defineId(AbstractPhantom.class, EntityDataSerializers.INT);
    private static final double FLIGHT_OBSTACLE_LOOKAHEAD = 3.0D;
    private static final double RIDDEN_MAX_VERTICAL_SPEED = 0.12D;
    private static final int RIDDEN_PROJECTILE_ABILITY_COOLDOWN_TICKS = 8;
    private static final long CLEARED_TARGET_SUPPRESSION_TICKS = 200L;
    private static final long DISMOUNT_FALL_PROTECTION_TICKS = 600L;
    private static final Map<UUID, Long> DISMOUNT_FALL_PROTECTION = new ConcurrentHashMap<>();
    private static final Brain.Provider<AbstractPhantom> BRAIN_PROVIDER = Brain.provider(
            List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY),
            phantom -> List.of(
                    ActivityData.create(Activity.CORE, 0, ImmutableList.of(new PhantomTargetBehavior())),
                    ActivityData.create(Activity.IDLE, 0, ImmutableList.of(new PhantomIdleBehavior())),
                    ActivityData.create(
                            Activity.FIGHT,
                            0,
                            ImmutableList.of(new PhantomCombatBehavior()),
                            MemoryModuleType.ATTACK_TARGET
                    )
            )
    );

    private final Map<UUID, Long> suppressedTargets = new HashMap<>();
    private final PhantomAssignments assignments = new PhantomAssignments(this);
    private Order order = Order.FOLLOW;
    private boolean divingStrike;
    private int attackCooldown;
    private int circleTicks;
    private int diveTicks;
    private int recoveryTicks;
    private double orbitAngle;
    private int orbitDirection = 1;
    private int idleFlightTicks;
    private Vec3 idleFlightTarget;
    private Vec3 wanderCenter;
    private int ownerHurtByTimestamp;
    private int ownerHurtTargetTimestamp;
    private int riddenProjectileAbilityCooldownTicks;
    private Input clientRidingInput = Input.EMPTY;

    protected AbstractPhantom(EntityType<? extends AbstractPhantom> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new ForestAwareFlyingMoveControl(this);
        this.setNoGravity(true);
        if (!level.isClientSide()) {
            this.getConfiguredStats().apply(this);
            this.setHealth(this.getMaxHealth());
        }
    }

    protected Config.PhantomStats getConfiguredStats() {
        return Config.BLACK_PHANTOM_STATS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ORDER, Order.FOLLOW.ordinal());
        builder.define(DATA_TASK, Task.NONE.ordinal());
        builder.define(DATA_ANIMATION_FLAGS, (byte) 0);
    }

    public PhantomAssignments getAssignments() {
        return this.assignments;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        PhantomAnimations.register(controllers);
        this.registerSpeciesAnimationControllers(controllers);
    }

    protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar controllers) {}

    public Identifier getPhantomAnimationResource() {
        return PhantomAnimations.RESOURCE;
    }

    public RawAnimation getMovementAnimation(PhantomAnimationMotion motion) {
        return motion.animation();
    }

    public byte getPhantomAnimationFlags() {
        return this.entityData.get(DATA_ANIMATION_FLAGS);
    }

    protected void animate(PhantomAnimationAction action) {
        if (!this.level().isClientSide() && this.isAddedToLevel() && this.getId() >= 0
                && (this.lastAnimationAction != action || this.lastAnimationTick != this.level().getGameTime())
                && (this.level().getGameTime() >= this.animationPriorityUntil || action.priority() >= this.animationPriority)) {
            this.animationPriority = action.priority();
            this.lastAnimationAction = action;
            this.lastAnimationTick = this.level().getGameTime();
            this.animationPriorityUntil = this.level().getGameTime() + action.duration();
            this.triggerAnim("action", action.key());
            if (this.level() instanceof ServerLevel level) {
                net.ronm19.phantomism.vfx.PhantomVisualEffects.action(level, this, action);
            }
        }
    }

    public Task getTask() {
        return Task.values()[this.entityData.get(DATA_TASK)];
    }

    public void setTask(Task task) {
        net.ronm19.phantomism.entity.ai.RoyalTactics.ownerCommand(this, false);
        this.assignments.setTask(task);
        this.entityData.set(DATA_TASK, task.ordinal());
        this.resetIdleFlight();
    }

    public void setHome(GlobalPos home) {
        this.assignments.setHome(home);
        this.resetIdleFlight();
    }

    public void setGuardPosition(GlobalPos position) {
        this.assignments.setGuardPosition(position);
        this.clearCombatTarget(false);
        this.setOrder(Order.GUARD_POSITION);
    }

    public boolean returnHome() {
        if (this.assignments.homeCenter() == null) {
            return false;
        }
        this.clearCombatTarget(false);
        this.setOrder(Order.RETURN_HOME);
        return true;
    }

    public void flyToAssignment(Vec3 destination, double speed) {
        Vec3 offset = destination.subtract(this.position());
        Vec3 step = offset.lengthSqr() > 36.0D ? offset.normalize().scale(6.0D) : offset;
        for (int probe = 1; probe <= 8; probe++) {
            Vec3 point = this.position().add(step.scale(probe / 8.0D));
            AABB box = this.getBoundingBox().move(point.subtract(this.position())).inflate(0.25D);
            if (!this.isLoadedFlightBox(box)) {
                this.holdPosition();
                return;
            }
        }
        Vec3 waypoint = this.position().add(step);
        this.moveControl.setWantedPosition(waypoint.x, waypoint.y, waypoint.z, speed);
    }

    protected final boolean hasClearFlightCorridor(Vec3 offset) {
        int probes = Math.max(1, (int) Math.ceil(offset.length() / 0.75D));
        for (int probe = 1; probe <= probes; probe++) {
            Vec3 step = offset.scale((double) probe / probes);
            AABB box = this.getBoundingBox().move(step);
            if (!this.isLoadedFlightBox(box) || !this.level().noBlockCollision(this, box)) {
                return false;
            }
        }
        return true;
    }

    public boolean isLoadedFlightBox(AABB box) {
        if (box.minY < this.level().getMinY() || box.maxY > this.level().getMaxY() + 1.0D
                || !this.level().getWorldBorder().isWithinBounds(box)) {
            return false;
        }
        for (int chunkX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
                chunkX <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxX)); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
                    chunkZ <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ)); chunkZ++) {
                if (!this.level().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected Brain<AbstractPhantom> makeBrain(Brain.Packed packedBrain) {
        return BRAIN_PROVIDER.makeBrain(this, packedBrain);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<AbstractPhantom> getBrain() {
        return (Brain<AbstractPhantom>) super.getBrain();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public void tryToTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        if (this.level().isClientSide()
                || owner == null
                || !owner.isAlive()
                || owner.level() != this.level()
                || this.isPassenger()
                || this.isVehicle()) {
            return;
        }

        for (int attempt = 0; attempt < 32; attempt++) {
            int offsetX = this.getRandom().nextInt(9) - 4;
            int offsetY = this.getRandom().nextInt(5) - 1;
            int offsetZ = this.getRandom().nextInt(9) - 4;
            if (offsetX * offsetX + offsetZ * offsetZ < 4) {
                continue;
            }

            double destinationX = owner.getX() + offsetX;
            double destinationY = owner.getY() + offsetY;
            double destinationZ = owner.getZ() + offsetZ;
            AABB destinationBox = this.getBoundingBox().move(
                    destinationX - this.getX(),
                    destinationY - this.getY(),
                    destinationZ - this.getZ()
            );
            if (this.level().noCollision(this, destinationBox)) {
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                this.snapTo(destinationX, destinationY, destinationZ, this.getYRot(), this.getXRot());
                return;
            }
        }
    }

    @Override
    public boolean canTeleport(Level source, Level destination) {
        return super.canTeleport(source, destination)
                || net.ronm19.phantomism.entity.ai.PhantomDimensionTravel.allowsEndExit(this, source, destination);
    }

    @Override
    public void teleportToPortalDestination(ServerLevel source, net.minecraft.world.level.portal.TeleportTransition transition) {
        net.ronm19.phantomism.entity.ai.PhantomDimensionTravel.usePortal(this, source, transition);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.tickCount % 20 == 0) {
            this.getConfiguredStats().apply(this);
        }
        if (this.getControllingPassenger() instanceof Player) {
            this.resetIdleFlight();
            if (!this.preservesCombatTargetWhileRidden()) {
                this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                this.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                super.setTarget(null);
            }
            this.getNavigation().stop();
            return;
        }

        this.getBrain().tick(level, this);
        this.getBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
        this.evadeNearbyIgnitedCreeper(level);
        super.customServerAiStep(level);
    }

    protected boolean preservesCombatTargetWhileRidden() {
        return false;
    }

    protected int getFlightTurnRate() {
        return 10;
    }

    public final boolean activateRiddenProjectileAbility(Player rider) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || rider != this.getControllingPassenger()
                || !this.isOwnedBy(rider)
                || this.riddenProjectileAbilityCooldownTicks > 0
                || !this.performRiddenProjectileAbility(serverLevel, rider)) {
            return false;
        }

        this.riddenProjectileAbilityCooldownTicks = Math.max(1, this.getRiddenProjectileAbilityCooldownTicks());
        return true;
    }

    /** Override only for variants with a different mounted shot interval. */
    protected int getRiddenProjectileAbilityCooldownTicks() {
        return RIDDEN_PROJECTILE_ABILITY_COOLDOWN_TICKS;
    }

    protected boolean performRiddenProjectileAbility(ServerLevel serverLevel, Player rider) {
        return false;
    }

    @Override
    public LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        return other instanceof AbstractPhantom
                || other instanceof OwnableEntity companion && this.getOwnerReference() != null
                        && this.getOwnerReference().equals(companion.getOwnerReference())
                || super.considersEntityAsAlly(other);
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && !this.isSpeciesCombatTarget(target)) {
            target = null;
        }
        super.setTarget(target);
        if (this.brain != null) {
            if (target == null) {
                this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            } else {
                this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(Items.PHANTOM_MEMBRANE)) {
            if (player.isShiftKeyDown()) {
                return super.mobInteract(player, hand);
            }

            if ((!this.isTame() || this.isOwnedBy(player)) && this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide()) {
                    this.heal(4.0F);
                    this.usePlayerItem(player, hand, itemStack);
                    this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
                    this.gameEvent(GameEvent.EAT);
                }
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.SUCCESS;
        }

        if (player.getItemInHand(hand).isEmpty()
                && this.isTame()
                && this.isOwnedBy(player)
                && !this.isBaby()
                && !this.isVehicle()) {
            if (!this.level().isClientSide()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return !this.isBaby()
                && this.getPassengers().isEmpty()
                && passenger instanceof Player player
                && this.isTame()
                && this.isOwnedBy(player);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return !this.isBaby()
                && this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player)
                ? player
                : null;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return super.getPassengerRidingPosition(passenger).add(0.0D, -0.5D, 0.0D);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.clientRidingInput = Input.EMPTY;
        if (passenger instanceof Player rider) {
            rider.resetFallDistance();
            DISMOUNT_FALL_PROTECTION.put(
                    rider.getUUID(),
                    rider.level().getGameTime() + DISMOUNT_FALL_PROTECTION_TICKS
            );
        }
    }

    public static void tickDismountFallProtection(Player player) {
        Long expiresAt = DISMOUNT_FALL_PROTECTION.get(player.getUUID());
        if (expiresAt == null) {
            return;
        }
        if (player.onGround() || player.level().getGameTime() >= expiresAt) {
            DISMOUNT_FALL_PROTECTION.remove(player.getUUID());
        } else {
            player.resetFallDistance();
        }
    }

    public static boolean isDismountFallProtected(LivingEntity entity) {
        Long expiresAt = DISMOUNT_FALL_PROTECTION.get(entity.getUUID());
        if (expiresAt == null) {
            return false;
        }
        if (entity.level().getGameTime() >= expiresAt) {
            if (!entity.level().isClientSide()) {
                DISMOUNT_FALL_PROTECTION.remove(entity.getUUID(), expiresAt);
            }
            return false;
        }
        return true;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.getControllingPassenger() instanceof Player rider) {
            this.setYRot(rider.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(0.0F);
            this.xRotO = 0.0F;
            Input ridingInput = rider instanceof ServerPlayer serverPlayer
                    ? serverPlayer.getLastClientInput()
                    : this.clientRidingInput;
            double verticalInput = this.getRiddenVerticalInput(rider, ridingInput);
            if (!ridingInput.sprint()
                    && verticalInput >= 0.0D
                    && this.horizontalCollision
                    && rider.zza > 0.0F
                    && this.level().noCollision(this, this.getBoundingBox().move(0.0D, 0.5D, 0.0D))) {
                verticalInput = 1.0D;
            }
            Vec3 movement = this.getDeltaMovement();
            double maximumVerticalSpeed = this.getRiddenMaximumVerticalSpeed();
            double verticalSpeed = verticalInput == 0.0D ? 0.0D
                    : Mth.lerp(0.35D, movement.y, verticalInput * maximumVerticalSpeed);
            this.setDeltaMovement(
                    movement.x,
                    Mth.clamp(verticalSpeed, -maximumVerticalSpeed, maximumVerticalSpeed),
                    movement.z
            );
            this.travelFlying(new Vec3(rider.xxa, 0.0D, rider.zza),
                    (float) this.getRiddenHorizontalAcceleration());
            return;
        }

        super.travel(input);
    }

    public boolean isUltimateAnimationActive() { return this.lastAnimationAction == PhantomAnimationAction.ULTIMATE && this.level().getGameTime() < this.animationPriorityUntil; }

    protected double getRiddenHorizontalAcceleration() { return 0.5D * this.getAttributeValue(Attributes.FLYING_SPEED); }

    protected double getRiddenVerticalInput(Player rider, Input input) {
        return (input.jump() ? 1.0D : 0.0D) - (input.sprint() ? 1.0D : 0.0D);
    }

    protected double getRiddenMaximumVerticalSpeed() {
        return RIDDEN_MAX_VERTICAL_SPEED;
    }

    protected double getFlightAltitudeOffset(double offset) {
        return offset;
    }

    protected boolean isSuitableIdleFlightPosition(Vec3 destination, AABB clearance) {
        return !this.level().containsAnyLiquid(clearance);
    }

    public final void updateClientRidingInput(Input input) {
        if (this.level().isClientSide()) {
            this.clientRidingInput = input;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            net.ronm19.phantomism.entity.ai.PhantomDuels.tick(this);
            this.entityData.set(DATA_ANIMATION_FLAGS, (byte) ((this.diveTicks > 0 ? 1 : 0) | (this.isInLove() ? 2 : 0)));
            // Babies must never carry passengers, including riders from an older save.
            if (this.isBaby() && this.isVehicle()) {
                this.ejectPassengers();
            }
            this.riddenProjectileAbilityCooldownTicks = Math.max(0, this.riddenProjectileAbilityCooldownTicks - 1);
            return;
        }

        float flap = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * Mth.DEG_TO_RAD + Mth.PI);
        float nextFlap = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * Mth.DEG_TO_RAD + Mth.PI);
        if (flap > 0.0F && nextFlap <= 0.0F) {
            this.level().playLocalSound(
                    this,
                    SoundEvents.PHANTOM_FLAP,
                    this.getSoundSource(),
                    0.95F + this.random.nextFloat() * 0.05F,
                    0.95F + this.random.nextFloat() * 0.05F
            );
        }
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        this.assignments.dropCargo(level);
    }

    @Override
    public boolean doHurtTarget(ServerLevel serverLevel, Entity target) {
        if (target instanceof AbstractPhantom || this.isAlliedTo(target)) {
            return false;
        }

        boolean hit = super.doHurtTarget(serverLevel, target);
        if (hit) {
            this.playSound(SoundEvents.PHANTOM_BITE);
            this.animate(PhantomAnimationAction.BITE);
            net.ronm19.phantomism.vfx.PhantomVisualEffects.impact(serverLevel,
                    net.ronm19.phantomism.vfx.PhantomVfxTheme.of(this), target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            this.animate(PhantomAnimationAction.HURT);
        }
        return hurt;
    }

    @Override
    public boolean isOrderedToSit() {
        return false;
    }

    @Override
    public void setOrderedToSit(boolean orderedToSit) {
    }

    public Order getOrder() {
        return Order.values()[this.entityData.get(DATA_ORDER)];
    }

    public void setOrder(Order order) {
        net.ronm19.phantomism.entity.ai.RoyalTactics.ownerCommand(this, order == Order.FOLLOW);
        this.order = order;
        this.entityData.set(DATA_ORDER, order.ordinal());
        this.assignments.movementChanged();
        this.resetIdleFlight();
        if (order == Order.WANDER) {
            this.wanderCenter = this.position().add(0.0D, this.getFlightAltitudeOffset(2.0D), 0.0D);
        }
        if (order == Order.STAY) {
            this.getNavigation().stop();
        }
    }

    public void toggleOrder() {
        this.setOrder(this.order == Order.FOLLOW ? Order.STAY : Order.FOLLOW);
    }

    public void setCombatTarget(LivingEntity target) {
        if (target instanceof AbstractPhantom || !target.isAlive() || this.isAlliedTo(target) || !this.isSpeciesCombatTarget(target)) {
            return;
        }

        if (!net.ronm19.phantomism.entity.ai.PhantomDuels.begin(this, target)) { return; }
        net.ronm19.phantomism.entity.ai.RoyalTactics.ownerCommand(this, false);
        this.assignments.stopAreaCombat();
        this.suppressedTargets.remove(target.getUUID());
        this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        super.setTarget(target);
        this.attackCooldown = 0;
        this.diveTicks = 0;
        this.recoveryTicks = 0;
        this.circleTicks = 0;
        this.orbitAngle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        this.orbitDirection = this.getRandom().nextBoolean() ? 1 : -1;
    }

    public void clearCombatTarget(boolean suppressRetaliation) {
        if (suppressRetaliation) { net.ronm19.phantomism.entity.ai.RoyalTactics.ownerCommand(this, false); }
        if (suppressRetaliation) { net.ronm19.phantomism.entity.ai.PhantomDuels.end(this); }
        this.assignments.stopAreaCombat();
        this.assignments.resetWork();
        LivingEntity currentTarget = this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(this.getTarget());
        if (suppressRetaliation) {
            if (currentTarget != null) {
                this.suppressIncomingAggroFrom(currentTarget);
            }
            for (Mob mob : this.level().getEntitiesOfClass(
                    Mob.class,
                    this.getBoundingBox().inflate(64.0D),
                    mob -> mob.getTarget() == this
            )) {
                this.suppressIncomingAggroFrom(mob);
            }
        }

        this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        this.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        super.setTarget(null);
        this.attackCooldown = 0;
        this.diveTicks = 0;
        this.recoveryTicks = 0;
        this.getNavigation().stop();
        this.moveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
    }

    public boolean isTargetSuppressed(LivingEntity target) {
        Long expiresAt = this.suppressedTargets.get(target.getUUID());
        if (expiresAt == null) {
            return false;
        }
        if (this.level().getGameTime() >= expiresAt) {
            this.suppressedTargets.remove(target.getUUID());
            return false;
        }
        return true;
    }

    private void suppressIncomingAggroFrom(LivingEntity entity) {
        this.suppressedTargets.put(entity.getUUID(), this.level().getGameTime() + CLEARED_TARGET_SUPPRESSION_TICKS);
        if (entity instanceof Mob mob && mob.getTarget() == this) {
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel serverLevel) {
        return false;
    }

    protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return true;
    }

    protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target);
    }

    protected int getMeleeAttackCooldownTicks() {
        return 45;
    }

    protected int getAttackRecoveryTicks() {
        return 18;
    }

    protected boolean prefersRangedCombat() {
        return false;
    }

    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        return false;
    }

    protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        return false;
    }

    protected final boolean isDivingStrike() {
        return this.divingStrike;
    }

    protected final boolean isDiveInProgress() {
        return this.diveTicks > 0;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(Items.PHANTOM_MEMBRANE);
    }

    @Override
    public boolean canMate(Animal partner) {
        return partner instanceof AbstractPhantom phantom
                && phantom != this
                && this.isInLove()
                && phantom.isInLove();
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Entity entity = otherParent instanceof AbstractPhantom phantom && this.getRandom().nextBoolean()
                ? phantom.getType().create(level, EntitySpawnReason.BREEDING)
                : this.getType().create(level, EntitySpawnReason.BREEDING);
        if (!(entity instanceof AbstractPhantom offspring)) {
            return null;
        }

        AbstractPhantom ownerParent = this.isTame()
                ? this
                : otherParent instanceof AbstractPhantom phantom && phantom.isTame() ? phantom : null;
        if (ownerParent != null) {
            offspring.setOwnerReference(ownerParent.getOwnerReference());
            offspring.setTame(true, true);
        }
        return offspring;
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("PhantomismOrder", this.order.name());
        this.assignments.save(output);
        if (this.wanderCenter != null) {
            output.store("PhantomismWanderCenter", Vec3.CODEC, this.wanderCenter);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String savedOrder = input.read("PhantomismOrder", Codec.STRING).orElse(Order.FOLLOW.name());
        try {
            this.order = Order.valueOf(savedOrder);
        } catch (IllegalArgumentException exception) {
            this.order = Order.FOLLOW;
        }
        this.wanderCenter = input.read("PhantomismWanderCenter", Vec3.CODEC).orElse(null);
        this.assignments.load(input);
        this.entityData.set(DATA_ORDER, this.order.ordinal());
        this.entityData.set(DATA_TASK, this.assignments.task().ordinal());
        this.resetIdleFlight();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    private LivingEntity findBrainTarget(ServerLevel level) {
        LivingEntity currentTarget = this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (currentTarget != null && this.assignments.mustEndAreaCombat(currentTarget)) {
            this.clearCombatTarget(false);
            return null;
        }
        if (this.isValidTarget(currentTarget) && !this.isTargetSuppressed(currentTarget)) {
            return currentTarget;
        }

        if (currentTarget != null) {
            this.assignments.stopAreaCombat();
            this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            super.setTarget(null);
        }

        LivingEntity attacker = this.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (this.isValidTarget(attacker) && !this.isTargetSuppressed(attacker)
                && this.assignments.permitsDefensivePursuit(attacker)) {
            return attacker;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            if (owner.getLastHurtByMobTimestamp() != this.ownerHurtByTimestamp) {
                this.ownerHurtByTimestamp = owner.getLastHurtByMobTimestamp();
                LivingEntity ownerAttacker = owner.getLastHurtByMob();
                if (this.isOwnerDefenceTarget(ownerAttacker, owner)
                        && this.assignments.permitsDefensivePursuit(ownerAttacker)) {
                    return ownerAttacker;
                }
            }
            if (owner.getLastHurtMobTimestamp() != this.ownerHurtTargetTimestamp) {
                this.ownerHurtTargetTimestamp = owner.getLastHurtMobTimestamp();
                LivingEntity ownerTarget = owner.getLastHurtMob();
                if (this.isOwnerDefenceTarget(ownerTarget, owner)) {
                    return ownerTarget;
                }
            }
        }

        LivingEntity areaTarget = this.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .orElse(List.of()).stream()
                .filter(this::isValidTarget)
                .filter(target -> !this.isTargetSuppressed(target))
                .filter(this.assignments::permitsAutomaticTarget)
                .min(java.util.Comparator.comparingDouble(this::getAutomaticTargetPriority)).orElse(null);
        if (areaTarget != null) {
            this.assignments.beginAreaCombat();
            return areaTarget;
        }

        return this.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .orElse(List.of())
                .stream()
                .filter(target -> target instanceof Enemy)
                .filter(this::isValidTarget)
                .filter(target -> !this.isTargetSuppressed(target))
                .filter(target -> this.shouldHuntAutomatically(target, level))
                .min(java.util.Comparator.comparingDouble(this::getAutomaticTargetPriority))
                .orElse(null);
    }

    private boolean isOwnerDefenceTarget(LivingEntity target, LivingEntity owner) {
        return this.isValidTarget(target)
                && !this.isTargetSuppressed(target)
                && this.wantsToAttack(target, owner);
    }

    public void defendFamily(LivingEntity attacker) {
        if (!this.isTame() || this.isBaby() || this.isVehicle() || this.isPassenger()
                || !this.isOwnerDefenceTarget(attacker, this.getOwner())
                || this.isValidTarget(this.getTarget())
                || !this.assignments.permitsDefensivePursuit(attacker)) {
            return;
        }
        this.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, attacker, 100L);
        this.setTarget(attacker);
        this.resetIdleFlight();
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && this.isSpeciesCombatTarget(target)
                && !net.ronm19.phantomism.entity.ai.PhantomDuels.excludesObserver(this, target)
                && !(target instanceof AbstractPhantom)
                && target != this.getOwner()
                && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null
                        && owned.getOwnerReference().equals(this.getOwnerReference()))
                && !this.isAlliedTo(target)
                && this.distanceToSqr(target) <= Math.pow(this.getAttributeValue(Attributes.FOLLOW_RANGE), 2.0D)
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private void tickIdleFlight(ServerLevel level) {
        if (this.order == Order.RETURN_HOME) {
            Vec3 home = this.assignments.homeFlightCenter(level);
            if (home == null) {
                this.holdPosition();
            } else if (this.position().distanceToSqr(home) <= 9.0D) {
                this.setOrder(Order.STAY);
                this.holdPosition();
            } else {
                this.flyToAssignment(home, 1.2D);
            }
            return;
        }
        if (this.order == Order.GUARD_POSITION) {
            Vec3 center = this.assignments.guardCenter();
            if (center == null) {
                this.holdPosition();
            } else if (this.position().distanceToSqr(center) > 100.0D) {
                this.flyToAssignment(center, 1.2D);
            } else {
                this.tickWandering(level, center, 6.0D, 0.7D);
            }
            return;
        }
        if (this.order == Order.WANDER) {
            if (this.wanderCenter == null) {
                this.wanderCenter = this.position().add(0.0D, this.getFlightAltitudeOffset(2.0D), 0.0D);
            }
            Vec3 home = this.assignments.homeCenter();
            if (home == null) {
                this.tickWandering(level, this.wanderCenter, 12.0D, 0.75D);
            } else {
                this.tickHomePatrol(level, home);
            }
            return;
        }
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner == null || !owner.isAlive()) {
                this.holdPosition();
                return;
            }

            double distance = this.distanceToSqr(owner);
            if (this.order == Order.STAY) {
                this.resetIdleFlight();
                this.holdPosition();
            } else if (this.order == Order.FOLLOW) {
                if (distance > 576.0D && this.tickCount % 20 == 0) {
                    this.tryToTeleportToOwner();
                }
                if (distance > 100.0D) {
                    this.resetIdleFlight();
                    this.moveControl.setWantedPosition(owner.getX(), owner.getY() + this.getFlightAltitudeOffset(3.0D), owner.getZ(), 1.8D);
                } else {
                    this.tickWandering(level, owner.position().add(0.0D, this.getFlightAltitudeOffset(3.0D), 0.0D), 4.0D, 0.65D);
                }
            } else if (distance > 36.0D) {
                this.resetIdleFlight();
                if (distance > 576.0D && this.tickCount % 20 == 0) {
                    this.tryToTeleportToOwner();
                }
                this.moveControl.setWantedPosition(owner.getX(), owner.getY() + this.getFlightAltitudeOffset(3.0D), owner.getZ(), 1.6D);
            } else {
                this.resetIdleFlight();
                this.orbitAngle += 0.035D;
                this.moveControl.setWantedPosition(
                        owner.getX() + Math.cos(this.orbitAngle) * 4.0D,
                        owner.getY() + this.getFlightAltitudeOffset(3.0D + Math.sin(this.orbitAngle * 2.0D)),
                        owner.getZ() + Math.sin(this.orbitAngle) * 4.0D,
                        0.65D
                );
            }
            return;
        }

        if (this.wanderCenter == null || this.position().distanceToSqr(this.wanderCenter) > 576.0D) {
            this.wanderCenter = this.position().add(0.0D, this.getFlightAltitudeOffset(2.0D), 0.0D);
        }
        this.tickWandering(level, this.wanderCenter, 12.0D, 0.75D);
    }

    private void tickHomePatrol(ServerLevel level, Vec3 home) {
        if (!this.assignments.isWithinHome(this.position(), 0.0D)
                || this.assignments.isReturningFromPatrolCombat()) {
            Vec3 destination = this.assignments.homeFlightCenter(level);
            this.resetIdleFlight();
            if (destination != null && this.position().distanceToSqr(destination) <= 9.0D) {
                this.assignments.finishPatrolReturn();
                this.holdPosition();
            } else {
                this.flyToAssignment(destination != null ? destination : new Vec3(home.x, this.getY(), home.z), 1.2D);
            }
            return;
        }
        if (this.idleFlightTarget != null && (this.position().distanceToSqr(this.idleFlightTarget) < 2.25D
                || --this.idleFlightTicks <= 0 || this.horizontalCollision
                || !this.assignments.isWithinHome(this.idleFlightTarget, 0.0D)
                || !this.assignments.safeFlightPosition(this.idleFlightTarget))) {
            this.idleFlightTarget = null;
            this.idleFlightTicks = this.horizontalCollision ? 0 : 10 + this.getRandom().nextInt(21);
        }
        if (this.idleFlightTarget == null) {
            if (this.idleFlightTicks > 0) {
                this.idleFlightTicks--;
                this.holdPosition();
                return;
            }
            for (int attempt = 0; attempt < 16 && this.idleFlightTarget == null; attempt++) {
                double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
                double distance = Math.min(12.0D, Config.PHANTOM_HOME_RADIUS.get())
                        * (0.35D + this.getRandom().nextDouble() * 0.65D);
                Vec3 candidate = this.assignments.patrolDestination(level,
                        this.getX() + Math.cos(angle) * distance, this.getZ() + Math.sin(angle) * distance);
                if (candidate == null) {
                    continue;
                }
                for (int rise = 0; rise <= 4; rise++) {
                    Vec3 destination = candidate.add(0.0D, rise, 0.0D);
                    if (this.assignments.isWithinHome(destination, 0.0D)
                            && this.assignments.safeFlightPosition(destination)
                            && this.hasClearFlightCorridor(destination.subtract(this.position()))) {
                        this.idleFlightTarget = destination;
                        break;
                    }
                }
            }
            this.idleFlightTicks = this.idleFlightTarget == null ? 20 : 120;
            if (this.idleFlightTarget == null) {
                this.holdPosition();
                return;
            }
        }
        this.moveControl.setWantedPosition(this.idleFlightTarget.x, this.idleFlightTarget.y, this.idleFlightTarget.z, 0.75D);
    }

    private void tickWandering(ServerLevel level, Vec3 center, double radius, double speed) {
        if (this.position().distanceToSqr(center) > (radius + 4.0D) * (radius + 4.0D)) {
            this.resetIdleFlight();
            this.flyToAssignment(center, speed);
            return;
        }

        if (this.idleFlightTarget != null
                && (this.position().distanceToSqr(this.idleFlightTarget) < 2.25D
                || --this.idleFlightTicks <= 0
                || this.idleFlightTarget.distanceToSqr(center) > (radius + 2.0D) * (radius + 2.0D)
                || this.horizontalCollision)) {
            this.idleFlightTarget = null;
            this.idleFlightTicks = this.horizontalCollision ? 0 : 10 + this.getRandom().nextInt(21);
        }

        if (this.idleFlightTarget == null) {
            if (this.idleFlightTicks > 0) {
                this.idleFlightTicks--;
                this.holdPosition();
                return;
            }
            this.idleFlightTarget = this.findIdleFlightTarget(level, center, radius);
            this.idleFlightTicks = this.idleFlightTarget == null ? 10 : 120;
            if (this.idleFlightTarget == null) {
                this.holdPosition();
                return;
            }
        }

        this.moveControl.setWantedPosition(
                this.idleFlightTarget.x, this.idleFlightTarget.y, this.idleFlightTarget.z, speed);
    }

    private Vec3 findIdleFlightTarget(ServerLevel level, Vec3 center, double radius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.35D + this.getRandom().nextDouble() * 0.65D);
            Vec3 candidate = center.add(Math.cos(angle) * distance,
                    this.getRandom().nextDouble() * 3.0D - 1.5D, Math.sin(angle) * distance);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(Mth.floor(candidate.x)),
                    SectionPos.blockToSectionCoord(Mth.floor(candidate.z)))) {
                continue;
            }
            for (int rise = 0; rise <= 4; rise++) {
                Vec3 destination = candidate.add(0.0D, rise, 0.0D);
                Vec3 offset = destination.subtract(this.position());
                AABB clearance = this.getBoundingBox().move(offset).inflate(0.2D).expandTowards(0.0D, -1.0D, 0.0D);
                if (offset.lengthSqr() >= 4.0D
                        && clearance.minY >= level.getMinY()
                        && clearance.maxY <= level.getMaxY() + 1.0D
                        && level.getWorldBorder().isWithinBounds(clearance)
                        && level.noCollision(this, clearance)
                        && this.isSuitableIdleFlightPosition(destination, clearance)
                        && this.hasClearFlightCorridor(offset)) {
                    return destination;
                }
            }
        }
        return null;
    }

    private void resetIdleFlight() {
        this.idleFlightTarget = null;
        this.idleFlightTicks = 0;
    }

    private boolean tickBreeding(ServerLevel level) {
        if (!this.isInLove()) {
            return false;
        }

        AbstractPhantom mate = null;
        double closestDistance = Double.MAX_VALUE;
        for (AbstractPhantom candidate : level.getEntitiesOfClass(
                AbstractPhantom.class,
                this.getBoundingBox().inflate(16.0D),
                AbstractPhantom::isInLove
        )) {
            if (candidate != this && this.canMate(candidate)) {
                double distance = this.distanceToSqr(candidate);
                if (distance < closestDistance) {
                    mate = candidate;
                    closestDistance = distance;
                }
            }
        }

        if (mate == null) {
            return false;
        }
        if (this.getId() > mate.getId()) {
            this.holdPosition();
            return true;
        }

        this.getLookControl().setLookAt(mate);
        if (closestDistance > 9.0D) {
            this.moveControl.setWantedPosition(mate.getX(), mate.getY(), mate.getZ(), 1.15D);
        } else {
            this.getNavigation().stop();
            this.spawnChildFromBreeding(level, mate);
        }
        return true;
    }

    private void tickCombat(ServerLevel level, LivingEntity target) {
        this.getLookControl().setLookAt(target);
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.prepareSpeciesCombatAction(level, target)) {
            return;
        }

        if (this.diveTicks > 0) {
            this.moveControl.setWantedPosition(target.getX(), target.getY(0.5D), target.getZ(), 1.75D);
            if (this.getBoundingBox().inflate(0.25D).intersects(target.getBoundingBox())) {
                this.divingStrike = this.getY() > target.getY() + 1.0D;
                try {
                    this.doHurtTarget(level, target);
                } finally {
                    this.divingStrike = false;
                }
                this.attackCooldown = this.getMeleeAttackCooldownTicks();
                this.diveTicks = 0;
                this.recoveryTicks = this.getAttackRecoveryTicks();
            } else if (--this.diveTicks == 0) {
                this.attackCooldown = Math.max(this.attackCooldown, 12);
                this.recoveryTicks = this.getAttackRecoveryTicks();
            }
            return;
        }

        if (target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0)) {
            this.evadeCreeper(creeper);
            return;
        }

        if (this.recoveryTicks > 0) {
            this.recoveryTicks--;
            this.moveToOrbitPoint(target, 8.0D, 7.0D, 1.25D);
            return;
        }

        if (this.circleTicks > 0 || this.attackCooldown > 0) {
            this.circleTicks = Math.max(0, this.circleTicks - 1);
            this.moveToOrbitPoint(target, 6.0D, 5.0D, 1.1D);
            return;
        }

        if (this.prefersRangedCombat() && this.distanceToSqr(target) > 9.0D) {
            this.moveToOrbitPoint(target, 8.0D, 4.0D, 1.1D);
            return;
        }

        if (this.prepareCombatApproach(level, target)) {
            return;
        }

        double horizontalDistance = Math.sqrt(this.distanceToSqr(target.getX(), this.getY(), target.getZ()));
        if (horizontalDistance <= 22.0D) {
            this.diveTicks = 30;
            this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 0.95F + this.getRandom().nextFloat() * 0.1F);
        } else {
            this.moveControl.setWantedPosition(
                    target.getX(),
                    target.getY(0.5D) + this.getFlightAltitudeOffset(4.0D),
                    target.getZ(),
                    1.6D
            );
        }
    }

    private void moveToOrbitPoint(LivingEntity target, double radius, double altitude, double speed) {
        this.orbitAngle += this.orbitDirection * 0.075D;
        double x = target.getX() + Math.cos(this.orbitAngle) * radius;
        double y = target.getY(0.5D) + this.getFlightAltitudeOffset(altitude + Math.sin(this.orbitAngle * 2.0D));
        double z = target.getZ() + Math.sin(this.orbitAngle) * radius;
        Vec3 royalOrbit = net.ronm19.phantomism.entity.ai.RoyalTactics.orbit(this, target, new Vec3(x, y, z), radius);
        x = royalOrbit.x; y = royalOrbit.y; z = royalOrbit.z;
        this.moveControl.setWantedPosition(x, y, z, speed);
    }

    private void evadeCreeper(Creeper creeper) {
        double awayX = this.getX() - creeper.getX();
        double awayZ = this.getZ() - creeper.getZ();
        double horizontalDistance = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (horizontalDistance < 0.01D) {
            awayX = 1.0D;
            awayZ = 0.0D;
            horizontalDistance = 1.0D;
        }

        this.moveControl.setWantedPosition(
                this.getX() + awayX / horizontalDistance * 8.0D,
                this.getY() + 3.0D,
                this.getZ() + awayZ / horizontalDistance * 8.0D,
                1.4D
        );
        this.getLookControl().setLookAt(creeper);
    }

    private void evadeNearbyIgnitedCreeper(ServerLevel level) {
        Creeper creeper = level.getEntitiesOfClass(
                        Creeper.class,
                        this.getBoundingBox().inflate(10.0D),
                        candidate -> candidate.isAlive() && (candidate.isIgnited() || candidate.getSwellDir() > 0)
                ).stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (creeper != null) {
            this.evadeCreeper(creeper);
        }
    }

    private void holdPosition() {
        this.getNavigation().stop();
        this.moveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
    }

    public enum Order {
        FOLLOW,
        STAY,
        GUARD,
        WANDER,
        GUARD_POSITION,
        RETURN_HOME
    }

    private static final class ForestAwareFlyingMoveControl extends FlyingMoveControl<AbstractPhantom> {
        private ForestAwareFlyingMoveControl(AbstractPhantom phantom) {
            super(phantom, phantom.getFlightTurnRate(), true);
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speedModifier) {
            Vec3 start = this.mob.position();
            Vec3 destination = new Vec3(x, y, z);
            Vec3 towardDestination = destination.subtract(start);
            double distance = towardDestination.length();
            if (speedModifier <= 0.0D || distance <= FLIGHT_OBSTACLE_LOOKAHEAD) {
                super.setWantedPosition(x, y, z, speedModifier);
                return;
            }

            Vec3 directOffset = towardDestination.scale(FLIGHT_OBSTACLE_LOOKAHEAD / distance);
            if (this.mob.hasClearFlightCorridor(directOffset)) {
                super.setWantedPosition(x, y, z, speedModifier);
                return;
            }

            Vec3 forward = new Vec3(towardDestination.x, 0.0D, towardDestination.z);
            if (forward.lengthSqr() < 1.0E-6D) {
                double yawRadians = Math.toRadians(this.mob.getYRot());
                forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
            } else {
                forward = forward.normalize();
            }
            Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
            double verticalTrend = Math.max(-0.5D, Math.min(0.5D, towardDestination.y / distance));
            Vec3[] detours = {
                    forward.scale(2.5D).add(0.0D, 2.0D, 0.0D),
                    forward.scale(2.75D).add(side.scale(2.0D)).add(0.0D, verticalTrend, 0.0D),
                    forward.scale(2.75D).subtract(side.scale(2.0D)).add(0.0D, verticalTrend, 0.0D),
                    forward.scale(2.0D).add(side.scale(1.5D)).add(0.0D, 2.0D, 0.0D),
                    forward.scale(2.0D).subtract(side.scale(1.5D)).add(0.0D, 2.0D, 0.0D)
            };

            Vec3 selectedDetour = null;
            double bestDistance = Double.MAX_VALUE;
            for (Vec3 detour : detours) {
                if (!this.mob.hasClearFlightCorridor(detour)) {
                    continue;
                }
                double remainingDistance = start.add(detour).distanceToSqr(destination);
                if (remainingDistance < bestDistance) {
                    selectedDetour = detour;
                    bestDistance = remainingDistance;
                }
            }

            if (selectedDetour == null) {
                super.setWantedPosition(x, y, z, speedModifier);
            } else {
                Vec3 waypoint = start.add(selectedDetour);
                super.setWantedPosition(waypoint.x, waypoint.y, waypoint.z, speedModifier);
            }
        }
    }

    private static final class PhantomTargetBehavior extends Behavior<AbstractPhantom> {
        private PhantomTargetBehavior() {
            super(Map.of(), 1);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            return true;
        }

        @Override
        protected void tick(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            LivingEntity target = phantom.findBrainTarget(level);
            if (target == null) {
                phantom.setTarget(null);
            } else if (target != phantom.getTarget()) {
                phantom.setTarget(target);
            }
        }
    }

    private static final class PhantomIdleBehavior extends Behavior<AbstractPhantom> {
        private PhantomIdleBehavior() {
            super(Map.of(), 1);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            return phantom.getBrain().isActive(Activity.IDLE);
        }

        @Override
        protected boolean timedOut(long timestamp) {
            return false;
        }

        @Override
        protected void stop(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            phantom.resetIdleFlight();
        }

        @Override
        protected void tick(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            if (!phantom.tickBreeding(level) && !phantom.assignments.tick(level)) {
                phantom.tickIdleFlight(level);
            }
        }
    }

    private static final class PhantomCombatBehavior extends Behavior<AbstractPhantom> {
        private PhantomCombatBehavior() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT), 1);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            return true;
        }

        @Override
        protected void tick(ServerLevel level, AbstractPhantom phantom, long timestamp) {
            LivingEntity target = phantom.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target == null || !phantom.isValidTarget(target) || phantom.isTargetSuppressed(target)) {
                phantom.clearCombatTarget(false);
                return;
            }
            phantom.tickCombat(level, target);
        }
    }
}
