package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.EasterPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class EasterSupport {
    private static final Map<LivingEntity, Long> RENEWAL = new WeakHashMap<>();
    private EasterSupport() {}
    public static boolean renewalAvailable(LivingEntity target) { return RENEWAL.getOrDefault(target, Long.MIN_VALUE) <= target.level().getGameTime(); }
    public static boolean claimRenewal(LivingEntity target) {
        if (!renewalAvailable(target)) { return false; }
        RENEWAL.put(target, target.level().getGameTime() + 160); return true;
    }
    @SubscribeEvent
    public static void incoming(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EasterPhantom source) || !(source.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !source.safeEnemy(attacker) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        if (event.getNewDamage() >= 4 || source.getHealth() - event.getNewDamage() <= source.getMaxHealth() * .35F) { source.evade(level, attacker); }
        if (source.evading()) { event.setNewDamage(event.getNewDamage() * .65F); }
    }
}
