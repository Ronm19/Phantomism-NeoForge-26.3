package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.vfx.CinematicVfx;
import net.ronm19.phantomism.vfx.VfxPacket;

@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public final class VfxManager {
    private static final List<VfxEffect> ACTIVE = new ArrayList<>();
    private static ClientLevel world;
    private VfxManager() {}
    public static VfxEffect find(java.util.UUID caster, net.minecraft.resources.Identifier kind) {
        if (world != Minecraft.getInstance().level) { return null; }
        for (VfxEffect effect : ACTIVE) {
            if (effect.isAlive() && effect.packet.caster().equals(caster) && effect.packet.effect().equals(kind)) { return effect; }
        }
        return null;
    }
    @SubscribeEvent public static void worldTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) { for (VfxEffect effect : ACTIVE) { effect.remove(); } ACTIVE.clear(); world = current; }
        else { ACTIVE.removeIf(effect -> !effect.isAlive()); }
    }
    @SubscribeEvent public static void groups(RegisterParticleGroupsEvent event) { event.register(VfxEffect.GROUP, VfxGroup::new); }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) { event.register(VfxPacket.TYPE, (packet, context) -> context.enqueueWork(() -> accept(packet))); }
    public static void accept(VfxPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (world != client.level) { for (VfxEffect effect : ACTIVE) { effect.remove(); } ACTIVE.clear(); world = client.level; }
        ACTIVE.removeIf(effect -> !effect.isAlive());
        if (world == null || client.player == null || !world.dimension().toString().equals(packet.dimension())) { return; }
        if (packet.effect().equals(CinematicVfx.CANCEL)) { ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) || effect.packet.effect().equals(net.ronm19.phantomism.vfx.SolarVfx.ECLIPSE) && effect.packet.attached().equals(packet.caster())) { effect.remove(); return true; } return false; }); return; }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.OmenVfx.CLEAR_MARK)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(net.ronm19.phantomism.vfx.OmenVfx.MARK)) { effect.remove(); return true; } return false; }); return;
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.AncientVfx.CLEAR_MARK)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(net.ronm19.phantomism.vfx.AncientVfx.MARK)) { effect.remove(); return true; } return false; }); return;
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.RoyalVfx.PRESENCE)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(packet.effect())) { effect.remove(); return true; } return false; });
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.DarkVfx.PRESSURE)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(packet.effect())) { effect.remove(); return true; } return false; });
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.VoidVfx.FIELD) || packet.effect().equals(net.ronm19.phantomism.vfx.VoidVfx.ANCHOR) || packet.effect().equals(net.ronm19.phantomism.vfx.VoidVfx.MARK)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(packet.effect()) && effect.packet.entityId() == packet.entityId()) { effect.remove(); return true; } return false; });
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.PrimordialVfx.AURA)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(packet.effect())) { effect.remove(); return true; } return false; });
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.SOULS)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.SOULS)) { effect.remove(); return true; } return false; });
            if (packet.direction().x <= 0) { return; }
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.CLEAR_MARK)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.MARK)) { effect.remove(); return true; } return false; }); return;
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.SpectralVfx.CLOSE_MARK)) {
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(net.ronm19.phantomism.vfx.SpectralVfx.MARK)) { effect.remove(); return true; } return false; }); return;
        }
        if (packet.effect().equals(net.ronm19.phantomism.vfx.RiftVfx.CLOSE_PORTALS) || packet.effect().equals(net.ronm19.phantomism.vfx.RiftVfx.CLOSE_ANCHOR)) {
            var selected = packet.effect().equals(net.ronm19.phantomism.vfx.RiftVfx.CLOSE_PORTALS) ? net.ronm19.phantomism.vfx.RiftVfx.PORTAL : net.ronm19.phantomism.vfx.RiftVfx.ANCHOR;
            ACTIVE.removeIf(effect -> { if (effect.packet.caster().equals(packet.caster()) && effect.packet.session().equals(packet.session()) && effect.packet.effect().equals(selected)) { effect.remove(); return true; } return false; }); return;
        }
        VfxRegistry.Definition definition = VfxRegistry.get(packet.effect()); int quality = VfxQualitySettings.quality(client.player.position().distanceToSqr(packet.position()));
        if (definition == null || quality == 0 || ACTIVE.stream().anyMatch(effect -> effect.same(packet))) { return; }
        if (ACTIVE.size() >= 64) {
            if (!VfxRegistry.cinematic(definition)) { return; }
            VfxEffect oldest = ACTIVE.stream().filter(effect -> !VfxRegistry.cinematic(effect.definition)).findFirst().orElse(null);
            if (oldest == null) { return; } oldest.remove(); ACTIVE.remove(oldest);
        }
        VfxEffect effect = new VfxEffect(world, packet, definition, quality); ACTIVE.add(effect); client.particleEngine.add(effect);
    }
}
