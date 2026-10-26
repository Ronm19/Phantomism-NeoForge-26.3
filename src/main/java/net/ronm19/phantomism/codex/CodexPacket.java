package net.ronm19.phantomism.codex;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CodexPacket(boolean open, String player, String dimension, String data) implements CustomPacketPayload {
    public static final Type<CodexPacket> TYPE = new Type<>(CodexCatalog.id("codex_snapshot"));
    public static final StreamCodec<ByteBuf, CodexPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CodexPacket::open, ByteBufCodecs.stringUtf8(64), CodexPacket::player,
            ByteBufCodecs.stringUtf8(256), CodexPacket::dimension, ByteBufCodecs.stringUtf8(CodexCatalog.MAX_DATA), CodexPacket::data, CodexPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CodexCatalog.id("codex_refresh"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
