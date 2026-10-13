package net.ronm19.phantomism.datagen;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ModBiomeModifierProvider implements SingleRegistryBootstrap<BiomeModifier> {
    @Override
    public void run(BootstrapContext<BiomeModifier> context) {
        addSpawns(context, "black_phantom_spawns", ModTags.Biomes.BLACK_PHANTOM_SPAWNS,
                PhantomismContent.BLACK_PHANTOM.get(), PhantomDiscovery.BLACK.rarity().spawnWeight());
        addSpawns(context, "fire_phantom_spawns", ModTags.Biomes.FIRE_PHANTOM_SPAWNS,
                PhantomismContent.FIRE_PHANTOM.get(), PhantomDiscovery.FIRE.rarity().spawnWeight());
        addSpawns(context, "frost_phantom_spawns", ModTags.Biomes.FROST_PHANTOM_SPAWNS,
                PhantomismContent.FROST_PHANTOM.get(), PhantomDiscovery.FROST.rarity().spawnWeight());
        addSpawns(context, "earth_phantom_spawns", ModTags.Biomes.EARTH_PHANTOM_SPAWNS,
                PhantomismContent.EARTH_PHANTOM.get(), PhantomDiscovery.EARTH.rarity().spawnWeight());
        addSpawns(context, "water_phantom_spawns", ModTags.Biomes.WATER_PHANTOM_SPAWNS,
                PhantomismContent.WATER_PHANTOM.get(), PhantomDiscovery.WATER.rarity().spawnWeight());
        addSpawns(context, "storm_phantom_spawns", ModTags.Biomes.STORM_PHANTOM_SPAWNS,
                PhantomismContent.STORM_PHANTOM.get(), PhantomDiscovery.STORM.rarity().spawnWeight());
        addSpawns(context, "sand_phantom_spawns", ModTags.Biomes.SAND_PHANTOM_SPAWNS,
                PhantomismContent.SAND_PHANTOM.get(), PhantomDiscovery.SAND.rarity().spawnWeight());
        addSpawns(context, "gold_phantom_spawns", ModTags.Biomes.GOLD_PHANTOM_SPAWNS,
                PhantomismContent.GOLD_PHANTOM.get(), PhantomDiscovery.GOLD.rarity().spawnWeight());
        addSpawns(context, "cherry_phantom_spawns", ModTags.Biomes.CHERRY_PHANTOM_SPAWNS,
                PhantomismContent.CHERRY_PHANTOM.get(), PhantomDiscovery.CHERRY.rarity().spawnWeight());
    }

    private static void addSpawns(BootstrapContext<BiomeModifier> context, String name, TagKey<Biome> biomes,
            EntityType<?> type, int weight) {
        ResourceKey<BiomeModifier> key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name));
        context.register(key, BiomeModifiers.AddSpawnsBiomeModifier.singleSpawn(
                context.lookup(Registries.BIOME).getOrThrow(biomes),
                new Weighted<>(new MobSpawnSettings.SpawnerData(type, UniformInt.of(1,
                        type == PhantomismContent.GOLD_PHANTOM.get() ? 1 : 2)), weight)));
    }
}
