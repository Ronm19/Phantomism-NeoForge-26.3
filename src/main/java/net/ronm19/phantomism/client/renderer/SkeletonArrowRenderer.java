package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.entity.SkeletonArrow;

public final class SkeletonArrowRenderer extends ArrowRenderer<SkeletonArrow, ArrowRenderState> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/skeleton_arrow.png");
    public SkeletonArrowRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ArrowRenderState createRenderState() { return new ArrowRenderState(); }
    @Override protected Identifier getTextureLocation(ArrowRenderState state) { return TEXTURE; }
}
