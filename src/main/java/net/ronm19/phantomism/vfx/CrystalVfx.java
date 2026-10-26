package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.CrystalPhantom;

public final class CrystalVfx {
    public static final Identifier SHARD=id("crystal_shard"), BURST=id("shard_burst"), COUNTER=id("crystal_counter"), BARRIER=id("crystal_barrier"), SHELL=id("crystal_shell"), IMPACT_CHARGE=id("stored_impact"), REFLECT=id("crystal_reflect"), RECOVER=id("fracture_recovery"), DOMINION=id("prismatic_dominion"), REFRACTION=id("prismatic_refraction"), SHATTER=id("prismatic_shatter"), IMPACT=id("crystal_impact"), SHIELD_HIT=id("crystal_shield_hit");
    private CrystalVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(CrystalPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, source.variant() == 2 ? 0x65D7FF : source.variant() == 1 ? 0xA759F1 : 0xBA87FF, 0xB4F3FF, source.getRandom().nextLong()));
    }
    public static void cancel(CrystalPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
