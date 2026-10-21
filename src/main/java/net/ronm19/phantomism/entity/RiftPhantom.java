package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.RiftVfx;
import net.minecraft.core.GlobalPos;
import java.util.*;

public final class RiftPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/rift_phantom");
    private static final String[] CLIPS = {"bolt", "rescue", "throw", "anchor", "step", "lock", "corridor", "ultimate", "dash"};
    private final int[] cooldowns = new int[9];
    private UUID session = UUID.randomUUID(), pursuit, diveTarget;
    private int actionTicks, pursuitTicks, diveTicks, meleeCooldown, ultimateTicks, tamingAttempts;
    private boolean controlsActive;
    private GlobalPos anchor;
    private Vec3 anchorPosition, portalA, portalB, pendingA, ultimateCenter;
    private long anchorUntil, portalUntil, portalReady, pendingUntil;
    private final Set<UUID> displaced = new HashSet<>();
    private List<Mob> sensed = List.of();
    public RiftPhantom(EntityType<? extends RiftPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 8).add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, .30D).add(Attributes.MOVEMENT_SPEED, .24D).add(Attributes.FLYING_SPEED, .45D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<RiftPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.NETHER && level.getBiome(position).is(ModTags.Biomes.RIFT_PHANTOM_SPAWNS)
                && position.getY() > level.getMinY() + 2 && position.getY() < 117
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.RIFT_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return this.ultimateTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected int getMeleeAttackCooldownTicks() { return 36; }
    @Override public void travel(Vec3 input) { if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return this.session; }
    public boolean controlsEnabled() { return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME
            && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT) && (!this.isTame() || this.getOwner() != null && this.getOwner().isAlive() && this.getOwner().level() == this.level()); }
    public boolean acceptsAlly(LivingEntity ally) {
        if (!ally.isAlive() || ally.isRemoved() || ally.level() != this.level()) { return false; }
        return ally == this || this.getOwner() != null && (ally == this.getOwner() || ally instanceof OwnableEntity owned && java.util.Objects.equals(owned.getOwnerReference(), this.getOwnerReference()));
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        boolean provoked = target == this.getLastHurtByMob() && this.tickCount - this.getLastHurtByMobTimestamp() < 200
                || target instanceof Mob mob && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner());
        if (target instanceof Player) { return !this.isTame() && provoked; }
        if ((target instanceof NeutralMob || target.getType() == EntityTypes.PIGLIN) && !provoked) { return false; }
        return target instanceof Enemy && !(target instanceof AbstractPhantom) && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null) && !this.isAlliedTo(target);
    }
    public boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level() && !this.acceptsAlly(target) && this.isSpeciesCombatTarget(target)
                && !this.isTargetSuppressed(target) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) { return this.isLoadedFlightBox(new AABB(start, end).inflate(.02D)) && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public List<Mob> hostiles(ServerLevel level, double radius, int limit) {
        return this.hostilesAt(level, this.position(), radius, limit);
    }
    private List<Mob> hostilesAt(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius), target -> this.validTarget(target) && target.position().distanceToSqr(center) <= radius * radius
                && this.getAssignments().permitsDefensivePursuit(target) && this.line(level, this.getEyePosition(), target.getEyePosition()))
                .stream().sorted(Comparator.comparingDouble(target -> this.distanceToSqr(target) - (this.acceptsAllyTarget(target) ? 400 : 0) - target.getDeltaMovement().lengthSqr() * 100)).limit(limit).toList();
    }
    private boolean acceptsAllyTarget(Mob enemy) { return enemy.getTarget() != null && this.acceptsAlly(enemy.getTarget()); }
    private List<LivingEntity> allies(ServerLevel level) { return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(24), ally -> this.acceptsAlly(ally) && this.distanceToSqr(ally) <= 24 * 24).stream().limit(24).toList(); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return this.isTame() && this.getOrder() == Order.GUARD && this.getOwner() != null && target instanceof Enemy && this.getOwner().distanceToSqr(target) <= 24 * 24; }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return this.distanceToSqr(target) - (target instanceof Mob mob && this.acceptsAllyTarget(mob) ? 400 : 0); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand); if (isAlive() && !player.isSpectator() && !isTame() && stack.is(Items.CRYING_OBSIDIAN)) {
            if (!level().isClientSide()) { boolean success = tamingAttempts++ >= 1 || random.nextFloat() < .75F; if (!player.isCreative()) { stack.shrink(1); }
                if (success) { tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); } else { level().broadcastEntityEvent(this, (byte) 6); } } return InteractionResult.SUCCESS;
        } return super.mobInteract(player, hand);
    }
    public float damage(ServerLevel level, LivingEntity target, float amount, DamageSource source) {
        if (!this.controlsEnabled() || !this.validTarget(target) || !this.getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean hit = target.hurtServer(level, source, amount);
        return hit ? Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity enemy) || !this.controlsEnabled() || !this.validTarget(enemy) || this.meleeCooldown > 0
                || !this.getBoundingBox().inflate(.6D).intersects(enemy.getBoundingBox()) || !this.line(level, this.getEyePosition(), enemy.getEyePosition())) { return false; }
        if (!super.doHurtTarget(level, target)) { return false; } this.meleeCooldown = 36; return true;
    }
    public boolean riftBolt(ServerLevel level, Vec3 aim) {
        int interval = this.getControllingPassenger() instanceof Player ? 6 : 240;
        if (this.level() != level || !this.controlsEnabled() || this.cooldowns[0] > 240 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = this.getEyePosition().add(direction.scale(.7D)); if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        RiftBolt shot = PhantomismContent.RIFT_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(this.session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; } this.cooldowns[0] = 240; this.action("bolt", 6);
        RiftVfx.emit(this, RiftVfx.BOLT, shot, muzzle, direction, 36, .45F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.riftBolt(level, rider.getViewVector(1)); }
    public boolean dash(ServerLevel level, LivingEntity target) {
        if (!this.controlsEnabled() || this.cooldowns[8] > 0 || !this.validTarget(target) || this.distanceToSqr(target) > 6 * 6 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize();
        if (!this.isVehicle()) { Vec3 step = direction.scale(.65D); if (!this.safeDiveStep(level, step)) { return false; } this.setDeltaMovement(step); this.needsSync = true; }
        if (this.isVehicle() && !this.getBoundingBox().inflate(1.2D).intersects(target.getBoundingBox())) { return false; }
        this.diveTarget = target.getUUID(); this.diveTicks = 10; this.cooldowns[8] = 240; this.action("dash", 16);
        RiftVfx.emit(this, RiftVfx.DASH, this, this.position(), direction, 18, 1.3F); return true;
    }
    private void tickDive(ServerLevel level) {
        if (this.diveTicks <= 0) { return; } this.diveTicks--;
        if (!(level.getEntity(this.diveTarget) instanceof LivingEntity target) || !this.validTarget(target) || this.distanceToSqr(target) > 8 * 8 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { this.diveTicks = 0; return; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize();
        if (this.getBoundingBox().inflate(this.isVehicle() ? 1.2D : .65D).intersects(target.getBoundingBox())) {
            this.diveTicks = 0; if (this.damage(level, target, (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4D), this.damageSources().mobAttack(this)) > 0) { RiftVfx.emit(this, RiftVfx.DASH_IMPACT, null, target.getBoundingBox().getCenter(), direction, 12, 1.2F); } return;
        }
        if (this.isVehicle()) { this.diveTicks = 0; return; }
        Vec3 velocity = direction.scale(.65D);
        if (!this.safeDiveStep(level, velocity)) { this.diveTicks = 0; return; }
        this.setDeltaMovement(velocity); this.needsSync = true;
    }
    private boolean safeDiveStep(ServerLevel level, Vec3 velocity) {
        AABB swept = this.getBoundingBox().expandTowards(velocity);
        if (!this.isLoadedFlightBox(swept) || !level.noCollision(this, swept) || level.containsAnyLiquid(swept) || !level.getWorldBorder().isWithinBounds(swept)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } } return true;
    }
    public boolean activateRiddenDash(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider)) { return false; }
        Vec3 eye = rider.getEyePosition(), end = eye.add(rider.getViewVector(1).scale(6));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.6D).clip(eye, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && this.dash(level, target);
    }

    public boolean danger(ServerLevel level, LivingEntity ally) {
        boolean combat = !hostilesAt(level, ally.position(), 8, 1).isEmpty();
        return ally.isInLava() || ally.isOnFire() || ally.fallDistance > 4 || combat && ally.getHealth() <= ally.getMaxHealth() * .35F;
    }
    private void sense(ServerLevel level) { sensed = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), this::validTarget).stream().filter(enemy -> distanceToSqr(enemy) <= 48 * 48).limit(128).toList(); }
    private int threats(ServerLevel level, Vec3 position) { return (int) sensed.stream().filter(enemy -> validTarget(enemy) && enemy.position().distanceToSqr(position) <= 36).limit(16).count(); }
    private boolean movableAlly(LivingEntity ally) { return acceptsAlly(ally) && (!(ally instanceof AbstractPhantom phantom) || phantom.getOrder() != Order.STAY && phantom.getOrder() != Order.RETURN_HOME && (phantom.getTask() == Task.NONE || phantom.getTask() == Task.HUNT)); }
    public boolean rescue(ServerLevel level, LivingEntity ally, boolean severeHit) {
        if (!controlsEnabled() || cooldowns[1] > 0 || !movableAlly(ally) || distanceToSqr(ally) > 24 * 24 || !RiftTeleport.authorized(this, ally)
                || ally.getRootVehicle().getPersistentData().getLongOr("RiftRescueUntil", Long.MIN_VALUE) > level.getGameTime() || !severeHit && !danger(level, ally)) { return false; }
        sense(level); Entity root = ally.getRootVehicle(); Vec3 old = root.position(); int current = threats(level, old);
        java.util.function.Predicate<Vec3> better = candidate -> candidate.distanceToSqr(old) >= 16 && (threats(level, candidate) == 0 || threats(level, candidate) < current);
        Vec3 destination = validAnchor(level) && anchorPosition.distanceToSqr(old) <= 32 * 32 && better.test(anchorPosition) && RiftTeleport.safe(this, root, anchorPosition) ? anchorPosition : null;
        if (destination == null) {
            Vec3 away = old.subtract(hostilesAt(level, old, 12, 1).stream().findFirst().map(Entity::position).orElse(position())).normalize();
            Vec3 preferred = old.add(away.lengthSqr() < .1D ? new Vec3(6, 0, 0) : away.scale(7));
            destination = RiftTeleport.find(this, root, preferred, 16, better).orElse(null);
        }
        if (destination == null || !RiftTeleport.move(this, root, destination, 32, true)) { return false; }
        cooldowns[1] = 560; root.getPersistentData().putLong("RiftRescueUntil", level.getGameTime() + 200); action("rescue", 12); RiftVfx.transfer(this, old, root.position(), RiftVfx.Transfer.RESCUE); return true;
    }
    public boolean validAnchor(ServerLevel level) {
        return anchor != null && anchorPosition != null && anchor.dimension().equals(level.dimension()) && level.getGameTime() < anchorUntil && level.hasChunkAt(anchor.pos()) && distanceToSqr(anchorPosition) <= 64 * 64;
    }
    public boolean setAnchor(ServerLevel level, Vec3 position) {
        Entity traveler = getOwner() == null ? this : getOwner();
        position = RiftTeleport.arrival(this, traveler, position).orElse(null); if (position == null) { return false; }
        if (!controlsEnabled() || cooldowns[3] > 0 || validAnchor(level) || distanceToSqr(position) > 48 * 48 || !RiftTeleport.safe(this, traveler, position)) { return false; }
        anchor = GlobalPos.of(level.dimension(), BlockPos.containing(position)); anchorPosition = position; anchorUntil = level.getGameTime() + 600; cooldowns[3] = 600; action("anchor", 16);
        RiftVfx.emit(this, RiftVfx.ANCHOR, null, position, Vec3.ZERO, 600, 1); return true;
    }
    public boolean throwEnemy(ServerLevel level, LivingEntity target) { return displace(level, target, true); }
    private boolean displace(ServerLevel level, LivingEntity target, boolean ordinary) {
        if (!controlsEnabled() || ordinary && cooldowns[2] > 0 || !validTarget(target) || RiftControl.immune(target) || distanceToSqr(target) > 24 * 24
                || target.getPersistentData().getLongOr("RiftDisplacedUntil", Long.MIN_VALUE) > level.getGameTime() || target.isPassenger() || target.isVehicle()) { return false; }
        Vec3 protectedPosition = getOwner() == null ? position() : getOwner().position(); Vec3 old = target.position();
        Vec3 direction = old.subtract(protectedPosition).multiply(1, 0, 1).normalize(); if (direction.lengthSqr() < .1D) { direction = new Vec3(1, 0, 0); }
        Vec3 destination = RiftTeleport.find(this, target, old.add(direction.scale(6)), 8, candidate -> candidate.distanceToSqr(protectedPosition) > old.distanceToSqr(protectedPosition) + 9).orElse(null);
        if (destination == null || !RiftTeleport.move(this, target, destination, 8, false)) { return false; }
        target.getPersistentData().putLong("RiftDisplacedUntil", level.getGameTime() + 100); if (ordinary) { cooldowns[2] = 320; action("throw", 12); } RiftVfx.transfer(this, old, target.position(), RiftVfx.Transfer.THROW); return true;
    }
    public boolean step(ServerLevel level, Vec3 preferred) {
        if (!controlsEnabled() || cooldowns[4] > 0) { return false; }
        sense(level);
        Vec3 destination = RiftTeleport.find(this, this, preferred, 8, candidate -> candidate.distanceToSqr(position()) >= 4 && threats(level, candidate) <= threats(level, position())).orElse(null);
        if (destination == null) { return false; } Vec3 old = position();
        if (!RiftTeleport.move(this, this, destination, 8, true)) { return false; }
        RiftVfx.emit(this, RiftVfx.STEP, this, old, getViewVector(1), 18, 1);
        cooldowns[4] = 200; action("step", 12); RiftVfx.transfer(this, old, position(), RiftVfx.Transfer.STEP); return true;
    }
    public boolean lock(ServerLevel level, Mob target) {
        if (!controlsEnabled() || cooldowns[5] > 0 || !line(level, getEyePosition(), target.getEyePosition()) || !RiftControl.apply(this, target, true)) { return false; }
        cooldowns[5] = 400; action("lock", 16); RiftVfx.emit(this, RiftVfx.LOCK, target, target.position(), Vec3.ZERO, 80, 1); return true;
    }
    public boolean markPortal(ServerLevel level, Vec3 position) {
        if (!controlsEnabled() || !isTame()) { return false; } position = RiftTeleport.arrival(this, getOwner(), position).orElse(null); if (position == null) { return false; }
        if (!controlsEnabled() || !isTame() || cooldowns[6] > 0 || portalA != null || distanceToSqr(position) > 64 * 64 || !RiftTeleport.safe(this, getOwner(), position)) { return false; }
        pendingA = position; pendingUntil = level.getGameTime() + 600; RiftVfx.emit(this, RiftVfx.ANCHOR, null, position, Vec3.ZERO, 40, .7F); return true;
    }
    public boolean finishPortal(ServerLevel level, Vec3 position) {
        return pendingA != null && level.getGameTime() < pendingUntil && createCorridor(level, pendingA, position);
    }
    public boolean createCorridor(ServerLevel level, Vec3 first, Vec3 second) {
        if (!controlsEnabled() || !isTame()) { return false; }
        first = RiftTeleport.arrival(this, getOwner(), first).orElse(null); second = RiftTeleport.arrival(this, getOwner(), second).orElse(null); if (first == null || second == null) { return false; }
        if (!controlsEnabled() || !isTame() || cooldowns[6] > 0 || portalA != null || first.distanceToSqr(second) > 48 * 48 || first.distanceToSqr(second) < 16
                || distanceToSqr(first) > 64 * 64 || distanceToSqr(second) > 64 * 64 || !RiftTeleport.safe(this, getOwner(), first) || !RiftTeleport.safe(this, getOwner(), second)) { return false; }
        portalA = first; portalB = second; portalUntil = level.getGameTime() + 600; portalReady = level.getGameTime() + 10; pendingA = null; cooldowns[6] = 900;
        Vec3 direction = second.subtract(first).multiply(1, 0, 1).normalize();
        RiftVfx.emit(this, RiftVfx.PORTAL, null, first, direction, 600, 1.2F); RiftVfx.emit(this, RiftVfx.PORTAL, null, second, direction.scale(-1), 600, 1.2F); action("corridor", 20); return true;
    }
    public boolean enterCorridor(ServerLevel level, LivingEntity traveler) {
        if (!controlsEnabled() || !corridorValid(level) || level.getGameTime() < portalReady || !RiftTeleport.authorized(this, traveler)) { return false; }
        Entity root = traveler.getRootVehicle(); if (root.getPersistentData().getLongOr("RiftTraversalUntil", Long.MIN_VALUE) > level.getGameTime()) { return false; }
        boolean first = root.position().distanceToSqr(portalA) <= 9, second = root.position().distanceToSqr(portalB) <= 9;
        if (!first && !second) { return false; } Vec3 exit = first ? portalB : portalA;
        Vec3 destination = RiftTeleport.find(this, root, exit, 52, candidate -> candidate.distanceToSqr(exit) <= 9).orElse(null);
        if (destination == null) { return false; } Vec3 old = root.position();
        if (!RiftTeleport.move(this, root, destination, 52, true)) { return false; }
        root.getPersistentData().putLong("RiftTraversalUntil", level.getGameTime() + 40); RiftVfx.transfer(this, old, root.position(), RiftVfx.Transfer.CORRIDOR); return true;
    }
    private boolean corridorValid(ServerLevel level) {
        return portalA != null && portalB != null && level.getGameTime() < portalUntil && distanceToSqr(portalA) <= 80 * 80 && distanceToSqr(portalB) <= 80 * 80
                && level.hasChunkAt(BlockPos.containing(portalA)) && level.hasChunkAt(BlockPos.containing(portalB))
                && RiftTeleport.safe(this, getOwner() == null ? this : getOwner(), portalA, false) && RiftTeleport.safe(this, getOwner() == null ? this : getOwner(), portalB, false);
    }
    public boolean fracturedSpace(ServerLevel level) {
        List<Mob> enemies = hostiles(level, 20, 32);
        if (!controlsEnabled() || cooldowns[7] > 0 || ultimateTicks > 0 || enemies.size() < 3 || !(validTarget(getTarget()) || enemies.stream().anyMatch(this::acceptsAllyTarget))) { return false; }
        ultimateCenter = position(); ultimateTicks = 240; cooldowns[7] = 1800; displaced.clear(); action("ultimate", 32);
        RiftVfx.emit(this, RiftVfx.ULTIMATE, null, ultimateCenter, Vec3.ZERO, 240, 20);
        if (!isVehicle() && safeDiveStep(level, new Vec3(0, .2D, 0))) { setDeltaMovement(getDeltaMovement().add(0, .2D, 0)); needsSync = true; } return true;
    }
    private void tickUltimate(ServerLevel level) {
        if (ultimateTicks <= 0 || ultimateCenter == null) { return; }
        if (position().distanceToSqr(ultimateCenter) > 48 * 48) { ultimateTicks = 0; return; }
        ultimateTicks--;
        if (ultimateTicks > 0 && ultimateTicks % 40 == 0 && displaced.size() < 16) {
            int count = 0; for (Mob target : hostilesAt(level, ultimateCenter, 20, 32)) { if (!displaced.contains(target.getUUID()) && displaced.size() < 16 && displace(level, target, false)) { displaced.add(target.getUUID()); if (++count >= 4) { break; } } }
        }
        if (ultimateTicks == 0) { for (Mob enemy : hostilesAt(level, ultimateCenter, 20, 32)) { damage(level, enemy, 6, damageSources().mobAttack(this)); } RiftVfx.emit(this, RiftVfx.COLLAPSE, null, ultimateCenter, Vec3.ZERO, 20, 20); ultimateCenter = null; displaced.clear(); }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!controlsEnabled() || !validTarget(target)) { clearCombatTarget(false); return true; }
        if (!target.getUUID().equals(pursuit)) { pursuit = target.getUUID(); pursuitTicks = 0; } if (++pursuitTicks > 600) { clearCombatTarget(true); return true; }
        if (actionTicks > 0 || diveTicks > 0) { return true; }
        if (fracturedSpace(level)) { return true; }
        double distance = distanceToSqr(target);
        if (distance < 36 && throwEnemy(level, target)) { return true; }
        if (target instanceof Mob mob && distance < 24 * 24 && lock(level, mob)) { return true; }
        if (distance < 16 && step(level, position().add(position().subtract(target.position()).normalize().scale(6)))) { return true; }
        if (distance < 9 && doHurtTarget(level, target)) { return true; }
        if (distance <= 39 * 39 && line(level, getEyePosition(), target.getEyePosition()) && riftBolt(level, target.getBoundingBox().getCenter().subtract(getEyePosition()))) { return true; }
        if (distance < 36 && dash(level, target)) { return true; }
        if (distance > 324 && getControllingPassenger() == null) { getMoveControl().setWantedPosition(target.getX(), target.getY() + 3, target.getZ(), .8D); } return true;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; } RiftControl.maintain(level);
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (actionTicks > 0) { actionTicks--; } if (meleeCooldown > 0) { meleeCooldown--; }
        if (!controlsEnabled()) { if (controlsActive || portalA != null || anchor != null || ultimateTicks > 0) { closeSpatial(); } return; } controlsActive = true;
        tickDive(level); tickUltimate(level);
        if (anchor != null && !validAnchor(level)) { anchor = null; anchorPosition = null; RiftVfx.emit(this, RiftVfx.CLOSE_ANCHOR, null, position(), Vec3.ZERO, 1, 1); }
        if (portalA != null && !corridorValid(level)) { portalA = null; portalB = null; RiftVfx.emit(this, RiftVfx.CLOSE_PORTALS, null, position(), Vec3.ZERO, 1, 1); }
        if (pendingA != null && level.getGameTime() >= pendingUntil) { pendingA = null; }
        if (tickCount % 10 == 0) {
            sense(level);
            List<LivingEntity> allies = allies(level).stream().sorted(Comparator.comparingInt(ally -> ally == getOwner() ? 0 : 1)).toList();
            for (LivingEntity ally : allies) { if (danger(level, ally)) { if (tickCount % 40 == 0) { RiftVfx.emit(this, RiftVfx.SENSE, ally, ally.position(), Vec3.ZERO, 14, .7F); } if (rescue(level, ally, false)) { break; } } }
            if (getOwner() != null && anchor == null && cooldowns[3] == 0 && !hostiles(level, 32, 1).isEmpty()) { RiftTeleport.find(this, getOwner(), getOwner().position().add(-6, 0, 0), 12, candidate -> threats(level, candidate) == 0).ifPresent(candidate -> setAnchor(level, candidate)); }
            if (getControllingPassenger() != null && validTarget(getTarget())) { fracturedSpace(level); }
        }
        if (tickCount % 600 == 0 && getControllingPassenger() == null && !isTame() && getTarget() == null && random.nextInt(4) == 0) { step(level, position().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4)); }
    }
    private void action(String clip, int ticks) { if (isAddedToLevel()) { triggerAnim("rift_action", clip); } actionTicks = ticks; }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<RiftPhantom>("rift_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.rift." + clip)); } registrar.add(controller.additiveAnimations());
    }
    public void closeSpatial() { if (session != null) { RiftVfx.cancel(this); } RiftControl.release(this); session = UUID.randomUUID(); anchor = null; anchorPosition = null; portalA = null; portalB = null; pendingA = null; ultimateTicks = 0; ultimateCenter = null; displaced.clear(); diveTicks = 0; diveTarget = null; controlsActive = false; }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); pursuit = null; if (suppress && cooldowns != null) { closeSpatial(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if ((order == Order.STAY || order == Order.RETURN_HOME) && cooldowns != null) { closeSpatial(); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE && cooldowns != null) { closeSpatial(); } }
    @Override public void die(DamageSource source) { closeSpatial(); super.die(source); }
    @Override public void remove(RemovalReason reason) { closeSpatial(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("RiftTamingAttempts", tamingAttempts); output.putInt("RiftMeleeCooldown", meleeCooldown); for (int index = 0; index < cooldowns.length; index++) { output.putInt("RiftCooldown" + index, cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); closeSpatial(); tamingAttempts = Math.clamp(input.getIntOr("RiftTamingAttempts", 0), 0, 1); meleeCooldown = Math.clamp(input.getIntOr("RiftMeleeCooldown", 0), 0, 36); int[] maxima = {240, 560, 320, 600, 200, 400, 900, 1800, 240}; for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("RiftCooldown" + index, 0), 0, maxima[index]); } }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { return List.of(CooldownDiscipline.Slot.normal("rift_dash", 240, () -> cooldowns[8], value -> cooldowns[8] = value), CooldownDiscipline.Slot.normal("rift_throw", 320, () -> cooldowns[2], value -> cooldowns[2] = value)); }
}
