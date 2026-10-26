package net.ronm19.phantomism.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.ronm19.phantomism.Phantomism;

public final class ModTags {
    private ModTags() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name);
    }

    public static final class Items {
        public static final TagKey<Item> PHANTOM_STAFFS = create("phantom_staffs");
        public static final TagKey<Item> PHANTOM_SPAWN_EGGS = create("phantom_spawn_eggs");

        private Items() {
        }

        private static TagKey<Item> create(String name) {
            return TagKey.create(Registries.ITEM, id(name));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> GOLD_CAVE_CEILINGS = create("gold_cave_ceilings");
        public static final TagKey<Block> RAVAGER_BULLDOZER_BREAKABLE = create("ravager_bulldozer_breakable");
        public static final TagKey<Block> RAVAGER_SIEGE_BREAKABLE = create("ravager_siege_breakable");
        public static final TagKey<Block> RAVAGER_PROTECTED_BLOCKS = create("ravager_protected_blocks");
        private Blocks() {
        }

        public static TagKey<Block> create(String name) {
            return TagKey.create(Registries.BLOCK, id(name));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> RIFT_TELEPORT_IMMUNE = create("rift_teleport_immune");
        public static final TagKey<EntityType<?>> PHANTOMS = create("phantoms");
        public static final TagKey<EntityType<?>> VAMPIRE_HYPNOSIS_IMMUNE = create("vampire_hypnosis_immune");
        public static final TagKey<EntityType<?>> GRAVITY_MOVEMENT_IMMUNE = create("gravity_movement_immune");
        public static final TagKey<EntityType<?>> HUSK_EXHAUSTION_RESISTANT = create("husk_exhaustion_resistant");
        public static final TagKey<EntityType<?>> ZOMBIE_PRESENCE_ELIGIBLE = create("zombie_presence_eligible");
        public static final TagKey<EntityType<?>> RAVAGER_FEAR_IMMUNE = create("ravager_fear_immune");
        public static final TagKey<EntityType<?>> FROST_RESISTANT = create("frost_resistant");
        public static final TagKey<EntityType<?>> EARTH_CONTROL_RESISTANT = create("earth_control_resistant");
        public static final TagKey<EntityType<?>> SAND_CONTROL_RESISTANT = create("sand_control_resistant");
        public static final TagKey<EntityType<?>> GOLD_CONTROL_RESISTANT = create("gold_control_resistant");
        public static final TagKey<EntityType<?>> CHERRY_CONTROL_RESISTANT = create("cherry_control_resistant");
        public static final TagKey<EntityType<?>> TOXIC_CONTROL_RESISTANT = create("toxic_control_resistant");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, id(name));
        }
    }

    public static final class Biomes {
        public static final TagKey<Biome> BLACK_PHANTOM_SPAWNS = create("black_phantom_spawns");
        public static final TagKey<Biome> FIRE_PHANTOM_SPAWNS = create("fire_phantom_spawns");
        public static final TagKey<Biome> FROST_PHANTOM_SPAWNS = create("frost_phantom_spawns");
        public static final TagKey<Biome> EARTH_PHANTOM_SPAWNS = create("earth_phantom_spawns");
        public static final TagKey<Biome> WATER_PHANTOM_SPAWNS = create("water_phantom_spawns");
        public static final TagKey<Biome> STORM_PHANTOM_SPAWNS = create("storm_phantom_spawns");
        public static final TagKey<Biome> SAND_PHANTOM_SPAWNS = create("sand_phantom_spawns");
        public static final TagKey<Biome> GOLD_PHANTOM_SPAWNS = create("gold_phantom_spawns");
        public static final TagKey<Biome> CHERRY_PHANTOM_SPAWNS = create("cherry_phantom_spawns");
        public static final TagKey<Biome> PHANTOM_DRAGON_SPAWNS = create("phantom_dragon_spawns");
        public static final TagKey<Biome> VIOLET_PHANTOM_SPAWNS = create("violet_phantom_spawns");
        public static final TagKey<Biome> TOXIC_PHANTOM_SPAWNS = create("toxic_phantom_spawns");
        public static final TagKey<Biome> END_PHANTOM_SPAWNS = create("end_phantom_spawns");
        public static final TagKey<Biome> ZOMBIE_PHANTOM_SPAWNS = create("zombie_phantom_spawns");
        public static final TagKey<Biome> SKELETON_PHANTOM_SPAWNS = create("skeleton_phantom_spawns");
        public static final TagKey<Biome> HUSK_PHANTOM_SPAWNS = create("husk_phantom_spawns");
        public static final TagKey<Biome> ECHO_PHANTOM_SPAWNS = create("echo_phantom_spawns");
        public static final TagKey<Biome> BLOOD_PHANTOM_SPAWNS = create("blood_phantom_spawns");
        public static final TagKey<Biome> VAMPIRE_PHANTOM_SPAWNS = create("vampire_phantom_spawns");
        public static final TagKey<Biome> GRAVITY_PHANTOM_SPAWNS = create("gravity_phantom_spawns");
        public static final TagKey<Biome> INFERNAL_PHANTOM_SPAWNS = create("infernal_phantom_spawns");
        public static final TagKey<Biome> AUTUMN_PHANTOM_SPAWNS = create("autumn_phantom_spawns");
        public static final TagKey<Biome> AUTUMN_PRIMARY_SPAWNS = create("autumn_primary_spawns");
        public static final TagKey<Biome> LUNAR_PHANTOM_SPAWNS = create("lunar_phantom_spawns");
        public static final TagKey<Biome> SOLAR_PHANTOM_SPAWNS = create("solar_phantom_spawns");
        public static final TagKey<Biome> SPORE_PHANTOM_SPAWNS = create("spore_phantom_spawns");
        public static final TagKey<Biome> GALE_PHANTOM_SPAWNS = create("gale_phantom_spawns");
        public static final TagKey<Biome> OBSIDIAN_PHANTOM_SPAWNS = create("obsidian_phantom_spawns");
        public static final TagKey<Biome> CRYSTAL_PHANTOM_SPAWNS = create("crystal_phantom_spawns");
        public static final TagKey<Biome> PLAGUE_PHANTOM_SPAWNS = create("plague_phantom_spawns");
        public static final TagKey<Biome> SHADOW_PHANTOM_SPAWNS = create("shadow_phantom_spawns");
        public static final TagKey<Biome> ECLIPSE_PHANTOM_SPAWNS = create("eclipse_phantom_spawns");
        public static final TagKey<Biome> MIRAGE_PHANTOM_SPAWNS = create("mirage_phantom_spawns");
        public static final TagKey<Biome> OMEN_PHANTOM_SPAWNS = create("omen_phantom_spawns");
        public static final TagKey<Biome> ANCIENT_PHANTOM_SPAWNS = create("ancient_phantom_spawns");
        public static final TagKey<Biome> REAPER_PHANTOM_SPAWNS = create("reaper_phantom_spawns");
        public static final TagKey<Biome> PRIMORDIAL_PHANTOM_SPAWNS = create("primordial_phantom_spawns");
        public static final TagKey<Biome> PHANTOM_KING_SPAWNS = create("phantom_king_spawns");
        public static final TagKey<Biome> DARK_PHANTOM_SPAWNS = create("dark_phantom_spawns");
        public static final TagKey<Biome> VOID_PHANTOM_SPAWNS = create("void_phantom_spawns");
        public static final TagKey<Biome> ANGEL_PHANTOM_SPAWNS = create("angel_phantom_spawns");
        public static final TagKey<Biome> SPECTRAL_PHANTOM_SPAWNS = create("spectral_phantom_spawns");
        public static final TagKey<Biome> DEMON_PHANTOM_SPAWNS = create("demon_phantom_spawns");
        public static final TagKey<Biome> SENTINEL_PHANTOM_SPAWNS = create("sentinel_phantom_spawns");
        public static final TagKey<Biome> RIFT_PHANTOM_SPAWNS = create("rift_phantom_spawns");
        public static final TagKey<Biome> CHRONO_PHANTOM_SPAWNS = create("chrono_phantom_spawns");
        public static final TagKey<Biome> WITHER_PHANTOM_SPAWNS = create("wither_phantom_spawns");

        private Biomes() {
        }

        private static TagKey<Biome> create(String name) {
            return TagKey.create(Registries.BIOME, id(name));
        }
    }
}
