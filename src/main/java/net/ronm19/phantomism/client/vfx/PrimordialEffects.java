package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.PrimordialVfx;

public final class PrimordialEffects {
    private PrimordialEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.PRIMORDIAL||form==VfxRegistry.Form.PRIMORDIAL_STAR||form==VfxRegistry.Form.PRIMAL_DOMAIN;}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();float age=effect.age(partial),duration=effect.packet.duration(),fade=Math.clamp(Math.min(age/5,(duration-age)/18),0,1);double progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();Vec3 center=effect.interpolated(partial);int violet=effect.packet.primary(),cyan=effect.packet.secondary(),segments=16+effect.quality*8;
        var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());
        if(definition.form()==VfxRegistry.Form.PRIMAL_DOMAIN){
            double opening=Math.clamp(age/45D,0,1),closing=Math.clamp((duration-age)/40D,0,1),extent=radius*opening*closing;
            mesh.face(center.add(0,.12D,0),Math.max(.1D,extent*.7D),age*.0015D,true,violet,fade*.23F,definition.face());
            mesh.ring(center.add(0,.25D,0),Math.max(.1D,extent),.09D,true,age*.002D,cyan,fade*.55F,segments,definition.trail());
            Vec3 fragment=center.add(0,20+Math.sin(age*.015D),0);mesh.face(fragment,5*opening*closing,-age*.005D,false,cyan,fade*.6F,definition.face());mesh.ring(fragment,6*opening*closing,.06D,false,age*.006D,violet,fade*.7F,segments,definition.trail());
            if(age>=40){for(int ring=0;ring<3;ring++){double reach=(7+ring*4)*opening*closing;mesh.ring(center.add(0,8+ring*4,0),reach,.055D,true,(ring%2==0?1:-1)*age*.004D,ring%2==0?violet:cyan,fade*.5F,segments,definition.trail());}}
            for(int pillar=0;pillar<4+effect.quality*2;pillar++){
                double angle=pillar*Math.PI*2/(4+effect.quality*2)+.2D,reach=radius*.65D,raising=Math.clamp((age-55-pillar*3)/45D,0,1)*closing;Vec3 base=center.add(Math.cos(angle)*reach,.3D,Math.sin(angle)*reach);
                mesh.face(base.add(0,raising*4,0),raising*2.1D,angle,false,violet,fade*.55F,definition.ring());mesh.line(base,base.add(0,raising*8,0),.07D,cyan,fade*.65F,definition.trail());
                if(age>=100&&age<210){double falling=(age*.03D+pillar*.17D)%1;Vec3 top=base.add(Math.sin(angle)*2,16-falling*15,Math.cos(angle)*2);mesh.line(top.add(0,1.5D,0),top,.055D,cyan,fade*.75F,definition.trail());mesh.face(top,.3D,age*.02D,false,cyan,fade*.8F,definition.face());}
            }
            for(int wave:new int[]{120,180,240}){double elapsed=age-wave;if(elapsed>=0&&elapsed<34){double reach=Math.max(.1D,radius*elapsed/34);float opacity=fade*(float)(1-elapsed/34);mesh.ring(center.add(0,.5D,0),reach,.22D,true,0,wave==240?cyan:violet,opacity*.9F,segments,definition.trail());}}
            if(age>=210){float manifest=fade*(float)Math.clamp((age-210)/20D,0,1);wings(mesh,effect,center.add(0,7,0),7*closing,age,manifest*.65F);mesh.line(center.add(0,18,0),center.add(0,3,0),.13D,cyan,manifest*.5F,definition.trail());}
        }else if(definition.form()==VfxRegistry.Form.PRIMORDIAL_STAR){
            mesh.ribbon(effect.history(partial),radius*.16D,violet,fade*.85F,definition.trail());mesh.ribbon(effect.history(partial),radius*.04D,cyan,fade,definition.trail());mesh.face(center,radius*.42D,age*.1D,false,cyan,fade,definition.face());mesh.face(center,radius*.27D,-age*.12D,false,0xFFF5EF,fade,definition.face());
        }else if(kind.equals(PrimordialVfx.WING)){
            wings(mesh,effect,center.add(0,1.4D,0),2.2D,age,fade*.8F);mesh.ring(center.add(0,.15D,0),2.4D,.045D,true,age*.003D,cyan,fade*.5F,segments,definition.trail());
        }else if(kind.equals(PrimordialVfx.INSIGHT)){
            int category=Math.clamp((int)effect.packet.direction().x,0,3);mesh.face(center.add(0,category==0||category==3?.8D:0,0),category==2?.8D:.4D,age*.003D,false,category==0?violet:cyan,fade*.85F,definition.face());
        }else if(kind.equals(PrimordialVfx.SHIFT)){
            var points=new ArrayList<Vec3>();for(int step=0;step<=16;step++){double fraction=step/16D;points.add(center.add(effect.packet.direction().scale(fraction)).add(0,Math.sin(fraction*Math.PI)*.8D+.6D,0));}mesh.ribbon(points,.18D*(1-progress),violet,fade*.75F,definition.trail());mesh.ribbon(points,.035D,cyan,fade,definition.trail());
            for(int star=0;star<5;star++){double fraction=star/4D;mesh.face(center.add(effect.packet.direction().scale(fraction)).add(0,.7D,0),.3D*(1-progress),age*.02D+star,false,cyan,fade*.8F,definition.face());}
        }else if(kind.equals(PrimordialVfx.STRIKE)){
            double facing=Math.atan2(effect.packet.direction().z,effect.packet.direction().x);var points=new ArrayList<Vec3>();for(int step=0;step<=20;step++){double fraction=step/20D,angle=facing-Math.PI*.7D+fraction*Math.PI*1.4D;points.add(center.add(Math.cos(angle)*radius*(.4D+progress*.6D),.7D+Math.sin(fraction*Math.PI)*.5D,Math.sin(angle)*radius*(.4D+progress*.6D)));}mesh.ribbon(points,.16D,violet,fade*.9F,definition.trail());mesh.ribbon(points,.035D,cyan,fade,definition.trail());
        }else if(kind.equals(PrimordialVfx.RESTORATION)){
            mesh.face(center.add(0,.12D,0),radius*.45D,age*.002D,true,cyan,fade*.18F,definition.face());
            for(int star=0;star<5+effect.quality;star++){double angle=star*Math.PI*2/(5+effect.quality)+age*.005D;Vec3 point=center.add(Math.cos(angle)*radius*.65D,1.5D+Math.sin(age*.03D+star)*.5D,Math.sin(angle)*radius*.65D);mesh.face(point,.45D,-age*.02D,false,cyan,fade*.75F,definition.face());mesh.line(point,center.add(0,2,0),.022D,violet,fade*.25F,definition.trail());}
        }else if(kind.equals(PrimordialVfx.JUDGMENT)){
            mesh.line(center.add(0,5*(1-progress),0),center,.12D,cyan,fade*.85F,definition.trail());mesh.face(center.add(0,.2D,0),1.6D,age*.015D,true,violet,fade*.6F,definition.face());
        }else if(kind.equals(PrimordialVfx.AURA)){
            for(int star=0;star<3;star++){double angle=star*Math.PI*2/3+age*.012D;mesh.face(center.add(Math.cos(angle)*1.5D,.8D,Math.sin(angle)*1.5D),.25D,angle,false,cyan,fade*.6F,definition.face());}
        }else{
            boolean ending=kind.equals(PrimordialVfx.CONCLUSION);double reach=Math.max(.1D,radius*(ending?1-progress:progress));mesh.ring(center.add(0,.25D,0),reach,kind.equals(PrimordialVfx.NOVA)?.15D:.06D,true,age*.004D,violet,fade*.7F,segments,definition.trail());
            if(!ending){mesh.face(center.add(0,.7D,0),Math.max(.1D,1-progress),age*.025D,false,cyan,fade*.8F,definition.face());for(int star=0;star<4;star++){double angle=star*Math.PI/2+age*.02D;mesh.face(center.add(Math.cos(angle)*Math.min(3,reach),.5D+progress,Math.sin(angle)*Math.min(3,reach)),.2D,angle,false,cyan,fade*.7F,definition.face());}}
        }
        return mesh.finish(List.of(),fade,definition.silhouette(),violet);
    }
    private static void wings(VfxMesh.Builder mesh,VfxEffect effect,Vec3 center,double size,float age,float opacity){
        for(int side:new int[]{-1,1}){Vec3 base=center.add(side*size*.5D,0,0),tip=center.add(side*size,Math.sin(age*.04D)*size*.12D,0);mesh.face(base,size*.8D,side*.4D,false,effect.packet.primary(),opacity,effect.definition.hand());mesh.line(center,tip,.055D,effect.packet.secondary(),opacity,effect.definition.trail());for(int star=1;star<=3;star++){mesh.face(center.lerp(tip,star/3D),size*.09D,age*.025D,false,effect.packet.secondary(),opacity,effect.definition.face());}}
    }
}
