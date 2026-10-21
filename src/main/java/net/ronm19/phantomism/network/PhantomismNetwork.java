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
        event.registrar("1").playToServer(RiddenSpectralDismountPayload.TYPE, RiddenSpectralDismountPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.SpectralPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.requestSafeDismount(player); }
                }));
        event.registrar("1").playToClient(net.ronm19.phantomism.vfx.VfxPacket.TYPE, net.ronm19.phantomism.vfx.VfxPacket.STREAM_CODEC);
        event.registrar("1").playToServer(RiddenOmenDivePayload.TYPE, RiddenOmenDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.OmenPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenAncientDivePayload.TYPE, RiddenAncientDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.AncientPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenReaperDivePayload.TYPE, RiddenReaperDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.ReaperPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenPrimordialDivePayload.TYPE, RiddenPrimordialDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.PrimordialPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenMirageDivePayload.TYPE, RiddenMirageDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.MiragePhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenStrike(player); }
                }));
        event.registrar("1").playToServer(RiddenGolemDivePayload.TYPE, RiddenGolemDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.GolemPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenKingDivePayload.TYPE, RiddenKingDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.PhantomKing phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenDarkDivePayload.TYPE, RiddenDarkDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.DarkPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenVoidDivePayload.TYPE, RiddenVoidDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.VoidPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenAngelDivePayload.TYPE, RiddenAngelDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.AngelPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenSpectralDivePayload.TYPE, RiddenSpectralDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.SpectralPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenDemonStrikePayload.TYPE, RiddenDemonStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.DemonPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenStrike(player); }
                }));
        event.registrar("1").playToServer(RiddenSentinelRepulsePayload.TYPE, RiddenSentinelRepulsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.SentinelPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenRepulse(player); }
                }));
        event.registrar("1").playToServer(RiddenChronoDivePayload.TYPE, RiddenChronoDivePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.ChronoPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDive(player); }
                }));
        event.registrar("1").playToServer(RiddenRiftDashPayload.TYPE, RiddenRiftDashPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.RiftPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenDash(player); }
                }));
        event.registrar("1").playToServer(RiddenEchoStrikePayload.TYPE, RiddenEchoStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.EchoPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenStrike(player); }
                }));
        event.registrar("1").playToServer(RiddenBloodStrikePayload.TYPE, RiddenBloodStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.BloodPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenStrike(player); }
                }));
        event.registrar("1").playToServer(RiddenVampireBitePayload.TYPE, RiddenVampireBitePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.VampirePhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenBite(player); }
                }));
        event.registrar("1").playToServer(RiddenGravityCrushPayload.TYPE, RiddenGravityCrushPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.GravityPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenCrush(player); }
                }));
        event.registrar("1").playToServer(RiddenWitherBitePayload.TYPE, RiddenWitherBitePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof net.ronm19.phantomism.entity.WitherPhantom phantom
                            && phantom.getId() == payload.phantomId()) { phantom.activateRiddenBite(player); }
                }));
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
            if (action.name().startsWith("MIRAGE_")) {
                boolean success = false;
                if (level.getEntity(payload.recipientId()) instanceof net.ronm19.phantomism.entity.MiragePhantom source && source.isOwnedBy(player) && source.isAlive() && source.distanceToSqr(player) <= 80 * 80) {
                    success = switch (action) {
                        case MIRAGE_COPIES -> source.copies(level, false);
                        case MIRAGE_DOUBLE -> source.phantomDouble(level, player);
                        case MIRAGE_FIELD -> source.field(level);
                        case MIRAGE_HALL -> source.hall(level);
                        default -> false;
                    };
                }
                player.sendOverlayMessage(Component.translatable(success ? "message.phantomism.mirage.success" : "message.phantomism.mirage.unavailable")); return;
            }
            if (action.name().startsWith("GOLEM_")) {
                boolean success = level.getEntity(payload.recipientId()) instanceof net.ronm19.phantomism.entity.GolemPhantom source && source.excavation().command(player, action);
                if (!success) { player.sendOverlayMessage(Component.translatable("message.phantomism.golem.unavailable")); } return;
            }
            if (action.name().startsWith("RIFT_")) {
                Entity selected = level.getEntity(payload.recipientId()); boolean success = false;
                if (selected instanceof net.ronm19.phantomism.entity.RiftPhantom rift && rift.isOwnedBy(player) && rift.isAlive() && rift.distanceToSqr(player) <= 80 * 80) {
                    if (action == Action.RIFT_CLOSE) { rift.closeSpatial(); success = true; }
                    else if (action == Action.RIFT_ENTER) { success = rift.enterCorridor(level, player); }
                    else if (action == Action.RIFT_SEND_ALLY && level.getEntity(payload.targetId()) instanceof LivingEntity ally && player.distanceToSqr(ally) <= 48 * 48 && rift.acceptsAlly(ally)) { success = rift.enterCorridor(level, ally); }
                }
                player.sendOverlayMessage(Component.translatable(success ? "message.phantomism.rift.success" : "message.phantomism.rift.unavailable")); return;
            }
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
            if (action != Action.SET_HOME && action != Action.GUARD_POSITION && action != Action.RIFT_ANCHOR && action != Action.RIFT_PORTAL_A && action != Action.RIFT_PORTAL_B && action != Action.GOLEM_PRECISION && action != Action.GOLEM_AREA_A && action != Action.GOLEM_AREA_B && action != Action.GOLEM_CHEST) {
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
            if (action.name().startsWith("RIFT_")) {
                boolean success = false;
                if (level.getEntity(payload.recipientId()) instanceof net.ronm19.phantomism.entity.RiftPhantom rift && rift.isOwnedBy(player) && rift.isAlive() && rift.distanceToSqr(player) <= 80 * 80) {
                    Vec3 destination = Vec3.atBottomCenterOf(position.above());
                    success = switch (action) { case RIFT_ANCHOR -> rift.setAnchor(level, destination); case RIFT_PORTAL_A -> rift.markPortal(level, destination); case RIFT_PORTAL_B -> rift.finishPortal(level, destination); default -> false; };
                }
                player.sendOverlayMessage(Component.translatable(success ? "message.phantomism.rift.success" : "message.phantomism.rift.unavailable")); return;
            }
            if (action.name().startsWith("GOLEM_")) {
                boolean success = level.getEntity(payload.recipientId()) instanceof net.ronm19.phantomism.entity.GolemPhantom source && source.excavation().select(player, action, position);
                player.sendOverlayMessage(Component.translatable(success ? "message.phantomism.golem.selected" : "message.phantomism.golem.unavailable")); return;
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
            case MIRAGE_COPIES, MIRAGE_DOUBLE, MIRAGE_FIELD, MIRAGE_HALL, GOLEM_PRECISION, GOLEM_AREA_A, GOLEM_AREA_B, GOLEM_OBSIDIAN, GOLEM_DELIVER, GOLEM_CANCEL, GOLEM_CHEST, GOLEM_OWNER, GOLEM_STATUS, GOLEM_GUARD, GOLEM_TITAN, GOLEM_RAM_BREAK, RIFT_ANCHOR, RIFT_PORTAL_A, RIFT_PORTAL_B, RIFT_ENTER, RIFT_SEND_ALLY, RIFT_CLOSE, GUARD_POSITION, SET_HOME -> {
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
        COLLECT,
        RIFT_ANCHOR, RIFT_PORTAL_A, RIFT_PORTAL_B, RIFT_ENTER, RIFT_SEND_ALLY, RIFT_CLOSE,
        GOLEM_PRECISION, GOLEM_AREA_A, GOLEM_AREA_B, GOLEM_OBSIDIAN, GOLEM_DELIVER, GOLEM_CANCEL, GOLEM_CHEST, GOLEM_OWNER, GOLEM_STATUS, GOLEM_GUARD, GOLEM_TITAN, GOLEM_RAM_BREAK, MIRAGE_COPIES, MIRAGE_DOUBLE, MIRAGE_FIELD, MIRAGE_HALL
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

    public record RiddenEchoStrikePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenEchoStrikePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_echo_strike"));
        public static final StreamCodec<ByteBuf, RiddenEchoStrikePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenEchoStrikePayload::phantomId, RiddenEchoStrikePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenBloodStrikePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenBloodStrikePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_blood_strike"));
        public static final StreamCodec<ByteBuf, RiddenBloodStrikePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenBloodStrikePayload::phantomId, RiddenBloodStrikePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenVampireBitePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenVampireBitePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_vampire_bite"));
        public static final StreamCodec<ByteBuf, RiddenVampireBitePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenVampireBitePayload::phantomId, RiddenVampireBitePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenGravityCrushPayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenGravityCrushPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_gravity_crush"));
        public static final StreamCodec<ByteBuf, RiddenGravityCrushPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenGravityCrushPayload::phantomId, RiddenGravityCrushPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenChronoDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenChronoDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_chrono_dive"));
        public static final StreamCodec<ByteBuf, RiddenChronoDivePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenChronoDivePayload::phantomId, RiddenChronoDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenOmenDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenOmenDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_omen_dive"));
        public static final StreamCodec<ByteBuf, RiddenOmenDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenOmenDivePayload::phantomId, RiddenOmenDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenAncientDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenAncientDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_ancient_dive"));
        public static final StreamCodec<ByteBuf, RiddenAncientDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenAncientDivePayload::phantomId, RiddenAncientDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenReaperDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenReaperDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_reaper_dive"));
        public static final StreamCodec<ByteBuf, RiddenReaperDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenReaperDivePayload::phantomId, RiddenReaperDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenPrimordialDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenPrimordialDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_primordial_dive"));
        public static final StreamCodec<ByteBuf, RiddenPrimordialDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenPrimordialDivePayload::phantomId, RiddenPrimordialDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenMirageDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenMirageDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_mirage_dive"));
        public static final StreamCodec<ByteBuf, RiddenMirageDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenMirageDivePayload::phantomId, RiddenMirageDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenGolemDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenGolemDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_golem_dive"));
        public static final StreamCodec<ByteBuf, RiddenGolemDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenGolemDivePayload::phantomId, RiddenGolemDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenKingDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenKingDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_king_dive"));
        public static final StreamCodec<ByteBuf, RiddenKingDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenKingDivePayload::phantomId, RiddenKingDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenDarkDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenDarkDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_dark_dive"));
        public static final StreamCodec<ByteBuf, RiddenDarkDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenDarkDivePayload::phantomId, RiddenDarkDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenVoidDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenVoidDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_void_dive"));
        public static final StreamCodec<ByteBuf, RiddenVoidDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenVoidDivePayload::phantomId, RiddenVoidDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenAngelDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenAngelDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_angel_dive"));
        public static final StreamCodec<ByteBuf, RiddenAngelDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenAngelDivePayload::phantomId, RiddenAngelDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenSpectralDivePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenSpectralDivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_spectral_dive"));
        public static final StreamCodec<ByteBuf, RiddenSpectralDivePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenSpectralDivePayload::phantomId, RiddenSpectralDivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenSpectralDismountPayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenSpectralDismountPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_spectral_dismount"));
        public static final StreamCodec<ByteBuf, RiddenSpectralDismountPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenSpectralDismountPayload::phantomId, RiddenSpectralDismountPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenDemonStrikePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenDemonStrikePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_demon_strike"));
        public static final StreamCodec<ByteBuf, RiddenDemonStrikePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenDemonStrikePayload::phantomId, RiddenDemonStrikePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenSentinelRepulsePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenSentinelRepulsePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_sentinel_repulse"));
        public static final StreamCodec<ByteBuf, RiddenSentinelRepulsePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RiddenSentinelRepulsePayload::phantomId, RiddenSentinelRepulsePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RiddenRiftDashPayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenRiftDashPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_rift_dash"));
        public static final StreamCodec<ByteBuf, RiddenRiftDashPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenRiftDashPayload::phantomId, RiddenRiftDashPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenWitherBitePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenWitherBitePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_wither_bite"));
        public static final StreamCodec<ByteBuf, RiddenWitherBitePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenWitherBitePayload::phantomId, RiddenWitherBitePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RiddenSiegeChargePayload(int phantomId) implements CustomPacketPayload {
        public static final Type<RiddenSiegeChargePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "ridden_siege_charge"));
        public static final StreamCodec<ByteBuf, RiddenSiegeChargePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RiddenSiegeChargePayload::phantomId, RiddenSiegeChargePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
