package net.ronm19.phantomism.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.PhantomHornRally;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.entity.spawn.HalloweenEncounterSpawner;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.HalloweenVfx;
import net.ronm19.phantomism.vfx.HalloweenPumpkins;

public final class HalloweenPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int GAZE_RANGE = 42;
    public static final int MOUNTED_INTERVAL = 6;
    public static final int FRIGHT_DURATION = 100;
    private static final int[] INTERVALS = {100, 200, 280, 1800, 600};
    private static final String[] KEYS = {"gaze", "dive", "screech", "fright", "veil"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(HalloweenPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EYE_FLASH = SynchedEntityData.defineId(HalloweenPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(HalloweenPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> projectiles = new HashSet<>();
    private final List<UUID> ritualPumpkins = new ArrayList<>();
    private final Map<UUID, Long> fearRecovery = new HashMap<>();
    private final Map<UUID, Float> ultimateDamage = new HashMap<>();
    private UUID session = UUID.randomUUID();
    private UUID orderedTarget;
    private UUID diveTarget;
    private UUID sequenceRider;
    private UUID ownerContext;
    private Vec3 frightCenter;
    private int flashTicks;
    private int diveTicks;
    private int frightTicks;
    private int veilTicks;
    private int regroupTicks;
    private int regroupRecovery;
    private double diveTravelled;
    private boolean explicitSelection;
    private long nextCombatDecision;
    private boolean visualOpen;

    public HalloweenPhantom(EntityType<? extends HalloweenPhantom> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 120).add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .45D)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .48D);
    }

    public static boolean checkSpawnRules(EntityType<HalloweenPhantom> type, ServerLevelAccessor accessor,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level)) { return false; }
        if (!naturalHolidaySpawn(reason)) { return HalloweenEncounterSpawner.safeSpace(level, position); }
        return Config.halloweenSpawningEnabled() && festivalAllowsSpawn(HolidayFestival.HALLOWEEN, reason)
                && HalloweenEncounterSpawner.habitat(level, position)
                && HalloweenEncounterSpawner.hasPumpkin(level, position) && HalloweenEncounterSpawner.safeSpace(level, position);
    }

    public static boolean hauntedNight(Level level) {
        long time = Math.floorMod(level.dimensionType().defaultClock().isPresent() ? level.getDefaultClockTime() : level.getOverworldClockTime(), 24000L);
        return time >= 13000L && time < 23000L;
    }

    public boolean eyesHaunted() { return hauntedNight(level()) || entityData.get(EYE_FLASH); }
    public boolean abilityEyeFlash() { return entityData.get(EYE_FLASH); }
    public UUID session() { return session; }
    public void visualStarted() { visualOpen = true; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int frightTicks() { return frightTicks; }
    public int diveTicks() { return diveTicks; }
    public int veilTicks() { return veilTicks; }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.HALLOWEEN_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.HALLOWEEN; }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel level) { HalloweenVfx.mist(level, position()); HalloweenVfx.emit(this, HalloweenVfx.DEFEAT, this, position(), RECOVERY_TICKS, 2); } }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getFlightTurnRate() { return 20; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return frightTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(DIVING) ? 1 : 0)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(SCRIPTED, false);
        data.define(EYE_FLASH, false);
        data.define(DIVING, false);
    }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) {
        if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }

    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && !isHolidayDefeated() && !PhantomHornRally.returning(this)
                && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }

    public boolean ally(LivingEntity target) {
        return target == this || target instanceof AbstractPhantom || isAlliedTo(target)
                || isTame() && (target == getOwner() || target instanceof OwnableEntity pet
                && Objects.equals(getOwnerReference(), pet.getOwnerReference()) || CelestialBond.authorizedAlly(this, target));
    }

    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !ally(target) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
                && (explicitSelection || !isTargetSuppressed(target)) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean attacking(LivingEntity target, LivingEntity ward) {
        return ward != null && (ward.getLastHurtByMob() == target && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100
                || target instanceof Mob mob && mob.getTarget() == ward);
    }

    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (target instanceof Enemy || target.getUUID().equals(orderedTarget)
                || attacking(target, this) || attacking(target, getOwner())
                || target instanceof Mob mob && mob.getTarget() != null && ally(mob.getTarget()));
    }

    public boolean validTarget(LivingEntity target) {
        return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target);
    }
    @Override public void setCombatTarget(LivingEntity target) {
        explicitSelection = true;
        try {
            if (safeEnemy(target)) { orderedTarget = target.getUUID(); super.setCombatTarget(target); }
        } finally { explicitSelection = false; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition());
    }

    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr())
                && isLoadedFlightBox(new AABB(start, end).inflate(.1D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .5D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(center))).limit(16).toList();
    }

    private boolean damage(ServerLevel level, LivingEntity target, float amount) {
        return enabled() && validTarget(target) && target.hurtServer(level, damageSources().mobAttack(this), amount);
    }

    private void frighten(ServerLevel level, LivingEntity target, int duration, boolean strong) {
        if (!validTarget(target) || !(target instanceof Mob mob) || GravityControl.movementImmune(mob)
                || fearRecovery.getOrDefault(target.getUUID(), Long.MIN_VALUE) > level.getGameTime()
                || fearRecovery.size() >= 128) { return; }
        int ticks = Math.clamp(duration, 10, 60);
        fearRecovery.put(target.getUUID(), level.getGameTime() + ticks + 100);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, strong ? 1 : 0, false, false), this);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, false), this);
        if (strong) { target.knockback(.5D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 0); }
    }

    private void flash(int ticks) {
        flashTicks = Math.max(flashTicks, ticks);
        entityData.set(EYE_FLASH, true);
    }

    public boolean gaze(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || diveTicks > 0 || frightTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > GAZE_RANGE * GAZE_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        return fire(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), false);
    }

    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || diveTicks > 0 || frightTicks > 0 || projectiles.size() >= 12
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize();
        Vec3 muzzle = getEyePosition().add(direction.scale(.7D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        PumpkinGaze shot = PhantomismContent.PUMPKIN_GAZE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.configure(this, session);
        shot.setPos(muzzle);
        shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        projectiles.add(shot.getUUID());
        cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        flash(6);
        playSound(SoundEvents.BLAZE_SHOOT, .25F, 1.35F);
        HalloweenVfx.emit(this, HalloweenVfx.GAZE, shot, muzzle, 26, 1);
        return true;
    }

    public void gazeImpact(ServerLevel level, LivingEntity target, Vec3 position) {
        if (damage(level, target, 10)) { frighten(level, target, 30, false); }
        HalloweenVfx.burst(level, position, 12);
        HalloweenVfx.emit(this, HalloweenVfx.IMPACT, null, position, 16, 3);
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return fire(level, rider.getViewVector(1), true); }

    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 18).stream()
                .filter(enemy -> enemy.getEyePosition().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1)) > .65D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return dive(level, target);
    }

    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || frightTicks > 0 || diveTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > 18 * 18 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        cooldowns[1] = INTERVALS[1];
        diveTarget = target.getUUID();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        diveTicks = 16;
        entityData.set(DIVING, true);
        diveTravelled = 0;
        scripted(true);
        flash(16);
        playSound(SoundEvents.PHANTOM_SWOOP, .55F, .65F);
        HalloweenVfx.emit(this, HalloweenVfx.DIVE, this, position(), 16, 3);
        return true;
    }

    private void scripted(boolean active) {
        boolean previous = entityData.get(SCRIPTED);
        entityData.set(SCRIPTED, active);
        if (previous == active) { return; }
        setDeltaMovement(Vec3.ZERO);
        needsSync = true;
        if (level() instanceof ServerLevel && getControllingPassenger() instanceof ServerPlayer rider && rider.connection != null) {
            rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(),
                    List.of(SynchedEntityData.DataValue.create(SCRIPTED, active))));
            if (!active) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
        }
    }

    private boolean moveSafely(ServerLevel level, Vec3 step) {
        AABB destination = getBoundingBox().move(step);
        if (!isLoadedFlightBox(getBoundingBox().expandTowards(step)) || !level.getWorldBorder().isWithinBounds(destination)
                || destination.minY <= level.getMinY() || destination.maxY >= level.getMaxY()
                || !level.noBlockCollision(this, destination) || level.containsAnyLiquid(destination)) { return false; }
        for (Entity passenger : getPassengers()) {
            AABB riderBox = passenger.getBoundingBox().move(step);
            if (!level.noBlockCollision(passenger, riderBox) || level.containsAnyLiquid(riderBox)) { return false; }
        }
        setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, step);
        needsSync = true;
        for (Entity passenger : getPassengers()) { positionRider(passenger); }
        return true;
    }

    private boolean riderChanged() {
        return !Objects.equals(sequenceRider, getControllingPassenger() == null ? null : getControllingPassenger().getUUID());
    }

    private void stopDive() { diveTicks = 0; diveTarget = null; entityData.set(DIVING, false); if (frightTicks == 0) { scripted(false); } }

    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        Entity entity = level.getEntity(diveTarget);
        if (riderChanged() || !(entity instanceof LivingEntity target) || !validTarget(target)
                || !line(getEyePosition(), target.getEyePosition())) { stopDive(); return; }
        if (distanceToSqr(target) <= 6.25D) {
            if (damage(level, target, 22)) { frighten(level, target, 45, true); }
            HalloweenVfx.ring(level, target.position(), 4, 24, false);
            for (LivingEntity nearby : enemies(level, target.position(), 4)) {
                if (nearby != target && damage(level, nearby, 8)) { frighten(level, nearby, 25, false); }
            }
            stopDive();
            return;
        }
        Vec3 difference = target.position().subtract(position());
        Vec3 step = difference.normalize().scale(Math.min(1.35D, Math.min(18 - diveTravelled, difference.length())));
        Vec3 previous = position();
        if (diveTravelled >= 18 || !moveSafely(level, step)) { stopDive(); return; }
        diveTravelled += position().distanceTo(previous);
        if (diveTicks % 3 == 0) { HalloweenVfx.burst(level, position(), 3); }
        if (--diveTicks == 0) { stopDive(); }
    }

    public boolean screech(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || diveTicks > 0 || frightTicks > 0) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 9);
        if (targets.isEmpty()) { return false; }
        cooldowns[2] = INTERVALS[2];
        flash(14);
        for (LivingEntity target : targets) { if (damage(level, target, 10)) { frighten(level, target, 50, true); } }
        HalloweenVfx.ring(level, position(), 3, 24, false);
        HalloweenVfx.ring(level, position().add(0, .4D, 0), 6, 28, true);
        HalloweenVfx.ring(level, position().add(0, .8D, 0), 9, 32, false);
        playSound(SoundEvents.PHANTOM_AMBIENT, .7F, .55F);
        HalloweenVfx.emit(this, HalloweenVfx.SCREECH, null, position(), 18, 9);
        return true;
    }

    public boolean nightOfFright(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || diveTicks > 0 || frightTicks > 0) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 18);
        if (targets.isEmpty()) { return false; }
        cooldowns[3] = Config.halloweenUltimateCooldown();
        frightCenter = targets.getFirst().position();
        frightTicks = FRIGHT_DURATION;
        ultimateDamage.clear();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        scripted(true);
        flash(FRIGHT_DURATION);
        for (int slot = 0; slot < HalloweenPumpkins.COUNT; slot++) {
            ritualPumpkins.add(HalloweenPumpkins.create(this, frightCenter, slot).getUUID());
        }
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, .5F, .65F);
        HalloweenVfx.emit(this, HalloweenVfx.FRIGHT, null, frightCenter, FRIGHT_DURATION, 14);
        return true;
    }

    private void tickFright(ServerLevel level) {
        if (frightTicks <= 0) { return; }
        if (riderChanged() || frightCenter == null || distanceToSqr(frightCenter) > 32 * 32
                || !isLoadedFlightBox(new AABB(frightCenter, frightCenter).inflate(14))) { cancelCombat(); return; }
        int elapsed = FRIGHT_DURATION - frightTicks;
        for (int slot = 0; slot < ritualPumpkins.size(); slot++) {
            if (level.getEntity(ritualPumpkins.get(slot)) instanceof Display.BlockDisplay display) {
                HalloweenPumpkins.move(display, frightCenter, elapsed, slot);
            }
        }
        if (elapsed < 16) { moveSafely(level, new Vec3(0, .12D, 0)); }
        if (elapsed % 10 == 0) { HalloweenVfx.ritual(level, frightCenter, elapsed); }
        if (elapsed == 30 || elapsed == 50 || elapsed == 70 || elapsed == 95) {
            boolean finale = elapsed == 95;
            int radius = finale ? 14 : 6 + (elapsed - 30) / 5;
            HalloweenVfx.ring(level, frightCenter, radius, finale ? 48 : 32, elapsed == 50);
            for (LivingEntity target : enemies(level, frightCenter, radius)) {
                float spent = ultimateDamage.getOrDefault(target.getUUID(), 0F);
                float amount = Math.min(finale ? 24F : 6F, 42F - spent);
                if (amount > 0 && (ultimateDamage.size() < 16 || ultimateDamage.containsKey(target.getUUID())) && damage(level, target, amount)) {
                    ultimateDamage.put(target.getUUID(), spent + amount);
                    frighten(level, target, finale ? 60 : 30, finale);
                }
            }
            playSound(finale ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.EVOKER_CAST_SPELL, finale ? .7F : .4F, .75F);
            if (finale) { HalloweenVfx.burst(level, frightCenter.add(0, 1, 0), 28); }
        }
        if (--frightTicks == 0) { clearPumpkins(); ultimateDamage.clear(); frightCenter = null; scripted(false); }
    }

    private boolean veil(ServerLevel level, LivingEntity attacker, float incoming) {
        if (!enabled() || cooldowns[4] > 0 || !safeEnemy(attacker) || incoming < 4 && getHealth() > getMaxHealth() * .4F) { return false; }
        cooldowns[4] = INTERVALS[4];
        veilTicks = 60;
        HalloweenVfx.mist(level, position());
        HalloweenVfx.emit(this, HalloweenVfx.VEIL, this, position(), 60, 2);
        if (!isVehicle() && diveTicks == 0 && frightTicks == 0) {
            Vec3 away = position().subtract(attacker.position()).normalize();
            moveSafely(level, new Vec3(away.x * .8D, .4D, away.z * .8D));
        }
        playSound(SoundEvents.PHANTOM_FLAP, .3F, .55F);
        return true;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (amount > 0 && source.getEntity() instanceof LivingEntity attacker && safeEnemy(attacker)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            veil(level, attacker, amount);
            if (veilTicks > 0) { amount *= .65F; }
        }
        return super.hurtServer(level, source, amount);
    }

    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || diveTicks > 0 || frightTicks > 0) { return diveTicks > 0 || frightTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; }
        nextCombatDecision = level.getGameTime() + 10;
        List<LivingEntity> nearby = enemies(level, position(), 18);
        if (!isVehicle() && getOwner() instanceof LivingEntity owner && getHealth() < getMaxHealth() * .3F
                && nearby.size() >= 3 && regroupRecovery == 0) { regroupTicks = 40; regroupRecovery = 200; }
        if (regroupTicks > 0 && !isVehicle() && getOwner() instanceof LivingEntity owner && distanceToSqr(owner) > 16
                && line(getEyePosition(), owner.getEyePosition().add(0, 2, 0))) {
            getMoveControl().setWantedPosition(owner.getX(), owner.getY() + 2, owner.getZ(), 1.4D);
            return true;
        }
        if ((nearby.size() >= 3 || getHealth() < getMaxHealth() * .35F) && nightOfFright(level)) { return true; }
        if (nearby.stream().filter(enemy -> distanceToSqr(enemy) <= 81).count() >= 2 && screech(level)) { return true; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 18 * 18 && dive(level, target)) { return true; }
        return gaze(level, target);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        regroupTicks = Math.max(0, regroupTicks - 1);
        regroupRecovery = Math.max(0, regroupRecovery - 1);
        projectiles.removeIf(identity -> level.getEntity(identity) == null);
        if (flashTicks > 0 && --flashTicks == 0) { entityData.set(EYE_FLASH, false); }
        if (veilTicks > 0) { if (veilTicks % 10 == 0) { HalloweenVfx.mist(level, position()); } veilTicks--; }
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); }
        ownerContext = context;
        if (!enabled()) {
            if (diveTicks > 0 || frightTicks > 0 || veilTicks > 0 || !projectiles.isEmpty()) { cancelCombat(); }
            return;
        }
        tickDive(level);
        tickFright(level);
        if (tickCount % 40 == 0) {
            fearRecovery.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
            if (eyesHaunted()) { HalloweenVfx.burst(level, getEyePosition(), 2); }
            if (!isNoAi()) { for (LivingEntity target : enemies(level, position(), 7)) { frighten(level, target, 20, false); } }
        }
    }

    private void clearPumpkins() {
        if (level() instanceof ServerLevel level) {
            for (UUID identity : ritualPumpkins) { Entity display = level.getEntity(identity); if (display != null) { display.discard(); } }
        }
        ritualPumpkins.clear();
    }

    public void cancelCombat() {
        clearPumpkins();
        if (level() instanceof ServerLevel level) {
            if (visualOpen) { HalloweenVfx.cancel(this); }
            for (UUID identity : List.copyOf(projectiles)) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } }
        }
        projectiles.clear();
        visualOpen = false;
        fearRecovery.clear();
        ultimateDamage.clear();
        session = UUID.randomUUID();
        diveTicks = 0;
        diveTarget = null;
        entityData.set(DIVING, false);
        frightTicks = 0;
        frightCenter = null;
        veilTicks = 0;
        regroupTicks = 0;
        flashTicks = 0;
        entityData.set(EYE_FLASH, false);
        scripted(false);
    }

    @Override public void clearCombatTarget(boolean suppress) {
        super.clearCombatTarget(suppress);
        if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; }
    }
    @Override public void setOrder(Order order) {
        super.setOrder(order);
        if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; }
    }
    @Override public void remove(RemovalReason reason) {
        if (cooldowns != null) { cancelCombat(); }
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("HalloweenCooldown" + slot, cooldowns[slot]); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cancelCombat();
        for (int slot = 0; slot < cooldowns.length; slot++) {
            cooldowns[slot] = Math.clamp(input.getIntOr("HalloweenCooldown" + slot, 0), 0, slot == 3 ? Config.halloweenUltimateCooldown() : INTERVALS[slot]);
        }
    }
    @Override public int staffAbilityCooldown(String ability) {
        for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } }
        return -1;
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 4}) {
            final int slot = index;
            slots.add(CooldownDiscipline.Slot.normal("halloween_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value));
        }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() {
        return List.of("Pumpkin Gaze: 42 blocks; 5 seconds AI/Staff, 0.3 seconds mounted.",
                "Fright Dive: 10 seconds. Ghostly Screech: 14 seconds. Spooky Veil: 30 seconds.",
                "Night of Fright: " + Config.halloweenUltimateCooldown() / 20 + " seconds. Haunting Presence has per-enemy recovery.");
    }
}
