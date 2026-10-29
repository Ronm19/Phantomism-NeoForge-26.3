package net.ronm19.phantomism.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.ronm19.phantomism.entity.AbstractHolidayPhantom;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.CreatorPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

/** Records actual server-verified ownership, never discovery/encounter advancements. */
@EventBusSubscriber(modid="phantomism")
public final class MasterOfPhantoms extends SavedData {
    public static final Codec<MasterOfPhantoms> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Codec.STRING.listOf()).optionalFieldOf("tamedSpecies",Map.of()).forGetter(MasterOfPhantoms::encode)
    ).apply(i,MasterOfPhantoms::new));
    public static final SavedDataType<MasterOfPhantoms> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("phantomism","master_of_phantoms"),MasterOfPhantoms::new,CODEC);
    private static final Map<MinecraftServer,Set<String>> REQUIRED=new WeakHashMap<>();
    private final Map<String,Set<String>> tamed=new HashMap<>();
    public MasterOfPhantoms(){this(Map.of());}
    private MasterOfPhantoms(Map<String,List<String>> saved){saved.forEach((owner,species)->tamed.put(owner,new HashSet<>(species)));}
    private Map<String,List<String>> encode(){var result=new HashMap<String,List<String>>();tamed.forEach((owner,species)->result.put(owner,species.stream().sorted().toList()));return result;}
    public static MasterOfPhantoms get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE);}
    public static Set<String> required(ServerLevel level){
        return REQUIRED.computeIfAbsent(level.getServer(),server->{
            var types=new HashSet<String>();
            for(var entry:PhantomismContent.ENTITY_TYPES.getEntries()){
                var preview=entry.get().create(level,EntitySpawnReason.COMMAND);
                if(preview instanceof AbstractPhantom&&!(preview instanceof AbstractHolidayPhantom)&&!(preview instanceof CreatorPhantom)){
                    types.add(BuiltInRegistries.ENTITY_TYPE.getKey(entry.get()).toString());
                }
            }
            return Set.copyOf(types);
        });
    }
    public Set<String> species(UUID owner){return Set.copyOf(tamed.getOrDefault(owner.toString(),Set.of()));}
    public boolean complete(ServerLevel level,UUID owner){var needed=required(level);return !needed.isEmpty()&&species(owner).containsAll(needed);}
    public static boolean record(AbstractPhantom phantom){
        if(!(phantom.level() instanceof ServerLevel level)||!phantom.isTame()||phantom.getOwnerReference()==null){return false;}
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(phantom.getType()).toString();if(!required(level).contains(id)){return false;}
        UUID owner=phantom.getOwnerReference().getUUID();var data=get(level.getServer());boolean wasComplete=data.complete(level,owner);
        if(!data.tamed.computeIfAbsent(owner.toString(),key->new HashSet<>()).add(id)){return false;}
        data.setDirty();
        if(!wasComplete&&data.complete(level,owner)){var player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null){player.sendOverlayMessage(Component.translatable("message.phantomism.creator.collection_complete"));}}
        return true;
    }
    @SubscribeEvent public static void loaded(EntityJoinLevelEvent event){
        if(event.loadedFromDisk()&&event.getEntity() instanceof AbstractPhantom phantom){record(phantom);}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            for(var level:player.level().getServer().getAllLevels()){
                for(var entity:level.getAllEntities()){
                    if(entity instanceof AbstractPhantom phantom&&phantom.isTame()&&phantom.isOwnedBy(player)){record(phantom);}
                }
            }
        }
    }
}
