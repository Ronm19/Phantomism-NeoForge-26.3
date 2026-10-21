package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.MiragePhantom;

public final class MirageVfx {
    public static final Identifier FAKE_BOLT = id("mirror_false_bolt");
    public static final Identifier FORM = id("mirror_form"), BOLT = id("mirage_bolt"), STRIKE = id("illusory_strike"), RESCUE = id("decoy_rescue"), FIELD = id("mirage_field"), GUARD = id("mirror_guard"), SHATTER = id("mirror_shatter"), OPENING = id("false_opening"), HALL = id("hall_of_mirrors"), CONVERGE = id("mirror_convergence"), FINALE = id("true_reflection");
    private MirageVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(MiragePhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0xBE92EF, 0x72F5F5, source.getRandom().nextLong()));
    }
    public static void cancel(MiragePhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
