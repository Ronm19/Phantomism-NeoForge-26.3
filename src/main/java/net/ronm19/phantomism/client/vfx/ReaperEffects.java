package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.vfx.ReaperVfx;

public final class ReaperEffects {
    private static final Identifier SHADOW=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/vfx/reaper_shadow.png");
    private ReaperEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.REAPER||form==VfxRegistry.Form.REAPER_SHARD||form==VfxRegistry.Form.LAST_BELL;}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();float age=effect.age(partial),duration=effect.packet.duration(),fade=Math.clamp(Math.min(age/5,(duration-age)/18),0,1);double progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();Vec3 center=effect.interpolated(partial);int violet=effect.packet.primary(),cyan=effect.packet.secondary(),segments=16+effect.quality*8;
        var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());
        if(definition.form()==VfxRegistry.Form.LAST_BELL){
            double opening=Math.clamp(age/45D,0,1),closure=Math.clamp((duration-age)/30D,0,1);Vec3 base=center.add(0,8+opening*3-(1-closure)*3,0);
            mesh.face(center.add(0,23,0),radius*opening,0,true,0x110A20,fade*.35F,SHADOW);
            mesh.face(center.add(0,.14D,0),radius*.82D*opening,-age*.002D,true,violet,fade*.22F,definition.face());
            mesh.ring(center.add(0,.25D,0),radius*opening,.07D,true,age*.001D,cyan,fade*.5F,segments,definition.trail());
            bell(mesh,effect,base,opening*closure,age,fade*.72F,12+effect.quality*4);
            mesh.face(base.add(0,-.3D,0),6*opening*closure,age*.006D,true,cyan,fade*.7F,definition.face());
            for(int rune=0;rune<6+effect.quality*2;rune++){double angle=rune*Math.PI*2/(6+effect.quality*2)+age*.004D,reach=8+Math.sin(age*.025D+rune)*.6D;Vec3 point=base.add(Math.cos(angle)*reach,2+Math.sin(angle)*.3D,Math.sin(angle)*reach);mesh.face(point,.65D*opening*closure,-angle,false,rune%2==0?cyan:violet,fade*.65F,definition.hand());}
            for(int wave:new int[]{60,110,160}){double elapsed=age-wave;if(elapsed>=0&&elapsed<42){float opacity=fade*(float)(1-elapsed/42);double reach=Math.max(.1D,radius*elapsed/42);mesh.ring(center.add(0,.35D,0),reach,.17D,true,0,violet,opacity*.9F,segments,definition.trail());mesh.ring(base.add(0,-elapsed*.12D,0),reach*.8D,.075D,true,-elapsed*.01D,cyan,opacity*.75F,segments,definition.trail());
                    for(int arc=0;arc<4+effect.quality;arc++){double angle=arc*Math.PI*2/(4+effect.quality);var points=new ArrayList<Vec3>();for(int step=0;step<=10;step++){double fraction=step/10D,curve=angle+fraction*.8D;points.add(center.add(Math.cos(curve)*reach*fraction,Math.sin(fraction*Math.PI)*3+.5D,Math.sin(curve)*reach*fraction));}mesh.ribbon(points,.07D,arc%2==0?cyan:violet,opacity*.65F,definition.trail());}
                }}
            if(age>=195&&age<225){float judgment=fade*(float)Math.sin((age-195)/30*Math.PI);mesh.line(base,center.add(0,.5D,0),.12D,cyan,judgment*.7F,definition.trail());mesh.face(center.add(0,.3D,0),radius*.6D,age*.002D,true,cyan,judgment*.4F,definition.face());}
            if(age>=220){mesh.ring(center.add(0,.5D,0),Math.max(.1D,radius*closure),.1D,true,age*.003D,cyan,fade*.65F,segments,definition.trail());}
        }else if(definition.form()==VfxRegistry.Form.REAPER_SHARD){
            Vec3 heading=effect.packet.direction().normalize();mesh.ribbon(effect.history(partial),.13D,violet,fade*.8F,definition.trail());mesh.ribbon(effect.history(partial),.035D,cyan,fade,definition.trail());mesh.face(center,.35D,age*.08D,false,cyan,fade,definition.hand());mesh.line(center.subtract(heading.scale(.7D)),center.add(heading.scale(.7D)),.07D,violet,fade,definition.trail());
        }else if(kind.equals(ReaperVfx.SOULS)){
            int count=Math.clamp((int)effect.packet.direction().x,0,3);for(int soul=0;soul<count;soul++){double angle=age*.035D+soul*Math.PI*2/count;Vec3 point=center.add(Math.cos(angle)*1.25D,1.5D+Math.sin(angle*2)*.25D,Math.sin(angle)*1.25D);mesh.face(point,.24D,-angle,false,cyan,fade*.85F,definition.hand());mesh.line(point.add(0,.4D,0),point,.025D,violet,fade*.6F,definition.trail());}
        }else if(kind.equals(ReaperVfx.HARVEST)){
            Vec3 direction=effect.packet.direction();var points=new ArrayList<Vec3>();for(int step=0;step<12;step++){double fraction=Math.max(0,progress-step*.014D);points.add(center.add(direction.scale(fraction)).add(0,Math.sin(fraction*Math.PI)*2,0));}Vec3 point=points.getFirst();mesh.ribbon(points,.065D,violet,fade*.75F,definition.trail());mesh.face(point,.3D,age*.03D,false,cyan,fade,definition.hand());
        }else if(kind.equals(ReaperVfx.MARK)||kind.equals(ReaperVfx.SENSE)){
            Vec3 point=center.add(0,2.8D,0);mesh.face(point,kind.equals(ReaperVfx.MARK)?.75D:.4D,0,false,kind.equals(ReaperVfx.MARK)?violet:cyan,fade*.85F,definition.face());
        }else if(kind.equals(ReaperVfx.CUT)||kind.equals(ReaperVfx.SEVER)){
            double facing=Math.atan2(effect.packet.direction().z,effect.packet.direction().x),reach=radius*(.45D+progress*.55D);int count=kind.equals(ReaperVfx.SEVER)?2:1;
            for(int slash=0;slash<count;slash++){var points=new ArrayList<Vec3>();for(int step=0;step<=18+effect.quality*4;step++){double fraction=step/(double)(18+effect.quality*4),angle=facing-Math.PI*.7D+fraction*Math.PI*1.4D;points.add(center.add(Math.cos(angle)*reach,.6D+Math.sin(fraction*Math.PI)*.4D+slash*.5D,Math.sin(angle)*reach));}mesh.ribbon(points,kind.equals(ReaperVfx.SEVER)?.22D:.14D,violet,fade*.9F,definition.trail());mesh.ribbon(points,.035D,cyan,fade,definition.trail());}
        }else if(kind.equals(ReaperVfx.PASSAGE)){
            Vec3 direction=effect.packet.direction();var points=new ArrayList<Vec3>();for(int step=0;step<=12;step++){double fraction=step/12D;points.add(center.add(direction.scale(fraction)).add(0,.6D+Math.sin(fraction*Math.PI)*.5D,0));}mesh.ribbon(points,.16D*(1-progress),violet,fade*.65F,definition.trail());mesh.face(center,.8D*(1-progress),age*.02D,false,cyan,fade*.6F,definition.hand());
        }else if(kind.equals(ReaperVfx.BARGAIN)){
            for(int soul=0;soul<3;soul++){double angle=age*.05D+soul*Math.PI*2/3;Vec3 point=center.add(Math.cos(angle)*1.1D,.5D+progress*1.6D,Math.sin(angle)*1.1D);mesh.face(point,.45D,-angle,false,cyan,fade*.8F,definition.hand());}mesh.ring(center.add(0,.3D,0),1.4D,.065D,true,age*.012D,violet,fade*.75F,segments,definition.trail());
        }else if(kind.equals(ReaperVfx.JUDGMENT)){
            mesh.line(center.add(0,6*(1-progress),0),center,.1D,cyan,fade*.8F,definition.trail());mesh.face(center.add(0,.15D,0),1.2D+progress,age*.01D,true,violet,fade*.7F,definition.face());mesh.ring(center.add(0,.25D,0),Math.max(.1D,progress*2),.08D,true,0,cyan,fade*.8F,segments,definition.trail());
        }else{
            mesh.ring(center.add(0,.25D,0),Math.max(.1D,radius*(kind.equals(ReaperVfx.CONCLUSION)?1-progress:progress)),.055D,true,age*.004D,violet,fade*.7F,segments,definition.trail());if(!kind.equals(ReaperVfx.CONCLUSION)){mesh.face(center.add(0,.6D,0),Math.max(.1D,1-progress),age*.03D,false,cyan,fade*.7F,definition.hand());}
        }
        return mesh.finish(List.of(),fade,definition.silhouette(),violet);
    }
    private static void bell(VfxMesh.Builder mesh,VfxEffect effect,Vec3 base,double scale,float age,float opacity,int segments){
        if(scale<=.001D){return;}double[][] profile={{0,5.5D},{.5D,5.8D},{1,4.7D},{4.5D,3.4D},{6,2.4D},{7,1.2D},{7.8D,.7D}};int color=effect.packet.primary();
        for(int band=0;band<profile.length-1;band++){for(int panel=0;panel<segments;panel++){double first=panel*Math.PI*2/segments,second=(panel+1)*Math.PI*2/segments;Vec3 lowerFirst=base.add(Math.cos(first)*profile[band][1]*scale,profile[band][0]*scale,Math.sin(first)*profile[band][1]*scale),lowerSecond=base.add(Math.cos(second)*profile[band][1]*scale,profile[band][0]*scale,Math.sin(second)*profile[band][1]*scale),upperFirst=base.add(Math.cos(first)*profile[band+1][1]*scale,profile[band+1][0]*scale,Math.sin(first)*profile[band+1][1]*scale),upperSecond=base.add(Math.cos(second)*profile[band+1][1]*scale,profile[band+1][0]*scale,Math.sin(second)*profile[band+1][1]*scale);mesh.quad(effect.definition.ring(),lowerFirst,lowerSecond,upperSecond,upperFirst,color,opacity);}}
        mesh.ring(base,5.5D*scale,.12D,true,0,effect.packet.secondary(),opacity,segments*2,effect.definition.trail());Vec3 clapper=base.add(Math.sin(age*.12D)*.3D,scale*.8D,0);mesh.line(base.add(0,scale*6,0),clapper,.09D*scale,effect.packet.secondary(),opacity,effect.definition.trail());mesh.face(clapper,.45D*scale,age*.02D,false,effect.packet.secondary(),opacity,effect.definition.hand());
    }
}
