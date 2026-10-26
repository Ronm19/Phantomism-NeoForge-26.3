package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.PlagueVfx;

public final class PlagueEffects {
    private PlagueEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.PLAGUE||form==VfxRegistry.Form.PANDEMIC;}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());
        Vec3 center=effect.interpolated(partial);double age=effect.age(partial),duration=effect.packet.duration(),progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();
        float fade=(float)Math.max(0,Math.min(1,Math.min(age/4D,(duration-age)/10D)));int green=effect.packet.primary(),lime=effect.packet.secondary(),segments=effect.quality==0?24:40;
        if(kind.equals(PlagueVfx.PANDEMIC)){
            double opening=Math.min(1,age/32D),closing=Math.min(1,(duration-age)/32D),extent=radius*opening*closing;
            mesh.face(center.add(0,.1D,0),Math.max(.1D,extent*.8D),age*.003D,true,green,fade*.22F,definition.face());
            mesh.face(center.add(0,7,0),Math.max(.1D,extent*.26D),-age*.004D,false,green,fade*.7F,definition.ring());
            for(int layer=0;layer<3;layer++){mesh.ring(center.add(0,.2D+layer*.16D,0),Math.max(.1D,extent*(.55D+layer*.18D)),.12D,true,age*.004D*(layer%2==0?1:-1),layer==1?lime:green,fade*(layer==1?.55F:.4F),segments,definition.trail());}
            for(int index=0;index<(effect.quality==0?8:14);index++){
                double angle=index*Math.PI*2/14+age*.009D,reach=extent*(.25D+index%4*.15D),height=.4D+Math.floorMod(effect.packet.seed()+index*17,31)/10D+Math.sin(age*.04D+index)*.4D;
                Vec3 point=center.add(Math.cos(angle)*reach,height,Math.sin(angle)*reach);mesh.face(point,.1D+index%3*.06D,angle+age*.02D,false,index%3==0?lime:green,fade*.6F,definition.hand());
                if(index<6){Vec3 end=center.add(Math.cos(angle)*extent*.7D,.25D,Math.sin(angle)*extent*.7D);mesh.ribbon(List.of(center.add(0,.3D,0),center.add(Math.cos(angle+.15D)*reach,.6D,Math.sin(angle+.15D)*reach),end),.06D,green,fade*.7F,definition.trail());}
            }
        }else if(kind.equals(PlagueVfx.SPIT)||kind.equals(PlagueVfx.DIVE)){
            mesh.ribbon(effect.history(partial),kind.equals(PlagueVfx.DIVE)?.6D:.24D,green,fade,definition.trail());mesh.ribbon(effect.history(partial),.055D,lime,fade*.8F,definition.trail());mesh.face(center,.4D,age*.1D,false,lime,fade,definition.hand());
        }else if(kind.equals(PlagueVfx.CHAIN)){
            Vec3 delta=effect.packet.direction();List<Vec3> points=new ArrayList<>();for(int step=0;step<=6;step++){double fraction=step/6D;points.add(center.add(delta.scale(fraction)).add(0,step==0||step==6?0:Math.sin(step*2.4D+age*.3D)*.16D,0));}mesh.ribbon(points,.07D,green,fade,definition.trail());mesh.ribbon(points,.025D,lime,fade,definition.trail());mesh.face(center.add(delta),.22D,age*.1D,false,lime,fade,definition.hand());
        }else if(kind.equals(PlagueVfx.CLOUD)){
            for(int layer=0;layer<3;layer++){mesh.ring(center.add(0,.15D+layer*.7D,0),radius*(.9D-layer*.12D),.11D,true,age*.007D+layer,green,fade*.28F,segments,definition.trail());}
            for(int index=0;index<8;index++){double angle=index*Math.PI/4+age*.006D;Vec3 point=center.add(Math.cos(angle)*radius*.58D,.8D+Math.sin(age*.03D+index)*.4D,Math.sin(angle)*radius*.58D);mesh.face(point,radius*.36D,age*.004D+index,false,green,fade*.13F,definition.ring());mesh.face(point,.12D,angle,false,lime,fade*.5F,definition.hand());}
        }else if(kind.equals(PlagueVfx.HUSK)){
            for(int layer=0;layer<3;layer++){mesh.face(center.add((layer-1)*.35D,.5D,0),radius*(.7D-layer*.1D),age*.015D+layer*.4D,false,layer==1?lime:green,fade*.45F,definition.face());}mesh.ribbon(effect.history(partial),.3D,green,fade*.5F,definition.trail());
        }else if(kind.equals(PlagueVfx.INFECT)||kind.equals(PlagueVfx.MUTATION)||kind.equals(PlagueVfx.HERD)){
            mesh.face(center.add(0,.4D,0),Math.max(.15D,radius*(.6D+progress*.4D)),age*.03D,false,green,fade*.65F,definition.face());mesh.ring(center.add(0,.1D,0),Math.max(.1D,radius*progress),.06D,true,0,lime,fade*.5F,segments,definition.trail());
        }else{
            boolean collapse=kind.equals(PlagueVfx.COLLAPSE);double reach=radius*(collapse?1-progress:progress);
            mesh.ring(center.add(0,.15D,0),Math.max(.1D,reach),collapse?.22D:.12D,true,age*.009D,green,fade*.75F,segments,definition.trail());
            if(collapse){mesh.ring(center.add(0,.3D,0),Math.max(.1D,radius*progress),.14D,true,0,lime,fade*.55F,segments,definition.trail());mesh.face(center.add(0,.2D,0),Math.max(.1D,reach*.7D),-age*.01D,true,green,fade*.2F,definition.face());}
            for(int index=0;index<(collapse?18:8);index++){double angle=index*Math.PI*2/(collapse?18:8);Vec3 point=center.add(Math.cos(angle)*reach,.5D+Math.sin(progress*Math.PI)*(index%3+1),Math.sin(angle)*reach);mesh.face(point,.12D+index%3*.07D,angle+progress*2,false,index%4==0?lime:green,fade*.75F,definition.hand());}
        }
        return mesh.finish(List.of(),fade,definition.silhouette(),green);
    }
}
