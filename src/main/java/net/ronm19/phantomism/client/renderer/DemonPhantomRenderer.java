package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.DemonPhantom;

public final class DemonPhantomRenderer extends PhantomGeoRenderer<DemonPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = new PhantomismAccessoryVisibility(false, true, true, false, false, true, true);
    public DemonPhantomRenderer(EntityRendererProvider.Context context) { super(context, "demon_phantom", ACCESSORIES); }
    @Override public void scaleModelForRender(RenderPassInfo<PhantomGeoRenderState> pass, float width, float height) { super.scaleModelForRender(pass, width * 1.1F, height * 1.1F); }
}
