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

/** Cached native clock projectile, with a gold rim and readable hands on both sides. */
public final class NewYearsClockModel {
    public static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("phantomism","textures/entity/new_years_clock.png");
    private static final ModelPart MODEL=bake();
    private NewYearsClockModel() {}
    private static ModelPart bake() {
        var mesh=new MeshDefinition();
        for(int mark=0;mark<16;mark++) {
            var rim=CubeListBuilder.create().texOffs(0,0).addBox(-1,-4.5F,-.5F,2,1,1);
            mesh.getRoot().addOrReplaceChild("rim_"+mark,rim,PartPose.rotation(0,0,(float)(mark*Math.PI/8)));
        }
        var dial=CubeListBuilder.create().texOffs(8,0).addBox(-3.5F,-2.5F,-.35F,7,5,.7F)
            .texOffs(28,0).addBox(-2.5F,-3.5F,-.35F,5,1,.7F).texOffs(28,0).addBox(-2.5F,2.5F,-.35F,5,1,.7F);
        mesh.getRoot().addOrReplaceChild("navy_dial",dial,PartPose.ZERO);
        for(int side=0;side<2;side++) {
            float depth=side==0?.65F:-1.05F;
            var hands=CubeListBuilder.create().texOffs(42,0).addBox(-.25F,-2.5F,depth,.5F,2.75F,.4F)
                .texOffs(48,0).addBox(0,-.25F,depth,2,.5F,.4F).texOffs(56,0).addBox(-.4F,-.4F,depth,.8F,.8F,.4F);
            mesh.getRoot().addOrReplaceChild("hands_"+side,hands,PartPose.ZERO);
            for(int mark=0;mark<4;mark++) {
                var tick=CubeListBuilder.create().texOffs(0,8).addBox(-.25F,-3.25F,depth,.5F,.75F,.4F);
                mesh.getRoot().addOrReplaceChild("tick_"+side+"_"+mark,tick,PartPose.rotation(0,0,(float)(mark*Math.PI/2)));
            }
        }
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    public static void submit(PoseStack poses,SubmitNodeCollector collector,float yaw,float pitch,float spin,int outline) {
        poses.pushPose();poses.rotateDegrees(Axis.YP,-yaw);poses.rotateDegrees(Axis.XP,pitch);poses.rotateDegrees(Axis.ZP,spin);poses.scale(1,-1,1);
        collector.submitModelPart(MODEL,poses,RenderTypes.entityCutout(TEXTURE),net.minecraft.util.LightCoordsUtil.pack(12,15),OverlayTexture.NO_OVERLAY,null,-1,outline);
        poses.popPose();
    }
}
