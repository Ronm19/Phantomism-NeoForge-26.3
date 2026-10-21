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
import net.minecraft.world.effect.*;
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
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.DarkVfx;

public final class DarkPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/dark_phantom");
    public static final int BOLT_RANGE = 42, MOUNTED_COOLDOWN = 6, HOUR_DURATION = 200, HOUR_RADIUS = 24;
    private static final int[] INTERVALS = {200, 100, 200, 320, 500, 600, 1800};
    private static final String[] CLIPS = {"bolt", "rend", "rush", "blackout", "bubble", "shield", "hour", "hunt", "finale"};
    private static final EntityDataAccessor<Byte> PRESSURE = SynchedEntityData.defineId(DarkPhantom.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(DarkPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[7];
    private UUID session = UUID.randomUUID(), orderedId, warnedPlayer, failedTamer, rushTarget;
    private int actionTicks, meleeTicks, rendTicks, rushTicks, bubbleTicks, shieldTicks, hourTicks, hourPressure;
    private int successfulAttacks, gainDelay, idleTicks, warningTicks, launchedBlades;
    private float shieldPool;
    private long failedUntil;
    private boolean visualOpen;
    private Vec3 rendDirection = Vec3.ZERO, hourCenter, territory;
    private final Map<UUID, Integer> bladeAssignments = new HashMap<>();
    private final Set<UUID> marked = new HashSet<>(), rushHits = new HashSet<>();

    public DarkPhantom(EntityType<? extends DarkPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 96).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .55D).add(Attributes.FOLLOW_RANGE, 56)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .475D);
    }
    public static boolean night(ServerLevel level) { long time = Math.floorMod(level.getOverworldClockTime(), 24000L); return time >= 13000 && time < 23000; }
    public static boolean checkSpawnRules(EntityType<DarkPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        ServerLevel level = accessor.getLevel();
        return level.dimension() == Level.OVERWORLD && level.hasChunkAt(position) && level.getDifficulty() != Difficulty.PEACEFUL && night(level)
                && level.getBiome(position).is(ModTags.Biomes.DARK_PHANTOM_SPAWNS) && level.getFluidState(position).isEmpty()
                && level.getBlockState(position.below()).isValidSpawn(level, position.below(), type)
                && level.noBlockCollision(null, type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D));
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.DARK_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override protected int getMeleeAttackCooldownTicks() { return 30; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PRESSURE, (byte) 0); builder.define(HUNTING, false); }
    @Override public boolean isUltimateAnimationActive() { return entityData.get(HUNTING) || super.isUltimateAnimationActive(); }
    @Override public void travel(Vec3 input) { if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return !effect.is(MobEffects.DARKNESS) && !effect.is(MobEffects.BLINDNESS) && super.canBeAffected(effect); }
    public UUID session() { return session; }
    public void visualStarted() { visualOpen = true; }
    public int pressure() { return Byte.toUnsignedInt(entityData.get(PRESSURE)); }
    private void pressure(int value) { entityData.set(PRESSURE, (byte) Math.clamp(value, 0, 3)); }
    public int hourTicks() { return hourTicks; }
    public Vec3 hourCenter() { return hourCenter; }
    public int launchedBlades() { return launchedBlades; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    public boolean ally(LivingEntity target) {
        return target != null && target.level() == level() && (target == this || isAlliedTo(target) || target == getOwner()
                || target instanceof AbstractPhantom || isTame() && target instanceof OwnableEntity owned && Objects.equals(getOwnerReference(), owned.getOwnerReference()));
    }
    private boolean recent(LivingEntity attacker, LivingEntity victim) {
        return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100
                || attacker instanceof Mob mob && mob.getTarget() == victim);
    }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !ally(target) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(this, target) && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean explicit = target.getUUID().equals(orderedId) || recent(target, this) || recent(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof Player || target instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return explicit || !isTame() && target instanceof Player && target.getUUID().equals(warnedPlayer) && warningTicks >= 60
                    && distanceToSqr(target) < 16 * 16 && (territory == null || target.position().distanceToSqr(territory) <= 24 * 24);
        }
        return explicit || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (marked.contains(target.getUUID()) ? -200 : 0) + distanceToSqr(target) * .04D - Math.min(100, target.getMaxHealth()); }
    @Override public void setCombatTarget(LivingEntity target) { if (!safeEnemy(target) || !PhantomDuels.begin(this, target)) { return; } orderedId = target.getUUID(); super.setCombatTarget(target); }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .4D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList();
    }
    public boolean inDarkness() { return level().getMaxLocalRawBrightness(blockPosition()) <= 7 || level() instanceof ServerLevel server && night(server) && level().canSeeSky(blockPosition()); }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount, boolean gain) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        float multiplier = (inDarkness() ? 1.1F : 1) * (hourTicks > 0 && marked.contains(target.getUUID()) ? 1.1F : 1);
        if (!target.hurtServer(level, source, amount * multiplier)) { return 0; }
        float dealt = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        if (dealt > 0 && gain) { successfulAttack(); }
        return dealt;
    }
    private void successfulAttack() {
        idleTicks = 0;
        if (gainDelay > 0) { return; }
        gainDelay = 12;
        if (++successfulAttacks >= 2) { successfulAttacks = 0; pressure(pressure() + 1); pressureVisual(); }
    }
    private void pressureVisual() { if (pressure() > 0) { DarkVfx.emit(this, DarkVfx.PRESSURE, this, position(), Vec3.ZERO, 32, pressure()); } }
    public boolean bolt(ServerLevel level, Vec3 aim) {
        int interval = getControllingPassenger() instanceof Player ? MOUNTED_COOLDOWN : INTERVALS[0];
        if (!enabled() || cooldowns[0] > INTERVALS[0] - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.8D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        DarkProjectile projectile = PhantomismContent.DARK_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (projectile == null) { return false; }
        projectile.setOwner(this); projectile.configure(session, false, null, 12); projectile.setPos(muzzle); projectile.shoot(direction.x, direction.y, direction.z, 1.9F, 0);
        if (!level.addFreshEntity(projectile)) { return false; }
        cooldowns[0] = INTERVALS[0]; action("bolt", 12); DarkVfx.emit(this, DarkVfx.BOLT, projectile, muzzle, direction, 32, 1); sound(SoundEvents.PHANTOM_BITE, .6F, .65F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_COOLDOWN; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return bolt(level, rider.getViewVector(1)); }
    public boolean rend(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || meleeTicks > 0 || actionTicks > 0 || !validTarget(target) || distanceToSqr(target) > 4 * 4 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        rendDirection = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize(); rendTicks = 6; meleeTicks = 30; cooldowns[1] = INTERVALS[1]; action("rend", 16); return true;
    }
    private void tickRend(ServerLevel level) {
        if (rendTicks <= 0 || --rendTicks > 0) { return; }
        boolean hit = false;
        for (LivingEntity target : enemies(level, position(), 4, 8)) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            if (getBoundingBox().inflate(1.6D).intersects(target.getBoundingBox()) && rendDirection.dot(offset.normalize()) > .05D
                    && damage(level, target, damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.2D + pressure() * .08D)), false) > 0) { hit = true; }
        }
        if (hit) { successfulAttack(); }
        DarkVfx.emit(this, DarkVfx.REND, null, position().add(rendDirection.scale(1.4D)), rendDirection, 16, 3); sound(SoundEvents.PHANTOM_BITE, 1, .7F);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity enemy) || !enabled() || meleeTicks > 0 || actionTicks > 0 || rushTicks > 0 || !validTarget(enemy)) { return false; }
        if (rend(level, enemy)) { return true; }
        if (!getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition())) { return false; }
        if (damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE), true) <= 0) { return false; }
        meleeTicks = 30; action("rend", 10); DarkVfx.emit(this, DarkVfx.IMPACT, null, enemy.position(), Vec3.ZERO, 14, 1.5F); return true;
    }
    private boolean safeRushStep(Vec3 step) {
        for (Entity member : getSelfAndPassengers().limit(9).toList()) {
            AABB box = member.getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noBlockCollision(member, box) || level().containsAnyLiquid(box)) { return false; }
        }
        return true;
    }
    public boolean rush(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || actionTicks > 0 || rushTicks > 0 || !validTarget(target) || distanceToSqr(target) > 18 * 18 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
        if (!safeRushStep(direction.scale(.6D))) { return false; }
        rushTarget = target.getUUID(); rushTicks = 18; rushHits.clear(); cooldowns[2] = INTERVALS[2]; meleeTicks = 30; action("rush", 22);
        DarkVfx.emit(this, DarkVfx.RUSH, this, position(), direction, 22, 2); sound(SoundEvents.PHANTOM_FLAP, 1, .55F); return true;
    }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; }
        if (!(level.getEntity(rushTarget) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { rushTicks = 0; return; }
        --rushTicks;
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(1.15D);
        for (int probe = 0; probe < 3 && rushTicks >= 0; probe++) {
            if (getBoundingBox().inflate(.65D).intersects(target.getBoundingBox())) {
                if (rushHits.add(target.getUUID()) && damage(level, target, damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.7D), true) > 0) {
                    DarkVfx.emit(this, DarkVfx.RUSH_IMPACT, null, target.position(), step.normalize(), 20, 4);
                }
                rushTicks = 0; setDeltaMovement(Vec3.ZERO); break;
            }
            if (!safeRushStep(step.scale(1D / 3))) { rushTicks = 0; setDeltaMovement(Vec3.ZERO); break; }
            move(MoverType.SELF, step.scale(1D / 3)); setDeltaMovement(Vec3.ZERO); needsSync = true;
            for (Entity passenger : getPassengers()) { positionRider(passenger); }
        }
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 18, 16).stream().filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).findFirst().orElse(null);
        return target != null && (distanceToSqr(target) < 9 ? rend(level, target) : rush(level, target));
    }
    public boolean blackout(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0) { return false; }
        List<LivingEntity> crowd = enemies(level, position(), 12, 16);
        if (crowd.size() < 2) { return false; }
        boolean affected = false;
        for (LivingEntity target : crowd) { affected |= DarkControl.apply(this, target, 40, false); }
        if (!affected) { return false; }
        cooldowns[3] = INTERVALS[3]; pressure(pressure() + 1); idleTicks = 0; action("blackout", 20);
        DarkVfx.emit(this, DarkVfx.BLACKOUT, null, position(), Vec3.ZERO, 30, 12); sound(SoundEvents.PHANTOM_AMBIENT, .8F, .5F); return true;
    }
    public boolean threatenedBy(LivingEntity attacker, LivingEntity victim) { return enabled() && validTarget(attacker) && ally(victim) && recent(attacker, victim) && distanceToSqr(victim) <= 8 * 8; }
    public boolean bubble(ServerLevel level, LivingEntity victim, LivingEntity attacker) {
        if (cooldowns[4] > 0 || bubbleTicks > 0 || !threatenedBy(attacker, victim)) { return false; }
        bubbleTicks = 80; cooldowns[4] = INTERVALS[4]; action("bubble", 18); DarkVfx.emit(this, DarkVfx.BUBBLE, this, position(), Vec3.ZERO, 80, 6); return true;
    }
    public float bubbleMitigation(LivingEntity victim, LivingEntity attacker) { return enabled() && bubbleTicks > 0 && ally(victim) && validTarget(attacker) && distanceToSqr(victim) <= 36 && line(getEyePosition(), victim.getEyePosition()) ? .15F : 0; }
    public boolean shield(ServerLevel level, LivingEntity attacker, float amount) {
        if (!enabled() || cooldowns[5] > 0 || shieldTicks > 0 || !validTarget(attacker) || !Float.isFinite(amount) || amount < 4) { return false; }
        shieldTicks = 80; shieldPool = 12; cooldowns[5] = INTERVALS[5]; action("shield", 14); DarkVfx.emit(this, DarkVfx.SHIELD, this, position(), Vec3.ZERO, 80, 2); return true;
    }
    public float absorb(float incoming) {
        if (!enabled() || shieldTicks <= 0 || shieldPool <= 0 || !Float.isFinite(incoming) || incoming <= 0) { return 0; }
        float absorbed = Math.min(shieldPool, incoming * .35F); shieldPool -= absorbed;
        DarkVfx.emit(this, DarkVfx.SHIELD_HIT, this, position(), Vec3.ZERO, 12, 2); if (shieldPool <= 0) { shieldTicks = 0; } return absorbed;
    }
    public boolean darkHour(ServerLevel level) {
        if (!enabled() || cooldowns[6] > 0 || hourTicks > 0 || actionTicks > 0) { return false; }
        List<LivingEntity> crowd = enemies(level, position(), HOUR_RADIUS, 24);
        if (crowd.size() < 3 || getTarget() == null || !validTarget(getTarget())) { return false; }
        hourCenter = position(); hourTicks = HOUR_DURATION; hourPressure = pressure(); pressure(0); launchedBlades = 0; bladeAssignments.clear(); marked.clear();
        cooldowns[6] = INTERVALS[6]; entityData.set(HUNTING, true); action("hour", 30);
        for (LivingEntity target : crowd) { mark(target); DarkControl.apply(this, target, 40, true); }
        DarkVfx.emit(this, DarkVfx.HOUR, null, hourCenter, Vec3.ZERO, HOUR_DURATION, HOUR_RADIUS); sound(SoundEvents.WITHER_SPAWN, .6F, .55F); return true;
    }
    private void mark(LivingEntity target) { if (marked.size() < 24 && marked.add(target.getUUID())) { DarkVfx.emit(this, DarkVfx.MARK, target, target.position(), Vec3.ZERO, Math.max(1, hourTicks), 1); } }
    public boolean bladeValid(LivingEntity target) { return hourTicks > 0 && hourCenter != null && validTarget(target) && marked.contains(target.getUUID()) && target.distanceToSqr(hourCenter) <= HOUR_RADIUS * HOUR_RADIUS; }
    private void launchBlade(ServerLevel level) {
        if (launchedBlades >= 12 || hourCenter == null || level.getEntitiesOfClass(DarkProjectile.class, new AABB(hourCenter, hourCenter).inflate(32), shot -> shot.getOwner() == this && shot.isBlade()).size() >= 4) { return; }
        List<LivingEntity> crowd = enemies(level, hourCenter, HOUR_RADIUS, 24);
        for (LivingEntity target : crowd) { mark(target); }
        LivingEntity target = crowd.stream().filter(this::bladeValid).min(Comparator.<LivingEntity>comparingInt(enemy -> bladeAssignments.getOrDefault(enemy.getUUID(), 0)).thenComparingDouble(this::getAutomaticTargetPriority)).orElse(null);
        if (target == null) { return; }
        Vec3 spawn = target.getBoundingBox().getCenter().add((launchedBlades % 3 - 1) * .7D, 7, 0);
        if (!line(spawn, target.getBoundingBox().getCenter())) { return; }
        DarkProjectile blade = PhantomismContent.DARK_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (blade == null) { return; }
        blade.setOwner(this); blade.configure(session, true, target.getUUID(), 6 + hourPressure); blade.setPos(spawn); Vec3 direction = target.getBoundingBox().getCenter().subtract(spawn).normalize(); blade.shoot(direction.x, direction.y, direction.z, 1.1F, 0);
        if (level.addFreshEntity(blade)) { launchedBlades++; bladeAssignments.merge(target.getUUID(), 1, Integer::sum); DarkVfx.emit(this, DarkVfx.BLADE, blade, spawn, direction, 36, 1); }
    }
    private void tickHour(ServerLevel level) {
        if (hourTicks <= 0 || hourCenter == null) { return; }
        if (distanceToSqr(hourCenter) > 48 * 48 || !isLoadedFlightBox(new AABB(hourCenter, hourCenter).inflate(1))) { endCombat(); return; }
        int age = HOUR_DURATION - --hourTicks;
        if (age == 40) { action("hunt", 24); }
        if (age >= 40 && age < 184 && (age - 40) % 12 == 0) { launchBlade(level); }
        if (age % 40 == 0 && age < HOUR_DURATION) { for (LivingEntity target : enemies(level, hourCenter, HOUR_RADIUS, 24)) { mark(target); DarkControl.apply(this, target, 30, true); } }
        if (hourTicks == 0) {
            for (LivingEntity target : enemies(level, hourCenter, HOUR_RADIUS, 24)) { damage(level, target, damageSources().mobAttack(this), 16 + hourPressure * 2, false); }
            DarkControl.release(this, true); discardBlades(level); entityData.set(HUNTING, false); action("finale", 24);
            DarkVfx.emit(this, DarkVfx.FINALE, null, hourCenter, Vec3.ZERO, 28, HOUR_RADIUS); sound(SoundEvents.PHANTOM_DEATH, .9F, .55F); hourCenter = null; marked.clear(); bladeAssignments.clear();
        }
    }
    private void discardBlades(ServerLevel level) { for (DarkProjectile shot : level.getEntitiesOfClass(DarkProjectile.class, getBoundingBox().inflate(80), shot -> shot.getOwner() == this && shot.isBlade())) { shot.discard(); } }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (actionTicks > 0 || rushTicks > 0 || rendTicks > 0) { return true; }
        if (darkHour(level) || blackout(level)) { return true; }
        if (distanceToSqr(target) < 16 && rend(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (actionTicks > 0) { actionTicks--; } if (meleeTicks > 0) { meleeTicks--; } if (gainDelay > 0) { gainDelay--; }
        if (shieldTicks > 0 && --shieldTicks == 0) { shieldPool = 0; } if (bubbleTicks > 0) { bubbleTicks--; }
        if (enabled() && validTarget(getTarget()) && distanceToSqr(getTarget()) <= 56 * 56) { idleTicks = 0; } else if (++idleTicks >= 100 && (idleTicks - 100) % 60 == 0) { pressure(pressure() - 1); successfulAttacks = 0; }
        if (!enabled()) { if (visualOpen || hourTicks > 0 || rushTicks > 0 || rendTicks > 0) { endCombat(); } return; }
        if (territory == null) { territory = position(); }
        if (!isTame() && tickCount % 10 == 0) {
            Player intruder = level.players().stream().filter(player -> safeEnemy(player) && distanceToSqr(player) < 12 * 12 && player.position().distanceToSqr(territory) <= 24 * 24).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (intruder == null) { warnedPlayer = null; warningTicks = 0; }
            else if (!intruder.getUUID().equals(warnedPlayer)) { warnedPlayer = intruder.getUUID(); warningTicks = 0; DarkVfx.emit(this, DarkVfx.WARNING, this, position(), Vec3.ZERO, 28, 4); sound(SoundEvents.PHANTOM_AMBIENT, .8F, .65F); }
            else if ((warningTicks += 10) >= 60 && validTarget(intruder)) { setTarget(intruder); }
        }
        tickRend(level); tickRush(level); tickHour(level);
        if (tickCount % 10 == 0 && bubbleTicks > 0) { for (LivingEntity target : enemies(level, position(), 6, 16)) { DarkControl.apply(this, target, 20, false); } }
        if (tickCount % 20 == 0 && validTarget(getTarget())) {
            if (!isVehicle() && actionTicks == 0) { if (!darkHour(level) && !blackout(level) && distanceToSqr(getTarget()) > 4 * 4) { rush(level, getTarget()); } }
            if (pressure() > 0) { pressureVisual(); }
        }
        if (!isVehicle() && actionTicks == 0 && tickCount % 5 == 0 && validTarget(getTarget()) && distanceToSqr(getTarget()) <= BOLT_RANGE * BOLT_RANGE && line(getEyePosition(), getTarget().getEyePosition())) { bolt(level, getTarget().getEyePosition().subtract(getEyePosition())); }
    }
    private void sound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) { if (level() instanceof ServerLevel level) { level.playSound(null, blockPosition(), sound, SoundSource.HOSTILE, volume, pitch); } }
    public void action(String clip, int ticks) { actionTicks = Math.max(actionTicks, ticks); visualStarted(); triggerAnim("dark", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        super.registerControllers(registrar); var controller = new AnimationController<DarkPhantom>("dark", 2, state -> actionTicks > 0 ? PlayState.CONTINUE : PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.dark." + clip)); } registrar.add(controller.additiveAnimations());
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && !level().isClientSide()) {
            if (!isAlive() || player.isSpectator() || !player.getItemInHand(hand).is(Items.OBSIDIAN)) { failedTamer = null; failedUntil = 0; }
            else {
                boolean guaranteed = player.getUUID().equals(failedTamer) && level().getGameTime() <= failedUntil;
                if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); }
                if (guaranteed || getRandom().nextFloat() < .75F) { tame(player); clearCombatTarget(true); failedTamer = null; failedUntil = 0; warnedPlayer = null; warningTicks = 0; level().broadcastEntityEvent(this, (byte) 7); }
                else { failedTamer = player.getUUID(); failedUntil = level().getGameTime() + 6000; level().broadcastEntityEvent(this, (byte) 6); }
                return InteractionResult.SUCCESS;
            }
        }
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(Items.OBSIDIAN)) { return InteractionResult.SUCCESS; }
        return super.mobInteract(player, hand);
    }
    public void endCombat() {
        if (session != null && visualOpen && level() instanceof ServerLevel) { DarkVfx.cancel(this); }
        DarkControl.release(this, false); session = UUID.randomUUID(); visualOpen = false; hourTicks = 0; hourCenter = null; rushTicks = 0; rushTarget = null; rendTicks = 0;
        bubbleTicks = 0; shieldTicks = 0; shieldPool = 0; actionTicks = 0; orderedId = null; marked.clear(); bladeAssignments.clear(); rushHits.clear(); entityData.set(HUNTING, false);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endCombat(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endCombat(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endCombat(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("DarkCooldown" + index, cooldowns[index]); }
        output.putInt("DarkPressure", pressure()); output.putInt("DarkIdle", idleTicks); if (failedTamer != null) { output.putString("DarkFailedTamer", failedTamer.toString()); output.putLong("DarkFailedUntil", failedUntil); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endCombat(); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("DarkCooldown" + index, 0), 0, INTERVALS[index]); }
        pressure(input.getIntOr("DarkPressure", 0)); idleTicks = Math.clamp(input.getIntOr("DarkIdle", 0), 0, 280); successfulAttacks = 0; gainDelay = 0; failedTamer = null;
        try { String saved = input.getStringOr("DarkFailedTamer", ""); if (!isTame() && !saved.isEmpty()) { failedTamer = UUID.fromString(saved); } } catch (IllegalArgumentException ignored) { failedTamer = null; }
        failedUntil = Math.min(input.getLongOr("DarkFailedUntil", 0), level().getGameTime() + 6000); warnedPlayer = null; warningTicks = 0; territory = null;
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>(); for (int index = 0; index < 6; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("dark_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(slots);
    }
}
