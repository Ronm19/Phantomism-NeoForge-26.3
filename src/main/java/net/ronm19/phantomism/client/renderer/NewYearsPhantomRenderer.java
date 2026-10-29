package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.client.vfx.NewYearsEffects;
import net.ronm19.phantomism.entity.NewYearsPhantom;

public final class NewYearsPhantomRenderer extends PhantomGeoRenderer<NewYearsPhantom> {
    public NewYearsPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "new_years_phantom", new PhantomismAccessoryVisibility(false, false, false, false, false, true, true));
        withRenderLayer(new ClockGlow(this));
    }
    @Override protected int getBlockLightLevel(NewYearsPhantom phantom, BlockPos point) {
        return Math.max(super.getBlockLightLevel(phantom, point), Math.clamp(Math.round(NewYearsEffects.light(phantom, 0) * 12), 0, 10));
    }
    private static final class ClockGlow extends TextureLayerGeoLayer<NewYearsPhantom, Void, PhantomGeoRenderState> {
        ClockGlow(NewYearsPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/new_years_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/new_years_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
    }
}
