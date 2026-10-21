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
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.AngelVfx;

public final class AngelPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/angel_phantom");
    private static final int[] INTERVALS = {400, 300, 400, 240, 240, 480, 320, 2000};
    private static final String[] CLIPS = {"touch", "blessing", "trail", "smite", "dive", "shield", "intercept", "eye", "conclusion", "strike"};
    private final int[] cooldowns = new int[8];
    private final Deque<Trail> trail = new ArrayDeque<>();
    private final Map<UUID, Long> trailHealed = new HashMap<>(), cleansed = new HashMap<>();
    private List<LivingEntity> sensed = List.of();
    private UUID session = UUID.randomUUID(), diveTarget, orderedTarget;
    private UUID imminentAttacker, imminentWard;
    private long imminentUntil;
    private int meleeTicks, diveTicks, trailTicks, eyeTicks, actionTicks;
    private Vec3 eyeCenter, lastTrail;
    private boolean sessionOpen;
    private record Trail(Vec3 position, long until) {}

    public AngelPhantom(EntityType<? extends AngelPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 76).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .4D).add(Attributes.MOVEMENT_SPEED, .25D)
                .add(Attributes.FLYING_SPEED, .425D).add(Attributes.FOLLOW_RANGE, 56);
    }
    public static boolean checkSpawnRules(EntityType<AngelPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension().equals(Level.OVERWORLD) && level.getBiome(position).is(ModTags.Biomes.ANGEL_PHANTOM_SPAWNS)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.ANGEL_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return eyeTicks > 0 || super.isUltimateAnimationActive(); }
    @Override public void travel(Vec3 input) { if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    public UUID session() { return session; }
    public int eyeTicks() { return eyeTicks; }
    public Vec3 eyeCenter() { return eyeCenter; }
    public boolean available() { return isAlive() && !isRemoved() && !isBaby() && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level()); }
    public boolean enabled() { return available() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT); }
    public boolean sanctuaryRider(LivingEntity entity) { return available() && entity instanceof Player && getControllingPassenger() == entity && isOwnedBy(entity); }
    public boolean ally(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level()) { return false; }
        return target == this || isTame() && (target == getOwner() || isAlliedTo(target) || target instanceof OwnableEntity owned && getOwnerReference().equals(owned.getOwnerReference()))
                || !isTame() && (target.getType() == EntityTypes.VILLAGER || target.getType() == EntityTypes.IRON_GOLEM);
    }
    private boolean recent(LivingEntity attacker, LivingEntity victim) {
        return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100
                || attacker.getLastHurtMob() == victim && attacker.tickCount - attacker.getLastHurtMobTimestamp() < 100
                || attacker instanceof Mob mob && mob.getTarget() == victim);
    }
    public boolean threatens(LivingEntity attacker, LivingEntity victim) {
        return attacker != null && !ally(attacker) && !isAlliedTo(attacker) && !(attacker instanceof AbstractPhantom) && ally(victim) && !PhantomDuels.pair(attacker, victim)
                && (recent(attacker, victim) || level().getGameTime() <= imminentUntil && attacker.getUUID().equals(imminentAttacker) && victim.getUUID().equals(imminentWard));
    }
    public void noticeIncoming(LivingEntity attacker, LivingEntity victim) {
        if (!ally(victim) || ally(attacker) || isAlliedTo(attacker) || attacker instanceof AbstractPhantom || PhantomDuels.pair(attacker, victim)) { return; }
        imminentAttacker = attacker.getUUID(); imminentWard = victim.getUUID(); imminentUntil = level().getGameTime() + 1;
    }
    private boolean provoked(LivingEntity target) { return recent(target, this) || recent(target, getOwner()) || sensed.stream().anyMatch(friend -> ally(friend) && distanceToSqr(friend) <= 24 * 24 && threatens(target, friend)); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target == null || ally(target) || target instanceof AbstractPhantom) { return false; }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) { return false; }
        return PhantomDuels.pair(this, target) || target.getUUID().equals(orderedTarget) || provoked(target) || isTame() && !(target instanceof NeutralMob)
                && target.getType() != EntityTypes.PIGLIN && target instanceof Enemy && !(target instanceof OwnableEntity);
    }
    public boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && !target.isInvulnerable() && target.level() == level() && !ally(target) && !isAlliedTo(target)
                && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(this, target) && isSpeciesCombatTarget(target)
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob)) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return validTarget(target) && provoked(target); }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target == null || ally(target) || target instanceof AbstractPhantom || isAlliedTo(target) || !PhantomDuels.begin(this, target)) { return; }
        orderedTarget = target.getUUID();
        super.setCombatTarget(target);
    }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public boolean recipient(LivingEntity target, double radius) { return available() && ally(target) && distanceToSqr(target) <= radius * radius && line(getEyePosition(), target.getEyePosition()); }
    private double priority(LivingEntity target) {
        double health = target.getHealth() / target.getMaxHealth(); boolean owner = target == getOwner() || sanctuaryRider(target);
        return (health <= .35D ? owner ? -1500 : -500 : owner ? -20 : 0) - (dangerousEffects(target) ? 200 : 0) + health * 100 + distanceToSqr(target) * .01D;
    }
    private boolean dangerousEffects(LivingEntity target) { return target.getActiveEffects().stream().anyMatch(effect -> AngelSupport.cleanseable(effect) && (effect.getDuration() >= 100 || effect.getAmplifier() > 0 || (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER)) && effect.getDuration() >= 40)); }
    private void sense(ServerLevel level) {
        List<LivingEntity> memory = getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of());
        var candidates = new ArrayList<>(memory);
        candidates.addAll(level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(56), this::ally));
        candidates.add(this); if (getOwner() != null) { candidates.add(getOwner()); }
        sensed = candidates.stream().distinct().filter(target -> ally(target) && distanceToSqr(target) <= 56 * 56).sorted(Comparator.comparingDouble(this::priority)).limit(64).toList();
    }
    private List<LivingEntity> allies(double radius) { return sensed.stream().filter(target -> recipient(target, radius)).limit(32).toList(); }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return !AngelSupport.cleanseable(effect) && super.canBeAffected(effect); }
    public boolean cleanse(LivingEntity target, double radius) {
        if (!recipient(target, radius)) { return false; }
        long now = level().getGameTime();
        if (cleansed.getOrDefault(target.getUUID(), Long.MIN_VALUE) > now && target.getActiveEffects().stream().noneMatch(AngelSupport::cleanseable)) { return false; }
        boolean changed = false;
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) { if (AngelSupport.cleanseable(effect)) { changed |= target.removeEffect(effect.getEffect()); } }
        if (cleansed.size() < 128 || cleansed.containsKey(target.getUUID())) { cleansed.put(target.getUUID(), now + 20); }
        if (changed) { AngelVfx.emit(this, AngelVfx.CLEANSE, target, target.position(), Vec3.ZERO, 18, .8F); }
        return changed;
    }
    public float healAlly(LivingEntity target, float amount, double radius) {
        if (!recipient(target, radius) || !Float.isFinite(amount) || amount <= 0 || target.getHealth() >= target.getMaxHealth()) { return 0; }
        float before = target.getHealth(); target.heal(Math.min(amount, target.getMaxHealth() - before)); return Math.max(0, target.getHealth() - before);
    }
    public boolean touch(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || !recipient(target, 16) || target.getMaxHealth() - target.getHealth() < 2 || !AngelSupport.claimHeal(this, target)) { return false; }
        float healed;
        try { healed = healAlly(target, (float) Config.angelValue(Config.ANGEL_TOUCH_HEAL, 12), 16); } finally { AngelSupport.releaseHeal(this, target); }
        if (healed <= 0) { return false; }
        cooldowns[0] = 400; action("touch", 18); AngelVfx.link(this, target); return true;
    }
    public boolean blessing(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        if (!enabled() || cooldowns[1] > 0 || !recipient(target, 16) || !threatens(attacker, target)) { return false; }
        boolean changed = AngelSupport.ward(this, target, (float) Config.angelValue(Config.ANGEL_BLESSING_MITIGATION, .15D), 6, 120, 16, true);
        changed |= cleanse(target, 16); if (!changed) { return false; }
        cooldowns[1] = 300; action("blessing", 16); AngelVfx.emit(this, AngelVfx.BLESSING, target, target.position(), Vec3.ZERO, 120, 1); return true;
    }
    public boolean healTrail(ServerLevel level) {
        if (!enabled() || cooldowns[2] > 0 || trailTicks > 0 || getDeltaMovement().lengthSqr() < .01D || allies(20).stream().noneMatch(target -> target.getHealth() < target.getMaxHealth())) { return false; }
        trailTicks = 160; cooldowns[2] = 400; lastTrail = null; action("trail", 14); return true;
    }
    private void tickTrail(ServerLevel level) {
        long now = level.getGameTime(); trail.removeIf(segment -> now >= segment.until()); trailHealed.entrySet().removeIf(entry -> now >= entry.getValue());
        if (trailTicks > 0 && --trailTicks >= 0 && tickCount % 5 == 0 && getDeltaMovement().lengthSqr() >= .01D && (lastTrail == null || distanceToSqr(lastTrail) >= .25D)) {
            Vec3 previous = lastTrail; lastTrail = position(); if (trail.size() == 12) { trail.removeFirst(); } trail.addLast(new Trail(position(), now + 60));
            if (previous != null && previous.distanceToSqr(lastTrail) > 16 * 16) { previous = null; }
            AngelVfx.emit(this, AngelVfx.TRAIL, null, position(), previous == null ? Vec3.ZERO : previous.subtract(position()), 60, 2);
        }
        if (tickCount % 10 != 0 || trail.isEmpty()) { return; }
        for (LivingEntity target : allies(24)) {
            if (trailHealed.containsKey(target.getUUID()) || trail.stream().noneMatch(segment -> target.distanceToSqr(segment.position()) <= 4 && line(segment.position().add(0, .5D, 0), target.getEyePosition()))) { continue; }
            if (healAlly(target, (float) Config.angelValue(Config.ANGEL_TRAIL_HEAL, 1), 24) > 0 && trailHealed.size() < 64) { trailHealed.put(target.getUUID(), now + 40); }
        }
    }
    public boolean smite(ServerLevel level, Vec3 aim) {
        int interval = getControllingPassenger() instanceof Player ? 6 : 240;
        if (!enabled() || cooldowns[3] > 240 - interval || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .01D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.7D)); if (!line(getEyePosition(), muzzle)) { return false; }
        AngelSmite shot = PhantomismContent.ANGEL_SMITE.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 2F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        cooldowns[3] = 240; action("smite", 10); AngelVfx.emit(this, AngelVfx.SMITE, shot, muzzle, direction, 32, .5F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return smite(level, rider.getViewVector(1)); }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (!enabled() || !validTarget(target) || !getAssignments().permitsDefensivePursuit(target)) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount(); return target.hurtServer(level, source, amount) ? Math.max(0, Math.min(amount, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.65D).intersects(enemy.getBoundingBox()) || !line(getEyePosition(), enemy.getEyePosition())
                || damage(level, enemy, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0) { return false; }
        meleeTicks = 40; action("strike", 10); AngelVfx.emit(this, AngelVfx.IMPACT, null, enemy.getBoundingBox().getCenter(), enemy.position().subtract(position()), 16, 1); return true;
    }
    private boolean safeStep(Vec3 step) { AABB swept = getBoundingBox().expandTowards(step); return isLoadedFlightBox(swept) && level().noCollision(this, swept) && !level().containsAnyLiquid(swept); }
    public boolean dive(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[4] > 0 || !validTarget(target) || distanceToSqr(target) > 12 * 12 || !line(getEyePosition(), target.getEyePosition()) || !safeStep(target.position().subtract(position()).normalize().scale(.8D))) { return false; }
        diveTicks = 14; diveTarget = target.getUUID(); cooldowns[4] = 240; action("dive", 14); AngelVfx.emit(this, AngelVfx.DIVE, this, position(), target.position().subtract(position()), 20, 2); return true;
    }
    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; }
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12), this::validTarget).stream()
                .filter(enemy -> rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return dive(level, target);
    }
    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        if (!(level.getEntity(diveTarget) instanceof LivingEntity target) || !validTarget(target) || --diveTicks == 0) { diveTicks = 0; diveTarget = null; return; }
        if (getBoundingBox().inflate(.65D).intersects(target.getBoundingBox())) {
            if (line(getEyePosition(), target.getEyePosition())) { damage(level, target, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F); AngelVfx.emit(this, AngelVfx.IMPACT, null, target.position(), Vec3.ZERO, 18, 1.5F); }
            diveTicks = 0; diveTarget = null;
        } else if (!isVehicle()) { Vec3 step = target.position().subtract(position()).normalize().scale(.8D); if (safeStep(step)) { setDeltaMovement(step); } else { diveTicks = 0; diveTarget = null; } }
    }
    public boolean shield(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        if (!enabled() || cooldowns[5] > 0 || !recipient(target, 16) || !threatens(attacker, target)
                || !AngelSupport.ward(this, target, (float) Config.angelValue(Config.ANGEL_SHIELD_MITIGATION, .3D), (float) Config.angelValue(Config.ANGEL_SHIELD_CAPACITY, 8), 80, 16, false)) { return false; }
        cooldowns[5] = 480; action("shield", 16); AngelVfx.emit(this, AngelVfx.SHIELD, target, target.position(), Vec3.ZERO, 80, 1.5F); return true;
    }
    public boolean intercept(ServerLevel level, LivingEntity target, LivingEntity attacker, Entity attack) {
        if (!enabled() || cooldowns[6] > 0 || target == this || !recipient(target, 8) || !threatens(attacker, target) || !AngelSupport.claimIntercept(this, target, attack)) { return false; }
        if (!AngelSupport.ward(this, target, .2F, 6, 40, 8, false)) { return false; }
        if (!isVehicle()) { Vec3 direction = target.position().subtract(position()); if (direction.lengthSqr() > 4) { Vec3 step = direction.normalize().scale(.8D); if (safeStep(step)) { setDeltaMovement(step); } } }
        cooldowns[6] = 320; action("intercept", 12); AngelVfx.link(this, target); AngelVfx.emit(this, AngelVfx.INTERCEPT, target, target.position(), target.position().subtract(position()), 22, 1.4F); return true;
    }
    public boolean canIntercept(LivingEntity target) { return enabled() && cooldowns[6] == 0 && target != this && recipient(target, 8); }
    public boolean eye(ServerLevel level) {
        if (!enabled() || cooldowns[7] > 0 || eyeTicks > 0) { return false; }
        sense(level); List<LivingEntity> recipients = allies(24);
        long injured = recipients.stream().filter(target -> target.getMaxHealth() - target.getHealth() >= 4).count();
        long critical = recipients.stream().filter(target -> target.getHealth() <= target.getMaxHealth() * .5F && target.getMaxHealth() - target.getHealth() >= 4).count();
        long afflicted = recipients.stream().filter(this::dangerousEffects).count();
        boolean emergency = recipients.stream().anyMatch(target -> (target == getOwner() || sanctuaryRider(target)) && target.getHealth() <= target.getMaxHealth() * .25F && target.getLastHurtByMob() != null && target.tickCount - target.getLastHurtByMobTimestamp() < 100);
        if (injured < 3 && critical < 2 && afflicted < 2 && !emergency) { return false; }
        boolean committed = false; var healed = new ArrayList<LivingEntity>();
        for (LivingEntity target : recipients) {
            if (AngelSupport.claimHeal(this, target)) {
                try { if (healAlly(target, (float) Config.angelValue(Config.ANGEL_EYE_HEAL, 18), 24) > 0) { committed = true; healed.add(target); } }
                finally { AngelSupport.releaseHeal(this, target); }
            }
            committed |= cleanse(target, 24);
        }
        if (!committed) { return false; }
        eyeCenter = position(); eyeTicks = 240; cooldowns[7] = 2000;
        action("eye", 30); AngelVfx.emit(this, AngelVfx.EYE, null, eyeCenter, Vec3.ZERO, 240, 24);
        int cues = 0;
        for (LivingEntity target : recipients) {
            if (AngelSupport.ward(this, target, (float) Config.angelValue(Config.ANGEL_EYE_MITIGATION, .12D), 12, 240, 24, true) && cues++ < 8) { AngelVfx.emit(this, AngelVfx.BLESSING, target, target.position(), Vec3.ZERO, 240, 1); }
        }
        healed.stream().limit(8).forEach(target -> AngelVfx.link(this, target)); return true;
    }
    private void mountedSanctuary(ServerLevel level) {
        if (!(getControllingPassenger() instanceof LivingEntity rider) || !sanctuaryRider(rider)) { return; }
        cleanse(rider, 20);
        MobEffectInstance regeneration = rider.getEffect(MobEffects.REGENERATION);
        if (regeneration == null || regeneration.getAmplifier() == 0 && regeneration.getDuration() <= 40) { rider.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, false, true), this); }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20), this::ally).stream().sorted(Comparator.comparingDouble(this::priority)).limit(64).toList()) { cleanse(target, 20); }
    }
    private void support(ServerLevel level) {
        if (eye(level)) { return; }
        List<LivingEntity> friends = allies(16);
        for (LivingEntity target : friends) { if (target.getMaxHealth() - target.getHealth() >= 4 && touch(level, target)) { break; } }
        healTrail(level);
        for (LivingEntity target : friends) {
            LivingEntity attacker = target.getLastHurtByMob();
            if (attacker != null && dangerousEffects(target)) { blessing(level, target, attacker); }
            if (attacker != null && threatens(attacker, target) && target.getHealth() <= target.getMaxHealth() * .65F) { shield(level, target, attacker); blessing(level, target, attacker); break; }
        }
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        LivingEntity urgent = cooldowns[0] == 0 ? sensed.stream().filter(target -> target != this && ally(target)
                && target.getHealth() <= target.getMaxHealth() * .35F && distanceToSqr(target) > 16 * 16 && distanceToSqr(target) <= 56 * 56
                && line(getEyePosition(), target.getEyePosition())).findFirst().orElse(null) : null;
        if (enabled() && !isVehicle() && urgent != null) {
            Vec3 step = urgent.position().subtract(position()).normalize().scale(.45D);
            if (safeStep(step)) {
                getNavigation().stop(); setDeltaMovement(step);
                if (tickCount % 20 == 0) { getConfiguredStats().apply(this); } return;
            }
        }
        super.customServerAiStep(level);
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (isTame() && getOwner() != null && getOwner().distanceToSqr(target) > 24 * 24 && !target.getUUID().equals(orderedTarget)) { clearCombatTarget(false); return true; }
        if (diveTicks > 0 || distanceToSqr(target) <= 12 * 12 && dive(level, target)) { return true; }
        if (doHurtTarget(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; } if (actionTicks > 0) { actionTicks--; }
        cleansed.entrySet().removeIf(entry -> level.getGameTime() >= entry.getValue());
        if (available() && tickCount % 10 == 0) { mountedSanctuary(level); }
        if (!enabled()) { endSupport(); return; }
        if (eyeTicks > 0 && --eyeTicks == 0) { action("conclusion", 18); AngelVfx.emit(this, AngelVfx.CONCLUSION, null, eyeCenter, Vec3.ZERO, 24, 8); eyeCenter = null; }
        if (tickCount % 20 == 0) { sense(level); support(level); }
        tickTrail(level); tickDive(level);
        if (tickCount % 80 == 0) { AngelVfx.emit(this, AngelVfx.PRESENCE, this, position(), Vec3.ZERO, 32, 1); }
        if (!isVehicle() && tickCount % 5 == 0 && validTarget(getTarget()) && distanceToSqr(getTarget()) <= 42 * 42 && line(getEyePosition(), getTarget().getEyePosition())) { smite(level, getTarget().getEyePosition().subtract(getEyePosition())); }
    }
    private void action(String clip, int duration) { actionTicks = duration; sessionOpen = true; triggerAnim("angel", clip); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); var controller = new AnimationController<AngelPhantom>("angel", 3, state -> actionTicks > 0 ? PlayState.CONTINUE : PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.angel." + clip)); } controllers.add(controller);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isAlive() && !isTame() && !player.isSpectator() && player.getItemInHand(hand).is(Items.GOLDEN_APPLE)) {
            if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void endSupport() {
        if (sessionOpen && level() instanceof ServerLevel) { AngelVfx.cancel(this); session = UUID.randomUUID(); sessionOpen = false; }
        if (cooldowns == null) { return; } AngelSupport.release(this); trail.clear(); trailHealed.clear(); sensed = List.of(); diveTicks = 0; diveTarget = null; orderedTarget = null; imminentAttacker = null; imminentWard = null; trailTicks = 0; eyeTicks = 0; eyeCenter = null; lastTrail = null; actionTicks = 0;
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endSupport(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endSupport(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (cooldowns != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endSupport(); } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endSupport(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("AngelMelee", meleeTicks); for (int index = 0; index < cooldowns.length; index++) { output.putInt("AngelCooldown" + index, cooldowns[index]); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endSupport(); session = UUID.randomUUID(); meleeTicks = Math.clamp(input.getIntOr("AngelMelee", 0), 0, 40); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("AngelCooldown" + index, 0), 0, INTERVALS[index]); } }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { var result = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 7; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("angel_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(result); }
}
