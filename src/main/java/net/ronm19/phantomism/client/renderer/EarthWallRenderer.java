package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.EarthWall;

public final class EarthWallRenderer extends EntityRenderer<EarthWall, BlockDisplayEntityRenderState> {
    private final BlockRenderer delegate;

    public EarthWallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.delegate = new BlockRenderer(context);
    }

    @Override
    public BlockDisplayEntityRenderState createRenderState() {
        return this.delegate.createRenderState();
    }

    @Override
    public void extractRenderState(EarthWall wall, BlockDisplayEntityRenderState state, float partialTicks) {
        this.delegate.extractRenderState(wall, state, partialTicks);
    }

    @Override
    public void submit(BlockDisplayEntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        this.delegate.submit(state, poses, collector, camera);
    }

    @Override
    public boolean shouldRender(EarthWall wall, Frustum frustum, double cameraX, double cameraY, double cameraZ, float partialTicks) {
        return this.delegate.shouldRender(wall, frustum, cameraX, cameraY, cameraZ, partialTicks);
    }

    private static final class BlockRenderer extends DisplayRenderer.BlockDisplayRenderer {
        private BlockRenderer(EntityRendererProvider.Context context) {
            super(context);
        }
    }
}
