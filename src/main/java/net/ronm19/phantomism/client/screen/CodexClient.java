package net.ronm19.phantomism.client.screen;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.codex.CodexCatalog;
import net.ronm19.phantomism.codex.CodexPacket;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class CodexClient {
    private CodexClient() {
    }

    @SubscribeEvent
    public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(CodexPacket.TYPE, (packet, context) -> context.enqueueWork(() -> accept(packet)));
    }

    public static void accept(CodexPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !packet.player().equals(client.player.getUUID().toString())
                || !packet.dimension().equals(client.level.dimension().toString())) {
            return;
        }
        try {
            if (packet.data().length() > CodexCatalog.MAX_DATA) { return; }
            CodexCatalog.Snapshot snapshot = CodexCatalog.JSON.fromJson(packet.data(), CodexCatalog.Snapshot.class);
            if (!CodexCatalog.valid(snapshot)) {
                return;
            }
            if (client.gui.screen() instanceof PhantomCodexScreen screen) {
                screen.update(snapshot);
            } else if (packet.open()) {
                client.setScreenAndShow(new PhantomCodexScreen(snapshot));
            }
        } catch (RuntimeException exception) {
            Phantomism.LOGGER.warn("Ignoring invalid Codex snapshot", exception);
        }
    }
}
