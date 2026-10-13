package net.ronm19.phantomism.datagen;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.BrewingRecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class ModBrewingRecipeProvider {
    private final RecipeOutput output;

    public ModBrewingRecipeProvider(RecipeOutput output) {
        this.output = output;
    }

    public void generate() {
        for (Item container : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            for (Item ice : List.of(Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE)) {
                this.save(BrewingRecipeBuilder.brewingMix(container, Potions.WATER, ice, PhantomismContent.FROSTBOUND_POTION));
            }
        }
        this.save(BrewingRecipeBuilder.brewingContainerTransform(
                Items.POTION, PhantomismContent.FROSTBOUND_POTION, Items.GUNPOWDER, Items.SPLASH_POTION));
        this.save(BrewingRecipeBuilder.brewingContainerTransform(
                Items.SPLASH_POTION, PhantomismContent.FROSTBOUND_POTION, Items.DRAGON_BREATH, Items.LINGERING_POTION));
    }

    private void save(BrewingRecipeBuilder builder) {
        Identifier vanillaId = builder.defaultId().identifier();
        builder.save(this.output, ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, vanillaId.getPath())));
    }
}
