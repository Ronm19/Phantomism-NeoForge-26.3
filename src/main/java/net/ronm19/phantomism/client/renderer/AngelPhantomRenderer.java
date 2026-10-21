package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.AngelPhantom;

public final class AngelPhantomRenderer extends PhantomGeoRenderer<AngelPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, false, false, true, true, true, false);
    public AngelPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "angel_phantom", ACCESSORIES);
        withRenderLayer(new AngelHaloLayer(this));
    }
}
