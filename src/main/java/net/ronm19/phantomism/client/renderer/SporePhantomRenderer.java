package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.SporePhantom;
import org.jspecify.annotations.Nullable;

public final class SporePhantomRenderer extends PhantomGeoRenderer<SporePhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(true, false, false, false, false, true, true);
    public SporePhantomRenderer(EntityRendererProvider.Context context){super(context,"spore_phantom",ACCESSORIES,new Model());}
    public static Identifier texture(boolean baby, int visual, boolean eyes) {
        String state = visual >= 2 ? "_bloom" : visual == 1 ? "_combat" : "";
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/spore_phantom" + (baby ? "_baby" : "") + (eyes ? state + "_eyes" : "") + ".png");
    }
    @Override public void addRenderData(SporePhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) {
        super.addRenderData(entity, ignored, state, partial); state.sporeVisual = entity.visualState();
    }
    public static final class Model extends GeoModel<SporePhantom> {
        @Override public Identifier getModelResource(GeoRenderState state){return ((PhantomGeoRenderState)state).isBaby?PhantomAnimations.BABY_MODEL:PhantomAnimations.ADULT_MODEL;}
        @Override public Identifier getTextureResource(GeoRenderState state){var presentation=(PhantomGeoRenderState)state;return texture(presentation.isBaby,presentation.sporeVisual,false);}
        @Override public Identifier getAnimationResource(SporePhantom entity){return SporePhantom.ANIMATIONS;}
        @Override public Identifier[] getAnimationResourceFallbacks(SporePhantom entity){return new Identifier[]{PhantomAnimations.RESOURCE};}
    }
}
