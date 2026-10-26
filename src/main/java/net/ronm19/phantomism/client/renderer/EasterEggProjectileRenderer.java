package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.EasterEggProjectile;

public final class EasterEggProjectileRenderer extends EntityRenderer<EasterEggProjectile, EasterEggProjectileRenderer.State> {
    public EasterEggProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(EasterEggProjectile egg, State state, float partial) {
        super.extractRenderState(egg, state, partial); state.style = egg.style(); state.turn = (egg.tickCount + partial) * 9; state.yaw = net.minecraft.util.Mth.rotLerp(partial, egg.yRotO, egg.getYRot());
        state.lightCoords = net.minecraft.util.LightCoordsUtil.pack(10, 15);
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose(); poses.translate(0, .15D, 0); EasterEggModel.submit(poses, collector, state.style, .55F, state.yaw + state.turn, state.turn * .4F, state.lightCoords, state.outlineColor); poses.popPose();
        super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public int style; public float turn, yaw; }
}
