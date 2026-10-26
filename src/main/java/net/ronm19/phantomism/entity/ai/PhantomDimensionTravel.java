package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomDimensionTravel {
    private static final String PORTAL_RIDER = "phantomism:portal_rider";
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static final Map<AbstractPhantom, Retry> RETRIES = new WeakHashMap<>();
    private static final Set<AbstractPhantom> IN_TRANSIT = Collections.newSetFromMap(new WeakHashMap<>());

    private PhantomDimensionTravel() {}

    public static @Nullable ServerPlayer owningRider(AbstractPhantom phantom) {
        return phantom.isAlive() && !phantom.isBaby() && phantom.isTame()
                && phantom.getFirstPassenger() instanceof ServerPlayer rider && phantom.isOwnedBy(rider) ? rider : null;
    }

    public static boolean allowsEndExit(AbstractPhantom phantom, Level source, Level destination) {
        return source.dimension() == Level.END && destination.dimension() == Level.OVERWORLD
                && owningRider(phantom) != null && ownedPassengers(phantom, phantom, 0);
    }

    private static boolean ownedPassengers(AbstractPhantom phantom, Entity parent, int depth) {
        if (depth > 8) { return false; }
        for (Entity passenger : parent.getPassengers()) {
            if (!passenger.isAlive() || !(passenger instanceof LivingEntity living && phantom.isOwnedBy(living) || passenger instanceof OwnableEntity owned
                    && phantom.getOwnerReference() != null && phantom.getOwnerReference().equals(owned.getOwnerReference()))
                    || !ownedPassengers(phantom, passenger, depth + 1)) { return false; }
        }
        return true;
    }

    public static boolean followsOwner(AbstractPhantom phantom) {
        return phantom.isAlive() && !phantom.isRemoved() && phantom.isTame() && phantom.getOwnerReference() != null
                && !IN_TRANSIT.contains(phantom)
                && !phantom.isPassenger() && !phantom.isLeashed() && phantom.getTask() == PhantomAssignments.Task.NONE
                && (phantom.getOrder() == AbstractPhantom.Order.FOLLOW || phantom.getOrder() == AbstractPhantom.Order.GUARD)
                && ownedPassengers(phantom, phantom, 0);
    }

    private static boolean loaded(ServerLevel level, AABB box) {
        if (!Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)
                || box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) { return false; }
        for (int horizontal = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
                horizontal <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxX)); horizontal++) {
            for (int depth = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
                    depth <= SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ)); depth++) {
                if (!level.hasChunk(horizontal, depth)) { return false; }
            }
        }
        return true;
    }

    private static Vec3 passengerFeet(Entity parent, Entity passenger, Vec3 destination) {
        return parent.getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(parent))
                .subtract(parent.position()).add(destination);
    }

    private static AABB groupBox(Entity parent, Vec3 destination, int depth) {
        AABB box = parent.getBoundingBox().move(destination.subtract(parent.position()));
        if (depth >= 8) { return box; }
        for (Entity passenger : parent.getPassengers()) { box = box.minmax(groupBox(passenger, passengerFeet(parent, passenger, destination), depth + 1)); }
        return box;
    }

    private static boolean safeFloor(ServerLevel level, BlockPos position) {
        var state = level.getBlockState(position);
        return state.isFaceSturdy(level, position, Direction.UP) && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS) && !state.is(Blocks.CAMPFIRE)
                && !state.is(Blocks.SOUL_CAMPFIRE) && !state.is(Blocks.POWDER_SNOW);
    }

    private static boolean supported(ServerLevel level, AbstractPhantom phantom, Vec3 position) {
        Vec3 end = new Vec3(position.x, Math.max(level.getMinY(), position.y - 32.0D), position.z);
        if (!loaded(level, new AABB(position, end).inflate(0.01D, 0, 0.01D))) { return false; }
        var hit = level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, phantom));
        return hit.getType() == HitResult.Type.BLOCK && safeFloor(level, hit.getBlockPos());
    }

    public static boolean safeDestination(ServerLevel level, AbstractPhantom phantom, Vec3 position, @Nullable ServerPlayer prospectiveRider) {
        AABB box = groupBox(phantom, position, 0);
        if (prospectiveRider != null && !phantom.hasPassenger(prospectiveRider)) {
            box = box.minmax(prospectiveRider.getBoundingBox().move(passengerFeet(phantom, prospectiveRider, position).subtract(prospectiveRider.position())));
        }
        box = box.inflate(0.06D);
        return loaded(level, box) && level.noBlockCollision(phantom, box) && !level.containsAnyLiquid(box)
                && level.getEntities(phantom, box, entity -> entity != prospectiveRider && !phantom.hasIndirectPassenger(entity)
                        && entity.isAlive() && entity.isPickable()).isEmpty() && supported(level, phantom, position);
    }

    private static Optional<Vec3> near(ServerLevel level, AbstractPhantom phantom, Vec3 preferred, @Nullable ServerPlayer rider, boolean follow) {
        for (int height = follow ? 0 : -1; height <= 3; height++) {
            for (int ring = follow ? 3 : 0; ring <= (follow ? 6 : 3); ring++) {
                int samples = ring == 0 ? 1 : 8;
                for (int sample = 0; sample < samples; sample++) {
                    double angle = sample * Math.PI * 2.0D / samples;
                    Vec3 destination = preferred.add(Math.cos(angle) * ring, height + 0.5D, Math.sin(angle) * ring);
                    if (safeDestination(level, phantom, destination, rider)) { return Optional.of(destination); }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean reserve(ServerLevel level) {
        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != level.getGameTime()) { budget.tick = level.getGameTime(); budget.attempts = 0; }
        return budget.attempts++ < 4;
    }

    public static @Nullable AbstractPhantom followOwner(AbstractPhantom phantom) {
        return followOwner(phantom, false);
    }

    private static @Nullable ServerPlayer owner(AbstractPhantom phantom) {
        if (!(phantom.level() instanceof ServerLevel source) || phantom.getOwnerReference() == null || !phantom.isAlive() || phantom.isRemoved()) { return null; }
        var owner = source.getServer().getPlayerList().getPlayer(phantom.getOwnerReference().getUUID());
        return owner != null && owner.isAlive() && !owner.isRemoved() && !owner.wonGame && owner.isAddedToLevel() && phantom.isOwnedBy(owner) ? owner : null;
    }

    private static @Nullable AbstractPhantom followOwner(AbstractPhantom phantom, boolean immediate) {
        if (!(phantom.level() instanceof ServerLevel source) || phantom.getOwnerReference() == null || !phantom.isAlive() || phantom.isRemoved()) { return null; }
        UUID pending = phantom.getPersistentData().read(PORTAL_RIDER, UUIDUtil.CODEC).orElse(null);
        if (pending != null && !pending.equals(phantom.getOwnerReference().getUUID())) {
            phantom.getPersistentData().remove(PORTAL_RIDER);
            pending = null;
        }
        if (IN_TRANSIT.contains(phantom) || !followsOwner(phantom) && pending == null) { return null; }
        var owner = owner(phantom);
        if (owner == null) { return null; }
        ServerLevel destination = owner.level();
        if (destination == source) {
            if (!PhantomHornRally.returning(phantom)) { RETRIES.remove(phantom); }
            if (pending != null) { remount(phantom, owner, pending); }
            return null;
        }
        AbstractPhantom moved = moveToOwner(phantom, owner, immediate);
        if (moved != null && pending != null) { remount(moved, owner, pending); }
        return moved;
    }

    public static @Nullable AbstractPhantom recallOwner(AbstractPhantom phantom, ServerPlayer owner, boolean immediate) {
        if (owner(phantom) != owner || !followsOwner(phantom)) { return null; }
        return moveToOwner(phantom, owner, immediate);
    }

    private static @Nullable AbstractPhantom moveToOwner(AbstractPhantom phantom, ServerPlayer owner, boolean immediate) {
        ServerLevel destination = owner.level();
        if (IN_TRANSIT.contains(phantom) || phantom.isPassenger() || !ownedPassengers(phantom, phantom, 0)
                || destination.getEntity(phantom.getUUID()) != null && destination.getEntity(phantom.getUUID()) != phantom) { return null; }
        Retry retry = RETRIES.get(phantom);
        if (retry == null || retry.destination != destination.dimension() || !retry.owner.equals(owner.getUUID())) {
            retry = new Retry(owner, destination);
            RETRIES.put(phantom, retry);
            phantom.interruptCombatForRecall();
        }
        long now = destination.getGameTime();
        if (!immediate && (now < retry.nextAttempt && owner.position().distanceToSqr(retry.position) < 16.0D || !reserve(destination))) { return null; }
        retry.nextAttempt = now + 5;
        retry.position = owner.position();
        var position = near(destination, phantom, owner.position(), null, true);
        if (position.isEmpty()) { return null; }
        Entity transferred = phantom.teleport(new TeleportTransition(destination, position.get(), Vec3.ZERO, phantom.getYRot(), phantom.getXRot(), TeleportTransition.PLACE_PORTAL_TICKET));
        if (!(transferred instanceof AbstractPhantom moved)) { return null; }
        RETRIES.remove(phantom);
        arrived(moved);
        return moved;
    }

    public static boolean beginTransfer(AbstractPhantom phantom) { return IN_TRANSIT.add(phantom); }

    public static void endTransfer(AbstractPhantom phantom) { IN_TRANSIT.remove(phantom); }

    private static void arrived(AbstractPhantom phantom) {
        phantom.clearCombatTarget(false);
        phantom.getNavigation().stop();
        phantom.setDeltaMovement(Vec3.ZERO);
        phantom.resetFallDistance();
        phantom.setPortalCooldown();
        for (Entity passenger : phantom.getPassengers()) { phantom.positionRider(passenger); passenger.resetFallDistance(); }
        phantom.syncVelocity = true;
    }

    private static void remount(AbstractPhantom phantom, ServerPlayer owner, UUID pending) {
        if (!pending.equals(owner.getUUID()) || !phantom.isOwnedBy(owner) || phantom.isBaby() || owner.wonGame) { return; }
        if (owner.isPassenger() || phantom.isVehicle() || phantom.distanceToSqr(owner) > 64.0D) {
            phantom.getPersistentData().remove(PORTAL_RIDER);
            return;
        }
        if (!safeDestination(owner.level(), phantom, phantom.position(), owner)) { return; }
        if (owner.startRiding(phantom, true, false)) {
            phantom.positionRider(owner);
            owner.resetFallDistance();
            phantom.getPersistentData().remove(PORTAL_RIDER);
        }
    }

    public static void usePortal(AbstractPhantom phantom, ServerLevel source, TeleportTransition original) {
        ServerPlayer rider = owningRider(phantom);
        boolean endExit = source.dimension() == Level.END && rider != null && phantom.portalProcess != null
                && phantom.portalProcess.isSamePortal((Portal) Blocks.END_PORTAL);
        boolean credits = endExit && !rider.seenCredits;
        TeleportTransition transition = original;
        if (endExit) {
            transition = ((Portal) Blocks.END_PORTAL).getPortalDestination(source, rider, phantom.portalProcess.getEntryPosition());
            if (transition == null) { return; }
        }
        if (!source.isAllowedToEnterPortal(transition.newLevel())
                || transition.newLevel() != source && !phantom.canTeleport(source, transition.newLevel())) { return; }
        Vec3 preferred = transition.position();
        Set<Relative> relative = transition.relatives();
        preferred = new Vec3(preferred.x + (relative.contains(Relative.X) ? phantom.getX() : 0),
                preferred.y + (relative.contains(Relative.Y) ? phantom.getY() : 0), preferred.z + (relative.contains(Relative.Z) ? phantom.getZ() : 0));
        var position = near(transition.newLevel(), phantom, preferred, null, false);
        if (position.isEmpty()) {
            if (rider != null) { rider.sendOverlayMessage(Component.translatable("message.phantomism.dimension_no_space")); }
            return;
        }
        var flags = new java.util.HashSet<>(relative);
        flags.remove(Relative.X); flags.remove(Relative.Y); flags.remove(Relative.Z);
        transition = new TeleportTransition(transition.newLevel(), position.get(), transition.deltaMovement(), transition.yRot(), transition.xRot(),
                transition.missingRespawnBlock(), transition.asPassenger(), flags, transition.postTeleportTransition());
        Entity transferred = phantom.teleport(transition);
        if (!(transferred instanceof AbstractPhantom moved)) { return; }
        arrived(moved);
        if (credits && rider.getVehicle() == moved) {
            moved.getPersistentData().store(PORTAL_RIDER, UUIDUtil.CODEC, rider.getUUID());
            rider.showEndCredits();
        }
    }

    @SubscribeEvent
    public static void ownerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner) || owner.wonGame || !owner.isAlive()) { return; }
        var followers = new ArrayList<AbstractPhantom>();
        for (ServerLevel level : owner.level().getServer().getAllLevels()) {
            if (level == owner.level()) { continue; }
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AbstractPhantom phantom && phantom.isOwnedBy(owner) && followsOwner(phantom)) { followers.add(phantom); }
            }
        }
        for (AbstractPhantom phantom : followers) { followOwner(phantom, true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractPhantom phantom) || !(phantom.level() instanceof ServerLevel) || !followsOwner(phantom)) { return; }
        ServerPlayer owner = owner(phantom);
        if (owner != null && owner.level() != phantom.level()) {
            followOwner(phantom);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void retryTransfers(ServerTickEvent.Post event) {
        for (AbstractPhantom phantom : new ArrayList<>(RETRIES.keySet())) {
            if (phantom.level().getServer() != event.getServer()) { continue; }
            ServerPlayer owner = owner(phantom);
            if (owner == null || !followsOwner(phantom)) { RETRIES.remove(phantom); continue; }
            if (owner.level() != phantom.level()) { followOwner(phantom); }
            else if (PhantomHornRally.returning(phantom) && phantom.distanceToSqr(owner) > 1600.0D) { recallOwner(phantom, owner, false); }
            else { RETRIES.remove(phantom); }
        }
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AbstractPhantom phantom && phantom.level() instanceof ServerLevel
                && !phantom.isRemoved()) { followOwner(phantom); }
    }

    private static final class Retry {
        private final UUID owner;
        private final ResourceKey<Level> destination;
        private long nextAttempt;
        private Vec3 position;

        private Retry(ServerPlayer owner, ServerLevel destination) {
            this.owner = owner.getUUID();
            this.destination = destination.dimension();
            this.position = owner.position();
        }
    }

    private static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int attempts;
    }
}
