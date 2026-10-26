package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.KingEncounterSpawner;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.RoyalVfx;

public final class PhantomKing extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/phantom_king");
    public static final int DOMINION_DURATION = 300, DOMINION_RADIUS = 40;
    private static final EntityDataAccessor<Byte> MOMENTUM = SynchedEntityData.defineId(PhantomKing.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DOMINION = SynchedEntityData.defineId(PhantomKing.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RAMMING = SynchedEntityData.defineId(PhantomKing.class, EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS = {240, 400, 120, 500, 400, 560, 180, 2400};
    private static final String[] CLIPS = {"ram", "judgment", "strike", "roar", "command", "guard", "intercept", "dominion", "rally", "finale"};
    private final int[] cooldowns = new int[8];
    private UUID session = UUID.randomUUID(), orderedId, warnedPlayer, ramTarget, strikeTarget, judgmentTarget, ownerContext, ramRider;
    private int actionTicks, meleeTicks, strikeTicks, ramTicks, ramWindup, ramMomentum, guardTicks, judgmentTicks, dominionTicks, idleTicks, gainDelay, warningTicks;
    private boolean visualOpen, ramHit, ramBreakAttempted, ramBreakingSpent;
    private Vec3 territory, dominionCenter;
    private final Set<UUID> marked = new HashSet<>();

    public PhantomKing(EntityType<? extends PhantomKing> type, Level level) { super(type, level); refreshDimensions(); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 144).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ARMOR, 22)
                .add(Attributes.ARMOR_TOUGHNESS, 8).add(Attributes.KNOCKBACK_RESISTANCE, .85D).add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .45D).add(Attributes.SCALE, 2);
    }
    public static boolean checkSpawnRules(EntityType<PhantomKing> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        AABB clearance = spawnBox(position);
        return accessor.getLevel().dimension() == Level.OVERWORLD && accessor.getBiome(position).is(ModTags.Biomes.PHANTOM_KING_SPAWNS)
                && accessor.hasChunksAt(BlockPos.containing(clearance.minX - 1, clearance.minY - 1, clearance.minZ - 1), BlockPos.containing(clearance.maxX + 1, clearance.maxY + 1, clearance.maxZ + 1))
                && accessor.getFluidState(position).isEmpty()
                && accessor.getBlockState(position).getCollisionShape(accessor, position).isEmpty()
                && accessor.noBlockCollision(null, clearance);
    }
    public static AABB spawnBox(BlockPos position) { return new AABB(position.getX() - .9D, position.getY(), position.getZ() - .9D, position.getX() + 1.9D, position.getY() + 1.8D, position.getZ() + 1.9D); }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.PHANTOM_KING_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected double getFlightAltitudeOffset(double offset) { return RoyalCruise.idle(this) ? offset + 2 : super.getFlightAltitudeOffset(offset); }
    @Override protected boolean isSuitableIdleFlightPosition(Vec3 destination, AABB clearance) { return super.isSuitableIdleFlightPosition(destination, clearance) && RoyalCruise.clearBelow(this, destination); }
    @Override protected void customServerAiStep(ServerLevel level) { super.customServerAiStep(level); RoyalCruise.tick(this, level); }
    @Override protected int getMeleeAttackCooldownTicks() { return 32; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(MOMENTUM, (byte) 0); builder.define(DOMINION, false); builder.define(RAMMING, false); }
    @Override public boolean isUltimateAnimationActive() { return entityData.get(DOMINION) || super.isUltimateAnimationActive(); }
    @Override public Vec3 getPassengerRidingPosition(Entity passenger) { return position().add(new Vec3(0, .44D * getScale(), .22D * getScale()).yRot((float) Math.toRadians(-getYRot()))); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(RAMMING) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(RAMMING) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(RAMMING)) { setDeltaMovement(Vec3.ZERO); return; } if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return session; }
    public void visualStarted() { visualOpen = true; }
    public int momentum() { return Byte.toUnsignedInt(entityData.get(MOMENTUM)); }
    private void momentum(int value) { entityData.set(MOMENTUM, (byte) Math.clamp(value, 0, 3)); }
    public int dominionTicks() { return dominionTicks; }
    public Vec3 dominionCenter() { return dominionCenter; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    public boolean crew(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && (target == this || isTame() && (target == getOwner()
                || target instanceof AbstractPhantom phantom && phantom.isTame() && Objects.equals(getOwnerReference(), phantom.getOwnerReference())
                || target instanceof AbstractPhantom && isAlliedTo(target)));
    }
    public boolean ally(LivingEntity target) { return target != null && (target == this || target instanceof AbstractPhantom || target == getOwner() || isAlliedTo(target)
            || isTame() && target instanceof OwnableEntity owned && Objects.equals(getOwnerReference(), owned.getOwnerReference())); }
    private boolean recent(LivingEntity attacker, LivingEntity victim) { return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100 || attacker instanceof Mob mob && mob.getTarget() == victim); }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable() && !ally(target)
                && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean explicit = target.getUUID().equals(orderedId) || recent(target, this) || recent(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof Player || target instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return explicit || !isTame() && target instanceof Player && target.getUUID().equals(warnedPlayer) && warningTicks >= 80 && territory != null
                    && target.distanceToSqr(territory) < 24 * 24 && distanceToSqr(target) < 18 * 18;
        }
        return explicit || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return false; }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (judges(target) ? -300 : 0) - Math.min(100, target.getMaxHealth()) + distanceToSqr(target) * .02D; }
    @Override public void setCombatTarget(LivingEntity target) { if (!safeEnemy(target) || !PhantomDuels.begin(this, target)) { return; } orderedId = target.getUUID(); super.setCombatTarget(target); }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .7D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList();
    }
    public List<AbstractPhantom> allies(double radius) {
        return level().getEntitiesOfClass(AbstractPhantom.class, getBoundingBox().inflate(radius), target -> target != this && !(target instanceof PhantomKing) && crew(target) && !target.isBaby())
                .stream().filter(target -> distanceToSqr(target) <= radius * radius).sorted(Comparator.comparingDouble(this::distanceToSqr)).limit(12).toList();
    }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount, boolean gain) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, source, amount * (dominionTicks > 0 ? 1.15F : 1))) { return 0; }
        float dealt = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        if (dealt > 0 && gain && gainDelay == 0) { momentum(momentum() + 1); gainDelay = 20; idleTicks = 0; }
        if (dealt > 0 && dominionTicks > 0) { RoyalVfx.emit(this, RoyalVfx.STRIKE_ARC, target, target.position(), Vec3.ZERO, 14, 2); }
        return dealt;
    }
    public boolean strike(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || meleeTicks > 0 || actionTicks > 0 || !validTarget(target) || !getBoundingBox().inflate(1.4D).intersects(target.getBoundingBox()) || !line(getEyePosition(), target.getEyePosition())) { return false; }
        strikeTarget = target.getUUID(); strikeTicks = 7; meleeTicks = 32; cooldowns[2] = INTERVALS[2]; action("strike", 18); return true;
    }
    private void tickStrike(ServerLevel level) {
        if (strikeTicks <= 0 || --strikeTicks > 0) { return; }
        if (level.getEntity(strikeTarget) instanceof LivingEntity target && validTarget(target) && getBoundingBox().inflate(1.4D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition())) {
            damage(level, target, damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.35D + momentum() * .12D)), true);
            RoyalVfx.emit(this, RoyalVfx.STRIKE, null, target.getBoundingBox().getCenter(), target.position().subtract(position()).normalize(), 20, 4); sound(SoundEvents.PHANTOM_BITE, 1, .55F);
        }
        strikeTarget = null;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity enemy) || !enabled() || meleeTicks > 0 || actionTicks > 0 || ramTicks > 0 || !validTarget(enemy)) { return false; }
        if (strike(level, enemy)) { return true; }
        if (!getBoundingBox().inflate(.8D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition()) || damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE), true) <= 0) { return false; }
        meleeTicks = 32; action("strike", 12); RoyalVfx.emit(this, RoyalVfx.IMPACT, null, enemy.position(), Vec3.ZERO, 16, 2); return true;
    }
    public boolean safeStep(Vec3 step) {
        for (Entity member : getSelfAndPassengers().limit(9).toList()) {
            AABB box = member.getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noBlockCollision(member, box) || level().containsAnyLiquid(box)) { return false; }
            if (level() instanceof ServerLevel server) { for (BlockPos point : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous(server, point)) { return false; } } }
        }
        return true;
    }
    public boolean ram(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || actionTicks > 0 || ramTicks > 0 || !validTarget(target) || distanceToSqr(target) > 24 * 24) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
        if ((!line(getEyePosition(), target.getEyePosition()) && !RoyalRamBreaking.canAttempt(level, this, direction)) || !safeStep(direction.scale(.2D)) && !RoyalRamBreaking.canAttempt(level, this, direction)) { return false; }
        ramTarget = target.getUUID(); ramWindup = 8; ramTicks = 26; ramMomentum = momentum(); ramHit = false; ramBreakAttempted = false; ramBreakingSpent = false; meleeTicks = 32; cooldowns[0] = INTERVALS[0]; action("ram", 30);
        ramRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); entityData.set(RAMMING, true); syncRamRider(false);
        RoyalVfx.emit(this, RoyalVfx.RAM, this, position(), direction, 30, 3); sound(SoundEvents.PHANTOM_SWOOP, 1, .5F); return true;
    }
    public boolean claimRamBreaking() { if (ramTicks <= 0 || ramWindup > 0 || ramBreakingSpent || ramMomentum < 2) { return false; } ramBreakingSpent = true; return true; }
    private void tickRam(ServerLevel level) {
        if (ramTicks <= 0) { return; }
        if (ramRider != null && (getControllingPassenger() == null || !ramRider.equals(getControllingPassenger().getUUID()))
                || !(level.getEntity(ramTarget) instanceof LivingEntity target) || !validTarget(target) || distanceToSqr(target) > 28 * 28) { endRam(); return; }
        ramTicks--; if (ramWindup > 0) { ramWindup--; setDeltaMovement(Vec3.ZERO); return; }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(1.15D + ramMomentum * .08D);
        for (int probe = 0; probe < 4; probe++) {
            if (getBoundingBox().inflate(.6D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition())) {
                if (!ramHit && damage(level, target, damageSources().mobAttack(this), 28 * (1 + ramMomentum * .04F), true) > 0) { ramHit = true; RoyalTactics.push(this, target, .8D); RoyalVfx.emit(this, RoyalVfx.RAM_IMPACT, null, target.position(), step.normalize(), 24, 7); }
                endRam(); return;
            }
            if (!safeStep(step.scale(.25D)) && !ramBreakAttempted && ramMomentum >= 2) { ramBreakAttempted = true; RoyalRamBreaking.clearOnce(level, this, step.normalize()); }
            if (!safeStep(step.scale(.25D))) { endRam(); return; }
            move(MoverType.SELF, step.scale(.25D)); setDeltaMovement(Vec3.ZERO); needsSync = true; for (Entity passenger : getPassengers()) { positionRider(passenger); }
        }
        if (ramTicks == 0) { endRam(); }
    }
    private void syncRamRider(boolean release) {
        if (level() instanceof ServerLevel && getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer rider && rider.connection != null) {
            rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(RAMMING, entityData.get(RAMMING)))));
            if (release) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
        }
    }
    private void endRam() {
        boolean active = entityData.get(RAMMING); ramTicks = 0; ramWindup = 0; ramTarget = null; ramRider = null; entityData.set(RAMMING, false);
        if (active) { setDeltaMovement(Vec3.ZERO); needsSync = true; syncRamRider(true); }
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = aimed(level, rider, 24); return target != null && (getBoundingBox().inflate(1.4D).intersects(target.getBoundingBox()) ? strike(level, target) : ram(level, target));
    }
    private LivingEntity aimed(ServerLevel level, Player rider, double range) { return enemies(level, position(), range, 24).stream().filter(target -> rider.getViewVector(1).dot(target.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).findFirst().orElse(null); }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return INTERVALS[1]; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { LivingEntity target = aimed(level, rider, 32); return target != null && judgment(level, target); }
    public boolean judgment(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || !validTarget(target) || distanceToSqr(target) > 32 * 32 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        judgmentTarget = target.getUUID(); judgmentTicks = 120; cooldowns[1] = INTERVALS[1]; action("judgment", 20);
        RoyalTactics.coordinate(this, target, 100, false); RoyalVfx.emit(this, RoyalVfx.JUDGMENT, target, target.position(), Vec3.ZERO, 120, 2); return true;
    }
    public boolean judges(LivingEntity target) { return target != null && enabled() && judgmentTicks > 0 && target.getUUID().equals(judgmentTarget) && safeEnemy(target) && distanceToSqr(target) <= 40 * 40; }
    public boolean roar(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || enemies(level, position(), 16, 3).size() < 2) { return false; }
        boolean changed = false; for (LivingEntity target : enemies(level, position(), 16, 20)) { changed |= RoyalTactics.disrupt(this, target, 30); RoyalTactics.push(this, target, .5D); }
        if (!changed) { return false; }
        cooldowns[3] = INTERVALS[3]; action("roar", 24); RoyalTactics.rally(this, 80); RoyalVfx.emit(this, RoyalVfx.ROAR, null, position(), Vec3.ZERO, 36, 16); sound(SoundEvents.ENDER_DRAGON_GROWL, .9F, .65F); return true;
    }
    public boolean command(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[4] > 0 || !validTarget(target) || distanceToSqr(target) > 32 * 32 || RoyalTactics.coordinate(this, target, 120, true) == 0) { return false; }
        cooldowns[4] = INTERVALS[4]; action("command", 18); RoyalVfx.emit(this, RoyalVfx.COMMAND, this, position(), Vec3.ZERO, 32, 24); return true;
    }
    public boolean guard(ServerLevel level, LivingEntity victim, LivingEntity attacker, float amount) {
        if (!enabled() || cooldowns[5] > 0 || guardTicks > 0 || !crew(victim) || !validTarget(attacker) || distanceToSqr(victim) > 12 * 12 || !Float.isFinite(amount) || amount < 4) { return false; }
        guardTicks = 80; cooldowns[5] = INTERVALS[5]; action("guard", 16); RoyalVfx.emit(this, RoyalVfx.GUARD, this, position(), Vec3.ZERO, 80, 5); return true;
    }
    public float protection(LivingEntity victim, LivingEntity attacker) {
        if (!enabled() || !crew(victim) || !validTarget(attacker) || !line(getEyePosition(), victim.getEyePosition())) { return 0; }
        if (guardTicks > 0 && distanceToSqr(victim) <= 12 * 12) { return .25F; }
        if (dominionTicks > 0 && victim == this) { return .2F; }
        return distanceToSqr(victim) <= 24 * 24 ? .08F : 0;
    }
    public boolean intercept(ServerLevel level, LivingEntity victim, LivingEntity attacker) {
        if (!enabled() || isVehicle() || cooldowns[6] > 0 || !crew(victim) || !validTarget(attacker) || distanceToSqr(victim) > 20 * 20
                || attacker.distanceToSqr(victim) > 12 * 12 || orderedId != null && !attacker.getUUID().equals(orderedId)
                || victim != getOwner() && victim.getHealth() > victim.getMaxHealth() * .35F || !recent(attacker, victim)) { return false; }
        Vec3 offset = attacker.position().subtract(victim.position()).multiply(1, 0, 1).normalize(), destination = victim.position().add(offset.scale(3)).add(0, 1, 0);
        Vec3 step = destination.subtract(position()); if (!safeStep(step.lengthSqr() > 4 ? step.normalize().scale(2) : step)) { return false; }
        if (!RoyalTactics.claim(this, victim)) { return false; }
        cooldowns[6] = INTERVALS[6]; if (getTarget() == null || orderedId == null) { setTarget(attacker); }
        getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.6D); guard(level, victim, attacker, 4);
        action("intercept", 14); RoyalVfx.emit(this, RoyalVfx.INTERCEPT, this, position(), destination.subtract(position()), 24, 4); return true;
    }
    public boolean dominion(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || dominionTicks > 0 || actionTicks > 0 || !validTarget(getTarget())) { return false; }
        List<LivingEntity> crowd = enemies(level, position(), DOMINION_RADIUS, 32); if (crowd.size() < 3) { return false; }
        dominionCenter = position(); dominionTicks = DOMINION_DURATION; cooldowns[7] = INTERVALS[7]; entityData.set(DOMINION, true); marked.clear();
        for (LivingEntity target : crowd) { mark(target); }
        if (!isVehicle() && safeStep(new Vec3(0, 1.2D, 0))) { move(MoverType.SELF, new Vec3(0, 1.2D, 0)); needsSync = true; }
        action("dominion", 36); RoyalTactics.rally(this, 100); RoyalVfx.emit(this, RoyalVfx.DOMINION, null, dominionCenter, Vec3.ZERO, DOMINION_DURATION, DOMINION_RADIUS); sound(SoundEvents.ENDER_DRAGON_GROWL, 1, .55F); return true;
    }
    private void mark(LivingEntity target) { if (marked.size() < 32 && marked.add(target.getUUID())) { RoyalVfx.emit(this, RoyalVfx.MARK, target, target.position(), Vec3.ZERO, Math.max(1, dominionTicks), 2); } }
    private void tickDominion(ServerLevel level) {
        if (dominionTicks <= 0 || dominionCenter == null) { return; }
        if (distanceToSqr(dominionCenter) > 64 * 64) { endCombat(); return; }
        int age = DOMINION_DURATION - --dominionTicks;
        if (age >= 40 && age < 276 && age % 20 == 0) {
            List<LivingEntity> crowd = enemies(level, dominionCenter, DOMINION_RADIUS, 32); for (LivingEntity target : crowd) { mark(target); }
            LivingEntity priority = crowd.stream().findFirst().orElse(null); if (priority != null) { RoyalTactics.coordinate(this, priority, 45, age % 80 == 40); }
            if (age % 60 == 40) { RoyalTactics.rally(this, 65); action("rally", 16); }
        }
        if (dominionTicks == 0) {
            for (LivingEntity target : enemies(level, dominionCenter, DOMINION_RADIUS, 32)) { if (marked.contains(target.getUUID())) { damage(level, target, damageSources().mobAttack(this), 24 + momentum() * 2, false); RoyalTactics.disrupt(this, target, 20); } }
            RoyalTactics.releaseDirectives(this); entityData.set(DOMINION, false); action("finale", 28); RoyalVfx.emit(this, RoyalVfx.FINALE, null, dominionCenter, Vec3.ZERO, 36, DOMINION_RADIUS); sound(SoundEvents.IRON_GOLEM_ATTACK, 1, .55F); dominionCenter = null; marked.clear();
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (actionTicks > 0 || ramTicks > 0 || strikeTicks > 0) { return true; }
        if (dominion(level) || roar(level)) { return true; }
        if (getBoundingBox().inflate(1.4D).intersects(target.getBoundingBox()) && strike(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        UUID currentOwner = !isTame() || getOwner() == null ? null : getOwner().getUUID();
        if (ownerContext != null && !ownerContext.equals(currentOwner)) { endCombat(); }
        ownerContext = currentOwner;
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (actionTicks > 0) { actionTicks--; } if (meleeTicks > 0) { meleeTicks--; } if (gainDelay > 0) { gainDelay--; } if (guardTicks > 0) { guardTicks--; }
        if (judgmentTicks > 0 && --judgmentTicks == 0) { judgmentTarget = null; }
        if (enabled() && validTarget(getTarget()) && distanceToSqr(getTarget()) <= 64 * 64) { idleTicks = 0; } else if (++idleTicks >= 100 && (idleTicks - 100) % 60 == 0) { momentum(momentum() - 1); }
        if (!enabled()) { if (visualOpen || dominionTicks > 0) { endCombat(); } return; }
        if (territory == null) { territory = position(); }
        if (!isTame() && tickCount % 10 == 0) {
            Player intruder = level.players().stream().filter(player -> safeEnemy(player) && distanceToSqr(player) < 14 * 14 && player.distanceToSqr(territory) < 24 * 24).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (intruder == null) { warnedPlayer = null; warningTicks = 0; if (getTarget() instanceof Player && orderedId == null && !recent(getTarget(), this)) { clearCombatTarget(false); } }
            else if (!intruder.getUUID().equals(warnedPlayer)) { warnedPlayer = intruder.getUUID(); warningTicks = 0; RoyalVfx.emit(this, RoyalVfx.WARNING, this, position(), Vec3.ZERO, 32, 5); sound(SoundEvents.PHANTOM_AMBIENT, .9F, .5F); }
            else if ((warningTicks += 10) >= 80 && validTarget(intruder)) { setTarget(intruder); }
        }
        tickStrike(level); tickRam(level); tickDominion(level);
        if (tickCount % 20 == 0) {
            RoyalTactics.maintain(); LivingEntity target = getTarget();
            if (validTarget(target)) {
                RoyalTactics.coordinate(this, target, 45, false);
                if (actionTicks == 0) { if (!isVehicle() && !dominion(level) && !roar(level) && distanceToSqr(target) > 16) { ram(level, target); } }
                if (actionTicks == 0 && !judgment(level, target)) { command(level, target); }
                RoyalVfx.emit(this, RoyalVfx.PRESENCE, this, position(), Vec3.ZERO, 28, 3 + momentum());
            } else if (tickCount % 80 == 0 && !allies(24).isEmpty()) { RoyalVfx.emit(this, RoyalVfx.PRESENCE, this, position(), Vec3.ZERO, 36, 3); }
            if (getOwner() instanceof LivingEntity owner && owner.getLastHurtByMob() instanceof LivingEntity attacker) { intercept(level, owner, attacker); }
            for (AbstractPhantom ally : allies(20)) { if (ally.getHealth() <= ally.getMaxHealth() * .35F && ally.getLastHurtByMob() instanceof LivingEntity attacker && intercept(level, ally, attacker)) { break; } }
        }
    }
    private void sound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) { if (level() instanceof ServerLevel level) { level.playSound(null, blockPosition(), sound, SoundSource.NEUTRAL, volume, pitch); } }
    public void action(String clip, int duration) { actionTicks = Math.max(actionTicks, duration); visualStarted(); triggerAnim("royal", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) { super.registerControllers(registrar); var controller = new AnimationController<PhantomKing>("royal", 2, state -> actionTicks > 0 ? PlayState.CONTINUE : PlayState.STOP); for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.king." + clip)); } registrar.add(controller.additiveAnimations()); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); warnedPlayer = null; warningTicks = 0; level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endCombat() {
        if (visualOpen && level() instanceof ServerLevel) { RoyalVfx.cancel(this); } RoyalTactics.release(this); session = UUID.randomUUID(); visualOpen = false; dominionTicks = 0; dominionCenter = null;
        endRam(); strikeTicks = 0; strikeTarget = null; guardTicks = 0; judgmentTicks = 0; judgmentTarget = null; actionTicks = 0; orderedId = null; marked.clear(); entityData.set(DOMINION, false);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endCombat(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endCombat(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endCombat(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("RoyalMomentum", momentum()); output.putInt("RoyalIdle", idleTicks); for (int index = 0; index < cooldowns.length; index++) { output.putInt("RoyalCooldown" + index, cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endCombat(); momentum(input.getIntOr("RoyalMomentum", 0)); idleTicks = Math.clamp(input.getIntOr("RoyalIdle", 0), 0, 280); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("RoyalCooldown" + index, 0), 0, INTERVALS[index]); } warnedPlayer = null; warningTicks = 0; territory = null; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { List<CooldownDiscipline.Slot> slots = new ArrayList<>(); for (int index = 0; index < 7; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("royal_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(slots); }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.ENCHANTED_GOLDEN_APPLE, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Ram: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Judgment: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Strike: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Roar: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Command: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Intercept: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Dominion: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "ram" -> cooldowns[0];
            case "judgment" -> cooldowns[1];
            case "roar" -> cooldowns[3];
            case "command" -> cooldowns[4];
            case "dominion" -> cooldowns[7];
            default -> -1;
        };
    }
}
