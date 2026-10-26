package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.GolemPhantom;
import net.ronm19.phantomism.network.PhantomismNetwork.Action;
import net.ronm19.phantomism.vfx.TitaniumVfx;

public final class GolemExcavation {
    private final GolemPhantom source;
    private final SimpleContainer cargo = new SimpleContainer(27);
    private GlobalPos corner, minimum, maximum, destination;
    private UUID author;
    private int cursor, progress, stalled, ramBudget;
    private boolean obsidian, deliver, ramAllowed;
    private String status = "idle";
    public GolemExcavation(GolemPhantom source) { this.source = source; }
    public SimpleContainer cargo() { return cargo; }
    public boolean active() { return minimum != null; }
    public boolean busy() { return active() || deliver; }
    public String status() { return status; }
    public int cursor() { return cursor; }
    public boolean obsidianAllowed() { return obsidian; }
    public void beginRam() { ramBudget = ramAllowed ? 8 : 0; }
    public boolean ramAuthorized() { return ramAllowed && source.isTame() && source.getOwner() instanceof ServerPlayer; }
    public void cancel() { minimum = null; maximum = null; corner = null; cursor = 0; progress = 0; stalled = 0; deliver = false; status = "cancelled"; }
    public boolean command(ServerPlayer player, Action action) {
        if (!authorized(player)) { return false; }
        if (author != null && !author.equals(player.getUUID())) { cancel(); destination = null; } author = player.getUUID();
        switch (action) {
            case GOLEM_OBSIDIAN -> obsidian = !obsidian;
            case GOLEM_RAM_BREAK -> { ramAllowed = !ramAllowed; ramBudget = 8; }
            case GOLEM_CANCEL -> cancel();
            case GOLEM_DELIVER -> deliver = true;
            case GOLEM_OWNER -> { destination = null; deliver = true; }
            case GOLEM_STATUS -> { }
            default -> { return false; }
        }
        player.sendOverlayMessage(Component.translatable("message.phantomism.golem.status", Component.translatable("work.phantomism.golem." + status), cargo.getItems().stream().filter(stack -> !stack.isEmpty()).count(), obsidian, ramAllowed)); return true;
    }
    public boolean select(ServerPlayer player, Action action, BlockPos position) {
        if (!authorized(player) || !source.level().hasChunkAt(position) || player.distanceToSqr(Vec3.atCenterOf(position)) > 64) { return false; }
        ServerLevel level = (ServerLevel) source.level();
        if (!permission(level, player, position) || !level.getWorldBorder().isWithinBounds(position)) { return false; }
        GlobalPos point = GlobalPos.of(level.dimension(), position.immutable());
        if (action == Action.GOLEM_CHEST) {
            var access = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, position, new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            NeoForge.EVENT_BUS.post(access); if (access.isCanceled() || access.getUseBlock().isFalse()) { return false; }
            if (level.getBlockEntity(position) == null || level.getCapability(Capabilities.Item.BLOCK, position, Direction.UP) == null) { return false; }
            var data = level.getBlockEntity(position).getPersistentData();
            String existing = data.getStringOr("PhantomismGolemOwner", "");
            if (!existing.isEmpty() && !existing.equals(player.getUUID().toString())) { return false; }
            data.putString("PhantomismGolemOwner", player.getUUID().toString()); level.getBlockEntity(position).setChanged(); destination = point; author = player.getUUID(); return true;
        }
        if (action == Action.GOLEM_AREA_A) { corner = point; status = "corner_set"; return true; }
        GlobalPos first = action == Action.GOLEM_PRECISION ? point : corner;
        if (first == null || first.dimension() != level.dimension() || action != Action.GOLEM_PRECISION && action != Action.GOLEM_AREA_B) { return false; }
        BlockPos low = new BlockPos(Math.min(position.getX(), first.pos().getX()), Math.min(position.getY(), first.pos().getY()), Math.min(position.getZ(), first.pos().getZ()));
        BlockPos high = new BlockPos(Math.max(position.getX(), first.pos().getX()), Math.max(position.getY(), first.pos().getY()), Math.max(position.getZ(), first.pos().getZ()));
        long volume = (long) (high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) * (high.getZ() - low.getZ() + 1);
        if (volume > Config.GOLEM_MAX_VOLUME.get() || high.getX() - low.getX() > 15 || high.getY() - low.getY() > 15 || high.getZ() - low.getZ() > 15 || source.distanceToSqr(Vec3.atCenterOf(low)) > 80 * 80 || source.distanceToSqr(Vec3.atCenterOf(high)) > 80 * 80) { return false; }
        if (volume == 1 && !material(level.getBlockState(position), true)) { return false; }
        source.setTask(PhantomAssignments.Task.NONE); source.clearCombatTarget(false); source.setOrder(net.ronm19.phantomism.entity.AbstractPhantom.Order.FOLLOW);
        minimum = GlobalPos.of(level.dimension(), low); maximum = GlobalPos.of(level.dimension(), high); author = player.getUUID(); cursor = 0; progress = 0; stalled = 0; deliver = false; status = "mining"; return true;
    }
    private boolean authorized(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && source.isOwnedBy(player) && !source.isBaby() && source.isAlive() && player.level() == source.level() && source.distanceToSqr(player) <= 80 * 80; }
    private boolean permission(ServerLevel level, ServerPlayer owner, BlockPos position) {
        return owner.getAbilities().mayBuild && !owner.blockActionRestricted(level, position, owner.gameMode.getGameModeForPlayer()) && level.mayInteract(owner, position);
    }
    public static boolean ore(BlockState state) { return state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES); }
    private boolean material(BlockState state, boolean precision) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty() && (ore(state) || obsidian && state.is(Blocks.OBSIDIAN)
                || precision && (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF) || state.is(Blocks.NETHERRACK) || state.is(Blocks.END_STONE)));
    }
    public boolean safeBlock(ServerLevel level, BlockPos position, ServerPlayer owner, boolean precision) {
        if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position) || !permission(level, owner, position) || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, source) || !CommonHooks.canEntityDestroy(level, position, source)) { return false; }
        BlockState state = level.getBlockState(position); float hardness = state.getDestroySpeed(level, position);
        if (!material(state, precision) || level.getBlockEntity(position) != null || hardness < 0 || hardness > 50 || level.isCloseToVillage(position, 1)) { return false; }
        for (BlockPos near : BlockPos.betweenClosed(position.offset(-1, -1, -1), position.offset(1, 1, 1))) { if (!level.hasChunkAt(near) || !level.getFluidState(near).isEmpty() || level.getBlockState(near).is(Blocks.NETHER_PORTAL) || GravityControl.dangerous(level, near)) { return false; } }
        return true;
    }
    private BlockPos current() {
        int width = maximum.pos().getX() - minimum.pos().getX() + 1, depth = maximum.pos().getZ() - minimum.pos().getZ() + 1;
        return new BlockPos(minimum.pos().getX() + cursor % width, maximum.pos().getY() - cursor / (width * depth), minimum.pos().getZ() + cursor / width % depth);
    }
    private int volume() { return (maximum.pos().getX() - minimum.pos().getX() + 1) * (maximum.pos().getY() - minimum.pos().getY() + 1) * (maximum.pos().getZ() - minimum.pos().getZ() + 1); }
    public void tick(ServerLevel level) {
        if (!source.enabled() || source.isVehicle() || source.getOwner() == null || !(source.getOwner() instanceof ServerPlayer owner) || author != null && !author.equals(owner.getUUID())) { return; }
        if (deliver) { delivery(level, owner); return; }
        if (!active()) { return; }
        if (minimum.dimension() != level.dimension() || maximum == null || maximum.dimension() != level.dimension()) { status = "dimension_paused"; return; }
        if (cursor >= volume()) { minimum = null; maximum = null; status = "complete"; deliver = !cargo.isEmpty(); return; }
        BlockPos position = current(); if (!level.hasChunkAt(position)) { status = "unloaded"; return; }
        boolean precision = volume() == 1;
        if (!material(level.getBlockState(position), precision)) { cursor++; progress = 0; stalled = 0; return; }
        if (!safeBlock(level, position, owner, precision)) { status = "safety_paused"; return; }
        if (source.getBoundingBox().distanceToSqr(Vec3.atCenterOf(position)) > 16) {
            if (++stalled > 400) { status = "unreachable"; return; }
            if (source.tickCount % 10 == 0) { approach(level, position); } return;
        }
        if (level.clip(new ClipContext(source.getEyePosition(), Vec3.atCenterOf(position), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getBlockPos().equals(position)) {
            status = "mining"; int delay = level.getBlockState(position).is(Blocks.OBSIDIAN) ? 100 : Config.GOLEM_WORK_TICKS.get();
            if (++progress == 1) { source.action(level.getBlockState(position).is(Blocks.OBSIDIAN) ? "obsidian" : "excavate", delay); }
            if (progress >= delay) { if (breakBlock(level, owner, position, precision)) { cursor++; progress = 0; stalled = 0; } else if (status.equals("cargo_full")) { deliver = true; } else { progress = 0; status = "protected"; } }
        } else { progress = 0; if (source.tickCount % 10 == 0) { approach(level, position); } }
    }
    private void approach(ServerLevel level, BlockPos position) {
        for (Direction face : Direction.values()) { Vec3 approach = Vec3.atCenterOf(position).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(3)).add(0, .7D, 0); AABB box = source.getBoundingBox().move(approach.subtract(source.position())); if (source.isLoadedFlightBox(box) && level.noBlockCollision(source, box) && !level.containsAnyLiquid(box)) { source.flyToAssignment(approach, .9D); return; } }
        status = "unreachable";
    }
    public boolean breakBlock(ServerLevel level, ServerPlayer owner, BlockPos position, boolean precision) {
        if (!safeBlock(level, position, owner, precision)) { return false; }
        BlockState state = level.getBlockState(position);
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) { status = "protected"; return false; }
        if (CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, state).isCanceled() || level.getBlockState(position) != state) { return false; }
        List<ItemEntity> items = new ArrayList<>(); for (ItemStack stack : Block.getDrops(state, level, position, null, source, tool)) { items.add(new ItemEntity(level, position.getX() + .5D, position.getY() + .5D, position.getZ() + .5D, stack.copy())); }
        BlockDropsEvent drops = new BlockDropsEvent(level, position, state, null, items, source, tool); NeoForge.EVENT_BUS.post(drops);
        if (level.getBlockState(position) != state || !safeBlock(level, position, owner, precision)) { return false; }
        var storage = VanillaContainerWrapper.of(cargo);
        try (var transaction = Transaction.openRoot()) {
            if (!drops.isCanceled()) { for (ItemEntity item : drops.getDrops()) { ItemStack stack = item.getItem(); if (!stack.isEmpty() && storage.insert(ItemResource.of(stack), stack.getCount(), transaction) != stack.getCount()) { status = "cargo_full"; return false; } } }
            if (!level.destroyBlock(position, false, source)) { return false; }
            transaction.commit();
        }
        if (!drops.isCanceled()) { state.spawnAfterBreak(level, position, tool, false); if (drops.getDroppedExperience() > 0) { state.getBlock().popExperience(level, position, drops.getDroppedExperience()); } }
        source.action("collect", 8); TitaniumVfx.emit(source, TitaniumVfx.MINE, null, Vec3.atCenterOf(position), Vec3.ZERO, 12, 1); return true;
    }
    private void delivery(ServerLevel level, ServerPlayer owner) {
        if (cargo.isEmpty()) { deliver = false; status = active() ? "mining" : "idle"; return; }
        ResourceHandler<ItemResource> target;
        Vec3 position;
        if (destination == null) {
            if (owner.level() != level) { status = "owner_unavailable"; return; }
            position = owner.position(); target = RangedResourceHandler.of(PlayerInventoryWrapper.of(owner), 0, 36);
        } else {
            if (destination.dimension() != level.dimension() || !level.hasChunkAt(destination.pos())) { status = "chest_unloaded"; return; }
            var blockEntity = level.getBlockEntity(destination.pos());
            if (blockEntity == null || !blockEntity.getPersistentData().getStringOr("PhantomismGolemOwner", "").equals(owner.getUUID().toString()) || !permission(level, owner, destination.pos())) { status = "chest_unavailable"; return; }
            target = level.getCapability(Capabilities.Item.BLOCK, destination.pos(), Direction.UP); position = Vec3.atCenterOf(destination.pos());
            if (target == null) { status = "chest_unavailable"; return; }
        }
        if (source.distanceToSqr(position) > 16) { if (source.tickCount % 10 == 0) { source.flyToAssignment(position.add(0, 2, 0), .9D); } status = "delivering"; return; }
        int moved = transfer(target); status = moved == 0 ? "destination_full" : "delivering";
        if (moved > 0) { source.action("deliver", 10); }
        if (cargo.isEmpty()) { deliver = false; status = active() ? "mining" : "idle"; }
    }
    public int transfer(ResourceHandler<ItemResource> target) {
        var storage = VanillaContainerWrapper.of(cargo); int moved = 0;
        try (var transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < 27; slot++) { ItemStack stack = cargo.getItem(slot); if (stack.isEmpty()) { continue; } ItemResource resource = ItemResource.of(stack); int accepted = target.insert(resource, stack.getCount(), transaction); if (accepted > 0) { if (storage.extract(slot, resource, accepted, transaction) != accepted) { return 0; } moved += accepted; } }
            transaction.commit();
        }
        return moved;
    }
    public void breakRamObstacles(ServerLevel level, Vec3 step) {
        if (!ramAuthorized() || ramBudget <= 0 || !(source.getOwner() instanceof ServerPlayer owner)) { return; }
        AABB box = source.getBoundingBox().expandTowards(step); if (!source.isLoadedFlightBox(box)) { return; }
        for (BlockPos point : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (ramBudget <= 0) { break; } BlockState state = level.getBlockState(point);
            if (!state.is(BlockTags.LEAVES) || !permission(level, owner, point) || level.isCloseToVillage(point, 1) || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, source) || !CommonHooks.canEntityDestroy(level, point, source) || state.hasBlockEntity()) { continue; }
            if (!CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, point, state).isCanceled() && level.getBlockState(point) == state) { if (level.destroyBlock(point, true, source)) { ramBudget--; } }
        }
    }
    public void dropCargo() { if (source.level() instanceof ServerLevel level) { for (int slot = 0; slot < 27; slot++) { ItemStack stack = cargo.removeItemNoUpdate(slot); if (!stack.isEmpty()) { source.spawnAtLocation(level, stack); } } } cancel(); }
    public void save(ValueOutput output) {
        output.store("GolemCargo", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(cargo.getItems())); output.putInt("GolemCursor", cursor); output.putBoolean("GolemObsidian", obsidian); output.putBoolean("GolemDeliver", deliver); output.putBoolean("GolemRamBreak", ramAllowed); output.putInt("GolemRamBudget", ramBudget);
        if (author != null) { output.store("GolemAuthor", net.minecraft.core.UUIDUtil.CODEC, author); }
        if (minimum != null) { output.store("GolemMin", GlobalPos.CODEC, minimum); output.store("GolemMax", GlobalPos.CODEC, maximum); }
        if (corner != null) { output.store("GolemCorner", GlobalPos.CODEC, corner); } if (destination != null) { output.store("GolemDestination", GlobalPos.CODEC, destination); }
    }
    public void load(ValueInput input) {
        cargo.clearContent(); List<ItemStack> saved = input.read("GolemCargo", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()); for (int slot = 0; slot < Math.min(27, saved.size()); slot++) { cargo.setItem(slot, saved.get(slot).copy()); }
        minimum = input.read("GolemMin", GlobalPos.CODEC).orElse(null); maximum = input.read("GolemMax", GlobalPos.CODEC).orElse(null); corner = input.read("GolemCorner", GlobalPos.CODEC).orElse(null); destination = input.read("GolemDestination", GlobalPos.CODEC).orElse(null); author = input.read("GolemAuthor", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        cursor = Math.max(0, input.getIntOr("GolemCursor", 0)); obsidian = input.getBooleanOr("GolemObsidian", false); deliver = input.getBooleanOr("GolemDeliver", false); ramAllowed = input.getBooleanOr("GolemRamBreak", false); ramBudget = Math.clamp(input.getIntOr("GolemRamBudget", 0), 0, 8); progress = 0; stalled = 0;
        if (minimum != null && (maximum == null || minimum.dimension() != maximum.dimension() || maximum.pos().getX() < minimum.pos().getX() || maximum.pos().getY() < minimum.pos().getY() || maximum.pos().getZ() < minimum.pos().getZ() || maximum.pos().getX() - minimum.pos().getX() > 15 || maximum.pos().getY() - minimum.pos().getY() > 15 || maximum.pos().getZ() - minimum.pos().getZ() > 15 || volume() > Config.GOLEM_MAX_VOLUME.get())) { cancel(); }
        status = active() ? "mining" : "idle";
    }
}
