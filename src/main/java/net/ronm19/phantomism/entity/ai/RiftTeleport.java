package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.RiftPhantom;

public final class RiftTeleport {
    private static final Set<UUID> MOVING = new HashSet<>();
    private RiftTeleport() {}
    public static List<Entity> group(Entity entity) { return entity.getRootVehicle().getSelfAndPassengers().limit(10).toList(); }
    public static Optional<Vec3> arrival(RiftPhantom source, Entity entity, Vec3 preferred) {
        for (int height = 0; height <= 3; height++) { Vec3 candidate = preferred.add(0, height, 0); if (safe(source, entity, candidate)) { return Optional.of(candidate); } } return Optional.empty();
    }
    public static boolean authorized(RiftPhantom source, Entity entity) {
        Entity root = entity.getRootVehicle(); List<Entity> group = group(root);
        return group.size() <= 8 && !root.isPassenger() && group.stream().allMatch(member -> member instanceof LivingEntity ally && source.acceptsAlly(ally)
                && member.isAlive() && !member.isRemoved() && member.level() == source.level() && !(member instanceof Mob mob && mob.isLeashed())
                && !(member instanceof ServerPlayer player && (player.isSpectator() || player.wonGame)));
    }
    public static boolean safe(RiftPhantom source, Entity entity, Vec3 destination) {
        return safe(source, entity, destination, true);
    }
    public static boolean safe(RiftPhantom source, Entity entity, Vec3 destination, boolean occupancy) {
        Entity root = entity.getRootVehicle(); if (!(source.level() instanceof ServerLevel level) || root.level() != level || !Double.isFinite(destination.lengthSqr()) || !root.canTeleport(level, level)) { return false; }
        List<Entity> members = group(root); if (members.size() > 8) { return false; }
        AABB total = null;
        for (Entity member : members) {
            if (!member.isAlive() || member.isRemoved() || member.level() != level) { return false; }
            Vec3 offset = member.position().subtract(root.position()); AABB box = member.getBoundingBox().move(destination.add(offset).subtract(member.position())).inflate(.04D, 0, .04D);
            if (!source.isLoadedFlightBox(box) || !level.noBlockCollision(member, box) || level.containsAnyLiquid(box)) { return false; }
            for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } }
            total = total == null ? box : total.minmax(box);
            if (member.isPassenger()) {
                Entity parent = member.getVehicle(); Vec3 expected = parent.getPassengerRidingPosition(member).subtract(member.getVehicleAttachmentPoint(parent)).subtract(root.position()).add(destination);
                AABB seated = member.getBoundingBox().move(expected.subtract(member.position())).inflate(.04D, 0, .04D);
                if (!source.isLoadedFlightBox(seated) || !level.noBlockCollision(member, seated) || level.containsAnyLiquid(seated)) { return false; }
                for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(seated.minX, seated.minY, seated.minZ), BlockPos.containing(seated.maxX, seated.maxY, seated.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } } total = total.minmax(seated);
            }
        }
        if (total == null || !level.getWorldBorder().isWithinBounds(total) || occupancy && !level.getEntities(root, total, other -> !members.contains(other) && other.isAlive() && other.isPickable()).isEmpty()) { return false; }
        AABB base = root.getBoundingBox().move(destination.subtract(root.position())); int depth = root instanceof AbstractPhantom ? 12 : 1;
        for (double horizontal : new double[]{base.minX + .1D, base.maxX - .1D}) { for (double forward : new double[]{base.minZ + .1D, base.maxZ - .1D}) {
            boolean floor = false;
            for (int down = 1; down <= depth; down++) {
                BlockPos below = BlockPos.containing(horizontal, destination.y - down + .05D, forward);
                if (!level.hasChunkAt(below) || GravityControl.dangerous(level, below)) { return false; }
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) { floor = true; break; }
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) { return false; }
            } if (!floor) { return false; }
        } } return true;
    }
    public static Optional<Vec3> find(RiftPhantom source, Entity entity, Vec3 preferred, double range, java.util.function.Predicate<Vec3> tactical) {
        Entity root = entity.getRootVehicle(); int checked = 0;
        for (int ring : new int[]{0, 2, 4, 6, 8}) { for (int height : new int[]{0, 1, -1, 2, -3, -6, -10}) {
            for (int sample = 0; sample < (ring == 0 ? 1 : 8); sample++) {
                if (++checked > 192) { return Optional.empty(); }
                double angle = sample * Math.PI / 4; Vec3 candidate = preferred.add(Math.cos(angle) * ring, height, Math.sin(angle) * ring);
                candidate = new Vec3(candidate.x, Math.floor(candidate.y), candidate.z);
                if (candidate.distanceToSqr(root.position()) <= range * range && tactical.test(candidate) && safe(source, root, candidate)) { return Optional.of(candidate); }
            }
        } } return Optional.empty();
    }
    public static boolean move(RiftPhantom source, Entity entity, Vec3 destination, double range, boolean ally) {
        Entity root = entity.getRootVehicle();
        if (!source.controlsEnabled() || root.level() != source.level() || root.position().distanceToSqr(destination) > range * range || MOVING.contains(root.getUUID())
                || ally && !authorized(source, root) || !ally && (!(root instanceof LivingEntity target) || !source.validTarget(target) || root.isVehicle() || root.isPassenger() || RiftControl.immune(target) || RiftControl.locked(target)) || !safe(source, root, destination)) { return false; }
        var event = new EntityTeleportEvent.EnderEntity((LivingEntity) root, destination.x, destination.y, destination.z); NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) { return false; } destination = event.getTarget();
        if (!Double.isFinite(destination.lengthSqr()) || root.position().distanceToSqr(destination) > range * range || !safe(source, root, destination)) { return false; }
        ServerLevel level = (ServerLevel) source.level(); List<Entity> members = group(root);
        List<Snapshot> snapshots = members.stream().map(member -> new Snapshot(member, member.position(), member.getDeltaMovement(), member.getYRot(), member.getXRot(), member.getVehicle())).toList();
        Vec3 translation = destination.subtract(root.position());
        MOVING.add(root.getUUID());
        try {
            Entity result = root.teleport(new TeleportTransition(level, destination, Vec3.ZERO, root.getYRot(), root.getXRot(), TeleportTransition.DO_NOTHING));
            boolean intact = result == root && root.position().distanceToSqr(destination) < .01D && snapshots.stream().allMatch(snapshot -> snapshot.entity.getVehicle() == snapshot.parent && snapshot.entity.level() == level && snapshot.entity.position().distanceToSqr(snapshot.position.add(translation)) < .01D)
                    && safe(source, root, root.position());
            if (!intact) { rollback(snapshots, level); return false; }
            for (Entity member : members) { member.resetFallDistance(); member.setDeltaMovement(Vec3.ZERO); member.needsSync = true; if (member instanceof Mob mob) { mob.getNavigation().stop(); mob.getMoveControl().setWantedPosition(member.getX(), member.getY(), member.getZ(), 0); } }
            return true;
        } finally { MOVING.remove(root.getUUID()); }
    }
    private static void rollback(List<Snapshot> snapshots, ServerLevel level) {
        for (Snapshot state : snapshots) {
            state.entity.teleportSetPosition(new net.minecraft.world.entity.PositionMoveRotation(state.position, state.motion, state.yaw, state.pitch), Set.of());
            state.entity.needsSync = true;
            if (state.entity instanceof ServerPlayer player && player.connection != null) { player.connection.teleport(state.position.x, state.position.y, state.position.z, state.yaw, state.pitch); }
        }
        for (Snapshot state : snapshots) { if (state.parent != null && state.entity.getVehicle() != state.parent) { state.entity.startRiding(state.parent, true, false); } }
        for (Snapshot state : snapshots) { if (!(state.entity instanceof ServerPlayer)) { level.getChunkSource().sendToTrackingPlayers(state.entity, net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(state.entity.getId(), net.minecraft.world.entity.PositionMoveRotation.of(state.entity), Set.of(), state.entity.onGround())); } }
    }
    private record Snapshot(Entity entity, Vec3 position, Vec3 motion, float yaw, float pitch, Entity parent) {}
}
