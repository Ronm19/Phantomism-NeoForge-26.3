package net.ronm19.phantomism.advancement;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.codex.CodexService;
import net.ronm19.phantomism.entity.AbstractPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomDiscoveryEvents {
    public static final double DISCOVERY_RANGE = 16.0D;

    private PhantomDiscoveryEvents() {
    }

    public static boolean discover(ServerPlayer player, PhantomDiscovery discovery) {
        if (discovery == PhantomDiscovery.CREATOR && !net.ronm19.phantomism.progression.CreatorRevelation.revealed(player)) { return false; }
        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }
        var advancements = player.level().getServer().getAdvancements();
        AdvancementHolder advancement = advancements.get(discovery.advancementId());
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return false;
        }
        AdvancementHolder root = advancements.get(PhantomDiscovery.ROOT_ID);
        if (root != null) {
            player.getAdvancements().award(root, PhantomDiscovery.CRITERION);
        }
        boolean awarded = player.getAdvancements().award(advancement, PhantomDiscovery.CRITERION);
        if (awarded) {
            CodexService.changed(player);
        }
        return awarded;
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof AbstractPhantom phantom
                && phantom.isAlive() && phantom.level() == player.level() && player.distanceToSqr(phantom) <= 64) {
            for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
                if (phantom.getType() == discovery.entityType()) {
                    discover(player, discovery);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive() || player.tickCount % 20 != 0) {
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
            if (player.distanceToSqr(phantom) > DISCOVERY_RANGE * DISCOVERY_RANGE) {
                continue;
            }
            for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
                if (undiscovered.containsKey(discovery) && phantom.getType() == discovery.entityType()) {
                    discover(player, discovery);
                    undiscovered.remove(discovery);
                    break;
                }
            }
            if (undiscovered.isEmpty()) {
                break;
            }
        }
    }
}
