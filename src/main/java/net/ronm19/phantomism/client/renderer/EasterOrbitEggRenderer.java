package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.ronm19.phantomism.entity.EasterOrbitEgg;

public final class EasterOrbitEggRenderer extends EntityRenderer<EasterOrbitEgg, EasterOrbitEggRenderer.State> {
    public EasterOrbitEggRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(EasterOrbitEgg egg, State state, float partial) {
        super.extractRenderState(egg, state, partial); state.style = egg.style(); state.turn = (egg.tickCount + partial) * 2.2F;
        state.lightCoords = net.minecraft.util.LightCoordsUtil.pack(10, 15);
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        EasterEggModel.submit(poses, collector, state.style, 1.15F, state.turn, (float)Math.sin(state.turn * .03D) * 8, state.lightCoords, state.outlineColor);
        super.submit(state, poses, collector, camera);
    }
    public static final class State extends EntityRenderState { public int style; public float turn; }
}
