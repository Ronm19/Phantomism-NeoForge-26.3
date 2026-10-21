package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AncientPhantom;

public final class AncientVfx {
    public static final Identifier MARK=id("ancient_mark"),STRIKE=id("ruin_strike"),STONE=id("ancient_stone"),SHIELD=id("ruin_shield"),DIVE=id("earthquake_dive"),QUAKE=id("ancient_quake"),SANCTUARY=id("ancient_sanctuary"),FORGOTTEN=id("forgotten_land"),JUDGMENT=id("ancient_judgment"),CONCLUSION=id("ancient_conclusion"),FRAGMENTS=id("ancient_fragments"),IMPACT=id("ancient_impact"),CLEAR_MARK=id("ancient_clear_mark");
    private AncientVfx(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,name);}
    public static void emit(AncientPhantom source,Identifier effect,Entity attached,Vec3 position,Vec3 direction,int duration,float radius){if(!(source.level() instanceof ServerLevel level)||!Double.isFinite(position.lengthSqr())||Math.abs(position.x)>30000000||Math.abs(position.y)>30000000||Math.abs(position.z)>30000000||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()>128*128){return;}CinematicVfx.emit(level,new VfxPacket(level.dimension().toString(),effect,source.getUUID(),source.session(),attached==null?-1:attached.getId(),attached==null?new UUID(0,0):attached.getUUID(),position,direction,duration,radius,0x60EADD,0xD7BE85,level.getRandom().nextLong()));}
    public static void cancel(AncientPhantom source){emit(source,CinematicVfx.CANCEL,null,source.position(),Vec3.ZERO,1,1);}
}
