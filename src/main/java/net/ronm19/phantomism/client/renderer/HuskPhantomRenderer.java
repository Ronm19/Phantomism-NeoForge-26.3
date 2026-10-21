package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.HuskPhantom;

public final class HuskPhantomRenderer extends PhantomGeoRenderer<HuskPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public HuskPhantomRenderer(EntityRendererProvider.Context context) { super(context, "husk_phantom", ACCESSORIES); }
}
