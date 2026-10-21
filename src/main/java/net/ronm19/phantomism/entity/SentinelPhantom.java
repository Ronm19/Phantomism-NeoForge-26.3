package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.SentinelVfx;

public final class SentinelPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/sentinel_phantom");
    private static final Identifier FURY = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "protective_fury");
    private static final String[] CLIPS = {"intercept", "repulse", "cleave", "pulse", "guard", "bastion"};
    private final int[] cooldowns = new int[7];
    private UUID protectedId, vengeanceId, session = UUID.randomUUID();
    private long markUntil, furyUntil, lastImpact = Long.MIN_VALUE;
    private int guardTicks, bastionTicks, meleeTicks, actionTicks;
    private Vec3 bastionCenter;
    public SentinelPhantom(EntityType<? extends SentinelPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 112).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 20)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, .85D).add(Attributes.MOVEMENT_SPEED, .24D)
                .add(Attributes.FLYING_SPEED, .4D).add(Attributes.FOLLOW_RANGE, 56);
    }
    public static boolean checkSpawnRules(EntityType<SentinelPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension().equals(Level.OVERWORLD) && (level.getBiome(position).is(ModTags.Biomes.SENTINEL_PHANTOM_SPAWNS) || level.getLevel().isVillage(position))
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SENTINEL_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 30; }
    @Override public boolean isUltimateAnimationActive() { return bastionTicks > 0 || super.isUltimateAnimationActive(); }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public void travel(Vec3 input) { if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return session; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == Task.NONE || getTask() == Task.HUNT) && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    public boolean ally(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != level()) { return false; }
        return entity == this || isTame() && (entity == getOwner() || entity instanceof OwnableEntity owned && Objects.equals(owned.getOwnerReference(), getOwnerReference()))
                || !isTame() && villageFriend(entity);
    }
    public static boolean villageFriend(LivingEntity entity) { return entity.getType() == EntityTypes.VILLAGER || entity.getType() == EntityTypes.IRON_GOLEM; }
    public LivingEntity protectee() {
        if (!(level() instanceof ServerLevel level) || protectedId == null) { return null; }
        if (!(level.getEntity(protectedId) instanceof LivingEntity entity) || !ally(entity) || distanceToSqr(entity) > 56 * 56
                || isTame() && entity != getOwner()) { protectedId = null; return null; }
        return entity;
    }
    private void chooseProtectee(ServerLevel level) {
        if (isTame()) { LivingEntity owner = getOwner(); protectedId = owner != null && ally(owner) && distanceToSqr(owner) <= 56 * 56 ? owner.getUUID() : null; return; }
        LivingEntity current = protectee();
        if (current != null && !threats(level, current, 24, 1).isEmpty()) { return; }
        List<LivingEntity> friends = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32), SentinelPhantom::villageFriend).stream().filter(this::ally).limit(24).toList();
        LivingEntity best = null; double score = Double.MAX_VALUE;
        for (LivingEntity friend : friends) {
            double candidate = distanceToSqr(friend) - (!threats(level, friend, 16, 1).isEmpty() ? 2000 : 0);
            if (candidate < score) { best = friend; score = candidate; }
        }
        protectedId = best == null ? null : best.getUUID();
    }
    private boolean recentAttack(LivingEntity attacker, LivingEntity victim) {
        return victim != null && (attacker.getLastHurtMob() == victim && attacker.tickCount - attacker.getLastHurtMobTimestamp() < 100
                || victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100);
    }
    public boolean legitimate(LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target.isRemoved() || target.level() != level() || ally(target) || isAlliedTo(target)
                || target instanceof AbstractPhantom || villageFriend(target) || isTargetSuppressed(target) || PhantomDuels.excludesObserver(this, target)
                || target instanceof Mob mob && VampireInfluence.isHypnotized(mob)) { return false; }
        LivingEntity ward = protectee();
        boolean provoked = target.getUUID().equals(vengeanceId) && level().getGameTime() < markUntil || recentAttack(target, this) || recentAttack(target, ward)
                || target instanceof Mob mob && (mob.getTarget() == this || ward != null && mob.getTarget() == ward);
        if (target instanceof Player player) { return !player.isCreative() && !player.isSpectator() && provoked; }
        if (target instanceof OwnableEntity owned && owned.getOwnerReference() != null) { return provoked || PhantomDuels.pair(this, target); }
        return target instanceof Enemy && (!(target instanceof NeutralMob) || provoked) || provoked;
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) { return legitimate(target); }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target != null && target != getOwner() && !isAlliedTo(target) && !(target instanceof AbstractPhantom) && !PhantomDuels.begin(this, target)) { return; }
        super.setCombatTarget(target);
    }
    private boolean threatens(LivingEntity enemy, LivingEntity ward) {
        return legitimate(enemy) && ward != null && !PhantomDuels.pair(enemy, ward) && (recentAttack(enemy, ward)
                || enemy instanceof Mob mob && (mob.getTarget() == ward || mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)
                        && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == ward));
    }
    public boolean line(Vec3 start, Vec3 end) {
        return isLoadedFlightBox(new AABB(start, end).inflate(.02D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private List<LivingEntity> threats(ServerLevel level, LivingEntity ward, double radius, int cap) {
        List<LivingEntity> known = getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of());
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, ward.getBoundingBox().inflate(radius), target -> threatens(target, ward) && ward.distanceToSqr(target) <= radius * radius);
        return java.util.stream.Stream.concat(known.stream().limit(128), nearby.stream().limit(128)).distinct().filter(target -> threatens(target, ward) && ward.distanceToSqr(target) <= radius * radius)
                .sorted(Comparator.comparingDouble(target -> ward.distanceToSqr(target) - (target.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 0 : target.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2))).limit(cap).toList();
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && threatens(target, protectee()); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return distanceToSqr(target) - (target.getUUID().equals(vengeanceId) ? 1000 : 0) - (threatens(target, protectee()) ? 500 : 0); }
    public void notice(LivingEntity attacker, LivingEntity victim) {
        if (!enabled() || victim != protectee() || !acceptsAttack(attacker, victim)) { return; }
        if (!attacker.getUUID().equals(vengeanceId)) { SentinelVfx.emit(this, SentinelVfx.MARK, attacker, attacker.position(), Vec3.ZERO, 100, 1); }
        vengeanceId = attacker.getUUID(); markUntil = level().getGameTime() + 100; furyUntil = level().getGameTime() + 60;
        if (getAssignments().permitsDefensivePursuit(attacker)) { getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, attacker, 60); setTarget(attacker); }
    }
    public boolean acceptsAttack(LivingEntity attacker, LivingEntity victim) {
        return attacker != null && attacker.isAlive() && !attacker.isRemoved() && attacker.level() == level() && !ally(attacker) && !isAlliedTo(attacker)
                && !villageFriend(attacker) && !(attacker instanceof AbstractPhantom) && !isTargetSuppressed(attacker) && !PhantomDuels.pair(attacker, victim)
                && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator())) && !(attacker instanceof Mob mob && VampireInfluence.isHypnotized(mob));
    }
    public boolean canIntercept(LivingEntity ward) { return ward != null && enabled() && ward == protectee() && cooldowns[0] == 0 && getHealth() > getMaxHealth() * .2F && distanceToSqr(ward) <= 16 * 16; }
    public boolean canDefendAttack(Entity attack, LivingEntity ward) { return attack instanceof Projectile ? enabled() && ward != null && ward == protectee() && cooldowns[3] == 0 && distanceToSqr(ward) <= 16 * 16 : canIntercept(ward); }
    public void commitIntercept(LivingEntity ward) { cooldowns[0] = 80; action("intercept", 10); shieldImpact(ward); }
    public boolean intercept(ServerLevel level, LivingEntity attacker) {
        LivingEntity ward = protectee();
        if (!canIntercept(ward) || !threatens(attacker, ward) || isVehicle() || isPassenger() || ward.distanceToSqr(attacker) > 16 * 16) { return false; }
        Vec3 between = ward.position().add(attacker.position().subtract(ward.position()).normalize().scale(2));
        Vec3 step = between.subtract(position()); if (step.lengthSqr() > 64 || !safeMovement(step.normalize().scale(Math.min(.6D, step.length()))) || !SentinelDefense.claim(this, attacker, ward)) { return false; }
        getMoveControl().setWantedPosition(between.x, between.y, between.z, 1.3D); return true;
    }
    private boolean safeMovement(Vec3 velocity) {
        AABB swept = getBoundingBox().expandTowards(velocity);
        if (!isLoadedFlightBox(swept) || !level().getWorldBorder().isWithinBounds(swept) || !level().noCollision(this, swept) || level().containsAnyLiquid(swept)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) { if (GravityControl.dangerous((ServerLevel) level(), block)) { return false; } } return true;
    }
    public boolean repulse(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || meleeTicks > 0 || !legitimate(target) || !contact(target) || !getAssignments().permitsDefensivePursuit(target)) { return false; }
        if (!target.hurtServer(level, damageSources().mobAttack(this), (float) (getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * 1.8D))) { return false; }
        cooldowns[1] = 200; meleeTicks = 30; knock(target, 1.1D); action("repulse", 14); SentinelVfx.emit(this, SentinelVfx.REPULSE, null, target.getBoundingBox().getCenter(), target.position().subtract(position()), 16, 2); return true;
    }
    private boolean contact(LivingEntity target) { return getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition()); }
    private void knock(LivingEntity target, double strength) {
        if (target instanceof Mob mob && !GravityControl.movementImmune(mob) && !target.isPassenger() && !target.isVehicle()) {
            Vec3 away = target.position().subtract(protectee() == null ? position() : protectee().position()).multiply(1, 0, 1).normalize();
            if (away.lengthSqr() > .01D && safeKnock(mob, away.scale(strength))) { target.knockback(strength, -away.x, -away.z, damageSources().mobAttack(this), 0); target.needsSync = true; }
        }
    }
    private boolean safeKnock(Mob target, Vec3 velocity) {
        AABB box = target.getBoundingBox().expandTowards(velocity.scale(2));
        if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noBlockCollision(target, box) || level().containsAnyLiquid(box)) { return false; }
        BlockPos floor = BlockPos.containing(target.position().add(velocity.scale(2))).below();
        return level().getBlockState(floor).isSolid() && !GravityControl.dangerous((ServerLevel) level(), floor);
    }
    public boolean cleave(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || meleeTicks > 0) { return false; }
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3), target -> legitimate(target) && distanceToSqr(target) <= 9 && getAssignments().permitsDefensivePursuit(target)
                && line(getEyePosition(), target.getEyePosition()) && target.position().subtract(position()).normalize().dot(getViewVector(1)) >= -.15D).stream()
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(4).toList();
        if (targets.size() < 2) { return false; }
        boolean hit = false;
        for (LivingEntity target : targets) { if (legitimate(target)) { hit |= target.hurtServer(level, damageSources().mobAttack(this), (float) (getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * .8D)); } }
        if (!hit) { return false; } cooldowns[2] = 160; meleeTicks = 30; action("cleave", 16); SentinelVfx.emit(this, SentinelVfx.CLEAVE, this, position(), getViewVector(1), 16, 3); return true;
    }
    public boolean breakProjectile(ServerLevel level) {
        LivingEntity ward = protectee(); if (!enabled() || cooldowns[3] > 0 || ward == null || distanceToSqr(ward) > 16 * 16) { return false; }
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, ward.getBoundingBox().inflate(10), projectile -> !projectile.isRemoved() && !projectile.isInvulnerable() && SentinelDefense.breakable(projectile)).stream()
                .sorted(Comparator.comparingDouble(ward::distanceToSqr)).limit(16).toList()) {
            if (!(shot.getOwner() instanceof LivingEntity shooter) || !legitimate(shooter) || shooter instanceof Mob boss && GravityControl.movementImmune(boss)
                    || PhantomDuels.pair(shooter, ward) || shot.getDeltaMovement().lengthSqr() < .01D
                    || shot.position().distanceToSqr(ward.position()) > 100 || !ward.getBoundingBox().inflate(.6D).clip(shot.position(), shot.position().add(shot.getDeltaMovement().scale(5))).isPresent()
                    || !line(shot.position(), ward.getEyePosition()) || !line(getEyePosition(), shot.position()) || distanceToSqr(shot) > 8 * 8 || !SentinelDefense.claim(this, shot, ward)) { continue; }
            Vec3 point = shot.position(); shot.discard(); cooldowns[3] = 40; notice(shooter, ward);
            SentinelVfx.emit(this, SentinelVfx.BREAK, null, point, Vec3.ZERO, 12, 1); return true;
        } return false;
    }
    public boolean pulse(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0) { return false; }
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12), target -> legitimate(target) && distanceToSqr(target) <= 144
                && getAssignments().permitsDefensivePursuit(target) && (threatens(target, protectee()) || target == getTarget()) && line(getEyePosition(), target.getEyePosition())).stream().limit(16).toList();
        if (targets.isEmpty()) { return false; }
        targets.forEach(target -> knock(target, .9D)); cooldowns[4] = 400; action("pulse", 16); SentinelVfx.emit(this, SentinelVfx.PULSE, null, position(), Vec3.ZERO, 22, 12); return true;
    }
    public boolean guard(ServerLevel level) {
        LivingEntity ward = protectee(); if (!enabled() || cooldowns[5] > 0 || guardTicks > 0 || ward == null || distanceToSqr(ward) > 16 * 16 || !line(getEyePosition(), ward.getEyePosition())) { return false; }
        guardTicks = 80; cooldowns[5] = 560; action("guard", 16); SentinelVfx.emit(this, SentinelVfx.GUARD, ward, ward.position(), Vec3.ZERO, 80, 1.8F); return true;
    }
    public boolean bastion(ServerLevel level) {
        LivingEntity ward = protectee(); if (!enabled() || cooldowns[6] > 0 || bastionTicks > 0 || ward == null || distanceToSqr(ward) > 20 * 20) { return false; }
        List<LivingEntity> threats = threats(level, ward, 20, 16);
        if (threats.size() < 3 && !(threats.size() >= 1 && ward.getHealth() <= ward.getMaxHealth() * .35F)) { return false; }
        bastionCenter = ward.position(); bastionTicks = 240; cooldowns[6] = 1800; action("bastion", 30);
        SentinelVfx.emit(this, SentinelVfx.BASTION, null, bastionCenter, Vec3.ZERO, 240, 20); return true;
    }
    public boolean covers(LivingEntity victim) { return ally(victim) && (victim == protectee() && distanceToSqr(victim) <= 16 * 16 || bastionTicks > 0 && bastionCenter != null && victim.position().distanceToSqr(bastionCenter) <= 400 && distanceToSqr(bastionCenter) <= 32 * 32); }
    public float mitigation(LivingEntity victim) {
        float reduction = guardTicks > 0 && victim == protectee() && distanceToSqr(victim) <= 16 * 16 ? .35F : 0;
        if (bastionTicks > 0 && bastionCenter != null && ally(victim) && victim.position().distanceToSqr(bastionCenter) <= 400 && distanceToSqr(bastionCenter) <= 32 * 32) { reduction = Math.max(reduction, victim == this ? .25F : .2F); } return reduction;
    }
    public void shieldImpact(LivingEntity victim) { if (level().getGameTime() - lastImpact >= 4 || lastImpact == Long.MIN_VALUE) { lastImpact = level().getGameTime(); SentinelVfx.emit(this, SentinelVfx.IMPACT, null, victim.getBoundingBox().getCenter(), Vec3.ZERO, 10, 1.3F); } }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!enabled() || meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !legitimate(enemy) || !contact(enemy)) { return false; }
        boolean hit = super.doHurtTarget(level, target); if (hit) { meleeTicks = 30; } return hit;
    }
    public boolean activateRiddenRepulse(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        Vec3 start = rider.getEyePosition(), end = start.add(rider.getViewVector(1).scale(5));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1), this::legitimate).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.6D).clip(start, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && repulse(level, target);
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 400; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return pulse(level); }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !legitimate(target)) { clearCombatTarget(false); return true; }
        if (actionTicks > 0) { return true; }
        if (contact(target) && threatens(target, protectee()) && repulse(level, target)) { return true; }
        if (cleave(level)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; } if (guardTicks > 0) { guardTicks--; }
        if (bastionTicks > 0 && --bastionTicks == 0) { bastionCenter = null; }
        if (!enabled()) { if (protectedId != null || bastionTicks > 0 || furyUntil > 0) { endDefense(); } return; }
        UUID previousProtectee = protectedId;
        if (tickCount % 10 == 0 || protectee() == null) { chooseProtectee(level); }
        if (previousProtectee != null && !previousProtectee.equals(protectedId)) { endDefense(); chooseProtectee(level); }
        LivingEntity ward = protectee();
        if (ward == null && (guardTicks > 0 || bastionTicks > 0)) { endDefense(); return; }
        LivingEntity marked = vengeanceId == null ? null : level.getEntity(vengeanceId) instanceof LivingEntity living ? living : null;
        if (level.getGameTime() >= markUntil || !legitimate(marked)) { vengeanceId = null; }
        if (tickCount % 5 == 0 && ward != null) {
            breakProjectile(level);
            List<LivingEntity> threats = threats(level, ward, 24, 16);
            if (!threats.isEmpty()) {
                LivingEntity threat = threats.getFirst(); notice(threat, ward);
                if (ward.getHealth() <= ward.getMaxHealth() * .5F && ward.distanceToSqr(threat) < 64) { guard(level); }
                if (!bastion(level) && ward.distanceToSqr(threat) < 36 && threats.size() >= 2) { pulse(level); }
                intercept(level, threat);
            }
        }
        LivingEntity currentAggressor = vengeanceId != null && level.getEntity(vengeanceId) instanceof LivingEntity living ? living : null;
        boolean defending = ward != null && currentAggressor != null && furyUntil > level.getGameTime() && threatens(currentAggressor, ward) && distanceToSqr(ward) <= 24 * 24;
        for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.FLYING_SPEED)) {
            var instance = getAttribute(attribute); if (defending) { instance.addOrUpdateTransientModifier(new AttributeModifier(FURY, attribute == Attributes.ATTACK_DAMAGE ? .2D : .1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } else { instance.removeModifier(FURY); }
        }
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(Items.SHIELD)) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS;
        } return super.mobInteract(player, hand);
    }
    private void action(String clip, int ticks) { if (isAddedToLevel()) { triggerAnim("sentinel_action", clip); } actionTicks = ticks; }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<SentinelPhantom>("sentinel_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.sentinel." + clip)); } registrar.add(controller.additiveAnimations());
    }
    public void endDefense() {
        if (session != null && level() instanceof ServerLevel) { SentinelVfx.cancel(this); } SentinelDefense.release(this);
        session = UUID.randomUUID(); protectedId = null; vengeanceId = null; furyUntil = 0; guardTicks = 0; bastionTicks = 0; bastionCenter = null;
        if (getBrain() != null) { setTarget(null); getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY); }
        for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.FLYING_SPEED)) { var instance = getAttribute(attribute); if (instance != null) { instance.removeModifier(FURY); } }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endDefense(); } }
    @Override public void setTask(Task task) { super.setTask(task); if (cooldowns != null && task != Task.NONE && task != Task.HUNT) { endDefense(); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endDefense(); } }
    @Override public void die(DamageSource source) { endDefense(); super.die(source); }
    @Override public void remove(RemovalReason reason) { endDefense(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("SentinelCooldown" + index, cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endDefense(); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("SentinelCooldown" + index, 0), 0, index == 6 ? 1800 : 560); } }
}
