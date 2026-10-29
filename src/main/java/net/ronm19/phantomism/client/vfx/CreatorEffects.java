package net.ronm19.phantomism.client.vfx;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.CreatorVfx;
public final class CreatorEffects {
    private static final Identifier ENERGY=Identifier.fromNamespaceAndPath("phantomism","textures/vfx/halloween_energy.png");
    private CreatorEffects(){}
    public static boolean supports(Identifier kind){return CreatorVfx.ALL.contains(kind);}
    public static AABB bounds(VfxEffect effect){
        var box=new AABB(effect.position(),effect.position()).inflate(effect.packet.radius()+5);var packet=CreatorBeamManager.get(effect.packet.caster(),effect.packet.session());
        if(packet!=null){for(var branch:packet.branches()){box=box.minmax(new AABB(branch.endpoint(),branch.endpoint()).inflate(2));}}return box;
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect,Vec3 camera,float partial,int limit){
        var mesh=new VfxMesh.Builder(camera,Math.max(0,limit),1,Vec3.ZERO);Vec3 center=effect.interpolated(partial);double age=effect.age(partial),duration=effect.packet.duration();float fade=(float)Math.clamp(Math.min(age/7D,(duration-age)/12D),0,1);var kind=effect.packet.effect();
        if(kind.equals(CreatorVfx.BEAM)){
            return beaconBeams(effect,camera,partial,limit);
        }else if(kind.equals(CreatorVfx.AEGIS)){
            double radius=effect.packet.radius();mesh.ring(center,radius,.035D,true,age*.001D,0xFFFFFF,fade*.55F,48,ENERGY);
            for(int band=1;band<=4;band++){double a=band*Math.PI/10;mesh.ring(center.add(0,Math.sin(a)*radius*.6D,0),Math.cos(a)*radius,.4D,true,0,0x111111,fade*.08F,40,ENERGY);}
            for(int n=0;n<5;n++){double tilt=n*Math.PI/5;Vec3 axis=new Vec3(Math.cos(tilt),0,Math.sin(tilt));Vec3 previous=null;for(int s=0;s<=20;s++){double a=s*Math.PI/20;Vec3 point=center.add(axis.scale(Math.cos(a)*radius)).add(0,Math.sin(a)*radius*.6D,0);if(previous!=null){mesh.line(previous,point,.018D,0xFFFFFF,fade*.18F,ENERGY);}previous=point;}}
        }else if(kind.equals(CreatorVfx.EVOLUTION)){
            double grow=Math.min(1,age/30D);mesh.ring(center,24*grow,.07D,true,age*.001D,0xFFFFFF,fade*.65F,64,ENERGY);mesh.ring(center.add(0,.2D,0),5*grow,.045D,true,-age*.003D,0xFFFFFF,fade*.75F,40,ENERGY);
            for(int n=0;n<10;n++){double a=n*Math.PI/5+age*.002D;Vec3 floor=center.add(Math.cos(a)*9,0,Math.sin(a)*9);mesh.line(floor,floor.add(0,6+Math.sin(age*.03D+n)*2,0),.045D,0xFFFFFF,fade*.45F,ENERGY);}
        }else if(kind.equals(CreatorVfx.PULSE)||kind.equals(CreatorVfx.WAVE)||kind.equals(CreatorVfx.FINAL)){
            double progress=Math.min(1,age/Math.min(20,duration)),radius=effect.packet.radius()*progress;mesh.ring(center,radius,.065D,true,0,0xFFFFFF,fade*.85F,48,ENERGY);mesh.ring(center.add(0,.12D,0),radius*.96D,.09D,true,0,0x191919,fade*.5F,40,ENERGY);
        }else if(kind.equals(CreatorVfx.GENESIS)){
            mesh.line(center.add(effect.packet.direction()),center,.12D,0xFFFFFF,fade*.9F,ENERGY);mesh.line(center,center.add(0,5,0),.13D,0xFFFFFF,fade*.7F,ENERGY);mesh.ring(center,age*.07D,.04D,true,0,0xFFFFFF,fade,24,ENERGY);
        }else if(kind.equals(CreatorVfx.STEP)){
            var points=effect.history(partial);for(int n=1;n<points.size();n++){mesh.line(points.get(n-1),points.get(n),.28D,0x050505,fade*.5F,ENERGY);mesh.line(points.get(n-1),points.get(n),.025D,0xFFFFFF,fade*.65F,ENERGY);}
        }else{
            int count=kind.equals(CreatorVfx.PRESENCE)?2:6;for(int n=0;n<count;n++){double a=n*Math.PI*2/count+age*.01D;mesh.face(center.add(Math.cos(a),.3D+age*.015D,Math.sin(a)),.08D,0,false,0xFFFFFF,fade*.5F,ENERGY);}
        }
        return mesh.finish(List.of(),1,ENERGY,0xFFFFFF);
    }
    private static List<VfxMesh.Batch> beaconBeams(VfxEffect effect,Vec3 camera,float partial,int limit){
        var packet=CreatorBeamManager.get(effect.packet.caster(),effect.packet.session());var world=Minecraft.getInstance().level;
        if(packet==null||world==null||!(world.getEntity(packet.casterId()) instanceof net.ronm19.phantomism.entity.CreatorPhantom source)){return List.of();}
        double age=effect.age(partial),duration=effect.packet.duration();
        double phase=net.ronm19.phantomism.entity.CreatorPhantom.BEAM_DURATION-duration+age;
        float strength=(float)Math.clamp((phase-7)/5,0,1)*(float)Math.clamp((duration-age)/8,0,1);
        Vec3 start=source.getEyePosition(partial);var endpoints=new java.util.ArrayList<Vec3>();
        for(var branch:packet.branches()){
            var target=world.getEntity(branch.entityId());
            if(target!=null&&target.getUUID().equals(branch.uuid())&&target.isAlive()){endpoints.add(target.getEyePosition(partial));}
        }
        var result=new java.util.ArrayList<>(CreatorBeaconBeam.build(start,endpoints,camera,
                world.getGameTime()+partial,strength,packet.mode()==0,effect.quality,Math.max(0,limit)));
        int used=result.stream().mapToInt(VfxMesh.Batch::count).sum();
        var accents=new VfxMesh.Builder(camera,Math.max(0,limit-used),1,Vec3.ZERO);
        if(phase<12){accents.ring(start,.3D+phase*.025D,.035D,false,0,0xFFFFFF,(float)Math.clamp(phase/7D,0,1)*.8F,16,ENERGY);}
        if(effect.quality>1&&endpoints.size()<=32){for(var end:endpoints){accents.face(end,.11D,age*.01D,false,0xFFFFFF,strength*.65F,ENERGY);}}
        if(packet.clustered()&&packet.mode()>=2){accents.ring(start,.8D,.025D,true,age*.015D,0x252525,strength*.25F,16,ENERGY);}
        result.addAll(accents.finish(List.of(),1,ENERGY,0xFFFFFF));return List.copyOf(result);
    }

}
