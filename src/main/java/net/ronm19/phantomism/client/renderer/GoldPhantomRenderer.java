package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.GoldPhantom;

public final class GoldPhantomRenderer extends PhantomGeoRenderer<GoldPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, false, false, false, false, true, true);

    public GoldPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "gold_phantom", ACCESSORIES);
    }
}
