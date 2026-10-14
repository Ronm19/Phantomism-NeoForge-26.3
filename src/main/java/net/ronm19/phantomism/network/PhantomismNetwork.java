package net.ronm19.phantomism.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class PhantomismNetwork {
    private static final double RECIPIENT_RANGE = 128.0D;

    private PhantomismNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(RiddenSiegeChargePayload.TYPE, RiddenSiegeChargePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.getVehicle() instanceof net.ronm19.phantomism.entity.RavagerPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateSiegeCharge(player); }
                }));
        event.registrar("1").playToServer(BlockCommandPayload.TYPE, BlockCommandPayload.STREAM_CODEC,
                PhantomismNetwork::handleBlockCommand);
        event.registrar("1").playToServer(
                StaffCommandPayload.TYPE,
                StaffCommandPayload.STREAM_CODEC,
                PhantomismNetwork::handleStaffCommand
        );
        event.registrar("1").playToServer(
                RiddenProjectileAbilityPayload.TYPE,
                RiddenProjectileAbilityPayload.STREAM_CODEC,
                PhantomismNetwork::handleRiddenProjectileAbility
        );
    }

    private static void handleRiddenProjectileAbility(
            RiddenProjectileAbilityPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.getVehicle() instanceof AbstractPhantom phantom
                    && phantom.getId() == payload.phantomId()) {
                phantom.activateRiddenProjectileAbility(player);
            }
        });
    }

    private static void handleStaffCommand(StaffCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !isHoldingStaff(player)
                    || payload.action() < 0
                    || payload.action() >= Action.values().length) {
                return;
            }

            ServerLevel level = (ServerLevel) player.level();
            Action action = Action.values()[payload.action()];
            if (action == Action.GUARD_POSITION || action == Action.SET_HOME) {
                return;
            }
            List<AbstractPhantom> recipients = action == Action.RECALL && payload.recipientId() < 0
                    ? findAllOwnedLoaded(level, player)
                    : findRecipients(level, player, payload.recipientId(), action == Action.RECALL);
            if (recipients.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.phantomism.staff.no_recipients"));
                return;
            }

            LivingEntity target = action == Action.ATTACK_TARGET
                    ? findAttackTarget(level, player, payload.targetId())
                    : null;
            if (action == Action.ATTACK_TARGET && target == null) {
                return;
            }

            for (AbstractPhantom phantom : recipients) {
                applyAction(phantom, player, action, target);
            }
        });
    }

    private static void handleBlockCommand(BlockCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !isHoldingStaff(player)
                    || payload.action() < 0 || payload.action() >= Action.values().length) {
                return;
            }
            Action action = Action.values()[payload.action()];
            if (action != Action.SET_HOME && action != Action.GUARD_POSITION) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            BlockPos position = payload.position();
            Vec3 start = player.getEyePosition();
            if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)
                    || start.distanceToSqr(Vec3.atCenterOf(position)) > 36.0D) {
                return;
            }
            var hit = level.clip(new ClipContext(start, start.add(player.getViewVector(1.0F).scale(6.0D)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(position)) {
                return;
            }
            var state = level.getBlockState(position);
            if (action == Action.SET_HOME && (!(state.getBlock() instanceof BedBlock) || !player.isShiftKeyDown())) {
                player.sendOverlayMessage(Component.translatable("screen.phantomism.staff.select_home"));
                return;
            }
            if (action == Action.GUARD_POSITION && (state.isAir() || !state.getFluidState().isEmpty()
                    || state.getCollisionShape(level, position).isEmpty())) {
                return;
            }
            List<AbstractPhantom> recipients = findRecipients(level, player, payload.recipientId(), false);
            if (recipients.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.phantomism.staff.no_recipients"));
                return;
            }
            GlobalPos assignedPosition = GlobalPos.of(level.dimension(), position.immutable());
            int assigned = 0;
            for (AbstractPhantom phantom : recipients) {
                if (action == Action.SET_HOME) {
                    phantom.setHome(assignedPosition);
                } else {
                    Vec3 center = Vec3.atCenterOf(position).add(0.0D, 2.0D, 0.0D);
                    var box = phantom.getBoundingBox().move(center.subtract(phantom.position()));
                    if (!phantom.isLoadedFlightBox(box)
                            || !level.noCollision(phantom, box) || level.containsAnyLiquid(box)) {
                        continue;
                    }
                    phantom.setGuardPosition(assignedPosition);
                }
                assigned++;
            }
            player.sendOverlayMessage(Component.translatable(assigned > 0
                    ? "message.phantomism.staff." + (action == Action.SET_HOME ? "home_set" : "guard_set")
                    : "message.phantomism.staff.invalid_position", assigned));
        });
    }

    private static boolean isHoldingStaff(ServerPlayer player) {
        return player.getMainHandItem().is(PhantomismContent.PHANTOM_STAFF.get())
                || player.getOffhandItem().is(PhantomismContent.PHANTOM_STAFF.get());
    }

    private static List<AbstractPhantom> findRecipients(ServerLevel level, ServerPlayer player, int recipientId, boolean recall) {
        if (recipientId < 0) {
            return level.getEntitiesOfClass(
                    AbstractPhantom.class,
                    player.getBoundingBox().inflate(RECIPIENT_RANGE),
                    phantom -> phantom.isOwnedBy(player) && phantom.isAlive()
            );
        }

        Entity candidate = level.getEntity(recipientId);
        if (candidate instanceof AbstractPhantom phantom && phantom.isOwnedBy(player) && phantom.isAlive()
                && (recall || phantom.distanceToSqr(player) <= RECIPIENT_RANGE * RECIPIENT_RANGE)) {
            return List.of(phantom);
        }

        return List.of();
    }

    private static List<AbstractPhantom> findAllOwnedLoaded(ServerLevel level, ServerPlayer player) {
        return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(AbstractPhantom.class::isInstance)
                .map(AbstractPhantom.class::cast)
                .filter(AbstractPhantom::isAlive)
                .filter(phantom -> phantom.isOwnedBy(player))
                .toList();
    }

    private static LivingEntity findAttackTarget(ServerLevel level, ServerPlayer player, int targetId) {
        Entity candidate = level.getEntity(targetId);
        if (!(candidate instanceof LivingEntity target)
                || target == player
                || target instanceof AbstractPhantom
                || !target.isAlive()
                || player.distanceToSqr(target) > 1_024.0D
                || target.isAlliedTo(player)) {
            return null;
        }

        return target;
    }

    private static void applyAction(
            AbstractPhantom phantom,
            ServerPlayer player,
            Action action,
            LivingEntity target
    ) {
        switch (action) {
            case FOLLOW -> {
                phantom.clearCombatTarget(false);
                phantom.setOrder(AbstractPhantom.Order.FOLLOW);
            }
            case STAY -> {
                phantom.clearCombatTarget(false);
                phantom.setOrder(AbstractPhantom.Order.STAY);
            }
            case GUARD -> {
                phantom.clearCombatTarget(false);
                phantom.setOrder(AbstractPhantom.Order.GUARD);
            }
            case ATTACK_TARGET -> phantom.setCombatTarget(target);
            case RECALL -> {
                phantom.clearCombatTarget(false);
                phantom.setOrder(AbstractPhantom.Order.FOLLOW);
                phantom.tryToTeleportToOwner();
            }
            case CLEAR_TARGET -> phantom.clearCombatTarget(true);
            case WANDER -> phantom.setOrder(AbstractPhantom.Order.WANDER);
            case RETURN_HOME -> {
                if (!phantom.returnHome()) {
                    player.sendOverlayMessage(Component.translatable(phantom.getAssignments().home() == null
                            ? "message.phantomism.staff.no_home" : "message.phantomism.staff.home_dimension", phantom.getDisplayName()));
                }
            }
            case ACTIVITY_NONE -> phantom.setTask(Task.NONE);
            case HARVEST -> phantom.setTask(Task.HARVEST);
            case HUNT -> phantom.setTask(Task.HUNT);
            case COLLECT -> phantom.setTask(Task.COLLECT);
            case GUARD_POSITION, SET_HOME -> {
            }
        }
    }

    public enum Action {
        FOLLOW,
        STAY,
        GUARD,
        ATTACK_TARGET,
        RECALL,
        CLEAR_TARGET,
        WANDER,
        GUARD_POSITION,
        SET_HOME,
        RETURN_HOME,
        ACTIVITY_NONE,
        HARVEST,
        HUNT,
        COLLECT
    }

    public record BlockCommandPayload(int action, int recipientId, BlockPos position) implements CustomPacketPayload {
        public static final Type<BlockCommandPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "staff_block_command"));
        public static final StreamCodec<ByteBuf, BlockCommandPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BlockCommandPayload::action,
                ByteBufCodecs.VAR_INT, BlockCommandPayload::recipientId,
                BlockPos.STREAM_CODEC, BlockCommandPayload::position, BlockCommandPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StaffCommandPayload(int action, int recipientId, int targetId) implements CustomPacketPayload {
        public static final Type<StaffCommandPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "staff_command")
        );
        public static final StreamCodec<ByteBuf, StaffCommandPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                StaffCommandPayload::action,
                ByteBufCodecs.VAR_INT,
                StaffCommandPayload::recipientId,
                ByteBufCodecs.VAR_INT,
                StaffCommandPayload::targetId,
                StaffCommandPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RiddenProjectileAbilityPayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenProjectileAbilityPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_projectile_ability")
        );
        public static final StreamCodec<ByteBuf, RiddenProjectileAbilityPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                RiddenProjectileAbilityPayload::phantomId,
                RiddenProjectileAbilityPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RiddenSiegeChargePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenSiegeChargePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_siege_charge"));
        public static final StreamCodec<ByteBuf, RiddenSiegeChargePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenSiegeChargePayload::phantomId, RiddenSiegeChargePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
