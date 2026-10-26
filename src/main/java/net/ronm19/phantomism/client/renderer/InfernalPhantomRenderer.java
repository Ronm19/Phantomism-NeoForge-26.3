package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.InfernalPhantom;
import org.jspecify.annotations.Nullable;

public final class InfernalPhantomRenderer extends PhantomGeoRenderer<InfernalPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public InfernalPhantomRenderer(EntityRendererProvider.Context context) { super(context, "infernal_phantom", ACCESSORIES, new Model()); withRenderLayer(new Glow(this)); }
    public static Identifier texture(boolean baby, int heat, boolean eyes) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/infernal_phantom" + (baby ? "_baby" : "") + (eyes ? (heat > 0 ? "_heat" + Math.clamp(heat, 0, 3) : "") + "_eyes" : "") + ".png"); }
    public static Identifier glow(boolean baby, int heat) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/infernal_phantom" + (baby ? "_baby" : "") + "_glow" + Math.clamp(heat, 0, 3) + ".png"); }
    @Override public void addRenderData(InfernalPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.infernalHeat = entity.ascendant() ? 3 : entity.heat(); }
    public static final class Model extends GeoModel<InfernalPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState)state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(((PhantomGeoRenderState)state).isBaby, 0, false); }
        @Override public Identifier getAnimationResource(InfernalPhantom entity) { return InfernalPhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(InfernalPhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
    private static final class Glow extends TextureLayerGeoLayer<InfernalPhantom, Void, PhantomGeoRenderState> {
        private Glow(InfernalPhantomRenderer renderer) { super(renderer, glow(false, 0), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return glow(state.isBaby, state.infernalHeat); }
    }
}
