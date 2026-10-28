package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.IllagerCharm;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.IllagerVfx;

public final class IllagerPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath("phantomism", "entity/illager_phantom");
    public static final int BARRAGE_RANGE = 42, BARRAGE_COOLDOWN = 100, MOUNTED_COOLDOWN = 6;
    public static final int WRATH_COOLDOWN = 200, DOMINION_COOLDOWN = 500, CONQUEST_COOLDOWN = 1800, CONQUEST_DURATION = 180;
    public static final int MAX_VEXES = 3, MAX_BOLTS = 24, MAX_FANGS = 12, MAX_TARGETS = 12;
    private static final int[] INTERVALS = {100, 200, 500, 1800};
    private static final String[] CLIPS = {"barrage", "wrath", "dominion", "conquest", "command", "judgment", "execution", "watch", "rest"};
    private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(IllagerPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(IllagerPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[4];
    private final Set<UUID> vexes = new LinkedHashSet<>(), fangs = new LinkedHashSet<>(), bolts = new LinkedHashSet<>();
    private final Map<UUID, Cast> casts = new HashMap<>();
    private final Map<UUID, Budget> marked = new HashMap<>();
    private UUID session = UUID.randomUUID(), orderedTarget, ownerContext, wrathTarget, conquestTarget;
    private int wrathTicks, conquestTicks, meleeTicks, tameAttempts;
    private boolean wrathUltimate, wrathMounted, visualOpen, charmOpen, explicitSelection;

    public IllagerPhantom(EntityType<? extends IllagerPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 280).add(Attributes.ATTACK_DAMAGE, 18).add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .65D).add(Attributes.FOLLOW_RANGE, 56)
                .add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .45D);
    }
    public static boolean checkSpawnRules(EntityType<IllagerPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, net.minecraft.util.RandomSource random) {
        if (!(accessor instanceof ServerLevel level) || !level.getServer().isSameThread() || level.dimension() != Level.OVERWORLD || !level.hasChunkAt(position)) { return false; }
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.05D);
        return box.minY > level.getMinY() && box.maxY < level.getMaxY() && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && !level.containsAnyLiquid(box) && level.noBlockCollision(null, box)
                && (net.ronm19.phantomism.entity.spawn.IllagerEncounterSpawner.activeRaid(level.getRaidAt(position))
                || net.ronm19.phantomism.entity.spawn.IllagerEncounterSpawner.outpost(level, position) != null);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ILLAGER_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected int getFlightTurnRate() { return 16; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL, 0); data.define(DIVING, false); }
    public UUID session() { return session; }
    public int visualState() { return entityData.get(VISUAL); }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int conquestTicks() { return conquestTicks; }
    public int wrathTicks() { return wrathTicks; }
    public void visualStarted() { visualOpen = true; }
    public void charmStarted() { charmOpen = true; }
    private void releaseCharm() { if (charmOpen) { IllagerCharm.release(this); charmOpen = false; } }
    @Override public boolean isUltimateAnimationActive() { return conquestTicks > 0 || super.isUltimateAnimationActive(); }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean canCharm() {
        return isAlive() && !isRemoved() && !isBaby() && isTame() && getOwner() instanceof ServerPlayer owner && owner.isAlive()
                && owner.level() == level() && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    public boolean recipient(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && (target == this || target instanceof IllagerSpectralVex
                || isTame() && (target == getOwner() || getTeam() != null && getTeam().isAlliedTo(target.getTeam())
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())));
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
        return ward != null && (ward.getLastHurtByMob() == enemy && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100
                || enemy instanceof Mob mob && mob.getTarget() == ward);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (isFamilyDefenseThreat(target) || target.getUUID().equals(orderedTarget) || attacking(target, this) || attacking(target, getOwner())
                || target instanceof Mob mob && (isTame() && IllagerCharm.hostile(this, mob) || recipient(mob.getTarget())));
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    @Override protected boolean prefersRangedCombat() { return true; }
    @Override public void setCombatTarget(LivingEntity target) {
        explicitSelection = true;
        try {
            if (!safeEnemy(target)) { return; }
            orderedTarget = target.getUUID(); super.setCombatTarget(target);
            if (getTarget() == target) { IllagerCharm.breakFor(this, target); } else { orderedTarget = null; }
        } finally { explicitSelection = false; }
    }
    public void retaliate(LivingEntity attacker) {
        if (!safeEnemy(attacker)) { return; }
        IllagerCharm.breakFor(this, attacker);
        if (enabled()) { setCombatTarget(attacker); if (cooldowns[0] == 0 && conquestTicks == 0) { barrage((ServerLevel)level(), attacker); } }
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.05D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> center.distanceToSqr(target.position()) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> center.distanceToSqr(target.position()))).limit(MAX_TARGETS).toList();
    }
    private boolean damage(ServerLevel level, LivingEntity target, float amount, boolean ultimate, boolean execution, boolean primary) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return false; }
        Budget budget = ultimate ? marked.get(target.getUUID()) : null;
        if (ultimate) {
            if (conquestTicks <= 0 || budget == null || execution && budget.executed) { return false; }
            amount = Math.min(amount, execution ? primary ? 45 : 25 : Math.max(0, 30 - budget.prelude));
            if (amount <= 0) { return false; }
        }
        int immunity = target.damageCooldownTime;
        target.damageCooldownTime = 0;
        boolean accepted;
        try { accepted = target.hurtServer(level, damageSources().mobAttack(this), amount); }
        finally { target.damageCooldownTime = Math.max(immunity, target.damageCooldownTime); }
        if (accepted && budget != null) { if (execution) { budget.executed = true; } else { budget.prelude += amount; } }
        return accepted;
    }
    public boolean barrage(ServerLevel level, LivingEntity target) {
        if (!enabled() || isVehicle() || cooldowns[0] > 0 || conquestTicks > 0 || wrathTicks > 0 || !validTarget(target)
                || distanceToSqr(target) > BARRAGE_RANGE * BARRAGE_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (!volley(level, target, target.getEyePosition().subtract(getEyePosition()), false)) { return false; }
        cooldowns[0] = BARRAGE_COOLDOWN; action("barrage"); return true;
    }
    private boolean volley(ServerLevel level, LivingEntity primary, Vec3 direction, boolean ultimate) {
        pruneEntities(level);
        if (bolts.size() > MAX_BOLTS - 3 || casts.size() >= 16 || direction.lengthSqr() < .001D || !Double.isFinite(direction.lengthSqr())) { return false; }
        UUID castId = UUID.randomUUID(); var cast = new Cast(ultimate); casts.put(castId, cast);
        var targets = new ArrayList<LivingEntity>(); if (validTarget(primary)) { targets.add(primary); }
        for (LivingEntity enemy : enemies(level, position(), BARRAGE_RANGE)) { if (enemy != primary && targets.size() < 3 && (!ultimate || marked.containsKey(enemy.getUUID()))) { targets.add(enemy); } }
        boolean piercing = targets.size() > 1 && targets.get(1).position().subtract(getEyePosition()).normalize().dot(direction.normalize()) > .98D;
        int created = 0;
        for (int index = 0; index < 3; index++) {
            LivingEntity target = targets.isEmpty() ? null : targets.get(index % targets.size());
            Vec3 aim = direction.normalize();
            if (target != null) {
                double lead = Math.clamp(distanceTo(target) / 2.2D, 0, 8); Vec3 movement = target.getDeltaMovement().scale(lead);
                if (movement.lengthSqr() > 16) { movement = movement.normalize().scale(4); }
                aim = target.getEyePosition().add(movement).subtract(getEyePosition()).normalize();
            }
            Vec3 muzzle = getEyePosition().add(aim.scale(.7D)); if (!line(getEyePosition(), muzzle)) { continue; }
            var shot = PhantomismContent.ILLAGER_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { continue; }
            shot.configure(this, session, castId, piercing); shot.setPos(muzzle); shot.shoot(aim.x, aim.y, aim.z, 2.2F, 0);
            if (level.addFreshEntity(shot)) { bolts.add(shot.getUUID()); created++; IllagerVfx.emit(this, IllagerVfx.BOLT, shot, muzzle, aim, 28, .7F); }
        }
        if (created == 0) { casts.remove(castId); return false; }
        IllagerVfx.emit(this, IllagerVfx.CROSSBOW, this, getEyePosition(), direction.normalize(), 15, 1.6F);
        playSound(SoundEvents.CROSSBOW_SHOOT, .7F, .8F); return true;
    }
    public boolean boltImpact(ServerLevel level, UUID castId, LivingEntity target, Vec3 point) {
        Cast cast = casts.get(castId);
        if (cast == null || !validTarget(target) || cast.bolts.getOrDefault(target.getUUID(), 0) >= 3) { return false; }
        cast.bolts.merge(target.getUUID(), 1, Integer::sum);
        boolean result = damage(level, target, 6, cast.ultimate || conquestTicks > 0, false, false);
        IllagerVfx.emit(this, IllagerVfx.IMPACT, null, point, Vec3.ZERO, 15, 1.2F); return result;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_COOLDOWN; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        if (!enabled() || cooldowns[0] > 0 || conquestTicks > 0 || wrathTicks > 0) { return false; }
        Vec3 start = rider.getEyePosition(), direction = rider.getViewVector(1), end = start.add(direction.scale(BARRAGE_RANGE));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.5D), this::safeEnemy).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.3D).clip(start, end).isPresent() && line(start, enemy.getEyePosition()))
                .min(Comparator.comparingDouble(rider::distanceToSqr)).orElse(null);
        if (target != null) { setCombatTarget(target); }
        return volley(level, target, direction, false);
    }
    public boolean wrath(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || conquestTicks > 0 || wrathTicks > 0 || !validTarget(target) || distanceToSqr(target) > 20 * 20 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        startDive(target, false); cooldowns[1] = WRATH_COOLDOWN; return true;
    }
    private void startDive(LivingEntity target, boolean ultimate) {
        wrathTarget = target.getUUID(); wrathTicks = ultimate ? 50 : 30; wrathUltimate = ultimate; wrathMounted = isVehicle();
        entityData.set(DIVING, !wrathMounted); action(ultimate ? "execution" : "wrath");
        IllagerVfx.emit(this, IllagerVfx.WRATH, this, position(), target.position().subtract(position()).normalize(), 35, ultimate ? 7 : 5);
    }
    private void tickWrath(ServerLevel level) {
        if (wrathTicks <= 0) { return; }
        LivingEntity target = level.getEntity(wrathTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || !line(getEyePosition(), target.getEyePosition()) || --wrathTicks == 0 || wrathMounted && !isVehicle()) { stopWrath(); return; }
        if (!wrathMounted && distanceToSqr(target) > 3 * 3) {
            Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            step = step.normalize().scale(Math.min(step.length(), .85D)); AABB sweep = getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(sweep) || !level.getWorldBorder().isWithinBounds(sweep) || !level.noBlockCollision(this, sweep) || level.containsAnyLiquid(sweep)) { stopWrath(); return; }
            move(MoverType.SELF, step); needsSync = true;
        }
        if (getBoundingBox().inflate(2).intersects(target.getBoundingBox())) {
            boolean ultimate = wrathUltimate; boolean hit = damage(level, target, ultimate ? 45 : 24, ultimate, true, true);
            if (hit) { target.knockback(.6D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), ultimate ? 45 : 24); }
            for (LivingEntity enemy : enemies(level, target.position(), 5)) { if (enemy != target) { damage(level, enemy, ultimate ? 25 : 12, ultimate, true, false); } }
            IllagerVfx.emit(this, IllagerVfx.EXECUTION, null, target.position(), getViewVector(1), 32, 5); stopWrath();
        }
    }
    private void stopWrath() { wrathTicks = 0; wrathTarget = null; wrathMounted = false; wrathUltimate = false; entityData.set(DIVING, false); }
    @Override public void travel(Vec3 input) { if (entityData.get(DIVING) && !isVehicle()) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    public boolean activateRiddenWrath(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        var target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20), this::safeEnemy).stream()
                .filter(enemy -> distanceToSqr(enemy) <= 400 && line(getEyePosition(), enemy.getEyePosition()) && rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) { return false; } setCombatTarget(target); return wrath(level, target);
    }
    public boolean dominion(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || conquestTicks > 0 || enemies(level, position(), 24).isEmpty()) { return false; }
        boolean used = summon(level, false); used |= castFangs(level, false);
        if (!used) { return false; } cooldowns[2] = DOMINION_COOLDOWN; action("dominion"); return true;
    }
    private boolean summon(ServerLevel level, boolean ultimate) {
        pruneEntities(level); boolean used = false;
        for (int index = vexes.size(); index < MAX_VEXES; index++) {
            var vex = PhantomismContent.ILLAGER_SPECTRAL_VEX.get().create(level, EntitySpawnReason.TRIGGERED); if (vex == null) { continue; }
            Vec3 point = position().add(Math.cos(index * 2.1D) * 2, 1.2D, Math.sin(index * 2.1D) * 2);
            if (!isLoadedFlightBox(new AABB(point, point).inflate(1)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(point))) { continue; }
            vex.configure(this, ultimate, ultimate ? Math.min(400, conquestTicks) : 400); vex.setPos(point); vex.setBoundOrigin(blockPosition());
            if (level.addFreshEntity(vex)) { vexes.add(vex.getUUID()); used = true; IllagerVfx.emit(this, IllagerVfx.DOMINION, vex, point, Vec3.ZERO, 35, 2); }
        }
        return used;
    }
    private boolean castFangs(ServerLevel level, boolean ultimate) {
        pruneEntities(level); if (fangs.size() >= MAX_FANGS || casts.size() >= 16) { return false; }
        UUID castId = UUID.randomUUID(); casts.put(castId, new Cast(ultimate)); int created = 0;
        for (LivingEntity target : enemies(level, position(), 24)) {
            if (ultimate && !marked.containsKey(target.getUUID())) { continue; }
            for (int offset = -1; offset <= 1 && fangs.size() < MAX_FANGS; offset++) {
                Vec3 location = ground(level, target.position().add(offset * .7D, 0, 0)); if (location == null) { continue; }
                var fang = PhantomismContent.ILLAGER_FANGS.get().create(level, EntitySpawnReason.TRIGGERED); if (fang == null) { continue; }
                fang.configure(this, castId, session, Math.abs(offset) * 3); fang.setPos(location); fang.setOwner(this); fang.setYRot(getYRot());
                if (level.addFreshEntity(fang)) { fangs.add(fang.getUUID()); created++; IllagerVfx.emit(this, IllagerVfx.FANGS, fang, location, Vec3.ZERO, 22, 1); }
            }
        }
        if (created == 0) { casts.remove(castId); } return created > 0;
    }
    private Vec3 ground(ServerLevel level, Vec3 near) {
        for (int depth = -1; depth <= 6; depth++) {
            BlockPos floor = BlockPos.containing(near).below(depth);
            if (!level.hasChunkAt(floor) || !level.hasChunkAt(floor.above()) || !level.getWorldBorder().isWithinBounds(floor)) { return null; }
            var state = level.getBlockState(floor);
            if (state.isFaceSturdy(level, floor, Direction.UP) && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()) { return new Vec3(near.x, floor.getY() + 1, near.z); }
        }
        return null;
    }
    public float fangAmount(UUID castId, LivingEntity target) {
        Cast cast = casts.get(castId);
        if (!enabled() || cast == null || !validTarget(target) || !cast.fangs.add(target.getUUID())) { return 0; }
        if (!cast.ultimate && conquestTicks <= 0) { return 8; }
        Budget budget = marked.get(target.getUUID()); if (conquestTicks <= 0 || budget == null) { return 0; }
        float amount = Math.min(8, Math.max(0, 30 - budget.prelude)); budget.prelude += amount; return amount;
    }
    public boolean vexStrike(ServerLevel level, LivingEntity target, boolean ultimate) { return damage(level, target, 4, ultimate || conquestTicks > 0, false, false); }
    public boolean conquest(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || conquestTicks > 0 || wrathTicks > 0 || !validTarget(getTarget())) { return false; }
        var enemies = enemies(level, position(), 18); if (enemies.size() < 2 && enemies.stream().noneMatch(enemy -> enemy.getMaxHealth() >= 100)) { return false; }
        marked.clear(); for (LivingEntity enemy : enemies) { marked.put(enemy.getUUID(), new Budget()); }
        if (!marked.containsKey(getTarget().getUUID())) { return false; }
        conquestTarget = getTarget().getUUID(); conquestTicks = CONQUEST_DURATION; cooldowns[3] = CONQUEST_COOLDOWN; action("command");
        IllagerVfx.emit(this, IllagerVfx.CONQUEST, this, position(), Vec3.ZERO, CONQUEST_DURATION, 18); return true;
    }
    private void tickConquest(ServerLevel level) {
        if (conquestTicks <= 0) { return; }
        LivingEntity target = level.getEntity(conquestTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || distanceToSqr(target) > 32 * 32 || !line(getEyePosition(), target.getEyePosition())) { stopConquest(level); return; }
        int elapsed = CONQUEST_DURATION - --conquestTicks;
        if (elapsed == 40 || elapsed == 60 || elapsed == 80) { volley(level, target, target.getEyePosition().subtract(getEyePosition()), true); action("barrage"); }
        if (elapsed == 100) { summon(level, true); castFangs(level, true); action("judgment"); }
        if (elapsed == 120) { startDive(target, true); }
        if (conquestTicks == 0) { stopConquest(level); }
    }
    private void stopConquest(ServerLevel level) {
        conquestTicks = 0; conquestTarget = null; marked.clear(); if (wrathUltimate) { stopWrath(); }
        for (UUID identity : List.copyOf(vexes)) { if (level.getEntity(identity) instanceof IllagerSpectralVex vex && vex.conquestSummon()) { vex.discard(); } }
    }
    public LivingEntity summonTarget() {
        if (validTarget(getTarget()) && distanceToSqr(getTarget()) <= 32 * 32) { return getTarget(); }
        return level() instanceof ServerLevel level ? enemies(level, position(), 24).stream().findFirst().orElse(null) : null;
    }
    public int vexCount() { if (level() instanceof ServerLevel level) { pruneEntities(level); } return vexes.size(); }
    private void pruneEntities(ServerLevel level) { vexes.removeIf(identity -> !(level.getEntity(identity) instanceof IllagerSpectralVex)); fangs.removeIf(identity -> !(level.getEntity(identity) instanceof IllagerFangs)); bolts.removeIf(identity -> !(level.getEntity(identity) instanceof IllagerBolt)); }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || isVehicle()) { return false; }
        if (conquestTicks > 0 || wrathTicks > 0) { return true; }
        int crowd = enemies(level, position(), 18).size();
        if ((crowd >= 3 || target.getMaxHealth() >= 100) && conquest(level)) { return true; }
        if ((crowd >= 2 || target.getMaxHealth() >= 60) && dominion(level)) { return true; }
        if (distanceToSqr(target) <= 10 * 10 && wrath(level, target)) { return true; }
        return barrage(level, target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return enabled() && conquestTicks == 0 && wrathTicks == 0 && meleeTicks == 0 && target instanceof LivingEntity living && getBoundingBox().inflate(2).intersects(living.getBoundingBox()) && damage(level, living, (float)getAttributeValue(Attributes.ATTACK_DAMAGE), false, false, true) && recordMelee(); }
    private boolean recordMelee() { meleeTicks = 40; return true; }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        if (meleeTicks > 0) { meleeTicks--; }
        casts.entrySet().removeIf(entry -> --entry.getValue().ticks <= 0);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(context)) { endIllager(); } ownerContext = context;
        if (canCharm() && (tickCount + getId()) % 10 == 0) { IllagerCharm.refresh(this); }
        if (!enabled()) { endCombat(); entityData.set(VISUAL, 0); if (!canCharm()) { releaseCharm(); } return; }
        tickConquest(level); tickWrath(level); entityData.set(VISUAL, conquestTicks > 0 ? 2 : validTarget(getTarget()) ? 1 : 0);
        if (!isNoAi() && tickCount % 10 == 0 && validTarget(getTarget())) { prepareSpeciesCombatAction(level, getTarget()); }
    }
    private void action(String clip) { triggerAnim("illager", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<IllagerPhantom>("illager", 3, state -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.illager." + clip)); } controllers.add(controller);
    }
    @Override public TamingProfile preferredTaming() { return new TamingProfile(net.minecraft.world.item.Items.EMERALD, .5F, 3, "Emeralds: 50%, guaranteed by the third eligible attempt. Staff: instant."); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.EMERALD)) {
            if (!level().isClientSide()) {
                if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); }
                if (++tameAttempts >= 3 || getRandom().nextFloat() < .5F) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte)7); }
                else { level().broadcastEntityEvent(this, (byte)6); }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    private void endCombat() {
        if (level() instanceof ServerLevel level) {
            for (var collection : List.of(vexes, fangs, bolts)) { for (UUID identity : List.copyOf(collection)) { Entity entity = level.getEntity(identity); if (entity != null) { entity.discard(); } } collection.clear(); }
            if (visualOpen) { IllagerVfx.cancel(this); session = UUID.randomUUID(); visualOpen = false; }
        }
        casts.clear(); marked.clear(); conquestTicks = 0; conquestTarget = null; stopWrath();
    }
    public void endIllager() { endCombat(); orderedTarget = null; releaseCharm(); }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endIllager(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endIllager(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endIllager(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endIllager(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < 4; slot++) { output.putInt("IllagerCooldown" + slot, cooldowns[slot]); } output.putInt("IllagerTamingAttempts", tameAttempts); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endIllager(); session = UUID.randomUUID(); for (int slot = 0; slot < 4; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("IllagerCooldown" + slot, 0), 0, INTERVALS[slot]); } tameAttempts = Math.clamp(input.getIntOr("IllagerTamingAttempts", 0), 0, 3); }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { var result = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 3; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("illager_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(result); }
    @Override public int staffAbilityCooldown(String ability) { return switch (ability) { case "barrage" -> cooldowns[0]; case "wrath" -> cooldowns[1]; case "dominion" -> cooldowns[2]; case "conquest" -> cooldowns[3]; default -> -1; }; }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
    private static final class Cast { final boolean ultimate; final Map<UUID, Integer> bolts = new HashMap<>(); final Set<UUID> fangs = new HashSet<>(); int ticks = 80; Cast(boolean ultimate) { this.ultimate = ultimate; } }
    private static final class Budget { float prelude; boolean executed; }
}
