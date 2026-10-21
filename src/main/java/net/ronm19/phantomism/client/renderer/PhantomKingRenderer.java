package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.PhantomKing;

public final class PhantomKingRenderer extends PhantomGeoRenderer<PhantomKing> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public PhantomKingRenderer(EntityRendererProvider.Context context) { super(context, "phantom_king", ACCESSORIES); this.shadowRadius = 1.3F; this.withRenderLayer(new KingCrownLayer(this)); }
}
