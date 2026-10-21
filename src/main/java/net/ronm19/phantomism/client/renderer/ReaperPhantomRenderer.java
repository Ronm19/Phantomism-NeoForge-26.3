package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.ReaperPhantom;

public final class ReaperPhantomRenderer extends PhantomGeoRenderer<ReaperPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES=PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public ReaperPhantomRenderer(EntityRendererProvider.Context context){super(context,"reaper_phantom",ACCESSORIES);}
}
