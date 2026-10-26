package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ModTags.Items.PHANTOM_STAFFS).add(PhantomismContent.PHANTOM_STAFF.key());
        this.tag(ModTags.Items.PHANTOM_SPAWN_EGGS)
                .add(PhantomismContent.BLACK_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.FIRE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.FROST_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.EARTH_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.WATER_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.STORM_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SAND_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.GOLD_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.CHERRY_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.PHANTOM_DRAGON_SPAWN_EGG.key())
                .add(PhantomismContent.VIOLET_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.TOXIC_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SCULK_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.RAVAGER_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.END_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.ZOMBIE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SKELETON_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.HUSK_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.ECHO_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.BLOOD_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.VAMPIRE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.GRAVITY_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.WITHER_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.CHRONO_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.RIFT_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SENTINEL_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.DEMON_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SPECTRAL_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.ANGEL_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.OMEN_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.ANCIENT_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.REAPER_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.PRIMORDIAL_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.VOID_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.DARK_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.PHANTOM_KING_SPAWN_EGG.key())
                .add(PhantomismContent.GOLEM_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.MIRAGE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.ECLIPSE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SHADOW_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.PLAGUE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.CRYSTAL_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.OBSIDIAN_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.GALE_PHANTOM_SPAWN_EGG.key())
                .add(PhantomismContent.SPORE_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.SOLAR_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.ILLAGER_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.INFERNAL_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.LUNAR_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.AUTUMN_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.HALLOWEEN_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.CHRISTMAS_PHANTOM_SPAWN_EGG.key()).add(PhantomismContent.EASTER_PHANTOM_SPAWN_EGG.key());
    }
}
