package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.RavagerPhantom;

public final class RavagerPhantomRenderer extends PhantomGeoRenderer<RavagerPhantom> {
    public static final float MODEL_SCALE = 1.15F;
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, false, true, false, false, true, true);

    public RavagerPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, "ravager_phantom", ACCESSORIES);
        this.shadowRadius = 0.75F;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<PhantomGeoRenderState> pass, float width, float height) {
        super.scaleModelForRender(pass, width * MODEL_SCALE, height * MODEL_SCALE);
    }
}
