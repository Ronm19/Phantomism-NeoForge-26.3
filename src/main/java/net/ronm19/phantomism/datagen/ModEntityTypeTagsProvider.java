package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityTypeIds;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ModEntityTypeTagsProvider extends EntityTypeTagsProvider {
    public ModEntityTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ModTags.EntityTypes.PHANTOMS)
                .add(PhantomismContent.BLACK_PHANTOM.key())
                .add(PhantomismContent.FIRE_PHANTOM.key())
                .add(PhantomismContent.FROST_PHANTOM.key())
                .add(PhantomismContent.EARTH_PHANTOM.key())
                .add(PhantomismContent.WATER_PHANTOM.key())
                .add(PhantomismContent.STORM_PHANTOM.key())
                .add(PhantomismContent.SAND_PHANTOM.key())
                .add(PhantomismContent.GOLD_PHANTOM.key())
                .add(PhantomismContent.CHERRY_PHANTOM.key());
        this.tag(ModTags.EntityTypes.CHERRY_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.GOLD_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.EARTH_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.SAND_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.FROST_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES).add(PhantomismContent.FROST_PHANTOM.key());
    }
}
