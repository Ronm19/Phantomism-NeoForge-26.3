package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.GravityPhantom;

public final class GravityPhantomRenderer extends PhantomGeoRenderer<GravityPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public GravityPhantomRenderer(EntityRendererProvider.Context context) { super(context, "gravity_phantom", ACCESSORIES); }
}
