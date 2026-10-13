package net.ronm19.phantomism.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.ronm19.phantomism.entity.StormPhantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Redirect(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean phantomism$absorbNaturalLightning(ServerLevel level, Entity entity) {
        StormPhantom absorber = entity instanceof LightningBolt lightning
                ? StormPhantom.findNaturalLightningAbsorber(level, lightning) : null;
        if (absorber != null && entity instanceof LightningBolt lightning) {
            lightning.setPos(absorber.position().add(0.0D, absorber.getBbHeight() * 0.5D, 0.0D));
            lightning.setVisualOnly(true);
        }
        boolean added = level.addFreshEntity(entity);
        if (added && absorber != null && entity instanceof LightningBolt lightning) {
            absorber.absorbNaturalLightning(lightning);
        }
        return added;
    }
}
