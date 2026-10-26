package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.EclipsePhantom;

public final class EclipseVfx {
    public static final Identifier SHIFT = id("eclipse_shift"), HEAL = id("sunbound_recovery"), GUARD = id("solar_guard"), PULSE = id("solar_pulse"), FANG = id("lunar_fang"), RUSH = id("lunar_rush"), BOLT = id("eclipse_bolt"), REND = id("hybrid_rend"), HYBRID_GUARD = id("hybrid_guard"), TOTAL = id("total_eclipse"), JUDGMENT = id("eclipse_judgment"), IMPACT = id("eclipse_impact");
    public static final int SOLAR = 0xFFC757, LUNAR = 0xAD6DFF;
    private EclipseVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(EclipsePhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        int primary = source.stance() == EclipsePhantom.Stance.SOLAR ? SOLAR : LUNAR;
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, primary, primary == SOLAR ? LUNAR : SOLAR, source.getRandom().nextLong()));
    }
    public static void cancel(EclipsePhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
