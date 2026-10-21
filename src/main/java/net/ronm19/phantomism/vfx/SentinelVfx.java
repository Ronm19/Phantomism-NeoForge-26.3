package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SentinelPhantom;

public final class SentinelVfx {
    public static final Identifier REPULSE = id("sentinel_repulse"), CLEAVE = id("sentinel_cleave"), BREAK = id("sentinel_break"),
            PULSE = id("guardian_pulse"), MARK = id("vengeance_mark"), GUARD = id("sentinel_guard"), BASTION = id("last_bastion"), IMPACT = id("sentinel_impact");
    private SentinelVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(SentinelPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x9CBDE3, 0xC7F0FF, level.getRandom().nextLong()));
    }
    public static void cancel(SentinelPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
