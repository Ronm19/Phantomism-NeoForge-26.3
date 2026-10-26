package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;
import net.ronm19.phantomism.codex.CodexCatalog;

public final class ModCodexLootModifiersProvider implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> lookup;

    public ModCodexLootModifiersProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        this.output = output;
        this.lookup = lookup;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return lookup.thenCompose(registries -> {
            var futures = new ArrayList<CompletableFuture<?>>();
            JsonObject global = new JsonObject();
            global.addProperty("replace", false);
            JsonArray entries = new JsonArray();
            for (var chest : ModCodexLootProvider.CHESTS) {
                String name = "codex_" + chest.name();
                entries.add(CodexCatalog.id(name).toString());
                var modifier = new AddTableLootModifier(Optional.of(Holder.direct(LootTableIdCondition.builder(Identifier.withDefaultNamespace("chests/" + chest.name())).build())), 0,
                        ResourceKey.create(Registries.LOOT_TABLE, CodexCatalog.id("chests/" + name)));
                JsonObject json = AddTableLootModifier.CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), modifier).getOrThrow().getAsJsonObject();
                json.addProperty("type", "neoforge:add_table");
                futures.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve("data/phantomism/loot_modifiers/" + name + ".json")));
            }
            global.add("entries", entries);
            futures.add(DataProvider.saveStable(cache, global, output.getOutputFolder().resolve("data/neoforge/loot_modifiers/global_loot_modifiers.json")));
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public String getName() {
        return "Phantom Codex Additive Membrane Loot";
    }
}
