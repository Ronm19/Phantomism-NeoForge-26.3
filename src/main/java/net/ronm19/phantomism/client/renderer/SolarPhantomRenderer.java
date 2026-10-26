package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.SolarPhantom;
import org.jspecify.annotations.Nullable;

public final class SolarPhantomRenderer extends PhantomGeoRenderer<SolarPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, true, false, true, true, true);
    public SolarPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "solar_phantom", ACCESSORIES, new Model()); withRenderLayer(new Glow(this));
    }
    public static Identifier texture(boolean baby, int visual, boolean eyes) {
        String state = visual == 3 ? "_supernova" : visual == 2 ? "_sunlit" : visual == 1 ? "_combat" : "";
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/solar_phantom" + (baby ? "_baby" : "") + (eyes ? state + "_eyes" : "") + ".png");
    }
    @Override public void addRenderData(SolarPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) {
        super.addRenderData(entity, ignored, state, partial); state.solarVisual = entity.visualState();
    }
    public static final class Model extends GeoModel<SolarPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState)state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(((PhantomGeoRenderState)state).isBaby, 0, false); }
        @Override public Identifier getAnimationResource(SolarPhantom entity) { return SolarPhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(SolarPhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
    private static final class Glow extends TextureLayerGeoLayer<SolarPhantom, Void, PhantomGeoRenderState> {
        private Glow(SolarPhantomRenderer renderer) { super(renderer, Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/solar_phantom_glow.png"), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/solar_phantom" + (state.isBaby ? "_baby" : "") + "_glow.png"); }
        @Override protected @Nullable RenderType getRenderType(PhantomGeoRenderState state) { return state.solarVisual >= 2 ? super.getRenderType(state) : null; }
    }
}
