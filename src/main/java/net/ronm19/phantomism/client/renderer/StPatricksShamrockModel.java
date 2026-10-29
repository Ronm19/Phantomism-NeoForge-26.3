package net.ronm19.phantomism.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Four stepped, heart-like leaflets and a stem; a cached native projectile model. */
public final class StPatricksShamrockModel {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/st_patricks_shamrock.png");
    private static final ModelPart MODEL = bake();
    private StPatricksShamrockModel() {}
    private static ModelPart bake() {
        var mesh = new MeshDefinition();
        for (int leaf = 0; leaf < 4; leaf++) {
            var cubes = CubeListBuilder.create();
            cubes.texOffs(0, 0).addBox(-2.5F, -4, -.5F, 2, 1, 1);
            cubes.texOffs(0, 0).addBox(.5F, -4, -.5F, 2, 1, 1);
            cubes.texOffs(12, 0).addBox(-2.5F, -3, -.5F, 5, 1, 1);
            cubes.texOffs(28, 0).addBox(-1.5F, -2, -.5F, 3, 1, 1);
            cubes.texOffs(40, 0).addBox(-.5F, -1, -.5F, 1, 1, 1);
            mesh.getRoot().addOrReplaceChild("leaf_" + leaf, cubes, PartPose.rotation(0, 0, (float)(Math.PI / 4 + leaf * Math.PI / 2)));
        }
        var core = CubeListBuilder.create().texOffs(48, 0).addBox(-.5F, -.5F, -.7F, 1, 1, 1);
        mesh.getRoot().addOrReplaceChild("core", core, PartPose.ZERO);
        var stem = CubeListBuilder.create().texOffs(56, 0).addBox(-.5F, .5F, -.4F, 1, 5, 1);
        mesh.getRoot().addOrReplaceChild("stem", stem, PartPose.rotation(0, 0, -.18F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
    public static void submit(PoseStack poses, SubmitNodeCollector collector, float yaw, float pitch, float spin, int outline) {
        poses.pushPose(); poses.rotateDegrees(Axis.YP, -yaw); poses.rotateDegrees(Axis.XP, pitch); poses.rotateDegrees(Axis.ZP, spin); poses.scale(1.1F, 1.1F, 1.1F);
        collector.submitModelPart(MODEL, poses, RenderTypes.entityCutout(TEXTURE), net.minecraft.util.LightCoordsUtil.pack(12, 15), OverlayTexture.NO_OVERLAY, null, -1, outline);
        poses.popPose();
    }
}
