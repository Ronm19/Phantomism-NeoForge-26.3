package net.ronm19.phantomism.client.model;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomGeoModel<T extends AbstractPhantom> extends GeoModel<T> {
    private final Identifier adultTexture;
    private final Identifier babyTexture;
    private static final Identifier[] FALLBACKS = {PhantomAnimations.RESOURCE};
    private static final Identifier[] NO_FALLBACKS = {};

    public PhantomGeoModel(String species) {
        this.adultTexture = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + ".png");
        this.babyTexture = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/" + species + "_baby.png");
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return ((PhantomGeoRenderState) state).isBaby ? PhantomAnimations.BABY_MODEL : PhantomAnimations.ADULT_MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return ((PhantomGeoRenderState) state).isBaby ? this.babyTexture : this.adultTexture;
    }

    @Override
    public Identifier getAnimationResource(T phantom) {
        return phantom.getPhantomAnimationResource();
    }

    @Override
    public Identifier[] getAnimationResourceFallbacks(T phantom) {
        return phantom.getPhantomAnimationResource().equals(PhantomAnimations.RESOURCE) ? NO_FALLBACKS : FALLBACKS;
    }
}
