package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.AncientVfx;

public final class AncientEffects {
    private AncientEffects(){}
    public static boolean supports(VfxRegistry.Form form){return form==VfxRegistry.Form.ANCIENT||form==VfxRegistry.Form.ANCIENT_STONE||form==VfxRegistry.Form.FORGOTTEN_LAND;}
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var definition=effect.definition;var kind=effect.packet.effect();float age=effect.age(partial),duration=effect.packet.duration(),fade=Math.clamp(Math.min(age/5,(duration-age)/20),0,1);double progress=Math.clamp(age/duration,0,1),radius=effect.packet.radius();Vec3 center=effect.interpolated(partial);int cyan=effect.packet.primary(),sand=effect.packet.secondary(),segments=16+effect.quality*8;
        var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,effect.packet.direction());
        if(definition.form()==VfxRegistry.Form.FORGOTTEN_LAND){
            double opening=Math.min(1,age/35D),closure=Math.min(1,(duration-age)/30D);float ruin=fade*(float)Math.clamp((age-35)/40D,0,1);
            mesh.face(center.add(0,.13D,0),radius*.8D*opening*closure,-age*.001D,true,cyan,fade*.28F,definition.face());
            mesh.ring(center.add(0,.23D,0),radius*opening*closure,.07D,true,age*.001D,cyan,fade*.65F,segments,definition.trail());
            for(int pillar=0;pillar<8;pillar++){double angle=pillar*Math.PI/4;Vec3 base=center.add(Math.cos(angle)*radius*.7D,0,Math.sin(angle)*radius*.7D);double height=(8+(pillar%3)*2)*Math.clamp((age-35-pillar*2)/35D,0,1)*closure;pillar(mesh,effect,base,angle,height,1.1D,ruin*.72F);}
            if(age>=75&&age<240){for(int shard=0;shard<6+effect.quality*3;shard++){double angle=shard*2.399963D+effect.packet.seed()*.00001D,reach=(4+shard%5*4)*closure,fall=(age* .19D+shard*2.3D)%20;Vec3 point=center.add(Math.cos(angle)*reach,22-fall,Math.sin(angle)*reach);stone(mesh,effect,point,.25D+(shard%3)*.1D,age*.025D+shard,fade*.7F);mesh.line(point.add(0,1.6D,0),point,.035D,cyan,fade*.5F,definition.trail());}}
            for(int wave:new int[]{100,160,220}){double elapsed=age-wave;if(elapsed>=0&&elapsed<35){float opacity=fade*(float)(1-elapsed/35);mesh.ring(center.add(0,.35D,0),Math.max(.1D,radius*elapsed/35),.16D,true,0,cyan,opacity*.9F,segments,definition.trail());mesh.face(center.add(0,.2D,0),radius*.7D,age*.001D,true,sand,opacity*.16F,definition.face());}}
            if(age>=250){mesh.ring(center.add(0,.7D+(1-closure)*2,0),Math.max(.1D,radius*closure),.09D,true,0,sand,fade*.8F,segments,definition.trail());}
        }else if(definition.form()==VfxRegistry.Form.ANCIENT_STONE){
            mesh.ribbon(effect.history(partial),.14D,cyan,fade*.8F,definition.trail());mesh.ribbon(effect.history(partial),.04D,sand,fade,definition.trail());stone(mesh,effect,center,.32D,age*.06D,fade);mesh.ring(center,.46D,.035D,false,age*.06D,cyan,fade*.6F,12,definition.trail());
        }else if(kind.equals(AncientVfx.MARK)){
            mesh.face(center.add(0,2.8D,0),.7D,0,false,cyan,fade*.9F,definition.face());mesh.line(center.add(-.55D,2.8D,0),center.add(0,3.4D,0),.025D,sand,fade*.8F,definition.trail());
        }else if(kind.equals(AncientVfx.SANCTUARY)||kind.equals(AncientVfx.SHIELD)){
            mesh.face(center.add(0,.12D,0),radius*.7D,age*.001D,true,cyan,fade*.2F,definition.face());mesh.ring(center.add(0,.22D,0),radius,.04D,true,0,sand,fade*.38F,segments,definition.trail());
            int count=kind.equals(AncientVfx.SANCTUARY)?6:4;for(int pillar=0;pillar<count;pillar++){double angle=pillar*Math.PI*2/count;Vec3 base=center.add(Math.cos(angle)*radius*.65D,.5D+Math.sin(age*.03D+pillar)*.2D,Math.sin(angle)*radius*.65D);pillar(mesh,effect,base,angle,kind.equals(AncientVfx.SANCTUARY)?4:2.6D,.55D,fade*.65F);}
        }else if(kind.equals(AncientVfx.DIVE)){
            mesh.ribbon(effect.history(partial),.25D,sand,fade*.75F,definition.trail());mesh.ribbon(effect.history(partial),.07D,cyan,fade,definition.trail());
        }else if(kind.equals(AncientVfx.STRIKE)){
            double facing=Math.atan2(effect.packet.direction().z,effect.packet.direction().x);for(int arc=0;arc<12+effect.quality*4;arc++){double first=facing-Math.PI*.7D+arc*Math.PI*1.4D/(12+effect.quality*4),second=first+Math.PI*1.4D/(12+effect.quality*4),reach=radius*(.6D+progress*.4D);mesh.line(center.add(Math.cos(first)*reach,.7D,Math.sin(first)*reach),center.add(Math.cos(second)*reach,.7D,Math.sin(second)*reach),.12D,cyan,fade*.9F,definition.trail());}
        }else{
            mesh.ring(center.add(0,.2D,0),Math.max(.1D,radius*progress),.1D,true,age*.003D,cyan,fade*.85F,segments,definition.trail());
            if(kind.equals(AncientVfx.JUDGMENT)){Vec3 point=center.add(0,10*(1-progress),0);stone(mesh,effect,point,.7D*(1-progress)+.12D,age*.02D,fade);mesh.line(point.add(0,1.4D,0),point,.06D,cyan,fade*.8F,definition.trail());}
            else if(!kind.equals(AncientVfx.CONCLUSION)){for(int shard=0;shard<5+effect.quality*2;shard++){double angle=shard*2.399963D;stone(mesh,effect,center.add(Math.cos(angle)*radius*progress,.4D+Math.sin(progress*Math.PI)*1.7D,Math.sin(angle)*radius*progress),.16D,angle+progress,fade*.8F);}}
        }
        return mesh.finish(List.of(),fade,definition.silhouette(),cyan);
    }
    private static void pillar(VfxMesh.Builder mesh,VfxEffect effect,Vec3 base,double angle,double height,double width,float opacity){if(height<=.01D){return;}Vec3 side=new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(width),depth=new Vec3(-Math.sin(angle),0,Math.cos(angle)).scale(width);Vec3 top=base.add(0,height,0);for(Vec3 edge:List.of(side,depth,side.scale(-1),depth.scale(-1))){Vec3 other=new Vec3(-edge.z,0,edge.x);mesh.quad(effect.definition.ring(),base.add(edge).add(other),base.add(edge).subtract(other),top.add(edge).subtract(other),top.add(edge).add(other),effect.packet.secondary(),opacity);}mesh.face(top.add(0,.03D,0),width*1.4D,angle,true,effect.packet.primary(),opacity,effect.definition.face());}
    private static void stone(VfxMesh.Builder mesh,VfxEffect effect,Vec3 center,double size,double angle,float opacity){Vec3 side=new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(size),depth=new Vec3(-Math.sin(angle),0,Math.cos(angle)).scale(size),up=new Vec3(0,size,0);for(Vec3 edge:List.of(side,depth,side.scale(-1),depth.scale(-1))){Vec3 other=new Vec3(-edge.z,0,edge.x);mesh.quad(effect.definition.hand(),center.add(edge).add(other).subtract(up),center.add(edge).subtract(other).subtract(up),center.add(edge).subtract(other).add(up),center.add(edge).add(other).add(up),effect.packet.secondary(),opacity);}mesh.quad(effect.definition.hand(),center.add(up).add(side).add(depth),center.add(up).add(side).subtract(depth),center.add(up).subtract(side).subtract(depth),center.add(up).subtract(side).add(depth),effect.packet.secondary(),opacity);}
}
