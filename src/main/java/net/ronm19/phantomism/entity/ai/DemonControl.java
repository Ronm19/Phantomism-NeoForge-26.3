package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.DemonPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class DemonControl {
    private record Lease(UUID source, UUID session, long until, long fleeUntil) {}
    private static final Map<Mob, Lease> LEASES = new WeakHashMap<>();
    private static final Map<Mob, Long> RECOVERY = new WeakHashMap<>();
    private static final Identifier WEAKNESS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "demon_intimidation");
    private DemonControl() {}
    public static boolean apply(DemonPhantom source, Mob target, boolean howl) {
        if (!(source.level() instanceof ServerLevel) || source.level() != target.level()) { return false; }
        RECOVERY.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue());
        if (RECOVERY.size() >= 512 && !RECOVERY.containsKey(target)) { return false; }
        long time = target.level().getGameTime();
        if (!source.enabled() || !source.validTarget(target) || GravityControl.movementImmune(target) || source.distanceToSqr(target) > (howl ? 144 : 64)
                || !source.line(source.getEyePosition(), target.getEyePosition()) || RECOVERY.getOrDefault(target, Long.MIN_VALUE) > time || LEASES.size() >= 128 && !LEASES.containsKey(target)) { return false; }
        Lease old = LEASES.get(target); if (old != null && old.until > time && (!old.source.equals(source.getUUID()) || !howl)) { return false; }
        LEASES.put(target, new Lease(source.getUUID(), source.session(), time + (howl ? 80 : 40), howl ? time + 40 : time));
        var attribute = target.getAttribute(Attributes.ATTACK_DAMAGE); if (attribute != null) { attribute.addOrUpdateTransientModifier(new AttributeModifier(WEAKNESS, howl ? -.2D : -.1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } return true;
    }
    private static void release(Mob target) { if (LEASES.remove(target) == null) { return; } var attribute = target.getAttribute(Attributes.ATTACK_DAMAGE); if (attribute != null) { attribute.removeModifier(WEAKNESS); } RECOVERY.put(target, target.level().getGameTime() + 80); }
    public static void release(DemonPhantom source) { if (source.level().isClientSide()) { return; } for (var entry : Map.copyOf(LEASES).entrySet()) { if (entry.getValue().source.equals(source.getUUID())) { release(entry.getKey()); } } }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob target) || !(target.level() instanceof ServerLevel level)) { return; }
        Lease lease = LEASES.get(target); if (lease == null) { return; }
        if (!(level.getEntity(lease.source) instanceof DemonPhantom source) || !source.session().equals(lease.session) || !source.enabled() || !source.validTarget(target)
                || !target.isAlive() || level.getGameTime() >= lease.until || source.distanceToSqr(target) > 24 * 24) { release(target); return; }
        if (level.getGameTime() < lease.fleeUntil && target.tickCount % 4 == 0) {
            Vec3 away = target.position().subtract(source.position()).multiply(1, 0, 1).normalize();
            if (away.lengthSqr() < .01D) { away = new Vec3(1, 0, 0); } Vec3 destination = target.position().add(away.scale(4));
            if (safeStep(source, target, destination)) { target.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.1D); }
        }
    }
    public static boolean safeStep(DemonPhantom source, Mob target, Vec3 destination) {
        if (!(source.level() instanceof ServerLevel level) || target.level() != level || !Double.isFinite(destination.lengthSqr()) || target.position().distanceToSqr(destination) > 36) { return false; }
        var box = target.getBoundingBox().expandTowards(destination.subtract(target.position())).deflate(.001D);
        if (!source.isLoadedFlightBox(box) || !level.getWorldBorder().isWithinBounds(box) || !level.noBlockCollision(target, box) || level.containsAnyLiquid(box)) { return false; }
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous(level, block)) { return false; } }
        var landing = target.getBoundingBox().move(destination.subtract(target.position()));
        for (double horizontal : new double[]{landing.minX + .02D, landing.maxX - .02D}) {
            for (double depth : new double[]{landing.minZ + .02D, landing.maxZ - .02D}) {
                boolean supported = false;
                for (int down = 1; down <= 3; down++) {
                    BlockPos floor = BlockPos.containing(horizontal, destination.y - down + .01D, depth);
                    if (!level.hasChunkAt(floor) || GravityControl.dangerous(level, floor)) { return false; }
                    if (level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) { supported = true; break; }
                }
                if (!supported) { return false; }
            }
        } return true;
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof DemonPhantom source) { source.endCombat(); }
        if (event.getEntity() instanceof Mob target) { release(target); RECOVERY.remove(target); }
    }
}
