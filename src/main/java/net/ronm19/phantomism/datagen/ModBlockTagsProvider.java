package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.ronm19.phantomism.Phantomism;

public final class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(net.ronm19.phantomism.util.ModTags.Blocks.RAVAGER_BULLDOZER_BREAKABLE)
                .addTag(net.minecraft.tags.BlockTags.LEAVES)
                .add(net.minecraft.world.level.block.Blocks.SHORT_GRASS.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.TALL_GRASS.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.FERN.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.LARGE_FERN.builtInRegistryHolder().key());
        this.tag(net.ronm19.phantomism.util.ModTags.Blocks.RAVAGER_SIEGE_BREAKABLE)
                .add(net.minecraft.world.level.block.Blocks.DIRT.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.COARSE_DIRT.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.SAND.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.RED_SAND.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.SANDSTONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.RED_SANDSTONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.STONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.COBBLESTONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.builtInRegistryHolder().key());
        this.tag(net.ronm19.phantomism.util.ModTags.Blocks.RAVAGER_PROTECTED_BLOCKS)
                .add(net.minecraft.world.level.block.Blocks.OBSIDIAN.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.BEDROCK.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.NETHER_PORTAL.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.END_PORTAL.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME.builtInRegistryHolder().key());
        this.tag(net.ronm19.phantomism.util.ModTags.Blocks.GOLD_CAVE_CEILINGS)
                .addTag(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                .addTag(net.minecraft.tags.BlockTags.TERRACOTTA)
                .add(net.minecraft.world.level.block.Blocks.SANDSTONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.RED_SANDSTONE.builtInRegistryHolder().key());
    }
}
