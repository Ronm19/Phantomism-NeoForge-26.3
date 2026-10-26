package net.ronm19.phantomism.client.renderer;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.CrystalPhantom;
import org.jspecify.annotations.Nullable;

public final class CrystalPhantomRenderer extends PhantomGeoRenderer<CrystalPhantom> {
    public CrystalPhantomRenderer(EntityRendererProvider.Context context){super(context,"crystal_phantom",PhantomismAccessoryVisibility.NO_ACCESSORIES,new Model());}
    public static Identifier texture(boolean baby,int variant,int visual,boolean eyes){String color=variant==2?"_diamond":variant==1?"_amethyst":"";String state=visual>=2?"_barrier":visual==1?"_combat":"";return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/entity/crystal_phantom"+(baby?"_baby":"")+(eyes?state+"_eyes":color)+".png");}
    @Override public void addRenderData(CrystalPhantom entity,@Nullable Void ignored,PhantomGeoRenderState state,float partial){super.addRenderData(entity,ignored,state,partial);state.crystalVisual=entity.visualState();state.crystalVariant=entity.variant();}
    public static final class Model extends GeoModel<CrystalPhantom> {
        @Override public Identifier getModelResource(GeoRenderState state){return ((PhantomGeoRenderState)state).isBaby?PhantomAnimations.BABY_MODEL:PhantomAnimations.ADULT_MODEL;}
        @Override public Identifier getTextureResource(GeoRenderState state){var presentation=(PhantomGeoRenderState)state;return texture(presentation.isBaby,presentation.crystalVariant,presentation.crystalVisual,false);}
        @Override public Identifier getAnimationResource(CrystalPhantom entity){return CrystalPhantom.ANIMATIONS;}
        @Override public Identifier[] getAnimationResourceFallbacks(CrystalPhantom entity){return new Identifier[]{PhantomAnimations.RESOURCE};}
    }
}
