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
    }
}
