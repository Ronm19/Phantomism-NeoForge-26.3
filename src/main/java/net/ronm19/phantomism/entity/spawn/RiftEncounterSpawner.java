package net.ronm19.phantomism.entity.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.RiftPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class RiftEncounterSpawner implements CustomSpawner {
    private final Map<UUID, Long> nextAttempts = new HashMap<>();
    private int delay = 40;
    private int playerCursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension() == Level.NETHER && event.getCustomSpawners().stream().noneMatch(RiftEncounterSpawner.class::isInstance)) { event.addCustomSpawner(new RiftEncounterSpawner()); }
    }
    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (level.dimension() != Level.NETHER || !level.getGameRules().get(GameRules.SPAWN_MOBS) || level.players().isEmpty() || --this.delay > 0) { return; }
        this.delay = 200; long time = level.getGameTime(); this.nextAttempts.entrySet().removeIf(entry -> time >= entry.getValue());
        var players = level.players(); int checks = Math.min(4, players.size()); int first = Math.floorMod(this.playerCursor, players.size()); this.playerCursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer player = players.get((first + index) % players.size());
            if (!player.isAlive() || player.isSpectator() || player.getY() >= 120 || this.nextAttempts.containsKey(player.getUUID())
                    || !level.hasChunkAt(player.blockPosition()) || !level.getBiome(player.blockPosition()).is(ModTags.Biomes.RIFT_PHANTOM_SPAWNS)) { continue; }
            AABB area = new AABB(player.getX() - 160, level.getMinY(), player.getZ() - 160, player.getX() + 160, 120, player.getZ() + 160);
            if (!level.getEntitiesOfClass(RiftPhantom.class, area, phantom -> phantom.isAlive() && !phantom.isTame()).isEmpty()) { continue; }
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2; double radius = 28 + level.getRandom().nextDouble() * 28;
                int horizontal = (int) Math.floor(player.getX() + Math.cos(angle) * radius), depth = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
                int height = Math.clamp(player.getBlockY() + level.getRandom().nextInt(33) - 16, 10, 112);
                BlockPos position = new BlockPos(horizontal, height, depth);
                if (!level.hasChunkAt(position)) { continue; }
                boolean done = false;
                for (int offset = 0; offset < 16; offset++) {
                    BlockPos candidate = position.below(offset);
                    if (!canPlace(level, candidate)) { continue; }
                    RiftPhantom phantom = PhantomismContent.RIFT_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
                    if (phantom == null) { break; }
                    phantom.setPos(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), EntitySpawnReason.NATURAL, null);
                    if (level.addFreshEntity(phantom)) { this.nextAttempts.put(player.getUUID(), time + 2400); done = true; } break;
                }
                if (done) { break; }
            }
        }
    }
    public static boolean canPlace(ServerLevel level, BlockPos position) {
        var type = PhantomismContent.RIFT_PHANTOM.get(); AABB box = type.getSpawnAABB(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (box.minY < level.getMinY() + 2 || box.maxY > 119 || !level.getWorldBorder().isWithinBounds(box)
                || !level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !RiftPhantom.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, position, level.getRandom())
                || !level.noCollision(box.inflate(0, 1, 0)) || level.containsAnyLiquid(box.inflate(0, 1, 0))) { return false; }
        BlockPos floor = position.below(2); var state = level.getBlockState(floor);
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 1, box.minZ), BlockPos.containing(box.maxX, box.maxY + 1, box.maxZ))) { if (net.ronm19.phantomism.entity.ai.GravityControl.dangerous(level, block)) { return false; } }
        if (state.getCollisionShape(level, floor).isEmpty() || !state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) { return false; }
        for (ServerPlayer player : level.players()) { if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D) < 24 * 24) { return false; } }
        return true;
    }
}
