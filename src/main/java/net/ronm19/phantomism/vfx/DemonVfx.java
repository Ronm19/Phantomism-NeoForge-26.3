package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.DemonPhantom;

public final class DemonVfx {
    public static final Identifier AURA = id("demon_aura"), BITE = id("infernal_bite"), BREATH = id("hellfire_breath"), DASH = id("shadow_dash"), WAVE = id("destruction_wave"),
            METEOR = id("flame_meteor"), IMPACT = id("demon_meteor_impact"), DRAIN = id("demon_soul_drain"), HOWL = id("fear_howl"), FORM = id("demon_form"), APOCALYPSE = id("apocalypse"), FINALE = id("apocalypse_finale");
    private DemonVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(DemonPhantom source, Identifier kind, Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0xFF203B, 0xFF9D38, level.getRandom().nextLong()));
    }
    public static void cancel(DemonPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
