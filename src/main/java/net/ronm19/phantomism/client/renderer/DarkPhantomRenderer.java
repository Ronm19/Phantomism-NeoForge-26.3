package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.DarkPhantom;

public final class DarkPhantomRenderer extends PhantomGeoRenderer<DarkPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public DarkPhantomRenderer(EntityRendererProvider.Context context) { super(context, "dark_phantom", ACCESSORIES); }
}
