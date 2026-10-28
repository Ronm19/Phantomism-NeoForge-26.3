package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.PhantomHornRally;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.LunarVfx;

public final class LunarPhantom extends AbstractPhantom implements CooldownDiscipline.Participant, CelestialBond.LunarParticipant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath("phantomism", "entity/lunar_phantom");
    public static final int LANCE_RANGE = 42, MOUNTED_INTERVAL = 6, ULTIMATE_DURATION = 240, ULTIMATE_COOLDOWN = 2200;
    public static final int MAX_TARGETS = 16, MAX_LANCES = 16;
    private static final int[] INTERVALS = {60, 160, 200, 240, 360, 440, 500, ULTIMATE_COOLDOWN};
    private static final String[] KEYS = {"lance", "fang", "rush", "slash", "moonbreak", "pulse", "veil", "ascendancy"};
    private static final String[] CLIPS = {"lance", "fang", "rush", "slash", "moonbreak", "pulse", "veil", "awakening", "dominion", "judgment", "impact", "rest", "affection"};
    private static final Identifier NIGHT_SPEED = Identifier.fromNamespaceAndPath("phantomism", "lunar_night_speed");
    private static final Identifier VEIL_SPEED = Identifier.fromNamespaceAndPath("phantomism", "lunar_veil_speed");
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(LunarPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PRESSURE = SynchedEntityData.defineId(LunarPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> VEILED = SynchedEntityData.defineId(LunarPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ASCENDANT = SynchedEntityData.defineId(LunarPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[8];
    private final Set<UUID> lances = new LinkedHashSet<>();
    private final Map<UUID, Float> ultimateDamage = new HashMap<>();
    private UUID session = UUID.randomUUID(), ownerContext, orderedTarget, rushTarget, ultimateTarget, solarPartner;
    private int chargeProgress, resourceLock, combatTicks, idleTicks, moonTicks, pressureTicks, sinceHurt = 200;
    private int rushTicks, ultimateTicks, veilTicks, meleeTicks, eclipseCooldown, tameAttempts;
    private boolean rushHit, visualOpen, animationOpen, explicitSelection;

    public LunarPhantom(EntityType<? extends LunarPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.ATTACK_DAMAGE, 20).add(Attributes.ARMOR, 18)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .7D).add(Attributes.FOLLOW_RANGE, 56)
                .add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .5D);
    }
    public static boolean night(Level level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return level.dimension() == Level.OVERWORLD && time >= 13000 && time < 23000;
    }
    public static boolean checkSpawnRules(EntityType<LunarPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level) || !night(level) || !level.hasChunkAt(position) || !level.getBiome(position).is(ModTags.Biomes.LUNAR_PHANTOM_SPAWNS)) { return false; }
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.05D);
        return box.minY > level.getMinY() && box.maxY < level.getMaxY() && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.canSeeSky(position) && !level.containsAnyLiquid(box) && level.noBlockCollision(null, box);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.LUNAR_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getFlightTurnRate() { return 20; }
    @Override protected double getFlightAltitudeOffset(double offset) { return super.getFlightAltitudeOffset(offset) + (solar() != null ? 1.25D : 0); }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean shouldPlayWingSound() { return tickCount % 3 == 0; }
    @Override public boolean isUltimateAnimationActive() { return ultimateTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(CHARGE, 0); data.define(PRESSURE, 0); data.define(VEILED, false); data.define(ASCENDANT, false); }
    public int charge() { return entityData.get(CHARGE); }
    public int pressure() { return entityData.get(PRESSURE); }
    public boolean veiled() { return entityData.get(VEILED); }
    public boolean ascendant() { return entityData.get(ASCENDANT); }
    public int ultimateTicks() { return ultimateTicks; }
    public int rushTicks() { return rushTicks; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public UUID session() { return session; }
    public void visualStarted() { visualOpen = true; }
    public boolean moonlight() { return night(level()) && !level().isRaining() && level().hasChunkAt(blockPosition()) && level().canSeeSky(blockPosition().above()); }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && !PhantomHornRally.returning(this) && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean recipient(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && (target == this || isTame()
                && (target == getOwner() || getTeam() != null && getTeam().isAlliedTo(target.getTeam())
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference()) || CelestialBond.authorizedAlly(this, target)));
    }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !recipient(target) && !isAlliedTo(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target)
                && !target.isPassengerOfSameVehicle(this) && (explicitSelection || !isTargetSuppressed(target)) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean attacking(LivingEntity enemy, LivingEntity ward) {
        return ward != null && (ward.getLastHurtByMob() == enemy && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100 || enemy instanceof Mob mob && mob.getTarget() == ward);
    }
    private boolean engaged(LivingEntity target) { return target.getUUID().equals(orderedTarget) || attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && recipient(mob.getTarget()); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (isFamilyDefenseThreat(target) || engaged(target) || getControllingPassenger() instanceof Player rider && isOwnedBy(rider));
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    @Override protected boolean prefersRangedCombat() { return true; }
    @Override public void setCombatTarget(LivingEntity target) {
        boolean previous = explicitSelection; explicitSelection = true;
        try { if (!safeEnemy(target)) { return; } orderedTarget = target.getUUID(); super.setCombatTarget(target); if (getTarget() != target) { orderedTarget = null; } }
        finally { explicitSelection = previous; }
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> engaged(target) && target.position().distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(MAX_TARGETS).toList();
    }
    public List<LivingEntity> allies(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::recipient).stream()
                .filter(target -> distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition())).limit(MAX_TARGETS).toList();
    }
    private SolarPhantom solar() {
        if (!(level() instanceof ServerLevel level) || solarPartner == null || !(level.getEntity(solarPartner) instanceof SolarPhantom solar)) { return null; }
        return solar.enabled() && solar.distanceToSqr(this) <= 576 && line(getEyePosition(), solar.getEyePosition()) && CelestialBond.authorizedAlly(this, solar) ? solar : null;
    }
    public float power(float base) { return base * (1 + charge() * .05F + pressure() * .03F + (night(level()) ? .1F : 0) + (solar() != null ? .1F : 0)); }
    private void earnedHit() {
        combatTicks = 80; idleTicks = 0;
        if (resourceLock > 0) { return; } resourceLock = 20; pressureTicks = 0;
        entityData.set(PRESSURE, Math.min(3, pressure() + 1));
        if (++chargeProgress >= 3) { chargeProgress = 0; entityData.set(CHARGE, Math.min(3, charge() + 1)); }
    }
    private boolean hit(ServerLevel level, LivingEntity target, float amount, boolean resources) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0 || !target.hurtServer(level, damageSources().mobAttack(this), amount)) { return false; }
        if (resources) { earnedHit(); } return true;
    }
    private void ultimateHit(ServerLevel level, LivingEntity target, float amount) {
        if (ultimateTicks <= 0 || !ultimateDamage.containsKey(target.getUUID())) { return; }
        float used = ultimateDamage.get(target.getUUID()), maximum = target.getUUID().equals(ultimateTarget) ? 65 : 25;
        float allowed = Math.max(0, Math.min(amount, maximum - used));
        if (hit(level, target, allowed, false)) { ultimateDamage.put(target.getUUID(), used + allowed); }
    }
    private void repel(LivingEntity target, double strength) { Vec3 away = target.position().subtract(position()); if (away.lengthSqr() > .001D) { away = away.normalize().scale(strength); target.push(away.x, .15D, away.z); target.syncVelocity = true; } }
    private boolean shoot(ServerLevel level, Vec3 aim, boolean ultimate) {
        lances.removeIf(identity -> !(level.getEntity(identity) instanceof MoonLance));
        if (!Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D || lances.size() >= MAX_LANCES) { return false; }
        Vec3 direction = aim.normalize(), mouth = getEyePosition().add(direction.scale(.7D));
        if (!line(getEyePosition(), mouth)) { return false; }
        MoonLance lance = PhantomismContent.MOON_LANCE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (lance == null) { return false; } lance.configure(this, session, ultimate); lance.setPos(mouth); lance.setDeltaMovement(direction.scale(2.8D));
        if (!level.addFreshEntity(lance)) { return false; } lances.add(lance.getUUID());
        LunarVfx.emit(this, LunarVfx.LANCE, lance, mouth, direction, 20, .3F); return true;
    }
    public boolean lance(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || rushTicks > 0 || ultimateTicks > 0 || !validTarget(target) || distanceToSqr(target) > LANCE_RANGE * LANCE_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (!shoot(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), false)) { return false; } cooldowns[0] = INTERVALS[0]; action("lance"); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return enabled() && cooldowns[0] == 0 && rushTicks == 0 && ultimateTicks == 0 && shoot(level, rider.getViewVector(1), false); }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
    public void lanceImpact(ServerLevel level, LivingEntity target, boolean ultimate, Vec3 point) {
        if (!enabled() || !validTarget(target)) { return; }
        if (ultimate) { ultimateHit(level, target, 5); } else { hit(level, target, power(15), true); }
        LunarVfx.emit(this, LunarVfx.IMPACT, null, point, Vec3.ZERO, 16, 1.2F);
    }
    public boolean fang(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || rushTicks > 0 || ultimateTicks > 0 || !validTarget(target) || distanceToSqr(target) > 25 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (!hit(level, target, power(24), true)) { return false; } cooldowns[1] = INTERVALS[1]; target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this); action("fang"); LunarVfx.emit(this, LunarVfx.FANG, target, target.position(), Vec3.ZERO, 18, 2); return true;
    }
    public boolean activateRiddenFang(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider) || !enabled() || cooldowns[1] > 0) { return false; }
        Vec3 start = rider.getEyePosition(), end = start.add(rider.getViewVector(1).scale(5));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), this::safeEnemy).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.5D).clip(start, end).isPresent() && line(getEyePosition(), enemy.getEyePosition()))
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && fang(level, target);
    }
    public boolean rush(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || rushTicks > 0 || ultimateTicks > 0 || !validTarget(target) || distanceToSqr(target) > 400 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        cooldowns[2] = INTERVALS[2]; rushTarget = target.getUUID(); rushTicks = 12; rushHit = false; action("rush"); LunarVfx.emit(this, LunarVfx.RUSH, this, position(), target.position().subtract(position()).normalize(), 16, 2); return true;
    }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; } LivingEntity target = level.getEntity(rushTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { stopRush(); return; }
        Vec3 offset = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()); Vec3 step = offset.lengthSqr() > 2.56D ? offset.normalize().scale(1.6D) : offset;
        AABB corridor = getBoundingBox().expandTowards(step).inflate(.05D);
        if (!isLoadedFlightBox(corridor) || !level.noBlockCollision(this, corridor) || level.containsAnyLiquid(corridor)) { stopRush(); return; }
        move(MoverType.SELF, step); setDeltaMovement(Vec3.ZERO); syncVelocity = true;
        if (!rushHit && getBoundingBox().inflate(1.2D).intersects(target.getBoundingBox())) { rushHit = true; hit(level, target, power(22), true); repel(target, .5D); stopRush(); return; }
        if (--rushTicks == 0) { stopRush(); }
    }
    private void stopRush() { boolean rushing = rushTicks > 0; rushTicks = 0; rushTarget = null; if (rushing && !level().isClientSide()) { setDeltaMovement(Vec3.ZERO); } }
    public boolean slash(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[3] > 0 || rushTicks > 0 || ultimateTicks > 0 || !validTarget(target) || distanceToSqr(target) > 144 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.position().subtract(position()).normalize();
        var victims = enemies(level, position(), 12).stream().filter(enemy -> enemy.position().subtract(position()).normalize().dot(direction) >= .35D).toList();
        if (victims.isEmpty()) { return false; } cooldowns[3] = INTERVALS[3];
        for (var enemy : victims) { hit(level, enemy, power(20), true); enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this); }
        action("slash"); LunarVfx.emit(this, LunarVfx.SLASH, null, position(), direction, 24, 12); return true;
    }
    public boolean moonbreak(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[4] > 0 || rushTicks > 0 || ultimateTicks > 0 || !validTarget(target) || distanceToSqr(target) > 144 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (!hit(level, target, power(34), true)) { return false; } cooldowns[4] = INTERVALS[4]; target.addEffect(new MobEffectInstance(PhantomismContent.ARMOR_FRACTURE, 80, 0), this); repel(target, .7D);
        action("moonbreak"); LunarVfx.emit(this, LunarVfx.MOONBREAK, target, target.position(), Vec3.ZERO, 30, 3); return true;
    }
    public boolean pulse(ServerLevel level) {
        if (!enabled() || cooldowns[5] > 0 || ultimateTicks > 0) { return false; }
        var enemies = enemies(level, position(), 10); var wards = allies(level, 10);
        if (enemies.isEmpty() && wards.stream().noneMatch(ward -> ward.getHealth() < ward.getMaxHealth())) { return false; }
        cooldowns[5] = INTERVALS[5];
        for (var enemy : enemies) { hit(level, enemy, power(12), true); repel(enemy, .4D); }
        for (var ward : wards) { ward.heal(8); ward.removeEffect(MobEffects.SLOWNESS); }
        action("pulse"); LunarVfx.emit(this, LunarVfx.PULSE, this, position(), Vec3.ZERO, 32, 10); return true;
    }
    public boolean veil(ServerLevel level) {
        if (!enabled() || cooldowns[6] > 0 || veilTicks > 0) { return false; }
        cooldowns[6] = INTERVALS[6]; veilTicks = 100; entityData.set(VEILED, true); action("veil"); LunarVfx.emit(this, LunarVfx.VEIL, this, position(), Vec3.ZERO, 100, 2.5F); return true;
    }
    public boolean ascendancy(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || ultimateTicks > 0 || rushTicks > 0 || !validTarget(getTarget()) || distanceToSqr(getTarget()) > 196) { return false; }
        var targets = enemies(level, position(), 14); if (!targets.contains(getTarget()) || targets.size() < 3 && getTarget().getMaxHealth() < 100) { return false; }
        ultimateDamage.clear(); for (var target : targets) { ultimateDamage.put(target.getUUID(), 0F); }
        ultimateTarget = getTarget().getUUID(); ultimateTicks = ULTIMATE_DURATION; cooldowns[7] = INTERVALS[7]; entityData.set(ASCENDANT, true);
        action("awakening"); LunarVfx.emit(this, LunarVfx.ASCENDANCY, this, position(), Vec3.ZERO, ULTIMATE_DURATION, 14); return true;
    }
    private void tickUltimate(ServerLevel level) {
        if (ultimateTicks <= 0) { return; } LivingEntity primary = level.getEntity(ultimateTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(primary) || distanceToSqr(primary) > 400 || !line(getEyePosition(), primary.getEyePosition())) { endLunar(); return; }
        int elapsed = ULTIMATE_DURATION - ultimateTicks;
        if (elapsed == 50) { action("dominion"); }
        if (elapsed >= 60 && elapsed <= 120 && elapsed % 20 == 0) {
            var targets = enemies(level, position(), 14).stream().filter(target -> ultimateDamage.containsKey(target.getUUID())).limit(3).toList();
            for (var target : targets) { shoot(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), true); }
        }
        if (elapsed == 140 || elapsed == 170) {
            action("judgment"); for (var target : enemies(level, position(), 14)) { ultimateHit(level, target, target == primary ? 10 : 4); }
            LunarVfx.emit(this, LunarVfx.SLASH, null, position(), primary.position().subtract(position()).normalize(), 24, 14);
        }
        if (elapsed == 220) {
            action("impact"); for (var target : enemies(level, position(), 14)) { ultimateHit(level, target, target == primary ? 65 : 25); repel(target, .6D); }
            LunarVfx.emit(this, LunarVfx.IMPACT, null, primary.position(), Vec3.ZERO, 30, 14);
        }
        if (--ultimateTicks == 0) { ultimateDamage.clear(); ultimateTarget = null; entityData.set(ASCENDANT, false); }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || isVehicle()) { return false; } if (ultimateTicks > 0 || rushTicks > 0) { return true; }
        var nearby = enemies(level, position(), 12);
        if ((nearby.size() >= 3 || target.getMaxHealth() >= 100) && ascendancy(level)) { return true; }
        if (nearby.size() >= 2 && slash(level, target)) { return true; }
        if (target.getArmorValue() >= 8 && moonbreak(level, target)) { return true; }
        if (distanceToSqr(target) <= 25 && fang(level, target)) { return true; }
        if (distanceToSqr(target) <= 225 && pressure() >= 1 && rush(level, target)) { return true; }
        return lance(level, target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!enabled() || rushTicks > 0 || ultimateTicks > 0 || meleeTicks > 0 || !(target instanceof LivingEntity living) || !getBoundingBox().inflate(1.5D).intersects(target.getBoundingBox())) { return false; }
        if (fang(level, living)) { meleeTicks = 40; return true; }
        if (!hit(level, living, power((float) getAttributeValue(Attributes.ATTACK_DAMAGE)), true)) { return false; } meleeTicks = 40; return true;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean result = super.hurtServer(level, source, amount); if (result) { sinceHurt = 0; } return result;
    }
    private void resources() {
        if (resourceLock > 0) { resourceLock--; } sinceHurt = Math.min(1000, sinceHurt + 1);
        if (validTarget(getTarget()) || ultimateTicks > 0) { combatTicks = 80; } else if (combatTicks > 0) { combatTicks--; }
        if (moonlight() && enabled()) { if (++moonTicks >= 200) { moonTicks = 0; entityData.set(CHARGE, Math.min(3, charge() + 1)); } } else { moonTicks = 0; }
        if (combatTicks == 0) {
            if (++pressureTicks >= 60) { pressureTicks = 0; entityData.set(PRESSURE, Math.max(0, pressure() - 1)); }
            if (!moonlight() && ++idleTicks >= 400) { idleTicks = 0; entityData.set(CHARGE, Math.max(0, charge() - 1)); chargeProgress = 0; }
        } else { idleTicks = 0; pressureTicks = 0; }
    }
    private void support(ServerLevel level) {
        if (sinceHurt >= 100 && combatTicks == 0 && getHealth() < getMaxHealth()) { heal(moonlight() ? 1 : .5F); }
        for (var ward : allies(level, 10)) { ward.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0), this); }
        SolarPhantom partner = level.getEntitiesOfClass(SolarPhantom.class, getBoundingBox().inflate(24), candidate -> CelestialBond.partner(candidate) == this).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        solarPartner = partner == null ? null : partner.getUUID();
        if (partner != null && !validTarget(getTarget()) && safeEnemy(partner.getTarget()) && partner.validTarget(partner.getTarget()) && !isVehicle()) { setCombatTarget(partner.getTarget()); }
        if (partner != null && tickCount % 100 == 0 && !validTarget(getTarget()) && distanceToSqr(partner) < 36) { getLookControl().setLookAt(partner, 12, 12); action("affection"); }
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        if (eclipseCooldown > 0) { eclipseCooldown--; } if (meleeTicks > 0) { meleeTicks--; }
        var speed = getAttribute(Attributes.FLYING_SPEED);
        if (speed != null) { if (night(level) && enabled()) { if (!speed.hasModifier(NIGHT_SPEED)) { speed.addTransientModifier(new AttributeModifier(NIGHT_SPEED, .12D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } } else { speed.removeModifier(NIGHT_SPEED); } }
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID(); if (ownerContext != null && !ownerContext.equals(context)) { endLunar(); } ownerContext = context;
        resources();
        if (!enabled()) { endLunar(); return; }
        tickRush(level); tickUltimate(level);
        if (veilTicks > 0 && --veilTicks == 0) { entityData.set(VEILED, false); }
        if (speed != null) { if (veiled()) { if (!speed.hasModifier(VEIL_SPEED)) { speed.addTransientModifier(new AttributeModifier(VEIL_SPEED, .18D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } } else { speed.removeModifier(VEIL_SPEED); } }
        if (tickCount % 40 == 0) { support(level); }
        if (tickCount % 10 == 0 && (charge() > 0 || night(level))) { level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + .5D, getZ(), 1 + charge(), .6D, .3D, .6D, .005D); }
        if (isNoAi() || tickCount % 20 != 0) { return; }
        if (allies(level, 10).stream().anyMatch(ward -> ward.getHealth() < ward.getMaxHealth() - 6) || enemies(level, position(), 10).size() >= 3) { pulse(level); }
        if (validTarget(getTarget()) && !isVehicle()) { prepareSpeciesCombatAction(level, getTarget()); }
        else if (!isVehicle() && ultimateTicks == 0 && tickCount % 160 == 0 && getDeltaMovement().lengthSqr() < .01D) { action("rest"); }
    }
    private void action(String clip) { animationOpen = true; triggerAnim("lunar", clip); playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .22F, 1.5F); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<LunarPhantom>("lunar", 3, state -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.lunar." + clip)); } controllers.add(controller);
    }
    @Override public AbstractPhantom phantom() { return this; }
    @Override public boolean celestialUltimateActive() { return ultimateTicks > 0; }
    @Override public boolean celestialCooperationEnabled() { return enabled(); }
    @Override public boolean prefersCelestialSalvation() { LivingEntity owner = getOwner(); return getHealth() < getMaxHealth() * .5F || owner != null && owner.getHealth() < owner.getMaxHealth() * .4F; }
    @Override public boolean reserveEclipse(UUID solar, int recoveryTicks) {
        if (!(level() instanceof ServerLevel level) || !enabled() || eclipseCooldown > 0 || !(level.getEntity(solar) instanceof SolarPhantom partner) || CelestialBond.partner(partner) != this) { return false; }
        eclipseCooldown = Math.clamp(recoveryTicks, 20, 2400); solarPartner = solar; return true;
    }
    @Override public TamingProfile preferredTaming() { return new TamingProfile(Items.AMETHYST_SHARD, .5F, 3, "Amethyst Shard: 50%, guaranteed on the third eligible attempt. Staff: instant."); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame() && isAlive() && !player.isSpectator() && stack.is(Items.AMETHYST_SHARD)) {
            if (!level().isClientSide()) { tameAttempts++; if (!player.isCreative()) { stack.shrink(1); }
                if (tameAttempts >= 3 || getRandom().nextFloat() < .5F) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte) 7); }
                else { level().broadcastEntityEvent(this, (byte) 6); }
            } return InteractionResult.SUCCESS;
        } return super.mobInteract(player, hand);
    }
    @Override public List<String> codexCooldowns() { return List.of("Moon Lance: 42 blocks; 15 base; 3 s AI/Staff and 0.3 s mounted.", "Moon Fang 8 s; Lunar Rush 10 s; Crescent Slash 12 s; Moonbreak 18 s; Moon Pulse 22 s.", "Moonlit Veil: five-second shield, 25-second recovery. Full Moon Ascendancy: twelve seconds, 110-second recovery; all phases share 65/25 direct-damage caps."); }
    public void endLunar() {
        boolean active = visualOpen || animationOpen || !lances.isEmpty() || ultimateTicks > 0 || rushTicks > 0 || veilTicks > 0;
        if (level() instanceof ServerLevel level) { for (UUID identity : List.copyOf(lances)) { Entity lance = level.getEntity(identity); if (lance != null) { lance.discard(); } } if (visualOpen) { LunarVfx.cancel(this); visualOpen = false; } if (active) { session = UUID.randomUUID(); } if (animationOpen) { stopTriggeredAnim("lunar", null); animationOpen = false; } }
        var speed = getAttribute(Attributes.FLYING_SPEED); if (speed != null) { speed.removeModifier(VEIL_SPEED); }
        lances.clear(); ultimateDamage.clear(); ultimateTicks = 0; ultimateTarget = null; veilTicks = 0; solarPartner = null; entityData.set(VEILED, false); entityData.set(ASCENDANT, false); stopRush();
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endLunar(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endLunar(); orderedTarget = null; } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endLunar(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endLunar(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("LunarCooldown" + slot, cooldowns[slot]); }
        output.putInt("LunarCharge", charge()); output.putInt("LunarPressure", pressure()); output.putInt("LunarChargeProgress", chargeProgress); output.putInt("LunarResourceLock", resourceLock);
        output.putInt("LunarEclipseCooldown", eclipseCooldown); output.putInt("LunarTamingAttempts", tameAttempts); output.putInt("LunarSinceHurt", sinceHurt);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endLunar();
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("LunarCooldown" + slot, 0), 0, INTERVALS[slot]); }
        entityData.set(CHARGE, Math.clamp(input.getIntOr("LunarCharge", 0), 0, 3)); entityData.set(PRESSURE, Math.clamp(input.getIntOr("LunarPressure", 0), 0, 3));
        chargeProgress = Math.clamp(input.getIntOr("LunarChargeProgress", 0), 0, 2); resourceLock = Math.clamp(input.getIntOr("LunarResourceLock", 0), 0, 20);
        eclipseCooldown = Math.clamp(input.getIntOr("LunarEclipseCooldown", 0), 0, 2400); tameAttempts = Math.clamp(input.getIntOr("LunarTamingAttempts", 0), 0, 3); sinceHurt = Math.clamp(input.getIntOr("LunarSinceHurt", 0), 0, 1000);
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        var result = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 7; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("lunar_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(result);
    }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
}
