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
                .add(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS, new ModStructureModifierProvider())
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
        event.addProvider(new ModDragonAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModVioletAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModToxicAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModSculkAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModRavagerAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModEndAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModZombieAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModSkeletonAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModHuskAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModEchoAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModBloodAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModVampireAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModGravityAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModWitherAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModChronoAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModOmenAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModAncientAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModReaperAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModPrimordialAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModMirageAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModGolemAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModKingAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModDarkAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModVoidAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModAngelAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModSpectralAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModDemonAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModSentinelAnimationProvider(event.getGenerator().getPackOutput()));
        event.addProvider(new ModRiftAnimationProvider(event.getGenerator().getPackOutput()));
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
