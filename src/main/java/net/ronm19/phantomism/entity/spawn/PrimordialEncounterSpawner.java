package net.ronm19.phantomism.entity.spawn;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PrimordialPhantom;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class PrimordialEncounterSpawner implements CustomSpawner {
    public static final int CHECK_INTERVAL_TICKS=200;
    public static final int SUCCESS_COOLDOWN_TICKS=12000;
    private final Map<UUID,Long> nextAttempts=new HashMap<>();
    private int delay=40,playerCursor;
    @SubscribeEvent public static void register(ModifyCustomSpawnersEvent event){
        if((event.getLevel().dimension()==Level.OVERWORLD||event.getLevel().dimension()==Level.END)&&event.getCustomSpawners().stream().noneMatch(PrimordialEncounterSpawner.class::isInstance)){event.addCustomSpawner(new PrimordialEncounterSpawner());}
    }
    @Override public void tick(ServerLevel level,boolean spawnEnemies){
        if(!level.getGameRules().get(GameRules.SPAWN_MOBS)||level.players().isEmpty()||level.dimension()!=Level.OVERWORLD&&level.dimension()!=Level.END||level.dimension()==Level.OVERWORLD&&!night(level)||--delay>0){return;}
        delay=CHECK_INTERVAL_TICKS;long now=level.getGameTime();nextAttempts.entrySet().removeIf(entry->now>=entry.getValue()||level.getPlayerByUUID(entry.getKey())==null);List<ServerPlayer> players=level.players();int first=Math.floorMod(playerCursor,players.size()),checks=Math.min(4,players.size());playerCursor=(first+checks)%players.size();
        for(int index=0;index<checks;index++){
            ServerPlayer player=players.get((first+index)%players.size());
            if(!player.isAlive()||player.isSpectator()||nextAttempts.containsKey(player.getUUID())||!level.hasChunkAt(player.blockPosition())){continue;}
            if(!level.getEntitiesOfClass(PrimordialPhantom.class,player.getBoundingBox().inflate(160),source->source.isAlive()&&!source.isTame()).isEmpty()){continue;}
            BlockPos position=findPosition(level,player);if(position==null){continue;}
            PrimordialPhantom source=PhantomismContent.PRIMORDIAL_PHANTOM.get().create(level,EntitySpawnReason.NATURAL);if(source==null){continue;}source.setPos(position.getX()+.5D,position.getY(),position.getZ()+.5D);source.finalizeSpawn(level,level.getCurrentDifficultyAt(position),EntitySpawnReason.NATURAL,null);if(level.addFreshEntity(source)){nextAttempts.put(player.getUUID(),now+SUCCESS_COOLDOWN_TICKS);}
        }
    }
    private static BlockPos findPosition(ServerLevel level,ServerPlayer player){
        boolean underground=cave(level,player.blockPosition());
        for(int attempt=0;attempt<32;attempt++){
            double angle=level.getRandom().nextDouble()*Math.PI*2,radius=32+level.getRandom().nextDouble()*16;
            int across=(int)Math.floor(player.getX()+Math.cos(angle)*radius),depth=(int)Math.floor(player.getZ()+Math.sin(angle)*radius);
            if(!level.hasChunkAt(new BlockPos(across,player.getBlockY(),depth))){continue;}
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,across,depth);
            for(int offset=0;offset<(underground?25:4);offset++){
                int height=underground?player.getBlockY()+(offset%2==0?1:-1)*((offset+1)/2):surface+2-offset;
                BlockPos position=new BlockPos(across,height,depth);
                if(Math.abs(height-player.getY())>32||!canPlace(level,position)){continue;}
                Vec3 center=Vec3.atBottomCenterOf(position).add(0,.5D,0);AABB sight=new AABB(player.getEyePosition(),center);
                if(level.hasChunksAt(BlockPos.containing(sight.minX,sight.minY,sight.minZ),BlockPos.containing(sight.maxX,sight.maxY,sight.maxZ))&&level.clip(new ClipContext(player.getEyePosition(),center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS){return position;}
            }
        }
        return null;
    }
    private static boolean cave(ServerLevel level,BlockPos position){return level.dimension()==Level.OVERWORLD&&level.hasChunkAt(position)&&level.getBiome(position).is(Biomes.DRIPSTONE_CAVES)&&!level.canSeeSky(position)&&position.getY()<level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,position.getX(),position.getZ())-16;}
    public static boolean night(ServerLevel level){long time=Math.floorMod(level.getOverworldClockTime(),24000L);return time>=13000&&time<23000;}
    public static boolean habitat(ServerLevel level,BlockPos position){
        if(!level.hasChunkAt(position)){return false;}var biome=level.getBiome(position);
        return level.dimension()==Level.END&&biome.is(Biomes.END_HIGHLANDS)||level.dimension()==Level.OVERWORLD&&night(level)&&(biome.is(BiomeTags.IS_MOUNTAIN)&&position.getY()>=100||cave(level,position));
    }
    public static boolean canPlace(ServerLevel level,BlockPos position){
        var type=PhantomismContent.PRIMORDIAL_PHANTOM.get();AABB box=type.getSpawnAABB(position.getX()+.5D,position.getY(),position.getZ()+.5D),clearance=box.inflate(.1D,0,.1D).expandTowards(0,.25D,0);
        if(!level.hasChunksAt(BlockPos.containing(clearance.minX,clearance.minY-6,clearance.minZ),BlockPos.containing(clearance.maxX,clearance.maxY,clearance.maxZ))||!level.getWorldBorder().isWithinBounds(clearance)||box.minY<level.getMinY()+6||clearance.maxY>level.getMaxY()-2||!PrimordialPhantom.checkSpawnRules(type,level,EntitySpawnReason.NATURAL,position,level.getRandom())||!level.noCollision(clearance)||level.containsAnyLiquid(clearance)||!supported(level,position)){return false;}
        for(BlockPos block:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY+1,box.maxZ))){if(GravityControl.dangerous(level,block)){return false;}}
        for(ServerPlayer player:level.players()){if(player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(position.getX()+.5D,position.getY(),position.getZ()+.5D)<24*24){return false;}}
        return true;
    }
    private static boolean supported(ServerLevel level,BlockPos position){for(int distance=1;distance<=6;distance++){BlockPos floor=position.below(distance);if(GravityControl.dangerous(level,floor)||!level.getFluidState(floor).isEmpty()){return false;}if(!level.getBlockState(floor).getCollisionShape(level,floor).isEmpty()){return true;}}return false;}
}
