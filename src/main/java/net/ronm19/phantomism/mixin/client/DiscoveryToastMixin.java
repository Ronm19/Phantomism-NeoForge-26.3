package net.ronm19.phantomism.mixin.client;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementToast.class)
public abstract class DiscoveryToastMixin {
    @Shadow @Final private AdvancementHolder advancement;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void phantomism$discovery(GuiGraphicsExtractor graphics, Font font, long visibleTime, CallbackInfo callback) {
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            if (!discovery.advancementId().equals(advancement.id())) {
                continue;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("toast/advancement"), 0, 0, 160, 32);
            graphics.text(font, Component.translatable("screen.phantomism.codex.discovered"), 30, 7, 0xFFE9C66A, false);
            graphics.text(font, font.plainSubstrByWidth(discovery.entityType().getDescription().getString(), 125), 30, 18, 0xFFFFFFFF, false);
            graphics.fakeItem(discovery.icon().create(), 8, 8);
            callback.cancel();
            return;
        }
    }
}
