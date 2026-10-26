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
import net.ronm19.phantomism.entity.LunarPhantom;
import org.jspecify.annotations.Nullable;

public final class LunarPhantomRenderer extends PhantomGeoRenderer<LunarPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, true, false, false, true, true);
    public LunarPhantomRenderer(EntityRendererProvider.Context context) { super(context, "lunar_phantom", ACCESSORIES, new Model()); withRenderLayer(new Glow(this)); }
    public static Identifier texture(boolean baby, int charge, boolean eyes) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/lunar_phantom" + (baby ? "_baby" : "") + (eyes ? (charge > 0 ? "_charge" + Math.clamp(charge, 0, 3) : "") + "_eyes" : "") + ".png"); }
    public static Identifier glow(boolean baby, int charge) { return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/lunar_phantom" + (baby ? "_baby" : "") + "_glow" + Math.clamp(charge, 0, 3) + ".png"); }
    @Override public void addRenderData(LunarPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.lunarCharge = entity.ascendant() ? 3 : entity.veiled() ? Math.max(2, entity.charge()) : Math.max(entity.getTarget() != null ? 1 : 0, entity.charge()); }
    public static final class Model extends GeoModel<LunarPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState)state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(((PhantomGeoRenderState)state).isBaby, 0, false); }
        @Override public Identifier getAnimationResource(LunarPhantom entity) { return LunarPhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(LunarPhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
    private static final class Glow extends TextureLayerGeoLayer<LunarPhantom, Void, PhantomGeoRenderState> {
        private Glow(LunarPhantomRenderer renderer) { super(renderer, glow(false, 0), RenderTypes::eyes); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return glow(state.isBaby, state.lunarCharge); }
    }
}
