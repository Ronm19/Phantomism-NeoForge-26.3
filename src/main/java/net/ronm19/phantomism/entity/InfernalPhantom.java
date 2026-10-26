package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.InfernalPhantomVfx;

public final class InfernalPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath("phantomism", "entity/infernal_phantom");
    public static final int BOLT_RANGE = 32, MOUNTED_INTERVAL = 2, ASCENDANT_DURATION = 240, ASCENDANT_COOLDOWN = 1800;
    public static final int MAX_BOLTS = 16, MAX_TARGETS = 16;
    private static final int[] INTERVALS = {60, 200, 480, 360, 240, 600, ASCENDANT_COOLDOWN};
    private static final String[] KEYS = {"bolt", "hellrush", "field", "overheat", "ash", "guard", "ascendant"};
    private static final String[] CLIPS = {"bolt", "hellrush", "field", "overheat", "ash", "guard", "awakening", "hellfire", "dominion", "impact", "rest"};
    private static final EntityDataAccessor<Integer> HEAT = SynchedEntityData.defineId(InfernalPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUSHING = SynchedEntityData.defineId(InfernalPhantom.class, EntityDataSerializers.BOOLEAN), ASCENDANT = SynchedEntityData.defineId(InfernalPhantom.class, EntityDataSerializers.BOOLEAN), GUARDED = SynchedEntityData.defineId(InfernalPhantom.class, EntityDataSerializers.BOOLEAN), RESTING = SynchedEntityData.defineId(InfernalPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[7];
    private final Set<UUID> bolts = new LinkedHashSet<>();
    private final Map<UUID, Float> fieldDamage = new HashMap<>();
    private final Map<UUID, Budget> marked = new LinkedHashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, ownerContext, rushTarget, burstTarget, ascendantTarget;
    private Vec3 fieldCenter;
    private int heatProgress, heatLock, idleHeat, combatTicks, meleeTicks, burstTicks, rushTicks, fieldTicks, guardTicks, ascendantTicks, restTicks, tameAttempts;
    private boolean rushUltimate, rushMounted, visualOpen, explicitSelection;

    public InfernalPhantom(EntityType<? extends InfernalPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 280).add(Attributes.ATTACK_DAMAGE, 18).add(Attributes.ARMOR, 16).add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .8D).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .5D); }
    public static boolean checkSpawnRules(EntityType<InfernalPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level) || !level.getServer().isSameThread() || level.dimension() != Level.NETHER || !level.hasChunkAt(position) || position.getY() >= 124) { return false; }
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.1D);
        return level.getBiome(position).is(ModTags.Biomes.INFERNAL_PHANTOM_SPAWNS) && box.minY > level.getMinY() && box.maxY < 125
                && level.getWorldBorder().isWithinBounds(box) && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && !level.containsAnyLiquid(box) && level.noBlockCollision(null, box);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.INFERNAL_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getFlightTurnRate() { return 15; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean prefersRangedCombat() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(HEAT, 0); data.define(RUSHING, false); data.define(ASCENDANT, false); data.define(GUARDED, false); data.define(RESTING, false); }
    public UUID session() { return session; }
    public int heat() { return entityData.get(HEAT); }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int fieldTicks() { return fieldTicks; }
    public int rushTicks() { return rushTicks; }
    public int ascendantTicks() { return ascendantTicks; }
    public boolean guarded() { return entityData.get(GUARDED); }
    public boolean ascendant() { return entityData.get(ASCENDANT); }
    public void visualStarted() { visualOpen = true; }
    @Override public boolean isUltimateAnimationActive() { return ascendant() || super.isUltimateAnimationActive(); }
    public boolean enabled() { return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
            && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
            && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level() && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner); }
    public boolean recipient(LivingEntity target) { return target != null && target.isAlive() && target.level() == level() && (target == this || isTame() && (target == getOwner() || isAlliedTo(target) || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference()))); }
    public boolean safeEnemy(LivingEntity target) { return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable() && !recipient(target) && !isAlliedTo(target)
            && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && (explicitSelection || !isTargetSuppressed(target)) && !PhantomDuels.excludesObserver(this, target)
            && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob)) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0)); }
    private boolean attacking(LivingEntity enemy, LivingEntity ward) { return ward != null && (ward.getLastHurtByMob() == enemy && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100 || enemy instanceof Mob mob && mob.getTarget() == ward); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) { return safeEnemy(target) && (target instanceof Enemy || target.getUUID().equals(orderedTarget) || attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && recipient(mob.getTarget())); }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    @Override public void setCombatTarget(LivingEntity target) { explicitSelection = true; try { if (!safeEnemy(target)) { return; } orderedTarget = target.getUUID(); super.setCombatTarget(target); if (getTarget() != target) { orderedTarget = null; } } finally { explicitSelection = false; } }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.05D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    private List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) { return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream().filter(target -> center.distanceToSqr(target.position()) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition())).sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(MAX_TARGETS).toList(); }
    private float power(float amount) { return amount * (1 + .05F * heat() + (level().dimension() == Level.NETHER && (validTarget(getTarget()) || combatTicks > 0) ? .05F : 0)); }
    private void gainHeat() { if (heatLock > 0 || ascendantTicks > 0 || heat() >= 3) { return; } heatLock = 20; idleHeat = 0; if (++heatProgress >= 3) { heatProgress = 0; entityData.set(HEAT, heat() + 1); } }
    private boolean hit(ServerLevel level, LivingEntity target, float amount, boolean earnHeat, int burnSeconds, boolean ultimate, boolean finishing) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return false; }
        ultimate |= ascendantTicks > 0; Budget budget = ultimate ? marked.get(target.getUUID()) : null;
        if (ultimate) {
            if (ascendantTicks <= 0 || budget == null || finishing && budget.finished) { return false; }
            boolean primary = target.getUUID().equals(ascendantTarget); float maximum = finishing ? primary ? 65 : 35 : primary ? 20 : 12;
            amount = Math.min(amount, Math.max(0, maximum - budget.damage)); if (amount <= 0) { return false; }
        }
        int immunity = target.damageCooldownTime; target.damageCooldownTime = 0; boolean accepted;
        try { accepted = target.hurtServer(level, damageSources().mobAttack(this), amount); } finally { target.damageCooldownTime = Math.max(immunity, target.damageCooldownTime); }
        if (accepted) { combatTicks = 40; idleHeat = 0; if (budget != null) { budget.damage += amount; budget.finished |= finishing; } else { if (earnHeat) { gainHeat(); } if (burnSeconds > 0) { target.igniteForSeconds(burnSeconds); } } }
        return accepted;
    }
    public boolean bolt(ServerLevel level, LivingEntity target) {
        if (!enabled() || isVehicle() || cooldowns[0] > 0 || burstTicks > 0 || ascendantTicks > 0 || rushTicks > 0 || !validTarget(target) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (!shoot(level, target.getEyePosition().subtract(getEyePosition()), false)) { return false; }
        burstTarget = target.getUUID(); burstTicks = 8; cooldowns[0] = INTERVALS[0]; action("bolt"); return true;
    }
    private boolean shoot(ServerLevel level, Vec3 direction, boolean ultimate) {
        bolts.removeIf(identity -> !(level.getEntity(identity) instanceof InfernalBolt));
        if (bolts.size() >= MAX_BOLTS || direction.lengthSqr() < .001D || !Double.isFinite(direction.lengthSqr())) { return false; }
        Vec3 aim = direction.normalize(), muzzle = getEyePosition().add(aim.scale(.7D)); if (!line(getEyePosition(), muzzle)) { return false; }
        var shot = PhantomismContent.INFERNAL_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.configure(this, session, ultimate); shot.setPos(muzzle); shot.shoot(aim.x, aim.y, aim.z, 2.4F, 0);
        if (!level.addFreshEntity(shot)) { return false; } bolts.add(shot.getUUID()); InfernalPhantomVfx.emit(this, InfernalPhantomVfx.BOLT, shot, muzzle, aim, 18, .5F);
        if (tickCount % 4 == 0) { playSound(SoundEvents.BLAZE_SHOOT, .35F, 1.2F); } return true;
    }
    public boolean boltImpact(ServerLevel level, LivingEntity target, boolean ultimate, Vec3 location) { boolean accepted = hit(level, target, power(5), !ultimate, 2, ultimate, false); if (accepted) { InfernalPhantomVfx.emit(this, InfernalPhantomVfx.IMPACT, null, location, Vec3.ZERO, 12, 1); } return accepted; }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return enabled() && cooldowns[0] == 0 && burstTicks == 0 && rushTicks == 0 && ascendantTicks == 0 && shoot(level, rider.getViewVector(1), false); }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = MOUNTED_INTERVAL; }
    public boolean hellrush(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || rushTicks > 0 || ascendantTicks > 0 || !validTarget(target) || distanceToSqr(target) > 24 * 24 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        startRush(target, false); cooldowns[1] = INTERVALS[1]; return true;
    }
    private void startRush(LivingEntity target, boolean ultimate) { burstTicks = 0; burstTarget = null; rushTarget = target.getUUID(); rushTicks = 40; rushUltimate = ultimate; rushMounted = isVehicle(); entityData.set(RUSHING, !rushMounted); action(ultimate ? "impact" : "hellrush"); InfernalPhantomVfx.emit(this, InfernalPhantomVfx.RUSH, this, position(), target.position().subtract(position()).normalize(), 40, ultimate ? 8 : 4); }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; } LivingEntity target = level.getEntity(rushTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || !line(getEyePosition(), target.getEyePosition()) || --rushTicks == 0 || rushMounted && !isVehicle()) { stopRush(); return; }
        if (!rushMounted && distanceToSqr(target) > 9) { Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()); step = step.normalize().scale(Math.min(step.length(), 1.1D)); if (!safeMove(level, step)) { stopRush(); return; } }
        if (getBoundingBox().inflate(2).intersects(target.getBoundingBox())) {
            boolean ultimate = rushUltimate; hit(level, target, ultimate ? 65 : power(26), !ultimate, ultimate ? 0 : 4, ultimate, true);
            for (LivingEntity enemy : enemies(level, target.position(), ultimate ? 14 : 4)) { if (enemy != target) { hit(level, enemy, ultimate ? 35 : power(12), false, ultimate ? 0 : 4, ultimate, true); repel(enemy, .65D); } }
            repel(target, .8D); InfernalPhantomVfx.emit(this, InfernalPhantomVfx.IMPACT, null, target.position(), Vec3.ZERO, 35, ultimate ? 14 : 4); stopRush();
        }
    }
    private boolean safeMove(ServerLevel level, Vec3 step) { AABB sweep = getBoundingBox().expandTowards(step); if (!isLoadedFlightBox(sweep) || !level.getWorldBorder().isWithinBounds(sweep) || !level.noBlockCollision(this, sweep) || level.containsAnyLiquid(sweep)) { return false; } move(MoverType.SELF, step); needsSync = true; return true; }
    private void stopRush() { rushTicks = 0; rushTarget = null; rushUltimate = false; rushMounted = false; entityData.set(RUSHING, false); }
    @Override public void travel(Vec3 input) { if (!isVehicle() && (entityData.get(RUSHING) || ascendant() || entityData.get(RESTING))) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    public boolean activateRiddenHellrush(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        var target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24), this::safeEnemy).stream().filter(enemy -> distanceToSqr(enemy) <= 576 && line(getEyePosition(), enemy.getEyePosition()) && rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) { return false; } setCombatTarget(target); return hellrush(level, target);
    }
    public boolean field(ServerLevel level) { if (!enabled() || cooldowns[2] > 0 || fieldTicks > 0 || ascendantTicks > 0 || enemies(level, position(), 10).isEmpty()) { return false; } fieldCenter = position(); fieldTicks = 160; fieldDamage.clear(); cooldowns[2] = INTERVALS[2]; action("field"); InfernalPhantomVfx.emit(this, InfernalPhantomVfx.FIELD, null, fieldCenter, Vec3.ZERO, 160, 10); return true; }
    private void tickField(ServerLevel level) {
        if (fieldTicks <= 0) { return; } if (position().distanceToSqr(fieldCenter) > 32 * 32) { fieldTicks = 0; fieldDamage.clear(); return; }
        int age = 160 - --fieldTicks;
        if (age % 20 == 0) {
            for (var enemy : enemies(level, fieldCenter, 10)) { fieldHit(level, enemy, power(2)); enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), this); }
            for (var ward : level.getEntitiesOfClass(AbstractPhantom.class, new AABB(fieldCenter, fieldCenter).inflate(10), target -> target.isTame() && recipient(target) && target.position().distanceToSqr(fieldCenter) <= 100 && line(fieldCenter.add(0, .6D, 0), target.getEyePosition())).stream().limit(16).toList()) { ward.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 35, 0), this); }
        }
        if (age == 20 || age == 45 || age == 70 || age == 95 || age == 120) { var targets = enemies(level, fieldCenter, 10); if (!targets.isEmpty()) { Vec3 location = targets.get((age / 25) % targets.size()).position(); for (var enemy : enemies(level, location, 3)) { fieldHit(level, enemy, power(3)); } InfernalPhantomVfx.emit(this, InfernalPhantomVfx.ERUPTION, null, location, Vec3.ZERO, 28, 3); } }
    }
    private void fieldHit(ServerLevel level, LivingEntity enemy, float amount) { float previous = fieldDamage.getOrDefault(enemy.getUUID(), 0F); if (!fieldDamage.containsKey(enemy.getUUID()) && fieldDamage.size() >= MAX_TARGETS) { return; } amount = Math.min(amount, Math.max(0, 24 - previous)); if (hit(level, enemy, amount, false, 1, false, false)) { fieldDamage.put(enemy.getUUID(), previous + amount); } }
    public boolean overheat(ServerLevel level) {
        int consumed = heat(); if (!enabled() || consumed == 0 || cooldowns[3] > 0 || ascendantTicks > 0 || enemies(level, position(), 4 + consumed).isEmpty()) { return false; }
        entityData.set(HEAT, 0); heatProgress = 0; heatLock = 40; cooldowns[3] = INTERVALS[3]; action("overheat");
        for (var enemy : enemies(level, position(), 4 + consumed)) { hit(level, enemy, consumed * 10, false, consumed + 1, false, false); repel(enemy, .4D + consumed * .15D); }
        InfernalPhantomVfx.emit(this, InfernalPhantomVfx.OVERHEAT, null, position(), Vec3.ZERO, 36, 4 + consumed); return true;
    }
    public boolean ash(ServerLevel level) { if (!enabled() || cooldowns[4] > 0 || ascendantTicks > 0 || enemies(level, position(), 6).isEmpty()) { return false; } cooldowns[4] = INTERVALS[4]; action("ash"); for (var enemy : enemies(level, position(), 6)) { hit(level, enemy, power(12), false, 2, false, false); enemy.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this); repel(enemy, .5D); } InfernalPhantomVfx.emit(this, InfernalPhantomVfx.ASH, null, position(), Vec3.ZERO, 30, 6); return true; }
    public boolean cinderGuard(ServerLevel level) { if (!enabled() || cooldowns[5] > 0 || guardTicks > 0) { return false; } guardTicks = 100; cooldowns[5] = INTERVALS[5]; entityData.set(GUARDED, true); action("guard"); for (var enemy : enemies(level, position(), 5)) { repel(enemy, .5D); } InfernalPhantomVfx.emit(this, InfernalPhantomVfx.GUARD, this, position(), Vec3.ZERO, 100, 10); return true; }
    private void repel(LivingEntity target, double strength) { if (validTarget(target)) { target.knockback(strength, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 0); } }
    public boolean hellAscendant(ServerLevel level) {
        if (!enabled() || cooldowns[6] > 0 || ascendantTicks > 0 || rushTicks > 0 || !validTarget(getTarget())) { return false; }
        var targets = enemies(level, position(), 18); if (targets.size() < 3 && getTarget().getMaxHealth() < 100 || !targets.contains(getTarget())) { return false; }
        marked.clear(); for (var target : targets) { marked.put(target.getUUID(), new Budget()); } ascendantTarget = getTarget().getUUID(); ascendantTicks = ASCENDANT_DURATION; cooldowns[6] = INTERVALS[6]; burstTicks = 0; entityData.set(ASCENDANT, true); action("awakening"); InfernalPhantomVfx.emit(this, InfernalPhantomVfx.ASCENDANT, this, position(), Vec3.ZERO, ASCENDANT_DURATION, 18); return true;
    }
    private void tickAscendant(ServerLevel level) {
        if (ascendantTicks <= 0) { return; } LivingEntity target = level.getEntity(ascendantTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || distanceToSqr(target) > 32 * 32 || !line(getEyePosition(), target.getEyePosition())) { endInfernal(); return; }
        int age = ASCENDANT_DURATION - --ascendantTicks;
        if (age <= 30 && !isVehicle()) { safeMove(level, new Vec3(0, .08D, 0)); }
        if (age == 60 || age == 70 || age == 80 || age == 90) { action("hellfire"); for (var enemy : enemies(level, position(), 32).stream().filter(enemy -> marked.containsKey(enemy.getUUID())).limit(3).toList()) { shoot(level, enemy.getEyePosition().subtract(getEyePosition()), true); } }
        if (age == 120 || age == 135 || age == 150 || age == 165 || age == 180) { action("dominion"); for (var enemy : enemies(level, target.position(), 5)) { hit(level, enemy, 6, false, 0, true, false); enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 0), this); } InfernalPhantomVfx.emit(this, InfernalPhantomVfx.ERUPTION, null, target.position(), Vec3.ZERO, 30, 5); }
        if (age == 200) { startRush(target, true); }
        if (ascendantTicks == 0) { stopAscendant(level); }
    }
    private void stopAscendant(ServerLevel level) { ascendantTicks = 0; ascendantTarget = null; entityData.set(ASCENDANT, false); marked.clear(); if (rushUltimate) { stopRush(); } for (UUID identity : List.copyOf(bolts)) { if (level.getEntity(identity) instanceof InfernalBolt bolt && bolt.ultimate()) { bolt.discard(); } } }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) { if (!enabled() || !validTarget(target) || isVehicle()) { return false; } if (ascendantTicks > 0 || rushTicks > 0) { return true; } int nearby = enemies(level, position(), 10).size(); if ((nearby >= 3 || target.getMaxHealth() >= 100) && hellAscendant(level)) { return true; } if (heat() == 3 && nearby >= 2 && overheat(level)) { return true; } if (nearby >= 3 && ash(level)) { return true; } if (nearby >= 2 && field(level)) { return true; } if (distanceToSqr(target) <= 100 && hellrush(level, target)) { return true; } return bolt(level, target); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { if (!enabled() || ascendantTicks > 0 || rushTicks > 0 || meleeTicks > 0 || !(target instanceof LivingEntity living) || !getBoundingBox().inflate(2).intersects(living.getBoundingBox())) { return false; } if (!hit(level, living, power((float)getAttributeValue(Attributes.ATTACK_DAMAGE)), true, 2, false, false)) { return false; } meleeTicks = 40; return true; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return !source.is(DamageTypeTags.IS_FIRE) && super.hurtServer(level, source, amount); }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { if (isAlive() && !isBaby() && tickCount % Math.max(2, 7 - heat()) == 0) { level().addParticle(ParticleTypes.FLAME, getX(), getY() + .4D, getZ(), 0, .01D, 0); } return; }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); } if (meleeTicks > 0) { meleeTicks--; } if (heatLock > 0) { heatLock--; }
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID(); if (ownerContext != null && !ownerContext.equals(context)) { endInfernal(); } ownerContext = context;
        if (!enabled()) { endInfernal(); decayHeat(); return; }
        if (combatTicks > 0) { combatTicks--; }
        if (validTarget(getTarget()) || combatTicks > 0 || burstTicks > 0 || ascendantTicks > 0 || rushTicks > 0) { idleHeat = 0; restTicks = 0; } else { decayHeat(); }
        if (burstTicks > 0) { int remaining = --burstTicks; LivingEntity target = level.getEntity(burstTarget) instanceof LivingEntity living ? living : null; if (!validTarget(target) || isVehicle() || !line(getEyePosition(), target.getEyePosition()) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE) { burstTicks = 0; } else if (remaining == 4 || remaining == 0) { shoot(level, target.getEyePosition().subtract(getEyePosition()), false); } }
        tickField(level); tickAscendant(level); tickRush(level); if (guardTicks > 0) { guardTicks--; } entityData.set(GUARDED, guardTicks > 0);
        if ((tickCount + getId()) % 20 == 0 && (validTarget(getTarget()) || combatTicks > 0)) { for (var enemy : enemies(level, position(), heat() >= 2 ? 7 : 5)) { enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0), this); if (heat() == 3) { enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 0), this); } } }
        if (!isTame() && !isVehicle() && !validTarget(getTarget()) && getOrder() == Order.WANDER && tickCount % 200 == 0 && random.nextInt(8) == 0 && !level.getBlockState(blockPosition().below()).getCollisionShape(level, blockPosition().below()).isEmpty() && level.getFluidState(blockPosition().below()).isEmpty()) { restTicks = 80; action("rest"); }
        if (restTicks > 0) { restTicks--; } entityData.set(RESTING, restTicks > 0 && !isVehicle());
        if (!isNoAi() && tickCount % 10 == 0 && validTarget(getTarget())) { prepareSpeciesCombatAction(level, getTarget()); }
    }
    private void decayHeat() { if (++idleHeat >= 200) { idleHeat = 0; heatProgress = 0; entityData.set(HEAT, Math.max(0, heat() - 1)); } }
    private void action(String clip) { triggerAnim("infernal", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { super.registerControllers(controllers); var controller = new AnimationController<InfernalPhantom>("infernal", 3, state -> PlayState.STOP); for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.infernal." + clip)); } controllers.add(controller); }
    @Override public TamingProfile preferredTaming() { return new TamingProfile(Items.BLAZE_POWDER, .5F, 3, "Blaze Powder: 50%, guaranteed by the third eligible attempt. Staff: instant."); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(Items.BLAZE_POWDER)) { if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } if (++tameAttempts >= 3 || random.nextFloat() < .5F) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte)7); } else { level().broadcastEntityEvent(this, (byte)6); } } return InteractionResult.SUCCESS; } return super.mobInteract(player, hand); }
    public void endInfernal() { if (level() instanceof ServerLevel level) { for (UUID identity : List.copyOf(bolts)) { Entity entity = level.getEntity(identity); if (entity != null) { entity.discard(); } } if (visualOpen) { InfernalPhantomVfx.cancel(this); visualOpen = false; session = UUID.randomUUID(); } } bolts.clear(); combatTicks = 0; fieldDamage.clear(); marked.clear(); burstTicks = 0; burstTarget = null; fieldTicks = 0; fieldCenter = null; guardTicks = 0; ascendantTicks = 0; ascendantTarget = null; restTicks = 0; entityData.set(ASCENDANT, false); entityData.set(GUARDED, false); entityData.set(RESTING, false); stopRush(); }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endInfernal(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endInfernal(); orderedTarget = null; } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endInfernal(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endInfernal(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < 7; slot++) { output.putInt("InfernalCooldown" + slot, cooldowns[slot]); } output.putInt("InfernalHeat", heat()); output.putInt("InfernalHeatProgress", heatProgress); output.putInt("InfernalHeatLock", heatLock); output.putInt("InfernalTamingAttempts", tameAttempts); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endInfernal(); session = UUID.randomUUID(); for (int slot = 0; slot < 7; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("InfernalCooldown" + slot, 0), 0, INTERVALS[slot]); } entityData.set(HEAT, Math.clamp(input.getIntOr("InfernalHeat", 0), 0, 3)); heatProgress = Math.clamp(input.getIntOr("InfernalHeatProgress", 0), 0, 2); heatLock = Math.clamp(input.getIntOr("InfernalHeatLock", 0), 0, 40); tameAttempts = Math.clamp(input.getIntOr("InfernalTamingAttempts", 0), 0, 3); }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { var result = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 6; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("infernal_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(result); }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < KEYS.length; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return -1; }
    private static final class Budget { float damage; boolean finished; }
}
