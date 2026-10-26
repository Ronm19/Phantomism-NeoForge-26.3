package net.ronm19.phantomism.entity.spawn;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.util.ModTags;

public final class AutumnHabitat {
    private static final Map<Object, Boolean> DAPPLED = new WeakHashMap<>();
    private AutumnHabitat() {}
    public static boolean habitat(ServerLevel level, BlockPos position) {
        if (level.dimension() != Level.OVERWORLD || !level.hasChunkAt(position)) { return false; }
        var biome = level.getBiome(position);
        if (biome.is(ModTags.Biomes.AUTUMN_PRIMARY_SPAWNS) || biome.unwrapKey().map(key -> key.identifier().getPath().equals("dappled_forest")).orElse(false)) { return true; }
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        boolean present = DAPPLED.computeIfAbsent(registry, ignored -> registry.listElements().anyMatch(entry -> entry.key().identifier().getPath().equals("dappled_forest") || entry.is(ModTags.Biomes.AUTUMN_PRIMARY_SPAWNS)));
        return !present && (!Config.autumnFallbackLoaded() || Config.AUTUMN_FOREST_FALLBACK.get()) && biome.is(ModTags.Biomes.AUTUMN_PHANTOM_SPAWNS);
    }
}
