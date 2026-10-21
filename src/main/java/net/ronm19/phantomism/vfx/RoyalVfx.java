package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PhantomKing;

public final class RoyalVfx {
    public static final Identifier PRESENCE = id("kings_presence"), RAM = id("royal_ram"), RAM_IMPACT = id("royal_ram_impact"), JUDGMENT = id("kings_judgment"), STRIKE = id("kings_strike"), STRIKE_ARC = id("royal_strike_arc"), ROAR = id("kings_roar"), COMMAND = id("royal_command"), DIRECTIVE = id("royal_directive"), RALLY = id("royal_rally"), GUARD = id("royal_guard"), INTERCEPT = id("royal_intercept"), DOMINION = id("kings_dominion"), MARK = id("royal_decree_mark"), FINALE = id("final_decree"), IMPACT = id("royal_impact"), WARNING = id("royal_warning"), HESITATE = id("royal_hesitation");
    private RoyalVfx() {}
    private static Identifier id(String value) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, value); }
    public static void emit(PhantomKing source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        if (!effect.equals(CinematicVfx.CANCEL)) { source.visualStarted(); }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0xEFB73E, 0xFFECB0, source.getRandom().nextLong()));
    }
    public static void cancel(PhantomKing source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
