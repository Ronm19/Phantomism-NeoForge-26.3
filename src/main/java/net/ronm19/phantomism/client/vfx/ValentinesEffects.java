package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.ValentinesPhantom;
import net.ronm19.phantomism.vfx.ValentinesVfx;

public final class ValentinesEffects {
    private static final Identifier ENERGY=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/halloween_energy.png");
    private static final Identifier HEART=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/valentines_heart_particle.png");
    private static final Identifier PETAL=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/valentines_petal.png");
    private static final int ROSE=0xF07EAC,MAGENTA=0xD74386,IVORY=0xFFE3ED,GOLD=0xE7BA7D;
    private ValentinesEffects() {}
    public static boolean supports(Identifier kind){return ValentinesVfx.ALL.contains(kind);}
    public static float light(ValentinesPhantom phantom,float partial){
        var effect=VfxManager.find(phantom.getUUID(),ValentinesVfx.SERENADE);
        if(effect==null||effect.packet.entityId()!=phantom.getId()||!effect.packet.attached().equals(phantom.getUUID())||phantom.isHolidayDefeated()){return 0;}
        double age=effect.age(partial),build=Math.clamp(age/24D,0,1),fade=Math.clamp((effect.packet.duration()-age)/24D,0,1);
        return (float)(build*build*(3-2*build)*fade*fade*(3-2*fade)*.78D);
    }
    private static void heart(VfxMesh.Builder mesh,Vec3 place,double size,double turn,int color,float alpha){mesh.face(place,size,turn,false,color,alpha,HEART);}
    private static void outline(VfxMesh.Builder mesh,Vec3 center,Vec3 camera,double size,float alpha){
        Vec3 normal=camera.subtract(center).normalize(),right=new Vec3(0,1,0).cross(normal).normalize();
        if(right.lengthSqr()<.01D){right=new Vec3(1,0,0);}Vec3 up=normal.cross(right).normalize(),previous=null;
        for(int n=0;n<=40;n++){
            double a=n*Math.PI/20,s=Math.sin(a),x=s*s*s,y=(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))/16;
            Vec3 point=center.add(right.scale(x*size)).add(up.scale(y*size));
            if(previous!=null){mesh.line(previous,point,.045D,ROSE,alpha,ENERGY);}previous=point;
        }
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,Vec3.ZERO);Vec3 center=effect.interpolated(partial);
        double age=effect.age(partial),duration=effect.packet.duration();float fade=(float)Math.clamp(Math.min(age/5D,(duration-age)/12D),0,1);var kind=effect.packet.effect();
        if(kind.equals(ValentinesVfx.SERENADE)){
            double opening=Math.min(1,age/24D);outline(mesh,center.add(0,7,0),camera,4.5D*opening,fade*.8F);
            mesh.ring(center,4.5D*opening,.065D,true,age*.003D,GOLD,fade*.4F,24,ENERGY);
            mesh.ring(center.add(0,.2D,0),6*opening,.06D,true,-age*.004D,ROSE,fade*.5F,32,ENERGY);
            int count=effect.quality<=1?10:16;
            for(int n=0;n<count;n++){
                double a=n*Math.PI*2/count+age*.006D,phase=(age/95D+n/(double)count)%1;
                Vec3 point=center.add(Math.cos(a)*(3+phase*8),11-phase*10,Math.sin(a)*(3+phase*8));
                heart(mesh,point,.22D+phase*.2D,Math.sin(age*.025D+n)*.12D,n%3==0?IVORY:ROSE,fade*(float)Math.sin(phase*Math.PI)*.8F);
                if(n%4==0){mesh.line(point.add(0,.5D,0),point,.018D,GOLD,fade*.25F,ENERGY);}
            }
        }else if(kind.equals(ValentinesVfx.BOLT)||kind.equals(ValentinesVfx.DASH)){
            var history=effect.history(partial);
            for(int n=1;n<history.size();n++){
                mesh.line(history.get(n-1),history.get(n),kind.equals(ValentinesVfx.DASH)?.08D:.035D,ROSE,fade*.6F,ENERGY);
                if(n%3==0){heart(mesh,history.get(n),.13D,n*.05D,n%2==0?IVORY:ROSE,fade*.7F);}
            }
        }else if(kind.equals(ValentinesVfx.AURA)){
            mesh.ring(center.add(0,.1D,0),effect.packet.radius(),.065D,true,age*.002D,GOLD,fade*.35F,40,ENERGY);
            mesh.ring(center.add(0,.2D,0),3,.07D,true,-age*.004D,ROSE,fade*.55F,24,ENERGY);
            for(int n=0;n<8;n++){double a=n*Math.PI/4+age*.009D;heart(mesh,center.add(Math.cos(a)*3,.8D+Math.sin(a)*.25D,Math.sin(a)*3),.25D,0,n%3==0?IVORY:ROSE,fade*.75F);}
        }else if(kind.equals(ValentinesVfx.CHARM)){
            for(int n=0;n<10;n++){double a=n*Math.PI/5+age*.01D;heart(mesh,center.add(Math.cos(a)*4,1+age*.015D,Math.sin(a)*4),.22D,Math.sin(age*.025D)*.1D,ROSE,fade*.7F);}
        }else if(kind.equals(ValentinesVfx.EMBRACE)){
            double radius=.5D+age*.2D;mesh.ring(center,radius,.055D,true,age*.006D,GOLD,fade*.5F,32,ENERGY);
            for(int n=0;n<12;n++){double a=n*Math.PI/6+age*.012D;mesh.face(center.add(Math.cos(a)*radius,.5D+Math.sin(age*.04D+n)*.6D,Math.sin(a)*radius),.25D,a,false,n%2==0?ROSE:IVORY,fade*.8F,PETAL);}
            for(int n=0;n<4;n++){heart(mesh,center.add(Math.cos(n*Math.PI/2)*2,1+age*.035D,Math.sin(n*Math.PI/2)*2),.3D,0,IVORY,fade*.7F);}
        }else if(kind.equals(ValentinesVfx.WAVE)||kind.equals(ValentinesVfx.FINAL)){
            double progress=Math.min(1,age/duration),radius=effect.packet.radius()*(.15D+progress*.6D);
            mesh.ring(center,radius,.075D,true,age*.004D,kind.equals(ValentinesVfx.FINAL)?GOLD:ROSE,fade*.55F,40,ENERGY);
            for(int n=0;n<12;n++){double a=n*Math.PI/6;heart(mesh,center.add(Math.cos(a)*radius,.7D+Math.sin(progress*Math.PI)*2,Math.sin(a)*radius),.35D,0,n%3==0?IVORY:ROSE,fade*.85F);}
            if(kind.equals(ValentinesVfx.FINAL)){outline(mesh,center.add(0,5,0),camera,3,fade*.65F);}
        }else{
            int count=kind.equals(ValentinesVfx.DEFEAT)?6:4;
            for(int n=0;n<count;n++){double a=n*Math.PI*2/count+age*.02D;heart(mesh,center.add(Math.cos(a)*.8D,.6D+age*.025D,Math.sin(a)*.8D),.2D,0,n%2==0?IVORY:ROSE,fade*.75F);}
        }
        return mesh.finish(List.of(),1,ENERGY,IVORY);
    }
}
