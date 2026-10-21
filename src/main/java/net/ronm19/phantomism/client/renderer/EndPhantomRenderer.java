package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.EndPhantom;

public final class EndPhantomRenderer extends PhantomGeoRenderer<EndPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;

    public EndPhantomRenderer(EntityRendererProvider.Context context) { super(context, "end_phantom", ACCESSORIES); }
}
