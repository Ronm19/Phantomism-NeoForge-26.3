package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.StPatricksShamrockProjectile;

public final class StPatricksShamrockRenderer extends EntityRenderer<StPatricksShamrockProjectile, StPatricksShamrockRenderer.State> {
    public StPatricksShamrockRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(StPatricksShamrockProjectile shot, State state, float partial) {
        super.extractRenderState(shot, state, partial); state.spin = (shot.tickCount + partial) * 5;
        var motion = shot.getDeltaMovement(); state.yaw = (float)Math.toDegrees(Math.atan2(-motion.x, motion.z)); state.pitch = (float)Math.toDegrees(Math.atan2(-motion.y, motion.horizontalDistance()));
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        StPatricksShamrockModel.submit(poses, collector, state.yaw, state.pitch, state.spin, state.outlineColor);
        super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public float yaw, pitch, spin; }
}
