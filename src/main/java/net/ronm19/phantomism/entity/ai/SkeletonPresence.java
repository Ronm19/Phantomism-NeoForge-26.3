package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SkeletonPhantom;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class SkeletonPresence {
    private static final Map<Mob, Calm> CALMED = new WeakHashMap<>();
    private static final Map<Mob, UUID> PROVOKED = new WeakHashMap<>();
    private SkeletonPresence() {}
    public static boolean eligible(Mob mob) { return mob.is(EntityTypeTags.SKELETONS) && mob.getMaxHealth() < 80; }
    private static boolean family(SkeletonPhantom source, @Nullable LivingEntity target) { return target != null && (target == source || source.isAlliedTo(target)); }
    private static @Nullable SkeletonPhantom source(Mob mob, UUID identity) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || !eligible(mob)) { return null; }
        return level.getEntity(identity) instanceof SkeletonPhantom phantom && phantom.isAlive() && phantom.isTame() && !phantom.isBaby()
                && phantom.distanceToSqr(mob) <= 18 * 18 && phantom.getOwner() != null && phantom.getOwner().isAlive() ? phantom : null;
    }
    private static @Nullable SkeletonPhantom active(Mob mob, Calm calm) {
        SkeletonPhantom source = source(mob, calm.source);
        return source != null && source.distanceToSqr(mob) <= 12 * 12 ? source : null;
    }
    public static boolean isCalmed(SkeletonPhantom phantom, LivingEntity target) {
        if (!(target instanceof Mob mob)) { return false; }
        Calm calm = CALMED.get(mob); SkeletonPhantom source = calm == null ? null : active(mob, calm);
        return source != null && (source == phantom || source.isAlliedTo(phantom));
    }
    public static @Nullable Mob interceptionTarget(SkeletonPhantom phantom, ServerLevel level) {
        if (!phantom.isTame() || phantom.getOwner() == null || !phantom.getOwner().isAlive()) { return null; }
        return level.getEntitiesOfClass(Mob.class, phantom.getBoundingBox().inflate(28), mob -> eligible(mob) && mob.isAlive()
                && !PROVOKED.containsKey(mob) && !isCalmed(phantom, mob) && family(phantom, mob.getTarget())
                && phantom.distanceToSqr(mob) > 144 && phantom.distanceToSqr(mob) <= 28 * 28 && mob.distanceToSqr(phantom.getOwner()) <= 24 * 24)
                .stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(phantom.getOwner()))).orElse(null);
    }
    public static void pulse(SkeletonPhantom phantom, ServerLevel level) {
        if (!phantom.isTame() || phantom.isBaby() || !phantom.isAlive() || phantom.getOwner() == null || !phantom.getOwner().isAlive()) { return; }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, phantom.getBoundingBox().inflate(12), mob -> eligible(mob) && mob.isAlive()
                && phantom.distanceToSqr(mob) <= 144 && mob.distanceToSqr(phantom.getOwner()) <= 24 * 24).stream().limit(16).toList()) {
            if (PROVOKED.containsKey(mob)) { continue; }
            Calm old = CALMED.get(mob);
            if (old != null && active(mob, old) != null) { clear(mob, phantom); continue; }
            if (old != null) { CALMED.remove(mob); restore(mob, old); }
            LivingEntity target = family(phantom, mob.getTarget()) ? mob.getTarget() : null;
            var brain = mob.getBrain();
            LivingEntity memory = brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                    ? brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(entity -> family(phantom, entity)).orElse(null) : null;
            CALMED.put(mob, new Calm(phantom.getUUID(), target == null ? null : target.getUUID(), memory == null ? null : memory.getUUID(),
                    memory == null ? 0 : brain.getTimeUntilExpiry(MemoryModuleType.ATTACK_TARGET), level.getGameTime()));
            clear(mob, phantom);
            if (target != null) { phantom.getLookControl().setLookAt(mob); phantom.showSkeletonAction("presence"); }
        }
    }
    private static void clear(Mob mob, SkeletonPhantom phantom) {
        boolean interrupted = family(phantom, mob.getTarget());
        if (interrupted) { mob.setTarget(null); }
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && family(phantom, mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); interrupted = true;
        }
        if (interrupted) { mob.getNavigation().stop(); }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)) { return; }
        UUID provoked = PROVOKED.get(mob);
        if (provoked != null && source(mob, provoked) == null) { PROVOKED.remove(mob); }
        Calm calm = CALMED.get(mob); if (calm == null) { return; }
        SkeletonPhantom phantom = active(mob, calm);
        if (phantom == null) { CALMED.remove(mob); restore(mob, calm); } else { clear(mob, phantom); }
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) { return; }
        Calm calm = CALMED.get(mob); SkeletonPhantom phantom = calm == null ? null : active(mob, calm);
        if (phantom != null && family(phantom, event.getNewAboutToBeSetTarget())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void preventHostileDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob)) { return; }
        Calm calm = CALMED.get(mob); SkeletonPhantom phantom = calm == null ? null : active(mob, calm);
        if (phantom != null && family(phantom, event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getInflictedDamage() <= 0) { return; }
        Calm calm = CALMED.remove(mob);
        if (calm != null) { PROVOKED.put(mob, calm.source); restore(mob, calm); }
    }
    public static void release(SkeletonPhantom phantom) {
        var entries = CALMED.entrySet().iterator();
        while (entries.hasNext()) { var entry = entries.next(); if (entry.getValue().source.equals(phantom.getUUID())) { entries.remove(); restore(entry.getKey(), entry.getValue()); } }
        PROVOKED.values().removeIf(identity -> identity.equals(phantom.getUUID()));
    }
    private static void restore(Mob mob, Calm calm) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) { return; }
        if (mob.getTarget() == null && calm.target != null && level.getEntity(calm.target) instanceof LivingEntity target && target.isAlive()) { mob.setTarget(target); }
        long remaining = calm.lifetime - (level.getGameTime() - calm.started);
        if (calm.memory != null && remaining > 0 && mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty() && level.getEntity(calm.memory) instanceof LivingEntity target && target.isAlive()) {
            if (calm.lifetime == Long.MAX_VALUE) { mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target); }
            else { mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, remaining); }
        }
    }
    private record Calm(UUID source, @Nullable UUID target, @Nullable UUID memory, long lifetime, long started) {}
}
