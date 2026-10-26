package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PlaguePhantom;

public final class PlagueVfx {
    public static final Identifier INFECT=id("infected"), CHAIN=id("epidemic_chain"), MUTATION=id("mutation"), HERD=id("herd_immunity"), SPIT=id("plague_spit"), DIVE=id("viral_dive"), CLOUD=id("pestilent_cloud"), HUSK=id("plague_husk"), PANDEMIC=id("pandemic"), SURGE=id("epidemic_surge"), COLLAPSE=id("pandemic_collapse"), IMPACT=id("plague_impact");
    private PlagueVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(PlaguePhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0x86C92D, 0xDBF79C, source.getRandom().nextLong()));
    }
    public static void cancel(PlaguePhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
