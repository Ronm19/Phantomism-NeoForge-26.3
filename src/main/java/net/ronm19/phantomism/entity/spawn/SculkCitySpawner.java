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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SculkPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class SculkCitySpawner implements CustomSpawner {
    public static final int MAX_WILD_PER_CITY = 4;
    private final Map<Long, Long> nextAttempts = new HashMap<>();
    private int checkDelay = 20;
    private int playerCursor;

    @SubscribeEvent
    public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension().equals(Level.OVERWORLD)
                && event.getCustomSpawners().stream().noneMatch(SculkCitySpawner.class::isInstance)) {
            event.addCustomSpawner(new SculkCitySpawner());
        }
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || level.players().isEmpty() || --this.checkDelay > 0) { return; }
        this.checkDelay = 200;
        long time = level.getGameTime();
        this.nextAttempts.entrySet().removeIf(entry -> time - entry.getValue() > 2400L);
        var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(net.minecraft.world.level.levelgen.structure.BuiltinStructures.ANCIENT_CITY);
        if (city == null) { return; }
        var considered = new HashSet<Long>();
        var players = level.players();
        int checks = Math.min(8, players.size());
        int startIndex = Math.floorMod(this.playerCursor, players.size());
        this.playerCursor = (startIndex + checks) % players.size();
        for (int check = 0; check < checks; check++) {
            ServerPlayer player = players.get((startIndex + check) % players.size());
            if (!player.isAlive() || player.isSpectator()) { continue; }
            StructureStart start = this.findCity(level, city, player);
            if (start == null) { continue; }
            long key = start.getChunkPos().pack();
            if (!considered.add(key) || this.nextAttempts.getOrDefault(key, Long.MIN_VALUE) > time) { continue; }
            int wild = countWild(level, start);
            if (wild >= MAX_WILD_PER_CITY || level.getEntitiesOfClass(SculkPhantom.class, player.getBoundingBox().inflate(56.0D),
                    phantom -> phantom.isAlive() && !phantom.isTame()).size() >= 2) {
                this.nextAttempts.put(key, time + 600L);
                continue;
            }
            BlockPos position = this.findSpawnPosition(level, start, player);
            if (position == null) { this.nextAttempts.put(key, time + 200L); continue; }
            int group = Math.min(MAX_WILD_PER_CITY - wild, 1 + level.getRandom().nextInt(2));
            boolean spawned = false;
            for (int member = 0; member < group; member++) {
                BlockPos candidate = member == 0 ? position : position.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
                if (!this.canPlace(level, start, candidate)) { continue; }
                var phantom = PhantomismContent.SCULK_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
                if (phantom == null) { continue; }
                phantom.setPos(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), EntitySpawnReason.NATURAL, null);
                spawned |= level.addFreshEntity(phantom);
            }
            this.nextAttempts.put(key, time + (spawned ? 600L + level.getRandom().nextInt(400) : 200L));
        }
    }

    private StructureStart findCity(ServerLevel level, Structure city, ServerPlayer player) {
        BlockPos position = player.blockPosition();
        if (!level.hasChunkAt(position)) { return null; }
        var references = level.getChunkAt(position).getReferencesForStructure(city);
        int checked = 0;
        for (long reference : references) {
            if (++checked > 4) { break; }
            ChunkPos chunk = ChunkPos.unpack(reference);
            if (!level.hasChunkAt(new BlockPos(chunk.getMinBlockX(), position.getY(), chunk.getMinBlockZ()))) { continue; }
            StructureStart start = level.getChunk(chunk.x(), chunk.z()).getStartForStructure(city);
            if (start != null && start.isValid() && area(start).inflate(24.0D, 16.0D, 24.0D).contains(player.position())) { return start; }
        }
        return null;
    }

    public static int countWild(ServerLevel level, StructureStart start) {
        return level.getEntitiesOfClass(SculkPhantom.class, area(start).inflate(24.0D),
                phantom -> phantom.isAlive() && !phantom.isTame()).size();
    }

    private static AABB area(StructureStart start) {
        var box = start.getBoundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D);
    }

    private BlockPos findSpawnPosition(ServerLevel level, StructureStart start, ServerPlayer player) {
        var box = start.getBoundingBox();
        int bottom = Math.max(box.minY(), player.blockPosition().getY() - 16);
        int top = Math.min(box.maxY(), player.blockPosition().getY() + 8);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 28.0D + level.getRandom().nextDouble() * 24.0D;
            int horizontal = Math.clamp((int) Math.floor(player.getX() + Math.cos(angle) * distance), box.minX(), box.maxX());
            int depth = Math.clamp((int) Math.floor(player.getZ() + Math.sin(angle) * distance), box.minZ(), box.maxZ());
            if (!level.hasChunkAt(new BlockPos(horizontal, player.blockPosition().getY(), depth))) { continue; }
            for (int height = top; height >= bottom; height--) {
                BlockPos candidate = new BlockPos(horizontal, height, depth);
                if (this.canPlace(level, start, candidate)) { return candidate; }
            }
        }
        return null;
    }

    private boolean canPlace(ServerLevel level, StructureStart start, BlockPos position) {
        var type = PhantomismContent.SCULK_PHANTOM.get();
        AABB box = type.getSpawnAABB(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (!start.getBoundingBox().isInside(position)
                || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
                || !SpawnPlacements.isSpawnPositionOk(type, level, position)
                || !level.getFluidState(position).isEmpty() || !level.getFluidState(position.above()).isEmpty()
                || !level.noCollision(box)) { return false; }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D) < 24.0D * 24.0D) { return false; }
        }
        return true;
    }
}
