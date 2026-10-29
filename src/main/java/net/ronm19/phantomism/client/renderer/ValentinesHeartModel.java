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

/** Seven cached voxel rows form a solid, two-sided heart; no Phantom model edits. */
public final class ValentinesHeartModel {
    public static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("phantomism","textures/entity/valentines_heart.png");
    private static final ModelPart MODEL=bake();
    private ValentinesHeartModel() {}
    private static ModelPart bake(){
        var mesh=new MeshDefinition();var cubes=CubeListBuilder.create()
            .texOffs(0,0).addBox(-3.5F,2.5F,-.5F,3,1,1).texOffs(0,0).addBox(.5F,2.5F,-.5F,3,1,1)
            .texOffs(10,0).addBox(-4.5F,.5F,-.5F,9,2,1).texOffs(34,0).addBox(-3.5F,-.5F,-.5F,7,1,1)
            .texOffs(0,8).addBox(-2.5F,-1.5F,-.5F,5,1,1).texOffs(16,8).addBox(-1.5F,-2.5F,-.5F,3,1,1)
            .texOffs(28,8).addBox(-.5F,-3.5F,-.5F,1,1,1);
        mesh.getRoot().addOrReplaceChild("heart",cubes,PartPose.ZERO);return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    public static void submit(PoseStack poses,SubmitNodeCollector collector,float yaw,float pitch,float sway,int outline){
        poses.pushPose();poses.rotateDegrees(Axis.YP,-yaw);poses.rotateDegrees(Axis.XP,pitch);poses.rotateDegrees(Axis.ZP,sway);
        collector.submitModelPart(MODEL,poses,RenderTypes.entityCutout(TEXTURE),net.minecraft.util.LightCoordsUtil.pack(12,15),OverlayTexture.NO_OVERLAY,null,-1,outline);poses.popPose();
    }
}
