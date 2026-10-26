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
        addSpawns(context, "phantom_dragon_spawns", ModTags.Biomes.PHANTOM_DRAGON_SPAWNS,
                PhantomismContent.PHANTOM_DRAGON.get(), PhantomDiscovery.DRAGON.rarity().spawnWeight());
        addSpawns(context, "violet_phantom_spawns", ModTags.Biomes.VIOLET_PHANTOM_SPAWNS,
                PhantomismContent.VIOLET_PHANTOM.get(), PhantomDiscovery.VIOLET.rarity().spawnWeight());
        addSpawns(context, "toxic_phantom_spawns", ModTags.Biomes.TOXIC_PHANTOM_SPAWNS,
                PhantomismContent.TOXIC_PHANTOM.get(), PhantomDiscovery.TOXIC.rarity().spawnWeight());
        addSpawns(context, "end_phantom_spawns", ModTags.Biomes.END_PHANTOM_SPAWNS,
                PhantomismContent.END_PHANTOM.get(), PhantomDiscovery.END.rarity().spawnWeight());
        addSpawns(context, "zombie_phantom_spawns", ModTags.Biomes.ZOMBIE_PHANTOM_SPAWNS,
                PhantomismContent.ZOMBIE_PHANTOM.get(), PhantomDiscovery.ZOMBIE.rarity().spawnWeight());
        addSpawns(context, "echo_phantom_spawns", ModTags.Biomes.ECHO_PHANTOM_SPAWNS,
                PhantomismContent.ECHO_PHANTOM.get(), PhantomDiscovery.ECHO.rarity().spawnWeight());
        addSpawns(context, "blood_phantom_spawns", ModTags.Biomes.BLOOD_PHANTOM_SPAWNS,
                PhantomismContent.BLOOD_PHANTOM.get(), PhantomDiscovery.BLOOD.rarity().spawnWeight());
        addSpawns(context, "vampire_phantom_spawns", ModTags.Biomes.VAMPIRE_PHANTOM_SPAWNS,
                PhantomismContent.VAMPIRE_PHANTOM.get(), PhantomDiscovery.VAMPIRE.rarity().spawnWeight());
        addSpawns(context, "gravity_phantom_spawns", ModTags.Biomes.GRAVITY_PHANTOM_SPAWNS,
                PhantomismContent.GRAVITY_PHANTOM.get(), 6);
        addSpawns(context, "obsidian_phantom_spawns", ModTags.Biomes.OBSIDIAN_PHANTOM_SPAWNS, PhantomismContent.OBSIDIAN_PHANTOM.get(), 6);
        addSpawns(context, "infernal_phantom_spawns", ModTags.Biomes.INFERNAL_PHANTOM_SPAWNS, PhantomismContent.INFERNAL_PHANTOM.get(), 3);
        addSpawns(context, "autumn_phantom_spawns", ModTags.Biomes.AUTUMN_PHANTOM_SPAWNS, PhantomismContent.AUTUMN_PHANTOM.get(), 3);
        addSpawns(context, "lunar_phantom_spawns", ModTags.Biomes.LUNAR_PHANTOM_SPAWNS, PhantomismContent.LUNAR_PHANTOM.get(), 3);
        addSpawns(context, "solar_phantom_spawns", ModTags.Biomes.SOLAR_PHANTOM_SPAWNS, PhantomismContent.SOLAR_PHANTOM.get(), 4);
        addSpawns(context, "spore_phantom_spawns", ModTags.Biomes.SPORE_PHANTOM_SPAWNS, PhantomismContent.SPORE_PHANTOM.get(), 12);
        addSpawns(context, "gale_phantom_spawns", ModTags.Biomes.GALE_PHANTOM_SPAWNS, PhantomismContent.GALE_PHANTOM.get(), 12);
        addSpawns(context, "crystal_phantom_spawns", ModTags.Biomes.CRYSTAL_PHANTOM_SPAWNS, PhantomismContent.CRYSTAL_PHANTOM.get(), 6);
        addSpawns(context, "plague_phantom_spawns", ModTags.Biomes.PLAGUE_PHANTOM_SPAWNS, PhantomismContent.PLAGUE_PHANTOM.get(), 6);
        addSpawns(context, "shadow_phantom_spawns", ModTags.Biomes.SHADOW_PHANTOM_SPAWNS, PhantomismContent.SHADOW_PHANTOM.get(), 6);
        addSpawns(context, "eclipse_phantom_spawns", ModTags.Biomes.ECLIPSE_PHANTOM_SPAWNS, PhantomismContent.ECLIPSE_PHANTOM.get(), 3);
        addSpawns(context, "mirage_phantom_spawns", ModTags.Biomes.MIRAGE_PHANTOM_SPAWNS, PhantomismContent.MIRAGE_PHANTOM.get(), 6);
        addSpawns(context, "omen_phantom_spawns", ModTags.Biomes.OMEN_PHANTOM_SPAWNS, PhantomismContent.OMEN_PHANTOM.get(), 6);
        addSpawns(context, "ancient_phantom_spawns", ModTags.Biomes.ANCIENT_PHANTOM_SPAWNS, PhantomismContent.ANCIENT_PHANTOM.get(), 3);
        addSpawns(context, "reaper_phantom_spawns", ModTags.Biomes.REAPER_PHANTOM_SPAWNS, PhantomismContent.REAPER_PHANTOM.get(), 3);
        addSpawns(context, "primordial_phantom_spawns", ModTags.Biomes.PRIMORDIAL_PHANTOM_SPAWNS, PhantomismContent.PRIMORDIAL_PHANTOM.get(), 3);
        addSpawns(context, "phantom_king_spawns", ModTags.Biomes.PHANTOM_KING_SPAWNS, PhantomismContent.PHANTOM_KING.get(), 3);
        addSpawns(context, "dark_phantom_spawns", ModTags.Biomes.DARK_PHANTOM_SPAWNS, PhantomismContent.DARK_PHANTOM.get(), 6);
        addSpawns(context, "void_phantom_spawns", ModTags.Biomes.VOID_PHANTOM_SPAWNS, PhantomismContent.VOID_PHANTOM.get(), 3);
        addSpawns(context, "angel_phantom_spawns", ModTags.Biomes.ANGEL_PHANTOM_SPAWNS, PhantomismContent.ANGEL_PHANTOM.get(), 3);
        addSpawns(context, "spectral_phantom_spawns", ModTags.Biomes.SPECTRAL_PHANTOM_SPAWNS, PhantomismContent.SPECTRAL_PHANTOM.get(), 6);
        addSpawns(context, "demon_phantom_spawns", ModTags.Biomes.DEMON_PHANTOM_SPAWNS, PhantomismContent.DEMON_PHANTOM.get(), 4);
        addSpawns(context, "sentinel_phantom_spawns", ModTags.Biomes.SENTINEL_PHANTOM_SPAWNS, PhantomismContent.SENTINEL_PHANTOM.get(), 6);
        addSpawns(context, "rift_phantom_spawns", ModTags.Biomes.RIFT_PHANTOM_SPAWNS, PhantomismContent.RIFT_PHANTOM.get(), 6);
        addSpawns(context, "chrono_phantom_spawns", ModTags.Biomes.CHRONO_PHANTOM_SPAWNS, PhantomismContent.CHRONO_PHANTOM.get(), 3);
        addSpawns(context, "wither_phantom_spawns", ModTags.Biomes.WITHER_PHANTOM_SPAWNS,
                PhantomismContent.WITHER_PHANTOM.get(), 6);
        addSpawns(context, "husk_phantom_spawns", ModTags.Biomes.HUSK_PHANTOM_SPAWNS,
                PhantomismContent.HUSK_PHANTOM.get(), PhantomDiscovery.HUSK.rarity().spawnWeight());
        addSpawns(context, "skeleton_phantom_spawns", ModTags.Biomes.SKELETON_PHANTOM_SPAWNS,
                PhantomismContent.SKELETON_PHANTOM.get(), PhantomDiscovery.SKELETON.rarity().spawnWeight());
    }

    private static void addSpawns(BootstrapContext<BiomeModifier> context, String name, TagKey<Biome> biomes,
            EntityType<?> type, int weight) {
        ResourceKey<BiomeModifier> key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name));
        context.register(key, BiomeModifiers.AddSpawnsBiomeModifier.singleSpawn(
                context.lookup(Registries.BIOME).getOrThrow(biomes),
                new Weighted<>(new MobSpawnSettings.SpawnerData(type, UniformInt.of(1,
                        type == PhantomismContent.INFERNAL_PHANTOM.get() || type == PhantomismContent.AUTUMN_PHANTOM.get() || type == PhantomismContent.LUNAR_PHANTOM.get() || type == PhantomismContent.SOLAR_PHANTOM.get() || type == PhantomismContent.OBSIDIAN_PHANTOM.get() || type == PhantomismContent.CRYSTAL_PHANTOM.get() || type == PhantomismContent.PLAGUE_PHANTOM.get() || type == PhantomismContent.ECLIPSE_PHANTOM.get() || type == PhantomismContent.PHANTOM_KING.get() || type == PhantomismContent.REAPER_PHANTOM.get() || type == PhantomismContent.PRIMORDIAL_PHANTOM.get() || type == PhantomismContent.OMEN_PHANTOM.get() || type == PhantomismContent.VOID_PHANTOM.get() || type == PhantomismContent.ANGEL_PHANTOM.get() || type == PhantomismContent.SPECTRAL_PHANTOM.get() || type == PhantomismContent.DEMON_PHANTOM.get() || type == PhantomismContent.SENTINEL_PHANTOM.get() || type == PhantomismContent.RIFT_PHANTOM.get() || type == PhantomismContent.CHRONO_PHANTOM.get() || type == PhantomismContent.GRAVITY_PHANTOM.get() || type == PhantomismContent.WITHER_PHANTOM.get() ? 1 : type == PhantomismContent.ZOMBIE_PHANTOM.get() ? 3
                                : type == PhantomismContent.GOLD_PHANTOM.get() || type == PhantomismContent.PHANTOM_DRAGON.get() ? 1 : 2)), weight)));
    }
}
