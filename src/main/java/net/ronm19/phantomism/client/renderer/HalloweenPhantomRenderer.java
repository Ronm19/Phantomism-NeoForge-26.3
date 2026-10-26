package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.HalloweenPhantom;
import org.jspecify.annotations.Nullable;

public final class HalloweenPhantomRenderer extends PhantomGeoRenderer<HalloweenPhantom> {
    public HalloweenPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "halloween_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES);
        withRenderLayer(new CandleGlow(this));
    }
    @Override public void addRenderData(HalloweenPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partialTick) {
        super.addRenderData(entity, ignored, state, partialTick);
        state.halloweenVisual = entity.eyesHaunted() ? 1 : 0;
    }
    private static final class CandleGlow extends TextureLayerGeoLayer<HalloweenPhantom, Void, PhantomGeoRenderState> {
        private CandleGlow(HalloweenPhantomRenderer renderer) {
            super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/halloween_phantom_glow.png"), RenderTypes::eyes);
        }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) {
            return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/halloween_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png");
        }
        @Override public void submitRenderTask(RenderPassInfo<PhantomGeoRenderState> pass, SubmitNodeCollector collector) {
            if (pass.renderState().halloweenVisual > 0) { super.submitRenderTask(pass, collector); }
        }
    }
}
