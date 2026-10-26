package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.IllagerPhantom;
import org.jspecify.annotations.Nullable;

public final class IllagerPhantomRenderer extends PhantomGeoRenderer<IllagerPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public IllagerPhantomRenderer(EntityRendererProvider.Context context) { super(context, "illager_phantom", ACCESSORIES, new Model()); }
    public static Identifier texture(boolean baby, int visual, boolean eyes) {
        String suffix = visual == 2 ? "_conquest" : visual == 1 ? "_combat" : "";
        return Identifier.fromNamespaceAndPath("phantomism", "textures/entity/illager_phantom" + (baby ? "_baby" : "") + (eyes ? suffix + "_eyes" : "") + ".png");
    }
    @Override public void addRenderData(IllagerPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.illagerVisual = entity.visualState(); }
    public static final class Model extends GeoModel<IllagerPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((PhantomGeoRenderState)state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(((PhantomGeoRenderState)state).isBaby, 0, false); }
        @Override public Identifier getAnimationResource(IllagerPhantom entity) { return IllagerPhantom.ANIMATIONS; }
        @Override public Identifier[] getAnimationResourceFallbacks(IllagerPhantom entity) { return new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
}
