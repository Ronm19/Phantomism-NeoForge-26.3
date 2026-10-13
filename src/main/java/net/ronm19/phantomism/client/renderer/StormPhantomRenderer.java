package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.StormPhantom;

public final class StormPhantomRenderer extends PhantomGeoRenderer<StormPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;

    public StormPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "storm_phantom", ACCESSORIES);
    }
}
