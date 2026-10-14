package net.ronm19.phantomism.entity.spawn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.RavagerPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class RavagerEncounterSpawner implements CustomSpawner {
    private final Map<Long, Long> nextAttempts = new HashMap<>();
    private int delay = 40;
    private int playerCursor;

    @SubscribeEvent
    public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension().equals(Level.OVERWORLD)
                && event.getCustomSpawners().stream().noneMatch(RavagerEncounterSpawner.class::isInstance)) {
            event.addCustomSpawner(new RavagerEncounterSpawner());
        }
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || level.players().isEmpty() || --this.delay > 0) { return; }
        this.delay = 200;
        long time = level.getGameTime();
        this.nextAttempts.entrySet().removeIf(entry -> time - entry.getValue() > 4800L);
        var considered = new HashSet<Long>();
        var players = level.players();
        int checks = Math.min(4, players.size());
        int first = Math.floorMod(this.playerCursor, players.size());
        this.playerCursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer observer = players.get((first + index) % players.size());
            if (!observer.isAlive() || observer.isSpectator()) { continue; }
            BlockPos center = encounterCenter(level, observer.blockPosition());
            if (center == null || Math.abs(observer.getY() - center.getY()) > 40.0D) { continue; }
            long key = center.asLong();
            if (!considered.add(key) || this.nextAttempts.getOrDefault(key, Long.MIN_VALUE) > time) { continue; }
            if (level.getEntitiesOfClass(RavagerPhantom.class, new net.minecraft.world.phys.AABB(center).inflate(84.0D),
                    phantom -> phantom.isAlive() && !phantom.isTame()).size() >= 2) { this.nextAttempts.put(key, time + 1200L); continue; }
            boolean spawned = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
                double distance = 28.0D + level.getRandom().nextDouble() * 24.0D;
                int horizontal = (int) Math.floor(observer.getX() + Math.cos(angle) * distance);
                int depth = (int) Math.floor(observer.getZ() + Math.sin(angle) * distance);
                if (!level.hasChunkAt(new BlockPos(horizontal, observer.blockPosition().getY(), depth))) { continue; }
                BlockPos position = new BlockPos(horizontal, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal, depth), depth);
                if (!canPlace(level, position, center)) { continue; }
                var phantom = PhantomismContent.RAVAGER_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
                if (phantom == null) { break; }
                phantom.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
                phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null);
                spawned = level.addFreshEntity(phantom);
                break;
            }
            this.nextAttempts.put(key, time + (spawned ? 1200L : 200L));
        }
    }

    public static BlockPos encounterCenter(ServerLevel level, BlockPos position) {
        var raid = level.getRaidAt(position);
        if (raid != null && raid.isActive() && !raid.isOver() && !raid.isStopped()) { return raid.getCenter(); }
        var outpost = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.PILLAGER_OUTPOST);
        if (outpost == null) { return null; }
        for (int offsetX = -4; offsetX <= 4; offsetX++) {
            for (int offsetZ = -4; offsetZ <= 4; offsetZ++) {
                int chunkX = (position.getX() >> 4) + offsetX;
                int chunkZ = (position.getZ() >> 4) + offsetZ;
                if (!level.hasChunkAt(new BlockPos(chunkX << 4, position.getY(), chunkZ << 4))) { continue; }
                var start = level.getChunk(chunkX, chunkZ).getStartForStructure(outpost);
                if (start == null || !start.isValid()) { continue; }
                BlockPos center = start.getBoundingBox().getCenter();
                if (horizontalDistance(position, center) <= 72.0D * 72.0D) { return center; }
            }
        }
        return null;
    }

    public static boolean canPlace(ServerLevel level, BlockPos position, BlockPos center) {
        var type = PhantomismContent.RAVAGER_PHANTOM.get();
        var box = type.getSpawnAABB(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (horizontalDistance(position, center) > 72.0D * 72.0D || !level.getWorldBorder().isWithinBounds(box)
                || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
                || !SpawnPlacements.isSpawnPositionOk(type, level, position) || !level.canSeeSky(position)
                || !level.getFluidState(position).isEmpty() || !level.noCollision(box)) { return false; }
        Vec3 point = Vec3.atBottomCenterOf(position);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(point) < 24.0D * 24.0D) { return false; }
        }
        return true;
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        double horizontal = first.getX() - second.getX();
        double depth = first.getZ() - second.getZ();
        return horizontal * horizontal + depth * depth;
    }
}
