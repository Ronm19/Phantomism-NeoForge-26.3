package net.ronm19.phantomism.client.gui;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.client.renderer.state.PhantomismRenderState;
import net.ronm19.phantomism.entity.AbstractPhantom;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class PhantomDiscoveryIcons {
    private static final Map<PhantomDiscovery, AbstractPhantom> PREVIEWS = new EnumMap<>(PhantomDiscovery.class);
    private static ClientLevel previewLevel;

    private PhantomDiscoveryIcons() {
    }

    public static boolean extract(GuiGraphicsExtractor graphics, ItemStack stack, int iconX, int iconY) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(PhantomDiscovery.ICON_DATA_KEY)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearPreviews();
            return false;
        }
        if (previewLevel != minecraft.level) {
            clearPreviews();
            previewLevel = minecraft.level;
        }
        String name = data.copyTag().getString(PhantomDiscovery.ICON_DATA_KEY).orElse("");
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            if (!discovery.modelName().equals(name)) {
                continue;
            }
            AbstractPhantom phantom = PREVIEWS.computeIfAbsent(discovery,
                    entry -> entry.createPreview(minecraft.level));
            if (phantom == null) {
                return false;
            }
            EntityRenderer<? super AbstractPhantom, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(phantom);
            if (!(renderer.createRenderState(phantom, 1.0F) instanceof PhantomismRenderState state)) {
                return false;
            }
            long animationTime = Util.getMillis();
            state.ageInTicks = (animationTime % 360000L) / 50.0F;
            state.flapTime = state.ageInTicks;
            state.bodyRot = (animationTime % 10000L) * 0.036F;
            state.yRot = 0.0F;
            state.xRot = 0.0F;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            Vector2f topLeft = graphics.pose().transformPosition(iconX, iconY, new Vector2f());
            Vector2f bottomRight = graphics.pose().transformPosition(iconX + 16, iconY + 16, new Vector2f());
            float scale = (bottomRight.x - topLeft.x) / 16.0F;
            Quaternionf tilt = new Quaternionf().rotateX(0.35F);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
            graphics.entity(state, 5.5F * scale, new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F),
                    rotation, tilt, Math.round(topLeft.x), Math.round(topLeft.y), Math.round(bottomRight.x), Math.round(bottomRight.y));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearPreviews();
    }

    private static void clearPreviews() {
        PREVIEWS.clear();
        previewLevel = null;
    }
}
