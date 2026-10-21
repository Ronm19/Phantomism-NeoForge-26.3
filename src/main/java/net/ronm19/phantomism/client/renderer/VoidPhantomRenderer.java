package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.VoidPhantom;

public final class VoidPhantomRenderer extends PhantomGeoRenderer<VoidPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES=PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public VoidPhantomRenderer(EntityRendererProvider.Context context){super(context,"void_phantom",ACCESSORIES);}
}
