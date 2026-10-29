package net.ronm19.phantomism.client.vfx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.ronm19.phantomism.network.CreatorBeamPacket;
@EventBusSubscriber(modid="phantomism",value=Dist.CLIENT)
public final class CreatorBeamManager {
    private record State(CreatorBeamPacket packet,long at){}
    private static final Map<UUID,State> ACTIVE=new HashMap<>();
    private static ClientLevel world;
    private CreatorBeamManager(){}
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event){event.register(CreatorBeamPacket.TYPE,(packet,context)->context.enqueueWork(()->accept(packet)));}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){var current=Minecraft.getInstance().level;if(current!=world){ACTIVE.clear();world=current;}if(world!=null){ACTIVE.entrySet().removeIf(e->world.getGameTime()-e.getValue().at()>=e.getValue().packet().remaining()||world.getEntity(e.getValue().packet().casterId())==null);}}
    public static void accept(CreatorBeamPacket packet){
        var current=Minecraft.getInstance().level;if(current!=world){ACTIVE.clear();world=current;}
        if(current==null||!current.dimension().toString().equals(packet.dimension())){return;}
        var source=current.getEntity(packet.casterId());if(!(source instanceof net.ronm19.phantomism.entity.CreatorPhantom)||!source.getUUID().equals(packet.caster())){return;}
        if(packet.remaining()==0){
            ACTIVE.remove(packet.caster());
            var ended=VfxManager.find(packet.caster(),net.ronm19.phantomism.vfx.CreatorVfx.BEAM);
            if(ended!=null&&ended.packet.session().equals(packet.session())){ended.remove();}
            return;
        }
        if(ACTIVE.size()>=32&&!ACTIVE.containsKey(packet.caster())){return;}
        ACTIVE.put(packet.caster(),new State(packet,current.getGameTime()));
        // A tracking observer may have missed the original start packet. The
        // authoritative batch is enough to resume the existing visual phase.
        var effect=VfxManager.find(packet.caster(),net.ronm19.phantomism.vfx.CreatorVfx.BEAM);
        if(effect==null||!effect.packet.session().equals(packet.session())){
            VfxManager.accept(new net.ronm19.phantomism.vfx.VfxPacket(packet.dimension(),net.ronm19.phantomism.vfx.CreatorVfx.BEAM,packet.caster(),packet.session(),packet.casterId(),packet.caster(),source.position(),net.minecraft.world.phys.Vec3.ZERO,packet.remaining(),2,0xFFFFFF,0x191919,0));
        }
    }
    public static CreatorBeamPacket get(UUID caster,UUID session){var state=ACTIVE.get(caster);return world==null||world!=Minecraft.getInstance().level||state==null||!state.packet().session().equals(session)||world.getGameTime()-state.at()>=state.packet().remaining()?null:state.packet();}
}
