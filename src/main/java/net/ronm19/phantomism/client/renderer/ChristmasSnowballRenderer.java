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
import net.ronm19.phantomism.entity.ChristmasSnowball;

public final class ChristmasSnowballRenderer extends EntityRenderer<ChristmasSnowball, ChristmasSnowballRenderer.State> {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;
    public ChristmasSnowballRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ChristmasSnowball snowball, State state, float partial) {
        super.extractRenderState(snowball, state, partial); state.turn = (snowball.tickCount + partial) * 12;
        blocks.update(state.snow, snowball.visualBlockState(), CONTEXT);
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose(); poses.translate(0, .125D, 0); poses.scale(.4F, .4F, .4F); poses.rotateDegrees(Axis.YP, state.turn); poses.rotateDegrees(Axis.XP, state.turn * .5F);
        for (int axis = 0; axis < 3; axis++) {
            poses.pushPose(); float width = axis == 0 ? .88F : .64F, height = axis == 1 ? .88F : .64F, depth = axis == 2 ? .88F : .64F;
            poses.translate(-width / 2, -height / 2, -depth / 2); poses.scale(width, height, depth);
            state.snow.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor); poses.popPose();
        }
        poses.popPose(); super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public final BlockModelRenderState snow = new BlockModelRenderState(); public float turn; }
}
