package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.VoidPhantom;

public final class VoidVfx {
    public static final Identifier FIELD=id("void_field"),BOLT=id("void_bolt"),IMPACT=id("void_impact"),GRASP=id("void_grasp"),ANCHOR=id("abyss_anchor"),RESCUE=id("abyss_rescue"),PULSE=id("anti_caster_pulse"),SEAL=id("void_seal"),STEP=id("void_step"),ARRIVAL=id("void_arrival"),SHIELD=id("dark_matter_shield"),HORIZON=id("null_horizon"),WAVE=id("void_horizon_wave"),MARK=id("void_horizon_mark"),COLLAPSE=id("void_collapse");
    private VoidVfx(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,name);}
    public static void emit(VoidPhantom source,Identifier effect,Entity attached,Vec3 position,Vec3 direction,int duration,float radius){if(!(source.level() instanceof ServerLevel level)||!Double.isFinite(position.lengthSqr())||Math.abs(position.x)>30000000||Math.abs(position.y)>30000000||Math.abs(position.z)>30000000||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()>128*128){return;}if(!effect.equals(CinematicVfx.CANCEL)){source.visualStarted();}CinematicVfx.emit(level,new VfxPacket(level.dimension().toString(),effect,source.getUUID(),source.session(),attached==null?-1:attached.getId(),attached==null?new UUID(0,0):attached.getUUID(),position,direction,duration,radius,0x963CFA,0xD6ADFF,source.getRandom().nextLong()));}
    public static void cancel(VoidPhantom source){emit(source,CinematicVfx.CANCEL,null,source.position(),Vec3.ZERO,1,1);}
}
