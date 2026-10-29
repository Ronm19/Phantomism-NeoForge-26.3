package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.FireworkPhantom;

public final class FireworkPhantomRenderer extends PhantomGeoRenderer<FireworkPhantom> {
    @Override protected int getBlockLightLevel(FireworkPhantom phantom, net.minecraft.core.BlockPos point) {
        return Math.max(super.getBlockLightLevel(phantom, point), net.ronm19.phantomism.client.vfx.FireworkFinaleLight.blockLight(phantom));
    }
    public FireworkPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "firework_phantom", new PhantomismAccessoryVisibility(false, false, false, false, false, true, true));
        withRenderLayer(new StarGlow(this));
    }
    private static final class StarGlow extends TextureLayerGeoLayer<FireworkPhantom, Void, PhantomGeoRenderState> {
        private StarGlow(FireworkPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/firework_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/firework_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}
