package net.ronm19.phantomism.datagen;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.common.world.StructureModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class ModStructureModifierProvider implements SingleRegistryBootstrap<StructureModifier> {
    @Override
    public void run(BootstrapContext<StructureModifier> context) {
        var city = context.lookup(Registries.STRUCTURE).getOrThrow(BuiltinStructures.ANCIENT_CITY);
        context.register(ResourceKey.create(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS,
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sculk_phantom_city_spawns")),
                StructureModifiers.AddSpawnsStructureModifier.singleSpawn(HolderSet.direct(city),
                        new Weighted<>(new MobSpawnSettings.SpawnerData(PhantomismContent.SCULK_PHANTOM.get(), UniformInt.of(1, 2)),
                                PhantomDiscovery.SCULK.rarity().spawnWeight())));
    }
}
