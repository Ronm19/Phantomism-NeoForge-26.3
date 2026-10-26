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
import net.minecraft.world.level.block.Blocks;
import net.ronm19.phantomism.entity.ChristmasGift;

public final class ChristmasGiftRenderer extends EntityRenderer<ChristmasGift, ChristmasGiftRenderer.State> {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;
    public ChristmasGiftRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ChristmasGift gift, State state, float partial) {
        super.extractRenderState(gift, state, partial); state.open = gift.openProgress(); state.turn = gift.getYRot();
        blocks.update(state.wrap, Blocks.WOOL.pick(gift.green() ? net.minecraft.world.item.DyeColor.GREEN : net.minecraft.world.item.DyeColor.RED).defaultBlockState(), CONTEXT);
        blocks.update(state.ribbon, Blocks.GOLD_BLOCK.defaultBlockState(), CONTEXT);
        blocks.update(state.trim, Blocks.WOOL.pick(gift.green() ? net.minecraft.world.item.DyeColor.RED : net.minecraft.world.item.DyeColor.GREEN).defaultBlockState(), CONTEXT);
    }
    private static void box(State state, BlockModelRenderState model, PoseStack poses, SubmitNodeCollector collector, double horizontal, double height, double depth, float width, float tall, float length) {
        poses.pushPose(); poses.translate(horizontal, height, depth); poses.scale(width, tall, length);
        model.submit(poses, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor); poses.popPose();
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose(); poses.rotateDegrees(Axis.YP, state.turn);
        box(state, state.wrap, poses, collector, -.45, 0, -.45, .9F, .7F, .9F);
        box(state, state.ribbon, poses, collector, -.075, -.005, -.455, .15F, .71F, .91F);
        box(state, state.ribbon, poses, collector, -.455, -.005, -.075, .91F, .71F, .15F);
        poses.pushPose(); poses.translate(0, .7D + state.open * .5D, -.45D); poses.rotateDegrees(Axis.XP, -state.open * 38);
        box(state, state.trim, poses, collector, -.49, 0, -.04, .98F, .16F, .98F);
        box(state, state.ribbon, poses, collector, -.08, .16, -.04, .16F, .025F, .98F);
        box(state, state.ribbon, poses, collector, -.49, .16, .37, .98F, .025F, .16F);
        for (int side : new int[]{-1, 1}) {
            poses.pushPose(); poses.translate(side * .14, .2, .45); poses.rotateDegrees(Axis.ZP, side * 28);
            box(state, state.ribbon, poses, collector, -.16, 0, -.07, .32F, .09F, .14F);
            box(state, state.trim, poses, collector, -.11, .025, -.072, .22F, .045F, .144F); poses.popPose();
        }
        poses.popPose(); poses.popPose(); super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState {
        public final BlockModelRenderState wrap = new BlockModelRenderState(), ribbon = new BlockModelRenderState(), trim = new BlockModelRenderState();
        public float open, turn;
    }
}
