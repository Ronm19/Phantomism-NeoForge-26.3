package net.ronm19.phantomism.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.phantomism.entity.LunarPhantom;

@EventBusSubscriber(modid = "phantomism")
public final class LunarVeil {
    private LunarVeil() {}
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void protect(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        LunarPhantom source = event.getEntity() instanceof LunarPhantom lunar ? lunar : event.getEntity().getVehicle() instanceof LunarPhantom mount && mount.isOwnedBy(event.getEntity()) ? mount : null;
        if (source == null || !source.enabled()) { return; }
        if (!source.veiled() && event.getSource().getEntity() instanceof LivingEntity attacker && source.validTarget(attacker) && (event.getAmount() >= 4 || event.getEntity().getHealth() < event.getEntity().getMaxHealth() * .4F)) { source.veil(level); }
        if (source.veiled()) { event.setAmount(event.getAmount() * .7F); }
        else if (source.rushTicks() > 0) { event.setAmount(event.getAmount() * .85F); }
    }
}
