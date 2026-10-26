package net.ronm19.phantomism.vfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.SolarPhantom;

public final class TotalEclipseVfx {
    public static final int DURATION = 60, MAX_HEALING_BEAMS = 8;
    private TotalEclipseVfx() {}
    public static void start(SolarPhantom solar, AbstractPhantom lunar, int healed, boolean protectedArea, int radius) {
        if (!(solar.level() instanceof ServerLevel level)) { return; }
        solar.visualStarted();
        CinematicVfx.emit(level, new VfxPacket(level.dimension().toString(), SolarVfx.ECLIPSE, solar.getUUID(), solar.session(),
                lunar.getId(), lunar.getUUID(), solar.position(), new Vec3(Math.clamp(healed, 0, MAX_HEALING_BEAMS), protectedArea ? 1 : 0, 0),
                DURATION, radius, 0xFFC65C, 0xA8CEFF, solar.getRandom().nextLong()));
    }
    public static void restored(SolarPhantom solar, LivingEntity ward) {
        SolarVfx.emit(solar, SolarVfx.ECLIPSE_RESTORATION, ward, ward.position(), Vec3.ZERO, 26, 1.25F);
    }
}
