package net.ronm19.phantomism.entity.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.IllagerPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = "phantomism")
public final class IllagerEncounterSpawner implements CustomSpawner {
    public static final int MAX_NEARBY_WILD = 1;
    private int delay = 80, cursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD && event.getCustomSpawners().stream().noneMatch(IllagerEncounterSpawner.class::isInstance)) { event.addCustomSpawner(new IllagerEncounterSpawner()); }
    }
    @Override public void tick(ServerLevel level, boolean spawnEnemies) {
        if (level.dimension() != Level.OVERWORLD || !level.getGameRules().get(GameRules.SPAWN_MOBS) || level.players().isEmpty() || --delay > 0) { return; }
        delay = 200; var players = level.players(); int checks = Math.min(2, players.size());
        for (int index = 0; index < checks; index++) {
            ServerPlayer player = players.get(Math.floorMod(cursor++, players.size()));
            if (!player.isAlive() || player.isSpectator() || !level.hasChunkAt(player.blockPosition())) { continue; }
            Raid raid = level.getRaidAt(player.blockPosition());
            if (activeRaid(raid)) { tryRaid(level, raid); continue; }
            BlockPos outpost = outpost(level, player.blockPosition()); if (outpost != null) { tryOutpost(level, outpost); }
        }
    }
    public static boolean activeRaid(Raid raid) { return raid != null && raid.isActive() && !raid.isStopped() && !raid.isOver(); }
    public static boolean tryRaid(ServerLevel level, Raid raid) {
        if (level.dimension() != Level.OVERWORLD || !activeRaid(raid) || !level.hasChunkAt(raid.getCenter())) { return false; }
        var identity = level.getRaids().getId(raid); if (identity.isEmpty()) { return false; }
        String key = "raid:" + level.dimension().toString() + ":" + identity.getAsInt();
        if (!IllagerEncounterData.get(level).claim(key, level.getGameTime(), 192000) || level.getRandom().nextInt(5) != 0) { return false; }
        return spawn(level, raid.getCenter());
    }
    public static boolean tryOutpost(ServerLevel level, BlockPos center) {
        String key = "outpost:" + level.dimension().toString() + ":" + center.asLong();
        if (level.dimension() != Level.OVERWORLD || !IllagerEncounterData.get(level).claim(key, level.getGameTime(), 2400)
                || level.getRandom().nextInt(4) != 0) { return false; }
        return spawn(level, center);
    }
    public static BlockPos outpost(ServerLevel level, BlockPos position) {
        if (level.dimension() != Level.OVERWORLD || !level.hasChunkAt(position)) { return null; }
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.PILLAGER_OUTPOST); if (structure == null) { return null; }
        for (int across = -6; across <= 6; across++) {
            for (int depth = -6; depth <= 6; depth++) {
                var chunk = level.getChunkSource().getChunkNow((position.getX() >> 4) + across, (position.getZ() >> 4) + depth); if (chunk == null) { continue; }
                var start = chunk.getStartForStructure(structure); if (start == null || !start.isValid()) { continue; }
                var bounds = start.getBoundingBox(); double horizontal = Math.max(bounds.minX() - position.getX(), Math.max(0, position.getX() - bounds.maxX()));
                double vertical = Math.max(bounds.minZ() - position.getZ(), Math.max(0, position.getZ() - bounds.maxZ()));
                if (horizontal * horizontal + vertical * vertical <= 80 * 80) { return bounds.getCenter(); }
            }
        }
        return null;
    }
    public static boolean spawn(ServerLevel level, BlockPos center) {
        if (!level.hasChunkAt(center) || !level.getEntitiesOfClass(IllagerPhantom.class, new AABB(center).inflate(112, level.getMaxY() - level.getMinY(), 112), source -> !source.isTame() && source.isAlive()).isEmpty()) { return false; }
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2; double radius = 28 + level.getRandom().nextInt(21);
            int across = (int)Math.floor(center.getX() + Math.cos(angle) * radius), depth = (int)Math.floor(center.getZ() + Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(across, center.getY(), depth))) { continue; }
            int altitude = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, across, depth) + 9 + level.getRandom().nextInt(8);
            BlockPos position = new BlockPos(across, altitude, depth);
            if (!IllagerPhantom.checkSpawnRules(PhantomismContent.ILLAGER_PHANTOM.get(), level, EntitySpawnReason.NATURAL, position, level.getRandom()) || !level.canSeeSky(position)) { continue; }
            var box = PhantomismContent.ILLAGER_PHANTOM.get().getSpawnAABB(across + .5D, altitude, depth + .5D);
            if (!level.noCollision(box) || level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator() && player.distanceToSqr(across + .5D, altitude, depth + .5D) < 24 * 24)) { continue; }
            var source = PhantomismContent.ILLAGER_PHANTOM.get().create(level, EntitySpawnReason.NATURAL); if (source == null) { return false; }
            source.setPos(across + .5D, altitude, depth + .5D); source.setOrder(AbstractPhantom.Order.WANDER);
            source.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null);
            return level.addFreshEntity(source);
        }
        return false;
    }
}
