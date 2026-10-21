package net.ronm19.phantomism.entity.spawn;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AncientPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class AncientEncounterSpawner implements CustomSpawner {
    private final Map<UUID,Long> nextAttempts=new HashMap<>();
    private int playerCursor;
    private int delay=40;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event){if(event.getLevel().dimension().equals(Level.OVERWORLD)&&event.getCustomSpawners().stream().noneMatch(AncientEncounterSpawner.class::isInstance)){event.addCustomSpawner(new AncientEncounterSpawner());}}
    @Override public void tick(ServerLevel level,boolean spawnEnemies){if(!night(level)||!level.getGameRules().get(GameRules.SPAWN_MOBS)||level.players().isEmpty()||--delay>0){return;}delay=200;attempt(level);}
    public static boolean night(ServerLevel level){long time=Math.floorMod(level.getOverworldClockTime(),24000);return level.dimension().equals(Level.OVERWORLD)&&time>=13000&&time<23000;}
    public static boolean cave(ServerLevel level,BlockPos position){var biome=level.getBiome(position);return(biome.is(Biomes.DRIPSTONE_CAVES)||biome.is(Biomes.LUSH_CAVES))&&!level.canSeeSky(position)&&position.getY()<=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,position.getX(),position.getZ())-16;}
    public static boolean habitat(ServerLevel level,BlockPos position){if(!level.dimension().equals(Level.OVERWORLD)||!level.hasChunkAt(position)){return false;}var biome=level.getBiome(position);return biome.is(ModTags.Biomes.ANCIENT_PHANTOM_SPAWNS)&&(biome.is(BiomeTags.IS_MOUNTAIN)||!biome.is(Biomes.DRIPSTONE_CAVES)&&!biome.is(Biomes.LUSH_CAVES)||cave(level,position))||nearRuin(level,position);}
    public static boolean nearRuin(ServerLevel level,BlockPos position){
        var structures=level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var ruins=List.of(BuiltinStructures.TRAIL_RUINS,BuiltinStructures.DESERT_PYRAMID,BuiltinStructures.JUNGLE_TEMPLE,BuiltinStructures.ANCIENT_CITY).stream().map(key->structures.get(key).orElseThrow().value()).toList();
        int centerX=Math.floorDiv(position.getX(),16),centerZ=Math.floorDiv(position.getZ(),16);
        for(int offsetX=-6;offsetX<=6;offsetX++){for(int offsetZ=-6;offsetZ<=6;offsetZ++){
            var chunk=level.getChunkSource().getChunkNow(centerX+offsetX,centerZ+offsetZ);if(chunk==null){continue;}
            for(var ruin:ruins){var start=chunk.getStartForStructure(ruin);if(start==null||!start.isValid()){continue;}var box=start.getBoundingBox();double across=Math.max(box.minX()-position.getX(),Math.max(0,position.getX()-box.maxX())),depth=Math.max(box.minZ()-position.getZ(),Math.max(0,position.getZ()-box.maxZ()));if(across*across+depth*depth<=64*64&&position.getY()>=box.minY()-32&&position.getY()<=box.maxY()+64){return true;}}
        }}return false;
    }
    public void attempt(ServerLevel level){
        if(!night(level)||!level.getGameRules().get(GameRules.SPAWN_MOBS)){return;}long now=level.getGameTime();nextAttempts.entrySet().removeIf(entry->entry.getValue()<=now||level.getPlayerByUUID(entry.getKey())==null);
        List<ServerPlayer> players=level.players();if(players.isEmpty()){return;}int checks=Math.min(4,players.size()),first=Math.floorMod(playerCursor,players.size());playerCursor=(first+checks)%players.size();
        for(int index=0;index<checks;index++){
            ServerPlayer observer=players.get((first+index)%players.size());if(!observer.isAlive()||observer.isSpectator()||!level.hasChunkAt(observer.blockPosition())||nextAttempts.containsKey(observer.getUUID())||!habitat(level,observer.blockPosition())){continue;}
            boolean underground=cave(level,observer.blockPosition());if(underground&&level.getRandom().nextInt(8)!=0){continue;}
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,observer.getBlockX(),observer.getBlockZ());if(!underground&&(observer.getY()<surface-8||observer.getY()>surface+64)){continue;}
            AABB area=new AABB(observer.getX()-160,level.getMinY(),observer.getZ()-160,observer.getX()+160,level.getMaxY()+1,observer.getZ()+160);int wild=level.getEntitiesOfClass(AncientPhantom.class,area,phantom->phantom.isAlive()&&!phantom.isTame()).size();if(wild>=2){continue;}
            int group=Math.min(2-wild,1+level.getRandom().nextInt(2));SpawnGroupData groupData=null;boolean spawned=false;
            for(int member=0;member<group;member++){BlockPos position=findPosition(level,observer,underground);if(position==null){break;}var phantom=PhantomismContent.ANCIENT_PHANTOM.get().create(level,EntitySpawnReason.NATURAL);if(phantom==null){break;}phantom.setPos(position.getX()+.5D,position.getY(),position.getZ()+.5D);groupData=phantom.finalizeSpawn(level,level.getCurrentDifficultyAt(position),EntitySpawnReason.NATURAL,groupData);spawned|=level.addFreshEntity(phantom);}
            if(spawned){nextAttempts.put(observer.getUUID(),now+4800);}
        }
    }
    private static BlockPos findPosition(ServerLevel level,ServerPlayer observer,boolean underground){for(int attempt=0;attempt<24;attempt++){double angle=level.getRandom().nextDouble()*Math.PI*2,distance=28+level.getRandom().nextDouble()*28;int across=(int)Math.floor(observer.getX()+Math.cos(angle)*distance),depth=(int)Math.floor(observer.getZ()+Math.sin(angle)*distance);if(!level.hasChunkAt(new BlockPos(across,observer.getBlockY(),depth))){continue;}int height=underground?observer.getBlockY()+level.getRandom().nextInt(17)-8:level.getHeight(Heightmap.Types.MOTION_BLOCKING,across,depth)+2;BlockPos position=new BlockPos(across,height,depth);if(Math.abs(height-observer.getY())<=48&&canPlace(level,position)){return position;}}return null;}
    public static boolean canPlace(ServerLevel level,BlockPos position){
        var type=PhantomismContent.ANCIENT_PHANTOM.get();AABB box=type.getSpawnAABB(position.getX()+.5D,position.getY(),position.getZ()+.5D);
        if(box.minY<level.getMinY()||box.maxY>level.getMaxY()+1||!level.getWorldBorder().isWithinBounds(box)||!level.hasChunksAt(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))||!AncientPhantom.checkSpawnRules(type,level,EntitySpawnReason.NATURAL,position,level.getRandom())||!level.canSeeSky(position)&&!cave(level,position)&&!nearRuin(level,position)||level.containsAnyLiquid(box)||!level.noCollision(box)){return false;}
        for(ServerPlayer player:level.players()){if(player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(position.getX()+.5D,position.getY(),position.getZ()+.5D)<24*24){return false;}}return true;
    }
}
