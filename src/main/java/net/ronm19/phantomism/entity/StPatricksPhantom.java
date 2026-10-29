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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.PhantomHornRally;
import net.ronm19.phantomism.entity.ai.StPatricksSupport;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;
import net.ronm19.phantomism.entity.spawn.HalloweenEncounterSpawner;
import net.ronm19.phantomism.entity.spawn.StPatricksEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.StPatricksVfx;

public final class StPatricksPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int BOLT_RANGE = 42, MOUNTED_INTERVAL = 6, SKY_DURATION = 240;
    private static final int[] INTERVALS = {100, 200, 520, 400, 1800};
    private static final String[] KEYS = {"burst", "dash", "aura", "trick", "sky"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(StPatricksPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DASHING = SynchedEntityData.defineId(StPatricksPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AURA = SynchedEntityData.defineId(StPatricksPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> projectiles = new HashSet<>(), dashHits = new HashSet<>();
    private final Map<UUID, Float> skyDamage = new HashMap<>(), skyHealing = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, dashTarget, sequenceRider, ownerContext, lureTarget;
    private Vec3 skyCenter, dashDirection = Vec3.ZERO;
    private int dashTicks, auraTicks, skyTicks;
    private long nextCombatDecision;
    private double dashTravelled;
    private boolean explicitSelection;

    public StPatricksPhantom(EntityType<? extends StPatricksPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 96).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .25D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .27D).add(Attributes.FLYING_SPEED, .48D);
    }
    public static boolean checkSpawnRules(EntityType<StPatricksPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos point, RandomSource random) {
        return accessor instanceof ServerLevel level && (!naturalHolidaySpawn(reason)
                || festivalAllowsSpawn(HolidayFestival.ST_PATRICKS, reason) && StPatricksEncounterSpawner.habitat(level, point))
                && HalloweenEncounterSpawner.safeSpace(level, point);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ST_PATRICKS_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.ST_PATRICKS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(DASHING) ? 1 : 0)); }
    @Override public boolean isUltimateAnimationActive() { return skyTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(SCRIPTED, false); data.define(DASHING, false); data.define(AURA, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && !player.getItemInHand(hand).is(Items.EMERALD)) { return InteractionResult.PASS; }
        return super.mobInteract(player, hand);
    }
    public UUID session() { return session; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int dashTicks() { return dashTicks; }
    public int auraTicks() { return auraTicks; }
    public int skyTicks() { return skyTicks; }
    public boolean auraVisible() { return entityData.get(AURA); }
    public boolean companionAvailable() {
        return isAlive() && !isRemoved() && !isBaby() && !isHolidayDefeated()
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean enabled() {
        return companionAvailable() && !PhantomHornRally.returning(this) && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT);
    }
    public boolean ally(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && (target == this || isTame() && (target == getOwner()
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())
                || CelestialBond.authorizedAlly(this, target) || !(target instanceof Enemy) && isAlliedTo(target)));
    }
    public List<LivingEntity> allies(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::ally).stream()
                .filter(target -> distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition()))
                .sorted(Comparator.comparingDouble((LivingEntity target) -> target.getHealth() / Math.max(1, target.getMaxHealth()) - (target == getOwner() ? .1D : 0)))
                .limit(8).toList();
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
                || attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && ally(mob.getTarget()));
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override public void setCombatTarget(LivingEntity target) {
        explicitSelection = true;
        try { if (safeEnemy(target)) { orderedTarget = target.getUUID(); super.setCombatTarget(target); } }
        finally { explicitSelection = false; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return enabled() && validTarget(target) && (attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && ally(mob.getTarget()))
                && line(getEyePosition(), target.getEyePosition());
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.1D))
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
    private boolean magicDamage(ServerLevel level, LivingEntity target, float amount) {
        return enabled() && validTarget(target) && target.hurtServer(level, damageSources().indirectMagic(this, this), amount);
    }
    public boolean burst(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 lead = target.getDeltaMovement().scale(Math.min(5, distanceTo(target) / 1.6D));
        if (lead.lengthSqr() > 9) { lead = lead.normalize().scale(3); }
        Vec3 point = target.getBoundingBox().getCenter().add(lead);
        if (!line(getEyePosition(), point)) { point = target.getBoundingBox().getCenter(); }
        return fire(level, point.subtract(getEyePosition()), false);
    }
    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || dashTicks > 0 || skyTicks > 0 || projectiles.size() >= 8
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.8D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        StPatricksShamrockProjectile shot = PhantomismContent.ST_PATRICKS_SHAMROCK_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.configure(this, session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.6F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        projectiles.add(shot.getUUID()); cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        animate(PhantomAnimationAction.PROJECTILE); StPatricksVfx.emit(this, StPatricksVfx.BURST, shot, muzzle, 30, 1);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .35F, 1.2F); return true;
    }
    public void shamrockImpact(ServerLevel level, LivingEntity target, Vec3 point) {
        if (!enabled()) { return; }
        if (target != null && magicDamage(level, target, 7)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, target instanceof Mob mob && GravityControl.movementImmune(mob) ? 20 : 60, 0), this);
        }
        StPatricksVfx.emit(this, StPatricksVfx.IMPACT, null, point, 18, 2); StPatricksVfx.spark(level, point, 6);
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return fire(level, rider.getViewVector(1), true); }
    public boolean activateRiddenDash(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 18).stream().filter(enemy -> enemy.getEyePosition().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1)) > .65D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return startDash(level, target, rider.getViewVector(1));
    }
    public boolean dash(ServerLevel level, LivingEntity target) { return startDash(level, target, getViewVector(1)); }
    private boolean startDash(ServerLevel level, LivingEntity target, Vec3 aim) {
        if (!enabled() || cooldowns[1] > 0 || skyTicks > 0 || dashTicks > 0 || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D
                || target != null && (!validTarget(target) || distanceToSqr(target) > 324 || !line(getEyePosition(), target.getEyePosition()))) { return false; }
        cooldowns[1] = INTERVALS[1]; dashTicks = 14; dashTravelled = 0; dashHits.clear(); dashDirection = aim.normalize();
        dashTarget = target == null ? null : target.getUUID(); sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        scripted(true); entityData.set(DASHING, true); animate(PhantomAnimationAction.CHARGE);
        StPatricksVfx.emit(this, StPatricksVfx.DASH, this, position(), 20, 3); return true;
    }
    private void scripted(boolean active) {
        boolean previous = entityData.get(SCRIPTED); entityData.set(SCRIPTED, active);
        if (previous == active) { return; }
        setDeltaMovement(Vec3.ZERO); needsSync = true;
        if (level() instanceof ServerLevel && getControllingPassenger() instanceof ServerPlayer rider && rider.connection != null) {
            rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(SCRIPTED, active))));
            if (!active) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
        }
    }
    private boolean moveSafely(ServerLevel level, Vec3 step) {
        AABB destination = getBoundingBox().move(step);
        if (!isLoadedFlightBox(getBoundingBox().expandTowards(step)) || !level.getWorldBorder().isWithinBounds(destination)
                || destination.minY <= level.getMinY() || destination.maxY >= level.getMaxY()
                || !level.noBlockCollision(this, destination) || level.containsAnyLiquid(destination)) { return false; }
        for (Entity passenger : getPassengers()) {
            AABB box = passenger.getBoundingBox().move(step);
            if (!level.noBlockCollision(passenger, box) || level.containsAnyLiquid(box)) { return false; }
        }
        setDeltaMovement(Vec3.ZERO); move(MoverType.SELF, step); needsSync = true;
        for (Entity passenger : getPassengers()) { positionRider(passenger); }
        return true;
    }
    private boolean riderChanged() { return !Objects.equals(sequenceRider, getControllingPassenger() == null ? null : getControllingPassenger().getUUID()); }
    private void stopDash() { dashTicks = 0; dashTarget = null; dashHits.clear(); entityData.set(DASHING, false); if (skyTicks == 0) { scripted(false); } }
    private void tickDash(ServerLevel level) {
        if (dashTicks <= 0) { return; }
        if (riderChanged()) { stopDash(); return; }
        if (dashTarget != null) {
            Entity entity = level.getEntity(dashTarget);
            if (!(entity instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { stopDash(); return; }
            if (distanceToSqr(target) > 4) { dashDirection = target.position().subtract(position()).normalize(); }
        }
        Vec3 before = position(), step = dashDirection.scale(Math.min(1.3D, 18 - dashTravelled));
        if (dashTravelled >= 18 || !moveSafely(level, step)) { stopDash(); return; }
        AABB sweep = getBoundingBox().move(before.subtract(position())).expandTowards(position().subtract(before)).inflate(.5D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, this::validTarget)) {
            if (dashHits.size() >= 12 || dashHits.contains(target.getUUID()) || !line(before.add(0, .3D, 0), target.getEyePosition())) { continue; }
            AABB bounds = target.getBoundingBox().inflate(.8D);
            if (!bounds.contains(before) && bounds.clip(before, position()).isEmpty()) { continue; }
            dashHits.add(target.getUUID()); damage(level, target, 14);
            if (target.getUUID().equals(dashTarget)) { dashTarget = null; }
            StPatricksVfx.emit(this, StPatricksVfx.IMPACT, null, target.position(), 14, 2);
        }
        dashTravelled += before.distanceTo(position());
        if (dashTicks % 3 == 0) { StPatricksVfx.spark(level, position(), 2); }
        if (--dashTicks <= 0) { stopDash(); }
    }
    public boolean aura(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || auraTicks > 0) { return false; }
        cooldowns[2] = INTERVALS[2]; auraTicks = 160; entityData.set(AURA, true); animate(PhantomAnimationAction.SHIELD);
        refreshAura(); StPatricksVfx.emit(this, StPatricksVfx.AURA, this, position(), 160, 12);
        playSound(SoundEvents.BEACON_ACTIVATE, .35F, 1.35F); return true;
    }
    private void refreshAura() { for (LivingEntity target : allies(12)) { StPatricksSupport.protect(this, target); } }
    public boolean trick(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || dashTicks > 0 || skyTicks > 0) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 12); if (targets.isEmpty()) { return false; }
        cooldowns[3] = INTERVALS[3]; animate(PhantomAnimationAction.EMPOWER);
        for (LivingEntity target : targets) { StPatricksSupport.distract(this, target, 60); }
        StPatricksVfx.emit(this, StPatricksVfx.TRICK, this, position(), 60, 12); return true;
    }
    public boolean sky(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || skyTicks > 0 || dashTicks > 0 || !isLoadedFlightBox(getBoundingBox().inflate(22))
                || enemies(level, position(), 20).isEmpty() && allies(20).stream().noneMatch(friend -> friend.getHealth() < friend.getMaxHealth())) { return false; }
        cooldowns[4] = INTERVALS[4]; skyTicks = SKY_DURATION; skyCenter = position(); skyDamage.clear(); skyHealing.clear();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); scripted(true); animate(PhantomAnimationAction.ULTIMATE);
        StPatricksVfx.emit(this, StPatricksVfx.SKY, this, skyCenter, SKY_DURATION + 20, 20);
        playSound(SoundEvents.BEACON_POWER_SELECT, .45F, 1.4F); return true;
    }
    private void tickSky(ServerLevel level) {
        if (skyTicks <= 0) { return; }
        if (riderChanged() || skyCenter == null || distanceToSqr(skyCenter) > 32 * 32 || !isLoadedFlightBox(new AABB(skyCenter, skyCenter).inflate(22))) { cancelCombat(); return; }
        int elapsed = SKY_DURATION - skyTicks;
        if (elapsed < 16) { moveSafely(level, new Vec3(0, .06D, 0)); }
        if (elapsed >= 40 && (elapsed - 40) % 60 == 0) {
            for (LivingEntity target : enemies(level, skyCenter, 20)) {
                float amount = Math.min(6, Math.max(0, 30 - skyDamage.getOrDefault(target.getUUID(), 0F)));
                if (amount > 0 && magicDamage(level, target, amount)) { skyDamage.merge(target.getUUID(), amount, Float::sum); }
            }
            for (LivingEntity friend : allies(20)) {
                float amount = Math.min(4, Math.max(0, 16 - skyHealing.getOrDefault(friend.getUUID(), 0F)));
                if (amount > 0 && StPatricksSupport.heal(this, friend, amount)) { skyHealing.merge(friend.getUUID(), amount, Float::sum); }
                friend.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0), this);
                friend.addEffect(new MobEffectInstance(MobEffects.LUCK, 60, 0), this);
            }
            StPatricksVfx.emit(this, StPatricksVfx.WAVE, null, skyCenter, 36, 20);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .45F, .9F + elapsed / 600F);
        }
        if (elapsed == 235) {
            for (LivingEntity target : enemies(level, skyCenter, 20)) {
                float amount = Math.min(6, Math.max(0, 30 - skyDamage.getOrDefault(target.getUUID(), 0F)));
                if (amount > 0 && magicDamage(level, target, amount)) { skyDamage.merge(target.getUUID(), amount, Float::sum); }
            }
            StPatricksVfx.emit(this, StPatricksVfx.FINAL, null, skyCenter.add(0, 2, 0), 28, 20);
        }
        if (--skyTicks <= 0) { skyCenter = null; skyDamage.clear(); skyHealing.clear(); scripted(false); }
    }
    public boolean lureInterested(Player player) {
        return !isTame() && enabled() && player != null && player.isAlive() && !player.isSpectator() && player.level() == level()
                && distanceToSqr(player) <= 144 && (player.getMainHandItem().is(Items.EMERALD) || player.getOffhandItem().is(Items.EMERALD))
                && line(getEyePosition(), player.getEyePosition());
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!enabled() || isTame() || getTarget() != null || dashTicks > 0 || skyTicks > 0) { lureTarget = null; return; }
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
        if (!enabled() || dashTicks > 0 || skyTicks > 0) { return dashTicks > 0 || skyTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; } nextCombatDecision = level.getGameTime() + 10;
        List<LivingEntity> threats = enemies(level, position(), 20);
        if ((threats.size() >= 3 || threats.size() >= 1 && allies(20).stream().anyMatch(friend -> friend.getHealth() < friend.getMaxHealth() * .4F)) && sky(level)) { return true; }
        if (!threats.isEmpty() && allies(12).stream().anyMatch(friend -> friend.getHealth() < friend.getMaxHealth() * .65F) && aura(level)) { return true; }
        if (threats.size() >= 2 && trick(level)) { return true; }
        if (!validTarget(target)) { return false; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 324 && dash(level, target)) { return true; }
        return burst(level, target);
    }
    @Override public void tick() {
        boolean recovering = isHolidayDefeated(); super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        if (recovering && !isHolidayDefeated()) { animate(PhantomAnimationAction.BLESSING); }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        projectiles.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); } ownerContext = context;
        if (!companionAvailable()) { StPatricksSupport.release(this); }
        else if (isTame() && tickCount % 20 == 0) { for (LivingEntity friend : allies(12)) { StPatricksSupport.favor(this, friend); } }
        if (!enabled()) { if (dashTicks > 0 || skyTicks > 0 || auraTicks > 0 || !projectiles.isEmpty()) { cancelCombat(); } return; }
        tickDash(level); tickSky(level);
        if (auraTicks > 0) { if (auraTicks % 10 == 0) { refreshAura(); } if (--auraTicks == 0) { entityData.set(AURA, false); StPatricksSupport.releaseAura(this); } }
    }
    private void clearProjectiles() {
        if (level() instanceof ServerLevel level) { for (UUID identity : projectiles) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } } }
        projectiles.clear();
    }
    public void cancelCombat() {
        if (level() instanceof ServerLevel) { StPatricksVfx.cancel(this); }
        clearProjectiles(); StPatricksSupport.release(this); skyDamage.clear(); skyHealing.clear();
        session = UUID.randomUUID(); dashTicks = auraTicks = skyTicks = 0; dashTarget = null; skyCenter = null; dashHits.clear();
        entityData.set(DASHING, false); entityData.set(AURA, false); scripted(false);
    }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel) { StPatricksVfx.emit(this, StPatricksVfx.DEFEAT, this, position(), 40, 2); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { cancelCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("StPatricksCooldown" + slot, cooldowns[slot]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); cancelCombat(); for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("StPatricksCooldown" + slot, 0), 0, INTERVALS[slot]); } }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 3}) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("st_patricks_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() { return List.of("Lucky Burst: 42 blocks; 5s AI/Staff, 0.3s mounted.", "Clover Dash: 10s. Golden Aura: 8s active / 26s cooldown.", "Trickster's Luck: 3s disruption / 20s cooldown. Shamrock Sky: 12s / 90s / 20 blocks."); }
}
