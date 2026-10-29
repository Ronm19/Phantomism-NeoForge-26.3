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
import net.ronm19.phantomism.entity.ai.ValentinesSupport;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;
import net.ronm19.phantomism.entity.spawn.HalloweenEncounterSpawner;
import net.ronm19.phantomism.entity.spawn.ValentinesEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.ValentinesVfx;

public final class ValentinesPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int BOLT_RANGE = 42, MOUNTED_INTERVAL = 6, SERENADE_DURATION = 240;
    private static final int[] INTERVALS = {100, 200, 520, 400, 360, 1800};
    private static final String[] KEYS = {"bolt", "dash", "aura", "charm", "embrace", "serenade"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(ValentinesPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DASHING = SynchedEntityData.defineId(ValentinesPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AURA = SynchedEntityData.defineId(ValentinesPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[6];
    private final Set<UUID> projectiles = new HashSet<>(), dashHits = new HashSet<>();
    private final Map<UUID, Float> serenadeDamage = new HashMap<>(), serenadeHealing = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, dashTarget, sequenceRider, ownerContext, lureTarget;
    private Vec3 serenadeCenter, dashDirection = Vec3.ZERO;
    private int dashTicks, auraTicks, serenadeTicks;
    private long nextCombatDecision;
    private double dashTravelled;
    private boolean explicitSelection;

    public ValentinesPhantom(EntityType<? extends ValentinesPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 96).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .25D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .27D).add(Attributes.FLYING_SPEED, .48D);
    }
    public static boolean checkSpawnRules(EntityType<ValentinesPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos point, RandomSource random) {
        return accessor instanceof ServerLevel level && (!naturalHolidaySpawn(reason)
                || festivalAllowsSpawn(HolidayFestival.VALENTINES, reason) && ValentinesEncounterSpawner.habitat(level, point))
                && HalloweenEncounterSpawner.safeSpace(level, point);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.VALENTINES_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.VALENTINES; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(DASHING) ? 1 : 0)); }
    @Override public boolean isUltimateAnimationActive() { return serenadeTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(SCRIPTED, false); data.define(DASHING, false); data.define(AURA, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && !flower(player.getItemInHand(hand))) { return InteractionResult.PASS; }
        return super.mobInteract(player, hand);
    }
    private static boolean flower(net.minecraft.world.item.ItemStack stack) { return stack.is(Items.POPPY) || stack.is(Items.RED_TULIP); }
    public UUID session() { return session; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int dashTicks() { return dashTicks; }
    public int auraTicks() { return auraTicks; }
    public int serenadeTicks() { return serenadeTicks; }
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
    public boolean bolt(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 lead = target.getDeltaMovement().scale(Math.min(5, distanceTo(target) / 1.6D));
        if (lead.lengthSqr() > 9) { lead = lead.normalize().scale(3); }
        Vec3 point = target.getBoundingBox().getCenter().add(lead);
        if (!line(getEyePosition(), point)) { point = target.getBoundingBox().getCenter(); }
        return fire(level, point.subtract(getEyePosition()), false);
    }
    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || dashTicks > 0 || serenadeTicks > 0 || projectiles.size() >= 8
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.8D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        ValentinesHeartProjectile shot = PhantomismContent.VALENTINES_HEART_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.configure(this, session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.6F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        projectiles.add(shot.getUUID()); cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        animate(PhantomAnimationAction.PROJECTILE); ValentinesVfx.emit(this, ValentinesVfx.BOLT, shot, muzzle, 30, 1);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .35F, 1.2F); return true;
    }
    public void heartImpact(ServerLevel level, LivingEntity target, Vec3 point) {
        if (!enabled()) { return; }
        if (target != null && magicDamage(level, target, 7)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, target instanceof Mob mob && GravityControl.movementImmune(mob) ? 20 : 60, 0), this);
        }
        ValentinesVfx.emit(this, ValentinesVfx.IMPACT, null, point, 18, 2); ValentinesVfx.spark(level, point, 6);
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
        if (!enabled() || cooldowns[1] > 0 || serenadeTicks > 0 || dashTicks > 0 || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D
                || target != null && (!validTarget(target) || distanceToSqr(target) > 324 || !line(getEyePosition(), target.getEyePosition()))) { return false; }
        cooldowns[1] = INTERVALS[1]; dashTicks = 14; dashTravelled = 0; dashHits.clear(); dashDirection = aim.normalize();
        dashTarget = target == null ? null : target.getUUID(); sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        scripted(true); entityData.set(DASHING, true); animate(PhantomAnimationAction.CHARGE);
        ValentinesVfx.emit(this, ValentinesVfx.DASH, this, position(), 20, 3); return true;
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
    private void stopDash() { dashTicks = 0; dashTarget = null; dashHits.clear(); entityData.set(DASHING, false); if (serenadeTicks == 0) { scripted(false); } }
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
            ValentinesVfx.emit(this, ValentinesVfx.IMPACT, null, target.position(), 14, 2);
        }
        dashTravelled += before.distanceTo(position());
        if (dashTicks % 3 == 0) { ValentinesVfx.spark(level, position(), 2); }
        if (--dashTicks <= 0) { stopDash(); }
    }
    public boolean aura(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || auraTicks > 0) { return false; }
        cooldowns[2] = INTERVALS[2]; auraTicks = 160; entityData.set(AURA, true); animate(PhantomAnimationAction.SHIELD);
        refreshAura(); ValentinesVfx.emit(this, ValentinesVfx.AURA, this, position(), 160, 12);
        playSound(SoundEvents.BEACON_ACTIVATE, .35F, 1.35F); return true;
    }
    private void refreshAura() { for (LivingEntity target : allies(12)) { ValentinesSupport.protect(this, target); } }
    public boolean charm(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || dashTicks > 0 || serenadeTicks > 0) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 12); if (targets.isEmpty()) { return false; }
        cooldowns[3] = INTERVALS[3]; animate(PhantomAnimationAction.EMPOWER);
        for (LivingEntity target : targets) { ValentinesSupport.distract(this, target, 60); level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, target.getX(), target.getEyeY() + .2D, target.getZ(), 2, .15D, .15D, .15D, .01D); }
        ValentinesVfx.emit(this, ValentinesVfx.CHARM, this, position(), 60, 12); return true;
    }
    public boolean embrace(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || dashTicks > 0 || serenadeTicks > 0) { return false; }
        List<LivingEntity> friends = allies(12).stream().filter(ValentinesSupport::needsCare).toList();
        if (friends.isEmpty()) { return false; }
        boolean helped = false;
        for (LivingEntity friend : friends) {
            boolean cleansed = ValentinesSupport.cleanse(this, friend);
            boolean healed = ValentinesSupport.heal(this, friend, 4);
            helped |= cleansed || healed;
        }
        if (!helped) { return false; }
        cooldowns[4] = INTERVALS[4]; animate(PhantomAnimationAction.BLESSING);
        ValentinesVfx.emit(this, ValentinesVfx.EMBRACE, this, position(), 40, 12);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .45F, 1.2F); return true;
    }
    public boolean serenade(ServerLevel level) {
        if (!enabled() || cooldowns[5] > 0 || serenadeTicks > 0 || dashTicks > 0 || !isLoadedFlightBox(getBoundingBox().inflate(22))
                || enemies(level, position(), 20).isEmpty() && allies(20).stream().noneMatch(friend -> friend.getHealth() < friend.getMaxHealth())) { return false; }
        cooldowns[5] = INTERVALS[5]; serenadeTicks = SERENADE_DURATION; serenadeCenter = position(); serenadeDamage.clear(); serenadeHealing.clear();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); scripted(true); animate(PhantomAnimationAction.ULTIMATE);
        ValentinesVfx.emit(this, ValentinesVfx.SERENADE, this, serenadeCenter, SERENADE_DURATION + 20, 20);
        playSound(SoundEvents.BEACON_POWER_SELECT, .45F, 1.4F); return true;
    }
    private void tickSerenade(ServerLevel level) {
        if (serenadeTicks <= 0) { return; }
        if (riderChanged() || serenadeCenter == null || distanceToSqr(serenadeCenter) > 32 * 32 || !isLoadedFlightBox(new AABB(serenadeCenter, serenadeCenter).inflate(22))) { cancelCombat(); return; }
        int elapsed = SERENADE_DURATION - serenadeTicks;
        if (elapsed < 16) { moveSafely(level, new Vec3(0, .06D, 0)); }
        if (elapsed >= 40 && (elapsed - 40) % 60 == 0) {
            for (LivingEntity target : enemies(level, serenadeCenter, 20)) {
                float amount = Math.min(4, Math.max(0, 24 - serenadeDamage.getOrDefault(target.getUUID(), 0F)));
                if (amount > 0 && magicDamage(level, target, amount)) { serenadeDamage.merge(target.getUUID(), amount, Float::sum); }
            }
            for (LivingEntity friend : allies(20)) {
                float amount = Math.min(4, Math.max(0, 16 - serenadeHealing.getOrDefault(friend.getUUID(), 0F)));
                if (amount > 0 && ValentinesSupport.heal(this, friend, amount)) { serenadeHealing.merge(friend.getUUID(), amount, Float::sum); }
                friend.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, true), this);
            }
            ValentinesVfx.emit(this, ValentinesVfx.WAVE, null, serenadeCenter, 36, 20);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .45F, .9F + elapsed / 600F);
        }
        if (elapsed == 235) {
            for (LivingEntity target : enemies(level, serenadeCenter, 20)) {
                float amount = Math.min(8, Math.max(0, 24 - serenadeDamage.getOrDefault(target.getUUID(), 0F)));
                if (amount > 0 && magicDamage(level, target, amount)) { serenadeDamage.merge(target.getUUID(), amount, Float::sum); }
            }
            ValentinesVfx.emit(this, ValentinesVfx.FINAL, null, serenadeCenter.add(0, 2, 0), 28, 20);
        }
        if (--serenadeTicks <= 0) { serenadeCenter = null; serenadeDamage.clear(); serenadeHealing.clear(); scripted(false); }
    }
    public boolean lureInterested(Player player) {
        return !isTame() && enabled() && player != null && player.isAlive() && !player.isSpectator() && player.level() == level()
                && distanceToSqr(player) <= 144 && (flower(player.getMainHandItem()) || flower(player.getOffhandItem()))
                && line(getEyePosition(), player.getEyePosition());
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!enabled() || isTame() || getTarget() != null || dashTicks > 0 || serenadeTicks > 0) { lureTarget = null; return; }
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
        if (!enabled() || dashTicks > 0 || serenadeTicks > 0) { return dashTicks > 0 || serenadeTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; } nextCombatDecision = level.getGameTime() + 10;
        List<LivingEntity> threats = enemies(level, position(), 20);
        if ((threats.size() >= 3 || threats.size() >= 1 && allies(20).stream().anyMatch(friend -> friend.getHealth() < friend.getMaxHealth() * .4F)) && serenade(level)) { return true; }
        if (allies(12).stream().anyMatch(ValentinesSupport::needsCare) && embrace(level)) { return true; }
        if (!threats.isEmpty() && allies(12).stream().anyMatch(friend -> friend.getHealth() < friend.getMaxHealth() * .65F) && aura(level)) { return true; }
        if (threats.size() >= 2 && charm(level)) { return true; }
        if (!validTarget(target)) { return false; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 324 && dash(level, target)) { return true; }
        return bolt(level, target);
    }
    @Override public void tick() {
        boolean recovering = isHolidayDefeated(); super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        if (recovering && !isHolidayDefeated()) { animate(PhantomAnimationAction.BLESSING); }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        projectiles.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); } ownerContext = context;
        if (!companionAvailable()) { ValentinesSupport.release(this); }
        else if (isTame() && tickCount % 20 == 0) { for (LivingEntity friend : allies(12)) { ValentinesSupport.bond(this, friend); } if (tickCount % 100 == 0) { ValentinesVfx.emit(this, ValentinesVfx.BOND, this, position(), 30, 3); } }
        if (!enabled()) { if (dashTicks > 0 || serenadeTicks > 0 || auraTicks > 0 || !projectiles.isEmpty()) { cancelCombat(); } return; }
        tickDash(level); tickSerenade(level);
        if (!isNoAi() && isTame() && !isVehicle() && getTarget() == null && serenadeTicks == 0 && tickCount % 40 == 0) { embrace(level); }
        if (auraTicks > 0) { if ((160 - auraTicks) % 40 == 0) { for (LivingEntity friend : allies(12)) { ValentinesSupport.heal(this, friend, 1); } } if (auraTicks % 10 == 0) { refreshAura(); } if (--auraTicks == 0) { entityData.set(AURA, false); ValentinesSupport.releaseAura(this); } }
    }
    private void clearProjectiles() {
        if (level() instanceof ServerLevel level) { for (UUID identity : projectiles) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } } }
        projectiles.clear();
    }
    public void cancelCombat() {
        if (level() instanceof ServerLevel) { ValentinesVfx.cancel(this); }
        clearProjectiles(); ValentinesSupport.release(this); serenadeDamage.clear(); serenadeHealing.clear();
        session = UUID.randomUUID(); dashTicks = auraTicks = serenadeTicks = 0; dashTarget = null; serenadeCenter = null; dashHits.clear();
        entityData.set(DASHING, false); entityData.set(AURA, false); scripted(false);
    }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel) { ValentinesVfx.emit(this, ValentinesVfx.DEFEAT, this, position(), 40, 2); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { cancelCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("ValentinesCooldown" + slot, cooldowns[slot]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); cancelCombat(); for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("ValentinesCooldown" + slot, 0), 0, INTERVALS[slot]); } }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 3, 4}) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("valentines_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() { return List.of("Heart Bolt: 42 blocks; 5s AI/Staff, 0.3s mounted.", "Cupid Dash: 10s. Loveguard Aura: 8s active / 26s cooldown.", "Sweet Charm: 3s disruption / 20s cooldown. Embrace of Roses: 18s cooldown.", "Heartstorm Serenade: 12s / 90s / 20 blocks."); }
}
