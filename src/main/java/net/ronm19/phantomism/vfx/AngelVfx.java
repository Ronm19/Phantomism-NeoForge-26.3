package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AngelPhantom;

public final class AngelVfx {
    public static final Identifier TOUCH = id("angel_touch"), BLESSING = id("angel_blessing"), TRAIL = id("angel_trail"), SMITE = id("angel_smite"), DIVE = id("holy_dive"),
            SHIELD = id("angel_shield"), INTERCEPT = id("divine_intercept"), EYE = id("eye_of_the_angel"), CONCLUSION = id("angel_conclusion"), CLEANSE = id("angel_cleanse"), PRESENCE = id("blessed_presence"), IMPACT = id("angel_impact");
    private AngelVfx() {}
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static void emit(AngelPhantom source, Identifier effect, Entity attached, Vec3 position, Vec3 direction, int duration, float radius) {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        if (!Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) { return; }
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), effect, source.getUUID(), source.session(), attached == null ? -1 : attached.getId(), attached == null ? new UUID(0, 0) : attached.getUUID(), position, direction, duration, radius, 0xFFE2A0, 0xE4F3FF, level.getRandom().nextLong()));
    }
    public static void link(AngelPhantom source, Entity target) { emit(source, TOUCH, null, source.getEyePosition(), target.getBoundingBox().getCenter().subtract(source.getEyePosition()), 24, 1); }
    public static void cancel(AngelPhantom source) { emit(source, CinematicVfx.CANCEL, null, source.position(), Vec3.ZERO, 1, 1); }
}
