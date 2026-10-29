package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.NewYearsPhantom;
import net.ronm19.phantomism.vfx.NewYearsVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class NewYearsSupport {
    private record Lease(WeakReference<NewYearsPhantom> source, UUID session, long expires) {}
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "new_years_fortune");
    private static final Map<LivingEntity, Lease> FORTUNE = new WeakHashMap<>(), WARDS = new WeakHashMap<>(), DISTRACTIONS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> DEFENSE_REST = new WeakHashMap<>(), HEAL_REST = new WeakHashMap<>();
    private NewYearsSupport() {}
    private static NewYearsPhantom source(LivingEntity target, Lease lease, boolean combat, boolean hostile) {
        NewYearsPhantom source = lease == null ? null : lease.source().get();
        return source == null || !source.companionAvailable() || combat && !source.enabled() || !source.session().equals(lease.session())
                || source.level() != target.level() || target.isRemoved() || !target.isAlive() || target.level().getGameTime() >= lease.expires()
                || source.distanceToSqr(target) > 144 || !source.line(source.getEyePosition(), target.getEyePosition())
                || (hostile ? !source.validTarget(target) : !source.ally(target)) ? null : source;
    }
    private static Lease lease(NewYearsPhantom source, int duration) {
        return new Lease(new WeakReference<>(source), source.session(), source.level().getGameTime() + duration);
    }
    public static void fortune(NewYearsPhantom source, LivingEntity target) {
        if (!source.isTame() || !source.companionAvailable() || !source.ally(target) || source.distanceToSqr(target) > 144
                || !source.line(source.getEyePosition(), target.getEyePosition())) { return; }
        Lease previous = FORTUNE.get(target);
        if (source(target, previous, false, false) != null && previous.source().get() != source) { return; }
        FORTUNE.put(target, lease(source, 30));
        speed(target);
    }
    /** The same flock boon never multiplies with itself or the existing March boon. */
    private static void speed(LivingEntity target) {
        double desired = protectedByShield(target) ? .08D : fortuned(target) ? .04D : 0;
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            var instance = target.getAttribute(attribute);
            if (instance == null) { continue; }
            double other = instance.hasModifier(Identifier.fromNamespaceAndPath("phantomism", "st_patricks_favor")) ? .05D : 0;
            double amount = Math.max(0, desired - other);
            var previous = instance.getModifier(SPEED);
            if (previous != null && Math.abs(previous.amount() - amount) < .000001D) { continue; }
            if (previous != null) { instance.removeModifier(SPEED); }
            if (amount > 0) { instance.addTransientModifier(new AttributeModifier(SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        }
    }
    private static void clearFortune(LivingEntity target) {
        FORTUNE.remove(target);
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.removeModifier(SPEED); } }
    }
    public static boolean fortuned(LivingEntity target) { return source(target, FORTUNE.get(target), false, false) != null; }
    public static void protect(NewYearsPhantom source, LivingEntity target) {
        if (!source.enabled() || source.shieldTicks() <= 0 || !source.ally(target) || source.distanceToSqr(target) > 144) { return; }
        Lease previous = WARDS.get(target);
        if (source(target, previous, true, false) != null && previous.source().get() != source) { return; }
        WARDS.put(target, lease(source, Math.min(15, source.shieldTicks()))); speed(target);
    }
    public static boolean protectedByShield(LivingEntity target) {
        NewYearsPhantom caster = source(target, WARDS.get(target), true, false);
        return caster != null && caster.shieldTicks() > 0;
    }
    public static boolean heal(NewYearsPhantom source, LivingEntity target, float amount) {
        long now = target.level().getGameTime();
        if (!source.enabled() || !source.ally(target) || source.distanceToSqr(target) > 400 || target.getHealth() >= target.getMaxHealth()
                || !source.line(source.getEyePosition(), target.getEyePosition()) || HEAL_REST.getOrDefault(target, Long.MIN_VALUE) > now) { return false; }
        float before = target.getHealth(); target.heal(Math.clamp(amount, 0, 4));
        if (target.getHealth() <= before) { return false; }
        HEAL_REST.put(target, now + 35); return true;
    }
    public static void distract(NewYearsPhantom source, LivingEntity target, int duration) {
        if (!source.enabled() || !source.validTarget(target) || source.distanceToSqr(target) > 144) { return; }
        boolean boss = target instanceof Mob mob && GravityControl.movementImmune(mob);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, boss ? 20 : duration, 0), source);
        if (!(target instanceof Mob mob) || boss) { return; }
        Lease previous = DISTRACTIONS.get(target);
        if (source(target, previous, true, true) != null && previous.source().get() != source) { return; }
        DISTRACTIONS.put(target, lease(source, Math.clamp(duration, 1, 20))); enforce(mob);
    }
    public static boolean distractedFrom(Mob mob, LivingEntity target) {
        NewYearsPhantom source = source(mob, DISTRACTIONS.get(mob), true, true);
        return source != null && source.ally(target);
    }
    private static void enforce(Mob mob) {
        if (distractedFrom(mob, mob.getTarget())) { mob.setTarget(null); mob.getNavigation().stop(); }
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && distractedFrom(mob, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) { brain.eraseMemory(MemoryModuleType.ATTACK_TARGET); }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel)) { return; }
        if (FORTUNE.containsKey(target) && source(target, FORTUNE.get(target), false, false) == null) { clearFortune(target); }
        if (WARDS.containsKey(target) && !protectedByShield(target)) { WARDS.remove(target); speed(target); }
        if (target.tickCount % 5 == 0 && (FORTUNE.containsKey(target) || WARDS.containsKey(target))) { speed(target); }
        if (DISTRACTIONS.containsKey(target)) {
            if (source(target, DISTRACTIONS.get(target), true, true) == null) { DISTRACTIONS.remove(target); }
            else if (target instanceof Mob mob && target.tickCount % 5 == 0) { enforce(mob); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void targeting(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && distractedFrom(mob, event.getNewAboutToBeSetTarget())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        NewYearsPhantom ward = source(target, WARDS.get(target), true, false);
        boolean hostile = attacker != null && (ward != null && ward.safeEnemy(attacker));
        float multiplier = !StPatricksSupport.protectedByAura(target) && ward != null && ward.shieldTicks() > 0 && (hostile || event.getSource().is(DamageTypeTags.IS_EXPLOSION)) ? .8F : 1;
        NewYearsPhantom fortune = source(target, FORTUNE.get(target), false, false);
        if (!StPatricksSupport.favored(target) && fortune != null && attacker != null && fortune.safeEnemy(attacker) && event.getNewDamage() >= 3
                && DEFENSE_REST.getOrDefault(target, Long.MIN_VALUE) <= level.getGameTime() && fortune.getRandom().nextFloat() < .15F) {
            DEFENSE_REST.put(target, level.getGameTime() + 200); multiplier = Math.min(multiplier, .75F);
            NewYearsVfx.emit(fortune, NewYearsVfx.FORTUNE, target, target.position(), 24, 1);
        }
        if (multiplier < 1) { event.setNewDamage(event.getNewDamage() * multiplier); }
    }
    public static void releaseShield(NewYearsPhantom source) {
        for (var entry : java.util.List.copyOf(WARDS.entrySet())) {
            if (entry.getValue().source().get() == source) { WARDS.remove(entry.getKey()); speed(entry.getKey()); }
        }
    }
    public static void release(NewYearsPhantom source) {
        for (var entry : java.util.List.copyOf(FORTUNE.entrySet())) { if (entry.getValue().source().get() == source) { clearFortune(entry.getKey()); } }
        releaseShield(source); DISTRACTIONS.entrySet().removeIf(entry -> entry.getValue().source().get() == source);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof NewYearsPhantom source) { release(source); }
        if (event.getEntity() instanceof LivingEntity target) { clearFortune(target); WARDS.remove(target); DISTRACTIONS.remove(target); DEFENSE_REST.remove(target); HEAL_REST.remove(target); }
    }
}
