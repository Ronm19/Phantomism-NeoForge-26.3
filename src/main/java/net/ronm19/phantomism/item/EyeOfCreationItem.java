package net.ronm19.phantomism.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.CreatorPhantom;
import net.ronm19.phantomism.progression.CreatorBonds;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class EyeOfCreationItem extends Item {
    public EyeOfCreationItem(Properties properties){super(properties);}
    public static boolean completedEnd(ServerPlayer player){var advancement=player.level().getServer().getAdvancements().get(Identifier.withDefaultNamespace("end/kill_dragon"));return advancement!=null&&player.getAdvancements().getOrStartProgress(advancement).isDone();}
    private static InteractionResult fail(ServerPlayer player,String key){player.sendOverlayMessage(Component.translatable("message.phantomism.creator."+key));return InteractionResult.FAIL;}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide()){return InteractionResult.SUCCESS;}
        if(!(context.getLevel() instanceof ServerLevel level)||!(context.getPlayer() instanceof ServerPlayer player)||player.isSpectator()){return InteractionResult.FAIL;}
        if(net.ronm19.phantomism.CreatorCertification.acquisitionLocked()){return fail(player,"acquisition_locked");}
        var bonds=CreatorBonds.get(level.getServer());
        if(player.isShiftKeyDown()){
            if(!level.dimension().equals(Level.END)||!net.ronm19.phantomism.progression.MasterOfPhantoms.get(level.getServer()).complete(level,player.getUUID())){return fail(player,"recognition_failed");}
            BlockPos anchor=context.getClickedPos();
            if(!level.hasChunkAt(anchor)||!level.getBlockState(anchor).isFaceSturdy(level,anchor,Direction.UP)){return fail(player,"solid_block");}
            CreatorPhantom manifestation=net.ronm19.phantomism.progression.CreatorSummoning.manifest(player,anchor);
            if(manifestation==null){return fail(player,"manifest_failed");}
            player.sendOverlayMessage(Component.translatable("message.phantomism.creator.manifested"));return InteractionResult.SUCCESS_SERVER;
        }
        if(!player.isCreative()&&(!level.dimension().equals(Level.END)||!completedEnd(player)||!bonds.crafted(player.getUUID()))){return fail(player,"locked");}
        if(bonds.active(player.getUUID())!=null){return fail(player,"already_bonded");}
        BlockPos clicked=context.getClickedPos();
        if(!level.hasChunkAt(clicked)||!level.getBlockState(clicked).isFaceSturdy(level,clicked,Direction.UP)){return fail(player,"solid_block");}
        CreatorPhantom creator=net.ronm19.phantomism.progression.CreatorSummoning.summon(player,clicked);
        if(creator==null){return fail(player,"no_space");}
        context.getItemInHand().shrink(1);
        player.sendOverlayMessage(Component.translatable("message.phantomism.creator.bonded"));
        return InteractionResult.SUCCESS_SERVER;
    }
}
