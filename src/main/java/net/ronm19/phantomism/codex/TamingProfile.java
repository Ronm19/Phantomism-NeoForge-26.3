package net.ronm19.phantomism.codex;

import net.minecraft.world.item.Item;
import net.ronm19.phantomism.registry.PhantomismContent;

public record TamingProfile(Item item, float chance, int guaranteedAttempt, String conditions) {
    public static TamingProfile staffOnly() {
        return new TamingProfile(PhantomismContent.PHANTOM_STAFF.get(), 1F, 1,
                "Staff only in the current implementation. Phantom Membranes heal or breed; they do not tame. No item-based temptation goal is registered.");
    }
}
