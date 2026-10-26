package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.world.level.biome.Biomes;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.util.ModTags;

public final class ModBiomeTagsProvider extends BiomeTagsProvider {
    public ModBiomeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ModTags.Biomes.BLACK_PHANTOM_SPAWNS)
                .add(Biomes.PLAINS).add(Biomes.SUNFLOWER_PLAINS)
                .add(Biomes.FOREST).add(Biomes.BIRCH_FOREST)
                .add(Biomes.OLD_GROWTH_BIRCH_FOREST).add(Biomes.DARK_FOREST);

        this.tag(ModTags.Biomes.FIRE_PHANTOM_SPAWNS)
                .add(Biomes.SAVANNA).add(Biomes.SAVANNA_PLATEAU).add(Biomes.WINDSWEPT_SAVANNA);

        this.tag(ModTags.Biomes.FROST_PHANTOM_SPAWNS)
                .add(Biomes.SNOWY_PLAINS).add(Biomes.SNOWY_TAIGA).add(Biomes.ICE_SPIKES);
        this.tag(ModTags.Biomes.EARTH_PHANTOM_SPAWNS)
                .add(Biomes.FOREST).add(Biomes.FLOWER_FOREST).add(Biomes.BIRCH_FOREST)
                .add(Biomes.OLD_GROWTH_BIRCH_FOREST).add(Biomes.MEADOW);
        this.tag(ModTags.Biomes.WATER_PHANTOM_SPAWNS)
                .add(Biomes.OCEAN).add(Biomes.DEEP_OCEAN).add(Biomes.COLD_OCEAN).add(Biomes.DEEP_COLD_OCEAN)
                .add(Biomes.LUKEWARM_OCEAN).add(Biomes.DEEP_LUKEWARM_OCEAN).add(Biomes.WARM_OCEAN)
                .add(Biomes.FROZEN_OCEAN).add(Biomes.DEEP_FROZEN_OCEAN).add(Biomes.RIVER).add(Biomes.FROZEN_RIVER);
        this.tag(ModTags.Biomes.STORM_PHANTOM_SPAWNS)
                .add(Biomes.WINDSWEPT_HILLS).add(Biomes.WINDSWEPT_FOREST).add(Biomes.WINDSWEPT_GRAVELLY_HILLS);
        this.tag(ModTags.Biomes.SAND_PHANTOM_SPAWNS).add(Biomes.DESERT);
        this.tag(ModTags.Biomes.GOLD_PHANTOM_SPAWNS)
                .add(Biomes.BADLANDS).add(Biomes.WOODED_BADLANDS).add(Biomes.STONY_PEAKS);
        this.tag(ModTags.Biomes.CHERRY_PHANTOM_SPAWNS).add(Biomes.CHERRY_GROVE);
        this.tag(ModTags.Biomes.PHANTOM_DRAGON_SPAWNS)
                .add(Biomes.JAGGED_PEAKS).add(Biomes.STONY_PEAKS).add(Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.VIOLET_PHANTOM_SPAWNS).add(Biomes.MEADOW).add(Biomes.FLOWER_FOREST);
        this.tag(ModTags.Biomes.TOXIC_PHANTOM_SPAWNS).add(Biomes.SWAMP).add(Biomes.MANGROVE_SWAMP);
        this.tag(ModTags.Biomes.END_PHANTOM_SPAWNS).add(Biomes.END_HIGHLANDS).add(Biomes.END_MIDLANDS).add(Biomes.SMALL_END_ISLANDS);
        this.tag(ModTags.Biomes.ECHO_PHANTOM_SPAWNS).add(Biomes.MEADOW).add(Biomes.FOREST).add(Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.BLOOD_PHANTOM_SPAWNS).add(Biomes.BADLANDS).add(Biomes.ERODED_BADLANDS).add(Biomes.WOODED_BADLANDS).add(Biomes.DARK_FOREST);
        this.tag(ModTags.Biomes.VAMPIRE_PHANTOM_SPAWNS).add(Biomes.DARK_FOREST).addTag(net.minecraft.tags.BiomeTags.IS_MOUNTAIN);
        this.tag(ModTags.Biomes.GRAVITY_PHANTOM_SPAWNS).add(Biomes.JAGGED_PEAKS).add(Biomes.STONY_PEAKS).add(Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.INFERNAL_PHANTOM_SPAWNS).add(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS);
        this.tag(ModTags.Biomes.AUTUMN_PRIMARY_SPAWNS).add(Biomes.DAPPLED_FOREST);
        this.tag(ModTags.Biomes.AUTUMN_PHANTOM_SPAWNS).add(Biomes.DAPPLED_FOREST, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
        this.tag(ModTags.Biomes.LUNAR_PHANTOM_SPAWNS).add(Biomes.MEADOW, Biomes.TAIGA, Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.SOLAR_PHANTOM_SPAWNS).add(Biomes.MEADOW, Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS);
        this.tag(ModTags.Biomes.SPORE_PHANTOM_SPAWNS).add(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.MUSHROOM_FIELDS);
        this.tag(ModTags.Biomes.GALE_PHANTOM_SPAWNS).add(Biomes.WINDSWEPT_HILLS, Biomes.MEADOW, Biomes.JAGGED_PEAKS);
        this.tag(ModTags.Biomes.OBSIDIAN_PHANTOM_SPAWNS).add(Biomes.BASALT_DELTAS, Biomes.NETHER_WASTES);
        this.tag(ModTags.Biomes.CRYSTAL_PHANTOM_SPAWNS).add(Biomes.STONY_PEAKS).add(Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.PLAGUE_PHANTOM_SPAWNS).add(Biomes.SWAMP).add(Biomes.DARK_FOREST);
        this.tag(ModTags.Biomes.SHADOW_PHANTOM_SPAWNS).add(Biomes.DARK_FOREST).add(Biomes.TAIGA);
        this.tag(ModTags.Biomes.ECLIPSE_PHANTOM_SPAWNS).add(Biomes.MEADOW).add(Biomes.WINDSWEPT_HILLS);
        this.tag(ModTags.Biomes.MIRAGE_PHANTOM_SPAWNS).add(Biomes.FLOWER_FOREST).add(Biomes.MEADOW).add(Biomes.DARK_FOREST);
        this.tag(ModTags.Biomes.OMEN_PHANTOM_SPAWNS).add(Biomes.DARK_FOREST).add(Biomes.SAVANNA).add(Biomes.SAVANNA_PLATEAU);
        this.tag(ModTags.Biomes.ANCIENT_PHANTOM_SPAWNS).addTag(net.minecraft.tags.BiomeTags.IS_MOUNTAIN).add(Biomes.WINDSWEPT_HILLS).add(Biomes.WINDSWEPT_FOREST).add(Biomes.WINDSWEPT_GRAVELLY_HILLS).add(Biomes.DRIPSTONE_CAVES).add(Biomes.LUSH_CAVES);
        this.tag(ModTags.Biomes.REAPER_PHANTOM_SPAWNS).add(Biomes.SOUL_SAND_VALLEY).add(Biomes.DARK_FOREST);
        this.tag(ModTags.Biomes.PRIMORDIAL_PHANTOM_SPAWNS).add(Biomes.END_HIGHLANDS).add(Biomes.DRIPSTONE_CAVES).addTag(net.minecraft.tags.BiomeTags.IS_MOUNTAIN);
        this.tag(ModTags.Biomes.PHANTOM_KING_SPAWNS).add(Biomes.WINDSWEPT_HILLS).add(Biomes.OLD_GROWTH_PINE_TAIGA).add(Biomes.OLD_GROWTH_SPRUCE_TAIGA).add(Biomes.MEADOW);
        this.tag(ModTags.Biomes.DARK_PHANTOM_SPAWNS).add(Biomes.DARK_FOREST);
        this.tag(ModTags.Biomes.VOID_PHANTOM_SPAWNS).add(Biomes.END_BARRENS).add(Biomes.END_HIGHLANDS);
        this.tag(ModTags.Biomes.ANGEL_PHANTOM_SPAWNS).add(Biomes.MEADOW).add(Biomes.CHERRY_GROVE);
        this.tag(ModTags.Biomes.SPECTRAL_PHANTOM_SPAWNS).add(Biomes.DARK_FOREST).add(Biomes.SOUL_SAND_VALLEY);
        this.tag(ModTags.Biomes.DEMON_PHANTOM_SPAWNS).add(Biomes.NETHER_WASTES).add(Biomes.BASALT_DELTAS).add(Biomes.SOUL_SAND_VALLEY);
        this.tag(ModTags.Biomes.SENTINEL_PHANTOM_SPAWNS).add(Biomes.PLAINS).add(Biomes.MEADOW);
        this.tag(ModTags.Biomes.RIFT_PHANTOM_SPAWNS).add(Biomes.WARPED_FOREST).add(Biomes.NETHER_WASTES);
        this.tag(ModTags.Biomes.CHRONO_PHANTOM_SPAWNS).add(Biomes.MEADOW).add(Biomes.SPARSE_JUNGLE);
        this.tag(ModTags.Biomes.WITHER_PHANTOM_SPAWNS).add(Biomes.SOUL_SAND_VALLEY);
        this.tag(ModTags.Biomes.HUSK_PHANTOM_SPAWNS).add(Biomes.DESERT);
        this.tag(ModTags.Biomes.ZOMBIE_PHANTOM_SPAWNS).add(Biomes.PLAINS).add(Biomes.FOREST).add(Biomes.TAIGA);
        this.tag(ModTags.Biomes.SKELETON_PHANTOM_SPAWNS).add(Biomes.WINDSWEPT_HILLS).add(Biomes.WINDSWEPT_GRAVELLY_HILLS)
                .add(Biomes.STONY_PEAKS).add(Biomes.JAGGED_PEAKS).add(Biomes.FROZEN_PEAKS)
                .add(Biomes.BADLANDS).add(Biomes.ERODED_BADLANDS).add(Biomes.DESERT);
    }
}
