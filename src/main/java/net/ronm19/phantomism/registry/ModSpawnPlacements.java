package net.ronm19.phantomism.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.ronm19.phantomism.entity.BlackPhantom;
import net.ronm19.phantomism.entity.FirePhantom;
import net.ronm19.phantomism.entity.FrostPhantom;
import net.ronm19.phantomism.entity.EarthPhantom;
import net.ronm19.phantomism.entity.WaterPhantom;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.SandPhantom;
import net.ronm19.phantomism.util.ModTags;

public final class ModSpawnPlacements {
    private ModSpawnPlacements() {
    }

    public static void register(RegisterSpawnPlacementsEvent event) {
        
        event.register(PhantomismContent.BLACK_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::checkBlackSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(PhantomismContent.FIRE_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::checkFireSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(PhantomismContent.FROST_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FrostPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.EARTH_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EarthPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.WATER_PHANTOM.get(), SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WaterPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.STORM_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, StormPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.SAND_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SandPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.GOLD_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.GoldPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.CHERRY_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.CherryPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean checkBlackSpawnRules(EntityType<BlackPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBiome(pos).is(ModTags.Biomes.BLACK_PHANTOM_SPAWNS)
                && isClearSpawnPosition(level, pos) && level.getLevel().isDarkOutside();
    }

    private static boolean checkFireSpawnRules(EntityType<FirePhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBiome(pos).is(ModTags.Biomes.FIRE_PHANTOM_SPAWNS) && isClearSpawnPosition(level, pos);
    }

    private static boolean isClearSpawnPosition(ServerLevelAccessor level, BlockPos pos) {
        return level.canSeeSky(pos) && level.getFluidState(pos).isEmpty();
    }
}
