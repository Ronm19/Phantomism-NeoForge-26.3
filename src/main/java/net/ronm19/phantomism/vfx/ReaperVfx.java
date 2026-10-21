package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ReaperPhantom;

public final class ReaperVfx {
    public static final Identifier SENSE=id("death_sense"),HARVEST=id("soul_harvest"),SOULS=id("reaper_souls"),MARK=id("reaper_mark"),CUT=id("soul_cut"),SEVER=id("mortal_sever"),PASSAGE=id("reaper_passage"),ARRIVAL=id("reaper_arrival"),SHARD=id("soul_shard"),BARGAIN=id("deaths_bargain"),BELL=id("last_bell"),JUDGMENT=id("reaper_judgment"),CONCLUSION=id("reaper_conclusion"),IMPACT=id("reaper_impact"),CLEAR_MARK=id("reaper_clear_mark");
    private ReaperVfx(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,name);}
    public static void emit(ReaperPhantom source,Identifier effect,Entity attached,Vec3 position,Vec3 direction,int duration,float radius){if(!(source.level() instanceof ServerLevel level)||!Double.isFinite(position.lengthSqr())||Math.abs(position.x)>30000000||Math.abs(position.y)>30000000||Math.abs(position.z)>30000000||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()>128*128){return;}if(!effect.equals(CinematicVfx.CANCEL)){source.visualStarted();}CinematicVfx.emit(level,new VfxPacket(level.dimension().toString(),effect,source.getUUID(),source.session(),attached==null?-1:attached.getId(),attached==null?new UUID(0,0):attached.getUUID(),position,direction,duration,radius,0xAE67EB,0x77E8F5,level.getRandom().nextLong()));}
    public static void cancel(ReaperPhantom source){emit(source,CinematicVfx.CANCEL,null,source.position(),Vec3.ZERO,1,1);}
}
