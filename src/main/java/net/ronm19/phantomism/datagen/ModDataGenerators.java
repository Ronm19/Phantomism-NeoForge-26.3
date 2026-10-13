package net.ronm19.phantomism.datagen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ronm19.phantomism.Phantomism;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class ModDataGenerators {
    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherRegistryEntries(GatherDataRegistryEntriesEvent event) {
        event.add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, new ModBiomeModifierProvider())
                .recipe(ModRecipeProvider::new)
                .add(Registries.LOOT_TABLE, new ModLootTableProvider());
    }

    @SubscribeEvent
    public static void gatherClientData(GatherDataEvent.Client event) {
        addSharedProviders(event);
        event.createProvider(ModModelProvider::new);
        event.addProvider(new ModPhantomGeometryProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModPhantomAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModGoldAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModCherryAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModParticleProvider(event.getGenerator().getPackOutput()));
    }

    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        addSharedProviders(event);
    }

    private static void addSharedProviders(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        var lookup = event.getWorldLookupProvider();
        event.addProvider(new ModBiomeTagsProvider(output, lookup));
        event.addProvider(new ModEntityTypeTagsProvider(output, lookup));
        event.addProvider(new ModBlockTagsProvider(output, lookup));
        event.addProvider(new ModItemTagsProvider(output, lookup));
        event.addProvider(new ModMixinConfigProvider(output));
    }
}
