package net.ronm19.phantomism.client.renderer;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.AngelPhantom;

import java.util.function.BiConsumer;

public final class AngelHaloLayer extends GeoRenderLayer<AngelPhantom, Void, PhantomGeoRenderState> {
    public AngelHaloLayer(GeoRenderer<AngelPhantom, Void, PhantomGeoRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(RenderPassInfo<PhantomGeoRenderState> pass, SubmitNodeCollector renderTasks) {
        if (pass.willRender() && pass.renderState().accessories.halo()) {
            pass.addBoneUpdater((renderPass, snapshots) -> snapshots.get("halo")
                    .filter(snapshot -> snapshot.getBone() instanceof CuboidGeoBone)
                    .ifPresent(snapshot -> snapshot.skipRender(true)));
        }
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<PhantomGeoRenderState> pass,
                                 BiConsumer<GeoBone, PerBoneRender<PhantomGeoRenderState>> consumer) {
        if (pass.willRender() && pass.renderState().accessories.halo()) {
            pass.model().getBone("halo").filter(CuboidGeoBone.class::isInstance)
                    .ifPresent(bone -> consumer.accept(bone, this::renderHalo));
        }
    }

    private void renderHalo(RenderPassInfo<PhantomGeoRenderState> pass, GeoBone bone,
                            SubmitNodeCollector renderTasks) {
        PhantomGeoRenderState state = pass.renderState();
        Identifier texture = getTextureResource(state);
        RenderType renderType = state.isInvisible ? this.renderer.getRenderType(state, texture)
                : RenderTypes.entityTranslucentEmissive(texture);
        if (renderType == null) {
            return;
        }
        int overlay = pass.packedOverlay();
        int color = pass.renderColor();
        renderTasks.submitCustomGeometry(pass.poseStack(), renderType, (pose, buffer) -> {
            PoseStack poses = new PoseStack();
            poses.last().set(pose);
            bone.translateAwayFromPivotPoint(poses);
            for (GeoCube cube : ((CuboidGeoBone)bone).cubes) {
                poses.pushPose();
                cube.translateToPivotPoint(poses);
                cube.rotate(poses);
                cube.translateAwayFromPivotPoint(poses);
                for (GeoQuad quad : cube.quads()) {
                    if (quad != null && quad.direction() == Direction.UP) {
                        quad.render(poses.last().pose(), poses.last().normal().transform(quad.normalVec()),
                                buffer, LightCoordsUtil.FULL_BRIGHT, overlay, color);
                    }
                }
                poses.popPose();
            }
        });
    }
}
