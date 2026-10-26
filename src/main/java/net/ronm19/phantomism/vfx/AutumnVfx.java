package net.ronm19.phantomism.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.AutumnPhantom;
import org.jspecify.annotations.Nullable;

public final class AutumnVfx {
    public static final Identifier LEAVES = id("leaves"), SENSE = id("sense"), BOLT = id("bolt"), IMPACT = id("impact"), RUSH = id("rush"), GUARD = id("guard"), GIFT = id("gift"), HARVEST = id("harvest"), GALE = id("gale"), FINALE = id("finale");
    public static final List<Identifier> ALL = List.of(LEAVES, SENSE, BOLT, IMPACT, RUSH, GUARD, GIFT, HARVEST, GALE, FINALE);
    private AutumnVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", "autumn_phantom/" + name); }
    public static void emit(AutumnPhantom source, Identifier kind, @Nullable Entity attached, Vec3 point, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr()) || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted(); CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), kind, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), point, direction, duration, radius, 0xED802D, 0xF7D786, source.getRandom().nextLong()));
    }
    public static void sense(AutumnPhantom source, net.minecraft.server.level.ServerPlayer owner, Vec3 point) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner, new VfxPacket(source.level().dimension().toString(), SENSE, source.getUUID(), source.session(), -1, new UUID(0, 0), point, Vec3.ZERO, 35, 1, 0xEDB95B, 0x97C96D, source.getRandom().nextLong()));
    }
    public static void cancel(AutumnPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
