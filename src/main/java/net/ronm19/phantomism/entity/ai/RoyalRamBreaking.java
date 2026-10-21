package net.ronm19.phantomism.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.entity.PhantomKing;
import net.ronm19.phantomism.util.ModTags;

public final class RoyalRamBreaking {
    public static final int BLOCK_BUDGET = 8;
    private RoyalRamBreaking() {}
    private static boolean allowed(ServerLevel level, PhantomKing source) { return source.enabled() && source.momentum() >= 2 && source.isTame() && source.getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level && owner.getAbilities().mayBuild && level.getGameRules().get(GameRules.MOB_GRIEFING) && EventHooks.canEntityGrief(level, source); }
    private static boolean material(BlockState state) { return (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) && !state.hasBlockEntity() && !state.is(ModTags.Blocks.RAVAGER_PROTECTED_BLOCKS) && state.getFluidState().isEmpty(); }
    public static boolean canAttempt(ServerLevel level, PhantomKing source, Vec3 direction) {
        if (!allowed(level, source) || !Double.isFinite(direction.lengthSqr())) { return false; }
        var box = source.getBoundingBox().expandTowards(direction.normalize().scale(2));
        if (!source.isLoadedFlightBox(box)) { return false; }
        return BlockPos.betweenClosedStream(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)).anyMatch(position -> material(level.getBlockState(position)));
    }
    public static int clearOnce(ServerLevel level, PhantomKing source, Vec3 direction) {
        if (!allowed(level, source) || !(source.getOwner() instanceof ServerPlayer owner) || !Double.isFinite(direction.lengthSqr()) || !source.claimRamBreaking()) { return 0; }
        var box = source.getBoundingBox().expandTowards(direction.normalize().scale(2)); if (!source.isLoadedFlightBox(box)) { return 0; }
        int broken = 0;
        for (BlockPos point : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (broken >= BLOCK_BUDGET) { break; }
            BlockPos position = point.immutable(); BlockState state = level.getBlockState(position); float hardness = state.getDestroySpeed(level, position);
            if (!level.getWorldBorder().isWithinBounds(position) || !material(state) || level.getBlockEntity(position) != null || hardness < 0 || hardness > 2.1F
                    || owner.blockActionRestricted(level, position, owner.gameMode.getGameModeForPlayer()) || !level.mayInteract(owner, position) || !owner.hasCorrectToolForDrops(state) || !CommonHooks.canEntityDestroy(level, position, source)) { continue; }
            if (CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, state).isCanceled() || level.getBlockState(position) != state) { continue; }
            if (level.destroyBlock(position, false, source)) { state.getBlock().playerDestroy(level, owner, position, state, null, owner.getMainHandItem().copy()); broken++; }
        }
        return broken;
    }
}
