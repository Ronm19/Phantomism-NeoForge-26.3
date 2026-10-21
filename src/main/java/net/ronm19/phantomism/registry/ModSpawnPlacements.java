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
        event.register(PhantomismContent.PHANTOM_DRAGON.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.PhantomDragon::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.VIOLET_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.VioletPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.TOXIC_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.ToxicPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.SCULK_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.SculkPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.RAVAGER_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.RavagerPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.END_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.EndPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.ZOMBIE_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.ZombiePhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.SKELETON_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.SkeletonPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.ECHO_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.EchoPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.BLOOD_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.BloodPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.VAMPIRE_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.VampirePhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.GRAVITY_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.GravityPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.CHRONO_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.ChronoPhantom::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.MIRAGE_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.MiragePhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.OMEN_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.OmenPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.ANCIENT_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.AncientPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.REAPER_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.ReaperPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.PRIMORDIAL_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.PrimordialPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.PHANTOM_KING.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.PhantomKing::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.DARK_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.DarkPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.VOID_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.VoidPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.ANGEL_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.AngelPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.SPECTRAL_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.SpectralPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.DEMON_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.DemonPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.SENTINEL_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.SentinelPhantom::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.RIFT_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.RiftPhantom::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.WITHER_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.WitherPhantom::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PhantomismContent.HUSK_PHANTOM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.ronm19.phantomism.entity.HuskPhantom::checkSpawnRules,
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
