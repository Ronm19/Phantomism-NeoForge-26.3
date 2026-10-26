package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.GaleVfx;

public final class GaleEffects {
    private GaleEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.GALE||form==VfxRegistry.Form.SKY_DOMINION;}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());Vec3 center=effect.interpolated(partial);double age=effect.age(partial),duration=effect.packet.duration(),progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();float fade=(float)Math.max(0,Math.min(1,Math.min(age/4D,(duration-age)/10D)));int cyan=effect.packet.primary(),white=effect.packet.secondary(),segments=effect.quality==0?24:40;
        if(kind.equals(GaleVfx.DOMINION)){
            double opening=Math.min(1,age/40D),closing=Math.min(1,(duration-age)/30D),extent=radius*opening*closing;mesh.face(center.add(0,.12D,0),Math.max(.1D,extent),age*.005D,true,cyan,fade*.35F,definition.face());
            for(int tier=0;tier<5;tier++){double height=tier*2.2D;mesh.ring(center.add(0,height,0),Math.max(.1D,extent*(.35D+tier*.13D)),.065D,true,age*.025D*(tier%2==0?1:-1),tier%2==0?cyan:white,fade*(.7F-tier*.08F),segments,definition.ring());}
            for(int index=0;index<4;index++){double angle=index*Math.PI*.5D+age*.025D;Vec3 start=center.add(Math.cos(angle)*extent*.7D,.3D,Math.sin(angle)*extent*.7D),end=center.add(Math.cos(angle+1)*extent*.45D,8D*opening,Math.sin(angle+1)*extent*.45D);mesh.beam(start,end,.1D,white,fade*.5F,definition.trail());}
        }else if(kind.equals(GaleVfx.UPDRAFT)||kind.equals(GaleVfx.VORTEX)||kind.equals(GaleVfx.RESCUE)){
            int rings=effect.quality==0?4:6;for(int tier=0;tier<rings;tier++){double rise=(tier/(double)rings+progress)%1,height=rise*(kind.equals(GaleVfx.RESCUE)?4:8);double reach=kind.equals(GaleVfx.RESCUE)?1+rise:radius*(.12D+.5D*rise);mesh.ring(center.add(0,height,0),Math.max(.1D,reach),.06D,true,age*.06D+tier, tier%2==0?cyan:white,fade*(float)(1-rise*.7D),segments,definition.ring());}
        }else if(kind.equals(GaleVfx.BOLT)||kind.equals(GaleVfx.SLIPSTREAM)||kind.equals(GaleVfx.RUSH)){
            Vec3 direction=effect.packet.direction().lengthSqr()>.001D?effect.packet.direction().normalize():new Vec3(0,0,1);boolean bolt=kind.equals(GaleVfx.BOLT);double length=bolt?1.6D:4D;for(int index=0;index<4;index++){double angle=index*Math.PI*.5D+age*.2D;Vec3 offset=new Vec3(Math.cos(angle)*.25D,Math.sin(angle)*.25D,0);mesh.beam(center.add(offset).subtract(direction.scale(length)),center.add(offset),.05D,index%2==0?cyan:white,fade*.85F,definition.trail());}mesh.ring(center,bolt?.35D:1,.06D,false,age*.1D,white,fade,segments,definition.ring());
        }else if(kind.equals(GaleVfx.BLADE)){
            Vec3 direction=effect.packet.direction().lengthSqr()>.001D?effect.packet.direction().normalize():new Vec3(0,0,1),side=new Vec3(-direction.z,0,direction.x).normalize();if(side.lengthSqr()<.001D){side=new Vec3(1,0,0);}Vec3 previous=null;int count=effect.quality==0?12:24;for(int index=0;index<=count;index++){double angle=-Math.PI*.7D+index*Math.PI*1.4D/count;Vec3 point=center.add(direction.scale(progress*radius+Math.cos(angle)*1.4D)).add(side.scale(Math.sin(angle)*2));if(previous!=null){mesh.beam(previous,point,.09D,cyan,fade,definition.trail());}previous=point;}
        }else{
            boolean finale=kind.equals(GaleVfx.JUDGMENT);double reach=Math.max(.1D,radius*(.1D+.9D*progress));for(int tier=0;tier<(finale?3:2);tier++){mesh.ring(center.add(0,.2D+tier*.6D,0),reach*(1-tier*.15D),finale?.13D:.07D,true,age*.03D*(tier%2==0?1:-1),tier%2==0?cyan:white,fade*.85F,segments,definition.ring());}if(finale||kind.equals(GaleVfx.TEMPEST)){mesh.face(center.add(0,.15D,0),Math.max(.1D,radius*(1-progress)),age*.01D,true,white,fade*.3F,definition.face());}
        }
        return mesh.finish(List.of(),1,definition.silhouette(),cyan);
    }
}
