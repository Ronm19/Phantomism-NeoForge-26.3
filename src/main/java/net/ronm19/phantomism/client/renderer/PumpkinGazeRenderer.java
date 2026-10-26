package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.phantomism.entity.HalloweenPhantom;
import net.ronm19.phantomism.entity.PumpkinGaze;

public final class PumpkinGazeRenderer extends EntityRenderer<PumpkinGaze, PumpkinGazeRenderer.State> {
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;

    public PumpkinGazeRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(PumpkinGaze projectile, State state, float partial) {
        super.extractRenderState(projectile, state, partial);
        state.visualBlock = projectile.visualBlockState();
        state.yaw = Mth.rotLerp(partial, projectile.yRotO, projectile.getYRot());
        state.pitch = Mth.lerp(partial, projectile.xRotO, projectile.getXRot());
        if (HalloweenPhantom.hauntedNight(projectile.level())) { state.lightCoords = LightCoordsUtil.FULL_BRIGHT; }
        blocks.update(state.blockModel, state.visualBlock, BLOCK_CONTEXT);
    }

    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        poses.translate(0, .125D, 0);
        poses.scale(.65F, .65F, .65F);
        poses.rotateDegrees(Axis.YP, 180 - state.yaw);
        poses.rotateDegrees(Axis.XP, state.pitch);
        poses.translate(-.5D, -.5D, -.5D);
        state.blockModel.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    public static final class State extends EntityRenderState {
        public final BlockModelRenderState blockModel = new BlockModelRenderState();
        public BlockState visualBlock;
        public float yaw;
        public float pitch;
    }
}
