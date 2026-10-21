package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.EndPhantom;
import org.jspecify.annotations.Nullable;

public final class EndTeleportSafety {
    private EndTeleportSafety() {}

    public static boolean loaded(ServerLevel level, AABB box, boolean interception) {
        if (!Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)
                || box.minY < level.getMinY() - (interception ? 32 : 0) || box.maxY > level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(box)) { return false; }
        for (int horizontal = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
                horizontal <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxX)); horizontal++) {
            for (int depth = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
                    depth <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ)); depth++) {
                if (!level.hasChunk(horizontal, depth)) { return false; }
            }
        }
        return true;
    }

    public static AABB occupiedBox(EndPhantom phantom, Vec3 destination) {
        AABB result = phantom.getBoundingBox().move(destination.subtract(phantom.position()));
        for (Entity passenger : phantom.getPassengers()) {
            Vec3 seat = phantom.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(phantom))
                    .subtract(phantom.position()).add(destination);
            result = result.minmax(passenger.getBoundingBox().move(seat.subtract(passenger.position())));
        }
        return result;
    }

    public static double passengerClearance(EndPhantom phantom) {
        double clearance = 0.125D;
        for (Entity passenger : phantom.getPassengers()) {
            double offset = phantom.getPassengerRidingPosition(passenger).y - passenger.getVehicleAttachmentPoint(phantom).y - phantom.getY();
            clearance = Math.max(clearance, 0.125D - offset);
        }
        return clearance;
    }

    public static boolean boarding(ServerLevel level, EndPhantom phantom, Entity passenger) {
        Vec3 feet = phantom.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(phantom));
        AABB box = phantom.getBoundingBox().minmax(passenger.getBoundingBox().move(feet.subtract(passenger.position()))).inflate(0.04D);
        return loaded(level, box, true) && level.noBlockCollision(phantom, box) && !level.containsAnyLiquid(box)
                && level.getEntities(phantom, box, entity -> entity != passenger && entity.isAlive() && entity.isPickable()).isEmpty();
    }

    public static boolean clear(ServerLevel level, EndPhantom phantom, Vec3 destination, boolean interception, @Nullable Entity ignored) {
        AABB box = occupiedBox(phantom, destination).inflate(0.06D);
        return loaded(level, box, interception) && level.noBlockCollision(phantom, box) && !level.containsAnyLiquid(box)
                && level.getEntities(phantom, box, entity -> entity != ignored && !phantom.hasPassenger(entity)
                        && entity.isAlive() && entity.isPickable()).isEmpty();
    }

    public static boolean destination(ServerLevel level, EndPhantom phantom, Vec3 position, boolean interception, @Nullable Entity ignored) {
        return clear(level, phantom, position, interception, ignored) && (interception || support(level, phantom, position, 32).isPresent());
    }

    public static boolean corridor(ServerLevel level, EndPhantom phantom, Vec3 offset, boolean interception) {
        int probes = Math.max(1, (int) Math.ceil(offset.length() / 0.5D));
        for (int probe = 1; probe <= probes; probe++) {
            Vec3 destination = phantom.position().add(offset.scale((double) probe / probes));
            AABB box = occupiedBox(phantom, destination).inflate(0.04D);
            if (!loaded(level, box, interception) || !level.noBlockCollision(phantom, box) || level.containsAnyLiquid(box)) { return false; }
        }
        return true;
    }

    public static Optional<Vec3> support(ServerLevel level, Entity entity, Vec3 position, int depth) {
        Vec3 bottom = new Vec3(position.x, Math.max(level.getMinY() - 31.0D, position.y - depth), position.z);
        if (bottom.y >= position.y) { return Optional.empty(); }
        AABB column = new AABB(position, bottom).inflate(0.01D);
        if (!loaded(level, column, true)) { return Optional.empty(); }
        var hit = level.clip(new ClipContext(position, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
        if (hit.getType() != HitResult.Type.BLOCK || !safeFloor(level, hit.getBlockPos())) { return Optional.empty(); }
        return Optional.of(hit.getLocation());
    }

    private static boolean safeFloor(ServerLevel level, BlockPos floor) {
        var state = level.getBlockState(floor);
        return state.isFaceSturdy(level, floor, Direction.UP) && state.getFluidState().isEmpty()
                && !state.is(BlockTags.FIRE) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS)
                && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE) && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.POWDER_SNOW);
    }

    public static Optional<Vec3> landing(ServerLevel level, EndPhantom phantom, Vec3 origin, int radius) {
        var candidates = new java.util.ArrayList<Vec3>();
        for (int ring = 0; ring <= radius; ring += 4) {
            int samples = ring == 0 ? 1 : 8;
            for (int sample = 0; sample < samples; sample++) {
                double angle = sample * Math.PI * 2.0D / samples;
                int horizontal = (int) Math.floor(origin.x + Math.cos(angle) * ring);
                int depth = (int) Math.floor(origin.z + Math.sin(angle) * ring);
                if (!level.hasChunk(SectionPos.blockToSectionCoord(horizontal), SectionPos.blockToSectionCoord(depth))) { continue; }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal, depth);
                BlockPos floor = new BlockPos(horizontal, top - 1, depth);
                Vec3 destination = new Vec3(horizontal + 0.5D, top + passengerClearance(phantom), depth + 0.5D);
                if (top <= level.getMinY() || destination.distanceToSqr(phantom.position()) > 96.0D * 96.0D
                        || !safeFloor(level, floor) || !clear(level, phantom, destination, false, null)) { continue; }
                candidates.add(destination);
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(origin::distanceToSqr));
    }

    public static Optional<Vec3> dismount(ServerLevel level, Entity passenger, Vec3 landing) {
        for (int sample = 0; sample < 8; sample++) {
            double angle = sample * Math.PI / 4.0D;
            Vec3 destination = landing.add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
            var floor = support(level, passenger, destination.add(0.0D, 1.0D, 0.0D), 4);
            if (floor.isEmpty()) { continue; }
            destination = new Vec3(destination.x, floor.get().y + 0.01D, destination.z);
            AABB box = passenger.getBoundingBox().move(destination.subtract(passenger.position())).inflate(0.03D, 0.0D, 0.03D);
            if (loaded(level, box, false) && level.noBlockCollision(passenger, box) && !level.containsAnyLiquid(box)
                    && level.getEntities(passenger, box, entity -> entity.isAlive() && entity.isPickable()).isEmpty()) {
                return Optional.of(destination);
            }
        }
        return Optional.empty();
    }
}
