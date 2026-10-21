package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.SkeletonPhantom;

public final class SkeletonPhantomRenderer extends PhantomGeoRenderer<SkeletonPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public SkeletonPhantomRenderer(EntityRendererProvider.Context context) { super(context, "skeleton_phantom", ACCESSORIES); }
}
