package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.GolemPhantom;
import org.jspecify.annotations.Nullable;

public final class GolemPhantomRenderer extends PhantomGeoRenderer<GolemPhantom> {
    public static final Identifier COMBAT_EYES = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/golem_phantom_combat_eyes.png");
    public static final Identifier BABY_COMBAT_EYES = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/golem_phantom_baby_combat_eyes.png");
    public GolemPhantomRenderer(EntityRendererProvider.Context context) { super(context, "golem_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES); }
    @Override public void addRenderData(GolemPhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partialTick) { super.addRenderData(entity, ignored, state, partialTick); state.golemAngry = entity.angry(); }
}
