package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.client.vfx.StPatricksEffects;
import net.ronm19.phantomism.entity.StPatricksPhantom;

public final class StPatricksPhantomRenderer extends PhantomGeoRenderer<StPatricksPhantom> {
    public StPatricksPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "st_patricks_phantom", new PhantomismAccessoryVisibility(false, false, false, false, false, true, true));
        withRenderLayer(new CloverGlow(this));
    }
    @Override protected int getBlockLightLevel(StPatricksPhantom phantom, BlockPos point) {
        return Math.max(super.getBlockLightLevel(phantom, point), Math.clamp(Math.round(StPatricksEffects.light(phantom, 0) * 12), 0, 10));
    }
    private static final class CloverGlow extends TextureLayerGeoLayer<StPatricksPhantom, Void, PhantomGeoRenderState> {
        CloverGlow(StPatricksPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/st_patricks_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/st_patricks_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}
