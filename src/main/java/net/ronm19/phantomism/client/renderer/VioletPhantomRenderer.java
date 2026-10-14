package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.VioletPhantom;

public final class VioletPhantomRenderer extends PhantomGeoRenderer<VioletPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, false, true, false, false, true, true);

    public VioletPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "violet_phantom", ACCESSORIES);
    }
}
