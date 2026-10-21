package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.entity.*;
import net.ronm19.phantomism.vfx.VoidVfx;

public final class VoidRescue {
    private record Snapshot(ResourceKey<Level> dimension,Vec3 position,long expires){}
    private static final Map<LivingEntity,Long> RECEIPTS=new WeakHashMap<>();
    private static final Set<UUID> MOVING=new HashSet<>();
    private record MoveState(Entity entity,Vec3 position,Vec3 motion,float yaw,float pitch,Entity vehicle){}
    private final VoidPhantom source;
    private Snapshot safe,anchor;
    private UUID ward;
    private long nextSelf,nextMarker;
    public VoidRescue(VoidPhantom source){this.source=source;}
    public static AABB occupied(Entity root,Vec3 destination){AABB box=root.getBoundingBox().move(destination.subtract(root.position()));for(var passenger:root.getPassengers()){Vec3 seat=root.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(root)).subtract(root.position()).add(destination);box=box.minmax(passenger.getBoundingBox().move(seat.subtract(passenger.position())));}return box.inflate(.04D,0,.04D);}
    public static boolean clear(ServerLevel level,Entity root,Vec3 destination){var box=occupied(root,destination);return !root.isPassenger()&&root.getPassengers().stream().noneMatch(Entity::isVehicle)&&EndTeleportSafety.loaded(level,box,false)&&level.noBlockCollision(root,box)&&!level.containsAnyLiquid(box)&&level.getEntities(root,box,entity->!root.hasPassenger(entity)&&entity.isAlive()&&entity.isPickable()).isEmpty();}
    public static boolean safe(ServerLevel level,Entity root,Vec3 destination){if(!clear(level,root,destination)||EndTeleportSafety.support(level,root,destination,6).isEmpty()){return false;}var box=occupied(root,destination);for(var block:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))){if(GravityControl.dangerous(level,block)){return false;}}return true;}
    public static boolean corridor(ServerLevel level,Entity root,Vec3 destination){Vec3 offset=destination.subtract(root.position());if(!Double.isFinite(offset.lengthSqr())||offset.lengthSqr()>42*42){return false;}int samples=Math.max(1,(int)Math.ceil(offset.length()*2));for(int sample=1;sample<=samples;sample++){var position=root.position().add(offset.scale((double)sample/samples));var box=occupied(root,position);if(!EndTeleportSafety.loaded(level,box,false)||!level.noBlockCollision(root,box)||level.containsAnyLiquid(box)){return false;}}return true;}
    private boolean family(LivingEntity target){return source.isTame()&&target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==source.level()&&(target==source.getOwner()||target instanceof AbstractPhantom phantom&&phantom.isTame()&&Objects.equals(source.getOwnerReference(),phantom.getOwnerReference()));}
    private boolean group(LivingEntity root){return (root==source||family(root))&&!root.isPassenger()&&root.getPassengers().stream().allMatch(passenger->passenger instanceof LivingEntity living&&family(living)&&!passenger.isVehicle());}
    public boolean move(ServerLevel level,LivingEntity root,Vec3 destination,boolean emergency){
        if(root.level()!=level||!root.isAlive()||MOVING.contains(root.getUUID())||!group(root)||!safe(level,root,destination)||!emergency&&(root!=source||!corridor(level,root,destination))){return false;}var event=EventHooks.onEnderTeleport(root,destination.x,destination.y,destination.z);destination=event.getTarget();if(event.isCanceled()||event.getTargetLevel()!=level||!safe(level,root,destination)||!emergency&&!corridor(level,root,destination)){return false;}
        var states=root.getSelfAndPassengers().map(member->new MoveState(member,member.position(),member.getDeltaMovement(),member.getYRot(),member.getXRot(),member.getVehicle())).toList();Vec3 translation=destination.subtract(root.position());MOVING.add(root.getUUID());
        try{var result=root.teleport(new TeleportTransition(level,destination,Vec3.ZERO,root.getYRot(),root.getXRot(),TeleportTransition.DO_NOTHING));if(result!=root||root.position().distanceToSqr(destination)>.01D||states.stream().anyMatch(state->state.entity().level()!=level||state.entity().getVehicle()!=state.vehicle()||state.entity().position().distanceToSqr(state.position().add(translation))>.01D)||!safe(level,root,root.position())){rollback(level,states);return false;}for(var state:states){var member=state.entity();member.setDeltaMovement(Vec3.ZERO);member.resetFallDistance();member.needsSync=true;if(member instanceof Mob mob){mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(member.getX(),member.getY(),member.getZ(),0);}}if(root instanceof AbstractPhantom phantom){for(var passenger:root.getPassengers()){phantom.positionRider(passenger);}}VoidVfx.emit(source,VoidVfx.ARRIVAL,root,root.position(),Vec3.ZERO,24,2);return true;}finally{MOVING.remove(root.getUUID());}
    }
    private static void rollback(ServerLevel level,List<MoveState> states){for(var state:states){var member=state.entity();member.teleportSetPosition(new PositionMoveRotation(state.position(),state.motion(),state.yaw(),state.pitch()),Set.of());member.needsSync=true;if(member instanceof net.minecraft.server.level.ServerPlayer player&&player.connection!=null){player.connection.teleport(state.position().x,state.position().y,state.position().z,state.yaw(),state.pitch());}}for(var state:states){if(state.vehicle()!=null&&state.entity().getVehicle()!=state.vehicle()){state.entity().startRiding(state.vehicle(),true,false);}if(!(state.entity() instanceof net.minecraft.server.level.ServerPlayer)){level.getChunkSource().sendToTrackingPlayers(state.entity(),net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(state.entity().getId(),PositionMoveRotation.of(state.entity()),Set.of(),state.entity().onGround()));}}}
    private boolean current(ServerLevel level,Snapshot snapshot){return snapshot!=null&&snapshot.dimension().equals(level.dimension())&&level.getGameTime()<snapshot.expires();}
    public boolean arm(ServerLevel level,LivingEntity target){if(!source.enabled()||!source.ready(2)||!family(target)||source.distanceToSqr(target)>56*56){return false;}var vehicle=target.getRootVehicle();if(!(vehicle instanceof LivingEntity root)||!group(root)||!safe(level,root,root.position())){return false;}anchor=new Snapshot(level.dimension(),root.position(),level.getGameTime()+VoidPhantom.tuning("anchorLifetime"));ward=target.getUUID();nextMarker=0;return true;}
    public boolean anchored(){return anchor!=null;}
    public UUID ward(){return ward;}
    public void clearAnchor(){anchor=null;ward=null;nextMarker=0;}
    public void reset(){clearAnchor();safe=null;nextSelf=0;}
    private boolean falling(ServerLevel level,LivingEntity target){if(!target.isAlive()||target.isRemoved()||target.onGround()||target.isInWater()||target.isInLava()||target.isFallFlying()||target.getDeltaMovement().y>=-.25D||target instanceof Player player&&(player.isSpectator()||player.getAbilities().flying)){return false;}var floor=EndTeleportSafety.support(level,target,target.position(),48);if(target.getY()<level.getMinY()+12&&floor.isEmpty()){return true;}if(target.getRootVehicle().isNoGravity()){return false;}return floor.isEmpty()||target.fallDistance>8&&target.fallDistance-3>=target.getHealth();}
    public boolean rescueWard(ServerLevel level){
        if(!source.enabled()||!source.ready(2)||!current(level,anchor)||ward==null||!(level.getEntity(ward) instanceof LivingEntity target)||!family(target)){clearAnchor();return false;}
        Vec3 distance=target.position().subtract(source.position());if(distance.x*distance.x+distance.z*distance.z>56*56||Math.abs(distance.y)>384||!falling(level,target)){return false;}var vehicle=target.getRootVehicle();if(!(vehicle instanceof LivingEntity root)||!group(root)||!safe(level,root,anchor.position())){return false;}
        RECEIPTS.entrySet().removeIf(entry->entry.getKey().isRemoved()||!entry.getKey().isAlive()||entry.getValue()<=entry.getKey().level().getGameTime());if(RECEIPTS.getOrDefault(target,Long.MIN_VALUE)>level.getGameTime()||RECEIPTS.size()>=256){return false;}Vec3 previous=root.position();if(!move(level,root,anchor.position(),true)){return false;}RECEIPTS.put(target,level.getGameTime()+100);source.consume(2);source.action("anchor",20);VoidVfx.emit(source,VoidVfx.ANCHOR,null,anchor.position(),Vec3.ZERO,1,1.5F);VoidVfx.emit(source,VoidVfx.COLLAPSE,null,anchor.position(),Vec3.ZERO,20,1.5F);Vec3 direction=root.position().subtract(previous);if(direction.lengthSqr()<=128*128){VoidVfx.emit(source,VoidVfx.RESCUE,null,previous,direction,24,2);}clearAnchor();return true;
    }
    public void tick(ServerLevel level){
        if(!source.isAlive()||source.isRemoved()||source.level()!=level){reset();return;}long now=level.getGameTime();if(safe!=null&&!current(level,safe)){safe=null;}if(source.tickCount%10==0&&safe(level,source,source.position())){safe=new Snapshot(level.dimension(),source.position(),now+6000);}
        if((now>=nextSelf||source.getY()<level.getMinY()-32)&&current(level,safe)&&falling(level,source)&&source.getY()<level.getMinY()+12&&move(level,source,safe.position(),true)){nextSelf=now+100;}
        if(!source.enabled()){clearAnchor();return;}if(anchor!=null&&(!current(level,anchor)||ward==null||!(level.getEntity(ward) instanceof LivingEntity target)||!family(target))){clearAnchor();}if(anchor==null&&source.tickCount%20==0){arm(level,source.getOwner());}if(anchor!=null){rescueWard(level);if(anchor!=null&&now>=nextMarker){VoidVfx.emit(source,VoidVfx.ANCHOR,null,anchor.position(),Vec3.ZERO,65,1.5F);nextMarker=now+60;}}
    }
}
