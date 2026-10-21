package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.OmenPhantom;

public final class OmenVfx {
    public static final Identifier BOLT = id("omen_bolt"), MARK = id("ominous_mark"), STANDARD = id("dark_standard"), SHIELD = id("omen_shield"),
            DIVE = id("condemned_dive"), CALAMITY = id("calamity"), JUDGMENT = id("omen_judgment"), CONCLUSION = id("omen_conclusion"), HARBINGER = id("harbinger"), IMPACT = id("omen_impact"), REVEAL = id("omen_reveal"), CLEAR_MARK = id("omen_clear_mark");
    private OmenVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(OmenPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x1AF599, 0xB0FFE5, level.getRandom().nextLong()));
    }
    public static void cancel(OmenPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
