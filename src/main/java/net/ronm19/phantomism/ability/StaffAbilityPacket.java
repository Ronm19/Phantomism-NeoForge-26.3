package net.ronm19.phantomism.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StaffAbilityPacket(String recipient, String ability, String target, BlockPos position, boolean activate) implements CustomPacketPayload {
    public static final Type<StaffAbilityPacket> TYPE = new Type<>(PhantomAbilities.id("staff_ability_request"));
    public static final StreamCodec<ByteBuf, StaffAbilityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), StaffAbilityPacket::recipient, ByteBufCodecs.stringUtf8(128), StaffAbilityPacket::ability,
            ByteBufCodecs.stringUtf8(64), StaffAbilityPacket::target, BlockPos.STREAM_CODEC, StaffAbilityPacket::position,
            ByteBufCodecs.BOOL, StaffAbilityPacket::activate, StaffAbilityPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Snapshot(String player, String dimension, String recipient, String data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PhantomAbilities.id("staff_ability_snapshot"));
        public static final StreamCodec<ByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Snapshot::player, ByteBufCodecs.stringUtf8(256), Snapshot::dimension,
                ByteBufCodecs.stringUtf8(64), Snapshot::recipient, ByteBufCodecs.stringUtf8(49152), Snapshot::data, Snapshot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
