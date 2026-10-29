package net.ronm19.phantomism.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.ronm19.phantomism.progression.CreatorRevelation;

/** Keeps vanilla egg behavior; the client replaces only its viewer-specific identity line. */
public final class CreatorSpawnEggItem extends SpawnEggItem {
    public CreatorSpawnEggItem(Properties properties){super(properties);}
    @Override public Component getName(ItemStack stack){return Component.empty().append(CreatorRevelation.name(false)).append(" ").append(Component.translatable("item.phantomism.creator_phantom_spawn_egg.suffix"));}
}
