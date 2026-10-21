package net.ronm19.phantomism.vfx;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;

public record VfxPacket(String dimension, Identifier effect, UUID caster, UUID session, int entityId, UUID attached,
        Vec3 position, Vec3 direction, int duration, float radius, int primary, int secondary, long seed) implements CustomPacketPayload {
    public static final Type<VfxPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "cinematic_vfx"));
    public VfxPacket {
        float maximumRadius = effect.equals(RoyalVfx.DOMINION) || effect.equals(RoyalVfx.FINALE) ? 40 : effect.equals(PrimordialVfx.DOMAIN) || effect.equals(PrimordialVfx.CONCLUSION) ? 36 : effect.equals(AncientVfx.FORGOTTEN) || effect.equals(AncientVfx.CONCLUSION) ? 35 : effect.equals(OmenVfx.CALAMITY) || effect.equals(OmenVfx.CONCLUSION) ? 28 : effect.equals(DemonVfx.APOCALYPSE) || effect.equals(DemonVfx.FINALE) ? 25 : 24;
        duration = Math.clamp(duration, 1, 800); radius = Float.isFinite(radius) ? Math.clamp(radius, .05F, maximumRadius) : 1;
        if (!Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30000000 || Math.abs(position.z) > 30000000 || Math.abs(position.y) > 30000000) { throw new IllegalArgumentException("Invalid VFX position"); }
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 16384) { throw new IllegalArgumentException("Invalid VFX vector"); }
        primary &= 0xFFFFFF; secondary &= 0xFFFFFF;
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, VfxPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public VfxPacket decode(RegistryFriendlyByteBuf buffer) {
            return new VfxPacket(buffer.readUtf(128), Identifier.parse(buffer.readUtf(128)), uuid(buffer), uuid(buffer), buffer.readVarInt(), uuid(buffer),
                    vector(buffer), vector(buffer), buffer.readVarInt(), buffer.readFloat(), buffer.readInt(), buffer.readInt(), buffer.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, VfxPacket value) {
            buffer.writeUtf(value.dimension, 128); buffer.writeUtf(value.effect.toString(), 128); uuid(buffer, value.caster); uuid(buffer, value.session); buffer.writeVarInt(value.entityId); uuid(buffer, value.attached);
            vector(buffer, value.position); vector(buffer, value.direction); buffer.writeVarInt(value.duration); buffer.writeFloat(value.radius); buffer.writeInt(value.primary); buffer.writeInt(value.secondary); buffer.writeLong(value.seed);
        }
        private UUID uuid(RegistryFriendlyByteBuf buffer) { return new UUID(buffer.readLong(), buffer.readLong()); }
        private void uuid(RegistryFriendlyByteBuf buffer, UUID value) { buffer.writeLong(value.getMostSignificantBits()); buffer.writeLong(value.getLeastSignificantBits()); }
        private Vec3 vector(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
        private void vector(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
