package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ModTags.Items.PHANTOM_STAFFS).add(PhantomismContent.PHANTOM_STAFF.key());
        this.tag(ModTags.Items.PHANTOM_SPAWN_EGGS)
                .add(PhantomismContent.BLACK_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.FIRE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.FROST_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.EARTH_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.WATER_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.STORM_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SAND_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.GOLD_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.CHERRY_PHANTOM_SPAWN_EGG.key());
    }
}
