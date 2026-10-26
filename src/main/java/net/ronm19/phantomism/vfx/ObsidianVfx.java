package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ObsidianPhantom;

public final class ObsidianVfx {
    public static final Identifier SHARD=id("obsidian_shard"), RAM=id("obsidian_ram"), SLAM=id("obsidian_slam"), BASTION=id("obsidian_bastion"), SHELTER=id("obsidian_shelter"), FRACTURE=id("obsidian_fracture"), GUARD=id("obsidian_guard"), CITADEL=id("obsidian_citadel"), WALL=id("obsidian_wall"), JUDGMENT=id("obsidian_judgment"), IMPACT=id("obsidian_impact"), ABSORB=id("obsidian_absorb");
    private ObsidianVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(ObsidianPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, source.variant() == 2 ? 0x6526AB : source.variant() == 1 ? 0xD03B95 : 0x8D27D9, 0xE5B9FF, source.getRandom().nextLong()));
    }
    public static void cancel(ObsidianPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
