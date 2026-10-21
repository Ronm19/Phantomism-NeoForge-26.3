package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.OmenPhantom;

public final class OmenPhantomRenderer extends PhantomGeoRenderer<OmenPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public OmenPhantomRenderer(EntityRendererProvider.Context context) { super(context, "omen_phantom", ACCESSORIES); }
}
