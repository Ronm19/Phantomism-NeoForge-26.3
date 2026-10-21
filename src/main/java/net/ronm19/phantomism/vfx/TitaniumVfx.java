package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.GolemPhantom;

public final class TitaniumVfx {
    public static final Identifier HOOK = id("titanium_hook"), FIST = id("titanium_fist"), SLAM = id("golem_slam"), RAM = id("golem_ram"), GUARD = id("iron_guard"), SHIELD_HIT = id("titanium_shield_hit"), MINE = id("golem_mine"), CHARGE = id("titan_charge"), IMPACT = id("titans_impact");
    private TitaniumVfx() {}
    private static Identifier id(String value) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, value); }
    public static void emit(GolemPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x20BBF5, 0xD8EEFA, source.getRandom().nextLong()));
    }
    public static void cancel(GolemPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
