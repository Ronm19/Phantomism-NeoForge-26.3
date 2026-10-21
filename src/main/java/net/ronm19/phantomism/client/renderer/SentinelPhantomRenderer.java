package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.SentinelPhantom;

public final class SentinelPhantomRenderer extends PhantomGeoRenderer<SentinelPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public SentinelPhantomRenderer(EntityRendererProvider.Context context) { super(context, "sentinel_phantom", ACCESSORIES); }
}
