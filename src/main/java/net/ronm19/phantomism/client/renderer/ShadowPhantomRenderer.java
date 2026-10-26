package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.ShadowPhantom;
import org.jspecify.annotations.Nullable;

public final class ShadowPhantomRenderer extends PhantomGeoRenderer<ShadowPhantom> {
    public ShadowPhantomRenderer(EntityRendererProvider.Context context) { super(context, "shadow_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES); }
    public static Identifier texture(boolean baby, int visual, boolean eyes) {
        String state = visual == 3 ? "_nightfall" : visual == 2 ? "_mark" : visual == 1 ? "_hunting" : "";
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/shadow_phantom" + (baby ? "_baby" : "") + (eyes ? state + "_eyes" : "") + ".png");
    }
    @Override public void addRenderData(ShadowPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.shadowVisual = entity.visualState(); }
    @Override public int getRenderColor(ShadowPhantom entity, @Nullable Void ignored, float partial) { int color = super.getRenderColor(entity, ignored, partial); return entity.concealed() ? ARGB.color(entity.vanishing() ? 100 : 180, color) : color; }
    @Override public @Nullable RenderType getRenderType(PhantomGeoRenderState state, Identifier texture) { return state.isInvisible ? super.getRenderType(state, texture) : RenderTypes.entityTranslucentCull(texture); }
}
