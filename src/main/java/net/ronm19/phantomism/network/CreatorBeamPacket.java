package net.ronm19.phantomism.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ronm19.phantomism.entity.CreatorPhantom;

/** One bounded snapshot for all branches; never one packet per decorative ray. */
public record CreatorBeamPacket(String dimension,int casterId,UUID caster,UUID session,int remaining,int mode,boolean clustered,List<Branch> branches) implements CustomPacketPayload {
    public record Branch(int entityId,UUID uuid,Vec3 endpoint) {}
    public static final Type<CreatorBeamPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("phantomism","creator_beams"));
    public CreatorBeamPacket {
        branches=List.copyOf(branches);
        if(remaining<0||remaining>60||mode<0||mode>3||branches.size()>64||branches.stream().map(Branch::uuid).distinct().count()!=branches.size()){throw new IllegalArgumentException("Invalid Creator beam snapshot");}
        for(var branch:branches){if(!Double.isFinite(branch.endpoint().lengthSqr())||branch.endpoint().lengthSqr()>3e15){throw new IllegalArgumentException("Invalid beam endpoint");}}
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,CreatorBeamPacket> STREAM_CODEC=new StreamCodec<>(){
        private UUID readId(RegistryFriendlyByteBuf b){return new UUID(b.readLong(),b.readLong());}
        private void writeId(RegistryFriendlyByteBuf b,UUID id){b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}
        @Override public CreatorBeamPacket decode(RegistryFriendlyByteBuf b){String dimension=b.readUtf(128);int casterId=b.readVarInt();UUID caster=readId(b),session=readId(b);int remaining=b.readVarInt(),mode=b.readVarInt();boolean clustered=b.readBoolean();int count=b.readVarInt();if(count<0||count>64){throw new IllegalArgumentException("Beam count");}var entries=new ArrayList<Branch>(count);for(int i=0;i<count;i++){entries.add(new Branch(b.readVarInt(),readId(b),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));}return new CreatorBeamPacket(dimension,casterId,caster,session,remaining,mode,clustered,entries);}
        @Override public void encode(RegistryFriendlyByteBuf b,CreatorBeamPacket p){b.writeUtf(p.dimension,128);b.writeVarInt(p.casterId);writeId(b,p.caster);writeId(b,p.session);b.writeVarInt(p.remaining);b.writeVarInt(p.mode);b.writeBoolean(p.clustered);b.writeVarInt(p.branches.size());for(var entry:p.branches){b.writeVarInt(entry.entityId());writeId(b,entry.uuid());b.writeDouble(entry.endpoint().x);b.writeDouble(entry.endpoint().y);b.writeDouble(entry.endpoint().z);}}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(CreatorPhantom source,int remaining,int mode,boolean clustered,List<Branch> branches){
        if(!(source.level() instanceof ServerLevel level)){return;}
        var packet=new CreatorBeamPacket(level.dimension().toString(),source.getId(),source.getUUID(),source.session(),remaining,mode,clustered,branches);
        for(var player:level.players()){if(player.connection!=null&&player.distanceToSqr(source)<=72*72){PacketDistributor.sendToPlayer(player,packet);}}
    }
}
