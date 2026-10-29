package net.ronm19.phantomism.client.renderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.CreatorPhantom;
public final class CreatorPhantomRenderer extends PhantomGeoRenderer<CreatorPhantom> {
    public CreatorPhantomRenderer(EntityRendererProvider.Context context){super(context,"creator_phantom",new PhantomismAccessoryVisibility(false,false,false,false,false,true,true));}
    @Override public void addRenderData(CreatorPhantom entity,Void ignored,net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState state,float partial){
        super.addRenderData(entity,ignored,state,partial);
        if(state.nameTag!=null){state.nameTag=net.ronm19.phantomism.client.screen.CreatorIdentityClient.name();}
    }
}
