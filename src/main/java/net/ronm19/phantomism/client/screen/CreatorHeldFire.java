package net.ronm19.phantomism.client.screen;

import java.util.UUID;
import net.ronm19.phantomism.entity.CreatorPhantom;

/** Batches held G into one request every four ticks; the server still validates each request. */
public final class CreatorHeldFire {
    private UUID recipient;
    private Object level;
    private long nextTick, lastTick;

    public boolean poll(boolean held, UUID identity, Object world, long tick) {
        if (!held || identity == null || world == null) { recipient = null; level = null; return false; }
        if (!identity.equals(recipient) || world != level || tick < lastTick) {
            recipient = identity; level = world; nextTick = tick;
        }
        lastTick = tick;
        if (tick < nextTick) { return false; }
        nextTick = tick + CreatorPhantom.MOUNTED_BEAM_INTERVAL;
        return true;
    }
}
