package net.ronm19.phantomism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ronm19.phantomism.entity.CreatorPhantom;
import net.ronm19.phantomism.progression.CreatorBonds;
import net.ronm19.phantomism.progression.CreatorSummoning;

/** Account certification skips progression only; the actual companion is unchanged. */
@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class CreatorCertification {
    /** Edit this collection in your IDE, then rebuild. Use real authenticated Minecraft account names.
     * Matching ignores case. This is combined with the server configuration allowlist.
     * BLOCK_ALL, disabled certification and the independent acquisition lock still override it.
     */
    private static final java.util.Set<String> CERTIFIED_USERNAMES = java.util.Set.of(
            "maxgaming6",
            "dev"

        // Add actual Minecraft usernames here, in double quotes, separated by commas.
        // Intentionally empty: no developer or local Windows account gets an implicit privilege.
    );
    public enum Mode { NORMAL, FORCE_TEST, BLOCK_ALL }
    public enum Result { GRANTED, ALREADY_CLAIMED, ALREADY_OWNS, NOT_CERTIFIED, UNAUTHENTICATED, BLOCKED, NO_SPACE, TEST_DISABLED }
    public static final class State extends SavedData {
        public static final Codec<State> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("accounts",Map.of()).forGetter(s->s.accounts),
            Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("grants",Map.of()).forGetter(s->s.grants)
        ).apply(i,State::new));
        public static final SavedDataType<State> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("phantomism","creator_certification"),State::new,CODEC);
        private final Map<String,String> accounts,grants;
        public State(){this(Map.of(),Map.of());}
        private State(Map<String,String> accounts,Map<String,String> grants){this.accounts=new HashMap<>(accounts);this.grants=new HashMap<>(grants);}
        public boolean claimed(UUID player){return grants.containsKey(player.toString());}
        public String grantedEntity(UUID player){return grants.get(player.toString());}
        public String pinnedAccount(String name){return accounts.get(name.toLowerCase(Locale.ROOT));}
        private void pin(String name,UUID player){accounts.put(name.toLowerCase(Locale.ROOT),player.toString());setDirty();}
        private void grant(UUID player,UUID entity){grants.put(player.toString(),entity.toString());setDirty();}
        private void reset(UUID player){grants.remove(player.toString());setDirty();}
    }
    private static final Map<MinecraftServer,Map<UUID,Integer>> PENDING=new WeakHashMap<>();
    private CreatorCertification(){}
    public static State state(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);}
    public static boolean acquisitionLocked(){return Config.SPEC.isLoaded()&&Config.CREATOR_ACQUISITION_LOCK.get();}
    public static Mode mode(){return Config.SPEC.isLoaded()?Config.CREATOR_CERTIFICATION_MODE.get():Mode.NORMAL;}
    public static boolean blocked(){return acquisitionLocked()||!Config.SPEC.isLoaded()||!Config.CREATOR_CERTIFICATION_ENABLED.get()||mode()==Mode.BLOCK_ALL;}
    public static boolean authenticated(ServerPlayer player){
        var server=player.level().getServer();
        // Dedicated offline-mode names are forgeable and never grant NORMAL privileges.
        // The integrated server's real session owner is trusted by its UUID only.
        var local=server.getSingleplayerProfile();
        return server.usesAuthentication()||server.isSingleplayer()&&local!=null&&local.id().equals(player.getUUID());
    }
    public static boolean certified(ServerPlayer player){
        if(!authenticated(player)){return false;}var server=player.level().getServer();var saved=state(server);String actual=player.getGameProfile().name().toLowerCase(Locale.ROOT);UUID id=player.getUUID();
        var allowed=new java.util.HashSet<String>(CERTIFIED_USERNAMES);allowed.addAll(Config.CREATOR_CERTIFIED_PLAYERS.get());
        for(String configured:allowed){
            String name=configured.toLowerCase(Locale.ROOT);String pinned=saved.accounts.get(name);
            if(pinned!=null){if(pinned.equals(id.toString())){return true;}continue;}
            if(name.equals(actual)){saved.pin(name,id);return true;}
        }
        return false;
    }
    public static Result grant(ServerPlayer player,boolean forceTest){
        if(blocked()){return Result.BLOCKED;}if(player.isSpectator()||!player.isAlive()){return Result.NO_SPACE;}
        if(forceTest&&mode()!=Mode.FORCE_TEST){return Result.TEST_DISABLED;}
        if(!forceTest&&!authenticated(player)){return Result.UNAUTHENTICATED;}
        if(!forceTest&&!certified(player)){return Result.NOT_CERTIFIED;}
        var server=player.level().getServer();var saved=state(server);UUID owner=player.getUUID();
        if(saved.claimed(owner)){return Result.ALREADY_CLAIMED;}
        UUID existing=CreatorBonds.get(server).active(owner);
        if(existing!=null){saved.grant(owner,existing);return Result.ALREADY_OWNS;}
        CreatorPhantom companion=CreatorSummoning.summon(player,player.blockPosition());
        if(companion==null||!companion.isTame()||!companion.isOwnedBy(player)||companion.isRemoved()){return Result.NO_SPACE;}
        saved.grant(owner,companion.getUUID());player.sendOverlayMessage(Component.translatable("message.phantomism.creator.certification.granted"));return Result.GRANTED;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||blocked()){return;}
        var server=player.level().getServer();if(!certified(player)||state(server).claimed(player.getUUID())){return;}
        var pending=PENDING.computeIfAbsent(server,s->new HashMap<>());if(pending.size()<1024){pending.put(player.getUUID(),server.getTickCount()+40);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();var pending=PENDING.get(server);if(pending==null){return;}
        int attempts=0;for(var entry:List.copyOf(pending.entrySet())){
            if(blocked()){pending.clear();return;}if(server.getTickCount()<entry.getValue()){continue;}if(attempts++>=2){break;}pending.remove(entry.getKey());var player=server.getPlayerList().getPlayer(entry.getKey());if(player!=null){grant(player,false);}
        }
        if(pending.isEmpty()){PENDING.remove(server);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){var pending=PENDING.get(player.level().getServer());if(pending!=null){pending.remove(player.getUUID());}}}
    @SubscribeEvent public static void joining(EntityJoinLevelEvent event){
        if(!acquisitionLocked()||event.loadedFromDisk()||event.getLevel().isClientSide()||!(event.getEntity() instanceof CreatorPhantom creator)){return;}
        UUID owner=creator.getOwnerReference()==null?null:creator.getOwnerReference().getUUID();
        if(owner==null||!creator.getUUID().equals(CreatorBonds.get(event.getLevel().getServer()).active(owner))){event.setCanceled(true);}
    }
    private static int action(net.minecraft.commands.CommandSourceStack source,ServerPlayer target,boolean reset){
        Result result;
        if(reset){if(blocked()){result=Result.BLOCKED;}else if(mode()!=Mode.FORCE_TEST){result=Result.TEST_DISABLED;}else if(CreatorBonds.get(source.getServer()).active(target.getUUID())!=null){result=Result.ALREADY_OWNS;}else{state(source.getServer()).reset(target.getUUID());source.sendSuccess(()->Component.translatable("message.phantomism.creator.certification.reset",target.getGameProfile().name()),true);return 1;}}
        else{result=grant(target,true);}
        Result response=result;source.sendSuccess(()->Component.translatable("message.phantomism.creator.certification.result",target.getGameProfile().name(),response.name()),true);return result==Result.GRANTED?1:0;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("phantomism").then(Commands.literal("creator").then(Commands.literal("certification")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(Commands.literal("grant").then(Commands.argument("player",EntityArgument.player()).executes(ctx->action(ctx.getSource(),EntityArgument.getPlayer(ctx,"player"),false))))
            .then(Commands.literal("reset").then(Commands.argument("player",EntityArgument.player()).executes(ctx->action(ctx.getSource(),EntityArgument.getPlayer(ctx,"player"),true))))
            .then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(ctx->{var player=EntityArgument.getPlayer(ctx,"player");ctx.getSource().sendSuccess(()->Component.translatable("message.phantomism.creator.certification.status",mode().name(),blocked(),player.getGameProfile().name(),state(ctx.getSource().getServer()).claimed(player.getUUID())),false);return 1;}))))));
    }
}
