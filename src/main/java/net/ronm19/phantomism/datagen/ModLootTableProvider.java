package net.ronm19.phantomism.datagen;

import java.util.List;
import java.util.Set;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class ModLootTableProvider extends LootTableProvider {
    public ModLootTableProvider() {
        super(Set.of(), List.of(
                new SubProviderEntry(ModBlockLootTableProvider::new, LootContextParamSets.BLOCK),
                new SubProviderEntry(ModCodexLootProvider::new, LootContextParamSets.CHEST),
                new SubProviderEntry(ModEntityLootTableProvider::new, LootContextParamSets.ENTITY)));
    }
}
