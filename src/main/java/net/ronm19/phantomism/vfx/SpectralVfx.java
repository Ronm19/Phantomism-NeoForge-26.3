package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SpectralPhantom;

public final class SpectralVfx {
    public static final Identifier STRIKE = id("spectral_strike"), DIVE = id("ghost_dive"), SLIP = id("spirit_slip"), MARK = id("soul_mark"),
            PASSAGE = id("spectral_passage"), EMERGE = id("spectral_emerge"), LANCE = id("spectral_lance"), IMPACT = id("spectral_impact"),
            GUARD = id("ethereal_guard"), PLANE = id("ethereal_plane"), FOLD = id("ethereal_fold"), CLOSE_MARK = id("spectral_close_mark");
    private SpectralVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(SpectralPhantom source, Identifier kind, Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0x9DEFFF, 0xA58AFF, level.getRandom().nextLong()));
    }
    public static void closeMark(SpectralPhantom source) { emit(source, CLOSE_MARK, null, source.position(), Vec3.ZERO, 1, 1); }
    public static void cancel(SpectralPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
