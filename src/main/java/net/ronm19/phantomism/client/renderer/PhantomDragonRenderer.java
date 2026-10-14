package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.PhantomDragon;

public final class PhantomDragonRenderer extends PhantomGeoRenderer<PhantomDragon> {
    public static final float MODEL_SCALE = 1.25F;
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(
            false, true, true, false, false, true, true);

    public PhantomDragonRenderer(EntityRendererProvider.Context context) {
        super(context, "phantom_dragon", ACCESSORIES);
        this.shadowRadius = 0.8F;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<PhantomGeoRenderState> pass, float width, float height) {
        super.scaleModelForRender(pass, width * MODEL_SCALE, height * MODEL_SCALE);
    }
}
