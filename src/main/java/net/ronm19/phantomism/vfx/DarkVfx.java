package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.DarkPhantom;

public final class DarkVfx {
    public static final Identifier PRESSURE = id("shadow_pressure"), BOLT = id("darkness_projectile"), IMPACT = id("darkness_impact"), REND = id("dark_rend"), RUSH = id("darkness_rush"), RUSH_IMPACT = id("darkness_rush_impact"), BLACKOUT = id("blackout"), DISRUPT = id("dark_disruption"), BUBBLE = id("bubble_of_darkness"), SHIELD = id("shield_of_darkness"), SHIELD_HIT = id("dark_shield_hit"), HOUR = id("dark_hour"), MARK = id("dark_hour_mark"), BLADE = id("dark_spectral_blade"), BLADE_IMPACT = id("dark_blade_impact"), FINALE = id("dark_hour_finale"), WARNING = id("dark_warning");
    private DarkVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(DarkPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        if (!effect.equals(CinematicVfx.CANCEL)) { source.visualStarted(); }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x6516B3, 0xDC89FA, source.getRandom().nextLong()));
    }
    public static void cancel(DarkPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
