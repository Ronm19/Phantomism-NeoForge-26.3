package net.ronm19.phantomism.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.BlackPhantom;
import net.ronm19.phantomism.entity.FirePhantom;
import net.ronm19.phantomism.entity.FrostPhantom;
import net.ronm19.phantomism.entity.FrostProjectile;
import net.ronm19.phantomism.entity.EarthPhantom;
import net.ronm19.phantomism.entity.EarthBomb;
import net.ronm19.phantomism.entity.EarthWall;
import net.ronm19.phantomism.entity.WaterPhantom;
import net.ronm19.phantomism.entity.WaterProjectile;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.StormProjectile;
import net.ronm19.phantomism.entity.SandPhantom;
import net.ronm19.phantomism.entity.SandBolt;
import net.ronm19.phantomism.entity.GoldPhantom;
import net.ronm19.phantomism.entity.GoldDustProjectile;
import net.ronm19.phantomism.entity.CherryPhantom;
import net.ronm19.phantomism.entity.PetalProjectile;
import net.ronm19.phantomism.effect.FrostboundEffect;
import net.ronm19.phantomism.item.PhantomStaffItem;

public final class PhantomismContent {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Phantomism.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BlackPhantom>> BLACK_PHANTOM = ENTITY_TYPES.registerEntityType(
            "black_phantom",
            BlackPhantom::new,
            MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FirePhantom>> FIRE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "fire_phantom",
            FirePhantom::new,
            MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3).fireImmune()
    );

    public static final DeferredHolder<EntityType<?>, EntityType<FrostPhantom>> FROST_PHANTOM = ENTITY_TYPES.registerEntityType(
            "frost_phantom",
            FrostPhantom::new,
            MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FrostProjectile>> FROST_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "frost_projectile",
            FrostProjectile::new,
            MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<EarthPhantom>> EARTH_PHANTOM = ENTITY_TYPES.registerEntityType(
            "earth_phantom", EarthPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<EarthBomb>> EARTH_BOMB = ENTITY_TYPES.registerEntityType(
            "earth_bomb", EarthBomb::new, MobCategory.MISC,
            builder -> builder.sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<EarthWall>> EARTH_WALL = ENTITY_TYPES.registerEntityType(
            "earth_wall", EarthWall::new, MobCategory.MISC,
            builder -> builder.sized(3.0F, 3.0F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<WaterPhantom>> WATER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "water_phantom", WaterPhantom::new, MobCategory.WATER_CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<WaterProjectile>> WATER_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "water_projectile", WaterProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<StormPhantom>> STORM_PHANTOM = ENTITY_TYPES.registerEntityType(
            "storm_phantom", StormPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<StormProjectile>> STORM_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "storm_projectile", StormProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<SandPhantom>> SAND_PHANTOM = ENTITY_TYPES.registerEntityType(
            "sand_phantom", SandPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<SandBolt>> SAND_BOLT = ENTITY_TYPES.registerEntityType(
            "sand_bolt", SandBolt::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<GoldPhantom>> GOLD_PHANTOM = ENTITY_TYPES.registerEntityType(
            "gold_phantom", GoldPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<GoldDustProjectile>> GOLD_DUST = ENTITY_TYPES.registerEntityType(
            "gold_dust", GoldDustProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<CherryPhantom>> CHERRY_PHANTOM = ENTITY_TYPES.registerEntityType(
            "cherry_phantom", CherryPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<PetalProjectile>> PETAL_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "petal_projectile", PetalProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(
            Registries.MOB_EFFECT, Phantomism.MOD_ID);
    public static final DeferredHolder<MobEffect, FrostboundEffect> FROSTBOUND = MOB_EFFECTS.register(
            "frostbound", FrostboundEffect::new);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Phantomism.MOD_ID);
    public static final DeferredHolder<Potion, Potion> FROSTBOUND_POTION = POTIONS.register(
            "frostbound", () -> new Potion(Phantomism.MOD_ID + ".frostbound", new MobEffectInstance(FROSTBOUND, 160)));

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Phantomism.MOD_ID);
    public static final DeferredItem<PhantomStaffItem> PHANTOM_STAFF = ITEMS.registerItem(
            "phantom_staff",
            PhantomStaffItem::new,
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<SpawnEggItem> BLACK_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "black_phantom_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(BLACK_PHANTOM.get()))
    );
    public static final DeferredItem<SpawnEggItem> FIRE_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "fire_phantom_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(FIRE_PHANTOM.get()))
    );

    public static final DeferredItem<SpawnEggItem> FROST_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "frost_phantom_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(FROST_PHANTOM.get()))
    );

    public static final DeferredItem<SpawnEggItem> EARTH_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "earth_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(EARTH_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> WATER_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "water_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(WATER_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> STORM_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "storm_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(STORM_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> SAND_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "sand_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SAND_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> GOLD_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "gold_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(GOLD_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> CHERRY_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "cherry_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(CHERRY_PHANTOM.get())));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            Phantomism.MOD_ID
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PHANTOM_EGGS_TAB = CREATIVE_TABS.register(
            "phantom_spawn_eggs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.phantomism.spawn_eggs"))
                    .icon(() -> new ItemStack(FIRE_PHANTOM_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BLACK_PHANTOM_SPAWN_EGG.get());
                        output.accept(FIRE_PHANTOM_SPAWN_EGG.get());
                        output.accept(FROST_PHANTOM_SPAWN_EGG.get());
                        output.accept(EARTH_PHANTOM_SPAWN_EGG.get());
                        output.accept(WATER_PHANTOM_SPAWN_EGG.get());
                        output.accept(STORM_PHANTOM_SPAWN_EGG.get());
                        output.accept(SAND_PHANTOM_SPAWN_EGG.get());
                        output.accept(GOLD_PHANTOM_SPAWN_EGG.get());
                        output.accept(CHERRY_PHANTOM_SPAWN_EGG.get());
                    })
                    .build()
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PHANTOM_ITEMS_TAB = CREATIVE_TABS.register(
            "phantom_items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.phantomism.items"))
                    .icon(() -> new ItemStack(PHANTOM_STAFF.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PHANTOM_STAFF.get());
                        output.accept(PotionContents.createItemStack(Items.POTION, FROSTBOUND_POTION));
                        output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, FROSTBOUND_POTION));
                        output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, FROSTBOUND_POTION));
                        output.accept(PotionContents.createItemStack(Items.TIPPED_ARROW, FROSTBOUND_POTION));
                    })
                    .build()
    );

    private PhantomismContent() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ModParticles.register(modEventBus);
    }
}
