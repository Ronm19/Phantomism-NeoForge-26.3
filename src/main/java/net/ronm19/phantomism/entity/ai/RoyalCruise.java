package net.ronm19.phantomism.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.PhantomKing;

public final class RoyalCruise {
    private RoyalCruise() {}
    public static boolean idle(PhantomKing source) { return source.isAlive() && !source.isBaby() && !source.isVehicle() && source.getTarget()==null && source.getOrder()!=AbstractPhantom.Order.STAY && source.getOrder()!=AbstractPhantom.Order.RETURN_HOME && source.getTask()==PhantomAssignments.Task.NONE; }
    public static boolean clearBelow(PhantomKing source, Vec3 destination) {
        AABB clearance=source.getBoundingBox().move(destination.subtract(source.position())).inflate(.2D,0,.2D).expandTowards(0,-3,0);
        return source.isLoadedFlightBox(clearance) && source.level().noBlockCollision(source,clearance) && !source.level().containsAnyLiquid(clearance);
    }
    public static void tick(PhantomKing source,ServerLevel level) {
        if(!idle(source)||source.isNoAi()){return;}
        double rise=0;AABB box=source.getBoundingBox().inflate(.2D,0,.2D);
        for(double across:new double[]{box.minX,box.maxX}){for(double depth:new double[]{box.minZ,box.maxZ}){for(int down=0;down<=3;down++){
            BlockPos block=BlockPos.containing(across,source.getY()-down,depth);if(!level.hasChunkAt(block)){return;}
            var shape=level.getBlockState(block).getCollisionShape(level,block);
            if(!shape.isEmpty()){rise=Math.max(rise,block.getY()+shape.max(Direction.Axis.Y)+3.2D-source.getY());break;}
        }}}
        if(rise<=.05D){return;}
        Vec3 offset=new Vec3(0,Math.min(4,rise),0);AABB swept=source.getBoundingBox().expandTowards(offset).inflate(.05D);
        if(!source.isLoadedFlightBox(swept)||!level.noBlockCollision(source,swept)||level.containsAnyLiquid(swept)){return;}
        source.getMoveControl().setWantedPosition(source.getX(),source.getY()+offset.y,source.getZ(),.9D);
    }
}
