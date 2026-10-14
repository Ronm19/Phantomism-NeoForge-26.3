package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.ronm19.phantomism.animation.PhantomAnimationMotion;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomGeoModel;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.BlackPhantom;
import org.jspecify.annotations.Nullable;

public class PhantomGeoRenderer<T extends AbstractPhantom> extends GeoEntityRenderer<T, PhantomGeoRenderState> {
    private final PhantomismAccessoryVisibility accessories;

    public PhantomGeoRenderer(EntityRendererProvider.Context context, String species, PhantomismAccessoryVisibility accessories) {
        this(context, species, accessories, new PhantomGeoModel<>(species));
    }

    protected PhantomGeoRenderer(EntityRendererProvider.Context context, String species, PhantomismAccessoryVisibility accessories, GeoModel<T> model) {
        super(context, model);
        this.accessories = accessories;
        this.shadowRadius = 0.65F;
        this.withRenderLayer(new EyesLayer<>(this, species));
    }

    @Override
    public PhantomGeoRenderState createRenderState(T entity, @Nullable Void ignored) {
        return new PhantomGeoRenderState();
    }

    @Override
    public void addRenderData(T entity, @Nullable Void ignored, PhantomGeoRenderState state, float partialTick) {
        state.accessories = this.accessories;
        state.mounted = entity.isVehicle();
        state.skyKnightActive = entity instanceof BlackPhantom black && black.isSkyKnightActive();
        state.addGeckolibData(PhantomAnimations.MOTION, motion(entity));
    }

    public static PhantomAnimationMotion motion(AbstractPhantom entity) {
        if (entity.getPose() == Pose.SLEEPING) {
            return PhantomAnimationMotion.SLEEP;
        }
        if (entity.isInWater()) {
            float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
            return entity.getDeltaMovement().y > 0.06D ? PhantomAnimationMotion.SWIM_SURFACE
                    : entity.getDeltaMovement().y < -0.06D ? PhantomAnimationMotion.SWIM_DIVE
                    : !entity.isVehicle() && turn > 3.0F ? PhantomAnimationMotion.SWIM_RIGHT
                    : !entity.isVehicle() && turn < -3.0F ? PhantomAnimationMotion.SWIM_LEFT : PhantomAnimationMotion.SWIM;
        }
        if (entity.onGround() && entity.getOrder() == AbstractPhantom.Order.STAY) {
            return PhantomAnimationMotion.SIT;
        }
        if ((entity.getPhantomAnimationFlags() & 2) != 0) {
            return PhantomAnimationMotion.BREED;
        }
        if ((entity.getPhantomAnimationFlags() & 1) != 0 && !entity.isVehicle()) {
            return PhantomAnimationMotion.DIVE;
        }
        if (entity.getDeltaMovement().y > 0.08D) {
            return PhantomAnimationMotion.ASCEND;
        }
        if (entity.getDeltaMovement().y < -0.08D) {
            return PhantomAnimationMotion.DESCEND;
        }
        float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
        if (!entity.isVehicle() && Math.abs(turn) > 3.0F) {
            return turn > 0 ? PhantomAnimationMotion.BANK_RIGHT : PhantomAnimationMotion.BANK_LEFT;
        }
        double speed = entity.getDeltaMovement().horizontalDistanceSqr();
        return speed > 0.25D ? PhantomAnimationMotion.FAST
                : speed > 0.004D ? PhantomAnimationMotion.FLY : PhantomAnimationMotion.HOVER;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<PhantomGeoRenderState> pass, float width, float height) {
        float babyScale = pass.renderState().isBaby ? 0.6F : 1.0F;
        super.scaleModelForRender(pass, width * babyScale, height * babyScale);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<PhantomGeoRenderState> pass) {
        super.adjustRenderPose(pass);
        pass.poseStack().translate(0.0D, -0.009D, 0.0D);
        pass.poseStack().rotateDegrees(Axis.XP, -pass.renderState().xRot);
    }

    @Override
    public int getRenderColor(T entity, @Nullable Void ignored, float partialTick) {
        int color = super.getRenderColor(entity, ignored, partialTick);
        return entity instanceof BlackPhantom black && black.isSkyKnightActive()
                ? net.minecraft.util.ARGB.color(net.minecraft.util.ARGB.alpha(color) * 153 / 255, color) : color;
    }

    @Override
    public @Nullable RenderType getRenderType(PhantomGeoRenderState state, Identifier texture) {
        return state.skyKnightActive && !state.isInvisible ? RenderTypes.entityTranslucentCull(texture)
                : super.getRenderType(state, texture);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<PhantomGeoRenderState> pass, BoneSnapshots snapshots) {
        if (pass.renderState().mounted) {
            snapshots.ifPresent("root", snapshot -> snapshot.setRotX(0.0F).setRotZ(0.0F));
        }
        PhantomismAccessoryVisibility flags = pass.renderState().accessories;
        visibility(snapshots, "mushrooms", flags.mushrooms());
        visibility(snapshots, "spikes", flags.spikes());
        visibility(snapshots, "tail_spikes", flags.spikes());
        visibility(snapshots, "horns", flags.horns());
        visibility(snapshots, "tendrils", flags.tendrils());
        visibility(snapshots, "halo", flags.halo());
        visibility(snapshots, "fangs", flags.fangs());
        visibility(snapshots, "left_wing", flags.wings());
        visibility(snapshots, "right_wing", flags.wings());
        visibility(snapshots, "tail", flags.tail());
    }

    private static void visibility(BoneSnapshots snapshots, String bone, boolean visible) {
        snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(!visible).skipChildrenRender(!visible));
    }

    private static final class EyesLayer<T extends AbstractPhantom> extends TextureLayerGeoLayer<T, Void, PhantomGeoRenderState> {
        private final Identifier adult;
        private final Identifier baby;

        private EyesLayer(PhantomGeoRenderer<T> renderer, String species) {
            super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_eyes.png"), RenderTypes::eyes);
            this.adult = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_eyes.png");
            this.baby = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_baby_eyes.png");
        }

        @Override
        protected Identifier getTextureResource(PhantomGeoRenderState state) {
            return state.isBaby ? this.baby : this.adult;
        }
    }
}
