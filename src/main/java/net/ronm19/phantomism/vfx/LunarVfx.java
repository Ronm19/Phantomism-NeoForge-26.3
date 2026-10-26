package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.LunarPhantom;
import org.jspecify.annotations.Nullable;

public final class LunarVfx {
    public static final Identifier LANCE = id("moon_lance"), IMPACT = id("impact"), FANG = id("moon_fang"), RUSH = id("lunar_rush"), SLASH = id("crescent_slash"), MOONBREAK = id("moonbreak"), PULSE = id("moon_pulse"), VEIL = id("moonlit_veil"), ASCENDANCY = id("full_moon_ascendancy");
    public static final List<Identifier> ALL = List.of(LANCE, IMPACT, FANG, RUSH, SLASH, MOONBREAK, PULSE, VEIL, ASCENDANCY);
    private LunarVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "lunar_phantom/" + name); }
    public static void emit(LunarPhantom source, Identifier kind, @Nullable Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr()) || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted(); CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0x8DBAFF, 0xF1E8FF, source.getRandom().nextLong()));
    }
    public static void cancel(LunarPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
