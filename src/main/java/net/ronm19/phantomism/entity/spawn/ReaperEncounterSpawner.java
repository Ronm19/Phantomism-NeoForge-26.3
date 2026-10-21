package net.ronm19.phantomism.entity.spawn;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ReaperPhantom;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class ReaperEncounterSpawner implements CustomSpawner {
    private final Map<UUID, Long> nextAttempts = new HashMap<>();
    private int delay = 40, playerCursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event) {
        if ((event.getLevel().dimension() == Level.OVERWORLD || event.getLevel().dimension() == Level.NETHER)
                && event.getCustomSpawners().stream().noneMatch(ReaperEncounterSpawner.class::isInstance)) { event.addCustomSpawner(new ReaperEncounterSpawner()); }
    }
    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!level.getGameRules().get(GameRules.SPAWN_MOBS) || level.players().isEmpty() || level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER
                || level.dimension() == Level.OVERWORLD && !night(level) || --delay > 0) { return; }
        delay = 400; long time = level.getGameTime(); nextAttempts.values().removeIf(until -> time >= until);
        List<ServerPlayer> players = level.players(); int first = Math.floorMod(playerCursor, players.size()), checks = Math.min(4, players.size()); playerCursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer player = players.get((first + index) % players.size());
            if (!player.isAlive() || player.isSpectator() || nextAttempts.containsKey(player.getUUID()) || !level.hasChunkAt(player.blockPosition())
                    || level.dimension() == Level.NETHER && player.getY() >= 120) { continue; }
            if (!level.getEntitiesOfClass(ReaperPhantom.class, player.getBoundingBox().inflate(128), source -> source.isAlive() && !source.isTame()).isEmpty()) { continue; }
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2, radius = 28 + level.getRandom().nextDouble() * 28;
                int horizontal = (int) Math.floor(player.getX() + Math.cos(angle) * radius), depth = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
                BlockPos column = new BlockPos(horizontal, player.getBlockY(), depth); if (!level.hasChunkAt(column)) { continue; }
                int height = level.dimension() == Level.NETHER ? Math.clamp(player.getBlockY() + level.getRandom().nextInt(33) - 16, 10, 112)
                        : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal, depth) + 2;
                boolean done = false;
                for (int offset = 0; offset < (level.dimension() == Level.NETHER ? 16 : 4); offset++) {
                    BlockPos candidate = new BlockPos(horizontal, height - offset, depth);
                    if (!canPlace(level, candidate)) { continue; }
                    ReaperPhantom source = PhantomismContent.REAPER_PHANTOM.get().create(level, EntitySpawnReason.NATURAL); if (source == null) { break; }
                    source.setPos(candidate.getX() + .5D, candidate.getY(), candidate.getZ() + .5D);
                    source.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), EntitySpawnReason.NATURAL, null);
                    if (level.addFreshEntity(source)) { nextAttempts.put(player.getUUID(), time + 4800); done = true; } break;
                }
                if (done) { break; }
            }
        }
    }
    public static boolean night(ServerLevel level) { long time = Math.floorMod(level.getOverworldClockTime(), 24000L); return time >= 13000 && time < 23000; }
    public static boolean habitat(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && (level.dimension() == Level.NETHER && level.getBiome(position).is(net.minecraft.world.level.biome.Biomes.SOUL_SAND_VALLEY)
                || level.dimension() == Level.OVERWORLD && night(level) && level.getBiome(position).is(net.minecraft.world.level.biome.Biomes.DARK_FOREST));
    }
    public static boolean canPlace(ServerLevel level, BlockPos position) {
        var type = PhantomismContent.REAPER_PHANTOM.get(); AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D);
        if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY - 2, box.minZ), BlockPos.containing(box.maxX, box.maxY + 1, box.maxZ))
                || !level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinY() + 3 || box.maxY > level.getMaxY() - 2
                || !ReaperPhantom.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, position, level.getRandom())
                || !level.noCollision(box.inflate(0, 1, 0)) || level.containsAnyLiquid(box.inflate(0, 1, 0))) { return false; }
        BlockPos floor = position.below(2); if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() || GravityControl.dangerous(level, floor)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 1, box.minZ), BlockPos.containing(box.maxX, box.maxY + 1, box.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } }
        for (ServerPlayer player : level.players()) { if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(position.getX() + .5D, position.getY(), position.getZ() + .5D) < 24 * 24) { return false; } }
        return true;
    }
}
