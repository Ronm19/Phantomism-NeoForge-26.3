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
    RAVAGER("ravager_phantom", PhantomismContent.RAVAGER_PHANTOM, PhantomismContent.RAVAGER_PHANTOM_SPAWN_EGG, Rarity.RARE),
    END("end_phantom", PhantomismContent.END_PHANTOM, PhantomismContent.END_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    ZOMBIE("zombie_phantom", PhantomismContent.ZOMBIE_PHANTOM, PhantomismContent.ZOMBIE_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    SKELETON("skeleton_phantom", PhantomismContent.SKELETON_PHANTOM, PhantomismContent.SKELETON_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    HUSK("husk_phantom", PhantomismContent.HUSK_PHANTOM, PhantomismContent.HUSK_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    ECHO("echo_phantom", PhantomismContent.ECHO_PHANTOM, PhantomismContent.ECHO_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    BLOOD("blood_phantom", PhantomismContent.BLOOD_PHANTOM, PhantomismContent.BLOOD_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    VAMPIRE("vampire_phantom", PhantomismContent.VAMPIRE_PHANTOM, PhantomismContent.VAMPIRE_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    GRAVITY("gravity_phantom", PhantomismContent.GRAVITY_PHANTOM, PhantomismContent.GRAVITY_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    WITHER("wither_phantom", PhantomismContent.WITHER_PHANTOM, PhantomismContent.WITHER_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    CHRONO("chrono_phantom", PhantomismContent.CHRONO_PHANTOM, PhantomismContent.CHRONO_PHANTOM_SPAWN_EGG, Rarity.RARE),
    RIFT("rift_phantom", PhantomismContent.RIFT_PHANTOM, PhantomismContent.RIFT_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    SENTINEL("sentinel_phantom", PhantomismContent.SENTINEL_PHANTOM, PhantomismContent.SENTINEL_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    DEMON("demon_phantom", PhantomismContent.DEMON_PHANTOM, PhantomismContent.DEMON_PHANTOM_SPAWN_EGG, Rarity.RARE),
    SPECTRAL("spectral_phantom", PhantomismContent.SPECTRAL_PHANTOM, PhantomismContent.SPECTRAL_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    ANGEL("angel_phantom", PhantomismContent.ANGEL_PHANTOM, PhantomismContent.ANGEL_PHANTOM_SPAWN_EGG, Rarity.RARE),
    OMEN("omen_phantom", PhantomismContent.OMEN_PHANTOM, PhantomismContent.OMEN_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    ANCIENT("ancient_phantom", PhantomismContent.ANCIENT_PHANTOM, PhantomismContent.ANCIENT_PHANTOM_SPAWN_EGG, Rarity.RARE),
    REAPER("reaper_phantom", PhantomismContent.REAPER_PHANTOM, PhantomismContent.REAPER_PHANTOM_SPAWN_EGG, Rarity.RARE),
    PRIMORDIAL("primordial_phantom", PhantomismContent.PRIMORDIAL_PHANTOM, PhantomismContent.PRIMORDIAL_PHANTOM_SPAWN_EGG, Rarity.LEGENDARY),
    VOID("void_phantom", PhantomismContent.VOID_PHANTOM, PhantomismContent.VOID_PHANTOM_SPAWN_EGG, Rarity.EPIC),
    DARK("dark_phantom", PhantomismContent.DARK_PHANTOM, PhantomismContent.DARK_PHANTOM_SPAWN_EGG, Rarity.EPIC),
    KING("phantom_king", PhantomismContent.PHANTOM_KING, PhantomismContent.PHANTOM_KING_SPAWN_EGG, Rarity.EPIC),
    GOLEM("golem_phantom", PhantomismContent.GOLEM_PHANTOM, PhantomismContent.GOLEM_PHANTOM_SPAWN_EGG, Rarity.EPIC),
    MIRAGE("mirage_phantom", PhantomismContent.MIRAGE_PHANTOM, PhantomismContent.MIRAGE_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    ECLIPSE("eclipse_phantom", PhantomismContent.ECLIPSE_PHANTOM, PhantomismContent.ECLIPSE_PHANTOM_SPAWN_EGG, Rarity.EPIC),
    SHADOW("shadow_phantom", PhantomismContent.SHADOW_PHANTOM, PhantomismContent.SHADOW_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    PLAGUE("plague_phantom", PhantomismContent.PLAGUE_PHANTOM, PhantomismContent.PLAGUE_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    CRYSTAL("crystal_phantom", PhantomismContent.CRYSTAL_PHANTOM, PhantomismContent.CRYSTAL_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    OBSIDIAN("obsidian_phantom", PhantomismContent.OBSIDIAN_PHANTOM, PhantomismContent.OBSIDIAN_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    GALE("gale_phantom", PhantomismContent.GALE_PHANTOM, PhantomismContent.GALE_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    SPORE("spore_phantom", PhantomismContent.SPORE_PHANTOM, PhantomismContent.SPORE_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    SOLAR("solar_phantom", PhantomismContent.SOLAR_PHANTOM, PhantomismContent.SOLAR_PHANTOM_SPAWN_EGG, Rarity.RARE),
    ILLAGER("illager_phantom", PhantomismContent.ILLAGER_PHANTOM, PhantomismContent.ILLAGER_PHANTOM_SPAWN_EGG, Rarity.UNCOMMON),
    INFERNAL("infernal_phantom", PhantomismContent.INFERNAL_PHANTOM, PhantomismContent.INFERNAL_PHANTOM_SPAWN_EGG, Rarity.RARE),
    LUNAR("lunar_phantom", PhantomismContent.LUNAR_PHANTOM, PhantomismContent.LUNAR_PHANTOM_SPAWN_EGG, Rarity.RARE),
    AUTUMN("autumn_phantom", PhantomismContent.AUTUMN_PHANTOM, PhantomismContent.AUTUMN_PHANTOM_SPAWN_EGG, Rarity.RARE),
    HALLOWEEN("halloween_phantom", PhantomismContent.HALLOWEEN_PHANTOM, PhantomismContent.HALLOWEEN_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    CHRISTMAS("christmas_phantom", PhantomismContent.CHRISTMAS_PHANTOM, PhantomismContent.CHRISTMAS_PHANTOM_SPAWN_EGG, Rarity.COMMON),
    EASTER("easter_phantom", PhantomismContent.EASTER_PHANTOM, PhantomismContent.EASTER_PHANTOM_SPAWN_EGG, Rarity.COMMON);

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

    public int codexNumber() { return this == HALLOWEEN ? 52 : this == CHRISTMAS ? 53 : this == EASTER ? 54 : ordinal() + 1; }

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
        EPIC(AdvancementType.CHALLENGE, 6),
        LEGENDARY(AdvancementType.CHALLENGE, 1);

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
