package net.ronm19.phantomism.client.vfx;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VfxQualitySettings {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue DENSITY;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DENSITY = builder.defineInRange("cinematicDensity", 2, 0, 3); SPEC = builder.build();
    }
    private VfxQualitySettings() {}
    public static int quality(double distanceSquared) {
        int configured = DENSITY.get(); int vanilla = Minecraft.getInstance().options.particles().get().ordinal();
        return distanceSquared > 72 * 72 || configured == 0 ? 0 : Math.max(1, Math.min(configured, 3 - vanilla) - (distanceSquared > 36 * 36 ? 1 : 0));
    }
}
