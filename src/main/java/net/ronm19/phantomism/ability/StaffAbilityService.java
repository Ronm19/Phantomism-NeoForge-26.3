package net.ronm19.phantomism.ability;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class StaffAbilityService {
    public static final Gson JSON = new Gson();
    public static final double RANGE = 128;
    public enum Result { SUCCESS, RECIPIENT, ABILITY, COOLDOWN, TARGET, POSITION, UNAVAILABLE, THROTTLED }
    public record State(List<PhantomAbilities.View> abilities) {}
    private static final Map<ServerPlayer, Long> READS = new WeakHashMap<>(), USES = new WeakHashMap<>();
    private StaffAbilityService() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(StaffAbilityPacket.Snapshot.TYPE, StaffAbilityPacket.Snapshot.STREAM_CODEC);
        event.registrar("1").playToServer(StaffAbilityPacket.TYPE, StaffAbilityPacket.STREAM_CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            if (!packet.activate()) { refresh(player, packet.recipient(), false); return; }
            Result result = activate(player, packet);
            if (result == Result.THROTTLED) { return; }
            player.sendOverlayMessage(Component.translatable("staff.ability.result." + result.name().toLowerCase(java.util.Locale.ROOT)));
            refresh(player, packet.recipient(), true);
        }));
    }

    public static boolean holdsStaff(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && (player.getMainHandItem().is(PhantomismContent.PHANTOM_STAFF.get())
                || player.getOffhandItem().is(PhantomismContent.PHANTOM_STAFF.get()));
    }

    private static boolean throttle(ServerPlayer player, Map<ServerPlayer, Long> requests, int interval) {
        long now = player.level().getGameTime();
        Long last = requests.get(player);
        if (last != null && now >= last && now - last < interval) { return false; }
        requests.put(player, now); return true;
    }

    public static AbstractPhantom recipient(ServerPlayer player, String identity) {
        if (!holdsStaff(player) || !(player.level() instanceof ServerLevel level)) { return null; }
        UUID uuid;
        try { uuid = UUID.fromString(identity); } catch (RuntimeException ignored) { return null; }
        return level.getEntity(uuid) instanceof AbstractPhantom phantom && phantom.isOwnedBy(player) && phantom.isAlive()
                && !phantom.isRemoved() && phantom.level() == level && level.hasChunkAt(phantom.blockPosition())
                && phantom.distanceToSqr(player) <= RANGE * RANGE ? phantom : null;
    }

    public static State snapshot(AbstractPhantom phantom) {
        return new State(PhantomAbilities.abilities(phantom).stream().map(ability -> PhantomAbilities.view(phantom, ability)).toList());
    }

    public static void refresh(ServerPlayer player, String identity, boolean immediate) {
        if (!holdsStaff(player) || !immediate && !throttle(player, READS, 20)) { return; }
        AbstractPhantom phantom = recipient(player, identity);
        State state = phantom == null ? new State(List.of()) : snapshot(phantom);
        PacketDistributor.sendToPlayer(player, new StaffAbilityPacket.Snapshot(player.getUUID().toString(), player.level().dimension().toString(), identity, JSON.toJson(state)));
    }

    public static boolean safeTarget(ServerPlayer player, AbstractPhantom phantom, LivingEntity target) {
        if (target == null || target == player || target == phantom || !target.isAlive() || target.isRemoved()
                || target.level() != phantom.level() || target instanceof AbstractPhantom || target.isAlliedTo(player)
                || phantom.isAlliedTo(target) || target.isInvulnerable() || target instanceof Player other && (other.isCreative() || other.isSpectator())
                || target instanceof OwnableEntity pet && java.util.Objects.equals(pet.getOwnerReference(), phantom.getOwnerReference())
                || phantom.isTargetSuppressed(target) || PhantomDuels.excludesObserver(phantom, target)
                || target.distanceToSqr(player) > 32 * 32 || target.distanceToSqr(phantom) > 64 * 64) { return false; }
        return line((ServerLevel) phantom.level(), player, player.getEyePosition(), target.getEyePosition())
                && line((ServerLevel) phantom.level(), phantom, phantom.getEyePosition(), target.getEyePosition());
    }

    private static boolean line(ServerLevel level, net.minecraft.world.entity.Entity source, Vec3 start, Vec3 end) {
        AABB corridor = new AABB(start, end).inflate(.1D);
        return level.hasChunksAt(BlockPos.containing(corridor.minX, corridor.minY, corridor.minZ), BlockPos.containing(corridor.maxX, corridor.maxY, corridor.maxZ))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
    }

    public static Result activate(ServerPlayer player, StaffAbilityPacket packet) {
        if (!throttle(player, USES, 4)) { return Result.THROTTLED; }
        AbstractPhantom phantom = recipient(player, packet.recipient());
        if (phantom == null) { return Result.RECIPIENT; }
        var ability = PhantomAbilities.abilities(phantom).stream().filter(candidate -> candidate.id().toString().equals(packet.ability())).findFirst().orElse(null);
        if (ability == null || ability.automatic()) { return Result.ABILITY; }
        var view = PhantomAbilities.view(phantom, ability);
        if (view.remaining() > 0) { return Result.COOLDOWN; }
        if (view.status() != PhantomAbilities.Status.READY) { return Result.UNAVAILABLE; }
        ServerLevel level = (ServerLevel) phantom.level();
        LivingEntity target = null;
        Vec3 position = null;
        if (ability.target() == PhantomAbilities.Target.ENEMY) {
            try { target = level.getEntity(UUID.fromString(packet.target())) instanceof LivingEntity living ? living : null; }
            catch (RuntimeException ignored) { return Result.TARGET; }
            if (!safeTarget(player, phantom, target) || phantom.getTarget() != target || !phantom.getAssignments().permitsDefensivePursuit(target)) { return Result.TARGET; }
        } else if (ability.target() == PhantomAbilities.Target.POSITION) {
            BlockPos block = packet.position();
            if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block) || player.distanceToSqr(Vec3.atCenterOf(block)) > 36
                    || !level.mayInteract(player, block)) { return Result.POSITION; }
            var hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getViewVector(1).scale(6)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(block)) { return Result.POSITION; }
            position = Vec3.atBottomCenterOf(block.above());
        }
        boolean success = phantom.isVehicle() && ability.shot() ? phantom.activateRiddenProjectileAbility(player)
                : ability.activation().activate(phantom, level, target, position);
        return success ? Result.SUCCESS : Result.UNAVAILABLE;
    }
}
