package net.ronm19.phantomism.entity.spawn;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class IllagerEncounterData extends SavedData {
    public static final Codec<IllagerEncounterData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(IllagerEncounterData::new, data -> data.receipts);
    public static final SavedDataType<IllagerEncounterData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("phantomism", "illager_encounters"), IllagerEncounterData::new, CODEC);
    private final Map<String, Long> receipts = new HashMap<>();
    public IllagerEncounterData() {}
    private IllagerEncounterData(Map<String, Long> entries) { entries.entrySet().stream().filter(entry -> entry.getKey().length() <= 256 && entry.getValue() >= 0).limit(2048).forEach(entry -> receipts.put(entry.getKey(), entry.getValue())); }
    public static IllagerEncounterData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public boolean claim(String identity, long now, long duration) {
        if (identity == null || identity.length() > 256 || receipts.getOrDefault(identity, 0L) > now) { return false; }
        receipts.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (receipts.size() >= 2048) { return false; }
        receipts.put(identity, now + Math.clamp(duration, 200, 192000)); setDirty(); return true;
    }
}
