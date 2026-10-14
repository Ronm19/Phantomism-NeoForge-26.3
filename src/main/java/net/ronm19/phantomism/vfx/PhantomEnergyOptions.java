package net.ronm19.phantomism.vfx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.ronm19.phantomism.registry.ModParticles;

public record PhantomEnergyOptions(int color, int fadeColor, int shape, float scale, int lifetime) implements ParticleOptions {
    public static final int GLOW = 0;
    public static final int SLASH = 1;
    public static final int SPARK = 2;
    public static final int SHOCKWAVE = 3;
    public static final int AURA = 4;
    public static final int PETAL = 5;
    public static final int SONIC_RING = 6;
    public static final MapCodec<PhantomEnergyOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(PhantomEnergyOptions::color),
            Codec.INT.fieldOf("fade_color").forGetter(PhantomEnergyOptions::fadeColor),
            Codec.INT.fieldOf("shape").forGetter(PhantomEnergyOptions::shape),
            Codec.FLOAT.fieldOf("scale").forGetter(PhantomEnergyOptions::scale),
            Codec.INT.fieldOf("lifetime").forGetter(PhantomEnergyOptions::lifetime)
    ).apply(instance, PhantomEnergyOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomEnergyOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PhantomEnergyOptions::color,
            ByteBufCodecs.INT, PhantomEnergyOptions::fadeColor,
            ByteBufCodecs.VAR_INT, PhantomEnergyOptions::shape,
            ByteBufCodecs.FLOAT, PhantomEnergyOptions::scale,
            ByteBufCodecs.VAR_INT, PhantomEnergyOptions::lifetime,
            PhantomEnergyOptions::new);

    public PhantomEnergyOptions {
        color &= 0xFFFFFF;
        fadeColor &= 0xFFFFFF;
        shape = Math.clamp(shape, GLOW, SONIC_RING);
        scale = Float.isFinite(scale) ? Math.clamp(scale, 0.05F, 8.0F) : 1.0F;
        lifetime = Math.clamp(lifetime, 4, 60);
    }

    public static PhantomEnergyOptions of(PhantomVfxTheme theme, int shape, float scale, int lifetime) {
        return new PhantomEnergyOptions(theme.color(), theme.fadeColor(), shape, scale, lifetime);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.PHANTOM_ENERGY.get();
    }
}
