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
import net.ronm19.phantomism.vfx.ChristmasVfx;


public final class ChristmasPhantom extends AbstractHolidayPhantom implements CooldownDiscipline.Participant {
    public static final int SNOWBALL_RANGE = 42, MOUNTED_INTERVAL = 6, MIRACLE_DURATION = 120;
    private static final int[] INTERVALS = {100, 200, 240, 1800, 600};
    private static final String[] KEYS = {"snowball", "dive", "gift", "miracle", "guard"};
    private static final EntityDataAccessor<Boolean> SCRIPTED = SynchedEntityData.defineId(ChristmasPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(ChristmasPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> projectiles = new HashSet<>();
    private final List<UUID> gifts = new ArrayList<>();
    private final Map<UUID, Float> miracleHealing = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, diveTarget, sequenceRider, ownerContext, giftTarget;
    private Vec3 miracleCenter;
    private int diveTicks, miracleTicks, giftAge;
    private double diveTravelled;
    private boolean explicitSelection;
    private long nextCombatDecision;
    public ChristmasPhantom(EntityType<? extends ChristmasPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 96).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 3).add(Attributes.KNOCKBACK_RESISTANCE, .35D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .44D);
    }
    public static boolean checkSpawnRules(EntityType<ChristmasPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level)) { return false; }
        return (!naturalHolidaySpawn(reason) || festivalAllowsSpawn(HolidayFestival.CHRISTMAS, reason)
                && net.ronm19.phantomism.entity.spawn.ChristmasEncounterSpawner.habitat(level, position))
                && HalloweenEncounterSpawner.safeSpace(level, position);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.CHRISTMAS_PHANTOM_STATS; }
    @Override public HolidayFestival festival() { return HolidayFestival.CHRISTMAS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return miracleTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public byte getPhantomAnimationFlags() { return (byte)(super.getPhantomAnimationFlags() | (entityData.get(DIVING) ? 1 : 0)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(SCRIPTED, false); data.define(DIVING, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(SCRIPTED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(SCRIPTED)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    public UUID session() { return session; }
    @Override public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!isTame() && !player.getItemInHand(hand).is(net.minecraft.world.item.Items.SNOWBALL)) { return net.minecraft.world.InteractionResult.PASS; }
        return super.mobInteract(player, hand);
    }
    public int miracleTicks() { return miracleTicks; }
    public int diveTicks() { return diveTicks; }
    public int cooldown(int slot) { return cooldowns[slot]; }
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
    public boolean cheer(ServerLevel level) {
        if (!enabled()) { return false; }
        boolean healed = false;
        for (LivingEntity ally : allies(12).stream().filter(target -> target.getHealth() < target.getMaxHealth()).limit(3).toList()) {
            if (net.ronm19.phantomism.entity.ai.ChristmasSupport.claimCheer(ally) && healAlly(ally, 1, 12) > 0) {
                ChristmasVfx.sparkle(level, ally.position().add(0, .7D, 0), 3); healed = true;
            }
        }
        return healed;
    }
    public boolean gift(ServerLevel level) {
        return allies(16).stream().filter(target -> target.getMaxHealth() - target.getHealth() >= 3).findFirst().map(target -> gift(level, target)).orElse(false);
    }
    public boolean gift(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || giftTarget != null || miracleTicks > 0 || !ally(target)
                || distanceToSqr(target) > 16 * 16 || !line(getEyePosition(), target.getEyePosition()) || target.getMaxHealth() - target.getHealth() < 3) { return false; }
        ChristmasGift present = ChristmasGift.create(this, target.position().add(1, .4D, 0), 40, 18, false);
        if (present == null) { return false; }
        gifts.add(present.getUUID()); giftTarget = target.getUUID(); giftAge = 0; cooldowns[2] = INTERVALS[2];
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BLESSING);
        ChristmasVfx.emit(this, ChristmasVfx.GIFT, present, present.position(), 40, 3);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .6F, 1.2F); return true;
    }
    public boolean guard(ServerLevel level) {
        LivingEntity target = allies(16).stream().filter(ally -> ally.getHealth() < ally.getMaxHealth() * .65F
                || ally.getLastHurtByMob() != null && ally.tickCount - ally.getLastHurtByMobTimestamp() < 60).findFirst().orElse(null);
        return guard(level, target);
    }
    public boolean guard(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[4] > 0 || !ally(target) || distanceToSqr(target) > 16 * 16 || !line(getEyePosition(), target.getEyePosition())
                || !net.ronm19.phantomism.entity.ai.ChristmasSupport.protect(this, target, 60)) { return false; }
        cooldowns[4] = INTERVALS[4]; ChristmasVfx.emit(this, ChristmasVfx.GUARD, target, target.position(), 60, 2);
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.SHIELD);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, .5F, 1.4F); return true;
    }
    public boolean miracle(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || miracleTicks > 0 || diveTicks > 0 || giftTarget != null
                || allies(16).stream().noneMatch(ally -> ally.getMaxHealth() - ally.getHealth() >= 6)) { return false; }
        cooldowns[3] = INTERVALS[3]; miracleTicks = MIRACLE_DURATION; miracleCenter = position(); miracleHealing.clear();
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE);
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); scripted(true);
        clearGifts();
        for (int slot = 0; slot < 6; slot++) {
            double angle = slot * Math.PI / 3;
            ChristmasGift present = ChristmasGift.create(this, miracleCenter.add(Math.cos(angle) * 5, 2, Math.sin(angle) * 5), MIRACLE_DURATION + 2, 30 + slot * 12, slot % 2 == 1);
            if (present != null) { gifts.add(present.getUUID()); }
        }
        ChristmasVfx.emit(this, ChristmasVfx.MIRACLE, null, miracleCenter, MIRACLE_DURATION, 16);
        playSound(SoundEvents.BEACON_ACTIVATE, .65F, 1.3F); return true;
    }
    private void tickMiracle(ServerLevel level) {
        if (miracleTicks <= 0) { return; }
        if (riderChanged() || miracleCenter == null || distanceToSqr(miracleCenter) > 32 * 32 || !isLoadedFlightBox(new AABB(miracleCenter, miracleCenter).inflate(16))) { cancelCombat(); return; }
        int elapsed = MIRACLE_DURATION - miracleTicks;
        if (elapsed < 20) { moveSafely(level, new Vec3(0, .08D, 0)); }
        for (int slot = 0; slot < gifts.size(); slot++) {
            if (level.getEntity(gifts.get(slot)) instanceof ChristmasGift present) {
                double angle = elapsed * .025D + slot * Math.PI / 3;
                present.setPos(miracleCenter.add(Math.cos(angle) * 5, 2 + Math.sin(elapsed * .07D + slot) * .4D, Math.sin(angle) * 5));
                present.setYRot((float)Math.toDegrees(-angle)); present.needsSync = true;
            }
        }
        if (elapsed % 12 == 0) { ChristmasVfx.sparkle(level, position(), 8); ChristmasVfx.sparkle(level, getEyePosition(), 3); }
        if (elapsed == 40 || elapsed == 65 || elapsed == 90 || elapsed == 110) {
            for (LivingEntity target : allies(24)) {
                if (target.distanceToSqr(miracleCenter) > 16 * 16) { continue; }
                float spent = miracleHealing.getOrDefault(target.getUUID(), 0F);
                float amount = healAlly(target, Math.min(elapsed == 110 ? 12 : 6, 30 - spent), 24);
                miracleHealing.put(target.getUUID(), spent + amount);
                if (elapsed == 110) {
                    for (var effect : List.of(MobEffects.POISON, MobEffects.SLOWNESS, MobEffects.WEAKNESS)) { target.removeEffect(effect); }
                    net.ronm19.phantomism.entity.ai.ChristmasSupport.protect(this, target, 60);
                }
            }
            for (LivingEntity target : enemies(level, miracleCenter, 16)) {
                if (target instanceof Mob mob && !GravityControl.movementImmune(mob)) {
                    target.knockback(.55D, miracleCenter.x - target.getX(), miracleCenter.z - target.getZ(), damageSources().mobAttack(this), 0);
                }
            }
            ChristmasVfx.emit(this, ChristmasVfx.PULSE, null, miracleCenter, elapsed == 110 ? 24 : 16, 16);
            if (elapsed == 110) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.ULTIMATE); }
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .65F, elapsed == 110 ? 1.6F : 1.1F);
        }
        if (--miracleTicks == 0) { clearGifts(); miracleCenter = null; miracleHealing.clear(); scripted(false); }
    }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && !isHolidayDefeated() && !PhantomHornRally.returning(this)
                && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
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

    public boolean snowball(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || diveTicks > 0 || miracleTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > SNOWBALL_RANGE * SNOWBALL_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        return fire(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), false);
    }

    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || cooldowns[0] > 0 || diveTicks > 0 || miracleTicks > 0 || projectiles.size() >= 12
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize();
        Vec3 muzzle = getEyePosition().add(direction.scale(.7D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        ChristmasSnowball shot = PhantomismContent.CHRISTMAS_SNOWBALL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.configure(this, session);
        shot.setPos(muzzle);
        shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        projectiles.add(shot.getUUID());
        cooldowns[0] = mounted ? MOUNTED_INTERVAL : INTERVALS[0];
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.PROJECTILE);
        
        playSound(SoundEvents.SNOWBALL_THROW, .25F, 1.35F);
        ChristmasVfx.emit(this, ChristmasVfx.SNOWBALL, shot, muzzle, 26, 1);
        return true;
    }

    public void snowballImpact(ServerLevel level, LivingEntity target, Vec3 position) {
        if (damage(level, target, 6)) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, true), this); }
        ChristmasVfx.burst(level, position, 12);
        ChristmasVfx.emit(this, ChristmasVfx.IMPACT, null, position, 16, 3);
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
        if (!enabled() || cooldowns[1] > 0 || miracleTicks > 0 || diveTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > 18 * 18 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        cooldowns[1] = INTERVALS[1];
        diveTarget = target.getUUID();
        sequenceRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
        diveTicks = 16;
        animate(net.ronm19.phantomism.animation.PhantomAnimationAction.CHARGE);
        entityData.set(DIVING, true);
        diveTravelled = 0;
        scripted(true);
        playSound(SoundEvents.PHANTOM_SWOOP, .55F, .65F);
        ChristmasVfx.emit(this, ChristmasVfx.DIVE, this, position(), 16, 3);
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

    private void stopDive() { diveTicks = 0; diveTarget = null; entityData.set(DIVING, false); if (miracleTicks == 0) { scripted(false); } }

    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        Entity entity = level.getEntity(diveTarget);
        if (riderChanged() || !(entity instanceof LivingEntity target) || !validTarget(target)
                || !line(getEyePosition(), target.getEyePosition())) { stopDive(); return; }
        if (distanceToSqr(target) <= 6.25D) {
            if (damage(level, target, 18)) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, true), this); target.knockback(.4D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 0); }
            ChristmasVfx.ring(level, target.position(), 4, 24, false);
            for (LivingEntity nearby : enemies(level, target.position(), 4)) {
                if (nearby != target) { damage(level, nearby, 4); }
            }
            stopDive();
            return;
        }
        Vec3 difference = target.position().subtract(position());
        Vec3 step = difference.normalize().scale(Math.min(1.35D, Math.min(18 - diveTravelled, difference.length())));
        Vec3 previous = position();
        if (diveTravelled >= 18 || !moveSafely(level, step)) { stopDive(); return; }
        diveTravelled += position().distanceTo(previous);
        if (diveTicks % 3 == 0) { ChristmasVfx.burst(level, position(), 3); }
        if (--diveTicks == 0) { stopDive(); }
    }


    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || diveTicks > 0 || miracleTicks > 0) { return diveTicks > 0 || miracleTicks > 0; }
        if (level.getGameTime() < nextCombatDecision) { return false; }
        nextCombatDecision = level.getGameTime() + 10;
        long wounded = allies(16).stream().filter(ally -> ally.getHealth() < ally.getMaxHealth() * .6F).count();
        if (wounded >= 3 && miracle(level) || gift(level)) { return true; }
        if (!validTarget(target)) { return false; }
        if (distanceToSqr(target) > 16 && distanceToSqr(target) < 18 * 18 && dive(level, target)) { return true; }
        return snowball(level, target);
    }
    @Override public void tick() {
        boolean recovering = isHolidayDefeated();
        super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        if (recovering && !isHolidayDefeated()) { animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BLESSING); }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        projectiles.removeIf(identity -> level.getEntity(identity) == null); gifts.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { cancelCombat(); } ownerContext = context;
        if (!enabled()) { if (diveTicks > 0 || miracleTicks > 0 || giftTarget != null || !projectiles.isEmpty() || !gifts.isEmpty()) { cancelCombat(); } return; }
        if (giftTarget != null) {
            Entity target = level.getEntity(giftTarget);
            if (!(target instanceof LivingEntity ally) || !ally(ally)) { cancelCombat(); }
            else {
                if (++giftAge == 18) { healAlly(ally, 12, 16); ChristmasVfx.sparkle(level, ally.getBoundingBox().getCenter(), 12); }
                if (giftAge >= 40) { giftTarget = null; }
            }
        }
        tickDive(level); tickMiracle(level);
        if (!isNoAi() && tickCount % 20 == 0 && miracleTicks == 0 && diveTicks == 0) {
            if (tickCount % 100 == 0) { cheer(level); }
            long injured = allies(16).stream().filter(ally -> ally.getHealth() < ally.getMaxHealth() * .6F).count();
            if (injured >= 3) { miracle(level); }
            if (miracleTicks == 0) { gift(level); }
        }
    }
    private void clearGifts() {
        if (level() instanceof ServerLevel level) { for (UUID identity : gifts) { Entity gift = level.getEntity(identity); if (gift != null) { gift.discard(); } } }
        gifts.clear();
    }
    public void cancelCombat() {
        if (level() instanceof ServerLevel level) {
            ChristmasVfx.cancel(this);
            for (UUID identity : projectiles) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } }
        }
        projectiles.clear(); clearGifts(); miracleHealing.clear();
        net.ronm19.phantomism.entity.ai.ChristmasSupport.release(this);
        session = UUID.randomUUID(); diveTicks = 0; diveTarget = null; miracleTicks = 0; miracleCenter = null; giftTarget = null;
        entityData.set(DIVING, false); scripted(false);
    }
    @Override protected void onHolidayDefeated() { cancelCombat(); if (level() instanceof ServerLevel level) { ChristmasVfx.sparkle(level, position(), 16); ChristmasVfx.emit(this, ChristmasVfx.DEFEAT, this, position(), 40, 2); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { cancelCombat(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { cancelCombat(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { cancelCombat(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("ChristmasCooldown" + slot, cooldowns[slot]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); cancelCombat();
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("ChristmasCooldown" + slot, 0), 0, INTERVALS[slot]); }
    }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index : new int[]{0, 1, 2, 4}) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("christmas_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    @Override public List<String> codexCooldowns() { return List.of("Snowball: 42 blocks; 5s AI/Staff, 0.3s mounted.", "Dive: 10s. Winter Gift: 12s. Snowy Guard: 30s. Christmas Miracle: 90s."); }
}
