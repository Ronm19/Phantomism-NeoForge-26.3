package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.WaterPhantom;

public final class WaterPhantomRenderer extends PhantomGeoRenderer<WaterPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;

    public WaterPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "water_phantom", ACCESSORIES);
    }
}
