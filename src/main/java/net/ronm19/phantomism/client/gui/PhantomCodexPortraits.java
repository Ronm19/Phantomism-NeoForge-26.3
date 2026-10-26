package net.ronm19.phantomism.client.gui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.animation.PhantomAnimations;
import net.ronm19.phantomism.animation.PhantomAnimationMotion;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;
import net.ronm19.phantomism.client.renderer.PhantomGeoRenderer;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.AbstractPhantom;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class PhantomCodexPortraits {
    private static final Map<PhantomDiscovery, AbstractPhantom> PREVIEWS = new EnumMap<>(PhantomDiscovery.class);
    private static final EnumSet<PhantomDiscovery> FAILED = EnumSet.noneOf(PhantomDiscovery.class);
    private static ClientLevel previewLevel;

    private PhantomCodexPortraits() {}

    public record Framing(float centerY, float centerZ, float extent) {}

    public static Quaternionf viewRotation(boolean fungal) {
        return new Quaternionf().rotateY((float)Math.PI - (fungal ? .5F : .32F)).rotateX(.12F);
    }

    public static Framing framing(PhantomismAccessoryVisibility accessories, boolean king) {
        if (accessories.mushrooms()) { return new Framing(.43F, .65F, .85F); }
        if (accessories.halo()) { return new Framing(.378F, .6F, .85F); }
        if (accessories.tendrils()) { return new Framing(.66F, .6F, 2.7F); }
        if (accessories.horns()) { return new Framing(.305F, .65F, .75F); }
        return new Framing(king ? .356F : .266F, .65F, king ? .95F : .72F);
    }

    public static boolean extract(GuiGraphicsExtractor graphics, String species, int positionX, int positionY, int size) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) { clear(); return false; }
        if (previewLevel != client.level) { clear(); previewLevel = client.level; }
        PhantomDiscovery discovery = null;
        for (PhantomDiscovery candidate : PhantomDiscovery.values()) {
            if (candidate.modelName().equals(species)) { discovery = candidate; break; }
        }
        if (discovery == null || FAILED.contains(discovery) || size <= 0) { return false; }
        try {
            AbstractPhantom phantom = PREVIEWS.computeIfAbsent(discovery, entry -> entry.createPreview(client.level));
            if (phantom == null) { FAILED.add(discovery); return false; }
            EntityRenderer<? super AbstractPhantom, ?> renderer = client.getEntityRenderDispatcher().getRenderer(phantom);
            if (!(renderer.createRenderState(phantom, 1F) instanceof PhantomGeoRenderState state)) { FAILED.add(discovery); return false; }
            if (!(renderer instanceof PhantomGeoRenderer<?> geo)
                    || geo.getGeoModel().getBakedModel(geo.getGeoModel().getModelResource(state)).getBone("head").isEmpty()) { FAILED.add(discovery); return false; }
            state.codexPortrait = true;
            state.ageInTicks = 0; state.flapTime = 0; state.bodyRot = 0; state.yRot = 0; state.xRot = 0; state.scale = 1;
            state.shadowPieces.clear(); state.outlineColor = 0;
            state.addGeckolibData(PhantomAnimations.MOTION, PhantomAnimationMotion.HOVER);
            Framing frame = framing(state.accessories, discovery == PhantomDiscovery.KING);
            Quaternionf view = viewRotation(state.accessories.mushrooms());
            Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(view);
            Vector3f translation = rotation.transform(new Vector3f(0, frame.centerY(), frame.centerZ())).negate();
            Vector2f start = graphics.pose().transformPosition(positionX, positionY, new Vector2f());
            Vector2f end = graphics.pose().transformPosition(positionX + size, positionY + size, new Vector2f());
            graphics.entity(state, (end.x - start.x) / frame.extent(), translation, rotation, view,
                    Math.round(start.x), Math.round(start.y), Math.round(end.x), Math.round(end.y));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (FAILED.add(discovery)) { Phantomism.LOGGER.warn("Codex portrait unavailable for {}", species, failure); }
            PREVIEWS.remove(discovery);
            return false;
        }
    }

    public static void clear() { PREVIEWS.clear(); FAILED.clear(); previewLevel = null; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
