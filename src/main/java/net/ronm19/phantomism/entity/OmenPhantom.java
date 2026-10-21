package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.OmenEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.OmenVfx;

public final class OmenPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/omen_phantom");
    private static final int[] INTERVALS = {200, 240, 360, 600, 500, 1800};
    private static final String[] CLIPS = {"bolt", "dive", "mark", "standard", "shield", "calamity", "judgment", "conclusion", "strike"};
    private final int[] cooldowns = new int[6];
    private UUID session = UUID.randomUUID(), markedId, orderedId, diveId, incomingId;
    private long incomingUntil;
    private Vec3 standardCenter, calamityCenter;
    private int markTicks, standardTicks, calamityTicks, shieldTicks, diveTicks, meleeTicks, actionTicks;
    private float shieldRemaining;
    private boolean sessionOpen;

    public OmenPhantom(EntityType<? extends OmenPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 76).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .4D).add(Attributes.MOVEMENT_SPEED, .25D)
                .add(Attributes.FLYING_SPEED, .425D).add(Attributes.FOLLOW_RANGE, 56);
    }
    public static boolean checkSpawnRules(EntityType<OmenPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension().equals(Level.OVERWORLD) && OmenEncounterSpawner.habitat(level.getLevel(), position)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.OMEN_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 34; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return calamityTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public void travel(Vec3 input) { if (getControllingPassenger() == null) { travelFlying(input, (float)(getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return session; }
    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level());
    }
    public static boolean raider(LivingEntity target) { return target != null && (target.is(EntityTypeTags.RAIDERS) || target.is(EntityTypeTags.ILLAGER)); }
    public boolean ally(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && (target == this || isAlliedTo(target)
                || isTame() && (target == getOwner() || target instanceof OwnableEntity owned && Objects.equals(getOwnerReference(), owned.getOwnerReference()))
                || !isTame() && (target.getType() == EntityTypes.VILLAGER || target.getType() == EntityTypes.IRON_GOLEM));
    }
    public boolean crew(AbstractPhantom target) { return isTame() && target.isTame() && ally(target) && target.getOwner() != null && target.getOwner().isAlive(); }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !ally(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(this, target) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    private boolean recent(LivingEntity attacker, LivingEntity victim) {
        return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100
                || attacker.getLastHurtMob() == victim && attacker.tickCount - attacker.getLastHurtMobTimestamp() < 100
                || attacker instanceof Mob mob && mob.getTarget() == victim);
    }
    private boolean provoked(LivingEntity target) { return recent(target, this) || recent(target, getOwner()) || level().getGameTime() <= incomingUntil && target.getUUID().equals(incomingId); }
    public void noticeIncoming(LivingEntity target) { if (safeEnemy(target)) { incomingId = target.getUUID(); incomingUntil = level().getGameTime() + 2; } }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (safeEnemy(target) && target instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return provoked(target) || target.getUUID().equals(orderedId) || PhantomDuels.pair(this, target);
        }
        return safeEnemy(target) && (raider(target) || provoked(target) || target.getUUID().equals(orderedId) || PhantomDuels.pair(this, target)
                || isTame() && target instanceof Enemy && !(target instanceof NeutralMob) && !(target instanceof OwnableEntity) && target.getType() != EntityTypes.PIGLIN);
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return validTarget(target) && (raider(target) || provoked(target)); }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return priority(target); }
    private double priority(LivingEntity target) {
        return (target.getUUID().equals(markedId) ? -10000 : 0) + (raider(target) ? -1000 : 0)
                + (target.getType() == EntityTypes.EVOKER ? -400 : target.getType() == EntityTypes.RAVAGER ? -300 : 0)
                - Math.min(200, target.getMaxHealth()) + distanceToSqr(target) * .01D;
    }
    @Override public void setCombatTarget(LivingEntity target) {
        if (!safeEnemy(target) || !PhantomDuels.begin(this, target)) { return; }
        orderedId = target.getUUID(); super.setCombatTarget(target);
    }
    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int maximum) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, 1, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::priority)).limit(maximum).toList();
    }
    public LivingEntity marked() {
        if (!(level() instanceof ServerLevel level) || markedId == null) { return null; }
        if (markTicks <= 0 || !(level.getEntity(markedId) instanceof LivingEntity target) || !validTarget(target) || distanceToSqr(target) > 56 * 56) { OmenVfx.emit(this, OmenVfx.CLEAR_MARK, null, position(), Vec3.ZERO, 1, 1); markedId = null; markTicks = 0; return null; }
        return target;
    }
    public boolean mark(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[2] > 0 || !validTarget(target) || distanceToSqr(target) > 56 * 56 || !line(getEyePosition(), target.getEyePosition())) { return false; }
        setMarked(target, 240); cooldowns[2] = 360; action("mark", 18); return true;
    }
    private void setMarked(LivingEntity target, int ticks) {
        OmenVfx.emit(this, OmenVfx.CLEAR_MARK, null, position(), Vec3.ZERO, 1, 1);
        markedId = target.getUUID(); markTicks = ticks;
        OmenVfx.emit(this, OmenVfx.MARK, target, target.position(), Vec3.ZERO, ticks, 1);
    }
    public boolean standard(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || standardTicks > 0 || enemies(level, position(), 28, 1).isEmpty()
                || !isTame() || level.getEntitiesOfClass(AbstractPhantom.class, getBoundingBox().inflate(20), target -> target != this && crew(target)).isEmpty()) { return false; }
        standardCenter = position(); standardTicks = 240; cooldowns[3] = 600; action("standard", 24);
        OmenVfx.emit(this, OmenVfx.STANDARD, null, standardCenter, Vec3.ZERO, 240, 20); return true;
    }
    public boolean empowers(AbstractPhantom ally, LivingEntity target) {
        Vec3 center = standardTicks > 0 ? standardCenter : calamityTicks > 0 ? calamityCenter : null;
        double radius = standardTicks > 0 ? 20 : 28;
        return enabled() && ally != this && crew(ally) && raider(target) && validTarget(target) && center != null
                && distanceToSqr(center) <= 56 * 56 && ally.distanceToSqr(center) <= radius * radius && target.distanceToSqr(center) <= radius * radius
                && ally.getOrder() != Order.STAY && ally.getOrder() != Order.RETURN_HOME && line(center.add(0, 1, 0), ally.getEyePosition());
    }
    public boolean shield(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || shieldTicks > 0) { return false; }
        shieldRemaining = 12; shieldTicks = 60; cooldowns[4] = 500; action("shield", 16);
        OmenVfx.emit(this, OmenVfx.SHIELD, this, position(), Vec3.ZERO, 60, 1.4F); return true;
    }
    public float absorb(float damage) {
        if (!enabled() || shieldTicks <= 0 || shieldRemaining <= 0 || !Float.isFinite(damage) || damage <= 0) { return 0; }
        float absorbed = Math.min(shieldRemaining, damage); shieldRemaining -= absorbed;
        if (shieldRemaining <= 0) { shieldTicks = 0; }
        OmenVfx.emit(this, OmenVfx.IMPACT, this, position(), Vec3.ZERO, 14, 1); return absorbed;
    }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        return target.hurtServer(level, source, amount) ? Math.max(0, Math.min(amount * 1.15F, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
    }
    public boolean bolt(ServerLevel level, Vec3 aim) {
        int interval = getControllingPassenger() instanceof Player ? 6 : 200;
        if (!enabled() || cooldowns[0] > 200 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.7D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        OmenBolt shot = PhantomismContent.OMEN_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 2.2F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        cooldowns[0] = 200; action("bolt", 10); OmenVfx.emit(this, OmenVfx.BOLT, shot, muzzle, direction, 32, .6F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return bolt(level, rider.getViewVector(1)); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox())
                || !line(getEyePosition(), enemy.getEyePosition()) || damage(level, enemy, damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0) { return false; }
        meleeTicks = 34; action("strike", 10); OmenVfx.emit(this, OmenVfx.IMPACT, null, enemy.position(), Vec3.ZERO, 16, 1); return true;
    }
    private boolean safeStep(Vec3 step) { AABB swept = getBoundingBox().expandTowards(step); return isLoadedFlightBox(swept) && level().noCollision(this, swept) && !level().containsAnyLiquid(swept); }
    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[1] > 0 || !validTarget(target) || distanceToSqr(target) > 12 * 12
                || !line(getEyePosition(), target.getEyePosition()) || !safeStep(target.position().subtract(position()).normalize().scale(.8D))) { return false; }
        diveId = target.getUUID(); diveTicks = 14; cooldowns[1] = 240; action("dive", 14);
        OmenVfx.emit(this, OmenVfx.DIVE, this, position(), target.position().subtract(position()), 20, 2); return true;
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = enemies(level, position(), 12, 32).stream().filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).findFirst().orElse(null);
        return dive(level, target);
    }
    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        if (!(level.getEntity(diveId) instanceof LivingEntity target) || !validTarget(target) || --diveTicks == 0) { diveTicks = 0; diveId = null; return; }
        if (getBoundingBox().inflate(.65D).intersects(target.getBoundingBox())) {
            if (line(getEyePosition(), target.getEyePosition())) { damage(level, target, damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F); OmenVfx.emit(this, OmenVfx.IMPACT, null, target.position(), Vec3.ZERO, 18, 2); }
            diveTicks = 0; diveId = null;
        } else if (!isVehicle()) { Vec3 step = target.position().subtract(position()).normalize().scale(.8D); if (safeStep(step)) { setDeltaMovement(step); } else { diveTicks = 0; diveId = null; } }
    }
    public int calamityTicks() { return calamityTicks; }
    public Vec3 calamityCenter() { return calamityCenter; }
    public boolean calamity(ServerLevel level) {
        if (!enabled() || cooldowns[5] > 0 || calamityTicks > 0) { return false; }
        List<LivingEntity> crowd = enemies(level, position(), 28, 48);
        if (crowd.size() < 4 && crowd.stream().filter(OmenPhantom::raider).count() < 2) { return false; }
        calamityCenter = position(); calamityTicks = 240; cooldowns[5] = 1800; action("calamity", 32);
        OmenVfx.emit(this, OmenVfx.CALAMITY, null, calamityCenter, Vec3.ZERO, 240, 28); return true;
    }
    private void judgment(ServerLevel level) {
        List<LivingEntity> targets = enemies(level, calamityCenter, 28, 8);
        for (LivingEntity target : targets) {
            if (damage(level, target, damageSources().mobAttack(this), raider(target) ? 6 : 5) <= 0) { continue; }
            if (target.getType() != EntityTypes.WITHER && target.getType() != EntityTypes.ENDER_DRAGON) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this); }
            OmenVfx.emit(this, OmenVfx.JUDGMENT, target, target.position(), Vec3.ZERO, 24, 1.5F);
        }
        if (!targets.isEmpty()) { setMarked(targets.getFirst(), 120); }
        action("judgment", 14);
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (diveTicks > 0 || distanceToSqr(target) <= 12 * 12 && dive(level, target)) { return true; }
        if (doHurtTarget(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; }
        if (!enabled()) { endOmen(); return; }
        if (markTicks > 0) { markTicks--; } marked();
        if (shieldTicks > 0 && --shieldTicks == 0) { shieldRemaining = 0; }
        if (standardTicks > 0 && (--standardTicks == 0 || distanceToSqr(standardCenter) > 56 * 56)) { standardTicks = 0; standardCenter = null; }
        if (calamityTicks > 0) {
            if (distanceToSqr(calamityCenter) > 56 * 56 || !isLoadedFlightBox(new AABB(calamityCenter, calamityCenter).inflate(1))) { endOmen(); return; }
            int elapsed = 240 - --calamityTicks;
            if (elapsed == 60 || elapsed == 110 || elapsed == 160) { judgment(level); }
            if (calamityTicks == 0) { OmenVfx.emit(this, OmenVfx.CONCLUSION, null, calamityCenter, Vec3.ZERO, 24, 28); action("conclusion", 18); calamityCenter = null; }
        }
        tickDive(level);
        if (tickCount % 10 == 0) {
            List<LivingEntity> crowd = enemies(level, position(), 56, 32);
            if (!crowd.isEmpty()) {
                LivingEntity priority = crowd.getFirst();
                if (marked() == null) { mark(level, priority); }
                OmenTactics.coordinate(this, marked());
                if (calamityTicks == 0) { calamity(level); } standard(level);
                if (tickCount % 60 == 0 && crowd.stream().anyMatch(OmenPhantom::raider)) { OmenVfx.emit(this, OmenVfx.HARBINGER, this, position(), Vec3.ZERO, 30, 2); }
            }
        }
        if (!isVehicle() && tickCount % 5 == 0 && validTarget(getTarget()) && distanceToSqr(getTarget()) <= 42 * 42
                && line(getEyePosition(), getTarget().getEyePosition())) { bolt(level, getTarget().getEyePosition().subtract(getEyePosition())); }
    }
    private void action(String clip, int duration) { actionTicks = duration; sessionOpen = true; triggerAnim("omen", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<OmenPhantom>("omen", 3, state -> actionTicks > 0 ? PlayState.CONTINUE : PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.omen." + clip)); } controllers.add(controller);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isAlive() && !isTame() && !player.isSpectator() && player.getItemInHand(hand).is(Items.OMINOUS_BOTTLE)) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte)7); } return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endOmen() {
        if (sessionOpen && level() instanceof ServerLevel) { OmenVfx.cancel(this); session = UUID.randomUUID(); sessionOpen = false; }
        markedId = null; orderedId = null; incomingId = null; diveId = null; markTicks = 0; standardTicks = 0; calamityTicks = 0; shieldTicks = 0; shieldRemaining = 0; diveTicks = 0; actionTicks = 0; standardCenter = null; calamityCenter = null;
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endOmen(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endOmen(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endOmen(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endOmen(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("OmenMelee", meleeTicks); for (int index = 0; index < cooldowns.length; index++) { output.putInt("OmenCooldown" + index, cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endOmen(); session = UUID.randomUUID(); meleeTicks = Math.clamp(input.getIntOr("OmenMelee", 0), 0, 34); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("OmenCooldown" + index, 0), 0, INTERVALS[index]); } }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { var slots = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 5; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("omen_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(slots); }
}
