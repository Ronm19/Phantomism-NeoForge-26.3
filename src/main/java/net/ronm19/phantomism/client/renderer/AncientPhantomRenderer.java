package net.ronm19.phantomism.client.renderer;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.AncientPhantom;

public final class AncientPhantomRenderer extends PhantomGeoRenderer<AncientPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES=PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public AncientPhantomRenderer(EntityRendererProvider.Context context){super(context,"ancient_phantom",ACCESSORIES);}
    @Override public void scaleModelForRender(RenderPassInfo<PhantomGeoRenderState> pass,float width,float height){super.scaleModelForRender(pass,width*1.1F,height*1.1F);}
}
