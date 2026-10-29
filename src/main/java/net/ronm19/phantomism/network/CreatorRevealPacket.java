package net.ronm19.phantomism.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A private viewer state, not an entity-wide revealed flag. */
public record CreatorRevealPacket(String player,String dimension,boolean revealed) implements CustomPacketPayload {
    public static final Type<CreatorRevealPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("phantomism","creator_reveal"));
    public static final StreamCodec<ByteBuf,CreatorRevealPacket> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64),CreatorRevealPacket::player,ByteBufCodecs.stringUtf8(256),CreatorRevealPacket::dimension,
            ByteBufCodecs.BOOL,CreatorRevealPacket::revealed,CreatorRevealPacket::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
