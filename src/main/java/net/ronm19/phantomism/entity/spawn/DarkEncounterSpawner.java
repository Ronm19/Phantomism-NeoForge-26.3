package net.ronm19.phantomism.entity.spawn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.DarkPhantom;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class DarkEncounterSpawner implements CustomSpawner {
    public static final int CHECK_INTERVAL_TICKS = 200;
    public static final int SUCCESS_COOLDOWN_TICKS = 6000;
    public static final int MAX_NEARBY_WILD = 2;
    private final Map<UUID, Long> nextAttempts = new HashMap<>();
    private int delay = 40;
    private int playerCursor;

    @SubscribeEvent
    public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD
                && event.getCustomSpawners().stream().noneMatch(DarkEncounterSpawner.class::isInstance)) {
            event.addCustomSpawner(new DarkEncounterSpawner());
        }
    }

    public static boolean habitat(ServerLevel level, BlockPos position) {
        return level.dimension() == Level.OVERWORLD && level.hasChunkAt(position) && level.getDifficulty() != Difficulty.PEACEFUL
                && DarkPhantom.night(level) && level.getBiome(position).is(ModTags.Biomes.DARK_PHANTOM_SPAWNS);
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (level.dimension() != Level.OVERWORLD || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || level.getDifficulty() == Difficulty.PEACEFUL || !DarkPhantom.night(level) || level.players().isEmpty() || --delay > 0) { return; }
        delay = CHECK_INTERVAL_TICKS;
        long now = level.getGameTime();
        nextAttempts.entrySet().removeIf(entry -> now >= entry.getValue() || level.getPlayerByUUID(entry.getKey()) == null);
        List<ServerPlayer> players = level.players();
        int first = Math.floorMod(playerCursor, players.size()), checks = Math.min(4, players.size());
        playerCursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer player = players.get((first + index) % players.size());
            if (!player.isAlive() || player.isSpectator() || nextAttempts.containsKey(player.getUUID()) || !habitat(level, player.blockPosition())) { continue; }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
            if (player.getY() < surface - 8 || player.getY() > surface + 48) { continue; }
            AABB area = new AABB(player.getX() - 160, level.getMinY(), player.getZ() - 160, player.getX() + 160, level.getMaxY() + 1, player.getZ() + 160);
            int wild = level.getEntitiesOfClass(DarkPhantom.class, area, source -> source.isAlive() && !source.isTame()).size();
            if (wild >= MAX_NEARBY_WILD) { continue; }
            int group = Math.min(MAX_NEARBY_WILD - wild, 1 + level.getRandom().nextInt(2));
            SpawnGroupData groupData = null;
            boolean spawned = false;
            for (int member = 0; member < group; member++) {
                BlockPos position = findPosition(level, player);
                if (position == null) { break; }
                DarkPhantom source = PhantomismContent.DARK_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
                if (source == null) { break; }
                source.setPos(position.getX() + .5D, position.getY(), position.getZ() + .5D);
                groupData = source.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, groupData);
                spawned |= level.addFreshEntity(source);
            }
            if (spawned) { nextAttempts.put(player.getUUID(), now + SUCCESS_COOLDOWN_TICKS); }
        }
    }

    private static BlockPos findPosition(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2, distance = 32 + level.getRandom().nextDouble() * 24;
            int across = (int) Math.floor(player.getX() + Math.cos(angle) * distance), depth = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(across, player.getBlockY(), depth))) { continue; }
            int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, across, depth), ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, across, depth);
            for (int surface : new int[]{canopy, ground}) {
                for (int offset = 0; offset < 4; offset++) {
                    BlockPos position = new BlockPos(across, surface + 2 - offset, depth);
                    if (Math.abs(position.getY() - player.getY()) <= 32 && canPlace(level, position)) { return position; }
                }
            }
        }
        return null;
    }

    public static boolean canPlace(ServerLevel level, BlockPos position) {
        if (!habitat(level, position)) { return false; }
        AABB box = PhantomismContent.DARK_PHANTOM.get().getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.1D, 0, .1D).expandTowards(0, .25D, 0);
        if (box.minY < level.getMinY() + 6 || box.maxY > level.getMaxY() - 2 || !level.getWorldBorder().isWithinBounds(box)
                || !level.hasChunksAt(BlockPos.containing(box.minX, box.minY - 6, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !level.noCollision(box) || level.containsAnyLiquid(box)) { return false; }
        boolean supported = false;
        for (int offset = 1; offset <= 6; offset++) {
            BlockPos floor = position.below(offset);
            if (GravityControl.dangerous(level, floor) || !level.getFluidState(floor).isEmpty()) { return false; }
            if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) { supported = true; break; }
        }
        if (!supported) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 1, box.minZ), BlockPos.containing(box.maxX, box.maxY + 1, box.maxZ))) {
            if (GravityControl.dangerous(level, block)) { return false; }
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(position.getX() + .5D, position.getY(), position.getZ() + .5D) < 24 * 24) { return false; }
        }
        return true;
    }
}
