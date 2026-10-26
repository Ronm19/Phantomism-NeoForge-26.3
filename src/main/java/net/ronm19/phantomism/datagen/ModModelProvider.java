package net.ronm19.phantomism.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, Phantomism.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(PhantomismContent.PHANTOM_CODEX.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.PHANTOM_STAFF.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(PhantomismContent.BLACK_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.FIRE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.FROST_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.EARTH_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.WATER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.STORM_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SAND_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.GOLD_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.CHERRY_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.PHANTOM_DRAGON_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.VIOLET_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.TOXIC_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SCULK_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.RAVAGER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.END_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ZOMBIE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SKELETON_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.HUSK_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ECHO_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.BLOOD_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.VAMPIRE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.GRAVITY_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.WITHER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.CHRONO_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.RIFT_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SENTINEL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.DEMON_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SPECTRAL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ANGEL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.VOID_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.DARK_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.PHANTOM_KING_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.AUTUMN_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.HALLOWEEN_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.EASTER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.CHRISTMAS_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.LUNAR_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.INFERNAL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ILLAGER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SOLAR_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SPORE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.GALE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.OBSIDIAN_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.CRYSTAL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.PLAGUE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.SHADOW_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ECLIPSE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.MIRAGE_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.GOLEM_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.PRIMORDIAL_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.HEART_OF_THE_WORLD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.REAPER_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ANCIENT_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.ANCIENT_RELIC.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(PhantomismContent.OMEN_PHANTOM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
    }
}
