package net.ronm19.phantomism.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.ronm19.phantomism.network.CreatorRevealPacket;
import net.ronm19.phantomism.progression.CreatorRevelation;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid="phantomism",value=Dist.CLIENT)
public final class CreatorIdentityClient {
    private static CreatorRevealPacket state;
    static { CreatorRevelation.localViewer(CreatorIdentityClient::revealed); }
    private CreatorIdentityClient(){}
    public static boolean revealed(){
        var client=Minecraft.getInstance();return state!=null&&client.player!=null&&client.level!=null&&state.revealed()
                &&state.player().equals(client.player.getUUID().toString())&&state.dimension().equals(client.level.dimension().toString());
    }
    public static Component name(){return CreatorRevelation.name(revealed());}
    public static void accept(CreatorRevealPacket packet){
        var player=Minecraft.getInstance().player;
        if(player==null||packet.player().equals(player.getUUID().toString())){state=packet;}
    }
    @SubscribeEvent public static void payload(RegisterClientPayloadHandlersEvent event){event.register(CreatorRevealPacket.TYPE,(packet,context)->context.enqueueWork(()->accept(packet)));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){state=null;}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        if(event.getItemStack().is(PhantomismContent.CREATOR_PHANTOM_SPAWN_EGG.get())&&!event.getToolTip().isEmpty()){
            event.getToolTip().set(0,Component.empty().append(name()).append(" ").append(Component.translatable("item.phantomism.creator_phantom_spawn_egg.suffix")));
        }
    }
}
