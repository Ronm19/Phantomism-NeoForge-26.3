package net.ronm19.phantomism.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.ronm19.phantomism.registry.PhantomismContent;

/** Overworld saved data keeps active bonds even while their chunks are unloaded. */
@EventBusSubscriber(modid="phantomism")
public final class CreatorBonds extends SavedData {
    public static final Codec<CreatorBonds> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("bonds",Map.of()).forGetter(v->v.bonds),
        Codec.STRING.listOf().optionalFieldOf("crafted",List.of()).forGetter(v->List.copyOf(v.crafted)),
        Codec.STRING.listOf().optionalFieldOf("revealed",List.of()).forGetter(v->List.copyOf(v.revealed)),
        Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("manifestations",Map.of()).forGetter(v->v.manifestations)
    ).apply(i,CreatorBonds::new));
    public static final SavedDataType<CreatorBonds> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("phantomism","creator_bonds"),CreatorBonds::new,CODEC);
    private final Map<String,String> bonds;
    private final Set<String> crafted,revealed;
    private final Map<String,String> manifestations;
    public CreatorBonds(){this(Map.of(),List.of(),List.of(),Map.of());}
    private CreatorBonds(Map<String,String> bonds,List<String> crafted,List<String> revealed,Map<String,String> manifestations){this.bonds=new HashMap<>(bonds);this.crafted=new HashSet<>(crafted);this.revealed=new HashSet<>(revealed);this.manifestations=new HashMap<>(manifestations);}
    public static CreatorBonds get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE);}
    public UUID active(UUID owner){String value=bonds.get(owner.toString());if(value==null){return null;}try{return UUID.fromString(value);}catch(IllegalArgumentException error){return null;}}
    public boolean claim(UUID owner,UUID entity){UUID previous=active(owner);if(previous!=null&&!previous.equals(entity)){return false;}if(previous==null){bonds.put(owner.toString(),entity.toString());setDirty();}return true;}
    public void release(UUID owner,UUID entity){if(entity.equals(active(owner))){bonds.remove(owner.toString());setDirty();}}
    public boolean crafted(UUID player){return crafted.contains(player.toString());}
    public void recordCraft(UUID player){if(crafted.add(player.toString())){setDirty();}}
    public boolean revealed(UUID player){return revealed.contains(player.toString());}
    public void reveal(UUID player){if(revealed.add(player.toString())){setDirty();}}
    public UUID manifestation(UUID player){try{return UUID.fromString(manifestations.get(player.toString()));}catch(IllegalArgumentException|NullPointerException error){return null;}}
    public boolean reserve(UUID player,UUID entity){UUID previous=manifestation(player);if(previous!=null&&!previous.equals(entity)){return false;}manifestations.put(player.toString(),entity.toString());setDirty();return true;}
    public void releaseManifestation(UUID player,UUID entity){if(entity.equals(manifestation(player))){manifestations.remove(player.toString());setDirty();}}
    @SubscribeEvent public static void craft(PlayerEvent.ItemCraftedEvent event){if(event.getEntity() instanceof ServerPlayer player&&event.getCrafting().is(PhantomismContent.EYE_OF_CREATION.get())){get(player.level().getServer()).recordCraft(player.getUUID());}}
}
