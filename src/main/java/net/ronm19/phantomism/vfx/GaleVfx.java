package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.GalePhantom;

public final class GaleVfx {
    public static final Identifier BOLT=id("gale_bolt"), SLIPSTREAM=id("gale_slipstream"), UPDRAFT=id("gale_updraft"), CROSSWIND=id("gale_crosswind"), RUSH=id("gale_rush"), BLADE=id("gale_blade"), VORTEX=id("gale_vortex"), RESCUE=id("gale_rescue"), TAILWIND=id("gale_tailwind"), DOMINION=id("gale_dominion"), TEMPEST=id("gale_tempest"), JUDGMENT=id("gale_judgment"), IMPACT=id("gale_impact");
    private GaleVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(GalePhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        source.visualStarted();
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, source.variant() == 2 ? 0x1A9BFA : source.variant() == 1 ? 0x7B98FF : 0x2FCBFF, 0xE5FAFF, source.getRandom().nextLong()));
    }
    public static void cancel(GalePhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
