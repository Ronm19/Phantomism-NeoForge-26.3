package net.ronm19.phantomism.entity.ai;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.ronm19.phantomism.codex.CodexCatalog;

@net.neoforged.fml.common.EventBusSubscriber(modid = "phantomism")
public final class CelestialPacts extends SavedData {
    public enum Mode { PERSISTENT, TIMED, SESSION, BATTLE }
    public static final Codec<CelestialPacts> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(CelestialPacts::new, data -> data.durable);
    public static final SavedDataType<CelestialPacts> TYPE = new SavedDataType<>(CodexCatalog.id("celestial_pacts"), CelestialPacts::new, CODEC);
    private final Map<String, Long> durable = new HashMap<>();
    private final Map<String, Grant> temporary = new HashMap<>();
    public CelestialPacts() { this(Map.of()); }
    private CelestialPacts(Map<String, Long> entries) {
        entries.entrySet().stream().limit(4096).filter(entry -> valid(entry.getKey()) && entry.getValue() > 0).forEach(entry -> durable.put(entry.getKey(), entry.getValue()));
    }
    private static boolean valid(String key) {
        try { String[] pair = key.split(":"); return pair.length == 2 && !UUID.fromString(pair[0]).equals(UUID.fromString(pair[1])); }
        catch (IllegalArgumentException failure) { return false; }
    }
    private static String key(UUID from, UUID to) { return from + ":" + to; }
    public static CelestialPacts get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    private boolean actor(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player && get(player.level().getServer()) == this; }
    public boolean offer(ServerPlayer player, UUID counterpart, Mode mode, long duration) {
        if (!actor(player) || counterpart == null || counterpart.equals(player.getUUID()) || mode == null) { return false; }
        long now = player.level().getServer().overworld().getGameTime(); prune(now);
        String key = key(player.getUUID(), counterpart);
        if (!durable.containsKey(key) && !temporary.containsKey(key) && durable.size() + temporary.size() >= 4096) { return false; }
        durable.remove(key); temporary.remove(key);
        if (mode == Mode.PERSISTENT) { durable.put(key, Long.MAX_VALUE); }
        else if (mode == Mode.TIMED) { durable.put(key, now + Math.clamp(duration, 20L, 12096000L)); }
        else { temporary.put(key, new Grant(mode, mode == Mode.SESSION ? Long.MAX_VALUE : now + 200)); }
        setDirty(); return true;
    }
    public boolean revoke(ServerPlayer player, UUID counterpart) {
        if (!actor(player) || counterpart == null) { return false; }
        String outward = key(player.getUUID(), counterpart), inward = key(counterpart, player.getUUID());
        boolean changed = durable.remove(outward) != null | durable.remove(inward) != null | temporary.remove(outward) != null | temporary.remove(inward) != null;
        if (changed) { setDirty(); } return changed;
    }
    public boolean allows(UUID first, UUID second, long now) {
        return first != null && second != null && (first.equals(second) || permits(key(first, second), now) && permits(key(second, first), now));
    }
    private boolean permits(String key, long now) { return durable.getOrDefault(key, 0L) > now || temporary.containsKey(key) && temporary.get(key).until > now; }
    public void touchBattle(UUID first, UUID second, long now) {
        if (!allows(first, second, now)) { return; }
        for (String key : new String[]{key(first, second), key(second, first)}) {
            Grant grant = temporary.get(key); if (grant != null && grant.mode == Mode.BATTLE) { temporary.put(key, new Grant(Mode.BATTLE, now + 200)); }
        }
    }
    private void prune(long now) { if (durable.entrySet().removeIf(entry -> entry.getValue() <= now)) { setDirty(); } temporary.entrySet().removeIf(entry -> entry.getValue().until <= now); }
    private record Grant(Mode mode, long until) {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String identity = player.getUUID().toString();
            get(player.level().getServer()).temporary.keySet().removeIf(key -> key.startsWith(identity + ":") || key.endsWith(":" + identity));
        }
    }
}
