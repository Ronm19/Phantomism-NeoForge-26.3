package net.ronm19.phantomism.advancement;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomDiscoveryEvents {
    private static final double DISCOVERY_RANGE = 32.0D;

    private PhantomDiscoveryEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 20 != 0) {
            return;
        }

        Map<PhantomDiscovery, AdvancementHolder> undiscovered = new EnumMap<>(PhantomDiscovery.class);
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            AdvancementHolder advancement = player.level().getServer().getAdvancements().get(discovery.advancementId());
            if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                undiscovered.put(discovery, advancement);
            }
        }
        if (undiscovered.isEmpty()) {
            return;
        }

        for (AbstractPhantom phantom : player.level().getEntitiesOfClass(AbstractPhantom.class,
                player.getBoundingBox().inflate(DISCOVERY_RANGE), AbstractPhantom::isAlive)) {
            if (player.distanceToSqr(phantom) > DISCOVERY_RANGE * DISCOVERY_RANGE || !player.hasLineOfSight(phantom)) {
                continue;
            }
            for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
                AdvancementHolder advancement = undiscovered.get(discovery);
                if (advancement != null && phantom.getType() == discovery.entityType()) {
                    AdvancementHolder root = player.level().getServer().getAdvancements().get(PhantomDiscovery.ROOT_ID);
                    if (root != null) {
                        player.getAdvancements().award(root, PhantomDiscovery.CRITERION);
                    }
                    player.getAdvancements().award(advancement, PhantomDiscovery.CRITERION);
                    undiscovered.remove(discovery);
                }
            }
            if (undiscovered.isEmpty()) {
                break;
            }
        }
    }
}
