package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismModel;
import net.ronm19.phantomism.client.renderer.state.PhantomismRenderState;

public final class BlackPhantomEyesLayer extends EyesLayer<PhantomismRenderState, PhantomismModel> {
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            "phantomism",
            "textures/entity/black_phantom_eyes.png"
    ));
    private static final RenderType BABY_EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            "phantomism",
            "textures/entity/black_phantom_baby_eyes.png"
    ));

    public BlackPhantomEyesLayer(RenderLayerParent<PhantomismRenderState, PhantomismModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, PhantomismRenderState state, float yRot, float xRot) {
        collector.order(1).submitModel(
                this.getParentModel(),
                state,
                poseStack,
                state.isBaby ? BABY_EYES : EYES,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
