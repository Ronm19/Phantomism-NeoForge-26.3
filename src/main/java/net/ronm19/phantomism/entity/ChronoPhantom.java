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
import net.ronm19.phantomism.vfx.CinematicVfx;

public final class ChronoPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/chrono_phantom");
    private static final String[] CLIPS = {"bolt", "accelerate", "shift", "rewind", "anchor", "slow", "stop", "dive"};
    private final int[] cooldowns = new int[8];
    private UUID session = UUID.randomUUID();
    private UUID anchored;
    private int anchorTicks;
    private int stopWindup;
    private int shiftTicks;
    private int actionTicks;
    private int pursuitTicks;
    private UUID pursuit;
    private UUID diveTarget;
    private int diveTicks;
    private int meleeCooldown;
    private Vec3 slowCenter;
    private Vec3 stopCenter;
    private int slowTicks;
    private int ultimateTicks;
    private boolean controlsActive;
    public ChronoPhantom(EntityType<? extends ChronoPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.ARMOR, 8).add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, .35D).add(Attributes.MOVEMENT_SPEED, .24D).add(Attributes.FLYING_SPEED, .4D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<ChronoPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.OVERWORLD && level.getBiome(position).is(ModTags.Biomes.CHRONO_PHANTOM_SPAWNS)
                && position.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ())
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.CHRONO_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return this.ultimateTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override public void travel(Vec3 input) { if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return this.session; }
    public boolean combatEnabled() { return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME
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
        var stack = player.getItemInHand(hand); if (!this.isTame() && stack.is(Items.CLOCK)) {
            if (!this.level().isClientSide()) { this.tame(player); this.clearCombatTarget(true); if (!player.isCreative()) { stack.shrink(1); } this.level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS;
        } return super.mobInteract(player, hand);
    }
    public float damage(ServerLevel level, LivingEntity target, float amount, DamageSource source) {
        if (!this.combatEnabled() || !this.validTarget(target) || !this.getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean hit = target.hurtServer(level, source, amount);
        return hit ? Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity enemy) || !this.combatEnabled() || !this.validTarget(enemy) || this.meleeCooldown > 0
                || !this.getBoundingBox().inflate(.6D).intersects(enemy.getBoundingBox()) || !this.line(level, this.getEyePosition(), enemy.getEyePosition())) { return false; }
        if (!super.doHurtTarget(level, target)) { return false; } this.meleeCooldown = 40; return true;
    }
    public boolean timeBolt(ServerLevel level, Vec3 aim) {
        int interval = this.getControllingPassenger() instanceof Player ? 6 : 240;
        if (this.level() != level || !this.combatEnabled() || this.cooldowns[0] > 240 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = this.getEyePosition().add(direction.scale(.7D)); if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        TimeBolt shot = PhantomismContent.TIME_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(this.session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; } this.cooldowns[0] = 240; this.action("bolt", 6);
        CinematicVfx.chrono(this, CinematicVfx.BOLT, shot, muzzle, direction, 36, .45F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.timeBolt(level, rider.getViewVector(1)); }
    public boolean accelerate(ServerLevel level) {
        if (!this.combatEnabled() || this.cooldowns[1] > 0 || this.hostiles(level, 32, 1).isEmpty()) { return false; }
        boolean applied = false; for (LivingEntity ally : this.allies(level)) { if (TemporalControl.accelerate(this, ally)) { applied = true; CinematicVfx.chrono(this, CinematicVfx.ACCELERATE, ally, ally.position(), Vec3.ZERO, 120, 1.2F); } }
        if (applied) { this.cooldowns[1] = 480; this.action("accelerate", 16); } return applied;
    }
    public boolean shiftFor(DamageSource damage, float amount) {
        if (!this.combatEnabled() || !(this.level() instanceof ServerLevel) || !(damage.getEntity() instanceof LivingEntity enemy) || !this.validTarget(enemy)) { return false; }
        if (this.shiftTicks > 0) { return true; } if (this.cooldowns[2] > 0 || amount < 4) { return false; }
        this.cooldowns[2] = 320; this.shiftTicks = 6; this.action("shift", 12);
        CinematicVfx.chrono(this, CinematicVfx.SHIFT, this, this.position(), this.getViewVector(1), 18, 1.5F); return true;
    }
    public boolean rewind(ServerLevel level, LivingEntity ally) {
        if (this.cooldowns[3] > 0 || !this.combatEnabled() || !this.acceptsAlly(ally) || !this.line(level, this.getEyePosition(), ally.getEyePosition())) { return false; }
        float recovered = TemporalHistory.recover(this, ally, Math.min(12, ally.getMaxHealth() * .3F)); if (recovered <= 0) { return false; }
        this.cooldowns[3] = 400; this.action("rewind", 20); CinematicVfx.chrono(this, CinematicVfx.REWIND, ally, ally.position(), Vec3.ZERO, 30, 1.6F); return true;
    }
    public boolean anchor(ServerLevel level, LivingEntity ally) {
        if (!this.combatEnabled() || this.cooldowns[4] > 0 || !this.acceptsAlly(ally) || this.distanceToSqr(ally) > 24 * 24 || this.hostiles(level, 32, 1).isEmpty()) { return false; }
        this.anchored = ally.getUUID(); this.anchorTicks = 160; this.cooldowns[4] = 600; TemporalHistory.watch(ally); this.action("anchor", 16);
        CinematicVfx.chrono(this, CinematicVfx.ANCHOR, ally, ally.position(), Vec3.ZERO, 160, 1); return true;
    }
    public boolean slowField(ServerLevel level) {
        if (!this.combatEnabled() || this.cooldowns[5] > 0) { return false; } List<Mob> enemies = this.hostiles(level, 8, 16); if (enemies.size() < 2) { return false; }
        int count = 0; for (Mob enemy : enemies) { if (TemporalControl.slow(this, enemy, 120)) { count++; } } if (count == 0) { return false; }
        this.cooldowns[5] = 480; this.slowCenter = this.position(); this.slowTicks = 120; this.action("slow", 20); CinematicVfx.chrono(this, CinematicVfx.SLOW, null, this.position(), Vec3.ZERO, 120, 8); return true;
    }
    public boolean timeStop(ServerLevel level) {
        if (!this.combatEnabled() || this.cooldowns[6] > 0 || this.stopWindup > 0 || !(this.validTarget(this.getTarget()) || this.hostiles(level, 24, 32).stream().anyMatch(this::acceptsAllyTarget))
                || this.hostiles(level, 24, 32).stream().filter(enemy -> !TemporalControl.stopped(enemy) && !GravityControl.movementImmune(enemy) && enemy.getHealth() >= 4).limit(3).count() < 3) { return false; }
        this.cooldowns[6] = 2400; this.stopWindup = 20; this.stopCenter = this.position(); this.ultimateTicks = 140; this.action("stop", 36); CinematicVfx.chrono(this, CinematicVfx.STOP, null, this.position(), Vec3.ZERO, 156, 24); return true;
    }
    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || this.cooldowns[7] > 0 || !this.validTarget(target) || this.distanceToSqr(target) > 6 * 6 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize();
        if (!this.isVehicle()) { Vec3 step = direction.scale(.65D); if (!this.safeDiveStep(level, step)) { return false; } this.setDeltaMovement(step); this.needsSync = true; }
        if (this.isVehicle() && !this.getBoundingBox().inflate(1.2D).intersects(target.getBoundingBox())) { return false; }
        this.diveTarget = target.getUUID(); this.diveTicks = 10; this.cooldowns[7] = 240; this.action("dive", 16);
        CinematicVfx.chrono(this, CinematicVfx.DIVE, this, this.position(), direction, 18, 1.3F); return true;
    }
    private void tickDive(ServerLevel level) {
        if (this.diveTicks <= 0) { return; } this.diveTicks--;
        if (!(level.getEntity(this.diveTarget) instanceof LivingEntity target) || !this.validTarget(target) || this.distanceToSqr(target) > 8 * 8 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { this.diveTicks = 0; return; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize();
        if (this.getBoundingBox().inflate(this.isVehicle() ? 1.2D : .65D).intersects(target.getBoundingBox())) {
            this.diveTicks = 0; if (this.damage(level, target, (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4D), this.damageSources().mobAttack(this)) > 0) { CinematicVfx.chrono(this, CinematicVfx.IMPACT, null, target.getBoundingBox().getCenter(), direction, 12, 1.2F); } return;
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
    public boolean activateRiddenDive(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider)) { return false; }
        Vec3 eye = rider.getEyePosition(), end = eye.add(rider.getViewVector(1).scale(6));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.6D).clip(eye, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && this.dive(level, target);
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || !this.validTarget(target)) { this.clearCombatTarget(false); return true; }
        if (!target.getUUID().equals(this.pursuit)) { this.pursuit = target.getUUID(); this.pursuitTicks = 0; } if (++this.pursuitTicks > 600) { this.clearCombatTarget(true); return true; }
        if (this.actionTicks > 0 || this.stopWindup > 0 || this.diveTicks > 0) { return true; }
        if (this.timeStop(level) || this.slowField(level)) { return true; }
        double distance = this.distanceToSqr(target);
        if (distance < 25 && this.dive(level, target)) { return true; }
        if (distance < 9 && this.doHurtTarget(level, target)) { return true; }
        if (distance <= 39 * 39 && this.line(level, this.getEyePosition(), target.getEyePosition()) && this.timeBolt(level, target.getBoundingBox().getCenter().subtract(this.getEyePosition()))) { return true; }
        if (distance > 144 && this.getControllingPassenger() == null) { this.getMoveControl().setWantedPosition(target.getX(), target.getY() + 3, target.getZ(), .8D); } return true;
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level)) { return; }
        TemporalControl.maintain(level);
        for (int index = 0; index < this.cooldowns.length; index++) { if (this.cooldowns[index] > 0) { this.cooldowns[index]--; } }
        if (this.actionTicks > 0) { this.actionTicks--; } if (this.shiftTicks > 0) { this.shiftTicks--; }
        if (this.meleeCooldown > 0) { this.meleeCooldown--; }
        if (!this.combatEnabled()) { if (this.controlsActive || this.stopWindup > 0 || this.anchorTicks > 0 || this.diveTicks > 0) { this.cancel(); } return; }
        this.controlsActive = true;
        this.tickDive(level);
        if (this.ultimateTicks > 0) { this.ultimateTicks--; }
        if (this.stopWindup > 0 && --this.stopWindup == 0 && this.stopCenter != null && this.position().distanceToSqr(this.stopCenter) <= 32 * 32) { for (Mob enemy : this.hostilesAt(level, this.stopCenter, 24, 32)) { if (TemporalControl.stop(this, enemy, this.stopCenter)) { CinematicVfx.chrono(this, CinematicVfx.FROZEN, enemy, enemy.position(), Vec3.ZERO, 120, 1); } } }
        if (this.slowTicks > 0 && --this.slowTicks > 0 && this.slowCenter != null && this.tickCount % 10 == 0 && this.position().distanceToSqr(this.slowCenter) <= 32 * 32) { for (Mob enemy : this.hostilesAt(level, this.slowCenter, 8, 16)) { TemporalControl.slow(this, enemy, this.slowTicks); } }
        if (this.anchorTicks > 0 && --this.anchorTicks == 0) { this.anchored = null; }
        if (this.tickCount % 10 == 0) {
            List<LivingEntity> allies = this.allies(level); for (LivingEntity ally : allies) { TemporalHistory.watch(ally); }
            LivingEntity endangered = allies.stream().filter(ally -> TemporalHistory.available(ally) >= (ally.getUUID().equals(this.anchored) ? 2 : 4))
                    .max(Comparator.comparingDouble(ally -> TemporalHistory.available(ally) + (ally == this.getOwner() ? 4 : 0) + (ally.getUUID().equals(this.anchored) ? 3 : 0))).orElse(null);
            if (endangered != null) { if (this.tickCount % 40 == 0) { CinematicVfx.chrono(this, CinematicVfx.AWARE, endangered, endangered.position(), Vec3.ZERO, 16, .6F); } this.rewind(level, endangered); }
            if (!this.hostiles(level, 32, 1).isEmpty()) { this.accelerate(level); if (this.getOwner() != null) { this.anchor(level, this.getOwner()); } }
            if (this.getControllingPassenger() != null && this.validTarget(this.getTarget())) { this.timeStop(level); this.slowField(level); }
        }
        if (this.tickCount % 40 == 0 && !this.hostiles(level, 32, 1).isEmpty()) { for (LivingEntity ally : this.allies(level)) { if (CooldownDiscipline.assist(this, ally) > 0) { CinematicVfx.chrono(this, CinematicVfx.DISCIPLINE, ally, ally.position(), Vec3.ZERO, 20, .8F); } } }
    }
    private void action(String clip, int ticks) { if (this.isAddedToLevel()) { this.triggerAnim("chrono_action", clip); } this.actionTicks = ticks; }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<ChronoPhantom>("chrono_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.chrono." + clip)); } registrar.add(controller.additiveAnimations());
    }
    private void cancel() { if (this.session != null) { CinematicVfx.cancel(this); } TemporalControl.release(this); this.session = UUID.randomUUID(); this.anchored = null; this.anchorTicks = 0; this.stopWindup = 0; this.shiftTicks = 0; this.diveTicks = 0; this.diveTarget = null; this.slowTicks = 0; this.ultimateTicks = 0; this.slowCenter = null; this.stopCenter = null; this.controlsActive = false; }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); this.pursuit = null; if (suppress) { this.cancel(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.cancel(); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.cancel(); } }
    @Override public void die(DamageSource source) { this.cancel(); super.die(source); }
    @Override public void remove(RemovalReason reason) { this.cancel(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("ChronoMeleeCooldown", this.meleeCooldown); for (int index = 0; index < this.cooldowns.length; index++) { output.putInt("ChronoCooldown" + index, this.cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.cancel(); this.meleeCooldown = Math.clamp(input.getIntOr("ChronoMeleeCooldown", 0), 0, 40); int[] maxima = {240, 480, 320, 400, 600, 480, 2400, 240}; for (int index = 0; index < this.cooldowns.length; index++) { this.cooldowns[index] = Math.clamp(input.getIntOr("ChronoCooldown" + index, 0), 0, maxima[index]); } }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { return List.of(CooldownDiscipline.Slot.normal("chrono_accelerate", 480, () -> this.cooldowns[1], value -> this.cooldowns[1] = value), CooldownDiscipline.Slot.normal("chrono_slow", 480, () -> this.cooldowns[5], value -> this.cooldowns[5] = value), CooldownDiscipline.Slot.normal("chrono_dive", 240, () -> this.cooldowns[7], value -> this.cooldowns[7] = value)); }
}
