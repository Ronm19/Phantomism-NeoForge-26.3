package net.ronm19.phantomism.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.phantomism.entity.InfernalPhantom;

@EventBusSubscriber(modid = "phantomism")
public final class InfernalCinderGuard {
    private InfernalCinderGuard() {}
    @SubscribeEvent(priority = EventPriority.LOW) public static void protect(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        var target = event.getEntity();
        var sources = level.getEntitiesOfClass(InfernalPhantom.class, target.getBoundingBox().inflate(10), source -> source.enabled() && source.recipient(target) && source.distanceToSqr(target) <= 100).stream().sorted(java.util.Comparator.comparingDouble(source -> source.distanceToSqr(target))).limit(8).toList();
        for (var source : sources) {
            if (!source.guarded() && event.getSource().getEntity() instanceof LivingEntity attacker && source.validTarget(attacker) && (event.getAmount() >= 4 || target.getHealth() <= target.getMaxHealth() * .4F)) { source.cinderGuard(level); }
            if (source.guarded()) { event.setAmount(event.getAmount() * .65F); return; }
        }
    }
}
