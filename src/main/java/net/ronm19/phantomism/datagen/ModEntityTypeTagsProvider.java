package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityTypeIds;
import net.minecraft.tags.EntityTypeTags;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ModEntityTypeTagsProvider extends EntityTypeTagsProvider {
    public ModEntityTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Phantomism.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ModTags.EntityTypes.GRAVITY_MOVEMENT_IMMUNE).add(EntityTypeIds.WITHER).add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WARDEN).add(EntityTypeIds.ELDER_GUARDIAN)
                .addOptionalTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses")));
        this.tag(ModTags.EntityTypes.VAMPIRE_HYPNOSIS_IMMUNE).add(EntityTypeIds.WITHER).add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WARDEN).add(EntityTypeIds.ELDER_GUARDIAN)
                .addOptionalTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses")));
        this.tag(ModTags.EntityTypes.PHANTOMS)
                .add(PhantomismContent.BLACK_PHANTOM.key())
                .add(PhantomismContent.FIRE_PHANTOM.key())
                .add(PhantomismContent.FROST_PHANTOM.key())
                .add(PhantomismContent.EARTH_PHANTOM.key())
                .add(PhantomismContent.WATER_PHANTOM.key())
                .add(PhantomismContent.STORM_PHANTOM.key())
                .add(PhantomismContent.SAND_PHANTOM.key())
                .add(PhantomismContent.GOLD_PHANTOM.key())
                .add(PhantomismContent.CHERRY_PHANTOM.key())
                .add(PhantomismContent.PHANTOM_DRAGON.key())
                .add(PhantomismContent.VIOLET_PHANTOM.key())
                .add(PhantomismContent.TOXIC_PHANTOM.key())
                .add(PhantomismContent.SCULK_PHANTOM.key())
                .add(PhantomismContent.RAVAGER_PHANTOM.key())
                .add(PhantomismContent.END_PHANTOM.key())
                .add(PhantomismContent.ZOMBIE_PHANTOM.key())
                .add(PhantomismContent.SKELETON_PHANTOM.key())
                .add(PhantomismContent.HUSK_PHANTOM.key())
                .add(PhantomismContent.ECHO_PHANTOM.key())
                .add(PhantomismContent.BLOOD_PHANTOM.key())
                .add(PhantomismContent.VAMPIRE_PHANTOM.key())
                .add(PhantomismContent.GRAVITY_PHANTOM.key())
                .add(PhantomismContent.WITHER_PHANTOM.key())
                .add(PhantomismContent.CHRONO_PHANTOM.key())
                .add(PhantomismContent.RIFT_PHANTOM.key())
                .add(PhantomismContent.SENTINEL_PHANTOM.key())
                .add(PhantomismContent.DEMON_PHANTOM.key())
                .add(PhantomismContent.SPECTRAL_PHANTOM.key())
                .add(PhantomismContent.ANGEL_PHANTOM.key())
                .add(PhantomismContent.OMEN_PHANTOM.key())
                .add(PhantomismContent.ANCIENT_PHANTOM.key())
                .add(PhantomismContent.REAPER_PHANTOM.key())
                .add(PhantomismContent.PRIMORDIAL_PHANTOM.key())
                .add(PhantomismContent.VOID_PHANTOM.key())
                .add(PhantomismContent.DARK_PHANTOM.key())
                .add(PhantomismContent.PHANTOM_KING.key())
                .add(PhantomismContent.GOLEM_PHANTOM.key())
                .add(PhantomismContent.MIRAGE_PHANTOM.key())
                .add(PhantomismContent.ECLIPSE_PHANTOM.key())
                .add(PhantomismContent.SHADOW_PHANTOM.key())
                .add(PhantomismContent.PLAGUE_PHANTOM.key())
                .add(PhantomismContent.CRYSTAL_PHANTOM.key())
                .add(PhantomismContent.OBSIDIAN_PHANTOM.key())
                .add(PhantomismContent.GALE_PHANTOM.key())
                .add(PhantomismContent.SPORE_PHANTOM.key()).add(PhantomismContent.SOLAR_PHANTOM.key()).add(PhantomismContent.ILLAGER_PHANTOM.key()).add(PhantomismContent.INFERNAL_PHANTOM.key()).add(PhantomismContent.LUNAR_PHANTOM.key()).add(PhantomismContent.AUTUMN_PHANTOM.key()).add(PhantomismContent.HALLOWEEN_PHANTOM.key()).add(PhantomismContent.CHRISTMAS_PHANTOM.key()).add(PhantomismContent.EASTER_PHANTOM.key()).add(PhantomismContent.FIREWORK_PHANTOM.key()).add(PhantomismContent.ST_PATRICKS_PHANTOM.key()).add(PhantomismContent.VALENTINES_PHANTOM.key()).add(PhantomismContent.CREATOR_PHANTOM.key()).add(PhantomismContent.NEW_YEARS_PHANTOM.key());
        this.tag(ModTags.EntityTypes.ZOMBIE_PRESENCE_ELIGIBLE)
                .add(EntityTypeIds.ZOMBIE).add(EntityTypeIds.HUSK).add(EntityTypeIds.DROWNED).add(EntityTypeIds.ZOMBIE_VILLAGER);
        this.tag(EntityTypeTags.UNDEAD).add(PhantomismContent.ZOMBIE_PHANTOM.key());
        this.tag(EntityTypeTags.INVERTED_HEALING_AND_HARM).add(PhantomismContent.ZOMBIE_PHANTOM.key());
        this.tag(EntityTypeTags.UNDEAD).add(PhantomismContent.HUSK_PHANTOM.key());
        this.tag(EntityTypeTags.INVERTED_HEALING_AND_HARM).add(PhantomismContent.HUSK_PHANTOM.key());
        this.tag(ModTags.EntityTypes.HUSK_EXHAUSTION_RESISTANT).add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).addOptionalTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses")));
        this.tag(EntityTypeTags.UNDEAD).add(PhantomismContent.SKELETON_PHANTOM.key());
        this.tag(EntityTypeTags.INVERTED_HEALING_AND_HARM).add(PhantomismContent.SKELETON_PHANTOM.key());
        this.tag(ModTags.EntityTypes.RAVAGER_FEAR_IMMUNE)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN).add(EntityTypeIds.ELDER_GUARDIAN)
                .addOptionalTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses")));
        this.tag(ModTags.EntityTypes.TOXIC_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.CHERRY_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.GOLD_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.EARTH_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.SAND_CONTROL_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(ModTags.EntityTypes.FROST_RESISTANT)
                .add(EntityTypeIds.ENDER_DRAGON).add(EntityTypeIds.WITHER).add(EntityTypeIds.WARDEN)
                .add(EntityTypeIds.ELDER_GUARDIAN).add(EntityTypeIds.RAVAGER);
        this.tag(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES).add(PhantomismContent.FROST_PHANTOM.key());
    }
}
