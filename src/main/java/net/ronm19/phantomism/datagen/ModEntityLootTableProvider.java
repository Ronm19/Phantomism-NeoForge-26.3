package net.ronm19.phantomism.datagen;

import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootTable;
import net.ronm19.phantomism.Phantomism;

public final class ModEntityLootTableProvider extends EntityLootSubProvider {
    public ModEntityLootTableProvider(LootTableSubProvider.Context output) {
        super(FeatureFlags.REGISTRY.allFlags(), output);
    }

    @Override
    public void generate() {
        this.getKnownEntityTypes().forEach(type -> this.add(type, LootTable.lootTable()));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(Phantomism.MOD_ID))
                .filter(type -> type.getDefaultLootTable().isPresent());
    }
}
