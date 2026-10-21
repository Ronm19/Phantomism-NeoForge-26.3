package net.ronm19.phantomism.mixin;

import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.SpectralPhantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpectralVehicleMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void phantomism$validatePhaseMove(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        if (!player.level().getServer().isSameThread()) { return; }
        if (player.getVehicle() instanceof SpectralPhantom source && source.isPhasing() && source.getControllingPassenger() == player
                && !source.passage().allowsMove(packet.movingTo().position())) {
            player.connection.send(ClientboundMoveVehiclePacket.fromEntity(source)); callback.cancel();
        }
    }
    @Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void phantomism$allowControlledPhase(LevelReader level, Entity entity, AABB previous, double horizontal, double height, double depth, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof SpectralPhantom source && source.isPhasing() && source.passage().allowsMove(new Vec3(horizontal, height, depth))) { callback.setReturnValue(false); }
    }
}
