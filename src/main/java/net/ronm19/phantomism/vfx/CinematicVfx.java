package net.ronm19.phantomism.vfx;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ChronoPhantom;
import org.jspecify.annotations.Nullable;

public final class CinematicVfx {
    public static final Identifier BOLT = id("temporal_bolt"), IMPACT = id("temporal_impact"), ACCELERATE = id("accelerate"), SHIFT = id("temporal_shift"), REWIND = id("rewind"),
            ANCHOR = id("temporal_anchor"), SLOW = id("slow_field"), STOP = id("time_stop"), FROZEN = id("frozen_sigil"), DIVE = id("time_fracture"),
            AWARE = id("awareness"), DISCIPLINE = id("cooldown_discipline"), BEAM = id("energy_beam"), SLASH = id("energy_slash"), ORBIT = id("orbital_sigils"), CANCEL = id("cancel");
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private CinematicVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void chrono(ChronoPhantom source, Identifier effect, @Nullable Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x83EFFF, 0xF2C567, level.getRandom().nextLong()));
    }
    public static void emit(ServerLevel level, VfxPacket packet) {
        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != level.getGameTime()) { budget.tick = level.getGameTime(); budget.events = 0; }
        if (!packet.effect().equals(CANCEL) && ++budget.events > 64) { return; }
        for (var player : level.players()) {
            if (player.connection != null && player.position().distanceToSqr(packet.position()) <= (packet.effect().equals(CANCEL) ? 128 * 128 : 72 * 72)) { PacketDistributor.sendToPlayer(player, packet); }
        }
    }
    public static void cancel(ChronoPhantom source) { chrono(source, CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
    private static final class Budget { private long tick = Long.MIN_VALUE; private int events; }
}
