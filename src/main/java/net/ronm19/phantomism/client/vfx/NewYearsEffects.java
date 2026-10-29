package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.NewYearsPhantom;
import net.ronm19.phantomism.vfx.NewYearsVfx;

/** Local, bounded clock/celebration meshes; never alters simulation clocks. */
public final class NewYearsEffects {
    private static final Identifier ENERGY=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/halloween_energy.png");
    private static final Identifier HOURGLASS=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/new_years_hourglass.png");
    private static final int GOLD=0xF8D283,SILVER=0xDCEAF5,BLUE=0x83B7F5,WHITE=0xFFF9E4;
    private static final int[] COLORS={GOLD,SILVER,BLUE,WHITE};
    private static final String[] ROMAN={"XII","I","II","III","IV","V","VI","VII","VIII","IX","X","XI"};
    private static final int[] DIGITS={0x3f,6,0x5b,0x4f,0x66};
    private NewYearsEffects() {}
    public static boolean supports(Identifier kind){return NewYearsVfx.ALL.contains(kind);}
    public static float light(NewYearsPhantom phantom,float partial){
        var effect=VfxManager.find(phantom.getUUID(),NewYearsVfx.COUNTDOWN);
        if(effect==null || effect.packet.entityId()!=phantom.getId() || !effect.packet.attached().equals(phantom.getUUID()) || phantom.isHolidayDefeated()){return 0;}
        double age=effect.age(partial),build=Math.clamp(age/24D,0,1),fade=Math.clamp((effect.packet.duration()-age)/24D,0,1);
        return (float)(build*build*(3-2*build)*fade*fade*(3-2*fade)*.78D);
    }
    private static Vec3[] basis(Vec3 center,Vec3 camera){
        Vec3 normal=camera.subtract(center).normalize(),right=new Vec3(0,1,0).cross(normal).normalize();
        if(right.lengthSqr()<.01D){right=new Vec3(1,0,0);}return new Vec3[]{right,normal.cross(right).normalize()};
    }
    private static Vec3 point(Vec3 c,Vec3 r,Vec3 u,double x,double y){return c.add(r.scale(x)).add(u.scale(y));}
    private static void stroke(VfxMesh.Builder m,Vec3 c,Vec3 r,Vec3 u,double x,double y,double xx,double yy,double width,int color,float alpha){m.line(point(c,r,u,x,y),point(c,r,u,xx,yy),width,color,alpha,ENERGY);}
    private static void glyph(VfxMesh.Builder m,Vec3 c,Vec3 r,Vec3 u,char letter,double size,float alpha){
        if(letter=='I'){stroke(m,c,r,u,0,-size,0,size,size*.15D,GOLD,alpha);}
        else if(letter=='V'){stroke(m,c,r,u,-size*.55D,size,0,-size,size*.12D,GOLD,alpha);stroke(m,c,r,u,0,-size,size*.55D,size,size*.12D,GOLD,alpha);}
        else {stroke(m,c,r,u,-size*.5D,-size,size*.5D,size,size*.12D,GOLD,alpha);stroke(m,c,r,u,-size*.5D,size,size*.5D,-size,size*.12D,GOLD,alpha);}
    }
    private static void digit(VfxMesh.Builder m,Vec3 c,Vec3 r,Vec3 u,int number,double size,float alpha){
        double[][] segments={{-1,1,1,1},{1,1,1,0},{1,0,1,-1},{-1,-1,1,-1},{-1,0,-1,-1},{-1,1,-1,0},{-1,0,1,0}};
        int mask=DIGITS[Math.clamp(number,0,4)];
        for(int i=0;i<7;i++){if((mask&(1<<i))!=0){var p=segments[i];stroke(m,c,r,u,p[0]*size,p[1]*size,p[2]*size,p[3]*size,.06D,WHITE,alpha);}}
    }
    private static void clock(VfxMesh.Builder m,Vec3 c,Vec3 camera,double radius,double progress,float alpha,boolean numbers){
        var basis=basis(c,camera);Vec3 r=basis[0],u=basis[1];
        Vec3 previous=null;
        for(int n=0;n<=32;n++){double a=n*Math.PI/16;Vec3 p=point(c,r,u,Math.sin(a)*radius,Math.cos(a)*radius);if(previous!=null){m.line(previous,p,radius*.012D,GOLD,alpha,ENERGY);}previous=p;}
        for(int n=0;n<12;n++){
            double a=n*Math.PI/6;double x=Math.sin(a),y=Math.cos(a);
            stroke(m,c,r,u,x*radius*.92D,y*radius*.92D,x*radius*.98D,y*radius*.98D,radius*.012D,SILVER,alpha);
            if(numbers){String text=ROMAN[n];Vec3 at=point(c,r,u,x*radius*.78D,y*radius*.78D);
                for(int i=0;i<text.length();i++){glyph(m,at.add(r.scale((i-(text.length()-1)/2D)*radius*.085D)),r,u,text.charAt(i),radius*.055D,alpha);}}
        }
        double minute=-(1-progress)*Math.PI*.66D,hour=-(1-progress)*Math.PI*.16D;
        stroke(m,c,r,u,0,0,Math.sin(minute)*radius*.63D,Math.cos(minute)*radius*.63D,radius*.018D,GOLD,alpha);
        stroke(m,c,r,u,0,0,Math.sin(hour)*radius*.38D,Math.cos(hour)*radius*.38D,radius*.025D,SILVER,alpha);
        m.face(c,radius*.045D,0,false,WHITE,alpha,ENERGY);
    }
    private static void star(VfxMesh.Builder m,Vec3 c,double size,int color,float alpha){
        m.face(c,size,0,false,color,alpha,ENERGY);m.face(c,size*.6D,Math.PI/4,false,WHITE,alpha*.55F,ENERGY);
    }
    private static void fireworks(VfxMesh.Builder m,Vec3 c,Vec3 camera,double age,float alpha,int count){
        for(int n=0;n<count;n++){
            double phase=(age+n*19)%65/65D,a=n*Math.PI*2/count;
            Vec3 center=c.add(Math.cos(a)*(5+n%2*3),3+Math.sin(a)*2,Math.sin(a)*(5+n%2*3));
            Vec3[] basis=basis(center,camera);double reach=.2D+phase*3.4D;
            for(int ray=0;ray<10;ray++){
                double angle=ray*Math.PI/5+n*.4D;Vec3 dir=basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
                m.line(center.add(dir.scale(reach*.65D)),center.add(dir.scale(reach)),.025D,COLORS[n%4],alpha*(float)Math.sin(phase*Math.PI),ENERGY);
            }
            star(m,center,.12D,COLORS[n%4],alpha*.4F);
        }
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        VfxMesh.Builder mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,Vec3.ZERO);
        Vec3 center=effect.interpolated(partial);double age=effect.age(partial),duration=effect.packet.duration();
        float fade=(float)Math.clamp(Math.min(age/5D,(duration-age)/12D),0,1);var kind=effect.packet.effect();
        if(kind.equals(NewYearsVfx.COUNTDOWN)){
            Vec3 face=center.add(0,7,0);double progress=Math.min(1,age/80D);
            clock(mesh,face,camera,5.2D,progress,fade*.8F,true);
            if(age<90){var basis=basis(face,camera);digit(mesh,face.add(basis[1].scale(-1.8D)),basis[0],basis[1],Math.max(0,4-(int)(age/20)),.6D,fade*.8F);}
            mesh.ring(center,.5D+3.5D*Math.min(1,age/24D),.06D,true,age*.002D,GOLD,fade*.4F,24,ENERGY);
            if(age>=80){fireworks(mesh,face,camera,age-80,fade*.85F,effect.quality<=1?3:4);}
        }else if(kind.equals(NewYearsVfx.SPARK)||kind.equals(NewYearsVfx.DASH)){
            var history=effect.history(partial);
            for(int n=1;n<history.size();n++){
                mesh.line(history.get(n-1),history.get(n),kind.equals(NewYearsVfx.DASH)?.07D:.035D,n%3==0?SILVER:GOLD,fade*.65F,ENERGY);
                if(kind.equals(NewYearsVfx.DASH)&&n%5==0){clock(mesh,history.get(n),camera,.25D,1,fade*.65F,false);}
                if(n%3==0){star(mesh,history.get(n),.06D,SILVER,fade*.5F);}
            }
        }else if(kind.equals(NewYearsVfx.SHIELD)){
            mesh.ring(center.add(0,.25D,0),2.8D,.08D,true,age*.004D,GOLD,fade*.65F,32,ENERGY);
            for(int n=0;n<6;n++){double a=age*.01D+n*Math.PI/3;star(mesh,center.add(Math.cos(a)*2.8D,.6D+Math.sin(a)*.3D,Math.sin(a)*2.8D),.12D,n%2==0?GOLD:SILVER,fade*.65F);}
            clock(mesh,center.add(0,1.2D,0),camera,.65D,1,fade*.35F,false);
        }else if(kind.equals(NewYearsVfx.WARD)){
            for(int n=0;n<8;n++){double a=n*Math.PI/4+age*.008D;mesh.face(center.add(Math.cos(a)*3,1+Math.sin(a)*.3D,Math.sin(a)*3),.4D,Math.sin(age*.02D)*.08D,false,WHITE,fade*.8F,HOURGLASS);}
        }else if(kind.equals(NewYearsVfx.WAVE)||kind.equals(NewYearsVfx.FINAL)){
            double radius=effect.packet.radius()*(.15D+Math.min(1,age/duration)*.6D);
            mesh.ring(center,radius,.07D,true,age*.002D,GOLD,fade*.5F,32,ENERGY);
            fireworks(mesh,center.add(0,5,0),camera,age,fade*.85F,kind.equals(NewYearsVfx.FINAL)?6:4);
        }else if(kind.equals(NewYearsVfx.DEFEAT)){
            clock(mesh,center.add(0,.8D,0),camera,1.2D,age/duration,fade*.75F,true);
        }else{
            for(int n=0;n<5;n++){double a=n*Math.PI*.4D+age*.015D;star(mesh,center.add(Math.cos(a)*.8D,.4D+age*.025D,Math.sin(a)*.8D),.13D,n%2==0?GOLD:SILVER,fade*.8F);}
        }
        return mesh.finish(List.of(),1,ENERGY,WHITE);
    }
}
