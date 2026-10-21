package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.EchoPhantom;

public final class EchoPhantomRenderer extends PhantomGeoRenderer<EchoPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public EchoPhantomRenderer(EntityRendererProvider.Context context) { super(context, "echo_phantom", ACCESSORIES); }
}
