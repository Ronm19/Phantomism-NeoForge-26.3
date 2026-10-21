package net.ronm19.phantomism.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.SpectralPhantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SpectralPlayerSaveMixin {
    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void phantomism$safeSavedRider(ValueOutput output, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player && player.getVehicle() instanceof SpectralPhantom source && source.isPhasing()) {
            Vec3 destination = source.passage().savedPosition(player);
            if (destination != null) { output.store("Pos", Vec3.CODEC, destination); output.store("Motion", Vec3.CODEC, Vec3.ZERO); }
        }
    }
}
