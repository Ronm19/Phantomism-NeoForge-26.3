package net.ronm19.phantomism.entity.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PlaguePhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.PlagueVfx;

public final class PlagueOutbreak {
    public static final int MAX_VICTIMS=12, MAX_GENERATION=3, MAX_LINEAGES=32, MAX_ACTIVE=128, MAX_OWNER_LINEAGES=2, MAX_OWNER_ACTIVE=24;
    public static final double SPREAD_RADIUS=3.5D;
    private static final Identifier SLOW=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"infected_mutation");
    private static final Map<ServerLevel, Registry> LEVELS=new WeakHashMap<>();
    public record Patient(UUID id,int generation,long born,long until,long nextDamage,int stage) {
        public static final Codec<Patient> CODEC=RecordCodecBuilder.create(builder->builder.group(UUIDUtil.CODEC.fieldOf("id").forGetter(Patient::id),Codec.intRange(0,3).fieldOf("generation").forGetter(Patient::generation),Codec.LONG.fieldOf("born").forGetter(Patient::born),Codec.LONG.fieldOf("until").forGetter(Patient::until),Codec.LONG.fieldOf("next_damage").forGetter(Patient::nextDamage),Codec.intRange(0,2).fieldOf("stage").forGetter(Patient::stage)).apply(builder,Patient::new));
    }
    private record Claim(WeakReference<PlaguePhantom> source,UUID lineage) {}
    private static final class Registry {
        private final Map<UUID,WeakReference<PlaguePhantom>> sources=new LinkedHashMap<>();
        private final Map<UUID,Claim> claims=new HashMap<>();
        private long swept=Long.MIN_VALUE;
        private void maintain(ServerLevel level) {
            if(swept==level.getGameTime()/20){return;}swept=level.getGameTime()/20;
            sources.entrySet().removeIf(entry->{PlaguePhantom source=entry.getValue().get();return source==null||source.level()!=level||!source.enabled()||!source.outbreak().live();});
            claims.entrySet().removeIf(entry->{PlaguePhantom source=entry.getValue().source().get();return source==null||source.level()!=level||!source.enabled()||!Objects.equals(source.outbreak().lineage,entry.getValue().lineage())||!source.outbreak().active(entry.getKey());});
        }
    }
    private final PlaguePhantom source;
    private final Map<UUID,Patient> patients=new LinkedHashMap<>();
    private UUID lineage,owner;
    private long until,nextSpread;
    private int cursor;
    private boolean quarantined;
    public PlagueOutbreak(PlaguePhantom source){this.source=source;}
    public UUID id(){return lineage;}
    public boolean live(){return lineage!=null&&source.level() instanceof ServerLevel level&&level.getGameTime()<until;}
    public int uniqueVictims(){return patients.size();}
    public int generation(LivingEntity target){Patient patient=patients.get(target.getUUID());return patient==null?-1:patient.generation();}
    public Collection<Patient> history(){return List.copyOf(patients.values());}
    private Registry registry(){ServerLevel level=(ServerLevel)source.level();Registry registry=LEVELS.computeIfAbsent(level,ignored->new Registry());registry.maintain(level);return registry;}
    private boolean register() {
        if(!(source.level() instanceof ServerLevel)||!source.enabled()||quarantined){return false;}Registry registry=registry();
        if(registry.sources.containsKey(source.getUUID())){return true;}
        UUID key=source.ownerKey();long owned=registry.sources.values().stream().map(WeakReference::get).filter(Objects::nonNull).filter(other->other.ownerKey().equals(key)).count();
        if(registry.sources.size()>=MAX_LINEAGES||owned>=MAX_OWNER_LINEAGES){return false;}
        registry.sources.put(source.getUUID(),new WeakReference<>(source));return true;
    }
    public boolean ensure() {
        if(!(source.level() instanceof ServerLevel level)||!source.enabled()){return false;}
        if(lineage!=null&&!live()){suspend();lineage=null;owner=null;patients.clear();quarantined=false;}
        if(lineage!=null&&!Objects.equals(owner,source.ownerKey())){quarantined=true;suspend();return false;}
        if(!register()){return false;}
        if(lineage==null){lineage=UUID.randomUUID();owner=source.ownerKey();until=level.getGameTime()+600;nextSpread=level.getGameTime()+40;}
        return live()&&!quarantined;
    }
    public boolean active(UUID victim) {
        Patient patient=patients.get(victim);return live()&&patient!=null&&source.level().getGameTime()<patient.until();
    }
    public boolean infected(LivingEntity target){return target!=null&&active(target.getUUID())&&target.hasEffect(PhantomismContent.INFECTED)&&owns(target.getUUID());}
    private boolean owns(UUID victim){Claim claim=registry().claims.get(victim);return claim!=null&&claim.source().get()==source&&Objects.equals(claim.lineage(),lineage);}
    public boolean useful(LivingEntity target){Patient patient=target==null?null:patients.get(target.getUUID());return source.canInfect(target)&&(!live()||patient==null&&patients.size()<MAX_VICTIMS||patient!=null&&patient.until()-source.level().getGameTime()<60);}
    public boolean infect(LivingEntity target){return target!=null&&source.line(source.getEyePosition(),target.getEyePosition())&&infect(target,0,false);}
    private boolean infect(LivingEntity target,int generation,boolean spreading) {
        if(!source.canInfect(target)||!ensure()){return false;}
        ServerLevel level=(ServerLevel)source.level();long now=level.getGameTime();Patient previous=patients.get(target.getUUID());Registry registry=registry();Claim existing=registry.claims.get(target.getUUID());
        if(existing!=null&&(existing.source().get()!=source||!Objects.equals(existing.lineage(),lineage))||spreading&&previous!=null||previous==null&&patients.size()>=MAX_VICTIMS||generation>MAX_GENERATION){return false;}
        if(previous==null&&target.hasEffect(PhantomismContent.INFECTED)){return false;}
        if(previous!=null&&previous.until()-now>=80&&target.hasEffect(PhantomismContent.INFECTED)){return false;}
        long ownerActive=registry.claims.values().stream().map(Claim::source).map(WeakReference::get).filter(Objects::nonNull).filter(other->other.ownerKey().equals(source.ownerKey())).count();
        if(existing==null&&(registry.claims.size()>=MAX_ACTIVE||ownerActive>=MAX_OWNER_ACTIVE)){return false;}
        int inherited=previous==null?generation:previous.generation();long expires=Math.min(until,now+160);
        Patient patient=new Patient(target.getUUID(),inherited,previous==null?now:previous.born(),expires,previous==null?now+40:Math.max(now+1,previous.nextDamage()),previous==null?0:previous.stage());
        patients.put(target.getUUID(),patient);registry.claims.put(target.getUUID(),new Claim(new WeakReference<>(source),lineage));
        if(!target.addEffect(new MobEffectInstance(PhantomismContent.INFECTED,(int)(expires-now),0,false,true,true),source)||!source.canInfect(target)){
            if(previous==null){patients.remove(target.getUUID());}else{patients.put(target.getUUID(),previous);}if(existing==null){registry.claims.remove(target.getUUID());}return false;
        }
        source.infectionPulse();PlagueVfx.emit(source,PlagueVfx.INFECT,target,target.getBoundingBox().getCenter(),Vec3.ZERO,16,.7F);return true;
    }
    private void end(Patient patient,LivingEntity target) {
        Registry registry=registry();Claim claim=registry.claims.get(patient.id());
        if(claim==null||claim.source().get()==source&&Objects.equals(claim.lineage(),lineage)){
            registry.claims.remove(patient.id());if(target!=null){target.removeEffect(PhantomismContent.INFECTED);removeSlow(target);}
        }
        patients.put(patient.id(),new Patient(patient.id(),patient.generation(),patient.born(),0,patient.nextDamage(),patient.stage()));
    }
    private static void removeSlow(LivingEntity target){AttributeInstance speed=target.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(SLOW);}}
    private void mutation(Patient patient,LivingEntity target) {
        AttributeInstance speed=target.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null){speed.removeModifier(SLOW);if(patient.stage()>0&&Math.floorMod(patient.id().hashCode(),3)==0&&target instanceof Mob mob&&!GravityControl.movementImmune(mob)){speed.addTransientModifier(new AttributeModifier(SLOW,patient.stage()==1?-.12D:-.2D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}
    }
    private void spread(ServerLevel level,LivingEntity parent,Patient patient) {
        if(patient.generation()>=MAX_GENERATION||patients.size()>=MAX_VICTIMS||!live()){return;}
        int transmitted=0;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,parent.getBoundingBox().inflate(SPREAD_RADIUS),source::canInfect).stream().filter(target->target!=parent&&!patients.containsKey(target.getUUID())&&parent.distanceToSqr(target)<=SPREAD_RADIUS*SPREAD_RADIUS&&source.line(parent.getEyePosition(),target.getEyePosition())&&(!source.pandemicActive()||source.inPandemic(target))).sorted(Comparator.comparingDouble(parent::distanceToSqr)).limit(8).toList()){
            if(infect(target,patient.generation()+1,true)){PlagueVfx.emit(source,PlagueVfx.CHAIN,null,parent.getBoundingBox().getCenter(),target.getBoundingBox().getCenter().subtract(parent.getBoundingBox().getCenter()),16,.2F);if(++transmitted>=2){break;}}
        }
    }
    public void tick() {
        if(!(source.level() instanceof ServerLevel level)){return;}long now=level.getGameTime();
        if(lineage==null){return;}if(!live()){suspend();lineage=null;owner=null;patients.clear();quarantined=false;return;}
        if(!source.ownerKey().equals(owner)){quarantined=true;}if(!source.enabled()||quarantined){suspend();return;}
        if(source.tickCount%20!=0||!register()){return;}
        var active=new ArrayList<Patient>();
        for(Patient patient:List.copyOf(patients.values())){
            LivingEntity target=level.getEntity(patient.id()) instanceof LivingEntity living?living:null;
            if(patient.until()<=now||target==null||!source.canInfect(target)||!target.hasEffect(PhantomismContent.INFECTED)){end(patient,target);continue;}
            Registry registry=registry();Claim claim=registry.claims.get(patient.id());
            if(claim==null){long owned=registry.claims.values().stream().map(Claim::source).map(WeakReference::get).filter(Objects::nonNull).filter(other->other.ownerKey().equals(source.ownerKey())).count();if(registry.claims.size()>=MAX_ACTIVE||owned>=MAX_OWNER_ACTIVE){patients.put(patient.id(),new Patient(patient.id(),patient.generation(),patient.born(),0,patient.nextDamage(),patient.stage()));removeSlow(target);continue;}registry.claims.put(patient.id(),new Claim(new WeakReference<>(source),lineage));}
            else if(claim.source().get()!=source||!Objects.equals(claim.lineage(),lineage)){patients.put(patient.id(),new Patient(patient.id(),patient.generation(),patient.born(),0,patient.nextDamage(),patient.stage()));continue;}
            int stage=Math.clamp((int)((now-patient.born())/60),0,2);long next=patient.nextDamage();
            if(now>=next&&source.pandemicTicks()!=1){float amount=1+(stage>0&&Math.floorMod(patient.id().hashCode(),3)==2?.25F*stage:0)+(source.pandemicActive()&&source.inPandemic(target)?.5F:0);source.damage(level,target,source.damageSources().mobAttack(source),amount,false);next=now+40;}
            if(!target.isAlive()||!active(patient.id())){continue;}
            Patient updated=new Patient(patient.id(),patient.generation(),patient.born(),patient.until(),next,stage);patients.put(patient.id(),updated);mutation(updated,target);active.add(updated);
            if(stage>patient.stage()){source.infectionPulse();PlagueVfx.emit(source,PlagueVfx.MUTATION,target,target.position(),Vec3.ZERO,16,1);}
        }
        if(now>=nextSpread&&!active.isEmpty()){nextSpread=now+(source.pandemicActive()?20:40);int first=Math.floorMod(cursor,active.size());int count=Math.min(4,active.size());cursor=(first+count)%active.size();for(int index=0;index<count;index++){Patient patient=active.get((first+index)%active.size());if(level.getEntity(patient.id()) instanceof LivingEntity target&&source.canInfect(target)){spread(level,target,patient);}}}
    }
    public void death(LivingEntity target){Patient patient=patients.get(target.getUUID());if(patient!=null&&active(patient.id())&&owns(patient.id())&&source.enabled()&&source.level() instanceof ServerLevel level){end(patient,target);spread(level,target,patient);}}
    public void suspend(){if(!(source.level() instanceof ServerLevel level)||lineage==null){return;}for(Patient patient:List.copyOf(patients.values())){end(patient,level.getEntity(patient.id()) instanceof LivingEntity target?target:null);}Registry registry=registry();registry.sources.remove(source.getUUID());}
    public void save(ValueOutput output){if(lineage==null){return;}output.store("PlagueLineage",UUIDUtil.CODEC,lineage);output.store("PlagueOutbreakOwner",UUIDUtil.CODEC,owner);output.putLong("PlagueLineageUntil",until);output.putLong("PlagueNextSpread",nextSpread);output.putBoolean("PlagueQuarantined",quarantined);output.store("PlaguePatients",Patient.CODEC.listOf(0,12),List.copyOf(patients.values()));}
    public void load(ValueInput input){suspend();patients.clear();lineage=input.read("PlagueLineage",UUIDUtil.CODEC).orElse(null);owner=input.read("PlagueOutbreakOwner",UUIDUtil.CODEC).orElse(null);long now=source.level().getGameTime();until=Math.clamp(input.getLongOr("PlagueLineageUntil",0),0,now+600);nextSpread=Math.clamp(input.getLongOr("PlagueNextSpread",now+40),now+1,now+40);quarantined=input.getBooleanOr("PlagueQuarantined",false);for(Patient patient:input.read("PlaguePatients",Patient.CODEC.listOf(0,12)).orElse(List.of())){patients.putIfAbsent(patient.id(),new Patient(patient.id(),patient.generation(),Math.min(now,Math.max(0,patient.born())),Math.min(patient.until(),Math.min(until,now+160)),Math.clamp(patient.nextDamage(),now+1,now+40),patient.stage()));}if(owner==null||!live()){lineage=null;owner=null;patients.clear();}}
    public static PlaguePhantom responsible(LivingEntity target){if(!(target.level() instanceof ServerLevel level)){return null;}Registry registry=LEVELS.get(level);if(registry==null){return null;}Claim claim=registry.claims.get(target.getUUID());PlaguePhantom source=claim==null?null:claim.source().get();return source!=null&&source.enabled()&&source.outbreak().live()&&Objects.equals(source.outbreak().id(),claim.lineage())?source:null;}
    public static float offense(LivingEntity target){PlaguePhantom source=responsible(target);if(source==null||!source.outbreak().infected(target)){return 1;}Patient patient=source.outbreak().patients.get(target.getUUID());return patient.stage()>0&&Math.floorMod(patient.id().hashCode(),3)==1?target instanceof Mob mob&&GravityControl.movementImmune(mob)?.95F:patient.stage()==1?.9F:.85F:1;}
    public static int globalActive(ServerLevel level){Registry registry=LEVELS.get(level);if(registry==null){return 0;}registry.maintain(level);return registry.claims.size();}
    public static int globalLineages(ServerLevel level){Registry registry=LEVELS.get(level);if(registry==null){return 0;}registry.maintain(level);return registry.sources.size();}
    public static void orphan(LivingEntity target){PlaguePhantom source=responsible(target);if(source!=null){Patient patient=source.outbreak().patients.get(target.getUUID());if(patient!=null){source.outbreak().end(patient,target);}}else{removeSlow(target);}}
}
