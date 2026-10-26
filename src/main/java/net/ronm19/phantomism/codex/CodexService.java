package net.ronm19.phantomism.codex;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class CodexService {
    private static final Map<ServerPlayer, Long> REQUESTS = new WeakHashMap<>();
    private static final Set<ServerPlayer> DIRTY = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> PENDING_OPEN = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    private CodexService() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(CodexPacket.TYPE, CodexPacket.STREAM_CODEC);
        event.registrar("1").playToServer(CodexPacket.Request.TYPE, CodexPacket.Request.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && holdsBook(player)) {
                        send(player, false);
                    }
                }));
    }

    public static boolean holdsBook(ServerPlayer player) {
        return player.getMainHandItem().is(PhantomismContent.PHANTOM_CODEX.get()) || player.getOffhandItem().is(PhantomismContent.PHANTOM_CODEX.get());
    }

    public static void changed(ServerPlayer player) {
        DIRTY.add(player);
    }

    public static void send(ServerPlayer player, boolean open) {
        long now = player.level().getGameTime();
        Long previous = REQUESTS.get(player);
        if (previous != null && now >= previous && now - previous < 20) {
            DIRTY.add(player);
            if (open) {
                PENDING_OPEN.add(player);
            }
            return;
        }
        REQUESTS.put(player, now);
        PENDING_OPEN.remove(player);
        DIRTY.remove(player);
        PacketDistributor.sendToPlayer(player, new CodexPacket(open, player.getUUID().toString(), player.level().dimension().toString(),
                CodexCatalog.JSON.toJson(CodexCatalog.snapshot(player))));
    }

    public static StarterCodexData starter(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage().computeIfAbsent(StarterCodexData.TYPE);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !starter(player).granted(player.getUUID()) && !starter(player).deliver(player)) {
            player.sendSystemMessage(Component.translatable("screen.phantomism.codex.pending"));
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        starter(player).deliver(player);
        if (DIRTY.remove(player)) {
            boolean open = PENDING_OPEN.remove(player);
            if (holdsBook(player)) {
                send(player, open);
            }
        }
    }
}
