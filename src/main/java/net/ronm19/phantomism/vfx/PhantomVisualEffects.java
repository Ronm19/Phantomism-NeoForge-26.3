package net.ronm19.phantomism.vfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.registry.ModParticles;
import java.util.Map;
import java.util.WeakHashMap;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomVisualEffects {
    private static final int SANDSTORM_PARTICLES_PER_BURST = 36;
    private static final int MAX_ENERGY_PARTICLES_PER_TICK = 512;
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();

    private PhantomVisualEffects() {
    }

    public static void sandstormCloud(ServerLevel level, Vec3 center) {
        level.sendParticles(ModParticles.SANDSTORM_DUST.get(), center.x, center.y, center.z,
                SANDSTORM_PARTICLES_PER_BURST, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static void action(ServerLevel level, AbstractPhantom phantom, PhantomAnimationAction action) {
        PhantomVfxTheme theme = PhantomVfxTheme.of(phantom);
        Vec3 center = phantom.position().add(0.0D, phantom.getBbHeight() * 0.6D, 0.0D);
        switch (action) {
            case BITE, CRITICAL_BITE, CHARGE -> emit(level, theme, center, PhantomEnergyOptions.SLASH, 1.6F, 10, 1, 0.0D, 0.0D);
            case PROJECTILE -> emit(level, theme, center.add(phantom.getViewVector(1.0F).scale(0.7D)), PhantomEnergyOptions.GLOW, 0.9F, 8, 1, 0.0D, 0.0D);
            case SHIELD, BARRIER -> {
                aura(level, theme, center);
                emit(level, theme, center, PhantomEnergyOptions.GLOW, 1.8F, 18, 1, 0.0D, 0.0D);
                shockwave(level, theme, phantom.position().add(0.0D, 0.05D, 0.0D), 2.0F);
            }
            case ULTIMATE -> {
                impact(level, theme, center);
                shockwave(level, theme, phantom.position().add(0.0D, 0.05D, 0.0D), 4.0F);
                aura(level, theme, center);
            }
            case EMPOWER, BLESSING, SENSE -> {
                aura(level, theme, center);
                shockwave(level, theme, phantom.position().add(0.0D, 0.05D, 0.0D), 1.6F);
            }
            case HURT -> emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.3F, 8, 4, 0.2D, 0.06D);
        }
    }

    public static void impact(ServerLevel level, PhantomVfxTheme theme, Vec3 center) {
        emit(level, theme, center, PhantomEnergyOptions.GLOW, 1.3F, 14, 1, 0.0D, 0.0D);
        emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.4F, 16, 12, 0.12D, 0.16D);
        emit(level, theme, center, PhantomEnergyOptions.SLASH, 1.2F, 8, 1, 0.0D, 0.0D);
    }

    public static void trail(ServerLevel level, PhantomVfxTheme theme, Vec3 center) {
        emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.3F, 12, 2, 0.08D, 0.01D);
        emit(level, theme, center, PhantomEnergyOptions.GLOW, 0.45F, 10, 1, 0.0D, 0.0D);
    }

    public static void aura(ServerLevel level, PhantomVfxTheme theme, Vec3 center) {
        emit(level, theme, center, PhantomEnergyOptions.AURA, 1.25F, 28, 10, 0.0D, 0.0D);
    }

    public static void petals(ServerLevel level, Vec3 center, int count, float size) {
        emit(level, PhantomVfxTheme.CHERRY, center, PhantomEnergyOptions.PETAL, size, 44,
                Math.clamp(count, 1, 18), 0.35D, 0.025D);
    }

    public static void healingBlossom(ServerLevel level, Vec3 center) {
        petals(level, center, 8, 0.17F);
        shockwave(level, PhantomVfxTheme.CHERRY, center.add(0.0D, -0.45D, 0.0D), 0.7F);
    }

    public static void blossomStorm(ServerLevel level, Vec3 center) {
        emit(level, PhantomVfxTheme.CHERRY, center, PhantomEnergyOptions.AURA, 6.0F, 36, 18, 0.0D, 0.0D);
        shockwave(level, PhantomVfxTheme.CHERRY, center.add(0.0D, -0.55D, 0.0D), 6.6F);
    }

    public static void shockwave(ServerLevel level, PhantomVfxTheme theme, Vec3 center, float size) {
        emit(level, theme, center, PhantomEnergyOptions.SHOCKWAVE, size, 24, 1, 0.0D, 0.0D);
    }

    public static void beam(ServerLevel level, PhantomVfxTheme theme, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int segments = Math.clamp((int) Math.ceil(delta.length() / 2.0D), 1, 8);
        for (int segment = 0; segment <= segments; segment++) {
            emit(level, theme, start.add(delta.scale((double) segment / segments)), PhantomEnergyOptions.SPARK, 0.55F, 10, 2, 0.06D, 0.0D);
        }
    }

    private static void emit(ServerLevel level, PhantomVfxTheme theme, Vec3 center, int shape, float scale,
            int lifetime, int count, double spread, double speed) {
        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != level.getGameTime()) {
            budget.tick = level.getGameTime();
            budget.particles = 0;
        }
        int available = Math.min(count, MAX_ENERGY_PARTICLES_PER_TICK - budget.particles);
        if (available <= 0) {
            return;
        }
        budget.particles += available;
        level.sendParticles(PhantomEnergyOptions.of(theme, shape, scale, lifetime), center.x, center.y, center.z,
                available, spread, spread, spread, speed);
    }

    private static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int particles;
    }
}
