package net.ronm19.phantomism.client.renderer;

import com.geckolib.cache.model.*;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomismRenderState;
import net.ronm19.phantomism.entity.MirageDecoy;
import org.jspecify.annotations.Nullable;

public final class MirageDecoyRenderer extends GeoEntityRenderer<MirageDecoy, MirageDecoyRenderer.State> {
    public record Profile(String texture, PhantomismAccessoryVisibility accessories) {}
    public static final class State extends PhantomismRenderState implements GeoRenderState {
        public String species = "mirage_phantom";
        public boolean baby, combat;
        public int eclipseVisual;
        public int shadowVisual;
        public int plagueVisual;
        public int galeVisual;
        public int galeVariant;
        public int obsidianVisual;
        public int obsidianVariant;
        public int crystalVisual;
        public int crystalVariant;
        public float appearanceScale = 1;
    }
    public MirageDecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new Model()); shadowRadius = 0;
        withRenderLayer(new TextureLayerGeoLayer<MirageDecoy, Void, State>(this, id("textures/entity/mirage_phantom_eyes.png"), RenderTypes::eyes) {
            @Override protected Identifier getTextureResource(State state) { return texture(state, true); }
        });
        withRenderLayer(new Accessories(this));
        for (String bone : new String[]{"right_wing", "right_wing_tip"}) { withRenderLayer(new EclipseWing(this, bone)); }
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }
    public static Profile profile(String species) { return MirageDisguiseProfiles.get(species); }
    public static Identifier texture(State state, boolean eyes) {
        if (state.species.equals("gale_phantom")) { return GalePhantomRenderer.texture(state.baby, state.galeVariant, state.galeVisual, eyes); }
        if (state.species.equals("obsidian_phantom")) { return ObsidianPhantomRenderer.texture(state.baby, state.obsidianVariant, state.obsidianVisual, eyes); }
        if (state.species.equals("crystal_phantom")) { return CrystalPhantomRenderer.texture(state.baby, state.crystalVariant, state.crystalVisual, eyes); }
        if (state.species.equals("plague_phantom")) { return PlaguePhantomRenderer.texture(state.baby, state.plagueVisual, eyes); }
        if (state.species.equals("shadow_phantom")) { return ShadowPhantomRenderer.texture(state.baby, state.shadowVisual, eyes); }
        if (state.species.equals("eclipse_phantom")) { return EclipsePhantomRenderer.texture(state.baby, state.eclipseVisual, eyes); }
        String name = profile(state.species).texture() + (state.baby ? "_baby" : "");
        boolean combat = state.combat && (state.species.equals("golem_phantom") || state.species.equals("mirage_phantom"));
        return id("textures/entity/" + name + (eyes ? combat ? "_combat_eyes" : "_eyes" : "") + ".png");
    }
    @Override public State createRenderState(MirageDecoy entity, @Nullable Void ignored) { return new State(); }
    @Override public void addRenderData(MirageDecoy entity, @Nullable Void ignored, State state, float partial) {
        state.galeVisual = entity.galeVisual();
        state.galeVariant = entity.galeVariant();
        state.obsidianVisual = entity.obsidianVisual();
        state.obsidianVariant = entity.obsidianVariant();
        state.crystalVisual = entity.crystalVisual();
        state.crystalVariant = entity.crystalVariant();
        state.plagueVisual = entity.plagueVisual();
        state.shadowVisual = entity.shadowVisual();
        state.eclipseVisual = entity.eclipseVisual();
        state.species = entity.species(); state.baby = entity.copiedBaby(); state.combat = entity.combatEyes(); state.appearanceScale = entity.visualScale(); state.accessories = profile(state.species).accessories();
    }
    @Override public int getRenderColor(MirageDecoy entity, @Nullable Void ignored, float partial) { return net.minecraft.util.ARGB.color((int) (210 + Math.sin((entity.tickCount + partial) * .14D) * 16), 0xE9E0FF); }
    @Override public @Nullable RenderType getRenderType(State state, Identifier texture) { return RenderTypes.entityTranslucentCull(texture); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(MirageDecoy entity, float partial) { return entity.getBoundingBox().inflate(3.5D * entity.visualScale(), 2D * entity.visualScale(), 3.5D * entity.visualScale()); }
    @Override public void scaleModelForRender(RenderPassInfo<State> pass, float width, float height) { super.scaleModelForRender(pass, width * pass.renderState().appearanceScale, height * pass.renderState().appearanceScale); }
    @Override public void adjustRenderPose(RenderPassInfo<State> pass) { super.adjustRenderPose(pass); pass.poseStack().translate(0, -.009D, 0); pass.poseStack().rotateDegrees(Axis.XP, -pass.renderState().xRot); }
    @Override public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots snapshots) {
        var flags = pass.renderState().accessories;
        String[] names = {"mushrooms", "spikes", "tail_spikes", "horns", "tendrils", "halo", "fangs", "left_wing", "right_wing", "tail"};
        boolean[] visible = {flags.mushrooms(), flags.spikes(), flags.spikes(), flags.horns(), flags.tendrils(), false, flags.fangs(), flags.wings(), flags.wings(), flags.tail()};
        for (int index = 0; index < names.length; index++) { final boolean show = visible[index]; snapshots.ifPresent(names[index], bone -> bone.skipRender(!show).skipChildrenRender(!show)); }
    }
    private static final class EclipseWing extends com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer<MirageDecoy, Void, State> {
        private EclipseWing(MirageDecoyRenderer renderer, String bone) { super(renderer, bone, EclipsePhantomRenderer.moonTexture(false, 0)); }
        @Override public boolean shouldRenderBone(State state) { return state.species.equals("eclipse_phantom") && !state.isInvisible && state.accessories.wings(); }
        @Override protected Identifier getTextureResource(State state) { return EclipsePhantomRenderer.moonTexture(state.baby, state.eclipseVisual); }
        @Override protected RenderType getRenderType(State state, Identifier texture) { return state.eclipseVisual == 2 ? RenderTypes.entityTranslucentEmissive(texture) : super.getRenderType(state, texture); }
    }
    private static final class Model extends GeoModel<MirageDecoy> {
        @Override public Identifier getModelResource(GeoRenderState state) { return ((State) state).baby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture((State) state, false); }
        @Override public Identifier getAnimationResource(MirageDecoy entity) { return id(entity.animationPath()); }
        @Override public Identifier[] getAnimationResourceFallbacks(MirageDecoy entity) { return getAnimationResource(entity).equals(PhantomAnimations.RESOURCE) ? new Identifier[0] : new Identifier[]{PhantomAnimations.RESOURCE}; }
    }
    private static final class Accessories extends GeoRenderLayer<MirageDecoy, Void, State> {
        private final Map<GeoBone, List<GeoQuad>> crowns = new IdentityHashMap<>(2);
        private Accessories(GeoRenderer<MirageDecoy, Void, State> renderer) { super(renderer); }
        @Override public void addPerBoneRender(RenderPassInfo<State> pass, BiConsumer<GeoBone, PerBoneRender<State>> consumer) {
            if (!pass.willRender() || pass.renderState().isInvisible) { return; }
            if (pass.renderState().species.equals("phantom_king")) { pass.model().getBone("head").ifPresent(bone -> consumer.accept(bone, this::crown)); }
            if (pass.renderState().accessories.halo()) { pass.model().getBone("halo").filter(CuboidGeoBone.class::isInstance).ifPresent(bone -> consumer.accept(bone, this::halo)); }
        }
        private void crown(RenderPassInfo<State> pass, GeoBone bone, SubmitNodeCollector tasks) {
            if (crowns.size() >= 2 && !crowns.containsKey(bone)) { crowns.clear(); }
            var quads = crowns.computeIfAbsent(bone, KingCrownLayer::build);
            tasks.submitCustomGeometry(pass.poseStack(), RenderTypes.entityTranslucentEmissive(id("textures/vfx/royal_crown.png")), (pose, buffer) -> {
                PoseStack stack = new PoseStack(); stack.last().set(pose); bone.translateAwayFromPivotPoint(stack);
                for (GeoQuad quad : quads) { quad.render(stack.last().pose(), stack.last().normal().transform(quad.normalVec()), buffer, 15728880, pass.packedOverlay(), pass.renderColor()); }
            });
        }
        private void halo(RenderPassInfo<State> pass, GeoBone bone, SubmitNodeCollector tasks) {
            tasks.submitCustomGeometry(pass.poseStack(), RenderTypes.entityTranslucentEmissive(texture(pass.renderState(), false)), (pose, buffer) -> {
                PoseStack stack = new PoseStack(); stack.last().set(pose); bone.translateAwayFromPivotPoint(stack);
                for (var cube : ((CuboidGeoBone) bone).cubes) {
                    stack.pushPose(); cube.translateToPivotPoint(stack); cube.rotate(stack); cube.translateAwayFromPivotPoint(stack);
                    for (GeoQuad quad : cube.quads()) { if (quad != null && quad.direction() == Direction.UP) { quad.render(stack.last().pose(), stack.last().normal().transform(quad.normalVec()), buffer, 15728880, pass.packedOverlay(), pass.renderColor()); } }
                    stack.popPose();
                }
            });
        }
    }
}
