package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomBasicInteractions {
    private PhantomBasicInteractions() {}
    @SubscribeEvent
    public static void bed(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) { return; }
        var level = player.level(); var position = event.getPos();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(position)) > 36
                || !level.hasChunkAt(position)) { return; }
        var state = level.getBlockState(position);
        if (!(state.getBlock() instanceof BedBlock)) { return; }
        if (state.getValue(BedBlock.PART) == BedPart.FOOT) { position = position.relative(state.getValue(BedBlock.FACING)); }
        if (!level.hasChunkAt(position) || !(level.getBlockState(position).getBlock() instanceof BedBlock)) { return; }
        // An individual nearby companion is selected; advanced/flock commands remain on the Staff.
        var nearest = level.getEntitiesOfClass(AbstractPhantom.class, player.getBoundingBox().inflate(16),
                p -> p.isAlive() && p.isOwnedBy(player) && !p.isVehicle() && !p.isPassenger()
                        && p.distanceToSqr(player) <= 256 && player.hasLineOfSight(p)).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (nearest == null) { return; }
        nearest.setHome(GlobalPos.of(level.dimension(), position.immutable()));
        player.sendOverlayMessage(Component.translatable("message.phantomism.basic.home", nearest.getDisplayName()));
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
