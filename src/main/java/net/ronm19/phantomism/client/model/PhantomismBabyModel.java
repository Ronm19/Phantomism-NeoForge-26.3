package net.ronm19.phantomism.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;

public class PhantomismBabyModel extends PhantomismModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "phantomism_baby"),
            "main"
    );

    public PhantomismBabyModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -8.0F, 5.0F, 3.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -5.0F, 0.0F));

        PartDefinition mushrooms = body.addOrReplaceChild("mushrooms", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        mushrooms.addOrReplaceChild("mushroom1", CubeListBuilder.create().texOffs(0, 51).addBox(-4.3F, -6.5F, 0.0F, 8.0F, 13.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 39).addBox(-0.2F, -6.5F, -8.0F, 0.0F, 13.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.9127F, -7.9F, -3.3041F, 0.0F, -0.4712F, 0.0F));

        mushrooms.addOrReplaceChild("mushroom2", CubeListBuilder.create().texOffs(1, 51).addBox(-3.7877F, -11.2F, 0.1959F, 8.0F, 13.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 39).addBox(0.2123F, -11.2F, -7.8041F, 0.0F, 13.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.575F, -3.2F, -4.0F, 0.0F, 0.6807F, 0.0F));

        PartDefinition spikes = body.addOrReplaceChild("spikes", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        spikes.addOrReplaceChild("spike2", CubeListBuilder.create().texOffs(20, 41).addBox(-1.0F, -4.0F, -6.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        spikes.addOrReplaceChild("spike3", CubeListBuilder.create().texOffs(20, 41).addBox(-1.0F, -4.0F, -1.9F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 14).addBox(-4.0F, -3.0F, -6.0F, 7.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -3.75F, -8.0F));

        PartDefinition fangs = head.addOrReplaceChild("fangs", CubeListBuilder.create(), PartPose.offset(-0.5F, 1.2263F, -5.5172F));
        PartDefinition rightFang = fangs.addOrReplaceChild("r_fang", CubeListBuilder.create(), PartPose.offset(-2.0F, 0.0F, 0.0F));
        rightFang.addOrReplaceChild("r_fang_r1", CubeListBuilder.create().texOffs(55, 44)
                .addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0175F, 0.0F, 0.0F));
        PartDefinition leftFang = fangs.addOrReplaceChild("l_fang", CubeListBuilder.create(), PartPose.offset(2.0F, 0.0F, 0.0F));
        leftFang.addOrReplaceChild("l_fang_r1", CubeListBuilder.create().texOffs(55, 44)
                .addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0175F, 0.0F, 0.0F));

        PartDefinition horns = head.addOrReplaceChild("horns", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        horns.addOrReplaceChild("r_horn", CubeListBuilder.create().texOffs(8, 41).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(42, 30).addBox(-0.5F, -2.75F, -1.75F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, -2.25F, -6.25F));

        horns.addOrReplaceChild("l_horn", CubeListBuilder.create().texOffs(14, 41).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(28, 41).addBox(-0.5F, -2.75F, -1.75F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -2.25F, -6.25F));

        PartDefinition tendrils = head.addOrReplaceChild("tendrils", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        tendrils.addOrReplaceChild("r_tendril", CubeListBuilder.create().texOffs(32, 48).addBox(-20.0F, -15.95F, -2.5F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        tendrils.addOrReplaceChild("l_tendril", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(3.0F, -16.05F, -2.5F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        head.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(28, 24).addBox(-3.4F, -6.4F, -6.0F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition leftWing = root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 12).addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -7.0F, -8.0F));
        leftWing.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().texOffs(28, 7).addBox(0.0F, 0.0F, 0.0F, 9.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 0.0F, 0.0F));
        PartDefinition rightWing = root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 12).mirror().addBox(-6.0F, 0.0F, 0.0F, 6.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.5F, -7.0F, -8.0F));
        rightWing.addOrReplaceChild("right_wing_tip", CubeListBuilder.create().texOffs(28, 7).mirror().addBox(-9.0F, 0.0F, 0.0F, 9.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-6.0F, 0.0F, 0.0F));
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(28, 30).addBox(-2.0F, 0.0F, 0.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -7.0F, 1.0F));
        tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(24, 36).addBox(-1.0F, 0.0F, -2.3F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, 6.0F));
        PartDefinition tailSpikes = tail.addOrReplaceChild("tail_spikes", CubeListBuilder.create(), PartPose.ZERO);
        tailSpikes.addOrReplaceChild("spike1", CubeListBuilder.create().texOffs(20, 41).addBox(-1.0F, -2.0F, 1.9F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}
