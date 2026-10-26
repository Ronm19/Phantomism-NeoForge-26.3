package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.AutumnPhantom;
import net.ronm19.phantomism.vfx.AutumnVfx;

public final class AutumnHarvest {
    public enum Mode { DISABLED, CROPS, VEGETATION, WOOD }
    private static final Map<Block, Item> SEEDS = Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS, Blocks.CARROTS, Items.CARROT, Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
    private static final Set<Block> PLANTS = Set.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY);
    private static final List<BlockPos> OFFSETS = offsets();
    private final AutumnPhantom source;
    private final List<ItemStack> pending = new ArrayList<>();
    private Mode mode = Mode.DISABLED;
    private GlobalPos area;
    private BlockState approvedWood;
    private BlockPos target;
    private long nextScan;
    private int cursor;
    public AutumnHarvest(AutumnPhantom source) { this.source = source; }
    public Mode mode() { return source.level().isClientSide() ? Mode.values()[Math.clamp(source.harvestMode(), 0, 3)] : mode; }
    public GlobalPos area() { return area; }
    public int pendingStacks() { return pending.size(); }
    private static List<BlockPos> offsets() { var result = new ArrayList<BlockPos>(); for (int across = -8; across <= 8; across++) { for (int depth = -8; depth <= 8; depth++) { for (int height = -3; height <= 3; height++) { if (across * across + depth * depth <= 64) { result.add(new BlockPos(across, height, depth)); } } } } result.sort(Comparator.comparingInt(position -> position.getX() * position.getX() + position.getY() * position.getY() + position.getZ() * position.getZ())); return List.copyOf(result); }
    public boolean select(Mode selection, Vec3 point) {
        if (!(source.level() instanceof ServerLevel level) || !(source.getOwner() instanceof ServerPlayer owner) || !source.isOwnedBy(owner) || owner.level() != level || !owner.isAlive()) { return false; }
        if (selection == Mode.DISABLED) { disable(); return true; }
        if (point == null || !Double.isFinite(point.lengthSqr()) || source.distanceToSqr(point) > 32 * 32 || owner.distanceToSqr(point) > 32 * 32) { return false; }
        BlockPos position = BlockPos.containing(point); if (!permission(level, owner, position, false)) { return false; }
        BlockState state = level.getBlockState(position);
        if (selection == Mode.WOOD && !state.is(BlockTags.LOGS)) { return false; }
        area = GlobalPos.of(level.dimension(), position); approvedWood = selection == Mode.WOOD ? state : null; mode = selection; source.harvestMode(mode); target = null; cursor = 0; nextScan = 0;
        source.setTask(PhantomAssignments.Task.HARVEST); return true;
    }
    public void disable() { mode = Mode.DISABLED; source.harvestMode(mode); area = null; approvedWood = null; target = null; }
    public boolean permission(ServerLevel level, ServerPlayer owner, BlockPos position, boolean breaking) {
        if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position) || !owner.getAbilities().mayBuild || owner.isSpectator() || owner.level() != level
                || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, source) || owner.blockActionRestricted(level, position, owner.gameMode.getGameModeForPlayer())
                || !level.mayInteract(owner, position) || level.getBlockEntity(position) != null || !CommonHooks.canEntityDestroy(level, position, source)) { return false; }
        return !breaking || !CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, level.getBlockState(position)).isCanceled();
    }
    public boolean eligible(ServerLevel level, ServerPlayer owner, BlockPos position, boolean breaking) {
        if (area == null || area.dimension() != level.dimension() || Vec3.atCenterOf(area.pos()).distanceToSqr(Vec3.atCenterOf(position)) > 81 || !level.hasChunkAt(position)) { return false; }
        BlockState state = level.getBlockState(position);
        boolean supported = mode == Mode.WOOD ? position.equals(area.pos()) && approvedWood != null && state.equals(approvedWood) && state.is(BlockTags.LOGS)
                : SEEDS.containsKey(state.getBlock()) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) || mode == Mode.VEGETATION && PLANTS.contains(state.getBlock());
        return supported && permission(level, owner, position, breaking);
    }
    private void scan(ServerLevel level, ServerPlayer owner) {
        if (level.getGameTime() < nextScan || area == null || area.dimension() != level.dimension()) { return; }
        nextScan = level.getGameTime() + 40;
        if (mode == Mode.WOOD) { target = eligible(level, owner, area.pos(), true) ? area.pos() : null; return; }
        target = null;
        for (int checked = 0; checked < 64; checked++) {
            BlockPos position = area.pos().offset(OFFSETS.get(cursor++ % OFFSETS.size()));
            if (eligible(level, owner, position, true) && source.distanceToSqr(Vec3.atCenterOf(position)) <= 24 * 24
                    && source.line(source.getEyePosition(), Vec3.atCenterOf(position).add(0, .5D, 0))) { target = position; break; }
        }
    }
    public void sense(ServerLevel level) {
        if (mode == Mode.DISABLED || !(source.getOwner() instanceof ServerPlayer owner) || !source.isOwnedBy(owner) || source.isRemoved() || !source.isAlive()) { return; }
        scan(level, owner);
        if (target != null && eligible(level, owner, target, true)) { AutumnVfx.sense(source, owner, Vec3.atCenterOf(target)); }
    }
    public boolean tick(ServerLevel level, ServerPlayer owner) {
        if (mode == Mode.DISABLED || area == null || area.dimension() != level.dimension() || !source.enabled() || source.isVehicle() || source.rushTicks() > 0 || source.galeTicks() > 0
                || source.getOrder() == AbstractPhantom.Order.STAY || source.getTarget() != null || source.threatened(level) || source.distanceToSqr(Vec3.atCenterOf(area.pos())) > 24 * 24 || owner.distanceToSqr(Vec3.atCenterOf(area.pos())) > 32 * 32) { return false; }
        if (!pending.isEmpty() || !source.getAssignments().cargo().isEmpty()) {
            if (source.getAssignments().cargo().isEmpty() && !pending.isEmpty()) { source.getAssignments().offerHarvest(pending.getFirst()); if (pending.getFirst().isEmpty()) { pending.removeFirst(); } }
            return source.getAssignments().deliverHarvest(level, owner);
        }
        if (mode == Mode.WOOD && approvedWood == null) { disable(); return false; }
        if (target != null && !eligible(level, owner, target, false)) { target = null; }
        if (target == null) { scan(level, owner); }
        if (target == null) { return false; }
        Vec3 point = Vec3.atCenterOf(target);
        if (source.distanceToSqr(point) > 6.25D) { if (!source.line(source.getEyePosition(), point.add(0, .5D, 0))) { target = null; return false; } source.flyToAssignment(point.add(0, 1.2D, 0), .9D); return true; }
        harvest(level, owner, target); target = null; nextScan = level.getGameTime() + 40; return false;
    }
    public boolean harvest(ServerLevel level, ServerPlayer owner, BlockPos position) {
        if (!source.isOwnedBy(owner) || source.getTask() != PhantomAssignments.Task.HARVEST || !source.enabled() || source.getTarget() != null || source.isVehicle() || !pending.isEmpty()
                || !source.getAssignments().cargo().isEmpty() || owner.distanceToSqr(Vec3.atCenterOf(position)) > 32 * 32 || source.distanceToSqr(Vec3.atCenterOf(position)) > 6.25D || !eligible(level, owner, position, true)) { return false; }
        BlockState state = level.getBlockState(position); Item seed = SEEDS.get(state.getBlock());
        var drops = new ArrayList<>(Block.getDrops(state, level, position, null, source, ItemStack.EMPTY));
        if (drops.size() > 9 || !eligible(level, owner, position, false) || !level.getBlockState(position).equals(state) || !source.line(source.getEyePosition(), Vec3.atCenterOf(position).add(0, .5D, 0))) { return false; }
        ItemStack planting = ItemStack.EMPTY, seedStack = ItemStack.EMPTY;
        if (seed != null) { for (var stack : drops) { if (stack.is(seed) && !stack.isEmpty()) { seedStack = stack; planting = stack.split(1); break; } } }
        if (!level.destroyBlock(position, false, source)) { return false; }
        if (!planting.isEmpty() && state.getBlock() instanceof CropBlock crop && level.getBlockState(position).isAir() && permission(level, owner, position, true)) {
            BlockState planted = crop.getStateForAge(0);
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, position);
            if (!planted.canSurvive(level, position) || !level.setBlock(position, planted, 3)) { seedStack.grow(planting.getCount()); }
            else if (EventHooks.onBlockPlace(owner, snapshot, net.minecraft.core.Direction.UP)) { snapshot.restore(3); seedStack.grow(planting.getCount()); }
        } else if (!planting.isEmpty()) { seedStack.grow(planting.getCount()); }
        for (var stack : drops) { if (!stack.isEmpty()) { pending.add(stack.copy()); } }
        if (!pending.isEmpty()) { source.getAssignments().offerHarvest(pending.getFirst()); if (pending.getFirst().isEmpty()) { pending.removeFirst(); } }
        if (mode == Mode.WOOD) { approvedWood = null; }
        source.harvesting(); AutumnVfx.emit(source, AutumnVfx.HARVEST, null, Vec3.atCenterOf(position), Vec3.ZERO, 24, 2); return true;
    }
    public void drop(ServerLevel level) { for (var stack : pending) { source.spawnAtLocation(level, stack); } pending.clear(); }
    public void save(ValueOutput output) { output.putString("AutumnHarvestMode", mode.name()); output.storeNullable("AutumnHarvestArea", GlobalPos.CODEC, area); output.storeNullable("AutumnApprovedWood", BlockState.CODEC, approvedWood); output.store("AutumnHarvestPending", ItemStack.CODEC.listOf(), pending); }
    public void load(ValueInput input) { disable(); try { mode = Mode.valueOf(input.getStringOr("AutumnHarvestMode", "DISABLED")); } catch (IllegalArgumentException ignored) { mode = Mode.DISABLED; } area = input.read("AutumnHarvestArea", GlobalPos.CODEC).orElse(null); approvedWood = input.read("AutumnApprovedWood", BlockState.CODEC).orElse(null); pending.clear(); pending.addAll(input.read("AutumnHarvestPending", ItemStack.CODEC.listOf()).orElse(List.of()).stream().filter(stack -> !stack.isEmpty()).limit(9).map(ItemStack::copy).toList()); if (area == null) { disable(); } source.harvestMode(mode); cursor = 0; nextScan = 0; }
}
