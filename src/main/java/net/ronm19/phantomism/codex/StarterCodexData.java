package net.ronm19.phantomism.codex;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class StarterCodexData extends SavedData {
    public static final Codec<StarterCodexData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.BOOL).xmap(StarterCodexData::new, data -> data.granted);
    public static final SavedDataType<StarterCodexData> TYPE = new SavedDataType<>(CodexCatalog.id("starter_codex"), StarterCodexData::new, CODEC);
    private final Map<String, Boolean> granted;

    public StarterCodexData() {
        this(Map.of());
    }

    private StarterCodexData(Map<String, Boolean> granted) {
        this.granted = new HashMap<>(granted);
    }

    public boolean granted(UUID player) {
        return granted.getOrDefault(player.toString(), false);
    }

    public boolean deliver(ServerPlayer player) {
        if (granted(player.getUUID())) {
            return false;
        }
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0 || !player.isAlive()) {
            return false;
        }
        player.getInventory().setItem(slot, new ItemStack(PhantomismContent.PHANTOM_CODEX.get()));
        granted.put(player.getUUID().toString(), true);
        setDirty();
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
