package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.RiftPhantom;
import org.jspecify.annotations.Nullable;

public final class RiftVfx {
    public static final Identifier BOLT = id("rift_bolt"), IMPACT = id("rift_impact"), TRANSFER = id("rift_transfer"), PORTAL = id("rift_portal"),
            ANCHOR = id("rift_anchor"), STEP = id("rift_step"), LOCK = id("spatial_lock"), SENSE = id("rift_sense"), ULTIMATE = id("fractured_space"), COLLAPSE = id("spatial_collapse"), CLOSE_ANCHOR = id("close_rift_anchor"), CLOSE_PORTALS = id("close_rift_portals");
    public static final Identifier RESCUE_OUT = id("rift_rescue_out"), RESCUE_IN = id("rift_rescue_in"), THROW_OUT = id("rift_throw_out"), THROW_IN = id("rift_throw_in"), TRAVEL_OUT = id("rift_travel_out"), TRAVEL_IN = id("rift_travel_in"), STEP_OUT = id("rift_step_out"), STEP_IN = id("rift_step_in"), DASH = id("rift_dash"), DASH_IMPACT = id("rift_dash_impact");
    public enum Transfer { RESCUE, THROW, CORRIDOR, STEP }
    private RiftVfx() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }
    public static void emit(RiftPhantom source, Identifier effect, @Nullable Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        emit(source, effect, attached, position, direction, duration, radius, level.getRandom().nextLong());
    }
    private static void emit(RiftPhantom source, Identifier effect, @Nullable Entity attached, Vec3 position, Vec3 direction, int duration, float radius, long seed) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0xC27BFF, 0x5EE5FF, seed));
    }
    public static void transfer(RiftPhantom source, Vec3 from, Vec3 to) {
        transfer(source, from, to, Transfer.CORRIDOR);
    }
    public static void transfer(RiftPhantom source, Vec3 from, Vec3 to, Transfer kind) {
        if (from.distanceToSqr(to) < .01D) { return; }
        Identifier departure = switch (kind) { case RESCUE -> RESCUE_OUT; case THROW -> THROW_OUT; case STEP -> STEP_OUT; case CORRIDOR -> TRAVEL_OUT; };
        Identifier arrival = switch (kind) { case RESCUE -> RESCUE_IN; case THROW -> THROW_IN; case STEP -> STEP_IN; case CORRIDOR -> TRAVEL_IN; };
        if (!(source.level() instanceof ServerLevel level)) { return; } long seed = level.getRandom().nextLong();
        emit(source, departure, null, from, to.subtract(from), 18, .8F, seed); emit(source, arrival, null, to, from.subtract(to), 18, .8F, seed);
    }
    public static void cancel(RiftPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
