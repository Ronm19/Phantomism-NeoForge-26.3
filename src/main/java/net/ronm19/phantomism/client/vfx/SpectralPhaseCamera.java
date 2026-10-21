package net.ronm19.phantomism.client.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockScreenEffectEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SpectralPhantom;
import net.minecraft.world.entity.player.Input;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class SpectralPhaseCamera {
    private static int requestedMount = -1;
    private SpectralPhaseCamera() {}
    @SubscribeEvent public static void overlay(ExtractBlockScreenEffectEvent event) {
        if (event.getOverlayType() == ExtractBlockScreenEffectEvent.OverlayType.BLOCK && event.getPlayer().getVehicle() instanceof SpectralPhantom source && source.isPhasing()) { event.setCanceled(true); }
    }
    public static Input withoutDismount(Input input) { return new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), false, input.sprint()); }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        Input input = event.getInput().keyPresses;
        if (event.getEntity().getVehicle() instanceof SpectralPhantom source && source.isPhasing() && source.getControllingPassenger() == event.getEntity()) {
            if (input.shift() && requestedMount != source.getId()) {
                requestedMount = source.getId(); ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSpectralDismountPayload(source.getId()));
            } else if (!input.shift()) { requestedMount = -1; }
            event.getInput().keyPresses = withoutDismount(input);
        } else { requestedMount = -1; }
    }
}
