package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.SpectralVfx;

public final class SpectralPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/spectral_phantom");
    public static final ResourceKey<DamageType> STRIKE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "spectral_strike"));
    private static final Identifier PLANE = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ethereal_plane");
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SpectralPhantom.class, EntityDataSerializers.BYTE);
    private static final int[] INTERVALS = {240, 200, 240, 240, 480, 1800};
    private static final String[] CLIPS = {"lance", "dive", "slip", "mark", "guard", "plane"};
    private final int[] cooldowns = new int[6];
    private final SpectralPassage passage = new SpectralPassage(this);
    private UUID session = UUID.randomUUID(), marked, diveTarget;
    private int meleeTicks, markTicks, diveTicks, guardTicks, planeTicks, veilTicks, actionTicks;
    private boolean sessionOpen;
    private List<LivingEntity> sensed = List.of();

    public SpectralPhantom(EntityType<? extends SpectralPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 56).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.ARMOR, 4)
                .add(Attributes.ARMOR_TOUGHNESS, 0).add(Attributes.KNOCKBACK_RESISTANCE, .2D).add(Attributes.MOVEMENT_SPEED, .25D)
                .add(Attributes.FLYING_SPEED, .525D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<SpectralPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        boolean habitat = level.getLevel().dimension() == Level.OVERWORLD && isNight(level.getLevel())
                || level.getLevel().dimension() == Level.NETHER && position.getY() < 117;
        return habitat && level.getBiome(position).is(ModTags.Biomes.SPECTRAL_PHANTOM_SPAWNS) && position.getY() > level.getMinY() + 3
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    public static boolean isNight(ServerLevel level) { long time = Math.floorMod(level.getOverworldClockTime(), 24000); return time >= 13000 && time < 23000; }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SPECTRAL_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 32; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, (byte) 0); }
    public SpectralPassage passage() { return passage; }
    @Override public boolean canAddPassenger(Entity passenger) { return !isPhasing() && super.canAddPassenger(passenger); }
    public UUID session() { return session; }
    public boolean isPhasing() { return (entityData.get(STATE) & 1) != 0; }
    public boolean inPlane() { return (entityData.get(STATE) & 2) != 0; }
    public boolean hasVeil() { return (entityData.get(STATE) & 4) != 0; }
    public void setPhasing(boolean active) { entityData.set(STATE, (byte) (active ? entityData.get(STATE) | 1 : entityData.get(STATE) & ~1)); }
    @Override public boolean isUltimateAnimationActive() { return inPlane() || super.isUltimateAnimationActive(); }
    public boolean movementEnabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == Task.NONE || getTask() == Task.HUNT) && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    public boolean combatEnabled() { return movementEnabled() && !isPhasing(); }
    private boolean provoked(LivingEntity target) {
        LivingEntity owner = getOwner();
        return getLastHurtByMob() == target && tickCount - getLastHurtByMobTimestamp() < 200
                || owner != null && (owner.getLastHurtByMob() == target && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200
                || owner.getLastHurtMob() == target && owner.tickCount - owner.getLastHurtMobTimestamp() < 200)
                || target instanceof Mob mob && (mob.getTarget() == this || owner != null && mob.getTarget() == owner);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target == null) { return false; }
        if (target instanceof Player player) { return !player.isCreative() && !player.isSpectator() && provoked(player); }
        if (target instanceof OwnableEntity owned && owned.getOwnerReference() != null) { return provoked(target) || PhantomDuels.pair(this, target); }
        return target instanceof Enemy && (!(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN || provoked(target));
    }
    public boolean validTarget(LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && !target.isInvulnerable() && target.level() == level()
                && target != getOwner() && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
                && !isAlliedTo(target) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target) && isSpeciesCombatTarget(target)
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob)) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return isTame() && validTarget(target) && (getOrder() == Order.GUARD || provoked(target)); }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target == null) { return; }
        if (target != getOwner() && !(target instanceof AbstractPhantom) && !isAlliedTo(target) && !PhantomDuels.begin(this, target)) { return; }
        super.setCombatTarget(target);
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) {
        return distanceToSqr(target) - (target.getUUID().equals(marked) ? 1500 : 0) - (provoked(target) ? 500 : 0) - (target instanceof RangedAttackMob ? 80 : 0);
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private void sense(ServerLevel level) {
        var memory = getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of());
        sensed = (memory.isEmpty() ? level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48), this::validTarget) : memory).stream()
                .filter(target -> validTarget(target) && distanceToSqr(target) <= 48 * 48).limit(96).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).toList();
    }
    private List<LivingEntity> threats(double radius, int limit) {
        return sensed.stream().filter(target -> validTarget(target) && distanceToSqr(target) <= radius * radius && getAssignments().permitsDefensivePursuit(target)
                && line(getEyePosition(), target.getEyePosition())).limit(limit).toList();
    }
    public float damage(ServerLevel level, LivingEntity target, DamageSource damage, float amount) {
        if (!combatEnabled() || !validTarget(target) || !getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        return target.hurtServer(level, damage, amount) ? Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
    }
    private DamageSource strikeSource() { return new DamageSource(registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STRIKE), this); }
    private boolean contact(LivingEntity target) { return getBoundingBox().inflate(.65D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition()); }
    public double armorBypass() { return Config.spectralBypass(inPlane()); }
    public boolean strike(ServerLevel level, LivingEntity target) {
        if (!combatEnabled() || meleeTicks > 0 || !validTarget(target) || !contact(target) || damage(level, target, strikeSource(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0) { return false; }
        meleeTicks = 32; veilTicks = 8; action("strike", 10);
        SpectralVfx.emit(this, SpectralVfx.STRIKE, null, target.getBoundingBox().getCenter(), target.position().subtract(position()), 14, 1.3F); return true;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return target instanceof LivingEntity enemy && strike(level, enemy); }
    public boolean lance(ServerLevel level, Vec3 aim) {
        int interval = getControllingPassenger() instanceof Player ? 6 : 240;
        if (!combatEnabled() || cooldowns[0] > 240 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.7D)); if (!line(getEyePosition(), muzzle)) { return false; }
        SpectralLance lance = PhantomismContent.SPECTRAL_LANCE.get().create(level, EntitySpawnReason.TRIGGERED); if (lance == null) { return false; }
        lance.setOwner(this); lance.configure(session); lance.setPos(muzzle); lance.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(lance)) { return false; }
        cooldowns[0] = 240; action("lance", 10); SpectralVfx.emit(this, SpectralVfx.LANCE, lance, muzzle, direction, 32, .6F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return lance(level, rider.getViewVector(1)); }
    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!combatEnabled() || cooldowns[1] > 0 || diveTicks > 0 || !validTarget(target) || distanceToSqr(target) > 12 * 12 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
        Vec3 step = direction.scale(.8D); if (!safeStep(step)) { return false; }
        diveTarget = target.getUUID(); diveTicks = 12; veilTicks = 12; cooldowns[1] = 200; action("dive", 14);
        if (getControllingPassenger() instanceof Player rider) { Vec3 impulse = rider.getViewVector(1).scale(.5D); if (safeStep(getDeltaMovement().add(impulse))) { setDeltaMovement(getDeltaMovement().add(impulse)); needsSync = true; } }
        SpectralVfx.emit(this, SpectralVfx.DIVE, this, position(), direction, 20, 1.4F); return true;
    }
    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; } diveTicks--;
        if (!(level.getEntity(diveTarget) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { diveTicks = 0; return; }
        if (contact(target)) {
            diveTicks = 0; meleeTicks = Math.max(meleeTicks, 32);
            if (damage(level, target, strikeSource(), (float) (getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * 1.6D)) > 0) {
                SpectralVfx.emit(this, SpectralVfx.STRIKE, null, target.getBoundingBox().getCenter(), target.position().subtract(position()), 18, 1.8F);
            }
            return;
        }
        if (!isVehicle()) { Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.8D); if (!safeStep(step)) { diveTicks = 0; } else { setDeltaMovement(step); needsSync = true; } }
    }
    private boolean safeStep(Vec3 step) {
        AABB box = getBoundingBox().expandTowards(step);
        if (!isLoadedFlightBox(box) || !level().noCollision(this, box) || level().containsAnyLiquid(box)) { return false; }
        for (Entity rider : getPassengers()) { AABB swept = rider.getBoundingBox().expandTowards(step); if (!isLoadedFlightBox(swept) || !level().noBlockCollision(rider, swept) || level().containsAnyLiquid(swept)) { return false; } }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous((ServerLevel) level(), block)) { return false; } }
        return true;
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        Vec3 start = rider.getEyePosition(), end = start.add(rider.getViewVector(1).scale(12));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.7D).clip(start, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && dive(level, target);
    }
    public boolean requestSafeDismount(Player rider) {
        if (!(level() instanceof ServerLevel) || rider != getControllingPassenger() || !isOwnedBy(rider) || !passage.recall()) { return false; }
        rider.stopRiding(); return rider.getVehicle() != this;
    }
    public boolean slip(ServerLevel level, LivingEntity threat) {
        if (!combatEnabled() || cooldowns[2] > 0 || !validTarget(threat) || distanceToSqr(threat) > 16 * 16) { return false; }
        Vec3 away = position().subtract(threat.position()).multiply(1, 0, 1).normalize(); if (away.lengthSqr() < .01D) { away = new Vec3(1, 0, 0); }
        Vec3 old = position();
        for (double turn : new double[]{0, .8D, -.8D, 1.6D, -1.6D}) {
            Vec3 candidate = old.add(away.yRot((float) turn).scale(3.5D)).add(0, .5D, 0);
            if (getAssignments().permitsDefensivePursuit(threat) && passage.shift(candidate)) {
                cooldowns[2] = 240; veilTicks = 12; diveTicks = 0; action("slip", 12);
                SpectralVfx.emit(this, SpectralVfx.SLIP, this, old, position().subtract(old), 24, 1.7F); return true;
            }
        }
        return false;
    }
    public boolean mark(ServerLevel level, LivingEntity target) {
        if (!combatEnabled() || cooldowns[3] > 0 || marked != null || !validTarget(target) || distanceToSqr(target) > 48 * 48 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        marked = target.getUUID(); markTicks = 160; cooldowns[3] = 240; action("mark", 10);
        SpectralVfx.emit(this, SpectralVfx.MARK, target, target.getEyePosition(), Vec3.ZERO, 160, .65F); return true;
    }
    public LivingEntity markedTarget() { return marked != null && level() instanceof ServerLevel level && level.getEntity(marked) instanceof LivingEntity target && validTarget(target) && distanceToSqr(target) <= 48 * 48 ? target : null; }
    public boolean guard(ServerLevel level, LivingEntity threat) {
        if (!movementEnabled() || cooldowns[4] > 0 || guardTicks > 0 || !validTarget(threat) || distanceToSqr(threat) > 24 * 24) { return false; }
        guardTicks = 60; cooldowns[4] = 480; action("guard", 12); SpectralVfx.emit(this, SpectralVfx.GUARD, this, position(), Vec3.ZERO, 60, 2); return true;
    }
    public float mitigation() {
        float guard = guardTicks > 0 ? .3F : 0;
        float plane = inPlane() ? (float) Config.spectralPlaneMitigation() : 0;
        return Math.max(guard, plane);
    }
    public boolean plane(ServerLevel level) {
        if (!combatEnabled() || cooldowns[5] > 0 || planeTicks > 0) { return false; }
        sense(level); List<LivingEntity> threats = threats(12, 4);
        if (!validTarget(getTarget()) || threats.size() < 3 && !(getHealth() <= getMaxHealth() * .5F && threats.stream().anyMatch(this::provoked))) { return false; }
        planeTicks = 240; cooldowns[5] = 1800; entityData.set(STATE, (byte) (entityData.get(STATE) | 2)); planeModifier(true); action("plane", 30);
        SpectralVfx.emit(this, SpectralVfx.PLANE, this, position(), Vec3.ZERO, 240, 3); return true;
    }
    private void planeModifier(boolean active) {
        var flight = getAttribute(Attributes.FLYING_SPEED); if (flight == null) { return; }
        if (active) { flight.addOrUpdateTransientModifier(new AttributeModifier(PLANE, Config.spectralPlaneSpeed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        else { flight.removeModifier(PLANE); }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!combatEnabled() || !validTarget(target)) { return true; }
        if (diveTicks > 0 || actionTicks > 0) { return true; }
        if (!isTame() && getHealth() <= getMaxHealth() * .65F && slip(level, target)) { return true; }
        if (plane(level) || mark(level, target)) { return true; }
        if (contact(target) && strike(level, target)) { return true; }
        if (distanceToSqr(target) <= 26 * 26 && lance(level, target.getBoundingBox().getCenter().subtract(getEyePosition()))) { return true; }
        return distanceToSqr(target) <= 12 * 12 && dive(level, target);
    }
    @Override protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (!isVehicle() && combatEnabled() && validTarget(target) && distanceToSqr(target) < 8 * 8 && !line(getEyePosition(), target.getEyePosition())) {
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize();
            return passage.beginToward(target.position().add(away.scale(2.5D)).add(0, 1, 0));
        }
        return false;
    }
    @Override public void travel(Vec3 input) {
        if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); }
    }
    @Override public void move(MoverType type, Vec3 movement) {
        if (passage != null) { passage.tryMountedEntry(movement); }
        if (passage != null && passage.active() && !passage.allowsMove(position().add(movement))) { setDeltaMovement(Vec3.ZERO); return; }
        Vec3 previous = position(); super.move(type, movement);
        if (passage != null && level() instanceof ServerLevel && passage.active()) { passage.moved(previous); }
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (passage != null && passage.active()) { getNavigation().stop(); if (tickCount % 20 == 0) { getConfiguredStats().apply(this); } return; }
        super.customServerAiStep(level);
    }
    @Override public void tick() {
        passage.beforeTick(); super.tick();
        boolean recoveringRider = !isAlive() && !isRemoved() && isPhasing() && isVehicle();
        if (recoveringRider) { travel(Vec3.ZERO); }
        if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; } if (veilTicks > 0) { veilTicks--; } if (guardTicks > 0) { guardTicks--; }
        if (planeTicks > 0 && --planeTicks == 0) { planeModifier(false); SpectralVfx.emit(this, SpectralVfx.FOLD, this, position(), Vec3.ZERO, 20, 3); }
        if (marked != null && (--markTicks <= 0 || markedTarget() == null)) { marked = null; markTicks = 0; SpectralVfx.closeMark(this); }
        if (!movementEnabled()) { cancelCombat(); passage.recall(); }
        else {
            if (tickCount % 20 == 0) { sense(level); }
            if (combatEnabled()) {
                tickDive(level);
                if (getControllingPassenger() != null && tickCount % 10 == 0 && validTarget(getTarget())) { mark(level, getTarget()); plane(level); }
                if (!isTame() && validTarget(getLastHurtByMob()) && tickCount % 10 == 0) { slip(level, getLastHurtByMob()); }
            }
        }
        passage.afterTick(); entityData.set(STATE, (byte) ((isPhasing() ? 1 : 0) | (planeTicks > 0 ? 2 : 0) | (veilTicks > 0 || guardTicks > 0 ? 4 : 0)));
        if (recoveringRider && !isPhasing() && !isRemoved()) { remove(RemovalReason.KILLED); }
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isAlive() && !isTame() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void passageAnimation() { action("passage", 18); }
    private void action(String clip, int ticks) { sessionOpen = true; if (isAddedToLevel()) { triggerAnim("spectral_action", clip); } actionTicks = ticks; }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<SpectralPhantom>("spectral_action", 2, test -> PlayState.STOP);
        for (String clip : List.of("strike", "lance", "dive", "slip", "mark", "guard", "plane", "passage")) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.spectral." + clip)); }
        registrar.add(controller.additiveAnimations());
    }
    public void cancelCombat() {
        if (session == null) { return; }
        if (sessionOpen) { SpectralVfx.cancel(this); session = UUID.randomUUID(); sessionOpen = false; }
        planeTicks = 0; guardTicks = 0; diveTicks = 0; diveTarget = null; marked = null; markTicks = 0; veilTicks = 0; planeModifier(false);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); if (passage != null) { passage.recall(); } } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (passage != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); passage.recall(); } }
    @Override public void setTask(Task task) { super.setTask(task); if (passage != null && task != Task.NONE && task != Task.HUNT) { cancelCombat(); passage.recall(); } }
    @Override public boolean canTeleport(Level from, Level to) { return (passage == null || !passage.active()) && super.canTeleport(from, to); }
    @Override public void die(DamageSource source) { passage.recall(); cancelCombat(); super.die(source); }
    @Override public void remove(RemovalReason reason) {
        if (passage != null) {
            if (passage.active() && isVehicle() && reason.shouldDestroy() && !passage.recall()) { cancelCombat(); return; }
            passage.recall(); passage.releaseTickets(); cancelCombat();
        }
        super.remove(reason);
    }
    @Override public void saveWithoutId(ValueOutput output) { super.saveWithoutId(output); if (passage != null) { Vec3 safe = passage.savedPosition(this); if (safe != null) { output.store("Pos", Vec3.CODEC, safe); output.store("Motion", Vec3.CODEC, Vec3.ZERO); } } }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("SpectralMelee", meleeTicks); passage.save(output);
        for (int index = 0; index < cooldowns.length; index++) { output.putInt("SpectralCooldown" + index, cooldowns[index]); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cancelCombat(); session = UUID.randomUUID(); passage.load(input);
        meleeTicks = Math.clamp(input.getIntOr("SpectralMelee", 0), 0, 32);
        for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("SpectralCooldown" + index, 0), 0, INTERVALS[index]); }
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> result = new ArrayList<>();
        for (int index = 0; index < 5; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("spectral_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(result);
    }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.GHAST_TEAR, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Lance: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Dive: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Slip: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Mark: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Plane: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "strike" -> meleeTicks;
            case "lance" -> cooldowns[0];
            case "dive" -> cooldowns[1];
            case "slip" -> cooldowns[2];
            case "mark" -> cooldowns[3];
            case "guard" -> cooldowns[4];
            case "plane" -> cooldowns[5];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
