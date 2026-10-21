package net.ronm19.phantomism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.entity.ZombiePhantom;

public final class ZombiePhantomRenderer extends PhantomGeoRenderer<ZombiePhantom> {
    public static final PhantomismAccessoryVisibility ACCESSORIES = PhantomismAccessoryVisibility.NO_ACCESSORIES;
    public static final Identifier REVIVAL_EYES = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/zombie_phantom_revival_eyes.png");
    public static final Identifier BABY_REVIVAL_EYES = Identifier.fromNamespaceAndPath("phantomism", "textures/entity/zombie_phantom_baby_revival_eyes.png");
    public ZombiePhantomRenderer(EntityRendererProvider.Context context) { super(context, "zombie_phantom", ACCESSORIES); }
}
