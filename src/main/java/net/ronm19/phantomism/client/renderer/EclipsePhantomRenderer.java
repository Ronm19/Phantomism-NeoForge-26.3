package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.EclipsePhantom;
import org.jspecify.annotations.Nullable;

public final class EclipsePhantomRenderer extends PhantomGeoRenderer<EclipsePhantom> {
    public EclipsePhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "eclipse_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES, new Model());
        for (String bone : new String[]{"right_wing", "right_wing_tip"}) { withRenderLayer(new LunarWing(this, bone)); }
    }
    public static Identifier texture(boolean baby, int visual, boolean eyes) {
        String variant = visual == 2 ? "_total" : visual == 1 ? "_lunar" : "";
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/eclipse_phantom" + (baby ? "_baby" : "") + variant + (eyes ? "_eyes" : "") + ".png");
    }
    @Override public void addRenderData(EclipsePhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.eclipseVisual = entity.visualState(); }
    public static Identifier moonTexture(boolean baby, int visual) { return texture(baby, visual, false).withPath(path -> path.replace(".png", "_moon.png")); }
    private static final class LunarWing extends CustomBoneTextureGeoLayer<EclipsePhantom, Void, PhantomGeoRenderState> {
        private LunarWing(EclipsePhantomRenderer renderer, String bone) { super(renderer, bone, moonTexture(false, 0)); }
        @Override public boolean shouldRenderBone(PhantomGeoRenderState state) { return !state.isInvisible && state.accessories.wings(); }
        @Override protected Identifier getTextureResource(PhantomGeoRenderState state) { return moonTexture(state.isBaby, state.eclipseVisual); }
        @Override protected net.minecraft.client.renderer.rendertype.RenderType getRenderType(PhantomGeoRenderState state, Identifier texture) {
            return state.eclipseVisual == 2 ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture) : super.getRenderType(state, texture);
        }
    }
    private static final class Model extends GeoModel<EclipsePhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState) state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { var presentation = (PhantomGeoRenderState) state; return texture(presentation.isBaby, presentation.eclipseVisual, false); }
        @Override public Identifier getAnimationResource(EclipsePhantom entity) { return EclipsePhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(EclipsePhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
}
