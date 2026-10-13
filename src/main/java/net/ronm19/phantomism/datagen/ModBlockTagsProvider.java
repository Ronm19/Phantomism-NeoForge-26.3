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
        this.tag(net.ronm19.phantomism.util.ModTags.Blocks.GOLD_CAVE_CEILINGS)
                .addTag(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                .addTag(net.minecraft.tags.BlockTags.TERRACOTTA)
                .add(net.minecraft.world.level.block.Blocks.SANDSTONE.builtInRegistryHolder().key())
                .add(net.minecraft.world.level.block.Blocks.RED_SANDSTONE.builtInRegistryHolder().key());
    }
}
