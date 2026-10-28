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
import net.ronm19.phantomism.entity.PhantomDragon;
import net.ronm19.phantomism.entity.DragonFireballProjectile;
import net.ronm19.phantomism.entity.VioletPhantom;
import net.ronm19.phantomism.entity.VioletLanceProjectile;
import net.ronm19.phantomism.entity.ToxicPhantom;
import net.ronm19.phantomism.entity.SculkPhantom;
import net.ronm19.phantomism.entity.RavagerPhantom;
import net.ronm19.phantomism.entity.EndPhantom;
import net.ronm19.phantomism.entity.ZombiePhantom;
import net.ronm19.phantomism.entity.InfectedSpitProjectile;
import net.ronm19.phantomism.effect.SonicDisruptionEffect;
import net.ronm19.phantomism.entity.AcidProjectile;
import net.ronm19.phantomism.effect.ArmorFractureEffect;
import net.ronm19.phantomism.effect.FrostboundEffect;
import net.ronm19.phantomism.item.PhantomStaffItem;

public final class PhantomismContent {
    public static final DeferredRegister<net.minecraft.server.level.TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, Phantomism.MOD_ID);
    public static final DeferredHolder<net.minecraft.server.level.TicketType, net.minecraft.server.level.TicketType> SPECTRAL_ANCHOR_TICKET = TICKET_TYPES.register("spectral_anchor", () -> new net.minecraft.server.level.TicketType(40, net.minecraft.server.level.TicketType.FLAG_LOADING));
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
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomDragon>> PHANTOM_DRAGON = ENTITY_TYPES.registerEntityType(
            "phantom_dragon", PhantomDragon::new, MobCategory.CREATURE,
            builder -> builder.sized(1.75F, 1.125F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<DragonFireballProjectile>> DRAGON_FIREBALL = ENTITY_TYPES.registerEntityType(
            "dragon_fireball", DragonFireballProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.35F, 0.35F).clientTrackingRange(6).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<VioletPhantom>> VIOLET_PHANTOM = ENTITY_TYPES.registerEntityType(
            "violet_phantom", VioletPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<VioletLanceProjectile>> VIOLET_LANCE = ENTITY_TYPES.registerEntityType(
            "violet_lance", VioletLanceProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<ToxicPhantom>> TOXIC_PHANTOM = ENTITY_TYPES.registerEntityType(
            "toxic_phantom", ToxicPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<AcidProjectile>> ACID_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "acid_projectile", AcidProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1));

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(
            Registries.MOB_EFFECT, Phantomism.MOD_ID);
    public static final DeferredHolder<MobEffect, FrostboundEffect> FROSTBOUND = MOB_EFFECTS.register(
            "frostbound", FrostboundEffect::new);
    public static final DeferredHolder<MobEffect, ArmorFractureEffect> ARMOR_FRACTURE = MOB_EFFECTS.register(
            "armor_fracture", ArmorFractureEffect::new);
    public static final DeferredHolder<EntityType<?>, EntityType<SculkPhantom>> SCULK_PHANTOM = ENTITY_TYPES.registerEntityType(
            "sculk_phantom", SculkPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<MobEffect, SonicDisruptionEffect> SONIC_DISRUPTION = MOB_EFFECTS.register(
            "sonic_disruption", SonicDisruptionEffect::new);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Phantomism.MOD_ID);
    public static final DeferredHolder<Potion, Potion> FROSTBOUND_POTION = POTIONS.register(
            "frostbound", () -> new Potion(Phantomism.MOD_ID + ".frostbound", new MobEffectInstance(FROSTBOUND, 160)));

    public static final DeferredHolder<EntityType<?>, EntityType<RavagerPhantom>> RAVAGER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "ravager_phantom", RavagerPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.61F, 1.035F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Phantomism.MOD_ID);
    public static final DeferredItem<net.minecraft.world.item.Item> HEART_OF_THE_WORLD = ITEMS.registerItem("heart_of_the_world", properties -> new net.minecraft.world.item.Item(properties.rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<net.minecraft.world.item.Item> ANCIENT_RELIC = ITEMS.registerItem("ancient_relic", net.minecraft.world.item.Item::new);
    public static final DeferredHolder<EntityType<?>, EntityType<EndPhantom>> END_PHANTOM = ENTITY_TYPES.registerEntityType(
            "end_phantom", EndPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<ZombiePhantom>> ZOMBIE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "zombie_phantom", ZombiePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<InfectedSpitProjectile>> INFECTED_SPIT = ENTITY_TYPES.registerEntityType(
            "infected_spit", InfectedSpitProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SkeletonPhantom>> SKELETON_PHANTOM = ENTITY_TYPES.registerEntityType(
            "skeleton_phantom", net.ronm19.phantomism.entity.SkeletonPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SkeletonArrow>> SKELETON_ARROW = ENTITY_TYPES.registerEntityType(
            "skeleton_arrow", net.ronm19.phantomism.entity.SkeletonArrow::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredHolder<MobEffect, net.ronm19.phantomism.effect.BoneFractureEffect> BONE_FRACTURE = MOB_EFFECTS.register(
            "bone_fracture", net.ronm19.phantomism.effect.BoneFractureEffect::new);
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.HuskPhantom>> HUSK_PHANTOM = ENTITY_TYPES.registerEntityType(
            "husk_phantom", net.ronm19.phantomism.entity.HuskPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<MobEffect, net.ronm19.phantomism.effect.ExhaustionEffect> EXHAUSTION = MOB_EFFECTS.register(
            "exhaustion", net.ronm19.phantomism.effect.ExhaustionEffect::new);
    public static final DeferredItem<SpawnEggItem> HUSK_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "husk_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(HUSK_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EchoPhantom>> ECHO_PHANTOM = ENTITY_TYPES.registerEntityType(
            "echo_phantom", net.ronm19.phantomism.entity.EchoPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EchoPulseProjectile>> ECHO_PULSE = ENTITY_TYPES.registerEntityType(
            "echo_pulse", net.ronm19.phantomism.entity.EchoPulseProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> ECHO_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "echo_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ECHO_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.BloodPhantom>> BLOOD_PHANTOM = ENTITY_TYPES.registerEntityType(
            "blood_phantom", net.ronm19.phantomism.entity.BloodPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.BloodProjectile>> BLOOD_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "blood_projectile", net.ronm19.phantomism.entity.BloodProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> BLOOD_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "blood_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(BLOOD_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.VampirePhantom>> VAMPIRE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "vampire", net.ronm19.phantomism.entity.VampirePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.VampireBolt>> VAMPIRE_BOLT = ENTITY_TYPES.registerEntityType(
            "vampire_crimson_bolt", net.ronm19.phantomism.entity.VampireBolt::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> VAMPIRE_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "vampire_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(VAMPIRE_PHANTOM.get())));
    public static final DeferredHolder<MobEffect, net.ronm19.phantomism.effect.InfectedEffect> INFECTED = MOB_EFFECTS.register("infected", net.ronm19.phantomism.effect.InfectedEffect::new);
    public static final DeferredHolder<MobEffect, net.ronm19.phantomism.effect.FinalityEffect> FINALITY = MOB_EFFECTS.register("finality", net.ronm19.phantomism.effect.FinalityEffect::new);
    public static final DeferredHolder<MobEffect, net.ronm19.phantomism.effect.BleedingEffect> BLEEDING = MOB_EFFECTS.register("bleeding", net.ronm19.phantomism.effect.BleedingEffect::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<net.ronm19.phantomism.item.PhantomCodexItem> PHANTOM_CODEX = ITEMS.registerItem("phantom_codex", net.ronm19.phantomism.item.PhantomCodexItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<PhantomStaffItem> PHANTOM_STAFF = ITEMS.registerItem(
            "phantom_staff",
            PhantomStaffItem::new,
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<SpawnEggItem> BLACK_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "black_phantom_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(BLACK_PHANTOM.get()))
    );
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.GravityPhantom>> GRAVITY_PHANTOM = ENTITY_TYPES.registerEntityType(
            "gravity", net.ronm19.phantomism.entity.GravityPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.GravityWellProjectile>> GRAVITY_WELL = ENTITY_TYPES.registerEntityType(
            "gravity_well", net.ronm19.phantomism.entity.GravityWellProjectile::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> GRAVITY_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "gravity_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(GRAVITY_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ChronoPhantom>> CHRONO_PHANTOM = ENTITY_TYPES.registerEntityType(
            "chrono", net.ronm19.phantomism.entity.ChronoPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.TimeBolt>> TIME_BOLT = ENTITY_TYPES.registerEntityType(
            "time_bolt", net.ronm19.phantomism.entity.TimeBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> CHRONO_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "chrono_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(CHRONO_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.RiftPhantom>> RIFT_PHANTOM = ENTITY_TYPES.registerEntityType(
            "rift", net.ronm19.phantomism.entity.RiftPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.RiftBolt>> RIFT_BOLT = ENTITY_TYPES.registerEntityType(
            "rift_bolt", net.ronm19.phantomism.entity.RiftBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SentinelPhantom>> SENTINEL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "sentinel", net.ronm19.phantomism.entity.SentinelPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.DemonPhantom>> DEMON_PHANTOM = ENTITY_TYPES.registerEntityType(
            "demon", net.ronm19.phantomism.entity.DemonPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.54F, .99F).clientTrackingRange(10).updateInterval(3).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.DemonMeteor>> DEMON_METEOR = ENTITY_TYPES.registerEntityType(
            "demon_meteor", net.ronm19.phantomism.entity.DemonMeteor::new, MobCategory.MISC,
            builder -> builder.sized(.5F, .5F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SpectralPhantom>> SPECTRAL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "spectral", net.ronm19.phantomism.entity.SpectralPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SpectralLance>> SPECTRAL_LANCE = ENTITY_TYPES.registerEntityType(
            "spectral_lance", net.ronm19.phantomism.entity.SpectralLance::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AngelPhantom>> ANGEL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "angel", net.ronm19.phantomism.entity.AngelPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AngelSmite>> ANGEL_SMITE = ENTITY_TYPES.registerEntityType(
            "angel_smite", net.ronm19.phantomism.entity.AngelSmite::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.OmenPhantom>> OMEN_PHANTOM = ENTITY_TYPES.registerEntityType(
            "omen", net.ronm19.phantomism.entity.OmenPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.OmenBolt>> OMEN_BOLT = ENTITY_TYPES.registerEntityType(
            "omen_bolt", net.ronm19.phantomism.entity.OmenBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> OMEN_PHANTOM_SPAWN_EGG = ITEMS.registerItem("omen_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(OMEN_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AncientPhantom>> ANCIENT_PHANTOM = ENTITY_TYPES.registerEntityType(
            "ancient", net.ronm19.phantomism.entity.AncientPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.54F, .99F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AncientStone>> ANCIENT_STONE = ENTITY_TYPES.registerEntityType(
            "ancient_stone", net.ronm19.phantomism.entity.AncientStone::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> ANCIENT_PHANTOM_SPAWN_EGG = ITEMS.registerItem("ancient_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ANCIENT_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ReaperPhantom>> REAPER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "reaper", net.ronm19.phantomism.entity.ReaperPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ReaperShard>> REAPER_SHARD = ENTITY_TYPES.registerEntityType(
            "reaper_shard", net.ronm19.phantomism.entity.ReaperShard::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> REAPER_PHANTOM_SPAWN_EGG = ITEMS.registerItem("reaper_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(REAPER_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PrimordialPhantom>> PRIMORDIAL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "primordial", net.ronm19.phantomism.entity.PrimordialPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.68F, 1.08F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PrimordialStar>> PRIMORDIAL_STAR = ENTITY_TYPES.registerEntityType(
            "primordial_star", net.ronm19.phantomism.entity.PrimordialStar::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> PRIMORDIAL_PHANTOM_SPAWN_EGG = ITEMS.registerItem("primordial_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(PRIMORDIAL_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.VoidPhantom>> VOID_PHANTOM = ENTITY_TYPES.registerEntityType(
            "void", net.ronm19.phantomism.entity.VoidPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.VoidBolt>> VOID_BOLT = ENTITY_TYPES.registerEntityType(
            "void_bolt", net.ronm19.phantomism.entity.VoidBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.DarkPhantom>> DARK_PHANTOM = ENTITY_TYPES.registerEntityType(
            "dark", net.ronm19.phantomism.entity.DarkPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.DarkProjectile>> DARK_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "darkness_projectile", net.ronm19.phantomism.entity.DarkProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(8).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PhantomKing>> PHANTOM_KING = ENTITY_TYPES.registerEntityType(
            "phantom_king", net.ronm19.phantomism.entity.PhantomKing::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.HalloweenPhantom>> HALLOWEEN_PHANTOM = ENTITY_TYPES.registerEntityType(
            "halloween_phantom", net.ronm19.phantomism.entity.HalloweenPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PumpkinGaze>> PUMPKIN_GAZE = ENTITY_TYPES.registerEntityType(
            "pumpkin_gaze", net.ronm19.phantomism.entity.PumpkinGaze::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(8).updateInterval(2));
    public static final DeferredItem<SpawnEggItem> HALLOWEEN_PHANTOM_SPAWN_EGG = ITEMS.registerItem("halloween_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(HALLOWEEN_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.NewYearsPhantom>> NEW_YEARS_PHANTOM = ENTITY_TYPES.registerEntityType(
            "new_years_phantom", net.ronm19.phantomism.entity.NewYearsPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.NewYearsClockProjectile>> NEW_YEARS_CLOCK_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "new_years_clock_projectile", net.ronm19.phantomism.entity.NewYearsClockProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.3F, .3F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredItem<SpawnEggItem> NEW_YEARS_PHANTOM_SPAWN_EGG = ITEMS.registerItem("new_years_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(NEW_YEARS_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.CreatorPhantom>> CREATOR_PHANTOM = ENTITY_TYPES.registerEntityType(
            "creator_phantom", net.ronm19.phantomism.entity.CreatorPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(2));
    public static final DeferredItem<SpawnEggItem> CREATOR_PHANTOM_SPAWN_EGG = ITEMS.registerItem("creator_phantom_spawn_egg", properties -> new net.ronm19.phantomism.item.CreatorSpawnEggItem(properties.spawnEgg(CREATOR_PHANTOM.get())));
    public static final DeferredItem<net.ronm19.phantomism.item.EyeOfCreationItem> EYE_OF_CREATION = ITEMS.registerItem("eye_of_creation", net.ronm19.phantomism.item.EyeOfCreationItem::new, properties -> properties.stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ValentinesPhantom>> VALENTINES_PHANTOM = ENTITY_TYPES.registerEntityType(
            "valentines_phantom", net.ronm19.phantomism.entity.ValentinesPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ValentinesHeartProjectile>> VALENTINES_HEART_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "valentines_heart_projectile", net.ronm19.phantomism.entity.ValentinesHeartProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.3F, .3F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredItem<SpawnEggItem> VALENTINES_PHANTOM_SPAWN_EGG = ITEMS.registerItem("valentines_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(VALENTINES_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.StPatricksPhantom>> ST_PATRICKS_PHANTOM = ENTITY_TYPES.registerEntityType(
            "st_patricks_phantom", net.ronm19.phantomism.entity.StPatricksPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.StPatricksShamrockProjectile>> ST_PATRICKS_SHAMROCK_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "st_patricks_shamrock_projectile", net.ronm19.phantomism.entity.StPatricksShamrockProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.3F, .3F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredItem<SpawnEggItem> ST_PATRICKS_PHANTOM_SPAWN_EGG = ITEMS.registerItem("st_patricks_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ST_PATRICKS_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.FireworkPhantom>> FIREWORK_PHANTOM = ENTITY_TYPES.registerEntityType(
            "firework_phantom", net.ronm19.phantomism.entity.FireworkPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.FireworkRocketProjectile>> FIREWORK_ROCKET_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "firework_rocket_projectile", net.ronm19.phantomism.entity.FireworkRocketProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredItem<SpawnEggItem> FIREWORK_PHANTOM_SPAWN_EGG = ITEMS.registerItem("firework_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(FIREWORK_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EasterPhantom>> EASTER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "easter_phantom", net.ronm19.phantomism.entity.EasterPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EasterEggProjectile>> EASTER_EGG_PROJECTILE = ENTITY_TYPES.registerEntityType(
            "easter_egg_projectile", net.ronm19.phantomism.entity.EasterEggProjectile::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(8).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EasterOrbitEgg>> EASTER_ORBIT_EGG = ENTITY_TYPES.registerEntityType(
            "easter_orbit_egg", net.ronm19.phantomism.entity.EasterOrbitEgg::new, MobCategory.MISC,
            builder -> builder.sized(1, 1).clientTrackingRange(10).updateInterval(2));
    public static final DeferredItem<SpawnEggItem> EASTER_PHANTOM_SPAWN_EGG = ITEMS.registerItem("easter_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(EASTER_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ChristmasPhantom>> CHRISTMAS_PHANTOM = ENTITY_TYPES.registerEntityType(
            "christmas_phantom", net.ronm19.phantomism.entity.ChristmasPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(10).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ChristmasSnowball>> CHRISTMAS_SNOWBALL = ENTITY_TYPES.registerEntityType(
            "christmas_snowball", net.ronm19.phantomism.entity.ChristmasSnowball::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(8).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ChristmasGift>> CHRISTMAS_GIFT = ENTITY_TYPES.registerEntityType(
            "christmas_gift", net.ronm19.phantomism.entity.ChristmasGift::new, MobCategory.MISC,
            builder -> builder.sized(1, 1).clientTrackingRange(10).updateInterval(2));
    public static final DeferredItem<SpawnEggItem> CHRISTMAS_PHANTOM_SPAWN_EGG = ITEMS.registerItem("christmas_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(CHRISTMAS_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AutumnPhantom>> AUTUMN_PHANTOM = ENTITY_TYPES.registerEntityType(
            "autumn_phantom", net.ronm19.phantomism.entity.AutumnPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.AutumnBolt>> AUTUMN_BOLT = ENTITY_TYPES.registerEntityType(
            "autumn_bolt", net.ronm19.phantomism.entity.AutumnBolt::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> AUTUMN_PHANTOM_SPAWN_EGG = ITEMS.registerItem("autumn_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(AUTUMN_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.LunarPhantom>> LUNAR_PHANTOM = ENTITY_TYPES.registerEntityType(
            "lunar_phantom", net.ronm19.phantomism.entity.LunarPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.MoonLance>> MOON_LANCE = ENTITY_TYPES.registerEntityType(
            "moon_lance", net.ronm19.phantomism.entity.MoonLance::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> LUNAR_PHANTOM_SPAWN_EGG = ITEMS.registerItem("lunar_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(LUNAR_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.InfernalPhantom>> INFERNAL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "infernal_phantom", net.ronm19.phantomism.entity.InfernalPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(3).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.InfernalBolt>> INFERNAL_BOLT = ENTITY_TYPES.registerEntityType(
            "infernal_bolt", net.ronm19.phantomism.entity.InfernalBolt::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(6).updateInterval(1).noSave().fireImmune());
    public static final DeferredItem<SpawnEggItem> INFERNAL_PHANTOM_SPAWN_EGG = ITEMS.registerItem("infernal_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(INFERNAL_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.IllagerPhantom>> ILLAGER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "illager_phantom", net.ronm19.phantomism.entity.IllagerPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.IllagerBolt>> ILLAGER_BOLT = ENTITY_TYPES.registerEntityType(
            "illager_bolt", net.ronm19.phantomism.entity.IllagerBolt::new, MobCategory.MISC,
            builder -> builder.sized(.25F, .25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.IllagerSpectralVex>> ILLAGER_SPECTRAL_VEX = ENTITY_TYPES.registerEntityType(
            "illager_spectral_vex", net.ronm19.phantomism.entity.IllagerSpectralVex::new, MobCategory.MISC,
            builder -> builder.sized(.4F, .8F).clientTrackingRange(8).updateInterval(2).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.IllagerFangs>> ILLAGER_FANGS = ENTITY_TYPES.registerEntityType(
            "illager_fangs", net.ronm19.phantomism.entity.IllagerFangs::new, MobCategory.MISC,
            builder -> builder.sized(.5F, .8F).clientTrackingRange(6).updateInterval(2).noSave());
    public static final DeferredItem<SpawnEggItem> ILLAGER_PHANTOM_SPAWN_EGG = ITEMS.registerItem("illager_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ILLAGER_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SolarPhantom>> SOLAR_PHANTOM = ENTITY_TYPES.registerEntityType(
            "solar_phantom", net.ronm19.phantomism.entity.SolarPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(3).fireImmune());
    public static final DeferredItem<SpawnEggItem> SOLAR_PHANTOM_SPAWN_EGG = ITEMS.registerItem("solar_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SOLAR_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SporePhantom>> SPORE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "spore_phantom", net.ronm19.phantomism.entity.SporePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.SporeBreath>> SPORE_BREATH = ENTITY_TYPES.registerEntityType(
            "spore_breath", net.ronm19.phantomism.entity.SporeBreath::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> SPORE_PHANTOM_SPAWN_EGG = ITEMS.registerItem("spore_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SPORE_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.GalePhantom>> GALE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "gale_phantom", net.ronm19.phantomism.entity.GalePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.WindBolt>> WIND_BOLT = ENTITY_TYPES.registerEntityType(
            "wind_bolt", net.ronm19.phantomism.entity.WindBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> GALE_PHANTOM_SPAWN_EGG = ITEMS.registerItem("gale_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(GALE_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ObsidianPhantom>> OBSIDIAN_PHANTOM = ENTITY_TYPES.registerEntityType(
            "obsidian_phantom", net.ronm19.phantomism.entity.ObsidianPhantom::new, MobCategory.CREATURE,
            builder -> builder.fireImmune().sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ObsidianShard>> OBSIDIAN_SHARD = ENTITY_TYPES.registerEntityType(
            "obsidian_shard", net.ronm19.phantomism.entity.ObsidianShard::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> OBSIDIAN_PHANTOM_SPAWN_EGG = ITEMS.registerItem("obsidian_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(OBSIDIAN_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.CrystalPhantom>> CRYSTAL_PHANTOM = ENTITY_TYPES.registerEntityType(
            "crystal_phantom", net.ronm19.phantomism.entity.CrystalPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.CrystalShard>> CRYSTAL_SHARD = ENTITY_TYPES.registerEntityType(
            "crystal_shard", net.ronm19.phantomism.entity.CrystalShard::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> CRYSTAL_PHANTOM_SPAWN_EGG = ITEMS.registerItem("crystal_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(CRYSTAL_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PlaguePhantom>> PLAGUE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "plague_phantom", net.ronm19.phantomism.entity.PlaguePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.PlagueSpit>> PLAGUE_SPIT = ENTITY_TYPES.registerEntityType(
            "plague_spit", net.ronm19.phantomism.entity.PlagueSpit::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> PLAGUE_PHANTOM_SPAWN_EGG = ITEMS.registerItem("plague_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(PLAGUE_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ShadowPhantom>> SHADOW_PHANTOM = ENTITY_TYPES.registerEntityType(
            "shadow_phantom", net.ronm19.phantomism.entity.ShadowPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.ShadowBolt>> SHADOW_BOLT = ENTITY_TYPES.registerEntityType(
            "shadow_bolt", net.ronm19.phantomism.entity.ShadowBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> SHADOW_PHANTOM_SPAWN_EGG = ITEMS.registerItem("shadow_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SHADOW_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EclipsePhantom>> ECLIPSE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "eclipse_phantom", net.ronm19.phantomism.entity.EclipsePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.EclipseBolt>> ECLIPSE_BOLT = ENTITY_TYPES.registerEntityType(
            "eclipse_bolt", net.ronm19.phantomism.entity.EclipseBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> ECLIPSE_PHANTOM_SPAWN_EGG = ITEMS.registerItem("eclipse_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ECLIPSE_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.MiragePhantom>> MIRAGE_PHANTOM = ENTITY_TYPES.registerEntityType(
            "mirage_phantom", net.ronm19.phantomism.entity.MiragePhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.MirageDecoy>> MIRAGE_DECOY = ENTITY_TYPES.registerEntityType(
            "mirage_decoy", net.ronm19.phantomism.entity.MirageDecoy::new, MobCategory.MISC,
            builder -> builder.sized(.95F, .8F).clientTrackingRange(12).updateInterval(2).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.MirageBolt>> MIRAGE_BOLT = ENTITY_TYPES.registerEntityType(
            "mirage_bolt", net.ronm19.phantomism.entity.MirageBolt::new, MobCategory.MISC,
            builder -> builder.sized(.2F, .2F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> MIRAGE_PHANTOM_SPAWN_EGG = ITEMS.registerItem("mirage_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(MIRAGE_PHANTOM.get())));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.GolemPhantom>> GOLEM_PHANTOM = ENTITY_TYPES.registerEntityType(
            "golem_phantom", net.ronm19.phantomism.entity.GolemPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, .9F).clientTrackingRange(12).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.GolemFist>> GOLEM_FIST = ENTITY_TYPES.registerEntityType(
            "golem_fist", net.ronm19.phantomism.entity.GolemFist::new, MobCategory.MISC,
            builder -> builder.sized(.5F, .5F).clientTrackingRange(12).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> GOLEM_PHANTOM_SPAWN_EGG = ITEMS.registerItem("golem_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(GOLEM_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> PHANTOM_KING_SPAWN_EGG = ITEMS.registerItem("phantom_king_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(PHANTOM_KING.get())));
    public static final DeferredItem<SpawnEggItem> DARK_PHANTOM_SPAWN_EGG = ITEMS.registerItem("dark_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(DARK_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> VOID_PHANTOM_SPAWN_EGG = ITEMS.registerItem("void_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(VOID_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> ANGEL_PHANTOM_SPAWN_EGG = ITEMS.registerItem("angel_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ANGEL_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> SPECTRAL_PHANTOM_SPAWN_EGG = ITEMS.registerItem("spectral_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SPECTRAL_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> DEMON_PHANTOM_SPAWN_EGG = ITEMS.registerItem("demon_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(DEMON_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> SENTINEL_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "sentinel_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SENTINEL_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> RIFT_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "rift_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(RIFT_PHANTOM.get())));

    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.WitherPhantom>> WITHER_PHANTOM = ENTITY_TYPES.registerEntityType(
            "wither", net.ronm19.phantomism.entity.WitherPhantom::new, MobCategory.CREATURE,
            builder -> builder.sized(1.4F, 0.9F).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<net.ronm19.phantomism.entity.CompanionWitherSkull>> WITHER_PHANTOM_SKULL = ENTITY_TYPES.registerEntityType(
            "wither_phantom_skull", net.ronm19.phantomism.entity.CompanionWitherSkull::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).noSave());
    public static final DeferredItem<SpawnEggItem> WITHER_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "wither_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(WITHER_PHANTOM.get())));
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

    public static final DeferredItem<SpawnEggItem> PHANTOM_DRAGON_SPAWN_EGG = ITEMS.registerItem(
            "phantom_dragon_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(PHANTOM_DRAGON.get())));
    public static final DeferredItem<SpawnEggItem> VIOLET_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "violet_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(VIOLET_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> TOXIC_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "toxic_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(TOXIC_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> SCULK_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "sculk_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SCULK_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> RAVAGER_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "ravager_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(RAVAGER_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> END_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "end_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(END_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> ZOMBIE_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "zombie_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ZOMBIE_PHANTOM.get())));
    public static final DeferredItem<SpawnEggItem> SKELETON_PHANTOM_SPAWN_EGG = ITEMS.registerItem(
            "skeleton_phantom_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(SKELETON_PHANTOM.get())));

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
                        output.accept(PHANTOM_DRAGON_SPAWN_EGG.get());
                        output.accept(VIOLET_PHANTOM_SPAWN_EGG.get());
                        output.accept(TOXIC_PHANTOM_SPAWN_EGG.get());
                        output.accept(SCULK_PHANTOM_SPAWN_EGG.get());
                        output.accept(RAVAGER_PHANTOM_SPAWN_EGG.get());
                        output.accept(END_PHANTOM_SPAWN_EGG.get());
                        output.accept(ZOMBIE_PHANTOM_SPAWN_EGG.get());
                        output.accept(SKELETON_PHANTOM_SPAWN_EGG.get());
                        output.accept(HUSK_PHANTOM_SPAWN_EGG.get());
                        output.accept(ECHO_PHANTOM_SPAWN_EGG.get());
                        output.accept(BLOOD_PHANTOM_SPAWN_EGG.get());
                        output.accept(VAMPIRE_PHANTOM_SPAWN_EGG.get());
                        output.accept(GRAVITY_PHANTOM_SPAWN_EGG.get());
                        output.accept(WITHER_PHANTOM_SPAWN_EGG.get());
                        output.accept(CHRONO_PHANTOM_SPAWN_EGG.get());
                        output.accept(RIFT_PHANTOM_SPAWN_EGG.get());
                        output.accept(SENTINEL_PHANTOM_SPAWN_EGG.get());
                        output.accept(DEMON_PHANTOM_SPAWN_EGG.get());
                        output.accept(SPECTRAL_PHANTOM_SPAWN_EGG.get());
                        output.accept(ANGEL_PHANTOM_SPAWN_EGG.get());
                        output.accept(VOID_PHANTOM_SPAWN_EGG.get());
                        output.accept(DARK_PHANTOM_SPAWN_EGG.get());
                        output.accept(PHANTOM_KING_SPAWN_EGG.get());
                        output.accept(AUTUMN_PHANTOM_SPAWN_EGG.get());
                        output.accept(LUNAR_PHANTOM_SPAWN_EGG.get());
                        output.accept(HALLOWEEN_PHANTOM_SPAWN_EGG.get());
                        output.accept(NEW_YEARS_PHANTOM_SPAWN_EGG.get());
                        output.accept(CREATOR_PHANTOM_SPAWN_EGG.get());
                        output.accept(VALENTINES_PHANTOM_SPAWN_EGG.get());
                        output.accept(ST_PATRICKS_PHANTOM_SPAWN_EGG.get());
                        output.accept(FIREWORK_PHANTOM_SPAWN_EGG.get());
                        output.accept(EASTER_PHANTOM_SPAWN_EGG.get());
                        output.accept(CHRISTMAS_PHANTOM_SPAWN_EGG.get());
                        output.accept(INFERNAL_PHANTOM_SPAWN_EGG.get());
                        output.accept(ILLAGER_PHANTOM_SPAWN_EGG.get());
                        output.accept(SOLAR_PHANTOM_SPAWN_EGG.get());
                        output.accept(SPORE_PHANTOM_SPAWN_EGG.get());
                        output.accept(GALE_PHANTOM_SPAWN_EGG.get());
                        output.accept(OBSIDIAN_PHANTOM_SPAWN_EGG.get());
                        output.accept(CRYSTAL_PHANTOM_SPAWN_EGG.get());
                        output.accept(PLAGUE_PHANTOM_SPAWN_EGG.get());
                        output.accept(SHADOW_PHANTOM_SPAWN_EGG.get());
                        output.accept(ECLIPSE_PHANTOM_SPAWN_EGG.get());
                        output.accept(MIRAGE_PHANTOM_SPAWN_EGG.get());
                        output.accept(GOLEM_PHANTOM_SPAWN_EGG.get());
                        output.accept(PRIMORDIAL_PHANTOM_SPAWN_EGG.get());
                        output.accept(REAPER_PHANTOM_SPAWN_EGG.get());
                        output.accept(ANCIENT_PHANTOM_SPAWN_EGG.get());
                        output.accept(OMEN_PHANTOM_SPAWN_EGG.get());
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
                        output.accept(PHANTOM_CODEX.get());
                        output.accept(HEART_OF_THE_WORLD.get());
                        output.accept(ANCIENT_RELIC.get());
                        output.accept(EYE_OF_CREATION.get());
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
        TICKET_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ModParticles.register(modEventBus);
    }
}
