package net.ronm19.phantomism.entity.ai;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SculkPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class SculkCombatEvents {
    private SculkCombatEvents() { }

    @SubscribeEvent
    public static void onSonicAttack(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getSource().is(DamageTypes.SONIC_BOOM)
                && event.getSource().getEntity() instanceof Warden warden
                && !warden.level().getEntitiesOfClass(SculkPhantom.class, warden.getBoundingBox().inflate(11.0D),
                        phantom -> phantom.isSilencing(warden)).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
