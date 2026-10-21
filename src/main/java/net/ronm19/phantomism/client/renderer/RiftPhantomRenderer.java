package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.RiftPhantom;

public final class RiftPhantomRenderer extends PhantomGeoRenderer<RiftPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public RiftPhantomRenderer(EntityRendererProvider.Context context) { super(context, "rift_phantom", ACCESSORIES); }
}
