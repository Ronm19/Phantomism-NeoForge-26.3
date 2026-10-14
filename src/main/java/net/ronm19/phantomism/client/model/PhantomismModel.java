package net.ronm19.phantomism.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.ronm19.phantomism.client.renderer.state.PhantomismRenderState;

public abstract class PhantomismModel extends EntityModel<PhantomismRenderState> {
    private final ModelPart mushrooms;
    private final ModelPart spikes;
    private final ModelPart horns;
    private final ModelPart tendrils;
    private final ModelPart halo;
    private final ModelPart fangs;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart tail;
    private final ModelPart tail2;
    private final ModelPart tailSpikes;

    protected PhantomismModel(ModelPart root) {
        super(root);
        ModelPart creatureRoot = root.getChild("root");
        ModelPart body = creatureRoot.getChild("body");
        ModelPart head = creatureRoot.getChild("head");
        this.mushrooms = body.getChild("mushrooms");
        this.spikes = body.getChild("spikes");
        this.horns = head.getChild("horns");
        this.tendrils = head.getChild("tendrils");
        this.halo = head.getChild("halo");
        this.fangs = head.getChild("fangs");
        this.fangs.visible = false;
        this.leftWing = creatureRoot.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWing = creatureRoot.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.tail = creatureRoot.getChild("tail");
        this.tail2 = this.tail.getChild("tail2");
        this.tailSpikes = this.tail.getChild("tail_spikes");
    }

    @Override
    public void setupAnim(PhantomismRenderState state) {
        super.setupAnim(state);
        PhantomismAccessoryVisibility accessories = state.accessories;
        this.mushrooms.visible = accessories.mushrooms();
        this.spikes.visible = accessories.spikes();
        this.tailSpikes.visible = accessories.spikes();
        this.horns.visible = accessories.horns();
        this.tendrils.visible = accessories.tendrils();
        this.halo.visible = accessories.halo();
        this.fangs.visible = accessories.fangs();
        this.leftWing.visible = accessories.wings();
        this.leftWingTip.visible = accessories.wings();
        this.rightWing.visible = accessories.wings();
        this.rightWingTip.visible = accessories.wings();
        this.tail.visible = accessories.tail();
        this.tail2.visible = accessories.tail();

        float flap = state.flapTime * 7.448451F * (float) (Math.PI / 180.0);
        float wingRotation = Mth.cos(flap) * 16.0F * (float) (Math.PI / 180.0);
        this.leftWing.zRot = wingRotation;
        this.leftWingTip.zRot = wingRotation;
        this.rightWing.zRot = -wingRotation;
        this.rightWingTip.zRot = -wingRotation;
        float tailRotation = -(5.0F + Mth.cos(flap * 2.0F) * 5.0F) * (float) (Math.PI / 180.0);
        this.tail.xRot = tailRotation;
        this.tail2.xRot = tailRotation;
    }
}
