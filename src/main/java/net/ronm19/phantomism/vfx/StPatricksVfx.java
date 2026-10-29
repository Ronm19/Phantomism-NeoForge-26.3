package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.StPatricksPhantom;

public final class StPatricksVfx {
    public static final Identifier FAVOR = id("favor"), BURST = id("burst"), IMPACT = id("impact"), DASH = id("dash"), AURA = id("aura"), TRICK = id("trick"), SKY = id("sky"), WAVE = id("wave"), FINAL = id("final"), DEFEAT = id("defeat");
    public static final List<Identifier> ALL = List.of(FAVOR, BURST, IMPACT, DASH, AURA, TRICK, SKY, WAVE, FINAL, DEFEAT);
    private static final List<DustParticleOptions> COLORS = List.of(new DustParticleOptions(0x68DE77, .75F), new DustParticleOptions(0xF6D578, .65F), new DustParticleOptions(0xF3F6D8, .55F));
    private StPatricksVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "st_patricks_phantom/" + name); }
    public static void emit(StPatricksPhantom source, Identifier kind, Entity attached, Vec3 point, int duration, float radius) {
        if (source.level() instanceof ServerLevel level && Double.isFinite(point.lengthSqr())) {
            CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                    attached == null ? new UUID(0, 0) : attached.getUUID(), point, Vec3.ZERO, duration, radius, 0x67DF7B, 0xF5D488, source.getRandom().nextLong()));
        }
    }
    public static void cancel(StPatricksPhantom source) {
        if (source.level() instanceof ServerLevel level) { CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), CinematicVfx.CANCEL, source.getUUID(), source.session(), -1, new UUID(0, 0), source.position(), Vec3.ZERO, 1, 1, 0x67DF7B, 0xF5D488, 0)); }
    }
    public static void spark(ServerLevel level, Vec3 point, int count) {
        for (DustParticleOptions color : COLORS) { level.sendParticles(color, point.x, point.y, point.z, Math.clamp(count, 1, 3), .16D, .16D, .16D, .01D); }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, .12D, .12D, .12D, .01D);
    }
}
