package net.ronm19.phantomism.client.screen;

import java.util.UUID;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.ability.PhantomAbilities;
import net.ronm19.phantomism.ability.StaffAbilityPacket;
import net.ronm19.phantomism.ability.StaffAbilityService;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.network.PhantomismNetwork;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class StaffAbilityClient {
    private record Selection(UUID player, UUID recipient, PhantomAbilities.View ability, Object level, long expires) {}
    private static Selection pending;
    private StaffAbilityClient() {}
    public static void cancel() { pending = null; }

    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(StaffAbilityPacket.Snapshot.TYPE, (packet, context) -> context.enqueueWork(() -> accept(packet)));
    }

    public static void accept(StaffAbilityPacket.Snapshot packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !packet.player().equals(client.player.getUUID().toString())
                || !packet.dimension().equals(client.level.dimension().toString()) || !(client.gui.screen() instanceof PhantomStaffScreen screen)) { return; }
        try {
            var state = StaffAbilityService.JSON.fromJson(packet.data(), StaffAbilityService.State.class);
            if (state == null || state.abilities() == null || state.abilities().size() > 32) { return; }
            for (var view : state.abilities()) {
                if (view == null || view.id() == null || view.id().length() > 128 || view.nameKey() == null || view.nameKey().length() > 256
                        || view.descriptionKey() == null || view.descriptionKey().length() > 256 || view.target() == null || view.status() == null
                        || view.remaining() < 0 || view.remaining() > 240000 || view.reason() == null || view.reason().length() > 128) { return; }
            }
            screen.abilities(packet.recipient(), state.abilities());
        } catch (RuntimeException ignored) {}
    }

    public static void activate(UUID recipient, PhantomAbilities.View ability) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || recipient == null || ability.automatic() || ability.status() != PhantomAbilities.Status.READY) { return; }
        net.ronm19.phantomism.PhantomismClient.cancelStaffSelection();
        client.gui.chatListener().handleSystemMessage(StaffAbilityTips.selection(ability), false);
        if (ability.target() == PhantomAbilities.Target.NONE) {
            ClientPacketDistributor.sendToServer(new StaffAbilityPacket(recipient.toString(), ability.id(), "", net.minecraft.core.BlockPos.ZERO, true));
            return;
        }
        pending = new Selection(client.player.getUUID(), recipient, ability, client.level, client.level.getGameTime() + 1200);
        client.setScreenAndShow(null);
        client.player.sendOverlayMessage(Component.translatable(ability.target() == PhantomAbilities.Target.POSITION ? "staff.ability.select_position" : "staff.ability.select_target"));
    }

    private static boolean holds(Player player) {
        return player.getMainHandItem().is(PhantomismContent.PHANTOM_STAFF.get()) || player.getOffhandItem().is(PhantomismContent.PHANTOM_STAFF.get());
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (pending == null || event.getAction() != InputConstants.PRESS) { return; }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level != pending.level() || !client.player.getUUID().equals(pending.player())
                || client.level.getGameTime() > pending.expires() || !holds(client.player)) { pending = null; return; }
        if (client.gui.screen() != null) { return; }
        if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            pending = null; event.setCanceled(true); client.player.sendOverlayMessage(Component.translatable("screen.phantomism.staff.selection_canceled")); return;
        }
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) { return; }
        event.setCanceled(true);
        Player player = client.player;
        var source = client.level.getEntitiesOfClass(AbstractPhantom.class, player.getBoundingBox().inflate(128), phantom -> phantom.getUUID().equals(pending.recipient()) && phantom.isOwnedBy(player)).stream().findFirst().orElse(null);
        if (source == null) { pending = null; return; }
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(pending.ability().target() == PhantomAbilities.Target.POSITION ? 6 : 32));
        var block = client.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (pending.ability().target() == PhantomAbilities.Target.POSITION) {
            if (block.getType() != HitResult.Type.BLOCK) { return; }
            ClientPacketDistributor.sendToServer(new StaffAbilityPacket(pending.recipient().toString(), pending.ability().id(), "", block.getBlockPos(), true));
            pending = null; return;
        }
        if (block.getType() != HitResult.Type.MISS) { end = block.getLocation(); }
        var hit = ProjectileUtil.getEntityHitResult(player, start, end, new AABB(start, end).inflate(1),
                entity -> entity instanceof LivingEntity living && entity != player && !(entity instanceof AbstractPhantom)
                        && living.isAlive() && !player.isAlliedTo(living) && EntitySelector.CAN_BE_PICKED.test(entity), start.distanceToSqr(end));
        if (hit == null) { return; }
        ClientPacketDistributor.sendToServer(new PhantomismNetwork.StaffCommandPayload(PhantomismNetwork.Action.ATTACK_TARGET.networkId(), source.getId(), hit.getEntity().getId()));
        ClientPacketDistributor.sendToServer(new StaffAbilityPacket(pending.recipient().toString(), pending.ability().id(), hit.getEntity().getUUID().toString(), net.minecraft.core.BlockPos.ZERO, true));
        pending = null;
    }
}
