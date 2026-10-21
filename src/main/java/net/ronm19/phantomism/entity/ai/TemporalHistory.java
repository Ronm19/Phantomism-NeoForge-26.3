package net.ronm19.phantomism.entity.ai;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ChronoPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class TemporalHistory {
    private static final Map<LivingEntity, History> HISTORIES = new WeakHashMap<>();
    private TemporalHistory() {}
    public static void watch(LivingEntity ally) {
        if (!ally.isAlive() || !(ally.level() instanceof ServerLevel)) { return; }
        if (!HISTORIES.containsKey(ally) && HISTORIES.size() < 512) { HISTORIES.put(ally, new History(ally.getHealth())); }
        observe(ally);
    }
    private static void observe(LivingEntity ally) {
        History history = HISTORIES.get(ally); if (history == null) { return; }
        long now = ally.level().getGameTime(); history.losses.removeIf(loss -> now - loss.time > 80 || loss.amount <= .0001F);
        float healed = Math.max(0, ally.getHealth() - history.health);
        for (Loss loss : history.losses) { float recovered = Math.min(healed, loss.amount); loss.amount -= recovered; healed -= recovered; if (healed <= 0) { break; } }
        history.health = ally.getHealth();
    }
    public static float available(LivingEntity ally) {
        if (!ally.isAlive() || ally.getHealth() <= 0) { return 0; } observe(ally);
        History history = HISTORIES.get(ally); return history == null || history.recovering ? 0 : Math.min(ally.getMaxHealth() - ally.getHealth(), (float) history.losses.stream().mapToDouble(loss -> loss.amount).sum());
    }
    public static float recover(ChronoPhantom source, LivingEntity ally, float cap) {
        if (!source.combatEnabled() || !source.acceptsAlly(ally) || source.distanceToSqr(ally) > 24 * 24 || !Float.isFinite(cap) || cap <= 0) { return 0; }
        float amount = Math.min(cap, available(ally)); History history = HISTORIES.get(ally);
        if (history == null || history.recovering || amount <= 0) { return 0; }
        history.recovering = true; float before = ally.getHealth();
        try { ally.heal(amount); observe(ally); return Math.max(0, ally.getHealth() - before); }
        finally { history.recovering = false; }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) { return; }
        if (!HISTORIES.containsKey(target)) {
            if (level.getEntitiesOfClass(ChronoPhantom.class, target.getBoundingBox().inflate(48), source -> source.combatEnabled() && source.acceptsAlly(target)).stream().findAny().isEmpty()) { return; }
            watch(target);
        }
        observe(target);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity(); History history = HISTORIES.get(target); if (history == null) { return; }
        if (!target.isAlive()) { HISTORIES.remove(target); return; }
        float loss = Math.min(target.getMaxHealth(), Math.max(0, event.getHealthDamage()));
        if (Float.isFinite(loss) && loss > 0) { while (history.losses.size() >= 32) { history.losses.removeFirst(); } history.losses.addLast(new Loss(target.level().getGameTime(), loss)); }
        history.health = target.getHealth();
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) { if (event.getEntity() instanceof LivingEntity target && HISTORIES.containsKey(target)) { if (!target.isAlive()) { HISTORIES.remove(target); } else { observe(target); } } }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof LivingEntity target) { HISTORIES.remove(target); } }
    private static final class History { private float health; private boolean recovering; private final ArrayDeque<Loss> losses = new ArrayDeque<>(); private History(float health) { this.health = health; } }
    private static final class Loss { private final long time; private float amount; private Loss(long time, float amount) { this.time = time; this.amount = amount; } }
}
