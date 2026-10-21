package net.ronm19.phantomism.entity.spawn;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.GolemPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class GolemVillageSpawner implements CustomSpawner {
    public static final class Ledger extends SavedData {
        public static final Codec<Ledger> CODEC = Codec.unboundedMap(Codec.STRING, Codec.BOOL).xmap(Ledger::new, ledger -> ledger.decisions);
        private final Map<String, Boolean> decisions;
        private final Map<Long, List<BlockPos>> homes = new HashMap<>();
        public Ledger() { this(Map.of()); }
        private Ledger(Map<String, Boolean> values) { decisions = new HashMap<>(values); for (String key : values.keySet()) { index(key); } }
        public boolean decided(String key) { return decisions.containsKey(key); }
        public boolean decide(String key, long seed) { if (decisions.containsKey(key)) { return false; } boolean selected = new java.util.Random(seed ^ key.hashCode() * 341873128712L).nextInt(100) < 40; decisions.put(key, selected); index(key); setDirty(); return selected; }
        public int size() { return decisions.size(); }
        private void index(String key) { if (!key.startsWith("home:")) { return; } try { BlockPos position = BlockPos.of(Long.parseLong(key.substring(5))); homes.computeIfAbsent(new ChunkPos(position.getX() >> 7, position.getZ() >> 7).pack(), ignored -> new ArrayList<>()).add(position); } catch (NumberFormatException ignored) { } }
        public boolean nearby(BlockPos center) { for (int across = -1; across <= 1; across++) { for (int depth = -1; depth <= 1; depth++) { for (BlockPos position : homes.getOrDefault(new ChunkPos((center.getX() >> 7) + across, (center.getZ() >> 7) + depth).pack(), List.of())) { if (position.distSqr(center) < 128 * 128) { return true; } } } } return false; }
    }
    public static final SavedDataType<Ledger> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "golem_villages"), Ledger::new, Ledger.CODEC);
    private int delay = 100, cursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event) { if (event.getLevel().dimension() == Level.OVERWORLD && event.getCustomSpawners().stream().noneMatch(GolemVillageSpawner.class::isInstance)) { event.addCustomSpawner(new GolemVillageSpawner()); } }
    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (level.dimension() != Level.OVERWORLD || !level.getGameRules().get(GameRules.SPAWN_MOBS) || level.players().isEmpty() || --delay > 0) { return; } delay = 200;
        var players = level.players(); var player = players.get(Math.floorMod(cursor++, players.size())); if (player.isSpectator() || !player.isAlive()) { return; }
        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(96), candidate -> candidate.isAlive() && !candidate.isBaby()).stream().limit(16).toList()) {
            BlockPos center = villager.blockPosition(); String key = generatedVillage(level, center); if (key == null) { continue; }
            Ledger ledger = level.getDataStorage().computeIfAbsent(TYPE);
            if (key.isEmpty()) { var home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null); if (home == null || home.dimension() != level.dimension() || !level.hasChunkAt(home.pos()) || !level.isVillage(center) || ledger.nearby(home.pos())) { continue; } center = home.pos(); key = "home:" + center.asLong(); }
            if (ledger.decided(key)) { continue; }
            BlockPos position = findPosition(level, center); if (position == null) { continue; }
            if (!ledger.decide(key, level.getSeed())) { continue; }
            if (!level.getEntitiesOfClass(GolemPhantom.class, new AABB(center).inflate(128), source -> source.isAlive()).isEmpty()) { return; }
            GolemPhantom source = PhantomismContent.GOLEM_PHANTOM.get().create(level, EntitySpawnReason.NATURAL); if (source == null) { return; }
            source.setPos(position.getX() + .5D, position.getY(), position.getZ() + .5D); source.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null); source.setGuardPosition(net.minecraft.core.GlobalPos.of(level.dimension(), position)); source.setPersistenceRequired(); level.addFreshEntity(source); return;
        }
    }
    private static String generatedVillage(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4); if (chunk == null) { return null; }
        var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        int checks = 0;
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (!registry.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE)) { continue; }
            for (long reference : entry.getValue()) {
                if (++checks > 32) { return null; } var key = ChunkPos.unpack(reference); var origin = level.getChunkSource().getChunkNow(key.x(), key.z()); if (origin == null) { return null; }
                var start = origin.getStartForStructure(entry.getKey()); if (start != null && start.isValid() && start.getBoundingBox().isInside(position)) { return "village:" + start.getChunkPos().pack(); }
            }
        }
        return "";
    }
    public static BlockPos findPosition(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 32; attempt++) { double angle = attempt * 2.399963229728653D; int radius = 8 + attempt % 4 * 4; int across = center.getX() + (int) (Math.cos(angle) * radius), depth = center.getZ() + (int) (Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(across, center.getY(), depth))) { continue; } BlockPos position = new BlockPos(across, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, across, depth) + 2, depth);
            AABB box = new AABB(position.getX() - .76D, position.getY(), position.getZ() - .76D, position.getX() + 1.76D, position.getY() + 1.62D, position.getZ() + 1.76D);
            if (Math.abs(position.getY() - center.getY()) > 24 || !level.getWorldBorder().isWithinBounds(box)) { continue; }
            boolean loaded = true; for (BlockPos point : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (!level.hasChunkAt(point)) { loaded = false; break; } }
            if (loaded && level.noBlockCollision(null, box) && !level.containsAnyLiquid(box) && level.getBlockState(position.below(3)).isFaceSturdy(level, position.below(3), net.minecraft.core.Direction.UP) && !net.ronm19.phantomism.entity.ai.GravityControl.dangerous(level, position.below(3))) { return position; }
        }
        return null;
    }
}
