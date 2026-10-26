package net.ronm19.phantomism.entity.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.HalloweenPhantom;
import net.ronm19.phantomism.entity.holiday.HolidayCalendar;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class HalloweenEncounterSpawner implements CustomSpawner {
    private final Map<UUID, Long> nextAttempts = new HashMap<>();
    private int delay = 20;
    private int cursor;

    @SubscribeEvent
    public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension().equals(Level.OVERWORLD)
                && event.getCustomSpawners().stream().noneMatch(HalloweenEncounterSpawner.class::isInstance)) {
            event.addCustomSpawner(new HalloweenEncounterSpawner());
        }
    }

    public static boolean habitat(ServerLevel level, BlockPos position) {
        return level.dimension().equals(Level.OVERWORLD) && level.hasChunkAt(position)
                && !level.getBiome(position).is(Biomes.DEEP_DARK);
    }

    public static boolean hasPumpkin(ServerLevel level, BlockPos center) {
        return pumpkins(level, center, Config.halloweenPumpkinRadius(), Config.halloweenPumpkinVerticalRange(), 64)
                .stream().anyMatch(pumpkin -> seesPumpkin(level, center, pumpkin));
    }

    private static boolean pumpkin(BlockState state) {
        return state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN);
    }

    private static boolean seesPumpkin(ServerLevel level, BlockPos position, BlockPos pumpkin) {
        int radius = Config.halloweenPumpkinRadius();
        if (position.distSqr(pumpkin) > radius * radius || Math.abs(position.getY() - pumpkin.getY()) > Config.halloweenPumpkinVerticalRange()
                || !habitat(level, pumpkin) || !pumpkin(level.getBlockState(pumpkin))) { return false; }
        var hit = level.clip(new ClipContext(Vec3.atBottomCenterOf(position).add(0, .65D, 0), Vec3.atCenterOf(pumpkin),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pumpkin);
    }

    private static List<BlockPos> pumpkins(ServerLevel level, BlockPos center, int radius, int vertical, int limit) {
        List<BlockPos> result = new ArrayList<>();
        int centerChunkX = SectionPos.blockToSectionCoord(center.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(center.getZ());
        int chunkRadius = (radius + 15) / 16;
        int inspected = 0;
        for (int ring = 0; ring <= chunkRadius; ring++) {
            for (int offsetX = -ring; offsetX <= ring; offsetX++) {
                for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != ring) { continue; }
                    var chunk = level.getChunkSource().getChunkNow(centerChunkX + offsetX, centerChunkZ + offsetZ);
                    if (chunk == null) { continue; }
                    int first = Math.max(0, chunk.getSectionIndex(center.getY() - vertical));
                    int last = Math.min(chunk.getSections().length - 1, chunk.getSectionIndex(center.getY() + vertical));
                    for (int sectionIndex = first; sectionIndex <= last; sectionIndex++) {
                        var section = chunk.getSections()[sectionIndex];
                        if (!section.maybeHas(HalloweenEncounterSpawner::pumpkin)) { continue; }
                        if (++inspected > 16) { return result; }
                        int bottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
                        for (int height = Math.max(0, center.getY() - vertical - bottom); height <= Math.min(15, center.getY() + vertical - bottom); height++) {
                            for (int horizontal = 0; horizontal < 16; horizontal++) {
                                int worldX = (centerChunkX + offsetX) * 16 + horizontal;
                                int deltaX = worldX - center.getX();
                                for (int depth = 0; depth < 16; depth++) {
                                    int worldZ = (centerChunkZ + offsetZ) * 16 + depth;
                                    int deltaZ = worldZ - center.getZ();
                                    if (deltaX * deltaX + deltaZ * deltaZ <= radius * radius && pumpkin(section.getBlockState(horizontal, height, depth))) {
                                        BlockPos position = new BlockPos(worldX, bottom + height, worldZ);
                                        if (habitat(level, position)) { result.add(position); }
                                        if (result.size() >= limit) { return result; }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static boolean safeSpace(ServerLevel level, BlockPos position) {
        AABB box = PhantomismContent.HALLOWEEN_PHANTOM.get().getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D);
        return box.minY > level.getMinY() && box.maxY < level.getMaxY() && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && !level.containsAnyLiquid(box) && level.noCollision(box);
    }

    public static boolean canPlace(ServerLevel level, BlockPos position) {
        return canPlace(level, position, null);
    }

    private static boolean canPlace(ServerLevel level, BlockPos position, BlockPos pumpkin) {
        if (!Config.halloweenSpawningEnabled() || !HolidayCalendar.naturalSpawningAllowed(HolidayFestival.HALLOWEEN) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !habitat(level, position) || !safeSpace(level, position)) { return false; }
        if (level.getEntitiesOfClass(HalloweenPhantom.class, new AABB(position).inflate(48), phantom -> phantom.isAlive() && !phantom.isTame()).size()
                >= Config.halloweenPopulationLimit()) { return false; }
        return pumpkin == null ? hasPumpkin(level, position) : seesPumpkin(level, position, pumpkin);
    }

    public static HalloweenPhantom spawnAt(ServerLevel level, BlockPos position) {
        return spawnAt(level, position, null);
    }

    private static HalloweenPhantom spawnAt(ServerLevel level, BlockPos position, BlockPos pumpkin) {
        if (!canPlace(level, position, pumpkin)) { return null; }
        HalloweenPhantom phantom = PhantomismContent.HALLOWEEN_PHANTOM.get().create(level, EntitySpawnReason.NATURAL);
        if (phantom == null) { return null; }
        phantom.setPos(position.getX() + .5D, position.getY(), position.getZ() + .5D);
        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null);
        return level.addFreshEntity(phantom) ? phantom : null;
    }

    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!Config.halloweenSpawningEnabled() || !HolidayCalendar.naturalSpawningAllowed(HolidayFestival.HALLOWEEN) || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty() || --delay > 0) { return; }
        delay = Config.halloweenSpawnInterval();
        long now = level.getGameTime();
        nextAttempts.entrySet().removeIf(entry -> entry.getValue() <= now);
        var players = level.players();
        int checks = Math.min(2, players.size());
        int first = Math.floorMod(cursor, players.size());
        cursor = (first + checks) % players.size();
        for (int index = 0; index < checks; index++) {
            ServerPlayer player = players.get((first + index) % players.size());
            if (!player.isAlive() || player.isSpectator() || nextAttempts.containsKey(player.getUUID())
                    || level.getRandom().nextInt(Config.halloweenSpawnChance()) != 0) { continue; }
            List<BlockPos> anchors = pumpkins(level, player.blockPosition(), 48, 32, 64);
            anchors.sort(java.util.Comparator.comparingDouble(pumpkin -> pumpkin.distSqr(player.blockPosition())));
            int inspected = 0;
            boolean spawned = false;
            for (BlockPos pumpkin : anchors) {
                if (++inspected > 8) { break; }
                for (int trial = 0; trial < 12; trial++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2;
                    double distance = 3 + level.getRandom().nextDouble() * Math.min(9, Config.halloweenPumpkinRadius() * .6D);
                    BlockPos position = trial < 3 ? pumpkin.above(3 - trial) : pumpkin.offset((int)Math.round(Math.cos(angle) * distance),
                            level.getRandom().nextInt(Math.min(7, Config.halloweenPumpkinVerticalRange() + 1)), (int)Math.round(Math.sin(angle) * distance));
                    if (spawnAt(level, position, pumpkin) != null) {
                        nextAttempts.put(player.getUUID(), now + 200);
                        spawned = true;
                        break;
                    }
                }
                if (spawned) { break; }
            }
        }
    }
}
