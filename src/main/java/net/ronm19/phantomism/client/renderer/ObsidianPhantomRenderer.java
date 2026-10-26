package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.ObsidianPhantom;
import org.jspecify.annotations.Nullable;

public final class ObsidianPhantomRenderer extends PhantomGeoRenderer<ObsidianPhantom> {
    public ObsidianPhantomRenderer(EntityRendererProvider.Context context){super(context,"obsidian_phantom",PhantomismAccessoryVisibility.NO_ACCESSORIES,new Model());}
    public static Identifier texture(boolean baby,int variant,int visual,boolean eyes){String color=variant==2?"_shadow":variant==1?"_volcanic":"";String state=visual>=3?"_citadel":visual==2?"_bastion":visual==1?"_combat":"";return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/entity/obsidian_phantom"+(baby?"_baby":"")+(eyes?state+"_eyes":color)+".png");}
    @Override public void addRenderData(ObsidianPhantom entity,@Nullable Void ignored,PhantomGeoRenderState state,float partial){super.addRenderData(entity,ignored,state,partial);state.obsidianVisual=entity.visualState();state.obsidianVariant=entity.variant();}
    public static final class Model extends GeoModel<ObsidianPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state){return ((PhantomGeoRenderState)state).isBaby?PhantomAnimations.BABY_MODEL:PhantomAnimations.ADULT_MODEL;}
        @Override public Identifier getTextureResource(GeoRenderState state){var presentation=(PhantomGeoRenderState)state;return texture(presentation.isBaby,presentation.obsidianVariant,presentation.obsidianVisual,false);}
        @Override public Identifier getAnimationResource(ObsidianPhantom entity){return ObsidianPhantom.ANIMATIONS;}
        @Override public Identifier[] getAnimationResourceFallbacks(ObsidianPhantom entity){return new Identifier[]{PhantomAnimations.RESOURCE};}
    }
}
