package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.FireworkRocketProjectile;

public final class FireworkRocketRenderer extends EntityRenderer<FireworkRocketProjectile, FireworkRocketRenderer.State> {
    public FireworkRocketRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(FireworkRocketProjectile rocket, State state, float partial) {
        super.extractRenderState(rocket, state, partial); state.style = rocket.style(); state.roll = (rocket.tickCount + partial) * 7;
        var motion = rocket.getDeltaMovement(); state.yaw = (float)Math.toDegrees(Math.atan2(-motion.x, motion.z)); state.pitch = (float)Math.toDegrees(Math.atan2(-motion.y, motion.horizontalDistance()));
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        FireworkRocketModel.submit(poses, collector, state.style, state.yaw, state.pitch, state.roll, state.outlineColor);
        super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public int style; public float yaw, pitch, roll; }
}
