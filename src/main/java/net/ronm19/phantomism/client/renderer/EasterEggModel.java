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

/** One cached, UV-mapped stepped egg shared by projectiles and orbit props. */
public final class EasterEggModel {
    private static final ModelPart MODEL = bake();
    private static final Identifier[] SKINS = java.util.stream.IntStream.range(0, 4).mapToObj(style -> Identifier.fromNamespaceAndPath("phantomism", "textures/entity/easter_egg_" + style + ".png")).toArray(Identifier[]::new);
    private EasterEggModel() {}
    private static ModelPart bake() {
        var mesh = new MeshDefinition(); var cubes = CubeListBuilder.create();
        int[] widths = {6, 10, 12, 12, 10, 8, 6, 2}, heights = {1, 2, 3, 3, 3, 2, 1, 1}; int y = -8;
        for (int slice = 0; slice < widths.length; slice++) {
            int width = widths[slice]; cubes.texOffs(0, slice * 16).addBox(-width / 2F, y, -width / 2F, width, heights[slice], width); y += heights[slice];
        }
        mesh.getRoot().addOrReplaceChild("egg", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }
    public static Identifier texture(int style) { return SKINS[Math.clamp(style, 0, 3)]; }
    public static void submit(PoseStack poses, SubmitNodeCollector collector, int style, float scale, float yaw, float tilt, int light, int outline) {
        poses.pushPose(); poses.rotateDegrees(Axis.YP, yaw); poses.rotateDegrees(Axis.XP, tilt); poses.scale(scale, scale, scale);
        collector.submitModelPart(MODEL, poses, RenderTypes.entityCutout(texture(style)), light, OverlayTexture.NO_OVERLAY, null, -1, outline);
        poses.popPose();
    }
}
