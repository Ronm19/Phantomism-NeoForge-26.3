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
import net.ronm19.phantomism.vfx.EasterVfx;


public final class EasterPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int EGG_RANGE = 42, MOUNTED_INTERVAL = 6, AWAKENING_DURATION = 120;
    private static final int[] INTERVALS = {100, 160, 280, 1700, 280};
    private static final String[] KEYS = {"egg", "leap", "renewal", "awakening", "evade"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(EasterPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LEAPING = SynchedEntityData.defineId(EasterPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> projectiles = new HashSet<>();
    private final List<UUID> eggs = new ArrayList<>();
    private final Map<UUID, Float> awakeningHealing = new HashMap<>(), awakeningDamage = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, leapTarget, sequenceRider, ownerContext;
    private Vec3 awakeningCenter, leapDirection = Vec3.ZERO;
    private int leapTicks, evasionTicks, awakeningTicks, recoilTicks;
    private long evasionUntil, nextCombatDecision;
    private double leapTravelled;
    private float lastTurn;
    private boolean explicitSelection;
    public EasterPhantom(EntityType<? extends EasterPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 88).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.ARMOR, 6)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .25D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .27D).add(Attributes.FLYING_SPEED, .49D);
    }
    public static boolean checkSpawnRules(EntityType<EasterPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level)) { return false; }
        return (!naturalHolidaySpawn(reason) || festivalAllowsSpawn(HolidayFestival.EASTER, reason)
                && net.ronm19.phantomism.entity.spawn.EasterEncounterSpawner.habitat(level, position))
                && HalloweenEncounterSpawner.safeSpace(level, position);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.EASTER_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.EASTER; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getFlightTurnRate() { return 16; }
    @Override protected double getRiddenHorizontalAcceleration() { return super.getRiddenHorizontalAcceleration() * 1.15D; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return awakeningTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(LEAPING) ? 1 : 0)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(SCRIPTED, false); data.define(LEAPING, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    @Override public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!isTame() && !player.getItemInHand(hand).is(net.minecraft.world.item.Items.EGG)) { return net.minecraft.world.InteractionResult.PASS; }
        return super.mobInteract(player, hand);
    }
    public UUID session() { return session; }
    public int awakeningTicks() { return awakeningTicks; }
    public int leapTicks() { return leapTicks; }
    public int evasionTicks() { return evasionTicks; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && !isHolidayDefeated() && !PhantomHornRally.returning(this)
                && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public List<LivingEntity> allies(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::ally).stream()
                .filter(target -> distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition()))
                .sorted(Comparator.comparingDouble((LivingEntity target) -> target.getHealth() / Math.max(1, target.getMaxHealth()) - (target == getOwner() ? .05D : 0)))
                .limit(8).toList();
    }
    public float healAlly(LivingEntity target, float amount, double radius) {
        if (!enabled() || !ally(target) || distanceToSqr(target) > radius * radius || !line(getEyePosition(), target.getEyePosition())
                || target instanceof AbstractHolidayPhantom holiday && holiday.isHolidayDefeated()) { return 0; }
        float previous = target.getHealth();
        target.heal(Math.min(Math.max(0, amount), target.getMaxHealth() - previous));
        return Math.max(0, target.getHealth() - previous);
    }
    public boolean ally(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && (target == this || isTame() && (target == getOwner()
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())
                || CelestialBond.authorizedAlly(this, target) || !(target instanceof Enemy) && isAlliedTo(target))
                || !isTame() && (target.getType() == net.minecraft.world.entity.EntityTypes.VILLAGER || target.getType() == net.minecraft.world.entity.EntityTypes.IRON_GOLEM));
    }

    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !ally(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
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
        return enabled() && validTarget(target) && (attacking(target, this) || attacking(target, getOwner())
                || target instanceof Mob mob && ally(mob.getTarget())) && line(getEyePosition(), target.getEyePosition());
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


    public boolean renewal(ServerLevel level) {
        return allies(12).stream().filter(target -> target.getMaxHealth() - target.getHealth() >= 3)
                .filter(net.ronm19.phantomism.entity.ai.EasterSupport::renewalAvailable).findFirst().map(target -> renewal(level, target)).orElse(false);
    }
    public boolean renewal(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || awakeningTicks > 0 || leapTicks > 0 || evasionTicks > 0
                || !ally(target) || distanceToSqr(target) > 144 || !line(getEyePosition(), target.getEyePosition())
                || target.getMaxHealth() - target.getHealth() < 3 || !net.ronm19.phantomism.entity.ai.EasterSupport.claimRenewal(target)) { return false; }
        if (healAlly(target, 8, 12) <= 0) { return false; }
        cooldowns[2] = INTERVALS[2]; target.removeEffect(MobEffects.SLOWNESS);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BLESSING);
        EasterVfx.emit(this, EasterVfx.RENEWAL, target, target.position(), 40, 3);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .5F, 1.35F); return true;
    }
    public boolean egg(ServerLevel level, LivingEntity target) {
        return enabled() && validTarget(target) && distanceToSqr(target) <= EGG_RANGE * EGG_RANGE
                && line(getEyePosition(), target.getEyePosition()) && fire(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), false);
    }
    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0 || projectiles.size() >= 12
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.7D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        EasterEggProjectile shot = PhantomismContent.EASTER_EGG_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.configure(this, session, random.nextInt(4)); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.65F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        projectiles.add(shot.getUUID()); cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.PROJECTILE);
        EasterVfx.emit(this, EasterVfx.EGG, shot, muzzle, 30, 1);
        playSound(SoundEvents.EGG_THROW, .35F, 1.1F); return true;
    }
    public void eggImpact(ServerLevel level, LivingEntity target, Vec3 point) {
        if (target != null && damage(level, target, 7) && !(target instanceof Mob mob && GravityControl.movementImmune(mob))) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, false, true), this);
        }
        EasterVfx.emit(this, EasterVfx.IMPACT, null, point, 18, 3);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .3F, 1.7F);
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return fire(level, rider.getViewVector(1), true); }
    public boolean activateRiddenLeap(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 18).stream()
                .filter(enemy -> enemy.getEyePosition().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1)) > .65D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return startLeap(level, target, rider.getViewVector(1));
    }
    public boolean leap(ServerLevel level, LivingEntity target) { return startLeap(level, target, getControllingPassenger() instanceof Player rider ? rider.getViewVector(1) : getViewVector(1)); }
    private boolean startLeap(ServerLevel level, LivingEntity target, Vec3 aim) {
        if (!enabled() || cooldowns[1] > 0 || awakeningTicks > 0 || leapTicks > 0 || evasionTicks > 0
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D
                || target != null && (!validTarget(target) || distanceToSqr(target) > 324 || !line(getEyePosition(), target.getEyePosition()))) { return false; }
        cooldowns[1] = INTERVALS[1]; leapTicks = 16; recoilTicks = 0; leapTravelled = 0;
        leapTarget = target == null ? null : target.getUUID(); leapDirection = aim.normalize();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        scripted(true); entityData.set(LEAPING, true);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.CHARGE);
        EasterVfx.emit(this, EasterVfx.LEAP, this, position(), 23, 3);
        playSound(SoundEvents.PHANTOM_SWOOP, .4F, 1.35F); return true;
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


    private void endMotion() {
        leapTicks = evasionTicks = recoilTicks = 0; leapTarget = null; entityData.set(LEAPING, false);
        if (awakeningTicks == 0) { scripted(false); }
    }
    private void tickLeap(ServerLevel level) {
        if (leapTicks <= 0) { return; }
        if (riderChanged()) { endMotion(); return; }
        Vec3 direction = leapDirection;
        if (recoilTicks > 0) {
            if (!moveSafely(level, direction.scale(.55D)) || --recoilTicks <= 0) { endMotion(); }
            return;
        }
        if (leapTarget != null) {
            Entity entity = level.getEntity(leapTarget);
            if (!(entity instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { endMotion(); return; }
            if (distanceToSqr(target) <= 6.25D) {
                damage(level, target, 16);
                EasterVfx.emit(this, EasterVfx.IMPACT, null, target.position(), 18, 3);
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < .01D) { away = leapDirection.scale(-1); }
                leapDirection = away.normalize().add(0, .18D, 0).normalize();
                leapTarget = null; recoilTicks = 6; return;
            }
            direction = target.position().subtract(position()).normalize();
        }
        Vec3 step = direction.scale(Math.min(1.2D, 18 - leapTravelled)), before = position();
        if (leapTravelled >= 18 || !moveSafely(level, step)) { endMotion(); return; }
        leapTravelled += before.distanceTo(position());
        if (--leapTicks <= 0) { endMotion(); }
    }
    private boolean escapeCorridor(ServerLevel level, Vec3 direction) {
        for (int part = 1; part <= 6; part++) {
            Vec3 step = direction.scale(part * .8D);
            AABB box = getBoundingBox().move(step);
            if (!isLoadedFlightBox(box) || !level.getWorldBorder().isWithinBounds(box) || !level.noBlockCollision(this, box)
                    || level.containsAnyLiquid(box) || box.minY <= level.getMinY() || box.maxY >= level.getMaxY()) { return false; }
            for (Entity rider : getPassengers()) {
                if (!level.noBlockCollision(rider, rider.getBoundingBox().move(step)) || level.containsAnyLiquid(rider.getBoundingBox().move(step))) { return false; }
            }
        }
        return true;
    }
    public boolean evade(ServerLevel level, LivingEntity danger) {
        if (!enabled() || cooldowns[4] > 0 || leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0
                || danger != null && !safeEnemy(danger)) { return false; }
        Vec3 away = danger == null ? getViewVector(1).scale(-1) : position().subtract(danger.position());
        away = new Vec3(away.x, 0, away.z); if (away.lengthSqr() < .01D) { away = new Vec3(0, 0, -1); }
        for (double angle : new double[]{0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2}) {
            Vec3 direction = away.normalize().yRot((float)angle).add(0, .12D, 0).normalize();
            if (!escapeCorridor(level, direction)) { continue; }
            cooldowns[4] = INTERVALS[4]; evasionTicks = 6; evasionUntil = level.getGameTime() + 8; leapDirection = direction;
            sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
            scripted(true); entityData.set(LEAPING, true);
            animate(net.ronm19.phantomism.animation.PhantomAnimationAction.CHARGE);
            EasterVfx.emit(this, EasterVfx.EVADE, this, position(), 14, 3); return true;
        }
        return false;
    }
    public boolean evading() { return enabled() && level().getGameTime() < evasionUntil; }
    private void tickEvasion(ServerLevel level) {
        if (evasionTicks > 0 && (riderChanged() || !moveSafely(level, leapDirection.scale(.8D)) || --evasionTicks <= 0)) { endMotion(); }
    }
    public boolean awakening(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0
                || enemies(level, position(), 12).isEmpty() && allies(12).stream().filter(ally -> ally.getMaxHealth() - ally.getHealth() >= 6).count() < 2
                || !isLoadedFlightBox(getBoundingBox().inflate(14))) { return false; }
        cooldowns[3] = INTERVALS[3]; awakeningTicks = AWAKENING_DURATION; awakeningCenter = position(); awakeningDamage.clear(); awakeningHealing.clear();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        scripted(true); clearEggs();
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE);
        EasterVfx.emit(this, EasterVfx.AWAKENING, null, awakeningCenter, AWAKENING_DURATION, 12);
        playSound(SoundEvents.BEACON_ACTIVATE, .5F, 1.6F); return true;
    }
    private void tickAwakening(ServerLevel level) {
        if (awakeningTicks <= 0) { return; }
        if (riderChanged() || awakeningCenter == null || distanceToSqr(awakeningCenter) > 32 * 32
                || !isLoadedFlightBox(new AABB(awakeningCenter, awakeningCenter).inflate(14))) { cancelCombat(); return; }
        int elapsed = AWAKENING_DURATION - awakeningTicks;
        if (elapsed < 20) { moveSafely(level, new Vec3(0, .065D, 0)); }
        if (elapsed == 20) {
            for (int slot = 0; slot < 6; slot++) {
                double angle = slot * Math.PI / 3;
                EasterOrbitEgg egg = EasterOrbitEgg.create(this, awakeningCenter.add(Math.cos(angle) * 4.8D, 2, Math.sin(angle) * 4.8D), 104, slot % 4);
                if (egg != null) { eggs.add(egg.getUUID()); }
            }
        }
        if (elapsed % 4 == 0) {
            for (int slot = 0; slot < eggs.size(); slot++) {
                if (level.getEntity(eggs.get(slot)) instanceof EasterOrbitEgg egg) {
                    double angle = elapsed * .028D + slot * Math.PI / 3;
                    egg.setPos(awakeningCenter.add(Math.cos(angle) * 4.8D, 2 + Math.sin(elapsed * .07D + slot) * .5D, Math.sin(angle) * 4.8D));
                    egg.needsSync = true;
                }
            }
        }
        if (elapsed == 40 || elapsed == 70 || elapsed == 100) {
            for (LivingEntity ally : allies(24)) {
                if (ally.distanceToSqr(awakeningCenter) > 144) { continue; }
                float spent = awakeningHealing.getOrDefault(ally.getUUID(), 0F);
                float healed = healAlly(ally, Math.min(elapsed == 100 ? 10 : 4, Math.max(0, 18 - spent)), 24);
                awakeningHealing.put(ally.getUUID(), spent + healed);
                if (elapsed == 100) { ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, true), this); }
            }
            for (LivingEntity enemy : enemies(level, awakeningCenter, 12)) {
                float spent = awakeningDamage.getOrDefault(enemy.getUUID(), 0F), amount = Math.min(8, 24 - spent);
                if (amount > 0 && damage(level, enemy, amount)) {
                    awakeningDamage.put(enemy.getUUID(), spent + amount);
                    if (!(enemy instanceof Mob mob && GravityControl.movementImmune(mob))) { enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, false, true), this); }
                }
            }
            EasterVfx.emit(this, elapsed == 100 ? EasterVfx.BLOSSOM : EasterVfx.BLOOM, null, awakeningCenter, elapsed == 100 ? 24 : 20, 12);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .6F, elapsed == 100 ? 1.75F : 1.2F);
            if (elapsed == 100) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE); }
        }
        if (--awakeningTicks <= 0) { clearEggs(); awakeningCenter = null; awakeningHealing.clear(); awakeningDamage.clear(); scripted(false); }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0) { return leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; }
        nextCombatDecision = level.getGameTime() + 10;
        int threats = enemies(level, position(), 12).size();
        long injured = allies(12).stream().filter(ally -> ally.getHealth() < ally.getMaxHealth() * .5F).count();
        if ((threats >= 3 || threats > 0 && injured >= 2) && awakening(level) || renewal(level)) { return true; }
        if (!validTarget(target)) { return false; }
        if (getHealth() < getMaxHealth() * .35F && evade(level, target)) { return true; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 324 && leap(level, target)) { return true; }
        return egg(level, target);
    }
    @Override public void tick() {
        boolean recovering = isHolidayDefeated(); super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        if (recovering && !isHolidayDefeated()) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BLESSING); }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        projectiles.removeIf(identity -> level.getEntity(identity) == null); eggs.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); } ownerContext = context;
        if (!enabled()) { if (leapTicks > 0 || evasionTicks > 0 || awakeningTicks > 0 || !projectiles.isEmpty() || !eggs.isEmpty()) { cancelCombat(); } return; }
        tickLeap(level); tickEvasion(level); tickAwakening(level);
        if (!isNoAi() && tickCount % 20 == 0 && leapTicks == 0 && evasionTicks == 0 && awakeningTicks == 0) { renewal(level); }
        float turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(getYRot() - lastTurn)); lastTurn = getYRot();
        if (tickCount % 8 == 0 && (turn > 12 || leapTicks > 0 || evasionTicks > 0) && getDeltaMovement().lengthSqr() > .03D) { EasterVfx.motes(level, position(), 2); }
    }
    private void clearEggs() {
        if (level() instanceof ServerLevel level) { for (UUID identity : eggs) { Entity egg = level.getEntity(identity); if (egg != null) { egg.discard(); } } }
        eggs.clear();
    }
    public void cancelCombat() {
        if (level() instanceof ServerLevel level) {
            EasterVfx.cancel(this); for (UUID identity : projectiles) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } }
        }
        projectiles.clear(); clearEggs(); awakeningHealing.clear(); awakeningDamage.clear();
        session = UUID.randomUUID(); leapTicks = evasionTicks = awakeningTicks = recoilTicks = 0; evasionUntil = 0; leapTarget = null; awakeningCenter = null;
        entityData.set(LEAPING, false); scripted(false);
    }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel level) { EasterVfx.emit(this, EasterVfx.DEFEAT, this, position(), 40, 2); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { cancelCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("EasterCooldown" + slot, cooldowns[slot]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cancelCombat();
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("EasterCooldown" + slot, 0), 0, INTERVALS[slot]); }
    }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 4}) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("easter_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() { return List.of("Egg Burst: 42 blocks; 5s AI/Staff, 0.3s mounted.", "Spring Leap: 8s. Renewal / Evasive Leap: 14s. Spring Awakening: 85s."); }
}

