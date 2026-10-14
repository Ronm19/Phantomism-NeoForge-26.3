package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.SculkPhantom;

public final class SculkPhantomRenderer extends PhantomGeoRenderer<SculkPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, false, true, false, true, true);

    public SculkPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "sculk_phantom", ACCESSORIES);
    }
}
