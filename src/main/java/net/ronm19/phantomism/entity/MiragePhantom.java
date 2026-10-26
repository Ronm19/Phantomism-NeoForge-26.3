package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.MirageEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.MirageVfx;

public final class MiragePhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/mirage_phantom");
    public static final int HALL_DURATION = 240, HALL_RADIUS = 20, HALL_COOLDOWN = 1800;
    private static final EntityDataAccessor<Boolean> COMBAT = SynchedEntityData.defineId(MiragePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DASHING = SynchedEntityData.defineId(MiragePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HALL = SynchedEntityData.defineId(MiragePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS = {240, 320, 200, 240, 360, 600, 400, HALL_COOLDOWN};
    private static final String[] CLIPS = {"copies", "double", "bolt", "strike", "rescue", "field", "guard", "hall", "converge", "finale", "swap", "shatter"};
    private final int[] cooldowns = new int[8];
    private UUID session = UUID.randomUUID(), orderedId, strikeId, strikeRider, ownerContext;
    private int mountedBoltTicks, openingTicks, fieldTicks, hallTicks, strikeTicks, actionTicks, meleeTicks, combatTicks, tameAttempts;
    private Vec3 fieldCenter, hallCenter;
    private boolean visualOpen;

    public MiragePhantom(EntityType<? extends MiragePhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.ARMOR, 6)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .25D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .25D).add(Attributes.FLYING_SPEED, .5D);
    }
    public static boolean checkSpawnRules(EntityType<MiragePhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return MirageEncounterSpawner.habitat(accessor.getLevel(), position) && accessor.getFluidState(position).isEmpty()
                && accessor.getBlockState(position).getCollisionShape(accessor, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.MIRAGE_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected int getMeleeAttackCooldownTicks() { return 36; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected boolean prefersRangedCombat() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(COMBAT, false); data.define(DASHING, false); data.define(HALL, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(DASHING) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(DASHING) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(DASHING)) { setDeltaMovement(Vec3.ZERO); } else if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    @Override public boolean isUltimateAnimationActive() { return entityData.get(HALL) || super.isUltimateAnimationActive(); }
    public boolean combatEyes() { return entityData.get(COMBAT); }
    public UUID session() { return session; }
    public UUID budgetOwner() { return isTame() && getOwnerReference() != null ? getOwnerReference().getUUID() : getUUID(); }
    public int decoyLimit() { return hallTicks > 0 ? 6 : 3; }
    public int hallTicks() { return hallTicks; }
    public int fieldTicks() { return fieldTicks; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer() != null && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }
    public boolean ally(LivingEntity target) {
        return target != null && (target == this || target instanceof AbstractPhantom || target instanceof MirageDecoy copy && copy.budgetOwner().equals(budgetOwner())
                || target == getOwner() || isAlliedTo(target) || isTame() && target instanceof OwnableEntity owned && Objects.equals(getOwnerReference(), owned.getOwnerReference()));
    }
    public boolean protects(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && (target == this || isTame() && (target == getOwner()
                || target instanceof AbstractPhantom phantom && phantom.isTame() && (Objects.equals(getOwnerReference(), phantom.getOwnerReference())
                || getTeam() != null && getTeam().isAlliedTo(phantom.getTeam()))));
    }
    public boolean authorizedDisguise(AbstractPhantom target) { return target != null && (target == this || protects(target)) && distanceToSqr(target) <= 32 * 32 && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace().equals(Phantomism.MOD_ID); }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable() && !ally(target)
                && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean recent(LivingEntity attacker, LivingEntity victim) {
        return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100
                || attacker.getLastHurtMob() == victim && attacker.tickCount - attacker.getLastHurtMobTimestamp() < 100
                || attacker instanceof Mob mob && mob.getTarget() == victim);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean explicit = target.getUUID().equals(orderedId) || recent(target, this) || recent(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof OwnableEntity owned && owned.getOwnerReference() != null || target instanceof Player) { return explicit; }
        return explicit || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && (recent(target, this) || MirageIllusions.distracted(this, target) || target instanceof Mob mob && protects(mob.getTarget())); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (MirageIllusions.distracted(this, target) ? -200 : 0) + distanceToSqr(target); }
    @Override public void setCombatTarget(LivingEntity target) { if (safeEnemy(target) && PhantomDuels.begin(this, target)) { orderedId = target.getUUID(); super.setCombatTarget(target); } }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public boolean safePlacement(Vec3 point, double width, double height) {
        if (!Double.isFinite(point.lengthSqr()) || point.distanceToSqr(position()) > 48 * 48) { return false; }
        AABB box = new AABB(point.x - width / 2, point.y, point.z - width / 2, point.x + width / 2, point.y + height, point.z + width / 2).inflate(.04D);
        if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noBlockCollision(this, box) || level().containsAnyLiquid(box)) { return false; }
        if (!(level() instanceof ServerLevel level)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } }
        return true;
    }
    public boolean safeStep(Vec3 step) {
        AABB box = getBoundingBox().expandTowards(step).inflate(.03D);
        for (Entity passenger : getPassengers()) { box = box.minmax(passenger.getBoundingBox().expandTowards(step).inflate(.03D)); }
        return isLoadedFlightBox(box) && level().getWorldBorder().isWithinBounds(box) && level().noBlockCollision(this, box) && !level().containsAnyLiquid(box);
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int maximum) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, 1, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(maximum).toList();
    }
    private List<LivingEntity> threats(ServerLevel level) {
        return enemies(level, position(), 24, 24).stream().filter(target -> target == getTarget() || recent(target, this) || target instanceof Mob mob
                && (protects(mob.getTarget()) || mob.getTarget() instanceof MirageDecoy copy && copy.validFor(this))).toList();
    }
    public AbstractPhantom disguise(LivingEntity ward) {
        if (!(level() instanceof ServerLevel level)) { return this; }
        return level.getEntitiesOfClass(AbstractPhantom.class, getBoundingBox().inflate(24), this::authorizedDisguise).stream()
                .min(Comparator.comparingDouble(candidate -> (ward != null && ward.getHealth() < ward.getMaxHealth() * .4F ? -candidate.getScale() * 100
                        : candidate instanceof AngelPhantom ? -150 : candidate instanceof PhantomKing ? -120 : candidate instanceof GolemPhantom ? -100 : 0) + distanceToSqr(candidate) * .01D)).orElse(this);
    }
    private MirageDecoy deploy(AbstractPhantom appearance, MirageDecoy.Mode mode, Vec3 center, int duration, int index) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = (index * 2.4D + attempt * Math.PI / 4);
            Vec3 point = center.add(Math.cos(angle) * (1.8D + attempt % 2), .35D + attempt % 3 * .35D, Math.sin(angle) * (1.8D + attempt % 2));
            if (!line(center.add(0, .4D, 0), point.add(0, .4D, 0))) { continue; }
            MirageDecoy copy = MirageIllusions.create(this, appearance, mode, point, duration);
            if (copy != null) { MirageVfx.emit(this, MirageVfx.FORM, copy, point, Vec3.ZERO, 18, copy.visualScale()); return copy; }
        }
        return null;
    }
    public boolean copies(ServerLevel level, boolean physical) {
        if (!enabled() || cooldowns[0] > 0) { return false; }
        int made = 0;
        for (int index = 0; index < 3; index++) { if (deploy(this, physical ? MirageDecoy.Mode.PHYSICAL : MirageDecoy.Mode.VISUAL, position(), physical ? 120 : 100, index) != null) { made++; } }
        if (made == 0) { return false; } cooldowns[0] = INTERVALS[0]; action("copies", 22); return true;
    }
    public boolean phantomDouble(ServerLevel level, LivingEntity ward) {
        if (!enabled() || cooldowns[1] > 0 || MirageIllusions.personal(this).size() >= decoyLimit()) { return false; }
        MirageDecoy copy = deploy(disguise(ward), ward == null ? MirageDecoy.Mode.VISUAL : MirageDecoy.Mode.PHYSICAL, ward == null ? position() : ward.position(), 160, 2);
        if (copy == null) { return false; } cooldowns[1] = INTERVALS[1]; action("double", 24); return true;
    }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float base) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(base) || base <= 0) { return 0; }
        boolean opening = openingTicks == 0 && MirageIllusions.distracted(this, target);
        float amount = base * (opening ? 1.25F : 1);
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, source, amount)) { return 0; }
        if (opening) { openingTicks = 60; MirageVfx.emit(this, MirageVfx.OPENING, target, target.position(), Vec3.ZERO, 18, 1.5F); }
        return Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
    }
    public boolean bolt(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || (mounted ? mountedBoltTicks > 0 || !(getControllingPassenger() instanceof Player) : cooldowns[2] > 0)
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.6D)); if (!line(getEyePosition(), muzzle)) { return false; }
        MirageBolt shot = PhantomismContent.MIRAGE_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.8F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        if (mounted) { mountedBoltTicks = 6; } else { cooldowns[2] = INTERVALS[2]; }
        action("bolt", 10); MirageVfx.emit(this, MirageVfx.BOLT, shot, muzzle, direction, 30, .6F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return bolt(level, rider.getViewVector(1), true); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.5D).intersects(enemy.getBoundingBox())
                || !line(getEyePosition(), enemy.getEyePosition()) || damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0) { return false; }
        meleeTicks = 36; action("strike", 12); MirageVfx.emit(this, MirageVfx.STRIKE, null, target.position(), Vec3.ZERO, 16, 2); return true;
    }
    public boolean illusoryStrike(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[3] > 0 || strikeTicks > 0 || !validTarget(target) || distanceToSqr(target) > 12 * 12
                || !line(getEyePosition(), target.getEyePosition()) || !safeStep(target.position().subtract(position()).normalize().scale(.2D))) { return false; }
        deploy(this, MirageDecoy.Mode.VISUAL, position(), 35, 0);
        strikeId = target.getUUID(); strikeRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); strikeTicks = 18; cooldowns[3] = INTERVALS[3];
        entityData.set(DASHING, true); action("strike", 20); syncRider(false); MirageVfx.emit(this, MirageVfx.STRIKE, this, position(), target.position().subtract(position()).normalize(), 20, 2); return true;
    }
    public boolean activateRiddenStrike(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 12, 16).stream().filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).findFirst().orElse(null);
        return illusoryStrike(level, target);
    }
    private void tickStrike(ServerLevel level) {
        if (strikeTicks <= 0) { return; }
        if (--strikeTicks == 0 || strikeRider != null && (getControllingPassenger() == null || !strikeRider.equals(getControllingPassenger().getUUID()))
                || !(level.getEntity(strikeId) instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { endStrike(); return; }
        if (getBoundingBox().inflate(.6D).intersects(target.getBoundingBox())) {
            damage(level, target, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F); meleeTicks = 36;
            MirageVfx.emit(this, MirageVfx.SHATTER, null, target.position(), Vec3.ZERO, 18, 2); endStrike(); return;
        }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.65D);
        if (!safeStep(step)) { endStrike(); return; } move(MoverType.SELF, step); setDeltaMovement(Vec3.ZERO); syncRider(false);
    }
    private void syncRider(boolean finish) {
        if (!(level() instanceof ServerLevel level) || !(getControllingPassenger() instanceof ServerPlayer rider)) { return; }
        if (rider.connection == null) { return; }
        positionRider(rider); needsSync = true; rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(DASHING, entityData.get(DASHING)))));
        if (finish) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
    }
    private void endStrike() { boolean active = entityData.get(DASHING); strikeTicks = 0; strikeId = null; strikeRider = null; entityData.set(DASHING, false); if (active) { setDeltaMovement(Vec3.ZERO); syncRider(true); } }
    public boolean rescue(ServerLevel level, LivingEntity ward, LivingEntity attacker) {
        if (!enabled() || cooldowns[4] > 0 || !protects(ward) || !validTarget(attacker) || distanceToSqr(ward) > 20 * 20 || !line(attacker.getEyePosition(), ward.getEyePosition())) { return false; }
        MirageIllusions.trim(this, decoyLimit() - 1);
        Vec3 direction = attacker.getEyePosition().subtract(ward.getEyePosition()).normalize();
        Vec3 point = ward.getBoundingBox().getCenter().add(direction.scale(1.4D)).add(0, -.4D, 0);
        MirageDecoy copy = MirageIllusions.create(this, disguise(ward), MirageDecoy.Mode.PHYSICAL, point, 100);
        if (copy == null) { return false; }
        cooldowns[4] = INTERVALS[4]; if (attacker instanceof Mob mob) { MirageIllusions.redirect(this, mob, copy, true); }
        action("rescue", 18); MirageVfx.emit(this, MirageVfx.RESCUE, copy, point, direction, 24, 2); return true;
    }
    public boolean field(ServerLevel level) {
        if (!enabled() || cooldowns[5] > 0 || fieldTicks > 0) { return false; }
        fieldCenter = position(); fieldTicks = 160; cooldowns[5] = INTERVALS[5]; action("field", 24);
        MirageVfx.emit(this, MirageVfx.FIELD, null, fieldCenter, Vec3.ZERO, 160, 12); return true;
    }
    public boolean inField(Vec3 point) { return fieldTicks > 0 && fieldCenter != null && point.distanceToSqr(fieldCenter) <= 12 * 12 || hallTicks > 0 && hallCenter != null && point.distanceToSqr(hallCenter) <= HALL_RADIUS * HALL_RADIUS; }
    public float mirrorGuard(ServerLevel level, DamageSource damage, float amount) {
        if (!enabled() || cooldowns[6] > 0 || amount < 2 || !Float.isFinite(amount) || !(damage.getEntity() instanceof LivingEntity attacker) || !validTarget(attacker)
                || !(damage.getDirectEntity() instanceof LivingEntity) && !(damage.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) { return amount; }
        MirageDecoy copy = MirageIllusions.personal(this).stream().filter(candidate -> candidate.mode() == MirageDecoy.Mode.PHYSICAL && distanceToSqr(candidate) <= 8 * 8)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        cooldowns[6] = INTERVALS[6]; action("guard", 16);
        float absorbed = 0;
        if (copy != null) {
            Vec3 destination = copy.position(), origin = position();
            if (!isVehicle() && safePlacement(destination, getBbWidth(), getBbHeight()) && safeStep(destination.subtract(position())) && line(getEyePosition(), copy.getEyePosition())) {
                teleportTo(destination.x, destination.y, destination.z); copy.setPos(origin); action("swap", 16);
            }
            absorbed = copy.absorb(Math.min(6, amount)); copy.shatter();
        }
        if (absorbed == 0) { absorbed = Math.min(4, amount * .25F); }
        MirageVfx.emit(this, MirageVfx.GUARD, this, position(), Vec3.ZERO, 20, 2); return Math.max(0, amount - absorbed);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return super.hurtServer(level, source, mirrorGuard(level, source, amount)); }
    public boolean hall(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || hallTicks > 0) { return false; }
        hallCenter = position(); hallTicks = HALL_DURATION; entityData.set(HALL, true); cooldowns[7] = HALL_COOLDOWN; action("hall", 32);
        fillHall(); MirageVfx.emit(this, MirageVfx.HALL, null, hallCenter, Vec3.ZERO, HALL_DURATION, HALL_RADIUS); return true;
    }
    private void fillHall() {
        int existing = MirageIllusions.personal(this).size(); AbstractPhantom alternate = disguise(getOwner());
        for (int index = existing; index < 6; index++) { deploy(index % 2 == 0 ? this : alternate, index % 3 == 0 ? MirageDecoy.Mode.PHYSICAL : MirageDecoy.Mode.VISUAL, hallCenter, Math.max(10, hallTicks), index); }
    }
    private void confuse(ServerLevel level, List<LivingEntity> crowd, boolean force) {
        List<MirageDecoy> copies = MirageIllusions.personal(this);
        for (LivingEntity target : crowd.stream().limit(12).toList()) {
            if (!(target instanceof Mob mob)) { continue; }
            copies.stream().filter(copy -> copy.distanceToSqr(mob) <= 24 * 24).min(Comparator.comparingDouble(mob::distanceToSqr))
                    .ifPresent(copy -> MirageIllusions.redirect(this, mob, copy, force && inField(target.position())));
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (strikeTicks > 0) { return true; }
        if (MirageIllusions.distracted(this, target) && distanceToSqr(target) < 12 * 12 && illusoryStrike(level, target)) { return true; }
        return doHurtTarget(level, target);
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (mountedBoltTicks > 0) { mountedBoltTicks--; } if (openingTicks > 0) { openingTicks--; } if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; }
        if (combatTicks > 0) { combatTicks--; } entityData.set(COMBAT, combatTicks > 0);
        UUID currentOwner = budgetOwner(); if (ownerContext != null && !ownerContext.equals(currentOwner)) { endMirage(); } ownerContext = currentOwner;
        if (!enabled()) { endMirage(); return; }
        if (fieldTicks > 0 && (--fieldTicks == 0 || distanceToSqr(fieldCenter) > 32 * 32)) { fieldTicks = 0; fieldCenter = null; }
        if (hallTicks > 0) {
            if (hallCenter == null || distanceToSqr(hallCenter) > 40 * 40) { endMirage(); return; }
            int elapsed = HALL_DURATION - --hallTicks;
            if (elapsed == 40 || elapsed == 100) { fillHall(); }
            if (elapsed == 200) { action("converge", 24); MirageVfx.emit(this, MirageVfx.CONVERGE, null, hallCenter, Vec3.ZERO, 40, HALL_RADIUS); }
            if (hallTicks == 0) {
                for (LivingEntity enemy : enemies(level, hallCenter, HALL_RADIUS, 8)) { damage(level, enemy, damageSources().mobAttack(this), 10); }
                action("finale", 24); MirageVfx.emit(this, MirageVfx.FINALE, null, hallCenter, Vec3.ZERO, 30, HALL_RADIUS);
                MirageIllusions.trim(this, 3); entityData.set(HALL, false); hallCenter = null;
            }
        }
        tickStrike(level);
        if (tickCount % 10 == 0) {
            List<LivingEntity> crowd = threats(level);
            if (!crowd.isEmpty()) {
                if (!validTarget(getTarget())) { setTarget(crowd.getFirst()); }
                combatTicks = 80; entityData.set(COMBAT, true);
                boolean protectionNeeded = crowd.stream().anyMatch(enemy -> enemy instanceof Mob mob && protects(mob.getTarget())
                        && (mob.getTarget().getHealth() < mob.getTarget().getMaxHealth() * .5F || mob.distanceToSqr(mob.getTarget()) < 4 * 4));
                for (LivingEntity enemy : crowd) { if (enemy instanceof Mob mob && protects(mob.getTarget()) && mob.getTarget() != this
                        && (mob.getTarget().getHealth() < mob.getTarget().getMaxHealth() * .5F || mob.distanceToSqr(mob.getTarget()) < 6 * 6)) { rescue(level, mob.getTarget(), enemy); break; } }
                if (crowd.size() >= 4 && hallTicks == 0) { hall(level); }
                if (crowd.size() >= 3) { field(level); }
                if (MirageIllusions.personal(this).isEmpty()) { if (!phantomDouble(level, getHealth() < getMaxHealth() * .4F ? this : protectionNeeded ? getOwner() == null ? this : getOwner() : null)) { copies(level, protectionNeeded); } }
                if (crowd.size() >= 2) { copies(level, protectionNeeded); }
                confuse(level, crowd, hallTicks > 0 && hallTicks < 40);
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(12), shot -> MirageIllusions.eligibleProjectile(this, shot)).stream().limit(16).toList()) {
                for (LivingEntity ward : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12), this::protects).stream().limit(8).toList()) {
                    if (ward.getBoundingBox().inflate(.4D).clip(projectile.position(), projectile.position().add(projectile.getDeltaMovement().scale(8))).isPresent()
                            && projectile.getOwner() instanceof LivingEntity attacker) { rescue(level, ward, attacker); break; }
                }
            }
        }
        if (!isVehicle() && tickCount % 5 == 0 && validTarget(getTarget()) && distanceToSqr(getTarget()) <= 39 * 39 && line(getEyePosition(), getTarget().getEyePosition())) { bolt(level, getTarget().getEyePosition().subtract(getEyePosition()), false); }
    }
    private void action(String clip, int duration) { actionTicks = duration; visualOpen = true; if (!clip.equals("copies") && !clip.equals("double")) { combatTicks = 80; entityData.set(COMBAT, true); } triggerAnim("mirage", clip); playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .45F, 1.3F + getRandom().nextFloat() * .25F); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<MiragePhantom>("mirage", 3, state -> actionTicks > 0 ? PlayState.CONTINUE : PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.mirage." + clip)); } controllers.add(controller);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) {
            if (!level().isClientSide()) {
                if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); }
                if (++tameAttempts >= preferredTaming().guaranteedAttempt() || getRandom().nextFloat() < preferredTaming().chance()) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte) 7); }
                else { level().broadcastEntityEvent(this, (byte) 6); }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endMirage() {
        if (level() instanceof ServerLevel) {
            MirageIllusions.trim(this, 0);
            if (visualOpen) { MirageVfx.cancel(this); visualOpen = false; session = UUID.randomUUID(); }
        }
        endStrike(); fieldTicks = 0; hallTicks = 0; fieldCenter = null; hallCenter = null; orderedId = null; actionTicks = 0; entityData.set(HALL, false);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endMirage(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endMirage(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endMirage(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endMirage(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("MirageCooldown" + index, cooldowns[index]); }
        output.putInt("MirageMountedBolt", mountedBoltTicks); output.putInt("MirageOpening", openingTicks); output.putInt("MirageTameAttempts", tameAttempts);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endMirage(); session = UUID.randomUUID();
        for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("MirageCooldown" + index, 0), 0, INTERVALS[index]); }
        mountedBoltTicks = Math.clamp(input.getIntOr("MirageMountedBolt", 0), 0, 6); openingTicks = Math.clamp(input.getIntOr("MirageOpening", 0), 0, 60); tameAttempts = Math.clamp(input.getIntOr("MirageTameAttempts", 0), 0, 2);
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index = 0; index < 7; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("mirage_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.GLOW_INK_SAC, .75F, 2, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Copies: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Double: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Bolt: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Strike: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Rescue: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]), "Field: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[5]), "Guard: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[6]), "Hall: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[7]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "copies" -> cooldowns[0];
            case "phantom_double" -> cooldowns[1];
            case "bolt" -> cooldowns[2];
            case "illusory_strike" -> cooldowns[3];
            case "field" -> cooldowns[5];
            case "hall" -> cooldowns[7];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[2] = ticks; }
}
