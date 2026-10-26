package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.IllagerPhantom;
import org.jspecify.annotations.Nullable;

public final class IllagerVfx {
    public static final Identifier CROSSBOW = id("illager_crossbow"), BOLT = id("illager_bolt"), IMPACT = id("illager_impact"), WRATH = id("vindicators_wrath"),
            DOMINION = id("evokers_dominion"), FANGS = id("illager_fangs"), CHARM = id("illager_charm"), CONQUEST = id("illagers_conquest"), EXECUTION = id("illager_execution");
    public static final List<Identifier> ALL = List.of(CROSSBOW, BOLT, IMPACT, WRATH, DOMINION, FANGS, CHARM, CONQUEST, EXECUTION);
    private IllagerVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", name); }
    public static void emit(IllagerPhantom source, Identifier kind, @Nullable Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr()) || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted(); CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0x4CE3AA, 0xB93554, source.getRandom().nextLong()));
    }
    public static void cancel(IllagerPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
