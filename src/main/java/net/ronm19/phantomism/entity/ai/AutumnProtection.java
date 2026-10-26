package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.tags.DamageTypeTags;
import net.ronm19.phantomism.entity.AutumnPhantom;

@EventBusSubscriber(modid = "phantomism")
public final class AutumnProtection {
    private record Ward(WeakReference<AutumnPhantom> source, long until, float reduction, double radius) {}
    private static final Map<LivingEntity, Ward> WARDS = new WeakHashMap<>();
    private AutumnProtection() {}
    private static boolean valid(LivingEntity target, Ward ward) { AutumnPhantom source = ward.source.get(); return source != null && source.enabled() && source.recipient(target) && source.distanceToSqr(target) <= ward.radius * ward.radius && target.level().getGameTime() < ward.until; }
    public static void ward(AutumnPhantom source, LivingEntity target, float reduction, int duration, double radius) { if (!source.recipient(target) || !source.enabled() || WARDS.size() >= 256 && !WARDS.containsKey(target)) { return; } Ward current = WARDS.get(target); if (current == null || !valid(target, current) || current.reduction <= reduction) { WARDS.put(target, new Ward(new WeakReference<>(source), target.level().getGameTime() + Math.clamp(duration, 1, 160), Math.clamp(reduction, 0, .25F), Math.clamp(radius, 1, 14))); } }
    public static void clear(AutumnPhantom source) { WARDS.entrySet().removeIf(entry -> entry.getValue().source.get() == source || !valid(entry.getKey(), entry.getValue())); }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) { Ward ward = WARDS.get(event.getEntity()); if (ward == null) { return; } if (!valid(event.getEntity(), ward)) { WARDS.remove(event.getEntity()); return; } if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { event.setNewDamage(event.getNewDamage() * (1 - ward.reduction)); } }
}
