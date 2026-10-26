package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SporePhantom;
import org.jspecify.annotations.Nullable;

public final class SporeVfx {
    public static final Identifier BREATH = id("spore_breath"), CLOUD = id("spore_cloud"), BLOOM = id("mushroom_bloom"), DIVE = id("decay_dive"), STORM = id("fungal_storm");
    private SporeVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(SporePhantom source, Identifier effect, @Nullable Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
                || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted();
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(),
                attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x94C846, 0xE5E9B3, source.getRandom().nextLong()));
    }
    public static void cancel(SporePhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
