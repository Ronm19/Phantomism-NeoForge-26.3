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
import net.ronm19.phantomism.entity.AutumnPhantom;
import org.jspecify.annotations.Nullable;

public final class AutumnPhantomRenderer extends PhantomGeoRenderer<AutumnPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, true, false, false, true, true);
    public AutumnPhantomRenderer(EntityRendererProvider.Context context) { super(context, "autumn_phantom", ACCESSORIES, new Model()); withRenderLayer(new Glow(this)); }
    public static Identifier texture(boolean baby, int visual, boolean eyes) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/autumn_phantom" + (baby ? "_baby" : "") + (eyes ? (visual > 0 ? "_visual" + Math.clamp(visual, 0, 3) : "") + "_eyes" : "") + ".png"); }
    public static Identifier glow(boolean baby, int visual) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/autumn_phantom" + (baby ? "_baby" : "") + "_glow" + Math.clamp(visual, 0, 3) + ".png"); }
    @Override public void addRenderData(AutumnPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.autumnVisual = entity.visual(); }
    public static final class Model extends GeoModel<AutumnPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState)state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(((PhantomGeoRenderState)state).isBaby, 0, false); }
        @Override public Identifier getAnimationResource(AutumnPhantom entity) { return AutumnPhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(AutumnPhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
    private static final class Glow extends TextureLayerGeoLayer<AutumnPhantom, Void, PhantomGeoRenderState> {
        private Glow(AutumnPhantomRenderer renderer) { super(renderer, glow(false, 0), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return glow(state.isBaby, state.autumnVisual); }
    }
}
