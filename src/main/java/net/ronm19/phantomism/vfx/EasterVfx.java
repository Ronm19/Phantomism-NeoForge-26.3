package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.EasterPhantom;

public final class EasterVfx {
    public static final Identifier EGG = id("egg"), IMPACT = id("impact"), LEAP = id("leap"), RENEWAL = id("renewal"), EVADE = id("evade"), AWAKENING = id("awakening"), BLOOM = id("bloom"), BLOSSOM = id("blossom"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(EGG, IMPACT, LEAP, RENEWAL, EVADE, AWAKENING, BLOOM, BLOSSOM, DEFEAT);
    private EasterVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "easter_phantom/" + name); }
    public static void emit(EasterPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0xE7A7CE, 0xA8D8C2, source.getRandom().nextLong()));
        }
    }
    public static void cancel(EasterPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xE7A7CE, 0xA8D8C2, 0)); }
    }
    public static void motes(ServerLevel level, Vec3 point, int count) {
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, point.x, point.y, point.z, Math.clamp(count, 1, 4), .3D, .3D, .3D, .01D);
    }
}
