package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.MiragePhantom;
import org.jspecify.annotations.Nullable;

public final class MiragePhantomRenderer extends PhantomGeoRenderer<MiragePhantom> {
    public static final Identifier COMBAT_EYES = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/mirage_phantom_combat_eyes.png");
    public static final Identifier BABY_COMBAT_EYES = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/mirage_phantom_baby_combat_eyes.png");
    public MiragePhantomRenderer(EntityRendererProvider.Context context) { super(context, "mirage_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES); }
    @Override public void addRenderData(MiragePhantom entity, @Nullable Void ignored, PhantomGeoRenderState state, float partial) { super.addRenderData(entity, ignored, state, partial); state.mirageCombat = entity.combatEyes(); }
    @Override public int getRenderColor(MiragePhantom entity, @Nullable Void ignored, float partial) { int original = super.getRenderColor(entity, ignored, partial); return ARGB.color((int) (242 + Math.sin((entity.tickCount + partial) * .09D) * 10), original); }
    @Override public @Nullable RenderType getRenderType(PhantomGeoRenderState state, Identifier texture) { return state.isInvisible ? super.getRenderType(state, texture) : RenderTypes.entityTranslucentCull(texture); }
}
