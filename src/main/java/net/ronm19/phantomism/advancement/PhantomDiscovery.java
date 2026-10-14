package net.ronm19.phantomism.advancement;

import java.util.function.Supplier;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import org.jspecify.annotations.Nullable;

public enum PhantomDiscovery {
    BLACK("black_phantom", PhantomismContent.BLACK_PHANTOM, PhantomismContent.BLACK_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    FIRE("fire_phantom", PhantomismContent.FIRE_PHANTOM, PhantomismContent.FIRE_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    FROST("frost_phantom", PhantomismContent.FROST_PHANTOM, PhantomismContent.FROST_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    EARTH("earth_phantom", PhantomismContent.EARTH_PHANTOM, PhantomismContent.EARTH_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    WATER("water_phantom", PhantomismContent.WATER_PHANTOM, PhantomismContent.WATER_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    STORM("storm_phantom", PhantomismContent.STORM_PHANTOM, PhantomismContent.STORM_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    SAND("sand_phantom", PhantomismContent.SAND_PHANTOM, PhantomismContent.SAND_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    GOLD("gold_phantom", PhantomismContent.GOLD_PHANTOM, PhantomismContent.GOLD_PHANTOM_SPAWN_EGG, Rarity.RARE),
    CHERRY("cherry_phantom", PhantomismContent.CHERRY_PHANTOM, PhantomismContent.CHERRY_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    DRAGON("phantom_dragon", PhantomismContent.PHANTOM_DRAGON, PhantomismContent.PHANTOM_DRAGON_SPAWN_EGG, Rarity.RARE),
    VIOLET("violet_phantom", PhantomismContent.VIOLET_PHANTOM, PhantomismContent.VIOLET_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    TOXIC("toxic_phantom", PhantomismContent.TOXIC_PHANTOM, PhantomismContent.TOXIC_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    SCULK("sculk_phantom", PhantomismContent.SCULK_PHANTOM, PhantomismContent.SCULK_PHANTOM_SPAWN_EGG, Rarity.RARE),
    RAVAGER("ravager_phantom", PhantomismContent.RAVAGER_PHANTOM, PhantomismContent.RAVAGER_PHANTOM_SPAWN_EGG, Rarity.RARE);

    public static final String ICON_DATA_KEY = "phantomism_discovery_icon";
    public static final String CRITERION = "discovered";
    public static final Identifier ROOT_ID = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "discovery/root");

    private final String name;
    private final Supplier<? extends EntityType<?>> entityType;
    private final Supplier<? extends Item> iconItem;
    private final Rarity rarity;

    PhantomDiscovery(String name, Supplier<? extends EntityType<?>> entityType, Supplier<? extends Item> iconItem, Rarity rarity) {
        this.name = name;
        this.entityType = entityType;
        this.iconItem = iconItem;
        this.rarity = rarity;
    }

    public String modelName() {
        return this.name;
    }

    public EntityType<?> entityType() {
        return this.entityType.get();
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public @Nullable AbstractPhantom createPreview(Level level) {
        Entity entity = this.entityType().create(level, EntitySpawnReason.LOAD);
        if (entity instanceof AbstractPhantom phantom) {
            phantom.setId(-this.ordinal() - 1);
            return phantom;
        }
        return null;
    }

    public Identifier advancementId() {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "discovery/" + this.name);
    }

    public String translationKey(String suffix) {
        return "advancements.phantomism.discovery." + this.name + "." + suffix;
    }

    public ItemStackTemplate icon() {
        CompoundTag data = new CompoundTag();
        data.putString(ICON_DATA_KEY, this.name);
        return new ItemStackTemplate(this.iconItem.get(), DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(data)).build());
    }

    public enum Rarity {
        COMMON(AdvancementType.TASK, 40),
        UNCOMMON(AdvancementType.TASK, 28),
        RARE(AdvancementType.GOAL, 16),
        EPIC(AdvancementType.CHALLENGE, 6);

        private final AdvancementType frame;
        private final int spawnWeight;

        Rarity(AdvancementType frame, int spawnWeight) {
            this.frame = frame;
            this.spawnWeight = spawnWeight;
        }

        public AdvancementType frame() {
            return this.frame;
        }

        public int spawnWeight() {
            return this.spawnWeight;
        }
    }
}
