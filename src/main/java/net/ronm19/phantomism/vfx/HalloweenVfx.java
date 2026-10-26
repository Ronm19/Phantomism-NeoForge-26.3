package net.ronm19.phantomism.vfx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.HalloweenPhantom;

public final class HalloweenVfx {
    public static final Identifier GAZE = id("gaze"), IMPACT = id("impact"), DIVE = id("dive"), SCREECH = id("screech"), FRIGHT = id("fright"), VEIL = id("veil"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(GAZE, IMPACT, DIVE, SCREECH, FRIGHT, VEIL, DEFEAT);
    private HalloweenVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "halloween_phantom/" + name); }
    public static void emit(HalloweenPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr())) { return; }
        source.visualStarted();
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(),
                attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(),
                point, Vec3.ZERO, duration, radius, 0xFF991F, 0xAD65ED, source.getRandom().nextLong()));
    }
    public static void cancel(HalloweenPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL,
                source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0xFF991F, 0xAD65ED, 0)); }
    }

    public static void burst(ServerLevel level, Vec3 point, int count) {
        level.sendParticles(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z, Math.clamp(count, 1, 28), .25D, .2D, .25D, .012D);
    }

    public static void mist(ServerLevel level, Vec3 point) {
        level.sendParticles(ParticleTypes.WITCH, point.x, point.y + .5D, point.z, 7, .8D, .3D, .8D, .02D);
        level.sendParticles(ParticleTypes.SMOKE, point.x, point.y + .5D, point.z, 4, .6D, .3D, .6D, .01D);
    }

    public static void ring(ServerLevel level, Vec3 center, double radius, int count, boolean spectral) {
        int samples = Math.clamp(count, 8, 48);
        for (int point = 0; point < samples; point++) {
            double angle = Math.PI * 2 * point / samples;
            level.sendParticles(spectral ? ParticleTypes.WITCH : ParticleTypes.SMALL_FLAME,
                    center.x + Math.cos(angle) * radius, center.y + .35D, center.z + Math.sin(angle) * radius,
                    1, .03D, .06D, .03D, .005D);
        }
    }

    public static void ritual(ServerLevel level, Vec3 center, int elapsed) {
        ring(level, center, 5 + Math.min(8, elapsed / 10D), 24, elapsed % 10 == 0);
        for (int orbit = 0; orbit < 4; orbit++) {
            double angle = elapsed * .075D + orbit * Math.PI / 2;
            Vec3 point = center.add(Math.cos(angle) * 6, 1.2D + Math.sin(elapsed * .12D + orbit), Math.sin(angle) * 6);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.JACK_O_LANTERN.defaultBlockState()),
                    point.x, point.y, point.z, 3, .18D, .18D, .18D, .01D);
            level.sendParticles(ParticleTypes.SOUL, point.x, point.y + .6D, point.z, 1, .1D, .3D, .1D, .01D);
            level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 2, .3D, .15D, .3D, .01D);
        }
    }
}
