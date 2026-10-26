package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.ChristmasPhantom;

public final class ChristmasPhantomRenderer extends PhantomGeoRenderer<ChristmasPhantom> {
    public ChristmasPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "christmas_phantom", new PhantomismAccessoryVisibility(false, false, true, false, false, true, true));
        withRenderLayer(new GoldGlow(this));
    }
    private static final class GoldGlow extends TextureLayerGeoLayer<ChristmasPhantom, Void, PhantomGeoRenderState> {
        private GoldGlow(ChristmasPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/christmas_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/christmas_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}
