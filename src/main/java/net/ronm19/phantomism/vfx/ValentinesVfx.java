package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.ValentinesPhantom;

public final class ValentinesVfx {
    public static final Identifier BOND = id("bond"), BOLT = id("bolt"), IMPACT = id("impact"), DASH = id("dash"), AURA = id("aura"), CHARM = id("charm"), EMBRACE = id("embrace"), SERENADE = id("serenade"), WAVE = id("wave"), FINAL = id("final"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(BOND, BOLT, IMPACT, DASH, AURA, CHARM, EMBRACE, SERENADE, WAVE, FINAL, DEFEAT);
    private static final List<DustParticleOptions> COLORS = List.of(new DustParticleOptions(0xEF83AD, .65F), new DustParticleOptions(0xFFE1ED, .55F), new DustParticleOptions(0xE7BA7D, .45F));
    private ValentinesVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "valentines_phantom/" + name); }
    public static void emit(ValentinesPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0xEF83AD, 0xFFE1ED, source.getRandom().nextLong()));
        }
    }
    public static void cancel(ValentinesPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xEF83AD, 0xFFE1ED, 0)); }
    }
    public static void spark(ServerLevel level, Vec3 point, int count) {
        for (DustParticleOptions color : COLORS) { level.sendParticles(color, point.x, point.y, point.z, Math.clamp(count, 1, 3), .16D, .16D, .16D, .01D); }
        level.sendParticles(ParticleTypes.HEART, point.x, point.y, point.z, 1, .12D, .12D, .12D, .01D);
    }
}
