package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.ChristmasPhantom;

public final class ChristmasVfx {
    public static final Identifier SNOWBALL = id("snowball"), IMPACT = id("impact"), DIVE = id("dive"), GIFT = id("gift"), GUARD = id("guard"), MIRACLE = id("miracle"), PULSE = id("pulse"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(SNOWBALL, IMPACT, DIVE, GIFT, GUARD, MIRACLE, PULSE, DEFEAT);
    private ChristmasVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "christmas_phantom/" + name); }
    public static void emit(ChristmasPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0xFFE2A0, 0xB7283B, source.getRandom().nextLong()));
        }
    }
    public static void cancel(ChristmasPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xFFE2A0, 0xB7283B, 0)); }
    }
    public static void sparkle(ServerLevel level, Vec3 point, int count) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, point.x, point.y, point.z, Math.clamp(count, 1, 16), .5D, .6D, .5D, .015D);
        level.sendParticles(ParticleTypes.WAX_ON, point.x, point.y, point.z, Math.clamp(count / 2, 1, 8), .4D, .4D, .4D, .02D);
    }
    public static void burst(ServerLevel level, Vec3 point, int count) { sparkle(level, point, count); }
    public static void ring(ServerLevel level, Vec3 point, double radius, int count, boolean ignored) { sparkle(level, point, 12); }
}
