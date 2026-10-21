package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ZombiePhantom;
import net.ronm19.phantomism.util.ModTags;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class ZombiePresence {
    private static final Map<Mob, Calm> CALMED = new WeakHashMap<>();
    private ZombiePresence() {}

    public static boolean eligible(Mob mob) { return mob.is(ModTags.EntityTypes.ZOMBIE_PRESENCE_ELIGIBLE) && mob.getMaxHealth() < 80; }
    private static boolean family(ZombiePhantom source, @Nullable LivingEntity target) { return target != null && (target == source || source.isAlliedTo(target)); }

    public static void pulse(ZombiePhantom source, ServerLevel level) {
        if (!source.isAlive() || source.isBaby() || source.isRemoved() || source.level() != level) { return; }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, source.getBoundingBox().inflate(12), mob -> mob.isAlive() && eligible(mob)
                && source.distanceToSqr(mob) <= 144).stream().limit(16).toList()) {
            Calm previous = CALMED.get(mob);
            ZombiePhantom previousSource = previous == null ? null : active(mob, previous);
            if (previousSource != null) {
                if (previous.source.equals(source.getUUID())) { previous.expires = level.getGameTime() + 30; }
                clear(mob, previousSource); continue;
            }
            if (previous != null) { CALMED.remove(mob); restore(mob, previous); }
            LivingEntity original = family(source, mob.getTarget()) ? mob.getTarget() : null;
            var brain = mob.getBrain();
            LivingEntity remembered = brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                    ? brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(target -> family(source, target)).orElse(null) : null;
            CALMED.put(mob, new Calm(source.getUUID(), original == null ? null : original.getUUID(), remembered == null ? null : remembered.getUUID(),
                    remembered == null ? 0 : brain.getTimeUntilExpiry(MemoryModuleType.ATTACK_TARGET), level.getGameTime()));
            clear(mob, source);
        }
    }

    private static @Nullable ZombiePhantom active(Mob mob, Calm control) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || !eligible(mob) || level.getGameTime() >= control.expires) { return null; }
        var entity = level.getEntity(control.source);
        return entity instanceof ZombiePhantom source && source.isAlive() && !source.isBaby() && source.distanceToSqr(mob) <= 144 ? source : null;
    }

    private static void clear(Mob mob, ZombiePhantom source) {
        boolean interrupted = family(source, mob.getTarget());
        if (interrupted) { mob.setTarget(null); }
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && family(source, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) { brain.eraseMemory(MemoryModuleType.ATTACK_TARGET); interrupted = true; }
        if (interrupted) { mob.getNavigation().stop(); }
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)) { return; }
        Calm control = CALMED.get(mob); if (control == null) { return; }
        ZombiePhantom source = active(mob, control);
        if (source == null) { CALMED.remove(mob); restore(mob, control); }
        else { clear(mob, source); }
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) { return; }
        Calm control = CALMED.get(mob); ZombiePhantom source = control == null ? null : active(mob, control);
        if (source != null && family(source, event.getNewAboutToBeSetTarget())) { event.setCanceled(true); }
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob)) { return; }
        Calm control = CALMED.get(mob); ZombiePhantom source = control == null ? null : active(mob, control);
        if (source != null && family(source, event.getEntity())) { event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ZombiePhantom zombie && zombie.level() instanceof ServerLevel level && zombie.tryRevive(level, event.getSource())) { event.setCanceled(true); }
    }

    public static void release(ZombiePhantom source) {
        if (source.level().isClientSide()) { return; }
        var entries = CALMED.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (entry.getValue().source.equals(source.getUUID())) { Mob mob = entry.getKey(); Calm control = entry.getValue(); entries.remove(); restore(mob, control); }
        }
    }

    private static void restore(Mob mob, Calm control) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) { return; }
        if (mob.getTarget() == null && control.target != null && level.getEntity(control.target) instanceof LivingEntity target && target.isAlive()) { mob.setTarget(target); }
        var brain = mob.getBrain(); long lifetime = control.lifetime - (level.getGameTime() - control.started);
        if (control.memory != null && brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty() && lifetime > 0
                && level.getEntity(control.memory) instanceof LivingEntity target && target.isAlive()) {
            if (control.lifetime == Long.MAX_VALUE) { brain.setMemory(MemoryModuleType.ATTACK_TARGET, target); }
            else { brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, lifetime); }
        }
    }

    private static final class Calm {
        private final UUID source;
        private final @Nullable UUID target;
        private final @Nullable UUID memory;
        private final long lifetime;
        private final long started;
        private long expires;
        private Calm(UUID source, @Nullable UUID target, @Nullable UUID memory, long lifetime, long started) {
            this.source = source; this.target = target; this.memory = memory; this.lifetime = lifetime; this.started = started; this.expires = started + 30;
        }
    }
}
