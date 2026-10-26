package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.PlaguePhantom;
import org.jspecify.annotations.Nullable;

public final class PlaguePhantomRenderer extends PhantomGeoRenderer<PlaguePhantom> {
    public PlaguePhantomRenderer(EntityRendererProvider.Context context){super(context,"plague_phantom",PhantomismAccessoryVisibility.NO_ACCESSORIES);}
    public static Identifier texture(boolean baby,int visual,boolean eyes){String state=visual==3?"_pandemic":visual==2?"_spreading":visual==1?"_combat":"";return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/entity/plague_phantom"+(baby?"_baby":"")+(eyes?state+"_eyes":"")+".png");}
    @Override public void addRenderData(PlaguePhantom entity,@Nullable Void ignored,PhantomGeoRenderState state,float partial){super.addRenderData(entity,ignored,state,partial);state.plagueVisual=entity.visualState();}
}
