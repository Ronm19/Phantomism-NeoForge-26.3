package net.ronm19.phantomism.client.renderer;

import com.geckolib.constant.dataticket.DataTicket;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.SpectralPhantom;
import org.jspecify.annotations.Nullable;

public final class SpectralPhantomRenderer extends PhantomGeoRenderer<SpectralPhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public static final DataTicket<Boolean> VEIL = DataTicket.create("phantomism_spectral_veil", Boolean.class);
    public SpectralPhantomRenderer(EntityRendererProvider.Context context) { super(context, "spectral_phantom", ACCESSORIES); }
    @Override public void addRenderData(SpectralPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) {
        super.addRenderData(entity, ignored, state, partial); state.addGeckolibData(VEIL, entity.isPhasing() || entity.hasVeil() || entity.inPlane());
    }
    @Override public int getRenderColor(SpectralPhantom entity, @Nullable Void ignored, float partial) {
        int color = super.getRenderColor(entity, ignored, partial);
        return entity.isPhasing() || entity.hasVeil() || entity.inPlane() ? ARGB.color(ARGB.alpha(color) * (entity.isPhasing() ? 140 : 200) / 255, color) : color;
    }
    @Override public @Nullable RenderType getRenderType(PhantomGeoRenderState state, Identifier texture) {
        return state.getOrDefaultGeckolibData(VEIL, false) && !state.isInvisible ? RenderTypes.entityTranslucentCull(texture) : super.getRenderType(state, texture);
    }
}
