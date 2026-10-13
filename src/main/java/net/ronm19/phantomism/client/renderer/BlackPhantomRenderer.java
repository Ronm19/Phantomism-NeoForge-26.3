package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAdultModel;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.model.PhantomismBabyModel;
import net.ronm19.phantomism.client.model.PhantomismModel;
import net.ronm19.phantomism.client.renderer.state.PhantomismRenderState;
import net.ronm19.phantomism.entity.BlackPhantom;

public final class BlackPhantomRenderer extends AgeableMobRenderer<BlackPhantom, PhantomismRenderState, PhantomismModel> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false,
            false,
            false,
            false,
            false,
            true,
            true
    );
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "phantomism",
            "textures/entity/black_phantom.png"
    );
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            "phantomism",
            "textures/entity/black_phantom_baby.png"
    );

    public BlackPhantomRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PhantomismAdultModel(context.bakeLayer(PhantomismAdultModel.LAYER_LOCATION)),
                new PhantomismBabyModel(context.bakeLayer(PhantomismBabyModel.LAYER_LOCATION)),
                0.65F
        );
        this.addLayer(new BlackPhantomEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(PhantomismRenderState state) {
        return state.isBaby ? BABY_TEXTURE : TEXTURE;
    }

    @Override
    public PhantomismRenderState createRenderState() {
        return new PhantomismRenderState();
    }

    @Override
    protected void scale(PhantomismRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        if (state.isBaby) {
            poseStack.scale(0.6F, 0.6F, 0.6F);
        }
    }

    @Override
    public void extractRenderState(BlackPhantom entity, PhantomismRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.flapTime = entity.getUniqueFlapTickOffset() + state.ageInTicks;
        state.skyKnightActive = entity.isSkyKnightActive();
        state.accessories = ACCESSORIES;
    }

    @Override
    protected void setupRotations(PhantomismRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.rotateDegrees(Axis.XP, -state.xRot);
    }

    @Override
    protected int getModelTint(PhantomismRenderState state) {
        return state.skyKnightActive ? 0x99FFFFFF : super.getModelTint(state);
    }

    @Override
    protected RenderType getRenderType(PhantomismRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (state.skyKnightActive && bodyVisible) {
            return RenderTypes.entityTranslucentCull(this.getTextureLocation(state));
        }
        return super.getRenderType(state, bodyVisible, translucent, glowing);
    }
}
