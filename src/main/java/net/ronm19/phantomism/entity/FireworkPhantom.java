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
import net.ronm19.phantomism.vfx.FireworkVfx;


public final class FireworkPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int BOLT_RANGE = 30, MOUNTED_INTERVAL = 6, FINALE_DURATION = 240;
    private static final int[] INTERVALS = {100, 200, 360, 1800, 600};
    private static final String[] KEYS = {"bolt", "rush", "starburst", "finale", "guard"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(FireworkPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RUSHING = SynchedEntityData.defineId(FireworkPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(FireworkPhantom.class, EntityDataSerializers.INT);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> projectiles = new HashSet<>(), rushHits = new HashSet<>();
    private final Map<UUID, Float> finaleDamage = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, rushTarget, sequenceRider, ownerContext, lureTarget;
    private Vec3 finaleCenter, rushDirection = Vec3.ZERO, lastEnergyPoint;
    private int rushTicks, finaleTicks, finalePower, idleFinaleTicks;
    private long nextCombatDecision, nextEnergyHit, lastEnergyActivity, nextFestivalCheck;
    private double rushTravelled, flightDistance;
    private boolean explicitSelection, julySpirit;
    public FireworkPhantom(EntityType<? extends FireworkPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .35D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .26D).add(Attributes.FLYING_SPEED, .47D);
    }
    public static boolean checkSpawnRules(EntityType<FireworkPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos point, RandomSource random) {
        if (!(accessor instanceof ServerLevel level)) { return false; }
        return (!naturalHolidaySpawn(reason) || festivalAllowsSpawn(HolidayFestival.FIREWORK, reason)
                && net.ronm19.phantomism.entity.spawn.FireworkEncounterSpawner.habitat(level, point))
                && HalloweenEncounterSpawner.safeSpace(level, point);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.FIREWORK_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.FIREWORK; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return finaleTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(RUSHING) ? 1 : 0)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(SCRIPTED, false); data.define(RUSHING, false); data.define(CHARGE, 0); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    @Override public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!isTame() && !player.getItemInHand(hand).is(net.minecraft.world.item.Items.FIREWORK_ROCKET)) { return net.minecraft.world.InteractionResult.PASS; }
        return super.mobInteract(player, hand);
    }
    public UUID session() { return session; }
    public int charge() { return entityData.get(CHARGE); }
    public int chargeStage() { return Math.min(3, charge() / 25); }
    public int finaleTicks() { return finaleTicks; }
    public int rushTicks() { return rushTicks; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public boolean seasonalSpiritActive() { return julySpirit; }
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



    private void gainEnergy(int amount) {
        if (!(level() instanceof ServerLevel) || !enabled() || amount <= 0 || finaleTicks > 0) { return; }
        entityData.set(CHARGE, Math.min(100, charge() + Math.min(8, amount))); lastEnergyActivity = level().getGameTime();
    }
    private void spendEnergy(int amount) { entityData.set(CHARGE, Math.max(0, charge() - Math.max(0, amount))); }
    private void awardHit() {
        if (level().getGameTime() >= nextEnergyHit && finaleTicks == 0) { nextEnergyHit = level().getGameTime() + 4; gainEnergy(julySpirit ? 5 : 4); }
    }
    private float attackScale() { return 1 + chargeStage() * .05F; }
    public boolean bolt(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 lead = target.getDeltaMovement().scale(Math.min(5, distanceTo(target) / 1.65D));
        if (lead.lengthSqr() > 9) { lead = lead.normalize().scale(3); }
        Vec3 destination = target.getBoundingBox().getCenter().add(lead);
        if (!line(getEyePosition(), destination)) { destination = target.getBoundingBox().getCenter(); }
        return fire(level, destination.subtract(getEyePosition()), false);
    }
    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || rushTicks > 0 || finaleTicks > 0
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        if (!launch(level, aim, false)) { return false; }
        cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.PROJECTILE); return true;
    }
    private boolean launch(ServerLevel level, Vec3 aim, boolean finale) {
        if (!enabled() || projectiles.size() >= 12 || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.8D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        FireworkRocketProjectile rocket = PhantomismContent.FIREWORK_ROCKET_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (rocket == null) { return false; }
        rocket.configure(this, session, random.nextInt(3), finale); rocket.setPos(muzzle); rocket.shoot(direction.x, direction.y, direction.z, 1.65F, 0);
        if (!level.addFreshEntity(rocket)) { return false; }
        projectiles.add(rocket.getUUID());
        FireworkVfx.emit(this, FireworkVfx.BOLT, rocket, muzzle, 24, 1);
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, .22F, 1.1F); return true;
    }
    public void rocketImpact(ServerLevel level, LivingEntity primary, Vec3 point, boolean finale) {
        if (!enabled() || finale && finaleTicks <= 0) { return; }
        float scale = finale ? 1 + finalePower * .05F : attackScale();
        if (primary != null) { impactDamage(level, primary, (finale ? 4 : 7) * scale, finale); }
        for (LivingEntity target : enemies(level, point, finale ? 2.5D : 3)) {
            if (target != primary) { impactDamage(level, target, (finale ? 2 : 2.5F) * scale, finale); }
        }
        FireworkVfx.burst(level, point, finale ? 6 : 9);
        FireworkVfx.emit(this, FireworkVfx.IMPACT, null, point, 16, 3);
        playSound(SoundEvents.FIREWORK_ROCKET_BLAST, .28F, 1.25F);
    }
    private boolean impactDamage(ServerLevel level, LivingEntity target, float amount, boolean finale) {
        if (finale) {
            if (finaleTicks <= 0) { return false; }
            float spent = finaleDamage.getOrDefault(target.getUUID(), 0F);
            amount = Math.min(amount, Math.max(0, 48 - spent));
            if (amount <= 0 || !blastDamage(level, target, amount)) { return false; }
            finaleDamage.put(target.getUUID(), spent + amount); return true;
        }
        if (blastDamage(level, target, amount)) { awardHit(); return true; }
        return false;
    }
    private boolean blastDamage(ServerLevel level, LivingEntity target, float amount) {
        return enabled() && validTarget(target) && target.hurtServer(level, damageSources().explosion(this, this), amount);
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return fire(level, rider.getViewVector(1), true); }
    public boolean activateRiddenRush(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 18).stream()
                .filter(enemy -> enemy.getEyePosition().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1)) > .65D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return startRush(level, target, rider.getViewVector(1));
    }
    public boolean rush(ServerLevel level, LivingEntity target) { return startRush(level, target, getControllingPassenger() instanceof Player rider ? rider.getViewVector(1) : getViewVector(1)); }
    private boolean startRush(ServerLevel level, LivingEntity target, Vec3 aim) {
        if (!enabled() || cooldowns[1] > 0 || finaleTicks > 0 || rushTicks > 0 || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D
                || target != null && (!validTarget(target) || distanceToSqr(target) > 324 || !line(getEyePosition(), target.getEyePosition()))) { return false; }
        cooldowns[1] = INTERVALS[1]; rushTicks = 14; rushTravelled = 0; rushHits.clear(); spendEnergy(10);
        rushTarget = target == null ? null : target.getUUID(); rushDirection = aim.normalize();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); scripted(true); entityData.set(RUSHING, true);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.CHARGE);
        FireworkVfx.emit(this, FireworkVfx.RUSH, this, position(), 20, 3);
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, .5F, .8F); return true;
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



    private void stopRush() { rushTicks = 0; rushTarget = null; rushHits.clear(); entityData.set(RUSHING, false); if (finaleTicks == 0) { scripted(false); } }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; }
        if (riderChanged()) { stopRush(); return; }
        if (rushTarget != null) {
            Entity entity = level.getEntity(rushTarget);
            if (!(entity instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { stopRush(); return; }
            if (distanceToSqr(target) > 4) { rushDirection = target.position().subtract(position()).normalize(); }
        }
        Vec3 step = rushDirection.scale(Math.min(1.3D, 18 - rushTravelled)), before = position();
        if (rushTravelled >= 18 || !moveSafely(level, step)) { stopRush(); return; }
        AABB sweep = getBoundingBox().move(before.subtract(position())).expandTowards(position().subtract(before)).inflate(.5D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, this::validTarget)) {
            if (rushHits.size() >= 12 || rushHits.contains(target.getUUID()) || !line(before.add(0, .3D, 0), target.getEyePosition())) { continue; }
            AABB contact = target.getBoundingBox().inflate(.8D);
            if (!contact.contains(before) && contact.clip(before, position()).isEmpty()) { continue; }
            rushHits.add(target.getUUID()); if (damage(level, target, 14 * attackScale())) { awardHit(); }
            if (target.getUUID().equals(rushTarget)) { rushTarget = null; }
            FireworkVfx.emit(this, FireworkVfx.IMPACT, null, target.position(), 12, 2);
        }
        rushTravelled += before.distanceTo(position());
        if (rushTicks % 3 == 0) { FireworkVfx.trail(level, position(), 2); }
        if (--rushTicks <= 0) { stopRush(); }
    }
    public boolean starburst(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || rushTicks > 0 || finaleTicks > 0) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 7); if (targets.isEmpty()) { return false; }
        float amount = 12 * attackScale(); cooldowns[2] = INTERVALS[2]; spendEnergy(20);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.EMPOWER);
        for (LivingEntity target : targets) {
            if (damage(level, target, amount)) {
                awardHit();
                if (!(target instanceof Mob mob && GravityControl.movementImmune(mob))) { target.knockback(.5D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 0); }
            }
        }
        FireworkVfx.emit(this, FireworkVfx.STARBURST, null, position(), 24, 7);
        FireworkVfx.burst(level, position().add(0, 1, 0), 12); playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, .6F, 1.1F); return true;
    }
    public boolean guard(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0) { return false; }
        boolean protectedAny = false;
        for (LivingEntity target : allies(10)) {
            if (net.ronm19.phantomism.entity.ai.FireworkSupport.protect(this, target, 80)) {
                FireworkVfx.emit(this, FireworkVfx.GUARD, target, target.position(), 80, 2); protectedAny = true;
            }
        }
        if (!protectedAny) { return false; }
        cooldowns[4] = INTERVALS[4]; animate(net.ronm19.phantomism.animation.PhantomAnimationAction.SHIELD);
        playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, .4F, 1.2F); return true;
    }
    public boolean finale(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || rushTicks > 0 || finaleTicks > 0 || enemies(level, position(), 24).isEmpty()
                || !isLoadedFlightBox(getBoundingBox().inflate(26))) { return false; }
        cooldowns[3] = INTERVALS[3]; finaleTicks = FINALE_DURATION; finalePower = chargeStage(); spendEnergy(60);
        finaleCenter = position(); finaleDamage.clear(); idleFinaleTicks = 0;
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); scripted(true);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE);
        FireworkVfx.emit(this, FireworkVfx.FINALE, null, finaleCenter, FINALE_DURATION, 24);
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, .5F, .75F); return true;
    }
    private void tickFinale(ServerLevel level) {
        if (finaleTicks <= 0) { return; }
        if (riderChanged() || finaleCenter == null || distanceToSqr(finaleCenter) > 32 * 32 || !isLoadedFlightBox(new AABB(finaleCenter, finaleCenter).inflate(26))) { cancelCombat(); return; }
        int elapsed = FINALE_DURATION - finaleTicks;
        if (elapsed < 16) { moveSafely(level, new Vec3(0, .065D, 0)); }
        if (elapsed % 20 == 0) {
            List<LivingEntity> targets = enemies(level, finaleCenter, 24);
            if (targets.isEmpty()) { idleFinaleTicks += 20; if (idleFinaleTicks >= 40) { cancelCombat(); return; } }
            else {
                idleFinaleTicks = 0;
                for (int rocket = 0; rocket < 3; rocket++) {
                    LivingEntity target = targets.get(Math.floorMod(elapsed / 20 + rocket, targets.size()));
                    launch(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), true);
                }
            }
            if (elapsed % 40 == 0) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE); }
            FireworkVfx.emit(this, FireworkVfx.VOLLEY, null, finaleCenter.add(0, 3, 0), 22, 12);
        }
        if (elapsed == 235) {
            for (LivingEntity target : enemies(level, finaleCenter, 24)) { impactDamage(level, target, 8 + finalePower, true); }
            FireworkVfx.emit(this, FireworkVfx.FINAL_BURST, null, finaleCenter.add(0, 3, 0), 28, 20);
            FireworkVfx.burst(level, position().add(0, 2, 0), 12);
            playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, .65F, 1); playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, .55F, 1);
        }
        if (--finaleTicks <= 0) { clearProjectiles(); finaleCenter = null; finaleDamage.clear(); scripted(false); }
    }
    public boolean lureInterested(Player player) {
        return !isTame() && enabled() && player != null && player.isAlive() && !player.isSpectator() && player.level() == level()
                && distanceToSqr(player) <= 144 && (player.getMainHandItem().is(net.minecraft.world.item.Items.FIREWORK_ROCKET)
                || player.getOffhandItem().is(net.minecraft.world.item.Items.FIREWORK_ROCKET)) && line(getEyePosition(), player.getEyePosition());
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!enabled() || isTame() || getTarget() != null || rushTicks > 0 || finaleTicks > 0) { lureTarget = null; return; }
        if (tickCount % 10 == 0 || lureTarget == null) {
            lureTarget = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12), this::lureInterested).stream()
                    .min(Comparator.comparingDouble(this::distanceToSqr)).map(Entity::getUUID).orElse(null);
        }
        if (lureTarget != null && level.getEntity(lureTarget) instanceof Player player && lureInterested(player)) {
            getLookControl().setLookAt(player, 20, 20);
            if (distanceToSqr(player) > 9) { getMoveControl().setWantedPosition(player.getX(), player.getY() + 1.5D, player.getZ(), .8D); }
            else { getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0); }
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || rushTicks > 0 || finaleTicks > 0) { return rushTicks > 0 || finaleTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; } nextCombatDecision = level.getGameTime() + 10;
        int threats = enemies(level, position(), 24).size();
        if (threats >= 3 && finale(level)) { return true; }
        if (getHealth() < getMaxHealth() * .45F && guard(level)) { return true; }
        if (enemies(level, position(), 7).size() >= 2 && starburst(level)) { return true; }
        if (!validTarget(target)) { return false; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 324 && rush(level, target)) { return true; }
        return bolt(level, target);
    }
    @Override public void tick() {
        boolean recovering = isHolidayDefeated(); super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        if (recovering && !isHolidayDefeated()) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BLESSING); }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        projectiles.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); } ownerContext = context;
        if (!enabled()) { lastEnergyPoint = position(); flightDistance = 0; if (rushTicks > 0 || finaleTicks > 0 || !projectiles.isEmpty()) { cancelCombat(); } return; }
        if (level.getGameTime() >= nextFestivalCheck) {
            julySpirit = festival().active(net.ronm19.phantomism.entity.holiday.HolidayCalendar.today(), Config.fireworkFestivalMonth()); nextFestivalCheck = level.getGameTime() + 200;
        }
        tickRush(level); tickFinale(level);
        double distance = lastEnergyPoint == null ? 0 : position().distanceTo(lastEnergyPoint); lastEnergyPoint = position();
        if (rushTicks == 0 && finaleTicks == 0 && !onGround() && distance > .01D && distance < 3) { flightDistance = Math.min(20, flightDistance + distance); }
        if (tickCount % 20 == 0) { if (flightDistance >= 4) { gainEnergy(julySpirit ? 3 : 2); } flightDistance = 0; }
        if (tickCount % 100 == 0 && rushTicks == 0 && finaleTicks == 0 && getTarget() == null && level.getGameTime() - lastEnergyActivity > 100) { spendEnergy(1); }
        if (chargeStage() > 0 && tickCount % 20 == 0 && getDeltaMovement().lengthSqr() > .02D) { FireworkVfx.trail(level, position(), chargeStage()); }
    }
    private void clearProjectiles() {
        if (level() instanceof ServerLevel level) { for (UUID identity : projectiles) { Entity rocket = level.getEntity(identity); if (rocket != null) { rocket.discard(); } } }
        projectiles.clear();
    }
    public void cancelCombat() {
        if (level() instanceof ServerLevel) { FireworkVfx.cancel(this); }
        clearProjectiles(); finaleDamage.clear(); net.ronm19.phantomism.entity.ai.FireworkSupport.release(this);
        session = UUID.randomUUID(); rushTicks = finaleTicks = 0; rushTarget = null; finaleCenter = null; rushHits.clear();
        entityData.set(RUSHING, false); scripted(false);
    }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel level) { FireworkVfx.emit(this, FireworkVfx.DEFEAT, this, position(), 40, 2); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { cancelCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("CelebrationCharge", charge());
        for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("FireworkCooldown" + slot, cooldowns[slot]); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cancelCombat(); entityData.set(CHARGE, Math.clamp(input.getIntOr("CelebrationCharge", 0), 0, 100));
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("FireworkCooldown" + slot, 0), 0, INTERVALS[slot]); }
    }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 4}) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("firework_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() { return List.of("Celebration Charge: " + charge() + "/100.", "Firework Bolt: 30 blocks; 5s AI/Staff, 0.3s mounted.", "Rush: 10s. Starburst: 18s. Blast Guard: 30s. Grand Finale: 12s active / 90s cooldown / 24 blocks."); }
}

