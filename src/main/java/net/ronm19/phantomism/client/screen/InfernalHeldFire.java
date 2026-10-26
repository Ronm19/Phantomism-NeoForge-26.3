package net.ronm19.phantomism.client.screen;

import java.util.UUID;

public final class InfernalHeldFire {
    private UUID recipient;
    private Object level;
    private long nextTick, lastTick;
    public boolean poll(boolean held, UUID identity, Object world, long tick) {
        if (!held || identity == null || world == null) { recipient = null; level = null; return false; }
        if (!identity.equals(recipient) || world != level || tick < lastTick) { recipient = identity; level = world; nextTick = tick; }
        lastTick = tick;
        if (tick < nextTick) { return false; } nextTick = tick + 2; return true;
    }
}
