package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.ToxicPhantom;

public final class ToxicPhantomRenderer extends PhantomGeoRenderer<ToxicPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, false, false, false, false, true, true);

    public ToxicPhantomRenderer(EntityRendererProvider.Context context) { super(context, "toxic_phantom", ACCESSORIES); }
}
