package net.ronm19.phantomism.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.advancement.PhantomDiscoveryEvents;
import net.ronm19.phantomism.codex.CodexService;
import net.ronm19.phantomism.network.CreatorRevealPacket;

@EventBusSubscriber(modid="phantomism")
public final class CreatorRevelation {
    private static java.util.function.BooleanSupplier localViewer=()->false;
    private CreatorRevelation(){}
    public static void localViewer(java.util.function.BooleanSupplier viewer){localViewer=viewer;}
    public static Component entityName(net.minecraft.world.level.Level level){return name(level.isClientSide()&&localViewer.getAsBoolean());}
    public static Component name(boolean revealed){
        return revealed?Component.translatable("entity.phantomism.creator_phantom.revealed"):
                Component.translatable("entity.phantomism.creator_phantom.mystery").withStyle(ChatFormatting.OBFUSCATED);
    }
    public static boolean revealed(ServerPlayer player){return CreatorBonds.get(player.level().getServer()).revealed(player.getUUID());}
    public static void reveal(ServerPlayer player){
        CreatorBonds.get(player.level().getServer()).reveal(player.getUUID());
        PhantomDiscoveryEvents.discover(player,PhantomDiscovery.CREATOR);CodexService.changed(player);send(player);
    }
    public static void send(ServerPlayer player){
        if(player.connection!=null){PacketDistributor.sendToPlayer(player,new CreatorRevealPacket(player.getUUID().toString(),player.level().dimension().toString(),revealed(player)));}
    }
    private static void restore(ServerPlayer player){
        var bonds=CreatorBonds.get(player.level().getServer());
        // Existing persisted owner bonds are reliable migration evidence.
        if(bonds.active(player.getUUID())!=null){reveal(player);}else{send(player);}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(CreatorRevealPacket.TYPE,CreatorRevealPacket.STREAM_CODEC);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player){restore(player);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player){restore(player);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer player){restore(player);}}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer player&&(player.tickCount==20||player.tickCount==40)){restore(player);}}
}
