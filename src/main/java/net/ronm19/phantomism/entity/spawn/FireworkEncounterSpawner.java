package net.ronm19.phantomism.entity.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.FireworkPhantom;
import net.ronm19.phantomism.entity.holiday.HolidayCalendar;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class FireworkEncounterSpawner implements CustomSpawner {
    private int delay = 20, cursor;
    private final Map<UUID, Long> recent = new HashMap<>();
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension().equals(Level.OVERWORLD) && event.getCustomSpawners().stream().noneMatch(FireworkEncounterSpawner.class::isInstance)) { event.addCustomSpawner(new FireworkEncounterSpawner()); }
    }
    public static boolean habitat(ServerLevel level, BlockPos point) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.hasChunkAt(point)) { return false; }
        var biome = level.getBiome(point);
        return biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.BEACH)
                || biome.is(Biomes.STONY_SHORE) || biome.is(Biomes.MEADOW);
    }
    public static FireworkPhantom spawnAt(ServerLevel level, BlockPos point) {
        if (!level.getGameRules().get(GameRules.SPAWN_MOBS) || !HolidayCalendar.naturalSpawningAllowed(HolidayFestival.FIREWORK)
                || !FireworkPhantom.checkSpawnRules(PhantomismContent.FIREWORK_PHANTOM.get(), level, EntitySpawnReason.NATURAL, point, level.getRandom())
                || level.getEntitiesOfClass(FireworkPhantom.class, new AABB(point).inflate(64), phantom -> phantom.isAlive() && !phantom.isTame()).size() >= 4) { return null; }
        FireworkPhantom phantom = PhantomismContent.FIREWORK_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
        if (phantom == null) { return null; }
        phantom.setPos(point.getX() + .5D, point.getY(), point.getZ() + .5D);
        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(point), EntitySpawnReason.NATURAL, null);
        return level.addFreshEntity(phantom) ? phantom : null;
    }
    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!HolidayCalendar.naturalSpawningAllowed(HolidayFestival.FIREWORK) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || level.players().isEmpty() || --delay > 0) { return; }
        delay = 100; long now = level.getGameTime(); recent.entrySet().removeIf(entry -> entry.getValue() <= now);
        var players = level.players(); int first = Math.floorMod(cursor++, players.size());
        for (int index = 0; index < Math.min(2, players.size()); index++) {
            var player = players.get((first + index) % players.size());
            if (!player.isAlive() || player.isSpectator() || recent.containsKey(player.getUUID())) { continue; }
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2, distance = 8 + level.getRandom().nextDouble() * 16;
                int horizontal = (int)Math.floor(player.getX() + Math.cos(angle) * distance), depth = (int)Math.floor(player.getZ() + Math.sin(angle) * distance);
                if (!level.hasChunkAt(new BlockPos(horizontal, player.getBlockY(), depth))) { continue; }
                int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, horizontal, depth) + 2;
                if (Math.abs(height - player.getY()) <= 48 && spawnAt(level, new BlockPos(horizontal, height, depth)) != null) { recent.put(player.getUUID(), now + 200); break; }
            }
        }
    }
}




