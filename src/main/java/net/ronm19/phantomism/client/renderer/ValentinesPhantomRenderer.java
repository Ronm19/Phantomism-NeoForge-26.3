package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.client.vfx.ValentinesEffects;
import net.ronm19.phantomism.entity.ValentinesPhantom;

public final class ValentinesPhantomRenderer extends PhantomGeoRenderer<ValentinesPhantom> {
    public ValentinesPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "valentines_phantom", new PhantomismAccessoryVisibility(false, false, false, false, false, true, true));
        withRenderLayer(new HeartGlow(this));
    }
    @Override protected int getBlockLightLevel(ValentinesPhantom phantom, BlockPos point) {
        return Math.max(super.getBlockLightLevel(phantom, point), Math.clamp(Math.round(ValentinesEffects.light(phantom, 0) * 12), 0, 10));
    }
    private static final class HeartGlow extends TextureLayerGeoLayer<ValentinesPhantom, Void, PhantomGeoRenderState> {
        HeartGlow(ValentinesPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/valentines_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/valentines_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}
