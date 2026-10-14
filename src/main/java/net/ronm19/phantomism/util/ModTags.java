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
        public static final TagKey<EntityType<?>> PHANTOMS = create("phantoms");
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

        private Biomes() {
        }

        private static TagKey<Biome> create(String name) {
            return TagKey.create(Registries.BIOME, id(name));
        }
    }
}
