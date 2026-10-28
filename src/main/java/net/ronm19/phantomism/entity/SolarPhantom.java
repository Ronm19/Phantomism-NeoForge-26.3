package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.SolarEnvironment;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.SolarVfx;

public final class SolarPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = id("entity/solar_phantom");
    public static final int BEAM_RANGE = 42, QUICK_COOLDOWN = 60, CHARGED_COOLDOWN = 140, CHARGE_TIME = 30, MOUNTED_COOLDOWN = 6;
    public static final int SANCTUARY_COOLDOWN = 500, EMERGENCY_COOLDOWN = 900, BLITZ_COOLDOWN = 160, COMET_COOLDOWN = 360;
    public static final int ULTIMATE_COOLDOWN = 2000, ULTIMATE_DURATION = 240, MAX_TARGETS = 24;
    private static final Identifier SUN_SPEED = id("solar_daylight_speed");
    private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(SolarPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DASH = SynchedEntityData.defineId(SolarPhantom.class, EntityDataSerializers.INT);
    private static final String[] CLIPS = {"quick_beam", "charged_beam", "sanctuary", "emergency", "blitz", "comet", "ascension", "judgment", "cataclysm", "salvation", "sunbathing", "affection", "display", "alignment"};
    private final int[] cooldowns = new int[4];
    private final Set<UUID> dashHits = new HashSet<>();
    private final Map<UUID, Scorch> scorches = new HashMap<>();
    private final Map<UUID, Budget> ultimateBudgets = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, ownerContext, chargeTarget, dashTarget;
    private Vec3 sanctuaryCenter, ultimateCenter, dashOrigin, lastDashPosition;
    private int recoveryCredit, meleeTicks, beamCharge, sanctuaryTicks, sanctuaryDuration, ultimateTicks, dashTicks, idleTicks;
    private int beamRecovery = QUICK_COOLDOWN, sanctuaryRecovery = SANCTUARY_COOLDOWN, dashRecovery = BLITZ_COOLDOWN, dustAttempts, appleAttempts;
    private float chargedDamage, sanctuaryHealing, dashDamage, ultimateMultiplier;
    private boolean emergency, salvation, mountedDash, visualOpen;

    public SolarPhantom(EntityType<? extends SolarPhantom> type, Level level) { super(type, level); }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.ATTACK_DAMAGE, 20).add(Attributes.ARMOR, 18)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .85D).add(Attributes.FOLLOW_RANGE, 56)
                .add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .5D);
    }
    public static boolean checkSpawnRules(EntityType<SolarPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !level.isBrightOutside() || level.isRaining()
                || !level.hasChunkAt(position) || !level.getBiome(position).is(ModTags.Biomes.SOLAR_PHANTOM_SPAWNS)) { return false; }
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.05D);
        return box.minY >= level.getMinY() + 1 && box.maxY < level.getMaxY()
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.canSeeSky(position) && !level.containsAnyLiquid(box) && level.noBlockCollision(null, box);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SOLAR_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected int getFlightTurnRate() { return 18; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return ultimateTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL, 0); data.define(DASH, 0); }
    public int visualState() { return entityData.get(VISUAL); }
    public UUID session() { return session; }
    public void visualStarted() { visualOpen = true; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int ultimateTicks() { return ultimateTicks; }
    public boolean salvationMode() { return salvation; }
    public int beamCharge() { return beamCharge; }
    public int dashTicks() { return dashTicks; }
    public int sanctuaryTicks() { return sanctuaryTicks; }
    public boolean empowered() {
        return level().dimension() == Level.OVERWORLD && level().isBrightOutside() && !level().isRaining()
                && level().hasChunkAt(blockPosition()) && level().canSeeSky(blockPosition().above());
    }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean recipient(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && (target == this || isTame()
                && (target == getOwner() || getTeam() != null && getTeam().isAlliedTo(target.getTeam())
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())
                || CelestialBond.authorizedAlly(this, target)));
    }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !recipient(target) && !isAlliedTo(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target)
                && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean attacking(LivingEntity enemy, LivingEntity ward) {
        return ward != null && (ward.getLastHurtByMob() == enemy && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100
                || enemy instanceof Mob mob && mob.getTarget() == ward);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (isFamilyDefenseThreat(target) || target.getUUID().equals(orderedTarget) || attacking(target, this) || attacking(target, getOwner())
                || target instanceof Mob mob && recipient(mob.getTarget()));
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    @Override protected boolean prefersRangedCombat() { return true; }
    @Override public void setCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return; }
        orderedTarget = target.getUUID(); super.setCombatTarget(target); if (getTarget() != target) { orderedTarget = null; }
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(MAX_TARGETS).toList();
    }
    public List<LivingEntity> allies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::recipient).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(MAX_TARGETS).toList();
    }
    public boolean damage(ServerLevel level, LivingEntity target, float amount) {
        return enabled() && validTarget(target) && Float.isFinite(amount) && amount > 0 && target.hurtServer(level, damageSources().mobAttack(this), amount);
    }
    public float damageMultiplier() { return (empowered() ? 1.2F : 1F) * (CelestialBond.offensive(this) ? 1.1F : 1F); }
    private float healingMultiplier() { return empowered() ? 1.15F : 1F; }

    public boolean annihilation(ServerLevel level, LivingEntity target, boolean charged) {
        if (!enabled() || isVehicle() || cooldowns[0] > 0 || beamCharge > 0 || ultimateTicks > 0 || dashTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > BEAM_RANGE * BEAM_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        beamRecovery = charged ? CHARGED_COOLDOWN : QUICK_COOLDOWN;
        if (charged) {
            chargeTarget = target.getUUID(); beamCharge = CHARGE_TIME; chargedDamage = 36 * damageMultiplier();
            action("charged_beam"); SolarVfx.emit(this, SolarVfx.CHARGE, this, getEyePosition(), Vec3.ZERO, CHARGE_TIME, 2);
        } else if (!emitBeam(level, getEyePosition(), target.getEyePosition().subtract(getEyePosition()), 20 * damageMultiplier(), false)) { return false; }
        cooldowns[0] = beamRecovery;
        return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_COOLDOWN; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return enabled() && cooldowns[0] == 0 && ultimateTicks == 0 && beamCharge == 0 && emitBeam(level, rider.getEyePosition(), rider.getViewVector(1), 20 * damageMultiplier(), true);
    }
    private boolean emitBeam(ServerLevel level, Vec3 start, Vec3 aim, float amount, boolean mounted) {
        if (!Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 end = start.add(aim.normalize().scale(BEAM_RANGE));
        if (!isLoadedFlightBox(new AABB(start, end).inflate(.5D))) { return false; }
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        LivingEntity first = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, limit).inflate(.5D), mounted ? this::safeEnemy : this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.2D).clip(start, limit).isPresent() && line(start, enemy.getEyePosition()))
                .min(Comparator.comparingDouble(enemy -> enemy.getBoundingBox().inflate(.2D).clip(start, limit).orElse(limit).distanceToSqr(start))).orElse(null);
        Vec3 impact = first == null ? limit : first.getBoundingBox().inflate(.2D).clip(start, limit).orElse(limit);
        if (first != null && mounted) { orderedTarget = first.getUUID(); }
        var hit = new HashSet<UUID>();
        if (first != null) { beamHit(level, first, amount, hit); }
        for (LivingEntity enemy : enemies(level, impact, 2.5D)) { beamHit(level, enemy, amount, hit); }
        action("quick_beam"); SolarVfx.emit(this, SolarVfx.BEAM, null, start, impact.subtract(start), 12, amount > 30 ? .45F : .25F);
        SolarVfx.emit(this, SolarVfx.IMPACT, null, impact, Vec3.ZERO, 18, 2.5F); SolarEnvironment.scorch(this, BlockPos.containing(impact).below());
        return true;
    }
    private void beamHit(ServerLevel level, LivingEntity enemy, float amount, Set<UUID> hit) {
        if (!hit.add(enemy.getUUID()) || !damage(level, enemy, amount * .8F)) { return; }
        if (scorches.size() < MAX_TARGETS || scorches.containsKey(enemy.getUUID())) {
            scorches.put(enemy.getUUID(), new Scorch(amount * .05F));
            SolarVfx.emit(this, SolarVfx.SCORCH, enemy, enemy.position(), Vec3.ZERO, 80, .7F);
        }
    }
    private void tickCharge(ServerLevel level) {
        if (beamCharge <= 0) { return; }
        LivingEntity target = level.getEntity(chargeTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || isVehicle() || distanceToSqr(target) > BEAM_RANGE * BEAM_RANGE || !line(getEyePosition(), target.getEyePosition())) { beamCharge = 0; chargeTarget = null; return; }
        getLookControl().setLookAt(target, 30, 30);
        if (--beamCharge == 0) { emitBeam(level, getEyePosition(), target.getEyePosition().subtract(getEyePosition()), chargedDamage, false); chargeTarget = null; }
    }

    public boolean sanctuary(ServerLevel level, boolean urgent) {
        if (!enabled() || cooldowns[1] > 0 || sanctuaryTicks > 0) { return false; }
        emergency = urgent; sanctuaryDuration = urgent ? 200 : 160; sanctuaryTicks = sanctuaryDuration; sanctuaryCenter = position();
        sanctuaryHealing = (urgent ? 28 : 16) * healingMultiplier(); sanctuaryRecovery = urgent ? EMERGENCY_COOLDOWN : SANCTUARY_COOLDOWN;
        cooldowns[1] = sanctuaryRecovery; action(urgent ? "emergency" : "sanctuary");
        SolarVfx.emit(this, SolarVfx.SANCTUARY, null, sanctuaryCenter, Vec3.ZERO, sanctuaryDuration, urgent ? 12 : 10); return true;
    }
    private void tickSanctuary(ServerLevel level) {
        if (sanctuaryTicks <= 0) { return; }
        int radius = emergency ? 12 : 10;
        if (--sanctuaryTicks % 20 == 0) {
            for (LivingEntity ward : allies(level, sanctuaryCenter, radius)) {
                ward.heal(sanctuaryHealing / (sanctuaryDuration / 20F));
                ward.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, emergency ? 1 : 0), this);
            }
            for (LivingEntity enemy : enemies(level, sanctuaryCenter, radius)) { damage(level, enemy, 1 * damageMultiplier()); }
        }
        if (sanctuaryTicks == 0) { sanctuaryCenter = null; }
    }
    public boolean needsEmergency(ServerLevel level) {
        var wards = allies(level, position(), 12);
        return wards.stream().anyMatch(ward -> ward == getOwner() && ward.getHealth() < ward.getMaxHealth() * .35F)
                || wards.stream().filter(ward -> ward.getHealth() < ward.getMaxHealth() * .45F).count() >= 2;
    }
    private boolean needsHealing(ServerLevel level) { return allies(level, position(), 10).stream().anyMatch(ward -> ward.getHealth() < ward.getMaxHealth() - 3); }

    public boolean blitz(ServerLevel level, LivingEntity target, boolean charged, boolean mounted) {
        if (!enabled() || cooldowns[2] > 0 || dashTicks > 0 || beamCharge > 0 || ultimateTicks > 0
                || mounted && !(getControllingPassenger() instanceof Player rider && isOwnedBy(rider))
                || !mounted && (isVehicle() || !validTarget(target))) { return false; }
        int reach = charged ? 22 : 12;
        if (target != null && (distanceToSqr(target) > reach * reach || !line(getEyePosition(), target.getEyePosition()))) { return false; }
        dashOrigin = position(); lastDashPosition = position(); dashTarget = target == null ? null : target.getUUID(); mountedDash = mounted;
        dashTicks = charged ? 30 : 20; dashRecovery = charged ? COMET_COOLDOWN : BLITZ_COOLDOWN; cooldowns[2] = dashRecovery;
        dashDamage = (charged ? 32 : 18) * damageMultiplier(); dashHits.clear(); entityData.set(DASH, charged ? 2 : 1);
        action(charged ? "comet" : "blitz"); SolarVfx.emit(this, SolarVfx.BLITZ, this, position(), getViewVector(1), dashTicks, charged ? 3 : 2); return true;
    }
    public boolean activateRiddenBlitz(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(22), this::safeEnemy).stream()
                .filter(enemy -> distanceToSqr(enemy) <= 22 * 22 && line(getEyePosition(), enemy.getEyePosition())
                        && rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .65D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target != null) { orderedTarget = target.getUUID(); }
        return blitz(level, target, target != null && target.getHealth() >= 40, true);
    }
    @Override protected double getRiddenHorizontalAcceleration() {
        return super.getRiddenHorizontalAcceleration() * (entityData.get(DASH) == 2 ? 2.6D : entityData.get(DASH) == 1 ? 1.9D : 1D);
    }
    @Override public void travel(Vec3 input) {
        if (entityData.get(DASH) > 0 && getControllingPassenger() == null) { setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }
    private void stopDash() {
        boolean moving = dashTicks > 0 && !mountedDash; dashTicks = 0; dashTarget = null; dashOrigin = null; lastDashPosition = null;
        mountedDash = false; dashHits.clear(); entityData.set(DASH, 0); if (moving) { setDeltaMovement(Vec3.ZERO); }
    }
    private void tickDash(ServerLevel level) {
        if (dashTicks <= 0) { return; }
        int reach = entityData.get(DASH) == 2 ? 22 : 12;
        LivingEntity target = dashTarget != null && level.getEntity(dashTarget) instanceof LivingEntity living ? living : null;
        if (--dashTicks <= 0 || position().distanceToSqr(dashOrigin) >= reach * reach || horizontalCollision
                || mountedDash && !(getControllingPassenger() instanceof Player rider && isOwnedBy(rider))
                || !mountedDash && (!validTarget(target) || isVehicle())) { stopDash(); return; }
        if (!mountedDash) {
            Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            double remaining = reach - position().distanceTo(dashOrigin);
            Vec3 step = direction.normalize().scale(Math.min(Math.min(entityData.get(DASH) == 2 ? 1.15D : .8D, remaining), direction.length()));
            AABB sweep = getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(sweep) || !level.getWorldBorder().isWithinBounds(sweep) || !level.noBlockCollision(this, sweep) || level.containsAnyLiquid(sweep)) { stopDash(); return; }
            move(MoverType.SELF, step); needsSync = true;
        }
        AABB swept = getBoundingBox().expandTowards(lastDashPosition.subtract(position())).inflate(.6D);
        if (!isLoadedFlightBox(swept)) { stopDash(); return; }
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, swept, this::validTarget).stream().limit(MAX_TARGETS).toList()) {
            if (line(getEyePosition(), enemy.getEyePosition()) && dashHits.add(enemy.getUUID()) && damage(level, enemy, dashDamage)) {
                enemy.knockback(.65D, getX() - enemy.getX(), getZ() - enemy.getZ(), damageSources().mobAttack(this), dashDamage);
                SolarVfx.emit(this, SolarVfx.IMPACT, null, enemy.position(), Vec3.ZERO, 16, 2);
            }
        }
        lastDashPosition = position();
    }

    public boolean supernova(ServerLevel level, boolean protective) {
        if (!enabled() || cooldowns[3] > 0 || ultimateTicks > 0 || beamCharge > 0 || dashTicks > 0 || enemies(level, position(), 16).isEmpty()) { return false; }
        salvation = protective; ultimateTicks = ULTIMATE_DURATION; ultimateCenter = position(); ultimateBudgets.clear();
        ultimateMultiplier = damageMultiplier(); cooldowns[3] = ULTIMATE_COOLDOWN;
        action("ascension"); SolarVfx.emit(this, SolarVfx.SUPERNOVA, null, ultimateCenter, new Vec3(protective ? 1 : 0, 0, 0), ULTIMATE_DURATION, 16); return true;
    }
    private boolean ultimateDamage(ServerLevel level, LivingEntity target, int phase) {
        if (ultimateBudgets.size() >= MAX_TARGETS && !ultimateBudgets.containsKey(target.getUUID())) { return false; }
        Budget budget = ultimateBudgets.computeIfAbsent(target.getUUID(), ignored -> new Budget());
        float amount;
        if (phase == 0) { if (budget.beams >= 2) { return false; } budget.beams++; amount = salvation ? 12 : 18; }
        else if (phase == 1) { if (budget.exploded) { return false; } budget.exploded = true; amount = salvation ? 40 : 75; }
        else { if (salvation || budget.fragment) { return false; } budget.fragment = true; amount = 8; }
        float limit = (salvation ? 64 : 119) * ultimateMultiplier;
        float accepted = Math.min(amount * ultimateMultiplier, Math.max(0, limit - budget.spent)); budget.spent += accepted;
        return damage(level, target, accepted);
    }
    private void tickUltimate(ServerLevel level) {
        if (ultimateTicks <= 0) { return; }
        int elapsed = ULTIMATE_DURATION - --ultimateTicks;
        if (elapsed == 50) { action("judgment"); }
        if (elapsed >= 60 && elapsed <= 120 && elapsed % 20 == 0) {
            for (LivingEntity enemy : enemies(level, ultimateCenter, 16)) {
                if (ultimateDamage(level, enemy, 0)) { SolarVfx.emit(this, SolarVfx.JUDGMENT, null, enemy.position(), Vec3.ZERO, 18, 2); }
            }
        }
        if (elapsed == 150) {
            action("cataclysm"); for (LivingEntity enemy : enemies(level, ultimateCenter, 16)) { ultimateDamage(level, enemy, 1); }
            SolarVfx.emit(this, SolarVfx.CATACLYSM, null, ultimateCenter, Vec3.ZERO, 32, 16);
            SolarEnvironment.scorch(this, BlockPos.containing(ultimateCenter).below());
        }
        if (elapsed == 180) { for (LivingEntity enemy : enemies(level, ultimateCenter, 16)) { ultimateDamage(level, enemy, 2); } }
        if (elapsed == 210) {
            action("salvation"); for (LivingEntity ward : allies(level, ultimateCenter, 16)) {
                ward.heal((salvation ? 36 : 12) * healingMultiplier()); ward.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, salvation ? 240 : 160, salvation ? 1 : 0), this);
            }
            SolarVfx.emit(this, SolarVfx.SALVATION, null, ultimateCenter, Vec3.ZERO, 30, 16);
        }
        if (ultimateTicks == 0) { ultimateCenter = null; ultimateBudgets.clear(); }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || ultimateTicks > 0 || beamCharge > 0 || dashTicks > 0 || isVehicle()) { return true; }
        if (doHurtTarget(level, target)) { return true; }
        double distance = distanceToSqr(target);
        boolean charged = target.getHealth() >= 40 || target.getMaxHealth() >= 100;
        if (!needsEmergency(level) && distance > 6 * 6 && distance <= (charged ? 22 * 22 : 12 * 12) && blitz(level, target, charged, false)) { return true; }
        annihilation(level, target, charged && distance > 8 * 8 && !needsEmergency(level)); return true;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!enabled() || meleeTicks > 0 || ultimateTicks > 0 || beamCharge > 0 || dashTicks > 0 || !(target instanceof LivingEntity enemy)
                || !getBoundingBox().inflate(.6D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition())
                || !damage(level, enemy, (float)getAttributeValue(Attributes.ATTACK_DAMAGE))) { return false; }
        meleeTicks = 40; animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BITE); return true;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.getEntity() == this) { return false; }
        if (source.is(DamageTypes.INDIRECT_MAGIC)) { amount *= .75F; }
        if (CelestialBond.protective(this)) { amount *= .9F; }
        return super.hurtServer(level, source, amount);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        boolean sun = empowered();
        var speed = getAttribute(Attributes.FLYING_SPEED);
        if (speed != null) {
            if (sun && !speed.hasModifier(SUN_SPEED)) { speed.addTransientModifier(new AttributeModifier(SUN_SPEED, .1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
            else if (!sun && speed.hasModifier(SUN_SPEED)) { speed.removeModifier(SUN_SPEED); }
        }
        recoveryCredit += sun ? 115 : 100;
        int recovery = recoveryCredit / 100; recoveryCredit %= 100;
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - recovery); }
        if (meleeTicks > 0) { meleeTicks--; }
        int eclipseRecovery = Math.clamp(getPersistentData().getIntOr("SolarEclipseCooldown", 0), 0, 1200);
        if (eclipseRecovery > 0) { getPersistentData().putInt("SolarEclipseCooldown", eclipseRecovery - 1); }
        UUID owner = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(owner)) { endSolar(); }
        ownerContext = owner;
        if (!enabled()) { endSolar(); return; }
        tickCharge(level); tickDash(level); tickSanctuary(level); tickUltimate(level);
        for (var iterator = scorches.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next(); Scorch scorch = entry.getValue();
            LivingEntity enemy = level.getEntity(entry.getKey()) instanceof LivingEntity living ? living : null;
            if (!validTarget(enemy) || --scorch.ticks < 0) { iterator.remove(); continue; }
            if (scorch.ticks % 20 == 0) { damage(level, enemy, scorch.damage); }
        }
        if (idleTicks > 0 && (--idleTicks == 0 || validTarget(getTarget()) || isVehicle() || getDeltaMovement().lengthSqr() > .015D)) {
            idleTicks = 0; stopTriggeredAnim("solar", null);
        }
        entityData.set(VISUAL, ultimateTicks > 0 ? 3 : validTarget(getTarget()) || dashTicks > 0 || beamCharge > 0 ? 1 : sun ? 2 : 0);
        if (tickCount % 20 == 0) { SolarEnvironment.tick(this); }
        if (isNoAi() || tickCount % 20 != 0) { return; }
        boolean urgent = needsEmergency(level);
        if (urgent || needsHealing(level)) { sanctuary(level, urgent); }
        var nearby = enemies(level, position(), 16);
        if (!nearby.isEmpty() && (urgent || nearby.size() >= 3 || nearby.stream().anyMatch(enemy -> enemy.getMaxHealth() >= 100))) { supernova(level, urgent); }
        CelestialBond.tick(this);
        if (validTarget(getTarget()) && !isVehicle()) { prepareSpeciesCombatAction(level, getTarget()); }
        else if (nearby.isEmpty() && !isVehicle() && ultimateTicks == 0 && idleTicks == 0 && getDeltaMovement().lengthSqr() < .005D && getRandom().nextInt(15) == 0) {
            String idle = sun && getRandom().nextBoolean() ? "sunbathing" : isTame() && getOwner() != null && distanceToSqr(getOwner()) < 6 * 6 ? "affection" : "display";
            action(idle); idleTicks = 60; if (getOwner() != null) { getLookControl().setLookAt(getOwner(), 10, 10); }
        }
    }
    private void action(String clip) { idleTicks = 0; triggerAnim("solar", clip); playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .35F, clip.equals("cataclysm") ? .55F : 1.2F); }
    public void recognizeCelestialPartner(AbstractPhantom partner) {
        if (enabled() && !validTarget(getTarget()) && ultimateTicks == 0 && beamCharge == 0 && dashTicks == 0 && !isVehicle() && distanceToSqr(partner) < 6 * 6) {
            getLookControl().setLookAt(partner, 12, 12); action("alignment"); idleTicks = 40;
        }
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<SolarPhantom>("solar", 3, state -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.solar." + clip)); } controllers.add(controller);
    }
    public TamingProfile tamingProfile(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) ? new TamingProfile(Items.GOLDEN_APPLE, .75F, 2, "Golden Apple: 75%, guaranteed by second eligible attempt.") : preferredTaming();
    }
    @Override public TamingProfile preferredTaming() { return new TamingProfile(Items.GLOWSTONE_DUST, .35F, 3, "Glowstone Dust: 35%, guaranteed by third eligible attempt. Golden Apple: 75%, guaranteed by second. Staff: instant."); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame() && isAlive() && !player.isSpectator() && (stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.GOLDEN_APPLE))) {
            if (!level().isClientSide()) {
                boolean apple = stack.is(Items.GOLDEN_APPLE); TamingProfile profile = tamingProfile(stack);
                int attempts = apple ? ++appleAttempts : ++dustAttempts;
                if (!player.isCreative()) { stack.shrink(1); }
                if (attempts >= profile.guaranteedAttempt() || getRandom().nextFloat() < profile.chance()) {
                    tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte)7);
                } else { level().broadcastEntityEvent(this, (byte)6); }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    @Override public List<String> codexCooldowns() { return List.of("Solar Annihilation: quick 20 total / charged 36 total; 42 blocks; 3 s / 7 s AI, 0.3 s mounted quick; controlled scorch included.", "Radiant Sanctuary: 16 / 28 total allied healing, Resistance I / II; shared 25 s / 45 s recovery.", "Sunflare Blitz: 18 / 32 damage per target once; 12 / 22 blocks; shared 8 s / 18 s recovery.", "Supernova Ascension: 12 s; four bounded phases; shared 100 s recovery."); }
    public void endSolar() {
        if (level() instanceof ServerLevel && visualOpen) { SolarVfx.cancel(this); visualOpen = false; session = UUID.randomUUID(); }
        scorches.clear(); ultimateBudgets.clear(); beamCharge = 0; chargeTarget = null; sanctuaryTicks = 0; sanctuaryCenter = null;
        ultimateTicks = 0; ultimateCenter = null; orderedTarget = null; idleTicks = 0; stopDash(); entityData.set(VISUAL, 0);
        if (level() instanceof ServerLevel) { stopTriggeredAnim("solar", null); }
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && scorches != null) { endSolar(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (scorches != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endSolar(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (scorches != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endSolar(); } }
    @Override public void remove(RemovalReason reason) { if (scorches != null) { endSolar(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        for (int slot = 0; slot < cooldowns.length; slot++) { output.putInt("SolarCooldown" + slot, cooldowns[slot]); }
        output.putInt("SolarDustTrust", dustAttempts); output.putInt("SolarAppleTrust", appleAttempts); output.putInt("SolarRecoveryCredit", recoveryCredit);
        output.putInt("SolarBeamRecovery", beamRecovery); output.putInt("SolarSanctuaryRecovery", sanctuaryRecovery); output.putInt("SolarDashRecovery", dashRecovery);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endSolar(); session = UUID.randomUUID();
        int[] maximums = {CHARGED_COOLDOWN, EMERGENCY_COOLDOWN, COMET_COOLDOWN, ULTIMATE_COOLDOWN};
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("SolarCooldown" + slot, 0), 0, maximums[slot]); }
        dustAttempts = Math.clamp(input.getIntOr("SolarDustTrust", 0), 0, 3); appleAttempts = Math.clamp(input.getIntOr("SolarAppleTrust", 0), 0, 2);
        recoveryCredit = Math.clamp(input.getIntOr("SolarRecoveryCredit", 0), 0, 99);
        beamRecovery = input.getIntOr("SolarBeamRecovery", QUICK_COOLDOWN) == CHARGED_COOLDOWN ? CHARGED_COOLDOWN : QUICK_COOLDOWN;
        sanctuaryRecovery = input.getIntOr("SolarSanctuaryRecovery", SANCTUARY_COOLDOWN) == EMERGENCY_COOLDOWN ? EMERGENCY_COOLDOWN : SANCTUARY_COOLDOWN;
        dashRecovery = input.getIntOr("SolarDashRecovery", BLITZ_COOLDOWN) == COMET_COOLDOWN ? COMET_COOLDOWN : BLITZ_COOLDOWN;
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        return List.of(CooldownDiscipline.Slot.normal("solar_annihilation", beamRecovery, () -> cooldowns[0], value -> cooldowns[0] = value),
                CooldownDiscipline.Slot.normal("solar_sanctuary", sanctuaryRecovery, () -> cooldowns[1], value -> cooldowns[1] = value),
                CooldownDiscipline.Slot.normal("solar_blitz", dashRecovery, () -> cooldowns[2], value -> cooldowns[2] = value));
    }
    private static final class Scorch { private int ticks = 80; private final float damage; private Scorch(float damage) { this.damage = damage; } }
    private static final class Budget { private int beams; private boolean exploded, fragment; private float spent; }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "annihilation" -> cooldowns[0];
            case "charged" -> cooldowns[0];
            case "sanctuary" -> cooldowns[1];
            case "emergency" -> cooldowns[1];
            case "blitz" -> cooldowns[2];
            case "comet" -> cooldowns[2];
            case "supernova" -> cooldowns[3];
            case "salvation" -> cooldowns[3];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
