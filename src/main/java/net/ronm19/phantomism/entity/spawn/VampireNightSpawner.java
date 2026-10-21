package net.ronm19.phantomism.entity.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.ronm19.phantomism.entity.VampirePhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class VampireNightSpawner implements CustomSpawner {
    public static final int MAX_NEARBY_WILD = 2;
    private final Map<UUID, Long> nextAttempts = new HashMap<>();
    private int delay = 40;
    private int playerCursor;

    @SubscribeEvent
    public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension().equals(Level.OVERWORLD)
                && event.getCustomSpawners().stream().noneMatch(VampireNightSpawner.class::isInstance)) {
            event.addCustomSpawner(new VampireNightSpawner());
        }
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        long night = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (!level.dimension().equals(Level.OVERWORLD) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || night < 13000 || night >= 23000 || level.players().isEmpty() || --this.delay > 0) { return; }
        this.delay = 200;
        long time = level.getGameTime();
        this.nextAttempts.entrySet().removeIf(entry -> time >= entry.getValue());
        var players = level.players();
        int checks = Math.min(4, players.size());
        int first = Math.floorMod(this.playerCursor, players.size());
        this.playerCursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer observer = players.get((first + index) % players.size());
            if (!observer.isAlive() || observer.isSpectator() || !level.hasChunkAt(observer.blockPosition())
                    || this.nextAttempts.containsKey(observer.getUUID())
                    || !level.getBiome(observer.blockPosition()).is(ModTags.Biomes.VAMPIRE_PHANTOM_SPAWNS)) { continue; }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, observer.getBlockX(), observer.getBlockZ());
            if (observer.getY() < surface - 8 || observer.getY() > surface + 64) { continue; }
            AABB area = new AABB(observer.getX() - 128, level.getMinY(), observer.getZ() - 128,
                    observer.getX() + 128, level.getMaxY() + 1, observer.getZ() + 128);
            int wild = level.getEntitiesOfClass(VampirePhantom.class, area, phantom -> phantom.isAlive() && !phantom.isTame()).size();
            if (wild >= MAX_NEARBY_WILD) { continue; }
            int group = Math.min(MAX_NEARBY_WILD - wild, 1 + level.getRandom().nextInt(2));
            SpawnGroupData groupData = null;
            boolean spawned = false;
            for (int member = 0; member < group; member++) {
                BlockPos position = findPosition(level, observer);
                if (position == null) { break; }
                var phantom = PhantomismContent.VAMPIRE_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
                if (phantom == null) { break; }
                phantom.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
                groupData = phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, groupData);
                spawned |= level.addFreshEntity(phantom);
            }
            if (spawned) { this.nextAttempts.put(observer.getUUID(), time + 1200L); }
        }
    }

    private static BlockPos findPosition(ServerLevel level, ServerPlayer observer) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 28.0D + level.getRandom().nextDouble() * 28.0D;
            int horizontal = (int) Math.floor(observer.getX() + Math.cos(angle) * distance);
            int depth = (int) Math.floor(observer.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(horizontal, observer.getBlockY(), depth))) { continue; }
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, horizontal, depth) + 2;
            BlockPos position = new BlockPos(horizontal, height, depth);
            if (Math.abs(height - observer.getY()) <= 48 && canPlace(level, position)) { return position; }
        }
        return null;
    }

    public static boolean canPlace(ServerLevel level, BlockPos position) {
        var type = PhantomismContent.VAMPIRE_PHANTOM.get();
        AABB box = type.getSpawnAABB(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() + 1 || !level.getWorldBorder().isWithinBounds(box)
                || !level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !VampirePhantom.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, position, level.getRandom())
                || !level.canSeeSky(position) || level.containsAnyLiquid(box) || !level.noCollision(box)) { return false; }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()
                    && player.distanceToSqr(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D) < 24.0D * 24.0D) { return false; }
        }
        return true;
    }
}
