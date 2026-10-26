package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ChristmasPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class ChristmasSupport {
    private record Ward(WeakReference<ChristmasPhantom> source, long until) {}
    private static final Map<LivingEntity, Ward> WARDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> REST = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> CHEER = new WeakHashMap<>();
    private ChristmasSupport() {}
    public static boolean claimCheer(LivingEntity ally) {
        long now = ally.level().getGameTime();
        if (CHEER.getOrDefault(ally, Long.MIN_VALUE) > now) { return false; }
        CHEER.put(ally, now + 100); return true;
    }
    public static boolean protect(ChristmasPhantom source, LivingEntity ally, int ticks) {
        long now = source.level().getGameTime();
        if (!source.enabled() || !source.ally(ally) || source.distanceToSqr(ally) > 24 * 24 || !source.line(source.getEyePosition(), ally.getEyePosition())
                || REST.getOrDefault(ally, Long.MIN_VALUE) > now) { return false; }
        WARDS.put(ally, new Ward(new WeakReference<>(source), now + Math.clamp(ticks, 1, 60))); REST.put(ally, now + 200); return true;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        if (event.getNewDamage() >= 4 || target.getHealth() - event.getNewDamage() <= target.getMaxHealth() * .35F) {
            for (ChristmasPhantom source : level.getEntitiesOfClass(ChristmasPhantom.class, target.getBoundingBox().inflate(16), phantom -> phantom.enabled() && phantom.ally(target) && phantom.safeEnemy(attacker))
                    .stream().sorted(Comparator.comparingDouble(phantom -> phantom.distanceToSqr(target))).limit(4).toList()) {
                if (source.guard(level, target)) { break; }
            }
        }
        Ward ward = WARDS.get(target);
        ChristmasPhantom source = ward == null ? null : ward.source().get();
        if (source == null || !source.enabled() || !source.ally(target) || source.level() != level || level.getGameTime() >= ward.until()
                || source.distanceToSqr(target) > 24 * 24 || !source.safeEnemy(attacker)) { WARDS.remove(target); return; }
        event.setNewDamage(event.getNewDamage() * .75F);
    }
    public static void release(ChristmasPhantom source) { WARDS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); }
}
