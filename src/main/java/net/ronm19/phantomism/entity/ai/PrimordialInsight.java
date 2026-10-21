package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PrimordialPhantom;
import net.ronm19.phantomism.vfx.PrimordialVfx;

public final class PrimordialInsight {
    public static final TagKey<Block> ORES=TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"primordial_insight_ores"));
    private final Map<BlockPos,Long> seen=new HashMap<>();
    private int cursor;
    public void clear(){seen.clear();cursor=0;}
    public void tick(PrimordialPhantom source,ServerLevel level){if(!source.enabled()||!(source.getOwner() instanceof ServerPlayer owner)||owner.level()!=level||owner.connection==null||source.distanceToSqr(owner)>64*64){return;}long now=level.getGameTime();seen.values().removeIf(until->until<=now);Vec3 look=owner.getViewVector(1).normalize(),side=look.cross(new Vec3(0,1,0)).normalize(),up=side.cross(look).normalize();int emitted=0;
        for(int check=0;check<96;check++){int sample=cursor;cursor=(cursor+1)%576;int distance=1+sample/9,offset=sample%9;Vec3 point=owner.getEyePosition().add(look.scale(distance)).add(side.scale(offset%3-1)).add(up.scale(offset/3-1));BlockPos block=BlockPos.containing(point);if(!level.hasChunkAt(block)||source.distanceToSqr(Vec3.atCenterOf(block))>64*64||seen.containsKey(block)||!level.getBlockState(block).is(ORES)){continue;}if(seen.size()<64){seen.put(block,now+200);}PrimordialVfx.insight(source,owner,Vec3.atCenterOf(block),1);if(++emitted>=3){break;}}
        if(source.tickCount%40==0){for(var target:source.enemies(level,source.position(),64,3)){PrimordialVfx.insight(source,owner,target.getEyePosition(),0);}if(source.recipient(owner)&&owner.getHealth()<owner.getMaxHealth()*.5F){PrimordialVfx.insight(source,owner,owner.getEyePosition(),3);}}
        if(source.tickCount%100==0){int centerX=Math.floorDiv(source.getBlockX(),16),centerZ=Math.floorDiv(source.getBlockZ(),16);for(int across=-4;across<=4;across++){for(int depth=-4;depth<=4;depth++){var chunk=level.getChunkSource().getChunkNow(centerX+across,centerZ+depth);if(chunk==null){continue;}for(var start:chunk.getAllStarts().values()){if(!start.isValid()){continue;}var box=start.getBoundingBox();Vec3 point=new Vec3(Math.clamp(source.getX(),box.minX(),box.maxX()),Math.clamp(source.getY(),box.minY(),box.maxY()),Math.clamp(source.getZ(),box.minZ(),box.maxZ()));BlockPos block=BlockPos.containing(point);if(source.distanceToSqr(point)>64*64||!level.hasChunkAt(block)||seen.containsKey(block)){continue;}if(seen.size()<64){seen.put(block,now+200);}PrimordialVfx.insight(source,owner,point,2);return;}}}}
    }
}
