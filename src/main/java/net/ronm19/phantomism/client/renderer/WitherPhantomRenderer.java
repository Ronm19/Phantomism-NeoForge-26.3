package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.WitherPhantom;

public final class WitherPhantomRenderer extends PhantomGeoRenderer<WitherPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public WitherPhantomRenderer(EntityRendererProvider.Context context) { super(context, "wither_phantom", ACCESSORIES); }
}
