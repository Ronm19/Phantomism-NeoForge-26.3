package net.ronm19.phantomism;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_UNDEAD_BONUS = BUILDER
            .comment("Extra damage added to a Black Phantom's diving bite against undead enemies. 2 damage = 1 heart.")
            .translation("config.phantomism.blackPhantom.diveUndeadBonus")
            .defineInRange("blackPhantom.diveUndeadBonus", 2.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_FLYING_BONUS = BUILDER
            .comment("Extra diving bite damage against flying enemies. Stacks with other applicable dive bonuses.")
            .translation("config.phantomism.blackPhantom.diveFlyingBonus")
            .defineInRange("blackPhantom.diveFlyingBonus", 2.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_PHANTOM_BONUS = BUILDER
            .comment("Additional diving bite damage against vanilla hostile Phantoms. Does not allow attacking companion phantoms.")
            .translation("config.phantomism.blackPhantom.divePhantomBonus")
            .defineInRange("blackPhantom.divePhantomBonus", 4.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_SKY_KNIGHT_SPEED_BONUS = BUILDER
            .comment("Flat movement-speed attribute bonus during Sky Knight. The default base movement speed is 0.25.")
            .translation("config.phantomism.blackPhantom.skyKnightSpeedBonus")
            .defineInRange("blackPhantom.skyKnightSpeedBonus", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_SKY_KNIGHT_ATTACK_BONUS = BUILDER
            .comment("Flat melee damage bonus during Sky Knight. 2 damage = 1 heart.")
            .translation("config.phantomism.blackPhantom.skyKnightAttackBonus")
            .defineInRange("blackPhantom.skyKnightAttackBonus", 1.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.IntValue PHANTOM_HOME_RADIUS = BUILDER
            .comment("Horizontal radius in blocks around the assigned bed for Wander, activities and base defense.",
                    "Phantoms take short local patrol steps and only inspect loaded chunks; this does not keep a whole base loaded.",
                    "Use 200-300 for large bases, or up to 1000. Existing configured values are preserved.")
            .translation("config.phantomism.phantom.homeRadius")
            .defineInRange("phantom.homeRadius", 200, 4, 1000);

    public static final ModConfigSpec.IntValue FAMILY_DEFENSE_RADIUS = BUILDER
            .comment("Nearby adult companions with the same owner defend a hurt pet within this many blocks.",
                    "Only living attackers trigger defense. Owner/family hits, environmental damage and cleared targets do not.",
                    "Does not take control from riders or replace active targets. Attackers must be within the defender's awareness range.")
            .translation("config.phantomism.phantom.familyDefenseRadius")
            .defineInRange("phantom.familyDefenseRadius", 32, 4, 64);

    public static final PhantomStats BLACK_PHANTOM_STATS = stats("blackPhantom", 32.0D, 4.0D, 2.0D, 0.1D);
    public static final PhantomStats FIRE_PHANTOM_STATS = stats("firePhantom", 36.0D, 5.0D, 3.0D, 0.15D);
    public static final PhantomStats FROST_PHANTOM_STATS = stats("frostPhantom", 36.0D, 4.0D, 4.0D, 0.15D);
    public static final PhantomStats EARTH_PHANTOM_STATS = stats("earthPhantom", 44.0D, 4.0D, 6.0D, 0.25D);
    public static final PhantomStats WATER_PHANTOM_STATS = stats("waterPhantom", 38.0D, 4.0D, 4.0D, 0.15D);
    public static final PhantomStats STORM_PHANTOM_STATS = stats("stormPhantom", 38.0D, 4.0D, 3.0D, 0.15D);
    public static final PhantomStats SAND_PHANTOM_STATS = stats("sandPhantom", 36.0D, 4.0D, 3.0D, 0.15D);
    public static final PhantomStats GOLD_PHANTOM_STATS = stats("goldPhantom", 36.0D, 4.0D, 5.0D, 0.15D);
    public static final PhantomStats CHERRY_PHANTOM_STATS = stats("cherryPhantom", 34.0D, 3.0D, 3.0D, 0.1D);
    public static final PhantomStats PHANTOM_DRAGON_STATS = stats("phantomDragon", 46.0D, 6.0D, 5.0D, 0.2D);
    public static final PhantomStats VIOLET_PHANTOM_STATS = stats("violetPhantom", 56.0D, 8.0D, 6.0D, 0.2D, 2.0D);
    public static final PhantomStats TOXIC_PHANTOM_STATS = stats("toxicPhantom", 64.0D, 6.0D, 8.0D, 0.35D, 2.0D, 48.0D);
    public static final PhantomStats SCULK_PHANTOM_STATS = stats("sculkPhantom", 60.0D, 6.0D, 8.0D, 0.3D, 2.0D);
    public static final PhantomStats RAVAGER_PHANTOM_STATS = stats("ravagerPhantom", 76.0D, 9.0D, 10.0D, 0.45D, 3.0D, 32.0D, 0.22D, 0.32D);
    public static final PhantomStats END_PHANTOM_STATS = stats("endPhantom", 56.0D, 7.0D, 6.0D, 0.25D, 2.0D, 32.0D, 0.28D, 0.45D);
    public static final PhantomStats ZOMBIE_PHANTOM_STATS = stats("zombiePhantom", 76.0D, 8.0D, 10.0D, 0.55D, 2.0D, 32.0D, 0.22D, 0.32D);
    public static final PhantomStats SKELETON_PHANTOM_STATS = stats("skeletonPhantom", 64.0D, 8.0D, 8.0D, 0.35D, 2.0D, 32.0D, 0.24D, 0.36D);
    public static final PhantomStats HUSK_PHANTOM_STATS = stats("huskPhantom", 88.0D, 9.0D, 14.0D, 0.70D, 4.0D, 32.0D, 0.20D, 0.35D);
    public static final PhantomStats ECHO_PHANTOM_STATS = stats("echoPhantom", 64.0D, 5.0D, 8.0D, 0.35D, 2.0D, 48.0D, 0.25D, 0.425D);
    public static final PhantomStats BLOOD_PHANTOM_STATS = stats("bloodPhantom", 72.0D, 8.0D, 10.0D, 0.45D, 3.0D, 40.0D, 0.24D, 0.4D);
    public static final PhantomStats VAMPIRE_PHANTOM_STATS = stats("vampirePhantom", 64.0D, 6.0D, 8.0D, 0.3D, 2.0D, 36.0D, 0.25D, 0.425D);
    public static final PhantomStats GRAVITY_PHANTOM_STATS = stats("gravityPhantom", 68.0D, 5.0D, 8.0D, 0.6D, 2.0D, 48.0D, 0.23D, 0.35D);
    public static final PhantomStats OMEN_PHANTOM_STATS = stats("omenPhantom", 76, 8, 10, .4D, 4, 56, .25D, .425D);
    public static final PhantomStats ANCIENT_PHANTOM_STATS = stats("ancientPhantom", 82, 9, 12, .5D, 6, 64, .25D, .4D);
    public static final PhantomStats REAPER_PHANTOM_STATS = stats("reaperPhantom", 72, 9, 10, .4D, 4, 48, .25D, .45D);
    public static final PhantomStats PRIMORDIAL_PHANTOM_STATS = stats("primordialPhantom", 88, 10, 14, .6D, 6, 64, .25D, .475D);
    public static final ModConfigSpec.IntValue GOLEM_MAX_VOLUME = BUILDER.defineInRange("golemPhantom.maxExcavationVolume", 512, 1, 4096);
    public static final ModConfigSpec.IntValue GOLEM_WORK_TICKS = BUILDER.defineInRange("golemPhantom.miningTicks", 12, 4, 200);
    public static final ModConfigSpec.BooleanValue ALLOW_OUT_OF_SEASON_HOLIDAY_SPAWNS = BUILDER.translation("config.phantomism.holidays.allowOutOfSeasonHolidaySpawns").comment("Allow Holiday Phantoms to spawn naturally outside their full festival month. Default false. Never restricts owned companions, commands or creative eggs.").define("holidays.allowOutOfSeasonHolidaySpawns", false);
    public static final ModConfigSpec.ConfigValue<String> HOLIDAY_TIME_ZONE = BUILDER.translation("config.phantomism.holidays.calendarTimeZone").comment("IANA server calendar timezone. UTC by default; independent of player timezone and Minecraft daylight. Example: Asia/Jerusalem.").define("holidays.calendarTimeZone", "UTC", Config::validHolidayZone);
    public static final ModConfigSpec.IntValue FIREWORK_FESTIVAL_MONTH = BUILDER.translation("config.phantomism.holidays.fireworkFestivalMonth").comment("Firework Phantom celebrates U.S. Independence Day for all of July, July 1-31 inclusive. The official default is 7; servers may explicitly choose another festival month.").defineInRange("holidays.fireworkFestivalMonth", 7, 1, 12);
    public static final ModConfigSpec.DoubleValue HOLIDAY_ALTERNATIVE_TAMING_CHANCE = BUILDER.translation("config.phantomism.holidays.provisionalTamingChance").comment("Provisional per-attempt chance for future Holiday alternative foods without fixed canon. Does not change Halloween's approved 50% seeds / 100% pumpkins or Staff's 100%.").defineInRange("holidays.provisionalTamingChance", .5D, 0, 1);
    private static boolean validHolidayZone(Object value) { try { if (!(value instanceof String zone)) { return false; } java.time.ZoneId.of(zone); return true; } catch (java.time.DateTimeException error) { return false; } }
    public static boolean allowOutOfSeasonHolidaySpawns() { return SPEC.isLoaded() && ALLOW_OUT_OF_SEASON_HOLIDAY_SPAWNS.get(); }
    public static String holidayTimeZone() { return SPEC.isLoaded() ? HOLIDAY_TIME_ZONE.get() : "UTC"; }
    public static int fireworkFestivalMonth() { return SPEC.isLoaded() ? FIREWORK_FESTIVAL_MONTH.get() : 7; }
    public static double holidayAlternativeTamingChance() { return SPEC.isLoaded() ? HOLIDAY_ALTERNATIVE_TAMING_CHANCE.get() : .5D; }
    public static final PhantomStats EASTER_PHANTOM_STATS = stats("easterPhantom", 88, 7, 6, .25D, 2, 48, .27D, .49D);
    public static final PhantomStats CHRISTMAS_PHANTOM_STATS = stats("christmasPhantom", 96, 7, 8, .35D, 3, 48, .25D, .44D);
    public static final PhantomStats HALLOWEEN_PHANTOM_STATS = stats("halloweenPhantom", 120, 10, 12, .45D, 4, 48, .25D, .48D);
    public static final ModConfigSpec.BooleanValue HALLOWEEN_NATURAL_SPAWNING = BUILDER.translation("config.phantomism.halloweenPhantom.naturalSpawning").comment("Permit common, population-limited pumpkin encounters in the Overworld during October, day and night. The holiday override permits other months. Disabling this never disables owned companions.").define("halloweenPhantom.naturalSpawning", true);
    public static final ModConfigSpec.IntValue HALLOWEEN_PUMPKIN_RADIUS = BUILDER.translation("config.phantomism.halloweenPhantom.pumpkinRadius").comment("Maximum distance from a visible Pumpkin, Carved Pumpkin or Jack o'Lantern. Natural encounters search around actual pumpkin patches, not random distant points.").defineInRange("halloweenPhantom.pumpkinRadius", 16, 1, 32);
    public static final ModConfigSpec.IntValue HALLOWEEN_PUMPKIN_VERTICAL = BUILDER.translation("config.phantomism.halloweenPhantom.pumpkinVerticalRange").comment("Maximum vertical difference from the pumpkin, still bounded by the total pumpkin radius and direct visibility.").defineInRange("halloweenPhantom.pumpkinVerticalRange", 16, 1, 32);
    public static final ModConfigSpec.IntValue HALLOWEEN_SPAWN_INTERVAL = BUILDER.translation("config.phantomism.halloweenPhantom.spawnInterval").defineInRange("halloweenPhantom.spawnInterval", 100, 20, 2400);
    public static final ModConfigSpec.IntValue HALLOWEEN_SPAWN_CHANCE = BUILDER.translation("config.phantomism.halloweenPhantom.spawnChance").comment("One successful roll in this many encounter checks, before pumpkin, safety and population checks. One permits common in-season encounters.").defineInRange("halloweenPhantom.spawnChance", 1, 1, 1000);
    public static final ModConfigSpec.IntValue HALLOWEEN_POPULATION_LIMIT = BUILDER.translation("config.phantomism.halloweenPhantom.populationLimit").comment("Maximum nearby wild Halloween Phantoms within 48 blocks. Tamed companions do not consume encounter slots.").defineInRange("halloweenPhantom.populationLimit", 4, 1, 8);
    public static final ModConfigSpec.IntValue HALLOWEEN_ULTIMATE_COOLDOWN = BUILDER.translation("config.phantomism.halloweenPhantom.ultimateCooldown").defineInRange("halloweenPhantom.ultimateCooldown", 1800, 600, 12000);
    public static boolean halloweenSpawningEnabled() { return !SPEC.isLoaded() || HALLOWEEN_NATURAL_SPAWNING.get(); }
    public static int halloweenPumpkinRadius() { return SPEC.isLoaded() ? HALLOWEEN_PUMPKIN_RADIUS.get() : 16; }
    public static int halloweenPumpkinVerticalRange() { return SPEC.isLoaded() ? HALLOWEEN_PUMPKIN_VERTICAL.get() : 16; }
    public static int halloweenSpawnInterval() { return SPEC.isLoaded() ? HALLOWEEN_SPAWN_INTERVAL.get() : 100; }
    public static int halloweenSpawnChance() { return SPEC.isLoaded() ? HALLOWEEN_SPAWN_CHANCE.get() : 1; }
    public static int halloweenPopulationLimit() { return SPEC.isLoaded() ? HALLOWEEN_POPULATION_LIMIT.get() : 4; }
    public static int halloweenUltimateCooldown() { return SPEC.isLoaded() ? HALLOWEEN_ULTIMATE_COOLDOWN.get() : 1800; }
    public static final PhantomStats AUTUMN_PHANTOM_STATS = stats("autumnPhantom", 240, 15, 14, .55D, 4, 48, .3D, .48D);
    public static final ModConfigSpec.BooleanValue AUTUMN_FOREST_FALLBACK = BUILDER.translation("config.phantomism.autumnPhantom.forestFallback").comment("Allow suitable temperate forests when no Dappled Forest biome is registered. Autumn is available all year.").define("autumnPhantom.forestFallback", true);
    public static boolean autumnFallbackLoaded() { return SPEC.isLoaded(); }
    public static final PhantomStats LUNAR_PHANTOM_STATS = stats("lunarPhantom", 300, 20, 18, .7D, 6, 56, .3D, .5D);
    public static final PhantomStats INFERNAL_PHANTOM_STATS = stats("infernalPhantom", 280, 18, 16, .8D, 6, 48, .3D, .5D);
    public static final PhantomStats ILLAGER_PHANTOM_STATS = stats("illagerPhantom", 280, 18, 16, .65D, 6, 56, .3D, .45D);
    public static final PhantomStats SOLAR_PHANTOM_STATS = stats("solarPhantom", 300, 20, 18, .85D, 6, 56, .3D, .5D);
    public static final PhantomStats SPORE_PHANTOM_STATS = stats("sporePhantom", 72, 6, 10, .35D, 2, 48, .3D, .4D);
    public static final PhantomStats GALE_PHANTOM_STATS = stats("galePhantom", 56, 7, 6, .4D, 2, 48, .3D, .6D);
    public static final PhantomStats OBSIDIAN_PHANTOM_STATS = stats("obsidianPhantom", 144, 10, 28, .95D, 10, 48, .2D, .3D);
    public static final PhantomStats CRYSTAL_PHANTOM_STATS = stats("crystalPhantom", 100, 8, 22, .75D, 8, 48, .25D, .35D);
    public static final PhantomStats PLAGUE_PHANTOM_STATS = stats("plaguePhantom", 68, 6, 8, .35D, 2, 48, .25D, .4D);
    public static final PhantomStats SHADOW_PHANTOM_STATS = stats("shadowPhantom", 60, 10, 6, .25D, 2, 48, .25D, .55D);
    public static final PhantomStats ECLIPSE_PHANTOM_STATS = stats("eclipsePhantom", 104, 12, 16, .65D, 6, 56, .25D, .475D);
    public static final PhantomStats MIRAGE_PHANTOM_STATS = stats("miragePhantom", 60, 6, 6, .25D, 2, 48, .25D, .5D);
    public static final PhantomStats GOLEM_PHANTOM_STATS = stats("golemPhantom", 400, 16, 30, .95D, 12, 48, .2D, .3D);
    public static final PhantomStats PHANTOM_KING_STATS = stats("phantomKing", 144, 14, 22, .85D, 8, 64, .25D, .45D);
    public static final PhantomStats DARK_PHANTOM_STATS = stats("darkPhantom", 96, 12, 14, .55D, 4, 56, .25D, .475D);
    public static final PhantomStats VOID_PHANTOM_STATS = stats("voidPhantom", 80, 7, 12, .55D, 4, 56, .25D, .45D);
    public static final PhantomStats ANGEL_PHANTOM_STATS = stats("angelPhantom", 76, 5, 10, .4D, 4, 56, .25D, .425D);
    public static final PhantomStats SPECTRAL_PHANTOM_STATS = stats("spectralPhantom", 56, 7, 4, .2D, 0, 48, .25D, .525D);
    public static final PhantomStats DEMON_PHANTOM_STATS = stats("demonPhantom", 100, 14, 14, .7D, 5, 48, .25D, .4D);
    public static final PhantomStats SENTINEL_PHANTOM_STATS = stats("sentinelPhantom", 112, 12, 20, .85D, 6, 56, .24D, .4D);
    public static final PhantomStats RIFT_PHANTOM_STATS = stats("riftPhantom", 64, 5, 8, .30D, 2, 48, .24D, .45D);
    public static final PhantomStats CHRONO_PHANTOM_STATS = stats("chronoPhantom", 64, 4, 8, .35D, 2, 48, .24D, .4D);
    public static final PhantomStats WITHER_PHANTOM_STATS = stats("witherPhantom", 108.0D, 10.0D, 16.0D, 0.65D, 6.0D, 56.0D, 0.25D, 0.425D);
    public static final ModConfigSpec.IntValue HUSK_EXHAUSTION_DURATION = BUILDER.defineInRange("huskExhaustionDurationTicks", 160, 20, 600);
    public static int huskExhaustionDuration() { return SPEC.isLoaded() ? HUSK_EXHAUSTION_DURATION.get() : 160; }
    public static final ModConfigSpec.BooleanValue RAVAGER_BULLDOZER = BUILDER
            .comment("Allow adult owned Ravager Phantoms to clear tagged natural foliage only during a charge. Requires mobGriefing and owner/protection permission.")
            .translation("config.phantomism.ravagerPhantom.bulldozer")
            .define("ravagerPhantom.bulldozer", true);
    public static final ModConfigSpec.BooleanValue RAVAGER_SIEGE_BREAKING = BUILDER
            .comment("Allow owner-mounted Siege Charge to breach tagged blocks with sufficient momentum and a suitable held tool.",
                    "Combat still works when disabled. Containers, portals, protected blocks and unbreakable blocks are always excluded.")
            .translation("config.phantomism.ravagerPhantom.siegeBreaking")
            .define("ravagerPhantom.siegeBreaking", true);

    static {
        BUILDER.comment("Shared Home patrol and family protection settings.")
                .translation("config.phantomism.phantom").push("phantom").pop();
    }

    public static final ModConfigSpec.DoubleValue SPECTRAL_STRIKE_BYPASS = BUILDER.translation("config.phantomism.spectralPhantom.strikeBypass").defineInRange("spectralPhantom.strikeBypass", .25D, 0, .5D);
    public static final ModConfigSpec.DoubleValue SPECTRAL_PLANE_BYPASS = BUILDER.translation("config.phantomism.spectralPhantom.planeBypass").defineInRange("spectralPhantom.planeBypass", .4D, 0, .5D);
    public static final ModConfigSpec.DoubleValue SPECTRAL_PLANE_SPEED = BUILDER.translation("config.phantomism.spectralPhantom.planeSpeed").defineInRange("spectralPhantom.planeSpeed", .15D, 0, .3D);
    public static final ModConfigSpec.DoubleValue SPECTRAL_PLANE_MITIGATION = BUILDER.translation("config.phantomism.spectralPhantom.planeMitigation").defineInRange("spectralPhantom.planeMitigation", .15D, 0, .3D);
    public static double spectralBypass(boolean plane) { return SPEC.isLoaded() ? (plane ? SPECTRAL_PLANE_BYPASS.get() : SPECTRAL_STRIKE_BYPASS.get()) : plane ? .4D : .25D; }
    public static double spectralPlaneSpeed() { return SPEC.isLoaded() ? SPECTRAL_PLANE_SPEED.get() : .15D; }
    public static double spectralPlaneMitigation() { return SPEC.isLoaded() ? SPECTRAL_PLANE_MITIGATION.get() : .15D; }

    public static final ModConfigSpec.DoubleValue ANGEL_TOUCH_HEAL = BUILDER.defineInRange("angelPhantom.touchHeal", 12.0D, 0.0D, 24.0D);
    public static final ModConfigSpec.DoubleValue ANGEL_TRAIL_HEAL = BUILDER.defineInRange("angelPhantom.trailHeal", 1.0D, 0.0D, 3.0D);
    public static final ModConfigSpec.DoubleValue ANGEL_EYE_HEAL = BUILDER.defineInRange("angelPhantom.eyeHeal", 18.0D, 0.0D, 36.0D);
    public static final ModConfigSpec.DoubleValue ANGEL_BLESSING_MITIGATION = BUILDER.defineInRange("angelPhantom.blessingMitigation", 0.15D, 0.0D, 0.25D);
    public static final ModConfigSpec.DoubleValue ANGEL_SHIELD_MITIGATION = BUILDER.defineInRange("angelPhantom.shieldMitigation", 0.3D, 0.0D, 0.35D);
    public static final ModConfigSpec.DoubleValue ANGEL_SHIELD_CAPACITY = BUILDER.defineInRange("angelPhantom.shieldCapacity", 8.0D, 0.0D, 16.0D);
    public static final ModConfigSpec.DoubleValue ANGEL_EYE_MITIGATION = BUILDER.defineInRange("angelPhantom.eyeMitigation", 0.12D, 0.0D, 0.2D);
    public static double angelValue(ModConfigSpec.DoubleValue value, double fallback) { return SPEC.isLoaded() ? value.get() : fallback; }

    private static final java.util.Map<String,Double> VOID_DEFAULTS = java.util.Map.ofEntries(
            java.util.Map.entry("boltDamage", 8.0D),
            java.util.Map.entry("boltRange", 42.0D),
            java.util.Map.entry("boltCooldown", 240.0D),
            java.util.Map.entry("mountedCooldown", 6.0D),
            java.util.Map.entry("fieldRadius", 10.0D),
            java.util.Map.entry("graspCooldown", 320.0D),
            java.util.Map.entry("graspDuration", 40.0D),
            java.util.Map.entry("graspRange", 18.0D),
            java.util.Map.entry("anchorCooldown", 800.0D),
            java.util.Map.entry("anchorLifetime", 1200.0D),
            java.util.Map.entry("pulseCooldown", 360.0D),
            java.util.Map.entry("pulseRadius", 12.0D),
            java.util.Map.entry("sealCooldown", 480.0D),
            java.util.Map.entry("sealDuration", 80.0D),
            java.util.Map.entry("stepCooldown", 320.0D),
            java.util.Map.entry("stepRange", 12.0D),
            java.util.Map.entry("stepMultiplier", 1.4D),
            java.util.Map.entry("shieldCooldown", 700.0D),
            java.util.Map.entry("shieldDuration", 120.0D),
            java.util.Map.entry("shieldMitigation", 0.3D),
            java.util.Map.entry("shieldRange", 20.0D));
    public static final java.util.Map<String,ModConfigSpec.DoubleValue> VOID_ABILITIES = voidSettings();
    private static java.util.Map<String,ModConfigSpec.DoubleValue> voidSettings() {
        var values = new java.util.LinkedHashMap<String,ModConfigSpec.DoubleValue>();
        for (var entry : VOID_DEFAULTS.entrySet()) {
            String name = entry.getKey(); double minimum = name.endsWith("Cooldown") ? 6 : name.endsWith("Duration") || name.endsWith("Radius") || name.equals("anchorLifetime") ? 1 : 0;
            double maximum = name.endsWith("Cooldown") || name.equals("anchorLifetime") ? 4800 : name.endsWith("Duration") ? 200 : name.endsWith("Radius") ? 16 : name.endsWith("Range") ? 42 : name.endsWith("Mitigation") ? .5D : name.endsWith("Multiplier") ? 3 : 100;
            values.put(name, BUILDER.translation("config.phantomism.voidPhantom.abilities."+name).defineInRange("voidPhantom.abilities."+name,entry.getValue(),minimum,maximum));
        }
        return java.util.Map.copyOf(values);
    }
    public static double voidValue(String name) { return SPEC.isLoaded() ? VOID_ABILITIES.get(name).get() : VOID_DEFAULTS.get(name); }

    public static final ModConfigSpec.BooleanValue SOLAR_AURA = BUILDER.define("solarPhantom.environment.aura", true);
    public static final ModConfigSpec.BooleanValue SOLAR_ILLUMINATION = BUILDER.define("solarPhantom.environment.temporaryVision", true);
    public static final ModConfigSpec.BooleanValue SOLAR_MELTING = BUILDER.define("solarPhantom.environment.meltSnowAndFrostedIce", false);
    public static final ModConfigSpec.BooleanValue SOLAR_SCORCHING = BUILDER.define("solarPhantom.environment.scorchGrass", false);
    public static boolean solarFlag(ModConfigSpec.BooleanValue flag, boolean fallback) { return SPEC.isLoaded() ? flag.get() : fallback; }
    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static PhantomStats stats(String species, double health, double damage, double armor, double knockback) {
        return stats(species, health, damage, armor, knockback, 0.0D);
    }

    private static PhantomStats stats(String species, double health, double damage, double armor, double knockback, double toughness) {
        return stats(species, health, damage, armor, knockback, toughness, 32.0D);
    }

    private static PhantomStats stats(String species, double health, double damage, double armor, double knockback, double toughness, double awareness) {
        return stats(species, health, damage, armor, knockback, toughness, awareness, 0.25D, 0.4D);
    }

    private static PhantomStats stats(String species, double health, double damage, double armor, double knockback, double toughness, double awareness, double movement, double flight) {
        BUILDER.comment("Settings for this species. Defaults preserve its normal balance.")
                .translation("config.phantomism." + species).push(species);
        BUILDER.comment("Base attributes for wild and tamed adults and babies. Ability bonuses are added separately.",
                        "Changes apply to existing loaded phantoms within one second. Raising health does not heal existing pets.",
                        "Very high speeds and awareness distances can affect navigation and server performance.")
                .translation("config.phantomism." + species + ".stats").push("stats");
        PhantomStats stats = new PhantomStats(
                stat(species, "maxHealth", health, 1.0D, 1024.0D, "Maximum health points. 2 points = 1 heart. Lowering this clamps current health; raising it does not heal existing pets."),
                stat(species, "attackDamage", damage, 0.0D, 2048.0D, "Base melee damage before armor and ability bonuses. 2 damage = 1 heart. Does not change projectile or area damage."),
                stat(species, "armor", armor, 0.0D, 30.0D, "Armor points used by Minecraft's damage reduction formula. 2 points = 1 armor icon."),
                stat(species, "armorToughness", toughness, 0.0D, 20.0D, "Reduces how strongly large hits penetrate armor. Vanilla diamond armor pieces provide 2 toughness each."),
                stat(species, "knockbackResistance", knockback, 0.0D, 1.0D, "Knockback resistance from 0 (none) to 1 (complete)."),
                stat(species, "movementSpeed", movement, 0.01D, 2.0D, "Base movement-speed attribute. Ability movement bonuses are added to this value."),
                stat(species, "flyingSpeed", flight, 0.01D, 2.0D, "Base flying-speed attribute. Also scales mounted horizontal movement; vertical aiming controls stay unchanged."),
                stat(species, "followRange", awareness, 4.0D, 128.0D, "Awareness and autonomous combat range in blocks. Home patrol radius is separate. Larger values increase nearby-entity scanning work."));
        BUILDER.pop(2);
        return stats;
    }

    private static ModConfigSpec.DoubleValue stat(String species, String key, double value, double minimum, double maximum, String description) {
        return BUILDER.comment(description).translation("config.phantomism." + species + ".stats." + key)
                .defineInRange(key, value, minimum, maximum);
    }

    public record PhantomStats(ModConfigSpec.DoubleValue maxHealth, ModConfigSpec.DoubleValue attackDamage,
            ModConfigSpec.DoubleValue armor, ModConfigSpec.DoubleValue armorToughness,
            ModConfigSpec.DoubleValue knockbackResistance, ModConfigSpec.DoubleValue movementSpeed,
            ModConfigSpec.DoubleValue flyingSpeed, ModConfigSpec.DoubleValue followRange) {
        public void apply(LivingEntity phantom) {
            if (!SPEC.isLoaded()) {
                return;
            }
            applyAttribute(phantom, Attributes.MAX_HEALTH, this.maxHealth.get());
            applyAttribute(phantom, Attributes.ATTACK_DAMAGE, this.attackDamage.get());
            applyAttribute(phantom, Attributes.ARMOR, this.armor.get());
            applyAttribute(phantom, Attributes.ARMOR_TOUGHNESS, this.armorToughness.get());
            applyAttribute(phantom, Attributes.KNOCKBACK_RESISTANCE, this.knockbackResistance.get());
            applyAttribute(phantom, Attributes.MOVEMENT_SPEED, this.movementSpeed.get());
            applyAttribute(phantom, Attributes.FLYING_SPEED, this.flyingSpeed.get());
            applyAttribute(phantom, Attributes.FOLLOW_RANGE, this.followRange.get());
            if (phantom.getHealth() > phantom.getMaxHealth()) {
                phantom.setHealth(phantom.getMaxHealth());
            }
        }

        private static void applyAttribute(LivingEntity phantom, Holder<Attribute> attribute, double value) {
            var instance = phantom.getAttribute(attribute);
            if (instance != null && instance.getBaseValue() != value) {
                instance.setBaseValue(value);
            }
        }
    }
}
