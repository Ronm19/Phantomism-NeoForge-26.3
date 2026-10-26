package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SolarPhantom;
import org.jspecify.annotations.Nullable;

public final class SolarVfx {
    public static final Identifier CHARGE = id("solar_charge"), BEAM = id("solar_annihilation"), IMPACT = id("solar_impact"), SCORCH = id("solar_scorch"),
            SANCTUARY = id("radiant_sanctuary"), BLITZ = id("sunflare_blitz"), SUPERNOVA = id("supernova_ascension"),
            JUDGMENT = id("heavenly_judgment"), CATACLYSM = id("supernova_cataclysm"), SALVATION = id("radiant_salvation"), ECLIPSE = id("celestial_bond_eclipse"), ECLIPSE_RESTORATION = id("celestial_bond_restoration");
    public static final List<Identifier> ALL = List.of(CHARGE, BEAM, IMPACT, SCORCH, SANCTUARY, BLITZ, SUPERNOVA, JUDGMENT, CATACLYSM, SALVATION, ECLIPSE, ECLIPSE_RESTORATION);
    private SolarVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(SolarPhantom source, Identifier effect, @Nullable Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
                || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted();
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0xFFB52C, 0xFFF5DB, source.getRandom().nextLong()));
    }
    public static void cancel(SolarPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
