package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.InfernalPhantom;
import org.jspecify.annotations.Nullable;

public final class InfernalPhantomVfx {
    public static final Identifier BOLT = id("bolt"), IMPACT = id("impact"), RUSH = id("hellrush"), FIELD = id("field"), ERUPTION = id("eruption"), OVERHEAT = id("overheat"), ASH = id("ash"), GUARD = id("cinder_guard"), ASCENDANT = id("hell_ascendant");
    public static final List<Identifier> ALL = List.of(BOLT, IMPACT, RUSH, FIELD, ERUPTION, OVERHEAT, ASH, GUARD, ASCENDANT);
    private InfernalPhantomVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "infernal_phantom/" + name); }
    public static void emit(InfernalPhantom source, Identifier kind, @Nullable Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr()) || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted(); CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0xFF651A, 0xFFDB7F, source.getRandom().nextLong()));
    }
    public static void cancel(InfernalPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
