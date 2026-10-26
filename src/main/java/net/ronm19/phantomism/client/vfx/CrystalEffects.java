package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.CrystalVfx;

public final class CrystalEffects {
    private CrystalEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.CRYSTAL||form==VfxRegistry.Form.PRISMATIC;}
    private static void crystal(VfxMesh.Builder mesh,Vec3 center,double height,double width,double rotation,int color,float opacity,Identifier texture){Vec3 top=center.add(0,height*.7D,0),bottom=center.add(0,-height*.3D,0);for(int side=0;side<4;side++){double first=rotation+side*Math.PI*.5D,second=first+Math.PI*.5D;Vec3 left=center.add(Math.cos(first)*width,0,Math.sin(first)*width),right=center.add(Math.cos(second)*width,0,Math.sin(second)*width);mesh.quad(texture,left,top,right,bottom,color,opacity);}}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());
        Vec3 center=effect.interpolated(partial);double age=effect.age(partial),duration=effect.packet.duration(),progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();
        float fade=(float)Math.max(0,Math.min(1,Math.min(age/4D,(duration-age)/10D)));int violet=effect.packet.primary(),cyan=effect.packet.secondary(),segments=effect.quality==0?24:40;
        if(kind.equals(CrystalVfx.DOMINION)){
            double opening=Math.min(1,age/40D),closing=Math.min(1,(duration-age)/35D),extent=radius*opening*closing;
            mesh.face(center.add(0,.12D,0),Math.max(.1D,extent),age*.002D,true,violet,fade*.4F,definition.face());
            for(int ring=0;ring<3;ring++){mesh.ring(center.add(0,.2D+ring*.18D,0),extent*(1-ring*.17D),.065D,true,age*.007D*(ring%2==0?1:-1),ring%2==0?violet:cyan,fade*.8F,segments,definition.ring());}
            int count=effect.quality==0?8:12;
            for(int index=0;index<count;index++){double angle=index*Math.PI*2/count;Vec3 point=center.add(Math.cos(angle)*extent*.82D,1.5D*opening,Math.sin(angle)*extent*.82D);crystal(mesh,point,(3+index%3)*opening,.35D,age*.007D+angle,index%2==0?violet:cyan,fade*.8F,definition.hand());}
            for(int tier=0;tier<2;tier++){mesh.ring(center.add(0,3+tier*3,0),Math.max(.1D,extent*(.8D-tier*.2D)),.05D,true,-age*.01D,cyan,fade*.35F,segments,definition.ring());}
        }else if(kind.equals(CrystalVfx.BARRIER)||kind.equals(CrystalVfx.SHELL)){
            double reach=kind.equals(CrystalVfx.SHELL)?2.2D:radius;
            for(int tier=0;tier<4;tier++){double height=tier*.24D;mesh.ring(center.add(0,height*reach,0),reach*Math.sqrt(Math.max(.05D,1-height*height)),.055D,true,age*.006D, tier%2==0?violet:cyan,fade*.55F,segments,definition.ring());}
            int count=effect.quality==0?6:10;for(int index=0;index<count;index++){double angle=age*.013D+index*Math.PI*2/count;crystal(mesh,center.add(Math.cos(angle)*reach*.9D,reach*.35D,Math.sin(angle)*reach*.9D),reach*.4D,.16D,angle,cyan,fade*.55F,definition.hand());}
        }else if(kind.equals(CrystalVfx.SHARD)){
            crystal(mesh,center,.8D,.22D,age*.15D,violet,fade,definition.hand());Vec3 direction=effect.packet.direction().normalize();mesh.beam(center.subtract(direction.scale(1.8D)),center,.09D,cyan,fade*.7F,definition.trail());
        }else if(kind.equals(CrystalVfx.REFLECT)){
            mesh.ring(center,.4D+progress, .055D,false,age*.04D,cyan,fade,segments,definition.ring());Vec3 direction=effect.packet.direction();if(direction.lengthSqr()>.001D){mesh.beam(center,center.add(direction.normalize().scale(Math.min(6,direction.length())*progress)),.08D,violet,fade*.7F,definition.trail());}
        }else if(kind.equals(CrystalVfx.IMPACT_CHARGE)){
            int charges=Math.clamp((int)effect.packet.direction().x,1,3);for(int index=0;index<charges;index++){double angle=index*Math.PI*2/3+age*.06D;crystal(mesh,center.add(Math.cos(angle)*1.4D,1.1D,Math.sin(angle)*1.4D),.7D,.15D,angle,violet,fade,definition.hand());}
        }else if(kind.equals(CrystalVfx.RECOVER)){
            for(int index=0;index<6;index++){double angle=index*Math.PI/3+age*.03D;crystal(mesh,center.add(Math.cos(angle),progress*2,Math.sin(angle)),.35D,.1D,angle,cyan,fade*.8F,definition.hand());}
        }else{
            boolean finale=kind.equals(CrystalVfx.SHATTER),surge=kind.equals(CrystalVfx.REFRACTION);double reach=radius*(.15D+.85D*progress);
            mesh.ring(center.add(0,.2D,0),Math.max(.1D,reach),finale?.16D:.08D,true,age*.01D,violet,fade,segments,definition.ring());
            if(finale||surge){mesh.face(center.add(0,.15D,0),Math.max(.1D,radius*(1-progress)),age*.01D,true,cyan,fade*.35F,definition.face());}
            int count=effect.quality==0?6:12;for(int index=0;index<count;index++){double angle=index*Math.PI*2/count;Vec3 point=center.add(Math.cos(angle)*reach*.7D,finale?Math.sin(progress*Math.PI)*(2+index%3):.5D+Math.sin(age*.1D+index)*.3D,Math.sin(angle)*reach*.7D);crystal(mesh,point,finale?2D:surge?1.3D:.8D,.18D,angle+age*.04D,index%2==0?violet:cyan,fade*.85F,definition.hand());}
        }
        return mesh.finish(List.of(),1,definition.silhouette(),violet);
    }
}
