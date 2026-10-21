package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.VampirePhantom;

public final class VampirePhantomRenderer extends PhantomGeoRenderer<VampirePhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES.withFangs(true);
    public VampirePhantomRenderer(EntityRendererProvider.Context context) { super(context, "vampire_phantom", ACCESSORIES); }
}
