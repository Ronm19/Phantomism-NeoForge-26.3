package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.DemonVfx;

public final class DemonPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/demon_phantom");
    private static final Identifier FORM = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "demon_form");
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(DemonPhantom.class, EntityDataSerializers.BYTE);
    private static final int[] INTERVALS = {100, 160, 240, 360, 320, 320, 480, 1200, 2400};
    private static final String[] CLIPS = {"bite", "breath", "dash", "wave", "meteor", "drain", "howl", "form", "apocalypse"};
    private final int[] cooldowns = new int[9];
    private UUID session = UUID.randomUUID(), dashTarget, warnedPlayer;
    private int meleeTicks, actionTicks, breathTicks, dashTicks, formTicks, apocalypseTicks, apocalypseAge, warningTicks;
    private Vec3 breathDirection = Vec3.ZERO, dashDirection = Vec3.ZERO, territory, apocalypseCenter;
    private UUID apocalypseCast;
    private final Map<UUID, Barrage> barrages = new HashMap<>();
    private static final class Barrage {
        private final long until; private final Vec3 center; private final boolean ultimate; private int launched;
        private final Map<UUID, Integer> hits = new HashMap<>(), assigned = new HashMap<>();
        private Barrage(long until, Vec3 center, boolean ultimate) { this.until = until; this.center = center; this.ultimate = ultimate; }
    }
    public DemonPhantom(EntityType<? extends DemonPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 5)
                .add(Attributes.KNOCKBACK_RESISTANCE, .7D).add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .4D).add(Attributes.FOLLOW_RANGE, 48);
    }
    public static boolean checkSpawnRules(EntityType<DemonPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension().equals(Level.NETHER) && level.getBiome(position).is(ModTags.Biomes.DEMON_PHANTOM_SPAWNS) && position.getY() < 117
                && position.getY() > level.getMinY() + 3 && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.DEMON_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 28; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public void travel(Vec3 input) { if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, (byte) 0); }
    public boolean inDemonForm() { return (entityData.get(STATE) & 1) != 0; }
    @Override public boolean isUltimateAnimationActive() { return (entityData.get(STATE) & 2) != 0 || super.isUltimateAnimationActive(); }
    public UUID session() { return session; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME && (getTask() == Task.NONE || getTask() == Task.HUNT)
                && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    private boolean provoked(LivingEntity enemy) {
        LivingEntity owner = getOwner();
        return getLastHurtByMob() == enemy && tickCount - getLastHurtByMobTimestamp() < 200 || owner != null
                && (owner.getLastHurtByMob() == enemy && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 || owner.getLastHurtMob() == enemy && owner.tickCount - owner.getLastHurtMobTimestamp() < 200)
                || enemy instanceof Mob mob && (mob.getTarget() == this || owner != null && mob.getTarget() == owner);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity enemy) {
        if (enemy == null) { return false; }
        if (enemy instanceof Player player) { return !player.isCreative() && !player.isSpectator() && (provoked(enemy) || !isTame() && distanceToSqr(enemy) <= 16 * 16 && (territory == null || enemy.position().distanceToSqr(territory) <= 32 * 32)); }
        if (enemy instanceof OwnableEntity owned && owned.getOwnerReference() != null) { return provoked(enemy) || PhantomDuels.pair(this, enemy); }
        return enemy instanceof Enemy && (!(enemy instanceof NeutralMob) && enemy.getType() != EntityTypes.PIGLIN || provoked(enemy));
    }
    public boolean validTarget(LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && !target.isInvulnerable() && target.level() == level() && target != getOwner()
                && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isAlliedTo(target) && !isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(this, target) && isSpeciesCombatTarget(target) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target != null && target != getOwner() && !isAlliedTo(target) && !(target instanceof AbstractPhantom) && !PhantomDuels.begin(this, target)) { return; }
        super.setCombatTarget(target);
    }
    public boolean line(Vec3 from, Vec3 to) { return Double.isFinite(from.lengthSqr()) && Double.isFinite(to.lengthSqr()) && isLoadedFlightBox(new AABB(from, to).inflate(.04D)) && level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), target -> validTarget(target) && target.position().distanceToSqr(center) <= radius * radius
                && getAssignments().permitsDefensivePursuit(target) && line(getEyePosition(), target.getEyePosition())).stream().sorted(Comparator.comparingDouble(this::priority)).limit(limit).toList();
    }
    private double priority(LivingEntity enemy) { return distanceToSqr(enemy) * .05D - Math.min(enemy.getHealth(), 100) - (enemy instanceof RangedAttackMob ? 20 : 0) - (enemy instanceof Mob mob && mob.getTarget() == getOwner() && getOwner() != null ? 100 : 0); }
    @Override protected double getAutomaticTargetPriority(LivingEntity enemy) { return priority(enemy); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity enemy, ServerLevel level) { return validTarget(enemy) && (!isTame() || getOrder() == Order.GUARD || provoked(enemy)); }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount, boolean burn) {
        if (!enabled() || !validTarget(target) || !getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        if (inDemonForm()) { amount *= 1.25F; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, source, amount)) { return 0; }
        float dealt = Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount()));
        if (dealt > 0 && burn && target.isAlive() && !target.fireImmune()) { target.igniteForSeconds(4); } return dealt;
    }
    private boolean contact(LivingEntity target) { return getBoundingBox().inflate(1).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition()); }
    public boolean bite(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || meleeTicks > 0 || !validTarget(target) || !contact(target)) { return false; }
        if (damage(level, target, damageSources().mobAttack(this), (float) (getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * 1.4D), true) <= 0) { return false; }
        cooldowns[0] = 100; meleeTicks = 28; action("bite", 12); DemonVfx.emit(this, DemonVfx.BITE, null, target.getBoundingBox().getCenter(), target.position().subtract(position()), 14, 1.7F); return true;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!enabled() || meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !validTarget(enemy) || !contact(enemy)) { return false; }
        boolean hit = super.doHurtTarget(level, target); if (hit) { meleeTicks = 28; } return hit;
    }
    public boolean breath(ServerLevel level, Vec3 aim) {
        int interval = getControllingPassenger() instanceof Player ? 80 : 160;
        if (!enabled() || breathTicks > 0 || cooldowns[1] > 160 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        breathDirection = aim.normalize(); breathTicks = 32; cooldowns[1] = 160; action("breath", 32);
        DemonVfx.emit(this, DemonVfx.BREATH, this, getEyePosition(), breathDirection.scale(18), 32, 2.8F); return true;
    }
    private void tickBreath(ServerLevel level) {
        if (breathTicks <= 0) { return; }
        if (getControllingPassenger() instanceof Player rider) { breathDirection = rider.getViewVector(1).normalize(); }
        if ((32 - breathTicks) % 8 == 0) {
            Vec3 origin = getEyePosition();
            double reach = 18;
            while (reach > 0 && !isLoadedFlightBox(new AABB(origin, origin.add(breathDirection.scale(reach))).inflate(.05D))) { reach -= 2; }
            if (reach <= 0) { breathTicks = 0; return; }
            var impact = level.clip(new ClipContext(origin, origin.add(breathDirection.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)); reach = Math.min(reach, origin.distanceTo(impact.getLocation()));
            for (LivingEntity target : enemies(level, origin, reach, 24)) {
                Vec3 aim = target.getBoundingBox().getCenter().subtract(origin); double distance = aim.length();
                if (distance > .1D && aim.dot(breathDirection) > 0 && aim.normalize().dot(breathDirection) >= .98D && line(origin, target.getBoundingBox().getCenter())) {
                    damage(level, target, damageSources().indirectMagic(this, this), 3, true);
                }
            }
        }
        breathTicks--;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 80; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return breath(level, rider.getViewVector(1)); }
    public boolean dash(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || dashTicks > 0 || !validTarget(target) || distanceToSqr(target) > 12 * 12 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
        if (!safeStep(direction.scale(.9D))) { return false; }
        dashTarget = target.getUUID(); dashDirection = direction; dashTicks = 10; cooldowns[2] = 240; action("dash", 12);
        if (getControllingPassenger() instanceof Player rider) { Vec3 impulse = rider.getViewVector(1).scale(.6D); if (safeStep(getDeltaMovement().add(impulse))) { setDeltaMovement(getDeltaMovement().add(impulse)); needsSync = true; } }
        DemonVfx.emit(this, DemonVfx.DASH, this, position(), direction, 14, 1.5F); return true;
    }
    private void tickDash(ServerLevel level) {
        if (dashTicks <= 0) { return; } dashTicks--;
        if (!(level.getEntity(dashTarget) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { dashTicks = 0; return; }
        if (contact(target)) { dashTicks = 0; if (damage(level, target, damageSources().mobAttack(this), (float) (getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * 1.3D), false) > 0) { DemonVfx.emit(this, DemonVfx.BITE, null, target.getBoundingBox().getCenter(), dashDirection, 16, 2); } return; }
        if (isVehicle()) { return; }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.9D);
        if (!safeStep(step)) { dashTicks = 0; return; } setDeltaMovement(step); needsSync = true;
    }
    private boolean safeStep(Vec3 step) {
        for (Entity passenger : getSelfAndPassengers().skip(1).limit(9).toList()) {
            AABB clearance = passenger.getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(clearance) || !level().getWorldBorder().isWithinBounds(clearance) || !level().noBlockCollision(passenger, clearance) || level().containsAnyLiquid(clearance)) { return false; }
        }
        AABB box = getBoundingBox().expandTowards(step); if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noCollision(this, box) || level().containsAnyLiquid(box)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous((ServerLevel) level(), block)) { return false; } } return true;
    }
    private void push(LivingEntity target, double force) {
        if (!(target instanceof Mob mob) || GravityControl.movementImmune(mob) || target.isPassenger() || target.isVehicle()) { return; }
        Vec3 away = target.position().subtract(position()).multiply(1, 0, 1).normalize(); if (away.lengthSqr() < .01D) { return; }
        if (DemonControl.safeStep(this, mob, target.position().add(away.scale(force)))) { target.knockback(force, -away.x, -away.z, damageSources().mobAttack(this), 0); target.needsSync = true; }
    }
    public boolean wave(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0) { return false; } List<LivingEntity> targets = enemies(level, position(), 8, 12); if (targets.size() < 2) { return false; }
        boolean hit = false; for (LivingEntity target : targets) { if (damage(level, target, damageSources().mobAttack(this), 8, false) > 0) { push(target, .8D); hit = true; } }
        if (!hit) { return false; } cooldowns[3] = 360; action("wave", 18); DemonVfx.emit(this, DemonVfx.WAVE, null, position(), Vec3.ZERO, 24, 8); return true;
    }
    public boolean flameMeteor(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || barrages.size() >= 4) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 39, 24); if (targets.isEmpty()) { return false; }
        UUID cast = UUID.randomUUID(); Barrage barrage = new Barrage(level.getGameTime() + 160, position(), false); barrages.put(cast, barrage);
        for (int index = 0; index < 3; index++) { if (!launchMeteor(level, cast, targets)) { break; } }
        if (barrage.launched == 0) { barrages.remove(cast); return false; }
        cooldowns[4] = 320; action("meteor", 20); return true;
    }
    private boolean launchMeteor(ServerLevel level, UUID cast, List<LivingEntity> candidates) {
        Barrage barrage = barrages.get(cast); if (barrage == null || barrage.launched >= (barrage.ultimate ? 24 : 3)) { return false; }
        for (LivingEntity target : candidates.stream().filter(this::validTarget).filter(enemy -> barrage.hits.getOrDefault(enemy.getUUID(), 0) < (barrage.ultimate ? 3 : 2)
                        && barrage.assigned.getOrDefault(enemy.getUUID(), 0) < Math.min(barrage.ultimate ? 3 : 2, Math.max(1, (int) Math.ceil(enemy.getHealth() / 8))))
                .sorted(Comparator.comparingDouble(enemy -> barrage.assigned.getOrDefault(enemy.getUUID(), 0) * 200 + priority(enemy))).toList()) {
            for (int height = 9; height >= 3; height--) {
                Vec3 start = target.getBoundingBox().getCenter().add((barrage.launched % 3 - 1) * .6D, height, 0); AABB box = new AABB(start, start).inflate(.35D);
                if (!isLoadedFlightBox(box) || !level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box) || level.containsAnyLiquid(box) || !line(start, target.getBoundingBox().getCenter())) { continue; }
                DemonMeteor meteor = PhantomismContent.DEMON_METEOR.get().create(level, EntitySpawnReason.TRIGGERED); if (meteor == null) { return false; }
                meteor.setOwner(this); meteor.setPos(start); meteor.configure(session, cast, target.getBoundingBox().getCenter().subtract(start).normalize().scale(.85D), barrage.ultimate);
                if (!level.addFreshEntity(meteor)) { return false; } barrage.launched++; barrage.assigned.merge(target.getUUID(), 1, Integer::sum);
                DemonVfx.emit(this, DemonVfx.METEOR, meteor, start, new Vec3(0, -1, 0), 70, .75F); return true;
            }
        } return false;
    }
    public boolean validBarrage(UUID cast) { Barrage barrage = barrages.get(cast); return enabled() && barrage != null && level().getGameTime() < barrage.until; }
    public boolean meteorCanHit(UUID cast, LivingEntity target) {
        Barrage barrage = barrages.get(cast); return validBarrage(cast) && validTarget(target) && target.position().distanceToSqr(barrage.center) <= (barrage.ultimate ? 25 * 25 : 45 * 45)
                && barrage.hits.getOrDefault(target.getUUID(), 0) < (barrage.ultimate ? 3 : 2);
    }
    public void meteorImpact(ServerLevel level, DemonMeteor meteor, UUID cast, Vec3 point, LivingEntity direct) {
        Barrage barrage = barrages.get(cast); if (barrage == null || !validBarrage(cast)) { return; }
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(2.5D), target -> meteorCanHit(cast, target) && target.getBoundingBox().distanceToSqr(point) <= 6.25D).stream()
                .sorted(Comparator.comparingInt(target -> target == direct ? 0 : 1)).limit(8).toList();
        for (LivingEntity target : targets) {
            if (meteorCanHit(cast, target) && line(point.add(0, .1D, 0), target.getEyePosition()) && damage(level, target, damageSources().mobProjectile(meteor, this), target == direct ? 10 : 6, true) > 0) { barrage.hits.merge(target.getUUID(), 1, Integer::sum); }
        }
        DemonVfx.emit(this, DemonVfx.IMPACT, null, point, Vec3.ZERO, 20, 2.5F);
    }
    public boolean drain(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[5] > 0 || getHealth() >= getMaxHealth() || !validTarget(target) || distanceToSqr(target) > 16 * 16 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        float dealt = damage(level, target, damageSources().indirectMagic(this, this), 4, false); if (dealt <= 0) { return false; }
        heal(Math.min(3, dealt * .75F)); cooldowns[5] = 320; action("drain", 14); DemonVfx.emit(this, DemonVfx.DRAIN, this, getEyePosition(), target.getBoundingBox().getCenter().subtract(getEyePosition()), 16, 1); return true;
    }
    public boolean howl(ServerLevel level) {
        if (!enabled() || cooldowns[6] > 0) { return false; } boolean applied = false;
        for (LivingEntity target : enemies(level, position(), 12, 16)) { if (target instanceof Mob mob && DemonControl.apply(this, mob, true)) { push(target, .4D); applied = true; } }
        if (!applied) { return false; } cooldowns[6] = 480; action("howl", 18); DemonVfx.emit(this, DemonVfx.HOWL, null, position(), Vec3.ZERO, 28, 12); return true;
    }
    public boolean demonForm(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || formTicks > 0 || enemies(level, position(), 20, 1).isEmpty()
                || getHealth() > getMaxHealth() * .5F && !(getOwner() != null && getOwner().getHealth() <= getOwner().getMaxHealth() * .35F)) { return false; }
        formTicks = 200; cooldowns[7] = 1200; entityData.set(STATE, (byte) (entityData.get(STATE) | 1)); modifiers(true); action("form", 24); DemonVfx.emit(this, DemonVfx.FORM, this, position(), Vec3.ZERO, 200, 3); return true;
    }
    private void modifiers(boolean active) {
        for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.FLYING_SPEED)) {
            var instance = getAttribute(attribute); if (instance == null) { continue; }
            if (active) { instance.addOrUpdateTransientModifier(new AttributeModifier(FORM, attribute == Attributes.ATTACK_DAMAGE ? .25D : .15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } else { instance.removeModifier(FORM); }
        }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return super.hurtServer(level, source, inDemonForm() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE) ? amount * .8F : amount); }
    public boolean apocalypse(ServerLevel level) {
        if (!enabled() || cooldowns[8] > 0 || apocalypseTicks > 0 || barrages.size() >= 4) { return false; }
        List<LivingEntity> targets = enemies(level, position(), 25, 32);
        if (targets.size() < 5 || !validTarget(getTarget()) && targets.stream().noneMatch(this::provoked) || targets.stream().noneMatch(target -> hasMeteorHeadroom(level, target))) { return false; }
        apocalypseCenter = position(); apocalypseTicks = 300; apocalypseAge = 0; apocalypseCast = UUID.randomUUID();
        barrages.put(apocalypseCast, new Barrage(level.getGameTime() + 380, apocalypseCenter, true)); cooldowns[8] = 2400; action("apocalypse", 30);
        entityData.set(STATE, (byte) (entityData.get(STATE) | 2));
        DemonVfx.emit(this, DemonVfx.APOCALYPSE, null, apocalypseCenter, Vec3.ZERO, 300, 25); return true;
    }
    private boolean hasMeteorHeadroom(ServerLevel level, LivingEntity target) {
        for (int height = 9; height >= 3; height--) {
            Vec3 point = target.getBoundingBox().getCenter().add(0, height, 0); AABB box = new AABB(point, point).inflate(.35D);
            if (isLoadedFlightBox(box) && level.getWorldBorder().isWithinBounds(box) && level.noCollision(box) && !level.containsAnyLiquid(box) && line(point, target.getBoundingBox().getCenter())) { return true; }
        } return false;
    }
    private void tickApocalypse(ServerLevel level) {
        if (apocalypseTicks <= 0) { return; }
        apocalypseAge++; apocalypseTicks--;
        if (apocalypseAge >= 30 && apocalypseAge <= 260 && (apocalypseAge - 30) % 10 == 0) { launchMeteor(level, apocalypseCast, enemies(level, apocalypseCenter, 25, 32)); }
        if (apocalypseTicks == 0) { DemonVfx.emit(this, DemonVfx.FINALE, null, apocalypseCenter, Vec3.ZERO, 24, 25); apocalypseCenter = null; apocalypseCast = null; }
    }
    public boolean activateRiddenStrike(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        Vec3 start = rider.getEyePosition(), end = start.add(rider.getViewVector(1).scale(12));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(.7D).clip(start, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && (contact(target) ? bite(level, target) : dash(level, target));
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { clearCombatTarget(false); return true; }
        if (breathTicks > 0 || dashTicks > 0 || actionTicks > 0) { return true; }
        if (apocalypse(level) || demonForm(level)) { return true; }
        if (getHealth() < getMaxHealth() * .7F && drain(level, target)) { return true; }
        if (distanceToSqr(target) < 16 && howl(level) || wave(level)) { return true; }
        if (contact(target) && bite(level, target)) { return true; }
        if (distanceToSqr(target) < 18 * 18 && breath(level, target.getBoundingBox().getCenter().subtract(getEyePosition()))) { return true; }
        if (flameMeteor(level)) { return true; }
        if (distanceToSqr(target) <= 144 && dash(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; }
        if (formTicks > 0 && --formTicks == 0) { modifiers(false); }
        if (!enabled()) { if (breathTicks > 0 || dashTicks > 0 || formTicks > 0 || apocalypseTicks > 0 || !barrages.isEmpty()) { endCombat(); } return; }
        if (territory == null) { territory = position(); }
        if (!isTame() && tickCount % 10 == 0 && getTarget() == null) {
            Player intruder = level.players().stream().filter(player -> validTarget(player) && distanceToSqr(player) < 14 * 14).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (intruder == null) { warnedPlayer = null; warningTicks = 0; }
            else if (!intruder.getUUID().equals(warnedPlayer)) { warnedPlayer = intruder.getUUID(); warningTicks = 0; DemonVfx.emit(this, DemonVfx.HOWL, this, position(), Vec3.ZERO, 24, 4); }
            else if ((warningTicks += 10) >= 30) { setTarget(intruder); }
        }
        tickBreath(level); tickDash(level); tickApocalypse(level); barrages.entrySet().removeIf(entry -> level.getGameTime() >= entry.getValue().until);
        if (tickCount % 20 == 0 && validTarget(getTarget())) {
            for (LivingEntity enemy : enemies(level, position(), 8, 16)) { if (enemy instanceof Mob mob) { DemonControl.apply(this, mob, false); } }
            DemonVfx.emit(this, DemonVfx.AURA, this, position(), Vec3.ZERO, 22, 3);
        }
        if (getControllingPassenger() != null && tickCount % 10 == 0 && validTarget(getTarget())) { demonForm(level); apocalypse(level); }
        entityData.set(STATE, (byte) ((formTicks > 0 ? 1 : 0) | (apocalypseTicks > 0 ? 2 : 0)));
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isAlive() && !isTame() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS;
        } return super.mobInteract(player, hand);
    }
    private void action(String clip, int ticks) { if (isAddedToLevel()) { triggerAnim("demon_action", clip); } actionTicks = ticks; }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<DemonPhantom>("demon_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.demon." + clip)); } registrar.add(controller.additiveAnimations());
    }
    public void endCombat() {
        if (session != null && level() instanceof ServerLevel) { DemonVfx.cancel(this); } DemonControl.release(this); session = UUID.randomUUID(); breathTicks = 0; dashTicks = 0; dashTarget = null;
        formTicks = 0; apocalypseTicks = 0; apocalypseCenter = null; apocalypseCast = null; if (barrages != null) { barrages.clear(); } modifiers(false); entityData.set(STATE, (byte) 0);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endCombat(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endCombat(); } }
    @Override public void setTask(Task task) { super.setTask(task); if (cooldowns != null && task != Task.NONE && task != Task.HUNT) { endCombat(); } }
    @Override public void die(DamageSource source) { endCombat(); super.die(source); }
    @Override public void remove(RemovalReason reason) { endCombat(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("DemonMelee", meleeTicks); for (int index = 0; index < cooldowns.length; index++) { output.putInt("DemonCooldown" + index, cooldowns[index]); } if (territory != null) { output.store("DemonTerritory", Vec3.CODEC, territory); output.putString("DemonTerritoryDimension", level().dimension().toString()); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endCombat(); meleeTicks = Math.clamp(input.getIntOr("DemonMelee", 0), 0, 28); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("DemonCooldown" + index, 0), 0, INTERVALS[index]); } territory = input.getStringOr("DemonTerritoryDimension", "").equals(level().dimension().toString()) ? input.read("DemonTerritory", Vec3.CODEC).orElse(null) : null; }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>(); for (int index = 0; index < 8; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("demon_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(slots);
    }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.BLAZE_ROD, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Bite: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Breath: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Dash: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Wave: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Meteor: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Drain: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Howl: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Form: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]), "Apocalypse: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[8]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "drain" -> cooldowns[5];
            case "breath" -> cooldowns[1];
            case "dash" -> cooldowns[2];
            case "wave" -> cooldowns[3];
            case "flame_meteor" -> cooldowns[4];
            case "howl" -> cooldowns[6];
            case "demon_form" -> cooldowns[7];
            case "apocalypse" -> cooldowns[8];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[1] = ticks; }
}
