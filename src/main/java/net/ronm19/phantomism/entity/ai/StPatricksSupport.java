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
import net.ronm19.phantomism.entity.StPatricksPhantom;
import net.ronm19.phantomism.vfx.StPatricksVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class StPatricksSupport {
    private record Lease(WeakReference<StPatricksPhantom> source, UUID session, long expires) {}
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "st_patricks_favor");
    private static final Map<LivingEntity, Lease> FAVOR = new WeakHashMap<>(), WARDS = new WeakHashMap<>(), DISTRACTIONS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> DEFENSE_REST = new WeakHashMap<>(), HEAL_REST = new WeakHashMap<>();
    private StPatricksSupport() {}
    private static StPatricksPhantom source(LivingEntity target, Lease lease, boolean combat, boolean hostile) {
        StPatricksPhantom source = lease == null ? null : lease.source().get();
        return source == null || !source.companionAvailable() || combat && !source.enabled() || !source.session().equals(lease.session())
                || source.level() != target.level() || target.isRemoved() || !target.isAlive() || target.level().getGameTime() >= lease.expires()
                || source.distanceToSqr(target) > 144 || !source.line(source.getEyePosition(), target.getEyePosition())
                || (hostile ? !source.validTarget(target) : !source.ally(target)) ? null : source;
    }
    private static Lease lease(StPatricksPhantom source, int duration) {
        return new Lease(new WeakReference<>(source), source.session(), source.level().getGameTime() + duration);
    }
    public static void favor(StPatricksPhantom source, LivingEntity target) {
        if (!source.isTame() || !source.companionAvailable() || !source.ally(target) || source.distanceToSqr(target) > 144
                || !source.line(source.getEyePosition(), target.getEyePosition())) { return; }
        Lease previous = FAVOR.get(target);
        if (source(target, previous, false, false) != null && previous.source().get() != source) { return; }
        FAVOR.put(target, lease(source, 30));
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            var instance = target.getAttribute(attribute);
            if (instance != null && !instance.hasModifier(SPEED)) { instance.addTransientModifier(new AttributeModifier(SPEED, .05D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        }
    }
    private static void clearFavor(LivingEntity target) {
        FAVOR.remove(target);
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.removeModifier(SPEED); } }
    }
    public static boolean favored(LivingEntity target) { return source(target, FAVOR.get(target), false, false) != null; }
    public static void protect(StPatricksPhantom source, LivingEntity target) {
        if (!source.enabled() || source.auraTicks() <= 0 || !source.ally(target) || source.distanceToSqr(target) > 144) { return; }
        Lease previous = WARDS.get(target);
        if (source(target, previous, true, false) != null && previous.source().get() != source) { return; }
        WARDS.put(target, lease(source, Math.min(15, source.auraTicks())));
    }
    public static boolean protectedByAura(LivingEntity target) {
        StPatricksPhantom caster = source(target, WARDS.get(target), true, false);
        return caster != null && caster.auraTicks() > 0;
    }
    public static boolean heal(StPatricksPhantom source, LivingEntity target, float amount) {
        long now = target.level().getGameTime();
        if (!source.enabled() || !source.ally(target) || source.distanceToSqr(target) > 400 || target.getHealth() >= target.getMaxHealth()
                || !source.line(source.getEyePosition(), target.getEyePosition()) || HEAL_REST.getOrDefault(target, Long.MIN_VALUE) > now) { return false; }
        float before = target.getHealth(); target.heal(Math.clamp(amount, 0, 4));
        if (target.getHealth() <= before) { return false; }
        HEAL_REST.put(target, now + 50); return true;
    }
    public static void distract(StPatricksPhantom source, LivingEntity target, int duration) {
        if (!source.enabled() || !source.validTarget(target) || source.distanceToSqr(target) > 144) { return; }
        boolean boss = target instanceof Mob mob && GravityControl.movementImmune(mob);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, boss ? 20 : duration, 0), source);
        if (!(target instanceof Mob mob) || boss) { return; }
        Lease previous = DISTRACTIONS.get(target);
        if (source(target, previous, true, true) != null && previous.source().get() != source) { return; }
        DISTRACTIONS.put(target, lease(source, Math.clamp(duration, 1, 60))); enforce(mob);
    }
    public static boolean distractedFrom(Mob mob, LivingEntity target) {
        StPatricksPhantom source = source(mob, DISTRACTIONS.get(mob), true, true);
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
        if (FAVOR.containsKey(target) && source(target, FAVOR.get(target), false, false) == null) { clearFavor(target); }
        if (WARDS.containsKey(target) && !protectedByAura(target)) { WARDS.remove(target); }
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
        StPatricksPhantom ward = source(target, WARDS.get(target), true, false);
        boolean hostile = attacker != null && (ward != null && ward.safeEnemy(attacker));
        float multiplier = ward != null && ward.auraTicks() > 0 && (hostile || event.getSource().is(DamageTypeTags.IS_EXPLOSION)) ? .8F : 1;
        StPatricksPhantom favor = source(target, FAVOR.get(target), false, false);
        if (favor != null && attacker != null && favor.safeEnemy(attacker) && event.getNewDamage() >= 3
                && DEFENSE_REST.getOrDefault(target, Long.MIN_VALUE) <= level.getGameTime() && favor.getRandom().nextFloat() < .15F) {
            DEFENSE_REST.put(target, level.getGameTime() + 200); multiplier = Math.min(multiplier, .75F);
            StPatricksVfx.emit(favor, StPatricksVfx.FAVOR, target, target.position(), 24, 1);
        }
        if (multiplier < 1) { event.setNewDamage(event.getNewDamage() * multiplier); }
    }
    public static void releaseAura(StPatricksPhantom source) { WARDS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); }
    public static void release(StPatricksPhantom source) {
        for (var entry : java.util.List.copyOf(FAVOR.entrySet())) { if (entry.getValue().source().get() == source) { clearFavor(entry.getKey()); } }
        releaseAura(source); DISTRACTIONS.entrySet().removeIf(entry -> entry.getValue().source().get() == source);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof StPatricksPhantom source) { release(source); }
        if (event.getEntity() instanceof LivingEntity target) { clearFavor(target); WARDS.remove(target); DISTRACTIONS.remove(target); DEFENSE_REST.remove(target); HEAL_REST.remove(target); }
    }
}
