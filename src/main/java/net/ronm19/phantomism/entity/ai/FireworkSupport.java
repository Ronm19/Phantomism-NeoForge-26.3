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
import net.ronm19.phantomism.entity.FireworkPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class FireworkSupport {
    private record Ward(WeakReference<FireworkPhantom> source, long expires) {}
    private static final Map<LivingEntity, Ward> WARDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> REST = new WeakHashMap<>();
    private FireworkSupport() {}
    public static boolean protect(FireworkPhantom source, LivingEntity target, int duration) {
        long now = source.level().getGameTime();
        if (!source.enabled() || !source.ally(target) || source.distanceToSqr(target) > 100 || !source.line(source.getEyePosition(), target.getEyePosition())
                || REST.getOrDefault(target, Long.MIN_VALUE) > now) { return false; }
        WARDS.put(target, new Ward(new WeakReference<>(source), now + Math.clamp(duration, 1, 80))); REST.put(target, now + 240); return true;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        boolean explosion = event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (explosion && event.getNewDamage() >= 3 || attacker != null && (event.getNewDamage() >= 6 || target.getHealth() - event.getNewDamage() < target.getMaxHealth() * .3F)) {
            for (FireworkPhantom source : level.getEntitiesOfClass(FireworkPhantom.class, target.getBoundingBox().inflate(10), phantom -> phantom.enabled() && phantom.ally(target) && (attacker == null || phantom.safeEnemy(attacker)))
                    .stream().sorted(Comparator.comparingDouble(phantom -> phantom.distanceToSqr(target))).limit(3).toList()) { if (source.guard(level)) { break; } }
        }
        Ward ward = WARDS.get(target); FireworkPhantom source = ward == null ? null : ward.source().get();
        if (source == null || !source.enabled() || !source.ally(target) || source.level() != level || level.getGameTime() >= ward.expires()
                || source.distanceToSqr(target) > 144 || !explosion && (attacker == null || !source.safeEnemy(attacker))) { WARDS.remove(target); return; }
        event.setNewDamage(event.getNewDamage() * (explosion ? .4F : .8F));
    }
    public static void release(FireworkPhantom source) { WARDS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); }
}
