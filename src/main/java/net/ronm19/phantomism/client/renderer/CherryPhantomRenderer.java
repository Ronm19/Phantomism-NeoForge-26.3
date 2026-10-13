package net.ronm19.phantomism.client.renderer;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.CherryPhantom;

public final class CherryPhantomRenderer extends PhantomGeoRenderer<CherryPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, false, false, false, true, true, true);

    public CherryPhantomRenderer(EntityRendererProvider.Context context) { super(context, "cherry_phantom", ACCESSORIES); }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<PhantomGeoRenderState> pass, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(pass, snapshots);
        snapshots.ifPresent("halo", crown -> fitCrown(crown, pass.renderState().isBaby));
    }

    public static void fitCrown(BoneSnapshot crown, boolean baby) {
        crown.setScale(0.85F, 0.85F, 0.85F).setTranslateY(baby ? -1.6F : -2.0F);
    }
}
