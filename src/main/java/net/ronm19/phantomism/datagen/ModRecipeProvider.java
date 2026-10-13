package net.ronm19.phantomism.datagen;

import net.minecraft.advancements.Advancement;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        super(recipes, advancements);
    }

    @Override
    protected void buildRecipes() {
        new ModAdvancementProvider(this.advancementOutput).generate();
        new ModBrewingRecipeProvider(this.output).generate();
        this.shaped(RecipeCategory.TOOLS, PhantomismContent.PHANTOM_STAFF.get())
                .pattern(" M ")
                .pattern(" S ")
                .pattern(" S ")
                .define('M', Items.PHANTOM_MEMBRANE)
                .define('S', Items.STICK)
                .unlockedBy("has_phantom_membrane", this.has(Items.PHANTOM_MEMBRANE))
                .save(this.output);
    }
}
