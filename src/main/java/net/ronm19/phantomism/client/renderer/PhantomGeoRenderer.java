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
        state.codexPortrait = false;
        state.accessories = this.accessories;
        state.golemAngry = false;
        state.mirageCombat = false;
        state.eclipseVisual = -1;
        state.shadowVisual = -1;
        state.plagueVisual = -1;
        state.autumnVisual = -1;
        state.halloweenVisual = -1;
        state.lunarCharge = -1;
        state.infernalHeat = -1;
        state.illagerVisual = -1;
        state.solarVisual = -1;
        state.sporeVisual = -1;
        state.galeVisual = -1;
        state.galeVariant = 0;
        state.obsidianVisual = -1;
        state.obsidianVariant = 0;
        state.crystalVisual = -1;
        state.crystalVariant = 0;
        state.mounted = entity.isVehicle();
        state.zombieReviving = entity instanceof net.ronm19.phantomism.entity.ZombiePhantom zombie && zombie.isReviving();
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
        applyVisibility(snapshots, pass.renderState().accessories, pass.renderState().codexPortrait);
    }

    public static void applyVisibility(BoneSnapshots snapshots, PhantomismAccessoryVisibility flags, boolean portrait) {
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
        snapshots.ifPresent("body", snapshot -> snapshot.skipRender(portrait));
        snapshots.ifPresent("mushrooms", snapshot -> {
            boolean headMounted = portrait && flags.mushrooms();
            float scale = headMounted ? .4F : 1F;
            snapshot.setScale(scale, scale, scale).setTranslation(0, headMounted ? -.05F : 0, headMounted ? -9.2F : 0);
        });
        if (portrait) {
            visibility(snapshots, "left_wing", false);
            visibility(snapshots, "right_wing", false);
            visibility(snapshots, "tail", false);
            visibility(snapshots, "spikes", false);
        }
    }

    private static void visibility(BoneSnapshots snapshots, String bone, boolean visible) {
        snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(!visible).skipChildrenRender(!visible));
    }

    private static final class EyesLayer<T extends AbstractPhantom> extends TextureLayerGeoLayer<T, Void, PhantomGeoRenderState> {
        private final Identifier adult;
        private final Identifier baby;

        @Override
        public void submitRenderTask(RenderPassInfo<PhantomGeoRenderState> pass, net.minecraft.client.renderer.SubmitNodeCollector collector) {
            if (pass.renderState().halloweenVisual != 0) { super.submitRenderTask(pass, collector); }
        }

        private EyesLayer(PhantomGeoRenderer<T> renderer, String species) {
            super(renderer, Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_eyes.png"), RenderTypes::eyes);
            this.adult = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_eyes.png");
            this.baby = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_baby_eyes.png");
        }

        @Override
        protected Identifier getTextureResource(PhantomGeoRenderState state) {
            if (state.autumnVisual >= 0) { return AutumnPhantomRenderer.texture(state.isBaby, state.autumnVisual, true); }
            if (state.lunarCharge >= 0) { return LunarPhantomRenderer.texture(state.isBaby, state.lunarCharge, true); }
            if (state.infernalHeat >= 0) { return InfernalPhantomRenderer.texture(state.isBaby, state.infernalHeat, true); }
            if (state.illagerVisual >= 0) { return IllagerPhantomRenderer.texture(state.isBaby, state.illagerVisual, true); }
            if (state.solarVisual >= 0) { return SolarPhantomRenderer.texture(state.isBaby, state.solarVisual, true); }
            if (state.sporeVisual >= 0) { return SporePhantomRenderer.texture(state.isBaby, state.sporeVisual, true); }
            if (state.galeVisual >= 0) { return GalePhantomRenderer.texture(state.isBaby, state.galeVariant, state.galeVisual, true); }
            if (state.obsidianVisual >= 0) { return ObsidianPhantomRenderer.texture(state.isBaby, state.obsidianVariant, state.obsidianVisual, true); }
            if (state.crystalVisual >= 0) { return CrystalPhantomRenderer.texture(state.isBaby, state.crystalVariant, state.crystalVisual, true); }
            if (state.plagueVisual >= 0) { return PlaguePhantomRenderer.texture(state.isBaby, state.plagueVisual, true); }
            if (state.shadowVisual >= 0) { return ShadowPhantomRenderer.texture(state.isBaby, state.shadowVisual, true); }
            if (state.eclipseVisual >= 0) { return EclipsePhantomRenderer.texture(state.isBaby, state.eclipseVisual, true); }
            if (state.mirageCombat) { return state.isBaby ? MiragePhantomRenderer.BABY_COMBAT_EYES : MiragePhantomRenderer.COMBAT_EYES; }
            if (state.golemAngry) { return state.isBaby ? GolemPhantomRenderer.BABY_COMBAT_EYES : GolemPhantomRenderer.COMBAT_EYES; }
            if (state.zombieReviving) { return state.isBaby ? ZombiePhantomRenderer.BABY_REVIVAL_EYES : ZombiePhantomRenderer.REVIVAL_EYES; }
            return state.isBaby ? this.baby : this.adult;
        }
    }
}
