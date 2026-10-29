package net.ronm19.phantomism.progression;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.CreatorCertification;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.CreatorPhantom;
import net.ronm19.phantomism.entity.ai.GravityControl;
import net.ronm19.phantomism.registry.PhantomismContent;
public final class CreatorSummoning {
    private CreatorSummoning(){}
    public static CreatorPhantom summon(ServerPlayer player,BlockPos anchor){
        return create(player,anchor,true);
    }
    public static CreatorPhantom manifest(ServerPlayer player,BlockPos anchor){
        if(!player.level().dimension().equals(net.minecraft.world.level.Level.END)||!player.level().hasChunkAt(anchor)
                ||!player.level().getBlockState(anchor).isFaceSturdy(player.level(),anchor,net.minecraft.core.Direction.UP)
                ||!MasterOfPhantoms.get(player.level().getServer()).complete(player.level(),player.getUUID())){return null;}
        return create(player,anchor,false);
    }
    private static CreatorPhantom create(ServerPlayer player,BlockPos anchor,boolean bonded){
        if(CreatorCertification.acquisitionLocked()||!player.isAlive()||player.isSpectator()){return null;}
        var level=player.level();var bonds=CreatorBonds.get(level.getServer());if(bonds.active(player.getUUID())!=null||bonds.manifestation(player.getUUID())!=null){return null;}
        CreatorPhantom creator=PhantomismContent.CREATOR_PHANTOM.get().create(level,EntitySpawnReason.TRIGGERED);if(creator==null){return null;}Vec3 safe=null;
        for(int height=2;height<=5&&safe==null;height++){for(int dx=-3;dx<=3&&safe==null;dx++){for(int dz=-3;dz<=3&&safe==null;dz++){
            Vec3 candidate=Vec3.atCenterOf(anchor.offset(dx,height,dz));creator.setPos(candidate);var bounds=creator.getBoundingBox().inflate(.15D);
            if(candidate.y>level.getMinY()+2&&bounds.maxY<level.getMaxY()&&creator.isLoadedFlightBox(bounds)&&level.getWorldBorder().isWithinBounds(bounds)&&level.noCollision(creator,bounds)&&!level.containsAnyLiquid(bounds)&&!GravityControl.dangerous(level,BlockPos.containing(candidate))&&!GravityControl.dangerous(level,BlockPos.containing(candidate).below())){safe=candidate;}
        }}}
        if(safe==null){creator.discard();return null;}
        creator.setPos(safe);creator.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(safe)),EntitySpawnReason.TRIGGERED,null);creator.setAge(0);creator.setPersistenceRequired();
        if(bonded){creator.tame(player);creator.setOrder(AbstractPhantom.Order.FOLLOW);if(!bonds.claim(player.getUUID(),creator.getUUID())){creator.discard();return null;}}
        else{creator.manifestedFor(player.getUUID());creator.setNoAi(true);creator.setOrder(AbstractPhantom.Order.STAY);if(!bonds.reserve(player.getUUID(),creator.getUUID())){creator.discard();return null;}}
        if(!level.addFreshEntity(creator)){bonds.release(player.getUUID(),creator.getUUID());bonds.releaseManifestation(player.getUUID(),creator.getUUID());creator.discard();return null;}
        if(bonded){CreatorRevelation.reveal(player);}return creator;
    }
    public static net.minecraft.world.InteractionResult bind(ServerPlayer player,CreatorPhantom creator,net.minecraft.world.InteractionHand hand){
        var eye=player.getItemInHand(hand);var level=player.level();var bonds=CreatorBonds.get(level.getServer());
        boolean valid=!CreatorCertification.acquisitionLocked()&&player.isAlive()&&!player.isSpectator()
                &&creator.level()==level&&creator.isAlive()&&!creator.isRemoved()&&!creator.isBaby()&&!creator.isRecovering()
                &&!creator.isTame()&&creator.getOwnerReference()==null&&player.distanceToSqr(creator)<=64
                &&eye.is(PhantomismContent.EYE_OF_CREATION.get())&&bonds.active(player.getUUID())==null
                &&MasterOfPhantoms.get(level.getServer()).complete(level,player.getUUID());
        UUID pending=bonds.manifestation(player.getUUID());
        valid&=(pending==null||pending.equals(creator.getUUID()))
                &&(creator.manifestedFor()==null||creator.manifestedFor().equals(player.getUUID())&&creator.getUUID().equals(pending));
        if(!valid){player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.phantomism.creator.recognition_failed"));return net.minecraft.world.InteractionResult.FAIL;}
        if(!bonds.claim(player.getUUID(),creator.getUUID())){return net.minecraft.world.InteractionResult.FAIL;}
        creator.tame(player);creator.clearCombatTarget(true);creator.setNoAi(false);creator.setOrder(AbstractPhantom.Order.FOLLOW);creator.setPersistenceRequired();
        bonds.releaseManifestation(player.getUUID(),creator.getUUID());creator.manifestedFor(null);
        if(!player.isCreative()){eye.shrink(1);}CreatorRevelation.reveal(player);level.broadcastEntityEvent(creator,(byte)7);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.phantomism.creator.bonded"));return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
    }
}
