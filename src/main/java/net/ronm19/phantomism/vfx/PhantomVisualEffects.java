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

    public static void crosscut(ServerLevel level, PhantomVfxTheme theme, Vec3 center) {
        emit(level, theme, center.add(-0.25D, 0.15D, 0.0D), PhantomEnergyOptions.SLASH, 1.7F, 10, 1, 0.0D, 0.0D);
        emit(level, theme, center.add(0.25D, -0.15D, 0.0D), PhantomEnergyOptions.SLASH, 1.5F, 12, 1, 0.0D, 0.0D);
        emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.3F, 14, 8, 0.15D, 0.08D);
    }

    public static void sonicBeam(ServerLevel level, Vec3 start, Vec3 end, boolean ultimate) {
        Vec3 delta = end.subtract(start);
        int segments = Math.clamp((int) Math.ceil(delta.length() / 3.5D), 1, 9);
        for (int segment = 0; segment <= segments; segment++) {
            Vec3 point = start.add(delta.scale((double) segment / segments));
            emit(level, PhantomVfxTheme.SCULK, point, PhantomEnergyOptions.SONIC_RING,
                    ultimate ? 1.0F : 0.55F, 16 + segment, 1, 0.0D, 0.0D);
            emit(level, PhantomVfxTheme.SCULK, point, PhantomEnergyOptions.GLOW, ultimate ? 0.4F : 0.18F, 12, 1, 0.0D, 0.0D);
        }
    }

    public static void sculkSpread(ServerLevel level, Vec3 center) {
        shockwave(level, PhantomVfxTheme.SCULK, center, 3.5F);
        for (int flare = 0; flare < 8; flare++) {
            double angle = flare * Math.PI / 4.0D;
            Vec3 point = center.add(Math.cos(angle) * 4.0D, 0.35D, Math.sin(angle) * 4.0D);
            emit(level, PhantomVfxTheme.SCULK, point, PhantomEnergyOptions.SPARK, 0.3F, 18, 1, 0.0D, 0.025D);
        }
    }

    public static void toxicMist(ServerLevel level, Vec3 center, float size) {
        emit(level, PhantomVfxTheme.TOXIC, center, PhantomEnergyOptions.AURA, size, 30, 12, 0.0D, 0.0D);
        shockwave(level, PhantomVfxTheme.TOXIC, center.add(0.0D, -0.3D, 0.0D), size);
    }

    public static void acidRain(ServerLevel level, Vec3 center, float radius) {
        for (int drop = 0; drop < 16; drop++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * Math.clamp(radius, 1.0F, 12.0F);
            Vec3 position = center.add(Math.cos(angle) * distance, 4.0D + level.getRandom().nextDouble() * 2.0D, Math.sin(angle) * distance);
            emit(level, PhantomVfxTheme.TOXIC, position, PhantomEnergyOptions.SPARK, 0.18F, 22, 1, 0.0D, 0.0D);
        }
    }

    public static void trail(ServerLevel level, PhantomVfxTheme theme, Vec3 center) {
        emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.3F, 12, 2, 0.08D, 0.01D);
        emit(level, theme, center, PhantomEnergyOptions.GLOW, 0.45F, 10, 1, 0.0D, 0.0D);
    }

    public static void rift(ServerLevel level, Vec3 center, float scale) {
        emit(level, PhantomVfxTheme.END, center, PhantomEnergyOptions.SONIC_RING, scale, 18, 1, 0.0D, 0.0D);
        emit(level, PhantomVfxTheme.END, center, PhantomEnergyOptions.GLOW, scale * 0.6F, 12, 1, 0.0D, 0.0D);
        emit(level, PhantomVfxTheme.END, center, PhantomEnergyOptions.SPARK, 0.2F, 20, 8, 0.25D, 0.05D);
    }

    public static void endMotes(ServerLevel level, Vec3 center) {
        emit(level, PhantomVfxTheme.END, center, PhantomEnergyOptions.SPARK, 0.12F, 24, 2, 0.45D, 0.01D);
    }

    public static void undeadMotes(ServerLevel level, Vec3 center) {
        emit(level, PhantomVfxTheme.ZOMBIE, center, PhantomEnergyOptions.SPARK, 0.1F, 24, 2, 0.4D, 0.008D);
    }

    public static void undeadRevival(ServerLevel level, Vec3 center) {
        shockwave(level, PhantomVfxTheme.ZOMBIE, center.add(0, -0.35D, 0), 4.5F);
        emit(level, PhantomVfxTheme.ZOMBIE, center, PhantomEnergyOptions.GLOW, 2.5F, 24, 1, 0, 0);
        emit(level, PhantomVfxTheme.ZOMBIE, center, PhantomEnergyOptions.AURA, 2.7F, 36, 14, 0, 0);
        emit(level, PhantomVfxTheme.ZOMBIE, center, PhantomEnergyOptions.SPARK, 0.35F, 32, 24, 0.5D, 0.16D);
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

    public static void physicalImpact(ServerLevel level, Vec3 center, float radius) {
        impact(level, PhantomVfxTheme.RAVAGER, center.add(0.0D, 0.15D, 0.0D));
        shockwave(level, PhantomVfxTheme.RAVAGER, center, Math.clamp(radius, 1.0F, 5.0F));
        var below = net.minecraft.core.BlockPos.containing(center).below();
        if (level.hasChunkAt(below)) {
            var state = level.getBlockState(below);
            if (!state.isAir()) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        center.x, center.y, center.z, 12, 0.6D, 0.12D, 0.6D, 0.13D);
            }
        }
    }

    public static void roar(ServerLevel level, PhantomVfxTheme theme, Vec3 center, float radius) {
        emit(level, theme, center, PhantomEnergyOptions.SONIC_RING, radius * 0.45F, 24, 1, 0.0D, 0.0D);
        shockwave(level, theme, center.add(0.0D, -0.4D, 0.0D), radius);
        emit(level, theme, center, PhantomEnergyOptions.SPARK, 0.35F, 16, 8, 0.5D, 0.12D);
    }

    public static void beam(ServerLevel level, PhantomVfxTheme theme, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int segments = Math.clamp((int) Math.ceil(delta.length() / 2.0D), 1, 8);
        for (int segment = 0; segment <= segments; segment++) {
            emit(level, theme, start.add(delta.scale((double) segment / segments)), PhantomEnergyOptions.SPARK, 0.55F, 10, 2, 0.06D, 0.0D);
        }
    }

    public static void echoRings(ServerLevel level, Vec3 center, float radius, boolean layered) {
        emit(level, PhantomVfxTheme.ECHO, center, PhantomEnergyOptions.SONIC_RING, radius, 18, 1, 0, 0);
        if (layered) { emit(level, PhantomVfxTheme.ECHO, center.add(0, 0.15D, 0), PhantomEnergyOptions.SONIC_RING, radius * 0.65F, 24, 1, 0, 0); }
        emit(level, PhantomVfxTheme.ECHO, center, PhantomEnergyOptions.GLOW, Math.min(0.6F, radius * 0.3F), 12, 1, 0, 0);
    }

    public static void bloodWings(ServerLevel level, Vec3 center, Vec3 forward) {
        Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize();
        for (int wing = -1; wing <= 1; wing += 2) {
            for (int arc = 1; arc <= 4; arc++) {
                Vec3 point = center.add(forward.scale(arc * 0.9D)).add(side.scale(wing * arc * 0.7D));
                emit(level, PhantomVfxTheme.BLOOD, point, PhantomEnergyOptions.SLASH, 1.4F, 15, 1, 0, 0);
            }
        }
        shockwave(level, PhantomVfxTheme.BLOOD, center, 3);
    }

    public static void bloodDominion(ServerLevel level, Vec3 center) {
        shockwave(level, PhantomVfxTheme.BLOOD, center.add(0, 0.1D, 0), 10);
        for (int pillar = 0; pillar < 8; pillar++) {
            double angle = pillar * Math.PI / 4 + level.getGameTime() * 0.025D;
            Vec3 point = center.add(Math.cos(angle) * 8, 0.8D, Math.sin(angle) * 8);
            emit(level, PhantomVfxTheme.BLOOD, point, PhantomEnergyOptions.GLOW, 0.6F, 24, 1, 0, 0);
            emit(level, PhantomVfxTheme.BLOOD, point.add(0, 1, 0), PhantomEnergyOptions.SPARK, 0.2F, 24, 2, 0.1D, 0.04D);
        }
    }

    public static void vampireRings(ServerLevel level, Vec3 center, float radius) {
        emit(level, PhantomVfxTheme.VAMPIRE, center, PhantomEnergyOptions.SONIC_RING, radius, 24, 1, 0, 0);
        emit(level, PhantomVfxTheme.VAMPIRE, center, PhantomEnergyOptions.SONIC_RING, radius * 0.55F, 18, 1, 0, 0);
        emit(level, PhantomVfxTheme.VAMPIRE, center, PhantomEnergyOptions.GLOW, 0.2F, 14, 1, 0, 0);
    }

    public static void vampireMist(ServerLevel level, Vec3 center) {
        emit(level, PhantomVfxTheme.VAMPIRE, center, PhantomEnergyOptions.AURA, 1.1F, 30, 5, 0.6D, 0.006D);
        emit(level, PhantomVfxTheme.VAMPIRE, center, PhantomEnergyOptions.SPARK, 0.12F, 22, 3, 0.75D, 0.008D);
    }

    public static void vampireMoon(ServerLevel level, Vec3 center) {
        vampireRings(level, center.add(0, 2.6D, 0), 2.4F);
        emit(level, PhantomVfxTheme.VAMPIRE, center.add(0, 2.6D, 0), PhantomEnergyOptions.GLOW, 1.4F, 40, 1, 0, 0);
        shockwave(level, PhantomVfxTheme.VAMPIRE, center.add(0, -0.4D, 0), 4);
        for (int arc = 0; arc < 8; arc++) {
            double angle = arc * Math.PI / 4;
            emit(level, PhantomVfxTheme.VAMPIRE, center.add(Math.cos(angle) * 2, 0.4D, Math.sin(angle) * 2), PhantomEnergyOptions.SLASH, 0.8F, 20, 1, 0, 0);
        }
    }

    public static void echoCharge(ServerLevel level, Vec3 center, int charge) {
        emit(level, PhantomVfxTheme.ECHO, center, PhantomEnergyOptions.SPARK, 0.12F, 20, Math.clamp(charge, 1, 5), 0.45D, 0.005D);
    }

    public static void huskDust(ServerLevel level, Vec3 center, float radius, boolean fragments) {
        shockwave(level, PhantomVfxTheme.HUSK, center.add(0, 0.08D, 0), radius);
        emit(level, PhantomVfxTheme.HUSK, center.add(0, 0.3D, 0), PhantomEnergyOptions.SPARK, 0.2F, 24, fragments ? 8 : 3, radius * 0.25D, 0.025D);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()), center.x, center.y + 0.2D, center.z,
                fragments ? 8 : 3, radius * 0.3D, 0.15D, radius * 0.3D, 0.025D);
        if (fragments) {
            for (int fragment = 0; fragment < 6; fragment++) {
                double angle = fragment * Math.PI / 3 + level.getGameTime() * 0.08D;
                Vec3 point = center.add(Math.cos(angle) * radius * 0.65D, 0.4D + fragment % 2 * 0.4D, Math.sin(angle) * radius * 0.65D);
                level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, net.minecraft.world.item.Items.BONE), point.x, point.y, point.z, 1, 0.03D, 0.05D, 0.03D, 0.03D);
                emit(level, PhantomVfxTheme.HUSK, point, PhantomEnergyOptions.GLOW, 0.18F, 18, 1, 0, 0);
            }
        }
    }

    public static void orbit(ServerLevel level, PhantomVfxTheme theme, Vec3 center, float radius, int particles) {
        emit(level, theme, center, PhantomEnergyOptions.ORBIT, radius, 28, Math.clamp(particles, 1, 16), 0, 0);
    }

    public static void implosion(ServerLevel level, PhantomVfxTheme theme, Vec3 center, float radius) {
        emit(level, theme, center, PhantomEnergyOptions.IMPLOSION, radius, 24, 12, 0, 0);
        emit(level, theme, center, PhantomEnergyOptions.DISTORTION, radius * 0.6F, 18, 1, 0, 0);
        emit(level, theme, center, PhantomEnergyOptions.GLOW, 0.3F, 12, 1, 0, 0);
    }

    public static void distortion(ServerLevel level, PhantomVfxTheme theme, Vec3 center, float radius) {
        emit(level, theme, center, PhantomEnergyOptions.DISTORTION, radius, 20, 1, 0, 0);
        orbit(level, theme, center, radius, 4);
    }

    public static void gravityVortex(ServerLevel level, Vec3 center, float radius, boolean ultimate) {
        distortion(level, PhantomVfxTheme.GRAVITY, center.add(0, ultimate ? 1.6D : 0.6D, 0), radius);
        orbit(level, PhantomVfxTheme.GRAVITY, center.add(0, 0.5D, 0), radius * 1.5F, ultimate ? 12 : 6);
        implosion(level, PhantomVfxTheme.GRAVITY, center.add(0, 0.7D, 0), radius * 1.8F);
        if (ultimate && level.getGameTime() % 24 < 4) { shockwave(level, PhantomVfxTheme.GRAVITY, center, 8); }
    }

    private static void emit(ServerLevel level, PhantomVfxTheme theme, Vec3 center, int shape, float scale,
            int lifetime, int count, double spread, double speed) {
        if (!Double.isFinite(center.lengthSqr()) || !Double.isFinite(spread) || !Double.isFinite(speed) || count <= 0) { return; }
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

    public static void witherStorm(ServerLevel level, Vec3 center, boolean charging) {
        Vec3 halo = center.add(0, charging ? 3.5D : 4.5D, 0);
        distortion(level, PhantomVfxTheme.WITHER, halo, charging ? 3 : 5);
        orbit(level, PhantomVfxTheme.WITHER, halo, charging ? 4 : 6, 10);
        if (charging) { implosion(level, PhantomVfxTheme.WITHER, halo, 3); }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, halo.x, halo.y, halo.z, 6, 3, 0.8D, 3, 0.01D);
        if (level.getGameTime() % 20 == 0) { shockwave(level, PhantomVfxTheme.WITHER, center.add(0, 0.2D, 0), 8); }
    }

    private static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int particles;
    }
}
