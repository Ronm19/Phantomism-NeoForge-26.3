package net.ronm19.phantomism.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.vfx.PhantomEnergyOptions;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(
            Registries.PARTICLE_TYPE, Phantomism.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SANDSTORM_DUST = PARTICLE_TYPES.register(
            "sandstorm_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, ParticleType<PhantomEnergyOptions>> PHANTOM_ENERGY = PARTICLE_TYPES.register(
            "phantom_energy", () -> new ParticleType<PhantomEnergyOptions>(false) {
                @Override
                public com.mojang.serialization.MapCodec<PhantomEnergyOptions> codec() {
                    return PhantomEnergyOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PhantomEnergyOptions> streamCodec() {
                    return PhantomEnergyOptions.STREAM_CODEC;
                }
            });

    private ModParticles() {
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
