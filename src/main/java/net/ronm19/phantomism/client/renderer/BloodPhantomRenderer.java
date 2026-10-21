package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.BloodPhantom;

public final class BloodPhantomRenderer extends PhantomGeoRenderer<BloodPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public BloodPhantomRenderer(EntityRendererProvider.Context context) { super(context, "blood_phantom", ACCESSORIES); }
}
