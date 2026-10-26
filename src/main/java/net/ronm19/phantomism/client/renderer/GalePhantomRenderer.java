package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.GalePhantom;
import org.jspecify.annotations.Nullable;

public final class GalePhantomRenderer extends PhantomGeoRenderer<GalePhantom> {
    public GalePhantomRenderer(EntityRendererProvider.Context context){super(context,"gale_phantom",PhantomismAccessoryVisibility.NO_ACCESSORIES,new Model());}
    public static Identifier texture(boolean baby,int variant,int visual,boolean eyes){String color=variant==2?"_storm":variant==1?"_deep_sky":"";String state=visual>=3?"_dominion":visual==2?"_combat":visual==1?"_fast":"";return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/entity/gale_phantom"+(baby?"_baby":"")+(eyes?state+"_eyes":color)+".png");}
    @Override public void addRenderData(GalePhantom entity,@Nullable Void ignored,PhantomGeoRenderState state,float partial){super.addRenderData(entity,ignored,state,partial);state.galeVisual=entity.visualState();state.galeVariant=entity.variant();}
    public static final class Model extends GeoModel<GalePhantom> {
        @Override public Identifier getModelResource(GeoRenderState state){return ((PhantomGeoRenderState)state).isBaby?PhantomAnimations.BABY_MODEL:PhantomAnimations.ADULT_MODEL;}
        @Override public Identifier getTextureResource(GeoRenderState state){var presentation=(PhantomGeoRenderState)state;return texture(presentation.isBaby,presentation.galeVariant,presentation.galeVisual,false);}
        @Override public Identifier getAnimationResource(GalePhantom entity){return GalePhantom.ANIMATIONS;}
        @Override public Identifier[] getAnimationResourceFallbacks(GalePhantom entity){return new Identifier[]{PhantomAnimations.RESOURCE};}
    }
}
