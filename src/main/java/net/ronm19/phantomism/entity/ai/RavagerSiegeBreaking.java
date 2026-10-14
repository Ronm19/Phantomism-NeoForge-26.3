package net.ronm19.phantomism.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.RavagerPhantom;
import net.ronm19.phantomism.util.ModTags;

public final class RavagerSiegeBreaking {
    private RavagerSiegeBreaking() {}

    public static double clearForwardSlice(ServerLevel level, RavagerPhantom phantom, Vec3 direction, double momentum, boolean siege) {
        if (!phantom.isChargeMoving() || phantom.isBaby() || !phantom.isTame() || !(phantom.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.level() != level || !level.getGameRules().get(GameRules.MOB_GRIEFING)
                || !EventHooks.canEntityGrief(level, phantom) || !owner.getAbilities().mayBuild
                || siege && phantom.getControllingPassenger() != owner) { return momentum; }
        boolean foliage = Config.RAVAGER_BULLDOZER.get();
        boolean breach = siege && Config.RAVAGER_SIEGE_BREAKING.get();
        if (!foliage && !breach) { return momentum; }
        boolean alongX = Math.abs(direction.x) > Math.abs(direction.z);
        Vec3 front = phantom.position().add(direction.scale(phantom.getBbWidth() * 0.5D + momentum));
        BlockPos center = BlockPos.containing(front);
        for (int height = 0; height < 3; height++) {
            for (int side = -1; side <= 1; side++) {
                BlockPos position = alongX ? center.offset(0, height, side) : center.offset(side, height, 0);
                if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) { return momentum; }
                BlockState state = level.getBlockState(position);
                if (state.isAir()) { continue; }
                float hardness = state.getDestroySpeed(level, position);
                boolean naturalFoliage = foliage && isNaturalFoliage(state);
                boolean approvedBreach = breach && state.is(ModTags.Blocks.RAVAGER_SIEGE_BREAKABLE);
                if ((!naturalFoliage && !approvedBreach) || state.hasBlockEntity() || level.getBlockEntity(position) != null
                        || !state.getFluidState().isEmpty() || hardness < 0.0F || hardness > 3.5F
                        || state.is(ModTags.Blocks.RAVAGER_PROTECTED_BLOCKS) || !owner.hasCorrectToolForDrops(state)
                        || owner.blockActionRestricted(level, position, owner.gameMode.getGameModeForPlayer())
                        || !level.mayInteract(owner, position) || !CommonHooks.canEntityDestroy(level, position, phantom)) { continue; }
                double minimum = naturalFoliage ? 0.12D : hardness <= 0.6F ? 0.3D : hardness <= 1.0F ? 0.55D : hardness <= 2.0F ? 0.85D : 1.1D;
                double cost = naturalFoliage ? 0.015D : hardness <= 0.6F ? 0.06D : hardness <= 1.0F ? 0.14D : hardness <= 2.0F ? 0.35D : 0.65D;
                if (momentum + 1.0E-6D < minimum) { continue; }
                if (CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, state).isCanceled()
                        || level.getBlockState(position) != state || !phantom.isCharging()) { continue; }
                if (level.destroyBlock(position, false, phantom)) {
                    var held = owner.getMainHandItem();
                    var tool = held.copy();
                    if (!naturalFoliage && state.requiresCorrectToolForDrops()) { held.mineBlock(level, state, position, owner); }
                    state.getBlock().playerDestroy(level, owner, position, state, null, tool);
                    momentum = Math.max(0.0D, momentum - cost);
                }
            }
        }
        return momentum;
    }

    public static boolean isNaturalFoliage(BlockState state) {
        return state.is(ModTags.Blocks.RAVAGER_BULLDOZER_BREAKABLE)
                && (!(state.getBlock() instanceof LeavesBlock) || !state.getValue(LeavesBlock.PERSISTENT));
    }
}
