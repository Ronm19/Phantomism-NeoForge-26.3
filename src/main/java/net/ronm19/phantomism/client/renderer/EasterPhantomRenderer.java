package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.EasterPhantom;

public final class EasterPhantomRenderer extends PhantomGeoRenderer<EasterPhantom> {
    public EasterPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "easter_phantom", new PhantomismAccessoryVisibility(false, false, true, false, false, true, true));
        withRenderLayer(new SpringGlow(this));
    }
    private static final class SpringGlow extends TextureLayerGeoLayer<EasterPhantom, Void, PhantomGeoRenderState> {
        private SpringGlow(EasterPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/easter_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/easter_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}


