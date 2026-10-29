package net.ronm19.phantomism.entity.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.CreatorPhantom;
import net.ronm19.phantomism.network.CreatorBeamPacket;
import net.ronm19.phantomism.vfx.CreatorVfx;

/** One target identity per branch. Sweeps never replace these real branches. */
public final class CreatorAdaptiveBeam {
    public enum Mode { PRECISION, SPLIT, SWEEP_ACCENT, CROWD }
    private final CreatorPhantom caster;
    private final List<UUID> targets=new ArrayList<>();
    private int remaining,initialCount;
    private Mode mode=Mode.PRECISION;
    private UUID rider;
    private boolean clustered;
    public CreatorAdaptiveBeam(CreatorPhantom caster){this.caster=caster;}
    public boolean active(){return remaining>0;}
    public int remaining(){return remaining;}
    public int count(){return targets.size();}
    public List<UUID> targets(){return List.copyOf(targets);}
    public Mode mode(){return mode;}
    public boolean clustered(){return clustered;}
    public static Mode modeFor(int count){return count<=1?Mode.PRECISION:count<=4?Mode.SPLIT:count<=9?Mode.SWEEP_ACCENT:Mode.CROWD;}
    public boolean start(ServerLevel level,List<LivingEntity> candidates){
        if(active()||!caster.enabled()||!replaceTargets(candidates)){return false;}
        initialCount=targets.size();remaining=CreatorPhantom.BEAM_DURATION;
        rider=caster.getControllingPassenger()==null?null:caster.getControllingPassenger().getUUID();
        updateLayout(level);
        send(level);CreatorVfx.emit(caster,CreatorVfx.BEAM,caster,caster.position(),Vec3.ZERO,CreatorPhantom.BEAM_DURATION,2);return true;
    }
    /** Retarget a mounted channel without resetting its charge, lifetime or damage pulses. */
    public boolean refresh(ServerLevel level,List<LivingEntity> candidates){
        var passenger=caster.getControllingPassenger();
        if(!active()||!caster.enabled()||rider==null||passenger==null||!rider.equals(passenger.getUUID())||!replaceTargets(candidates)){return false;}
        // Adding enemies cannot promote existing crowd damage to single-target damage.
        initialCount=Math.max(initialCount,targets.size());updateLayout(level);send(level);return true;
    }
    private boolean replaceTargets(List<LivingEntity> candidates){
        var selected=new ArrayList<UUID>();var seen=new HashSet<UUID>();
        for(var target:candidates){if(selected.size()>=Config.creatorBeamLimit()){break;}if(seen.add(target.getUUID())&&eligible(target)){selected.add(target.getUUID());}}
        if(selected.isEmpty()){return false;}targets.clear();targets.addAll(selected);return true;
    }
    private void updateLayout(ServerLevel level){
        mode=modeFor(targets.size());Vec3 centroid=Vec3.ZERO;
        for(var id:targets){centroid=centroid.add(level.getEntity(id).position());}centroid=centroid.scale(1D/targets.size());
        double spread=0;for(var id:targets){spread+=level.getEntity(id).distanceToSqr(centroid);}clustered=spread/targets.size()<=36;
    }
    private boolean eligible(LivingEntity target){return caster.validTarget(target)&&caster.distanceToSqr(target)<=Config.creatorBeamRange()*Config.creatorBeamRange()&&caster.line(caster.getEyePosition(),target.getEyePosition());}
    public void tick(ServerLevel level){
        if(!active()){return;}UUID current=caster.getControllingPassenger()==null?null:caster.getControllingPassenger().getUUID();
        if(!caster.enabled()||!java.util.Objects.equals(rider,current)){stop();return;}
        int elapsed=CreatorPhantom.BEAM_DURATION-remaining;
        if(elapsed%2==0){targets.removeIf(id->!(level.getEntity(id) instanceof LivingEntity target)||!eligible(target));}
        if(targets.isEmpty()){stop();return;}
        if(elapsed==12||elapsed==32||elapsed==52){
            float amount=initialCount==1?8:initialCount<=4?6:4;
            for(var id:List.copyOf(targets)){if(level.getEntity(id) instanceof LivingEntity target&&eligible(target)){caster.strike(level,target,amount);}}
        }
        if(elapsed%5==0){send(level);}if(--remaining<=0){stop();}
    }
    public List<CreatorBeamPacket.Branch> snapshot(ServerLevel level){
        var branches=new ArrayList<CreatorBeamPacket.Branch>();
        for(var id:targets){if(level.getEntity(id) instanceof LivingEntity target&&eligible(target)){branches.add(new CreatorBeamPacket.Branch(target.getId(),id,target.getEyePosition()));}}
        return List.copyOf(branches);
    }
    private void send(ServerLevel level){CreatorBeamPacket.send(caster,remaining,mode.ordinal(),clustered,snapshot(level));}
    public void stop(){boolean was=active()||!targets.isEmpty();remaining=0;targets.clear();if(was&&caster.level() instanceof ServerLevel){CreatorBeamPacket.send(caster,0,mode.ordinal(),clustered,List.of());}}
}
