package net.ronm19.phantomism.datagen;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.ronm19.phantomism.codex.CodexCatalog;

public final class ModCodexLootProvider implements LootTableSubProvider {
    public record Chest(String name, float chance) {
    }

    public static final List<Chest> CHESTS = List.of(new Chest("simple_dungeon", .12F), new Chest("abandoned_mineshaft", .08F),
            new Chest("woodland_mansion", .15F), new Chest("ancient_city", .10F));
    private final Context context;

    public ModCodexLootProvider(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        for (Chest chest : CHESTS) {
            context.accept(ResourceKey.create(Registries.LOOT_TABLE, CodexCatalog.id("chests/codex_" + chest.name())),
                    LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                            .when(LootItemRandomChanceCondition.randomChance(chest.chance()))
                            .add(LootItem.lootTableItem(Items.PHANTOM_MEMBRANE)
                                    .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2))))));
        }
    }
}
