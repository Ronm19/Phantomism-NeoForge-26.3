package net.ronm19.phantomism.vfx;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PrimordialPhantom;

public final class PrimordialVfx {
    public static final Identifier AURA=id("primordial_aura"),INSIGHT=id("cosmic_insight"),WING=id("creation_wing"),SHIFT=id("reality_shift"),ARRIVAL=id("primordial_arrival"),STRIKE=id("primordial_strike"),STAR=id("starfall"),SHATTER=id("starfall_shatter"),RESTORATION=id("world_restoration"),HEAL=id("primordial_heal"),NOVA=id("cosmic_nova"),DOMAIN=id("primal_domain"),JUDGMENT=id("primordial_judgment"),CONCLUSION=id("primordial_conclusion"),IMPACT=id("primordial_impact");
    private record InsightBudget(long time,int count){}
    private static final Map<ServerPlayer,InsightBudget> INSIGHTS=new WeakHashMap<>();
    private PrimordialVfx(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,name);}
    private static VfxPacket packet(PrimordialPhantom source,Identifier effect,Entity attached,Vec3 position,Vec3 direction,int duration,float radius){return new VfxPacket(source.level().dimension().toString(),effect,source.getUUID(),source.session(),attached==null?-1:attached.getId(),attached==null?new UUID(0,0):attached.getUUID(),position,direction,duration,radius,0xA68AFF,0x79EFFF,source.getRandom().nextLong());}
    private static boolean finite(Vec3 position,Vec3 direction){return Double.isFinite(position.lengthSqr())&&Math.abs(position.x)<=30000000&&Math.abs(position.y)<=30000000&&Math.abs(position.z)<=30000000&&Double.isFinite(direction.lengthSqr())&&direction.lengthSqr()<=128*128;}
    public static void emit(PrimordialPhantom source,Identifier effect,Entity attached,Vec3 position,Vec3 direction,int duration,float radius){if(!(source.level() instanceof ServerLevel level)||!finite(position,direction)){return;}if(!effect.equals(CinematicVfx.CANCEL)){source.visualStarted();}CinematicVfx.emit(level,packet(source,effect,attached,position,direction,duration,radius));}
    public static void insight(PrimordialPhantom source,ServerPlayer owner,Vec3 position,int kind){if(!source.enabled()||owner.level()!=source.level()||owner.connection==null||!source.isOwnedBy(owner)||owner.distanceToSqr(position)>64*64||!finite(position,Vec3.ZERO)){return;}long now=source.level().getGameTime();InsightBudget budget=INSIGHTS.get(owner);int count=budget!=null&&now>=budget.time()&&now-budget.time()<20?budget.count():0;if(count>=8){return;}INSIGHTS.put(owner,new InsightBudget(count==0?now:budget.time(),count+1));source.visualStarted();net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,packet(source,INSIGHT,null,position,new Vec3(Math.clamp(kind,0,3),0,0),80,1));}
    public static void cancel(PrimordialPhantom source){emit(source,CinematicVfx.CANCEL,null,source.position(),Vec3.ZERO,1,1);}
}
