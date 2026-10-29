package net.ronm19.phantomism.vfx;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.CreatorPhantom;
public final class CreatorVfx {
    public static final Identifier PRESENCE=id("presence"),PULSE=id("pulse"),STEP=id("step"),AEGIS=id("aegis"),GENESIS=id("genesis"),BEAM=id("beam"),INSTINCT=id("instinct"),EVOLUTION=id("evolution"),WAVE=id("wave"),FINAL=id("final"),DEFEAT=id("defeat"),BLOCK=id("block");
    public static final List<Identifier> ALL=List.of(PRESENCE,PULSE,STEP,AEGIS,GENESIS,BEAM,INSTINCT,EVOLUTION,WAVE,FINAL,DEFEAT,BLOCK);
    private CreatorVfx(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("phantomism","creator_phantom/"+name);}
    public static void emit(CreatorPhantom source,Identifier kind,Entity attached,Vec3 point,Vec3 vector,int duration,float radius){if(source.level() instanceof ServerLevel level){CinematicVfx.emit(level,new VfxPacket(level.dimension().toString(),kind,source.getUUID(),source.session(),attached==null?-1:attached.getId(),attached==null?new UUID(0,0):attached.getUUID(),point,vector,duration,radius,0xFFFFFF,0x191919,source.getRandom().nextLong()));}}
    public static void cancel(CreatorPhantom source){emit(source,CinematicVfx.CANCEL,null,source.position(),Vec3.ZERO,1,1);}
}
