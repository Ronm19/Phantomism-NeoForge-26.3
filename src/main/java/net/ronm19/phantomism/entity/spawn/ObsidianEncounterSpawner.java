package net.ronm19.phantomism.entity.spawn;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ObsidianPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class ObsidianEncounterSpawner implements CustomSpawner {
    public static final int MAX_NEARBY_WILD=1;
    private final Map<UUID,Long> nextAttempts=new HashMap<>();
    private int delay=40,playerCursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event){if(event.getLevel().dimension()==Level.NETHER&&event.getCustomSpawners().stream().noneMatch(ObsidianEncounterSpawner.class::isInstance)){event.addCustomSpawner(new ObsidianEncounterSpawner());}}
    @Override public void tick(ServerLevel level,boolean spawnEnemies){
        if(level.dimension()!=Level.NETHER||!level.getGameRules().get(GameRules.SPAWN_MOBS)||level.players().isEmpty()||--delay>0){return;}delay=200;long now=level.getGameTime();nextAttempts.entrySet().removeIf(entry->now>=entry.getValue()||level.getPlayerByUUID(entry.getKey())==null);var players=level.players();int first=Math.floorMod(playerCursor,players.size()),checks=Math.min(4,players.size());playerCursor=(first+checks)%players.size();
        for(int index=0;index<checks;index++){
            ServerPlayer observer=players.get((first+index)%players.size());if(!observer.isAlive()||observer.isSpectator()||nextAttempts.containsKey(observer.getUUID())||!habitat(level,observer.blockPosition())||observer.getY()>=124){continue;}
            if(!level.getEntitiesOfClass(ObsidianPhantom.class,observer.getBoundingBox().inflate(160),source->source.isAlive()&&!source.isTame()).isEmpty()){continue;}
            BlockPos position=findPosition(level,observer);if(position==null){continue;}var source=PhantomismContent.OBSIDIAN_PHANTOM.get().create(level,EntitySpawnReason.NATURAL);if(source==null){continue;}source.setPos(Vec3.atBottomCenterOf(position));source.finalizeSpawn(level,level.getCurrentDifficultyAt(position),EntitySpawnReason.NATURAL,null);if(level.addFreshEntity(source)){nextAttempts.put(observer.getUUID(),now+2400);}
        }
    }
    public static boolean habitat(ServerLevel level,BlockPos position){return level.dimension()==Level.NETHER&&level.hasChunkAt(position)&&level.getBiome(position).is(ModTags.Biomes.OBSIDIAN_PHANTOM_SPAWNS);}
    private static BlockPos findPosition(ServerLevel level,ServerPlayer observer){
        for(int attempt=0;attempt<24;attempt++){double angle=level.getRandom().nextDouble()*Math.PI*2,distance=28+level.getRandom().nextDouble()*24;int across=(int)Math.floor(observer.getX()+Math.cos(angle)*distance),depth=(int)Math.floor(observer.getZ()+Math.sin(angle)*distance);if(!level.hasChunkAt(new BlockPos(across,observer.getBlockY(),depth))){continue;}
            for(int offset:new int[]{0,4,-4,8,-8}){BlockPos position=new BlockPos(across,observer.getBlockY()+offset,depth);if(!canPlace(level,position)){continue;}Vec3 end=Vec3.atCenterOf(position);AABB sight=new AABB(observer.getEyePosition(),end);if(level.hasChunksAt(BlockPos.containing(sight.minX,sight.minY,sight.minZ),BlockPos.containing(sight.maxX,sight.maxY,sight.maxZ))&&level.clip(new ClipContext(observer.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer)).getType()==HitResult.Type.MISS){return position;}}
        }return null;
    }
    public static boolean canPlace(ServerLevel level,BlockPos position){
        var type=PhantomismContent.OBSIDIAN_PHANTOM.get();AABB box=type.getSpawnAABB(position.getX()+.5D,position.getY(),position.getZ()+.5D).inflate(.1D);
        if(!habitat(level,position)||!ObsidianPhantom.checkSpawnRules(type,level,EntitySpawnReason.NATURAL,position,level.getRandom())||!level.getWorldBorder().isWithinBounds(box)||!level.hasChunksAt(position.offset(-2,-6,-2),position.offset(2,2,2))||!level.noCollision(box)){return false;}
        boolean support=false;for(int distance=1;distance<=6;distance++){BlockPos below=position.below(distance);if(!level.getFluidState(below).isEmpty()){return false;}if(!level.getBlockState(below).getCollisionShape(level,below).isEmpty()){support=true;break;}}if(!support){return false;}
        for(ServerPlayer player:level.players()){if(player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(position.getX()+.5D,position.getY(),position.getZ()+.5D)<24*24){return false;}}return true;
    }
}
