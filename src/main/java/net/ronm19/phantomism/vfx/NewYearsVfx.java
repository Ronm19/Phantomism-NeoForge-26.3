package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.NewYearsPhantom;

public final class NewYearsVfx {
    public static final Identifier FORTUNE = id("fortune"), SPARK = id("spark"), IMPACT = id("impact"), DASH = id("dash"), SHIELD = id("shield"), WARD = id("ward"), COUNTDOWN = id("countdown"), WAVE = id("wave"), FINAL = id("final"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(FORTUNE, SPARK, IMPACT, DASH, SHIELD, WARD, COUNTDOWN, WAVE, FINAL, DEFEAT);
    private static final List<DustParticleOptions> COLORS = List.of(new DustParticleOptions(0xF8D283, .7F), new DustParticleOptions(0xDCEAF5, .6F), new DustParticleOptions(0x83B7F5, .6F), new DustParticleOptions(0xFFF9E4, .5F));
    private NewYearsVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "new_years_phantom/" + name); }
    public static void emit(NewYearsPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0xF8D283, 0xDCEAF5, source.getRandom().nextLong()));
        }
    }
    public static void cancel(NewYearsPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xF8D283, 0xDCEAF5, 0)); }
    }
    public static void spark(ServerLevel level, Vec3 point, int count) {
        for (DustParticleOptions color : COLORS) { level.sendParticles(color, point.x, point.y, point.z, Math.clamp(count, 1, 3), .16D, .16D, .16D, .01D); }
        level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, 1, .12D, .12D, .12D, .01D);
    }
    /** Native decoration only: no explosion entity, blast damage or block changes. */
    public static void fireworks(ServerLevel level, Vec3 point, int count) {
        int bounded = Math.clamp(count, 1, 4);
        for (int burst = 0; burst < bounded; burst++) {
            double angle = burst * Math.PI * 2 / bounded;
            Vec3 place = point.add(Math.cos(angle) * 3, burst % 2, Math.sin(angle) * 3);
            for (DustParticleOptions color : COLORS) { level.sendParticles(color, place.x, place.y, place.z, 3, .7D, .7D, .7D, .03D); }
            level.sendParticles(ParticleTypes.FIREWORK, place.x, place.y, place.z, 4, .5D, .5D, .5D, .06D);
        }
    }
}
