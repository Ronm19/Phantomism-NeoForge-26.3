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
import net.minecraft.sounds.SoundEvents;
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
import net.ronm19.phantomism.entity.spawn.EclipseEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.EclipseVfx;

public final class EclipsePhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public enum Stance { SOLAR, LUNAR }
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/eclipse_phantom");
    public static final int TOTAL_DURATION = 300, TOTAL_RADIUS = 20, TOTAL_COOLDOWN = 2400;
    private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(EclipsePhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOMENTUM = SynchedEntityData.defineId(EclipsePhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DASHING = SynchedEntityData.defineId(EclipsePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TOTAL = SynchedEntityData.defineId(EclipsePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS = {440, 360, 100, 240, 200, 200, 600, TOTAL_COOLDOWN};
    private static final String[] CLIPS = {"solar_guard", "solar_pulse", "lunar_fang", "lunar_rush", "bolt", "hybrid_rend", "hybrid_guard", "alignment", "sovereignty", "judgment", "shift"};
    private final int[] cooldowns = new int[8];
    private UUID session = UUID.randomUUID(), orderedId, rushId, rushRider, ownerContext;
    private int forcedTicks, forcedCooldown, decisionTicks, gainTicks, decayTicks, mountedBoltTicks, meleeTicks, rushTicks, totalTicks;
    private Vec3 totalCenter;
    private boolean initialized, visualOpen;

    public EclipsePhantom(EntityType<? extends EclipsePhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 104).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .65D).add(Attributes.FOLLOW_RANGE, 56)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .475D);
    }
    public static boolean checkSpawnRules(EntityType<EclipsePhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return EclipseEncounterSpawner.habitat(accessor.getLevel(), position) && accessor.getFluidState(position).isEmpty()
                && accessor.getBlockState(position).getCollisionShape(accessor, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ECLIPSE_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return stance() == Stance.LUNAR ? 30 : 34; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean prefersRangedCombat() { return false; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(STANCE, 0); data.define(MOMENTUM, 0); data.define(DASHING, false); data.define(TOTAL, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(DASHING) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(DASHING) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(DASHING)) { setDeltaMovement(Vec3.ZERO); } else if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    @Override public boolean isUltimateAnimationActive() { return entityData.get(TOTAL) || super.isUltimateAnimationActive(); }
    public Stance stance() { return entityData.get(STANCE) == 1 ? Stance.LUNAR : Stance.SOLAR; }
    public int momentum() { return Math.clamp(entityData.get(MOMENTUM), 0, 3); }
    public int visualState() { return entityData.get(TOTAL) ? 2 : stance().ordinal(); }
    public int totalTicks() { return totalTicks; }
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
                || isAlliedTo(target) && !(target instanceof AbstractPhantom) || isTame() && target instanceof OwnableEntity pet
                && Objects.equals(getOwnerReference(), pet.getOwnerReference()));
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
    public boolean threatening(LivingEntity target) {
        if (recent(target, this) || recent(target, getOwner()) || target == getTarget()) { return true; }
        return target instanceof Mob mob && recipient(mob.getTarget()) && !PhantomDuels.pair(mob.getTarget(), target);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean explicit = target.getUUID().equals(orderedId) || recent(target, this) || recent(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof OwnableEntity owned && owned.getOwnerReference() != null || target instanceof Player || target instanceof AbstractPhantom) { return explicit; }
        return explicit || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && threatening(target); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (threatening(target) ? -200 : 0) + (stance() == Stance.LUNAR ? target.getHealth() / target.getMaxHealth() * 40 : 0) + distanceToSqr(target); }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target != null && !recipient(target) && !PhantomDuels.excludesObserver(this, target) && PhantomDuels.begin(this, target) && safeEnemy(target)) { orderedId = target.getUUID(); super.setCombatTarget(target); }
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public boolean safeStep(Vec3 step) {
        if (!Double.isFinite(step.lengthSqr()) || step.lengthSqr() > 1) { return false; }
        AABB box = getBoundingBox().expandTowards(step).inflate(.03D);
        for (Entity passenger : getPassengers()) { box = box.minmax(passenger.getBoundingBox().expandTowards(step).inflate(.03D)); }
        return isLoadedFlightBox(box) && level().getWorldBorder().isWithinBounds(box) && level().noBlockCollision(this, box) && !level().containsAnyLiquid(box);
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int maximum) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();
    }
    public List<LivingEntity> recipients(ServerLevel level, Vec3 center, double radius) {
        var targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::recipient));
        if (!targets.contains(this) && distanceToSqr(center) <= radius * radius) { targets.add(this); }
        return targets.stream().filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition())).sorted(Comparator.comparingDouble(target -> target.getHealth() / target.getMaxHealth())).limit(12).toList();
    }
    private boolean available(Stance desired) { return stance() == desired || totalTicks > 0; }
    private void gain() { if (gainTicks == 0) { entityData.set(MOMENTUM, Math.min(3, momentum() + 1)); gainTicks = 40; decayTicks = 200; } }
    private int consume() { int stacks = momentum(); entityData.set(MOMENTUM, 0); return stacks; }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, source, amount)) { return 0; }
        float dealt = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount()); if (dealt > 0) { gain(); } return dealt;
    }
    public boolean shift(Stance next, boolean forced) {
        if (!(level() instanceof ServerLevel) || !isAlive() || isBaby() || forced && forcedCooldown > 0 || !forced && (forcedTicks > 0 || decisionTicks > 0)) { return false; }
        if (forced) { forcedTicks = 400; forcedCooldown = 800; initialized = true; }
        if (stance() == next) { return forced; }
        entityData.set(STANCE, next.ordinal()); initialized = true; decisionTicks = 100;
        if (validTarget(getTarget()) && decayTicks > 0 && gainTicks == 0) { gain(); }
        action("shift"); EclipseVfx.emit(this, EclipseVfx.SHIFT, this, position(), Vec3.ZERO, 22, 2.5F); return true;
    }
    public boolean commandStance(Player owner, Stance next) { return isOwnedBy(owner) && owner.level() == level() && distanceToSqr(owner) <= 80 * 80 && shift(next, true); }
    public boolean solarGuard(ServerLevel level) {
        if (!enabled() || !available(Stance.SOLAR) || cooldowns[0] > 0) { return false; }
        int stacks = momentum(); boolean made = false;
        for (LivingEntity ally : recipients(level, position(), 12)) { made |= EclipseSupport.shield(this, ally, 100, .25F + stacks * .025F); }
        if (!made) { return false; } consume(); cooldowns[0] = INTERVALS[0]; action("solar_guard"); EclipseVfx.emit(this, EclipseVfx.GUARD, this, position(), Vec3.ZERO, 100, 12); return true;
    }
    public boolean solarPulse(ServerLevel level) {
        if (!enabled() || !available(Stance.SOLAR) || cooldowns[1] > 0) { return false; }
        cooldowns[1] = INTERVALS[1];
        for (LivingEntity ally : recipients(level, position(), 12)) { EclipseSupport.heal(this, ally, 3, 60); }
        for (LivingEntity enemy : enemies(level, position(), 12, 12)) { damage(level, enemy, damageSources().mobAttack(this), 4); EclipseSupport.push(this, enemy, enemy.position().subtract(position()), .35D); }
        action("solar_pulse"); EclipseVfx.emit(this, EclipseVfx.PULSE, null, position(), Vec3.ZERO, 26, 12); return true;
    }
    public boolean lunarFang(ServerLevel level, LivingEntity target) {
        if (!enabled() || !available(Stance.LUNAR) || cooldowns[2] > 0 || !validTarget(target) || !getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox()) || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (damage(level, target, damageSources().mobAttack(this), 15) <= 0) { return false; }
        cooldowns[2] = INTERVALS[2]; meleeTicks = 30; action("lunar_fang"); EclipseVfx.emit(this, EclipseVfx.FANG, null, target.position(), Vec3.ZERO, 18, 2.5F); return true;
    }
    public boolean lunarRush(ServerLevel level, LivingEntity target) {
        if (!enabled() || !available(Stance.LUNAR) || cooldowns[3] > 0 || rushTicks > 0 || !validTarget(target) || distanceToSqr(target) > 14 * 14
                || !line(getEyePosition(), target.getEyePosition()) || !safeStep(target.position().subtract(position()).normalize().scale(.2D))) { return false; }
        rushId = target.getUUID(); rushRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); rushTicks = 22; cooldowns[3] = INTERVALS[3];
        entityData.set(DASHING, true); action("lunar_rush"); syncRider(false); EclipseVfx.emit(this, EclipseVfx.RUSH, this, position(), target.position().subtract(position()).normalize(), 24, 2); return true;
    }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; }
        if (--rushTicks == 0 || rushRider != null && (getControllingPassenger() == null || !rushRider.equals(getControllingPassenger().getUUID()))
                || !(level.getEntity(rushId) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { endRush(); return; }
        if (getBoundingBox().inflate(.6D).intersects(target.getBoundingBox())) { damage(level, target, damageSources().mobAttack(this), 16); meleeTicks = 30; EclipseVfx.emit(this, EclipseVfx.FANG, null, target.position(), Vec3.ZERO, 18, 2); endRush(); return; }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.75D);
        if (!safeStep(step)) { endRush(); return; } move(MoverType.SELF, step); setDeltaMovement(Vec3.ZERO); syncRider(false);
    }
    private void syncRider(boolean finish) {
        if (!(level() instanceof ServerLevel) || !(getControllingPassenger() instanceof ServerPlayer rider) || rider.connection == null) { return; }
        positionRider(rider); needsSync = true; rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(DASHING, entityData.get(DASHING)))));
        if (finish) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
    }
    private void endRush() { boolean active = entityData.get(DASHING); rushTicks = 0; rushId = null; rushRider = null; entityData.set(DASHING, false); if (active) { setDeltaMovement(Vec3.ZERO); syncRider(true); } }
    public boolean bolt(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || (mounted ? mountedBoltTicks > 0 || !(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider) : cooldowns[4] > 0)
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.6D)); if (!line(getEyePosition(), muzzle)) { return false; }
        EclipseBolt shot = PhantomismContent.ECLIPSE_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session, stance()); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; } if (mounted) { mountedBoltTicks = 6; } else { cooldowns[4] = INTERVALS[4]; }
        action("bolt"); EclipseVfx.emit(this, EclipseVfx.BOLT, shot, muzzle, direction, 32, .65F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return bolt(level, rider.getViewVector(1), true); }
    public boolean hybridRend(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[5] > 0 || !validTarget(target) || !getBoundingBox().inflate(2).intersects(target.getBoundingBox()) || !line(getEyePosition(), target.getEyePosition())) { return false; }
        int stacks = momentum(); float base = stance() == Stance.LUNAR || totalTicks > 0 ? 18 : 16;
        if (damage(level, target, damageSources().mobAttack(this), base + stacks * (totalTicks > 0 ? 1.5F : 1)) <= 0) { return false; }
        consume(); cooldowns[5] = INTERVALS[5]; meleeTicks = getMeleeAttackCooldownTicks();
        if (available(Stance.SOLAR)) { EclipseSupport.shield(this, this, 40, .2F); }
        action("hybrid_rend"); EclipseVfx.emit(this, EclipseVfx.REND, null, target.position(), Vec3.ZERO, 22, 3); return true;
    }
    public boolean activateRiddenRend(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 4, 16).stream().filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .4D).findFirst().orElse(null);
        return hybridRend(level, target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.55D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition())
                || damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0) { return false; }
        meleeTicks = getMeleeAttackCooldownTicks(); action(stance() == Stance.SOLAR ? "hybrid_rend" : "lunar_fang"); return true;
    }
    public boolean hybridGuard(ServerLevel level, LivingEntity ward, LivingEntity attacker, float incoming) {
        if (!enabled() || cooldowns[6] > 0 || !recipient(ward) || !validTarget(attacker) || distanceToSqr(ward) > 12 * 12 || PhantomDuels.pair(ward, attacker)
                || !line(getEyePosition(), ward.getEyePosition()) || !Float.isFinite(incoming) || incoming < 4 && ward.getHealth() - incoming > ward.getMaxHealth() * .3F) { return false; }
        if (!EclipseSupport.shield(this, ward, 60, available(Stance.SOLAR) ? .4F : .25F)) { return false; }
        cooldowns[6] = INTERVALS[6];
        if (available(Stance.LUNAR)) { EclipseSupport.push(this, attacker, attacker.position().subtract(ward.position()), .3D); }
        action("hybrid_guard"); EclipseVfx.emit(this, EclipseVfx.HYBRID_GUARD, ward, ward.position(), Vec3.ZERO, 60, 2); return true;
    }
    public boolean totalEclipse(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || totalTicks > 0 || EclipseSupport.domainNearby(this)) { return false; }
        cooldowns[7] = TOTAL_COOLDOWN; totalTicks = TOTAL_DURATION; totalCenter = position(); entityData.set(TOTAL, true);
        for (LivingEntity ally : recipients(level, totalCenter, TOTAL_RADIUS)) { EclipseSupport.shield(this, ally, 60, .35F); }
        action("alignment"); EclipseVfx.emit(this, EclipseVfx.TOTAL, null, totalCenter, Vec3.ZERO, TOTAL_DURATION, TOTAL_RADIUS); return true;
    }
    private void tickTotal(ServerLevel level) {
        if (totalTicks <= 0) { return; }
        if (totalCenter == null || distanceToSqr(totalCenter) > 24 * 24) { endEffects(); return; }
        int elapsed = TOTAL_DURATION - --totalTicks;
        if (elapsed == 40) { action("sovereignty"); }
        if (elapsed >= 40 && elapsed < 270 && elapsed % 60 == 0) {
            for (LivingEntity ally : recipients(level, totalCenter, TOTAL_RADIUS)) { EclipseSupport.heal(this, ally, 2, 60); EclipseSupport.shield(this, ally, 65, .25F); }
            for (LivingEntity enemy : enemies(level, totalCenter, TOTAL_RADIUS, 12)) { damage(level, enemy, damageSources().mobAttack(this), 4); EclipseSupport.push(this, enemy, totalCenter.subtract(enemy.position()), .2D); }
        }
        if (elapsed == 276) {
            for (LivingEntity enemy : enemies(level, totalCenter, TOTAL_RADIUS, 16)) { damage(level, enemy, damageSources().mobAttack(this), 16); EclipseSupport.push(this, enemy, enemy.position().subtract(totalCenter), .35D); }
            for (LivingEntity ally : recipients(level, totalCenter, TOTAL_RADIUS)) { EclipseSupport.heal(this, ally, 4, 60); EclipseSupport.shield(this, ally, 24, .35F); }
            action("judgment"); EclipseVfx.emit(this, EclipseVfx.JUDGMENT, null, totalCenter, Vec3.ZERO, 24, TOTAL_RADIUS);
        }
        if (totalTicks == 0) { entityData.set(TOTAL, false); totalCenter = null; }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || rushTicks > 0) { return true; }
        if (hybridRend(level, target) || lunarFang(level, target)) { return true; }
        if (distanceToSqr(target) > 9 && lunarRush(level, target)) { return true; }
        return doHurtTarget(level, target);
    }
    public void decide(ServerLevel level) {
        if (!enabled() || forcedTicks > 0 || decisionTicks > 0) { return; }
        boolean endangered = endangeredOwner() || recipients(level, position(), 16).stream().anyMatch(ally -> ally.getHealth() < ally.getMaxHealth() * .65F);
        Stance desired = endangered ? Stance.SOLAR : validTarget(getTarget()) ? Stance.LUNAR : level.isBrightOutside() ? Stance.SOLAR : Stance.LUNAR;
        if (stance() != desired) { shift(desired, false); } else { decisionTicks = 40; }
    }
    private boolean endangeredOwner() { return getOwner() instanceof LivingEntity owner && recipient(owner) && distanceToSqr(owner) <= 56 * 56 && owner.getHealth() < owner.getMaxHealth() * .65F; }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (forcedTicks > 0) { forcedTicks--; } if (forcedCooldown > 0) { forcedCooldown--; } if (decisionTicks > 0) { decisionTicks--; }
        if (gainTicks > 0) { gainTicks--; } if (mountedBoltTicks > 0) { mountedBoltTicks--; } if (meleeTicks > 0) { meleeTicks--; }
        if (decayTicks > 0) { decayTicks--; } else if (momentum() > 0) { entityData.set(MOMENTUM, momentum() - 1); decayTicks = 100; }
        if (!initialized) { entityData.set(STANCE, level.isBrightOutside() ? 0 : 1); initialized = true; }
        UUID owner = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !owner.equals(ownerContext)) { endEffects(); forcedTicks = 0; } ownerContext = owner;
        if (!enabled()) { endEffects(); return; }
        tickRush(level); tickTotal(level);
        if (tickCount % 100 == 0 && available(Stance.SOLAR)) { for (LivingEntity ally : recipients(level, position(), 10)) { EclipseSupport.heal(this, ally, 1, 100); } }
        if (!isNoAi() && tickCount % 10 == 0) {
            List<LivingEntity> threats = enemies(level, position(), 24, 24).stream().filter(this::threatening).toList();
            if (!validTarget(getTarget()) && !threats.isEmpty()) { setTarget(threats.getFirst()); }
            decide(level);
            if (endangeredOwner() && available(Stance.SOLAR) && !isVehicle() && distanceToSqr(getOwner()) > 10 * 10) { endRush(); flyToAssignment(getOwner().position().add(0, 2, 0), 1); }
            boolean endangered = recipients(level, position(), 12).stream().anyMatch(ally -> ally.getHealth() < ally.getMaxHealth() * .65F);
            if (endangered && !threats.isEmpty()) { solarGuard(level); solarPulse(level); }
            if (threats.size() >= 4 || endangered && threats.size() >= 2) { totalEclipse(level); }
            LivingEntity target = getTarget();
            if (!isVehicle() && validTarget(target)) {
                if (rushTicks == 0 && distanceToSqr(target) < 14 * 14) { if (!hybridRend(level, target) && !lunarFang(level, target) && distanceToSqr(target) > 9) { lunarRush(level, target); } }
                if (distanceToSqr(target) <= EclipseBolt.MAX_TRAVEL_DISTANCE * EclipseBolt.MAX_TRAVEL_DISTANCE && line(getEyePosition(), target.getEyePosition())) { bolt(level, target.getEyePosition().subtract(getEyePosition()), false); }
            }
        }
    }
    private void action(String clip) { visualOpen = true; triggerAnim("eclipse", clip); playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .4F, stance() == Stance.SOLAR ? 1.4F : .85F); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<EclipsePhantom>("eclipse_stance", 8, state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.eclipse." + (stance() == Stance.SOLAR ? "solar" : "lunar")))).additiveAnimations());
        var controller = new AnimationController<EclipsePhantom>("eclipse", 3, state -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.eclipse." + clip)); } controllers.add(controller);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte) 7); }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endEffects() {
        if (level() instanceof ServerLevel) { EclipseSupport.release(this); if (visualOpen) { EclipseVfx.cancel(this); visualOpen = false; session = UUID.randomUUID(); } }
        endRush(); totalTicks = 0; totalCenter = null; entityData.set(TOTAL, false); orderedId = null;
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endEffects(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endEffects(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endEffects(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endEffects(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("EclipseCooldown" + index, cooldowns[index]); }
        output.putInt("EclipseStance", stance().ordinal()); output.putInt("EclipseMomentum", momentum()); output.putInt("EclipseForcedTicks", forcedTicks); output.putInt("EclipseForcedCooldown", forcedCooldown);
        output.putInt("EclipseDecision", decisionTicks); output.putInt("EclipseGain", gainTicks); output.putInt("EclipseDecay", decayTicks); output.putInt("EclipseMountedBolt", mountedBoltTicks); output.putInt("EclipseMelee", meleeTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endEffects(); session = UUID.randomUUID(); initialized = true;
        for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("EclipseCooldown" + index, 0), 0, INTERVALS[index]); }
        entityData.set(STANCE, Math.clamp(input.getIntOr("EclipseStance", 0), 0, 1)); entityData.set(MOMENTUM, Math.clamp(input.getIntOr("EclipseMomentum", 0), 0, 3));
        forcedTicks = Math.clamp(input.getIntOr("EclipseForcedTicks", 0), 0, 400); forcedCooldown = Math.clamp(input.getIntOr("EclipseForcedCooldown", 0), 0, 800);
        decisionTicks = Math.clamp(input.getIntOr("EclipseDecision", 0), 0, 100); gainTicks = Math.clamp(input.getIntOr("EclipseGain", 0), 0, 40); decayTicks = Math.clamp(input.getIntOr("EclipseDecay", 0), 0, 200);
        mountedBoltTicks = Math.clamp(input.getIntOr("EclipseMountedBolt", 0), 0, 6); meleeTicks = Math.clamp(input.getIntOr("EclipseMelee", 0), 0, 34);
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index = 0; index < 6; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("eclipse_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.GOLDEN_APPLE, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Solar Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Solar Pulse: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Lunar Fang: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Lunar Rush: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Bolt: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Hybrid Rend: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Hybrid Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Alignment: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "solar_pulse" -> cooldowns[1];
            case "lunar_fang" -> cooldowns[2];
            case "bolt" -> cooldowns[4];
            case "hybrid_rend" -> cooldowns[5];
            case "total_eclipse" -> cooldowns[7];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[4] = ticks; }
}
