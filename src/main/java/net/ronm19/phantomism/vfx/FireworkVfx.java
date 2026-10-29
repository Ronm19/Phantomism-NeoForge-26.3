package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.FireworkPhantom;

public final class FireworkVfx {
    public static final Identifier BOLT = id("bolt"), IMPACT = id("impact"), RUSH = id("rush"), STARBURST = id("starburst"), GUARD = id("guard"), FINALE = id("finale"), VOLLEY = id("volley"), FINAL_BURST = id("final_burst"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(BOLT, IMPACT, RUSH, STARBURST, GUARD, FINALE, VOLLEY, FINAL_BURST, DEFEAT);
    private static final DustParticleOptions RED = new DustParticleOptions(0xEA4658, .8F), WHITE = new DustParticleOptions(0xF4F4E9, .8F), BLUE = new DustParticleOptions(0x4C9EFF, .8F);
    private FireworkVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "firework_phantom/" + name); }
    public static void emit(FireworkPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            // Only the visual envelope outlasts the twelve-second attack, for a soft one-second fade.
            if (kind.equals(FINALE)) { attached = source; duration += 20; }
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0xF2F4FF, 0x3D8DFF, source.getRandom().nextLong()));
        }
    }
    public static void cancel(FireworkPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xF2F4FF, 0x3D8DFF, 0)); }
    }
    public static void trail(ServerLevel level, Vec3 point, int count) {
        for (DustParticleOptions color : List.of(RED, WHITE, BLUE)) { level.sendParticles(color, point.x, point.y, point.z, Math.clamp(count, 1, 3), .15D, .15D, .15D, .005D); }
        level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, 1, .1D, .1D, .1D, .02D);
    }
    public static void burst(ServerLevel level, Vec3 point, int count) {
        for (DustParticleOptions color : List.of(RED, WHITE, BLUE)) { level.sendParticles(color, point.x, point.y, point.z, Math.clamp(count / 3, 1, 4), .6D, .6D, .6D, .02D); }
        level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, Math.clamp(count, 1, 12), .2D, .2D, .2D, .15D);
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 1F, .98F, .94F), point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
}
