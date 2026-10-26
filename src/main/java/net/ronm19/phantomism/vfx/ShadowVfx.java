package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ShadowPhantom;

public final class ShadowVfx {
    public static final Identifier MARK = id("shadow_mark"), BOLT = id("shadow_bolt"), STRIKE = id("shadow_strike"), DIVE = id("shadow_dive"), STEP = id("shadow_step"), VANISH = id("shadow_vanish"), RECON = id("recon_sweep"), GUARD = id("shadow_guard"), NIGHTFALL = id("nightfall"), FINALE = id("nightfall_execution"), IMPACT = id("shadow_impact");
    private ShadowVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(ShadowPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x934BDF, 0xCEBDE8, source.getRandom().nextLong()));
    }
    public static void cancel(ShadowPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
