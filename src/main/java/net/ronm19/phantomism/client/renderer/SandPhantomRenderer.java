package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.SandPhantom;

public final class SandPhantomRenderer extends PhantomGeoRenderer<SandPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, true, false, false, false, true, true);

    public SandPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "sand_phantom", ACCESSORIES);
    }
}
