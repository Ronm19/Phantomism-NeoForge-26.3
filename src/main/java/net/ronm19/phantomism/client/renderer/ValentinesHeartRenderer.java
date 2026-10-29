package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.ValentinesHeartProjectile;

public final class ValentinesHeartRenderer extends EntityRenderer<ValentinesHeartProjectile, ValentinesHeartRenderer.State> {
    public ValentinesHeartRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ValentinesHeartProjectile shot, State state, float partial) {
        super.extractRenderState(shot, state, partial); state.spin = (float)Math.sin((shot.tickCount + partial) * .1D) * 8;
        var motion = shot.getDeltaMovement(); state.yaw = (float)Math.toDegrees(Math.atan2(-motion.x, motion.z)); state.pitch = (float)Math.toDegrees(Math.atan2(-motion.y, motion.horizontalDistance()));
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        ValentinesHeartModel.submit(poses, collector, state.yaw, state.pitch, state.spin, state.outlineColor);
        super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public float yaw, pitch, spin; }
}
