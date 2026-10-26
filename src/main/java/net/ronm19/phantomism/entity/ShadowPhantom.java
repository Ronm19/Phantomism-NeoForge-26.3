package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
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
import net.ronm19.phantomism.entity.spawn.ShadowEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.ShadowVfx;

public final class ShadowPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/shadow_phantom");
    public static final int NIGHT_DURATION = 240, NIGHT_COOLDOWN = 1800, HUNT_RADIUS = 24;
    private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(ShadowPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CONCEALED = SynchedEntityData.defineId(ShadowPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VANISHED = SynchedEntityData.defineId(ShadowPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(ShadowPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS = {200, 120, 200, 240, 360, 400, 500, NIGHT_COOLDOWN};
    private static final String[] CLIPS = {"bolt", "strike", "dive", "step", "vanish", "recon", "guard", "nightfall", "hunt", "finale"};
    private record Isolation(long until, boolean isolated) {}
    private final Map<UUID, Isolation> isolation = new HashMap<>();
    private final int[] cooldowns = new int[8];
    private final List<UUID> reconTargets = new ArrayList<>();
    private UUID session = UUID.randomUUID(), orderedId, markId, diveId, diveRider, ownerContext;
    private int markTicks, markCooldown, focusTicks, mountedBoltTicks, openingTicks, exposedTicks, meleeTicks, diveTicks, liftTicks, vanishTicks, guardTicks, nightTicks, tameAttempts;
    private int isolationScans, nightSpent;
    private long isolationWindow = Long.MIN_VALUE;
    private Vec3 liftPoint;
    private boolean visualOpen;

    public ShadowPhantom(EntityType<? extends ShadowPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 6)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .25D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .55D);
    }
    public static boolean checkSpawnRules(EntityType<ShadowPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return ShadowEncounterSpawner.habitat(accessor.getLevel(), position) && accessor.getFluidState(position).isEmpty()
                && accessor.getBlockState(position).getCollisionShape(accessor, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SHADOW_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected int getMeleeAttackCooldownTicks() { return 32; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean prefersRangedCombat() { return false; }
    @Override protected boolean shouldPlayWingSound() { return false; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.EVENTS; }
    @Override public void playSound(SoundEvent sound, float volume, float pitch) { super.playSound(sound, sound == SoundEvents.PHANTOM_SWOOP ? Math.min(volume, .12F) : volume, pitch); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL, 0); data.define(CONCEALED, false); data.define(VANISHED, false); data.define(DIVING, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(DIVING) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(DIVING) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(DIVING)) { setDeltaMovement(Vec3.ZERO); } else if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    @Override public boolean isUltimateAnimationActive() { return visualState() == 3 || super.isUltimateAnimationActive(); }
    public int visualState() { return entityData.get(VISUAL); }
    public boolean concealed() { return entityData.get(CONCEALED); }
    public boolean vanishing() { return entityData.get(VANISHED); }
    public int vanishTicks() { return vanishTicks; }
    public int nightTicks() { return nightTicks; }
    public UUID session() { return session; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer() != null && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean recipient(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && (target == this || isTame() &&
                (target == getOwner() || getTeam() != null && getTeam().isAlliedTo(target.getTeam()) || target instanceof AbstractPhantom phantom
                && phantom.isTame() && Objects.equals(getOwnerReference(), phantom.getOwnerReference())));
    }
    public boolean ally(LivingEntity target) {
        return target != null && (recipient(target) || target instanceof AbstractPhantom && !PhantomDuels.pair(this, target)
                || isAlliedTo(target) && !(target instanceof AbstractPhantom) || isTame() && target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference()));
    }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable() && !ally(target)
                && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean recent(LivingEntity attacker, LivingEntity victim) {
        return attacker != null && victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100
                || attacker.getLastHurtMob() == victim && attacker.tickCount - attacker.getLastHurtMobTimestamp() < 100 || attacker instanceof Mob mob && mob.getTarget() == victim);
    }
    public boolean ownerThreat(LivingEntity target) { return recent(target, getOwner()) || target instanceof Mob mob && recipient(mob.getTarget()) && !PhantomDuels.pair(target, mob.getTarget()); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean explicit = target.getUUID().equals(orderedId) || recent(target, this) || recent(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof OwnableEntity owned && owned.getOwnerReference() != null || target instanceof Player || target instanceof AbstractPhantom) { return explicit; }
        return explicit || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && (ownerThreat(target) || recent(target, this) || distanceToSqr(target) <= 24 * 24 && !(target instanceof Mob mob && GravityControl.movementImmune(mob)) && isolated(target)); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (ownerThreat(target) ? -1000 : 0) + (target == marked() ? -120 : 0) + (isolated(target) ? -60 : 0) + target.getHealth() / target.getMaxHealth() * 40 + distanceToSqr(target) * .1D; }
    @Override public void setCombatTarget(LivingEntity target) { if (target != null && !recipient(target) && !PhantomDuels.excludesObserver(this, target) && PhantomDuels.begin(this, target) && safeEnemy(target)) { orderedId = target.getUUID(); if (markId != null && !markId.equals(orderedId)) { markId = null; markTicks = 0; } super.setCombatTarget(target); } }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public boolean safeTravel(Vec3 destination) {
        Vec3 delta = destination.subtract(position()); if (!Double.isFinite(delta.lengthSqr()) || delta.lengthSqr() > 8 * 8) { return false; }
        for (Entity member : getSelfAndPassengers().limit(9).toList()) {
            AABB swept = member.getBoundingBox().expandTowards(delta).inflate(.03D);
            if (member.isPassenger()) { Entity parent = member.getVehicle(); Vec3 seat = parent.getPassengerRidingPosition(member).subtract(member.getVehicleAttachmentPoint(parent)); swept = swept.minmax(member.getBoundingBox().move(seat.subtract(member.position())).expandTowards(delta).inflate(.03D)); }
            if (!isLoadedFlightBox(swept) || !level().getWorldBorder().isWithinBounds(swept) || !level().noBlockCollision(member, swept) || level().containsAnyLiquid(swept)) { return false; }
            if (level() instanceof ServerLevel server) { for (BlockPos point : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY - .2D, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) { if (GravityControl.dangerous(server, point)) { return false; } } }
        }
        return true;
    }
    public List<LivingEntity> enemies(ServerLevel level, double radius, int maximum) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::validTarget).stream().filter(target -> distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();
    }
    public boolean isolated(LivingEntity target) {
        if (!validTarget(target) || distanceToSqr(target) > 48 * 48 || !(level() instanceof ServerLevel level)) { return false; }
        long now = level.getGameTime(); Isolation cached = isolation.get(target.getUUID()); if (cached != null && cached.until() > now) { return cached.isolated(); }
        if (isolationWindow != now / 20) { isolationWindow = now / 20; isolationScans = 0; isolation.entrySet().removeIf(entry -> entry.getValue().until() <= now); }
        if (isolationScans++ >= 8 || isolation.size() >= 24) { return false; }
        boolean alone = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(6), other -> other != target && validTarget(other) && other.distanceToSqr(target) <= 36 && line(target.getEyePosition(), other.getEyePosition())).isEmpty();
        isolation.put(target.getUUID(), new Isolation(now + 20, alone)); return alone;
    }
    public LivingEntity marked() {
        if (markTicks <= 0 || markId == null || !(level() instanceof ServerLevel level)) { return null; }
        if (level.getEntity(markId) instanceof LivingEntity target && validTarget(target) && distanceToSqr(target) <= 48 * 48) { return target; }
        markId = null; markTicks = 0; return null;
    }
    public boolean mark(ServerLevel level, LivingEntity target) {
        if (!enabled() || markCooldown > 0 || !validTarget(target) || distanceToSqr(target) > 32 * 32 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        markId = target.getUUID(); markTicks = 300; markCooldown = 40; focusTicks = 16;
        ShadowVfx.emit(this, ShadowVfx.MARK, target, target.position().add(0, target.getBbHeight() + .4D, 0), Vec3.ZERO, 24, .65F); return true;
    }
    public boolean concealsFrom(Mob observer) {
        if (!enabled() || !ShadowStealth.compatible(observer) || observer.level() != level() || distanceToSqr(observer) < 16 || !validTarget(observer)) { return false; }
        if (exposedTicks > 0 && vanishTicks == 0 || !concealed() && vanishTicks == 0 && nightTicks == 0) { return false; }
        Vec3 toward = getEyePosition().subtract(observer.getEyePosition()).normalize();
        return !(distanceToSqr(observer) < 12 * 12 && observer.hasLineOfSight(this) && observer.getViewVector(1).dot(toward) > .45D);
    }
    @Override public double getVisibilityPercent(ServerLevel level, Entity observer) { return super.getVisibilityPercent(level, observer) * (observer instanceof Mob mob && concealsFrom(mob) ? .5D : 1); }
    private boolean behind(LivingEntity target) { return target.getViewVector(1).dot(position().subtract(target.position()).normalize()) < -.35D; }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float base, boolean ambush) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(base) || base <= 0) { return 0; }
        boolean opening = ambush && openingTicks == 0 && (isolated(target) || behind(target));
        float bonus = opening ? 1.2F + (marked() == target ? .15F : 0) + (nightTicks > 0 ? .1F : 0) : 1;
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, source, base * Math.min(1.45F, bonus))) { return 0; }
        float dealt = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        if (dealt > 0) { if (opening) { openingTicks = 60; } vanishTicks = 0; exposedTicks = 40; entityData.set(CONCEALED, false); entityData.set(VANISHED, false); }
        return dealt;
    }
    public boolean bolt(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || (mounted ? mountedBoltTicks > 0 || !(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider) : cooldowns[0] > 0) || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.6D)); if (!line(getEyePosition(), muzzle)) { return false; }
        ShadowBolt shot = PhantomismContent.SHADOW_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; } if (mounted) { mountedBoltTicks = 6; } else { cooldowns[0] = INTERVALS[0]; }
        vanishTicks = 0; exposedTicks = 40; entityData.set(VANISHED, false); entityData.set(CONCEALED, false); action("bolt"); ShadowVfx.emit(this, ShadowVfx.BOLT, shot, muzzle, direction, 32, .6F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return bolt(level, rider.getViewVector(1), true); }
    public boolean strike(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || !validTarget(target) || !getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox()) || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (damage(level, target, damageSources().mobAttack(this), 16, true) <= 0) { return false; }
        cooldowns[1] = INTERVALS[1]; meleeTicks = 32; action("strike"); ShadowVfx.emit(this, ShadowVfx.STRIKE, null, target.position(), Vec3.ZERO, 16, 2.8F); return true;
    }
    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || diveTicks > 0 || !validTarget(target) || distanceToSqr(target) > 14 * 14 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 lift = position().add(0, Math.max(0, Math.min(2, target.getY() + 2.5D - getY())), 0);
        liftTicks = lift.distanceToSqr(position()) > .2D && safeTravel(lift) ? 5 : 0; liftPoint = lift;
        diveId = target.getUUID(); diveRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); diveTicks = 26; cooldowns[2] = INTERVALS[2];
        entityData.set(DIVING, true); syncRider(false); vanishTicks = 0; exposedTicks = 40; action("dive"); ShadowVfx.emit(this, ShadowVfx.DIVE, this, position(), target.position().subtract(position()).normalize(), 28, 2); return true;
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, 14, 16).stream().filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .45D).findFirst().orElse(null); return dive(level, target);
    }
    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        if (--diveTicks == 0 || diveRider != null && (getControllingPassenger() == null || !diveRider.equals(getControllingPassenger().getUUID())) || !(level.getEntity(diveId) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { endDive(); return; }
        if (liftTicks > 0) {
            liftTicks--; Vec3 lift = liftPoint.subtract(position()); if (lift.length() > .45D) { lift = lift.normalize().scale(.45D); }
            if (safeTravel(position().add(lift))) { move(MoverType.SELF, lift); setDeltaMovement(Vec3.ZERO); syncRider(false); return; } liftTicks = 0;
        }
        if (getBoundingBox().inflate(.65D).intersects(target.getBoundingBox())) { damage(level, target, damageSources().mobAttack(this), 18, true); meleeTicks = 32; ShadowVfx.emit(this, ShadowVfx.IMPACT, null, target.position(), Vec3.ZERO, 20, 3); endDive(); return; }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.85D);
        if (!safeTravel(position().add(step))) { endDive(); return; } move(MoverType.SELF, step); setDeltaMovement(Vec3.ZERO); syncRider(false);
    }
    private void syncRider(boolean finish) {
        if (!(level() instanceof ServerLevel) || !(getControllingPassenger() instanceof ServerPlayer rider) || rider.connection == null) { return; }
        positionRider(rider); needsSync = true; rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(DIVING, entityData.get(DIVING)))));
        if (finish) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
    }
    private void endDive() { boolean active = entityData.get(DIVING); diveTicks = 0; liftTicks = 0; liftPoint = null; diveId = null; diveRider = null; entityData.set(DIVING, false); if (active) { setDeltaMovement(Vec3.ZERO); syncRider(true); } }
    public boolean step(ServerLevel level, LivingEntity target, boolean escape) {
        if (!enabled() || cooldowns[3] > 0 || diveTicks > 0 || !validTarget(target) || distanceToSqr(target) > 14 * 14) { return false; }
        Vec3 view = target.getViewVector(1).multiply(1, 0, 1).normalize(); if (view.lengthSqr() < .1D) { view = new Vec3(0, 0, 1); }
        Vec3 side = new Vec3(-view.z, 0, view.x);
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize(); if (away.lengthSqr() < .1D) { away = view.scale(-1); }
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 point = escape ? position().add(away.scale(4)).add(side.scale((attempt % 2 == 0 ? 1 : -1) * (attempt / 2)))
                    : target.position().subtract(view.scale(2.2D + attempt / 2 * .3D)).add(side.scale((attempt % 2 == 0 ? 1 : -1) * (attempt / 2)));
            point = new Vec3(point.x, Math.clamp(target.getY() + .5D, getY() - 2, getY() + 2), point.z);
            if (point.distanceToSqr(position()) < 1 || getOwner() instanceof LivingEntity owner && owner.getHealth() < owner.getMaxHealth() * .4F && point.distanceToSqr(owner.position()) > 16 * 16 || !safeTravel(point) || !line(point.add(0, getEyeHeight(), 0), target.getEyePosition())) { continue; }
            var teleport = new net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderEntity(this, point.x, point.y, point.z); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(teleport);
            if (teleport.isCanceled()) { return false; } point = teleport.getTarget();
            if (!enabled() || level() != level || !validTarget(target) || !safeTravel(point) || !line(point.add(0, getEyeHeight(), 0), target.getEyePosition()) || !canTeleport(level, level)) { return false; }
            Vec3 origin = position(); entityData.set(DIVING, true); syncRider(false); teleportTo(point.x, point.y, point.z); setDeltaMovement(Vec3.ZERO); resetFallDistance(); needsSync = true; entityData.set(DIVING, false); syncRider(true);
            level.getChunkSource().sendToTrackingPlayers(this, net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(getId(), net.minecraft.world.entity.PositionMoveRotation.of(this), Set.of(), onGround()));
            cooldowns[3] = nightTicks > 0 ? 120 : INTERVALS[3]; action("step"); ShadowVfx.emit(this, ShadowVfx.STEP, null, origin, point.subtract(origin), 16, 2); return true;
        }
        return false;
    }
    public boolean vanish(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || vanishTicks > 0 || diveTicks > 0) { return false; }
        cooldowns[4] = INTERVALS[4]; vanishTicks = 60; entityData.set(CONCEALED, true); entityData.set(VANISHED, true); action("vanish"); ShadowStealth.disrupt(this, level);
        ShadowVfx.emit(this, ShadowVfx.VANISH, this, position(), Vec3.ZERO, 60, 2); return true;
    }
    public boolean recon(ServerLevel level) {
        if (!enabled() || cooldowns[5] > 0) { return false; }
        var candidates = enemies(level, HUNT_RADIUS, 12); reconTargets.clear(); for (LivingEntity target : candidates) { reconTargets.add(target.getUUID()); }
        cooldowns[5] = INTERVALS[5];
        LivingEntity best = candidates.stream().filter(this::ownerThreat).findFirst().orElse(candidates.isEmpty() ? null : candidates.getFirst());
        if (best != null && (orderedId == null || !(level.getEntity(orderedId) instanceof LivingEntity ordered) || !validTarget(ordered))) {
            if (!validTarget(getTarget()) || ownerThreat(best) && !ownerThreat(getTarget())) { setTarget(best); getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, best); }
            mark(level, best);
        }
        action("recon"); ShadowVfx.emit(this, ShadowVfx.RECON, null, position(), Vec3.ZERO, 26, HUNT_RADIUS); return true;
    }
    public int reconCount() { return reconTargets.size(); }
    public boolean guard(ServerLevel level, LivingEntity attacker, float incoming) {
        if (!enabled() || cooldowns[6] > 0 || !validTarget(attacker) || !Float.isFinite(incoming) || incoming < 4 && getHealth() - incoming > getMaxHealth() * .3F) { return false; }
        cooldowns[6] = INTERVALS[6]; guardTicks = 40; action("guard"); ShadowVfx.emit(this, ShadowVfx.GUARD, this, position(), Vec3.ZERO, 40, 2); return true;
    }
    public float guardMitigation() { return enabled() && guardTicks > 0 ? .35F : 0; }
    public boolean nightfall(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || nightTicks > 0) { return false; }
        nightTicks = NIGHT_DURATION; nightSpent = 0; cooldowns[7] = NIGHT_COOLDOWN; if (marked() == null) { var choices = enemies(level, HUNT_RADIUS, 8); if (!choices.isEmpty()) { markCooldown = 0; mark(level, choices.getFirst()); } }
        action("nightfall"); entityData.set(VISUAL, 3); ShadowStealth.disrupt(this, level); ShadowVfx.emit(this, ShadowVfx.NIGHTFALL, this, position(), Vec3.ZERO, NIGHT_DURATION, 12); return true;
    }
    private void tickNightfall(ServerLevel level) {
        if (nightTicks <= 0) { return; } int elapsed = NIGHT_DURATION - --nightTicks;
        LivingEntity target = marked();
        if (elapsed % 40 == 0 && elapsed <= 160) {
            ShadowStealth.disrupt(this, level);
            if (target != null && distanceToSqr(target) <= 8 * 8 && !getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox())) { step(level, target, false); }
            if (target != null && getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition()) && nightSpent < 32) {
                float dealt = damage(level, target, damageSources().mobAttack(this), Math.min(8, 32 - nightSpent), false); if (dealt > 0) { nightSpent += 8; action("hunt"); ShadowVfx.emit(this, ShadowVfx.STRIKE, target, target.position(), Vec3.ZERO, 14, 2.5F); }
            }
        }
        if (elapsed == 216) {
            if (target != null && distanceToSqr(target) <= 8 * 8 && !getBoundingBox().inflate(2).intersects(target.getBoundingBox())) { step(level, target, false); }
            if (target != null && getBoundingBox().inflate(2).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition())) { damage(level, target, damageSources().mobAttack(this), Math.min(20, 52 - nightSpent), false); nightSpent = 52; }
            action("finale"); ShadowVfx.emit(this, ShadowVfx.FINALE, null, position(), Vec3.ZERO, 24, 5);
        }
        if (nightTicks == 0) { ShadowStealth.release(this); }
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.55D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition()) || damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE), false) <= 0) { return false; }
        meleeTicks = 32; action("strike"); return true;
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || diveTicks > 0) { return true; }
        if (withdrawing(target)) { vanish(level); step(level, target, true); return true; }
        if (strike(level, target)) { return true; }
        if (distanceToSqr(target) > 9 && (isolated(target) || ownerThreat(target)) && dive(level, target)) { return true; }
        return doHurtTarget(level, target);
    }
    private boolean withdrawing(LivingEntity target) { return getHealth() < getMaxHealth() * .4F && (!ownerThreat(target) || getOwner() == null || getOwner().getHealth() > getOwner().getMaxHealth() * .35F); }
    @Override protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        if (!validTarget(target)) { return true; }
        Vec3 direction = target.getViewVector(1).multiply(1, 0, 1).normalize(); if (direction.lengthSqr() < .1D) { direction = new Vec3(0, 0, 1); }
        double side = (getUUID().getLeastSignificantBits() & 1) == 0 ? 1 : -1;
        double range = !isolated(target) && !ownerThreat(target) ? 8 : 4;
        Vec3 destination = target.position().add(-direction.z * side * range, 3, direction.x * side * range);
        flyToAssignment(destination, 1.1D); return true;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        if (!onGround() && getDeltaMovement().lengthSqr() > .0025D && tickCount % 20 == 0) { gameEvent(net.minecraft.world.level.gameevent.GameEvent.FLAP); }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (markCooldown > 0) { markCooldown--; } if (focusTicks > 0) { focusTicks--; } if (markTicks > 0) { markTicks--; } if (mountedBoltTicks > 0) { mountedBoltTicks--; }
        if (openingTicks > 0) { openingTicks--; } if (exposedTicks > 0) { exposedTicks--; } if (meleeTicks > 0) { meleeTicks--; } if (vanishTicks > 0) { vanishTicks--; } if (guardTicks > 0) { guardTicks--; }
        UUID owner = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID(); if (ownerContext != null && !owner.equals(ownerContext)) { endShadow(); } ownerContext = owner;
        if (!enabled()) { endShadow(); return; }
        marked(); tickDive(level); tickNightfall(level);
        entityData.set(VANISHED, vanishTicks > 0);
        entityData.set(CONCEALED, vanishTicks > 0 || exposedTicks == 0 && (level.getMaxLocalRawBrightness(blockPosition()) <= 7 || nightTicks > 0));
        entityData.set(VISUAL, nightTicks > 0 ? 3 : focusTicks > 0 ? 2 : validTarget(getTarget()) ? 1 : 0);
        if (tickCount % 10 == 0) { ShadowStealth.maintain(); if (vanishTicks > 0) { ShadowStealth.disrupt(this, level); } }
        if (!isNoAi() && tickCount % 10 == 0) {
            LivingEntity target = getTarget();
            if (ownerThreat(target) || !validTarget(target)) { recon(level); target = getTarget(); }
            if (!validTarget(target)) { return; }
            if (marked() != target) { mark(level, target); }
            if (withdrawing(target)) { vanish(level); if (!isVehicle()) { step(level, target, true); if (vanishTicks == 0 && line(getEyePosition(), target.getEyePosition()) && distanceToSqr(target) <= 42 * 42) { bolt(level, target.getEyePosition().subtract(getEyePosition()), false); } } return; }
            if (target.getMaxHealth() >= 40 && target.getHealth() > 18 || ownerThreat(target) && enemies(level, 12, 4).size() >= 3) { nightfall(level); }
            if (!isVehicle()) {
                boolean group = !isolated(target) && !ownerThreat(target);
                if (!group && diveTicks == 0) { if (!strike(level, target) && distanceToSqr(target) > 9) { if (!step(level, target, false)) { dive(level, target); } } }
                if (distanceToSqr(target) <= ShadowBolt.MAX_TRAVEL_DISTANCE * ShadowBolt.MAX_TRAVEL_DISTANCE && line(getEyePosition(), target.getEyePosition())) { bolt(level, target.getEyePosition().subtract(getEyePosition()), false); }
            }
        }
    }
    private void action(String clip) { visualOpen = true; triggerAnim("shadow", clip); if (!clip.equals("vanish") && !clip.equals("recon")) { playSound(SoundEvents.PLAYER_ATTACK_SWEEP, .28F, .85F); } }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); controllers.add(new AnimationController<ShadowPhantom>("shadow_quiet", 8, state -> concealed() ? state.setAndContinue(RawAnimation.begin().thenLoop("animation.shadow.quiet")) : PlayState.STOP).additiveAnimations());
        var controller = new AnimationController<ShadowPhantom>("shadow", 2, state -> PlayState.STOP); for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.shadow." + clip)); } controllers.add(controller);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } if (++tameAttempts >= preferredTaming().guaranteedAttempt() || getRandom().nextFloat() < preferredTaming().chance()) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte) 7); } else { level().broadcastEntityEvent(this, (byte) 6); } }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endShadow() {
        if (level() instanceof ServerLevel) { ShadowStealth.release(this); if (visualOpen) { ShadowVfx.cancel(this); session = UUID.randomUUID(); visualOpen = false; } }
        endDive(); nightTicks = 0; vanishTicks = 0; guardTicks = 0; markTicks = 0; markId = null; orderedId = null; reconTargets.clear(); isolation.clear(); entityData.set(CONCEALED, false); entityData.set(VANISHED, false); entityData.set(VISUAL, 0);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endShadow(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endShadow(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endShadow(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endShadow(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("ShadowCooldown" + index, cooldowns[index]); }
        output.putInt("ShadowMountedBolt", mountedBoltTicks); output.putInt("ShadowOpening", openingTicks); output.putInt("ShadowExposed", exposedTicks); output.putInt("ShadowTameAttempts", tameAttempts); output.putInt("ShadowMarkCooldown", markCooldown); output.putInt("ShadowMelee", meleeTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endShadow(); session = UUID.randomUUID();
        for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("ShadowCooldown" + index, 0), 0, INTERVALS[index]); }
        mountedBoltTicks = Math.clamp(input.getIntOr("ShadowMountedBolt", 0), 0, 6); openingTicks = Math.clamp(input.getIntOr("ShadowOpening", 0), 0, 60); exposedTicks = Math.clamp(input.getIntOr("ShadowExposed", 0), 0, 40); tameAttempts = Math.clamp(input.getIntOr("ShadowTameAttempts", 0), 0, 3); markCooldown = Math.clamp(input.getIntOr("ShadowMarkCooldown", 0), 0, 40); meleeTicks = Math.clamp(input.getIntOr("ShadowMelee", 0), 0, 32);
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { List<CooldownDiscipline.Slot> slots = new ArrayList<>(); for (int index = 0; index < 6; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("shadow_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(slots); }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.INK_SAC, .5F, 3, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Bolt: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Strike: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Dive: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Step: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Vanish: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Recon: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Nightfall: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "bolt" -> cooldowns[0];
            case "strike" -> cooldowns[1];
            case "vanish" -> cooldowns[4];
            case "recon" -> cooldowns[5];
            case "nightfall" -> cooldowns[7];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
