package net.ronm19.phantomism.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.ronm19.phantomism.client.gui.PhantomDiscoveryIcons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), cancellable = true)
    private void phantomism$extractDiscoveryIcon(LivingEntity owner, Level level, ItemStack stack, int iconX, int iconY,
            int seed, CallbackInfo callbackInfo) {
        if (PhantomDiscoveryIcons.extract((GuiGraphicsExtractor) (Object) this, stack, iconX, iconY)) {
            callbackInfo.cancel();
        }
    }
}
