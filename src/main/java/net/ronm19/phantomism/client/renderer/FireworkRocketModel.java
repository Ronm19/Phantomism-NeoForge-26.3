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

/** Paper casing, stepped nose, two tail fins and a fuse rod; no billboard. */
public final class FireworkRocketModel {
    private static final ModelPart MODEL = bake();
    private static final Identifier[] SKINS = java.util.stream.IntStream.range(0, 3).mapToObj(style -> Identifier.fromNamespaceAndPath("phantomism", "textures/entity/firework_rocket_" + style + ".png")).toArray(Identifier[]::new);
    private FireworkRocketModel() {}
    private static ModelPart bake() {
        var mesh = new MeshDefinition(); var cubes = CubeListBuilder.create();
        cubes.texOffs(0, 0).addBox(-1.5F, -5, -1.5F, 3, 10, 3);
        cubes.texOffs(0, 16).addBox(-2.5F, 5, -2.5F, 5, 2, 5);
        cubes.texOffs(24, 16).addBox(-1.5F, 7, -1.5F, 3, 2, 3);
        cubes.texOffs(40, 16).addBox(-.5F, 9, -.5F, 1, 1, 1);
        cubes.texOffs(0, 32).addBox(-3.5F, -5, -.5F, 7, 2, 1);
        cubes.texOffs(24, 32).addBox(-.5F, -5, -3.5F, 1, 2, 7);
        cubes.texOffs(48, 32).addBox(-.5F, -10, -.5F, 1, 5, 1);
        mesh.getRoot().addOrReplaceChild("rocket", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
    public static Identifier texture(int style) { return SKINS[Math.clamp(style, 0, 2)]; }
    public static void submit(PoseStack poses, SubmitNodeCollector collector, int style, float yaw, float pitch, float roll, int outline) {
        poses.pushPose(); poses.rotateDegrees(Axis.YP, -yaw); poses.rotateDegrees(Axis.XP, 90 + pitch); poses.rotateDegrees(Axis.YP, roll); poses.scale(.7F, .7F, .7F);
        collector.submitModelPart(MODEL, poses, RenderTypes.entityCutout(texture(style)), net.minecraft.util.LightCoordsUtil.pack(12, 15), OverlayTexture.NO_OVERLAY, null, -1, outline);
        poses.popPose();
    }
}
