package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.ronm19.phantomism.entity.SpectralPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.SpectralVfx;

public final class SpectralPassage {
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> FORBIDDEN = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath(net.ronm19.phantomism.Phantomism.MOD_ID, "spectral_phase_forbidden"));
    private static final Map<ServerLevel, Map<ChunkPos, Map<UUID, Long>>> ANCHORS = new WeakHashMap<>();
    private final SpectralPhantom source;
    private Vec3 anchor, aiExit, mountedApproach;
    private String dimension;
    private final Set<ChunkPos> tickets = new HashSet<>();
    private boolean active, enteredSolid, restoring, relocating;
    private int cooldown;

    public SpectralPassage(SpectralPhantom source) { this.source = source; }
    public boolean active() { return source.level().isClientSide() ? source.isPhasing() : active; }
    public int cooldown() { return cooldown; }
    public Vec3 anchor() { return anchor; }
    private List<Entity> group() { return source.getSelfAndPassengers().limit(3).toList(); }
    private long clock() { return ((ServerLevel) source.level()).getServer().overworld().getGameTime(); }
    private boolean authorized() {
        return (source.isAlive() || active && source.isVehicle()) && !source.isRemoved() && !source.isBaby() && !source.isPassenger()
                && source.getPassengers().size() <= 1 && (!source.isVehicle() || source.getControllingPassenger() instanceof ServerPlayer rider && source.isOwnedBy(rider));
    }

    public boolean begin() {
        if (!(source.level() instanceof ServerLevel) || active || cooldown > 0 || !authorized() || !source.movementEnabled()) { return false; }
        if (source.getControllingPassenger() instanceof ServerPlayer rider && rider.getPersistentData().getLongOr("SpectralPassageUntil", Long.MIN_VALUE) > clock()) { return false; }
        Vec3 entry = entryAnchor(); if (entry == null) { return false; }
        anchor = entry; dimension = source.level().dimension().toString(); enteredSolid = false;
        if (!retainAnchor()) { releaseTickets(); anchor = null; return false; }
        active = true; source.setPhasing(true); physics(true); source.passageAnimation();
        SpectralVfx.emit(source, SpectralVfx.PASSAGE, source, source.position(), Vec3.ZERO, 44, 2);
        return true;
    }

    private Vec3 entryAnchor() {
        if (safe(source.position(), true)) { return source.position(); }
        if (!safe(source.position(), true, false)) { return null; }
        for (double distance : new double[]{.0625D, .125D, .25D}) {
            for (int sample = 0; sample < 8; sample++) {
                double angle = sample * Math.PI / 4D;
                Vec3 candidate = source.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                if (safe(candidate, true)) { return candidate; }
            }
        }
        return null;
    }

    public boolean tryMountedEntry(Vec3 movement) {
        if (!(source.level() instanceof ServerLevel) || active || cooldown > 0 || !source.movementEnabled()
                || !(source.getControllingPassenger() instanceof ServerPlayer rider) || !source.isOwnedBy(rider)
                || !rider.getLastClientInput().forward() || !Double.isFinite(movement.lengthSqr()) || movement.lengthSqr() > 36) { return false; }
        Vec3 direction = Vec3.directionFromRotation(0, rider.getYRot());
        Vec3 momentum = movement.multiply(1, 0, 1); double speed = momentum.length();
        double distance = 1.1D;
        if (speed >= .25D && momentum.dot(direction) > speed * .5D) {
            direction = momentum.scale(1D / speed); distance = Math.clamp(speed * 6D + .5D, 1.1D, 4D);
        }
        Vec3 step = direction.scale(distance);
        if (!obstructed(step) || !begin()) { return false; }
        mountedApproach = step; return true;
    }

    private boolean obstructed(Vec3 step) {
        return traversable(step) && group().stream().anyMatch(member -> !source.level().noBlockCollision(member, volume(member, source.position()).expandTowards(step)));
    }

    public boolean beginToward(Vec3 destination) {
        if (source.isVehicle() || source.position().distanceToSqr(destination) > 64 || !safe(destination, true) || !begin()) { return false; }
        Vec3 delta = destination.subtract(source.position());
        for (int sample = 1; sample <= 4; sample++) {
            Vec3 waypoint = source.position().add(delta.scale(sample / 4D));
            AABB swept = source.getBoundingBox().expandTowards(waypoint.subtract(source.position()));
            if (!source.isLoadedFlightBox(swept) || swept.minY < source.level().getMinY() + 3 || swept.maxY > source.level().getMaxY() - 2 || forbidden(swept)) { exit(); return false; }
        }
        aiExit = destination; return true;
    }

    private AABB volume(Entity member, Vec3 destination) {
        return volume(member, destination, true);
    }

    private AABB volume(Entity member, Vec3 destination, boolean clearance) {
        Vec3 offset = member == source ? Vec3.ZERO : source.getPassengerRidingPosition(member).subtract(member.getVehicleAttachmentPoint(source)).subtract(source.position());
        AABB box = member.getBoundingBox().move(destination.add(offset).subtract(member.position()));
        if (!clearance) { return box.deflate(.000001D); }
        return new AABB(box.minX - .03D, box.minY + .001D, box.minZ - .03D, box.maxX + .03D, box.maxY + .02D, box.maxZ + .03D);
    }

    public boolean safe(Vec3 destination, boolean occupancy) {
        return safe(destination, occupancy, true);
    }

    private boolean safe(Vec3 destination, boolean occupancy, boolean clearance) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(destination.lengthSqr())) { return false; }
        List<Entity> members = group();
        for (Entity member : members) {
            AABB box = volume(member, destination, clearance);
            if (!source.isLoadedFlightBox(box) || box.minY < level.getMinY() + 3 || box.maxY > level.getMaxY() - 2
                    || !level.noBlockCollision(member, box) || level.containsAnyLiquid(box) || forbidden(box)) { return false; }
            if (occupancy && !level.getEntities(member, box, other -> !members.contains(other) && other.isAlive() && other.isPickable()).isEmpty()) { return false; }
        }
        return true;
    }

    private boolean forbidden(AABB box) {
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var state = source.level().getBlockState(block);
            if (state.is(FORBIDDEN) || source.level() instanceof ServerLevel server && source.getOwner() instanceof ServerPlayer owner && !server.mayInteract(owner, block)
                    || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)
                    || state.is(Blocks.END_GATEWAY) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.STRUCTURE_VOID) || state.hasBlockEntity()
                    || !state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)) { return true; }
        }
        return false;
    }

    public boolean allowsMove(Vec3 destination) {
        if (!active() || !Double.isFinite(destination.lengthSqr()) || source.position().distanceToSqr(destination) > 36
                || source.level() instanceof ServerLevel && (!authorized() || !Objects.equals(dimension, source.level().dimension().toString()))) { return false; }
        return traversable(destination.subtract(source.position()));
    }

    private boolean traversable(Vec3 delta) {
        for (Entity member : group()) {
            AABB swept = volume(member, source.position()).expandTowards(delta);
            if (!source.isLoadedFlightBox(swept) || swept.minY < source.level().getMinY() + 3 || swept.maxY > source.level().getMaxY() - 2 || forbidden(swept)) { return false; }
        }
        return true;
    }

    public void moved(Vec3 previous) {
        if (!active) { return; }
        if (!safe(source.position(), false) || !safe(previous, false)) { enteredSolid = true; }
        else {
            Vec3 delta = source.position().subtract(previous);
            for (Entity member : group()) {
                if (!source.level().noBlockCollision(member, volume(member, previous).expandTowards(delta))) { enteredSolid = true; break; }
            }
        }
    }

    public void beforeTick() {
        if (source.level().isClientSide()) { physics(source.isPhasing()); return; }
        if (cooldown > 0) { cooldown--; }
        if (!active) { tryMountedEntry(source.getDeltaMovement()); }
        if (active) {
            physics(true);
            if (restoring) { retainAnchor(); if (recall()) { restoring = false; } }
            if (source.tickCount % 10 == 0) { retainAnchor(); }
        }
    }

    public void afterTick() {
        if (!(source.level() instanceof ServerLevel)) { return; }
        if (active) {
            physics(true);
            if (aiExit != null && !source.isVehicle()) {
                Vec3 delta = aiExit.subtract(source.position());
                if (delta.lengthSqr() > .01D) { Vec3 step = delta.length() > .7D ? delta.normalize().scale(.7D) : delta; if (allowsMove(source.position().add(step))) { source.move(MoverType.SELF, step); source.setDeltaMovement(Vec3.ZERO); source.needsSync = true; } else { recall(); } }
                else if (safe(source.position(), true)) { exit(); return; }
            }
            if (safe(source.position(), true) && (enteredSolid || restoring)) { exit(); return; }
            if (!enteredSolid && source.getControllingPassenger() instanceof ServerPlayer rider && safe(source.position(), true)) {
                if (!rider.getLastClientInput().forward() || mountedApproach != null
                        && (Vec3.directionFromRotation(0, rider.getYRot()).dot(mountedApproach.normalize()) <= .5D || !obstructed(mountedApproach))) { exit(); return; }
            }
            if (!authorized() || !source.movementEnabled()) { recall(); return; }
            if (source.tickCount % 40 == 0) { SpectralVfx.emit(source, SpectralVfx.PASSAGE, source, source.position(), Vec3.ZERO, 39, 2); }
        } else { tryMountedEntry(source.getDeltaMovement()); }
    }

    public boolean exit() {
        if (!(source.level() instanceof ServerLevel) || !active || !safe(source.position(), true)) { return false; }
        boolean traversed = enteredSolid;
        active = false; restoring = false; enteredSolid = false; aiExit = null; mountedApproach = null; source.setPhasing(false); physics(false); releaseTickets();
        if (traversed) {
            cooldown = Math.max(cooldown, 400);
            if (source.getControllingPassenger() instanceof ServerPlayer rider) { rider.getPersistentData().putLong("SpectralPassageUntil", clock() + 400); }
        }
        source.setDeltaMovement(Vec3.ZERO); source.needsSync = true;
        SpectralVfx.emit(source, SpectralVfx.EMERGE, null, source.position(), Vec3.ZERO, 20, 2); return true;
    }

    public boolean recall() {
        if (!(source.level() instanceof ServerLevel) || !active) { return !active; }
        Vec3 destination = recoveryPoint();
        if (destination == null) { source.setDeltaMovement(Vec3.ZERO); physics(true); return false; }
        if (destination.distanceToSqr(source.position()) > .001D && !relocate(destination, false)) { return false; }
        return exit();
    }

    public Vec3 recoveryPoint() {
        if (safe(source.position(), true)) { return source.position(); }
        if (anchor == null || !Objects.equals(dimension, source.level().dimension().toString())) { return null; }
        if (safe(anchor, true)) { return anchor; }
        for (int radius : new int[]{1, 2, 4, 6}) {
            for (int height : new int[]{0, 1, 2, -1, 4, -2}) {
                for (int sample = 0; sample < 8; sample++) {
                    double angle = sample * Math.PI / 4;
                    Vec3 candidate = anchor.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                    if (safe(candidate, true)) { return candidate; }
                }
            }
        }
        for (int height : new int[]{8, -8, 16, -16, 32, -32, 64, -64}) {
            for (int side : new int[]{0, 4, -4}) { Vec3 candidate = anchor.add(side, height, side); if (safe(candidate, true)) { return candidate; } }
        }
        return null;
    }

    public Vec3 savedPosition(Entity member) {
        if (!active || !(source.level() instanceof ServerLevel)) { return null; }
        Vec3 destination = recoveryPoint();
        if (destination == null) { return null; }
        return member == source ? destination : destination.add(source.getPassengerRidingPosition(member).subtract(member.getVehicleAttachmentPoint(source)).subtract(source.position()));
    }

    public boolean shift(Vec3 destination) {
        if (!(source.level() instanceof ServerLevel) || active || !authorized() || source.position().distanceToSqr(destination) > 36 || !safe(destination, true)) { return false; }
        return relocate(destination, true);
    }

    private boolean relocate(Vec3 destination, boolean event) {
        if (relocating || !(source.level() instanceof ServerLevel level) || !safe(destination, true)) { return false; }
        if (event) {
            var teleport = new EntityTeleportEvent.EnderEntity(source, destination.x, destination.y, destination.z); NeoForge.EVENT_BUS.post(teleport);
            if (teleport.isCanceled()) { return false; }
            destination = teleport.getTarget(); if (source.position().distanceToSqr(destination) > 36 || !safe(destination, true)) { return false; }
        }
        relocating = true;
        try {
            Vec3 translation = destination.subtract(source.position());
            for (Entity member : group()) {
                Vec3 point = member.position().add(translation);
                member.teleportSetPosition(new PositionMoveRotation(point, Vec3.ZERO, member.getYRot(), member.getXRot()), Set.of());
                member.setDeltaMovement(Vec3.ZERO); member.resetFallDistance(); member.needsSync = true;
                if (member instanceof ServerPlayer player && player.connection != null) { player.connection.teleport(point.x, point.y, point.z, player.getYRot(), player.getXRot()); }
                else if (member.isAddedToLevel()) { level.getChunkSource().sendToTrackingPlayers(member, ClientboundTeleportEntityPacket.teleport(member.getId(), PositionMoveRotation.of(member), Set.of(), member.onGround())); }
            }
            for (Entity passenger : source.getPassengers()) { source.positionRider(passenger); }
            return safe(source.position(), true);
        } finally { relocating = false; }
    }

    private void physics(boolean enabled) {
        source.noPhysics = enabled;
        for (Entity passenger : source.getPassengers()) { passenger.noPhysics = enabled || passenger instanceof net.minecraft.world.entity.player.Player player && player.isSpectator(); passenger.resetFallDistance(); }
        source.resetFallDistance();
    }

    private boolean retainAnchor() {
        if (!(source.level() instanceof ServerLevel level) || anchor == null || !Objects.equals(dimension, level.dimension().toString())) { return false; }
        var records = ANCHORS.computeIfAbsent(level, ignored -> new HashMap<>()); long time = level.getGameTime();
        records.values().forEach(leases -> leases.values().removeIf(until -> time >= until)); records.values().removeIf(Map::isEmpty);
        Set<UUID> owners = new HashSet<>(); records.values().forEach(leases -> owners.addAll(leases.keySet()));
        if (owners.size() >= 128 && !owners.contains(source.getUUID())) { return false; }
        for (Entity member : group()) {
            AABB box = volume(member, anchor);
            for (int horizontal = Math.floorDiv((int) Math.floor(box.minX), 16); horizontal <= Math.floorDiv((int) Math.floor(box.maxX), 16); horizontal++) {
                for (int depth = Math.floorDiv((int) Math.floor(box.minZ), 16); depth <= Math.floorDiv((int) Math.floor(box.maxZ), 16); depth++) {
                    if (!level.hasChunk(horizontal, depth)) { return false; }
                    ChunkPos chunk = new ChunkPos(horizontal, depth); tickets.add(chunk);
                    records.computeIfAbsent(chunk, ignored -> new HashMap<>()).put(source.getUUID(), time + 40);
                    level.getChunkSource().addTicketWithRadius(PhantomismContent.SPECTRAL_ANCHOR_TICKET.get(), chunk, 0);
                }
            }
        }
        return true;
    }

    public void releaseTickets() {
        if (!(source.level() instanceof ServerLevel level)) { return; }
        var records = ANCHORS.get(level);
        for (ChunkPos chunk : tickets) {
            var leases = records == null ? null : records.get(chunk);
            if (leases != null) {
                leases.remove(source.getUUID()); leases.values().removeIf(until -> level.getGameTime() >= until);
                if (leases.isEmpty()) { records.remove(chunk); }
            }
            if (leases == null || leases.isEmpty()) { level.getChunkSource().removeTicketWithRadius(PhantomismContent.SPECTRAL_ANCHOR_TICKET.get(), chunk, 0); }
        }
        tickets.clear(); if (records != null && records.isEmpty()) { ANCHORS.remove(level); }
    }

    public void save(ValueOutput output) {
        output.putInt("SpectralPassageCooldown", cooldown); output.putBoolean("SpectralPassageActive", active); output.putBoolean("SpectralPassageEntered", enteredSolid);
        if (active && anchor != null) { output.store("SpectralPassageAnchor", Vec3.CODEC, anchor); output.putString("SpectralPassageDimension", dimension); }
    }

    public void load(ValueInput input) {
        cooldown = Math.clamp(input.getIntOr("SpectralPassageCooldown", 0), 0, 400);
        anchor = input.read("SpectralPassageAnchor", Vec3.CODEC).orElse(null); dimension = input.getStringOr("SpectralPassageDimension", "");
        active = input.getBooleanOr("SpectralPassageActive", false) && anchor != null && Double.isFinite(anchor.lengthSqr()) && dimension.equals(source.level().dimension().toString());
        enteredSolid = input.getBooleanOr("SpectralPassageEntered", false); restoring = active; source.setPhasing(active); source.noPhysics = active;
        if (input.getBooleanOr("SpectralPassageActive", false) && !active) { cooldown = 400; }
    }
}
