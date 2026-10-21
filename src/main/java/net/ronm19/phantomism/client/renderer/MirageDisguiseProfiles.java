package net.ronm19.phantomism.client.renderer;

import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;

public final class MirageDisguiseProfiles {
    private MirageDisguiseProfiles() {}
    public static MirageDecoyRenderer.Profile get(String species) {
        return switch (species) {
            case "ancient_phantom" -> new MirageDecoyRenderer.Profile("ancient_phantom", AncientPhantomRenderer.ACCESSORIES);
            case "angel_phantom" -> new MirageDecoyRenderer.Profile("angel_phantom", AngelPhantomRenderer.ACCESSORIES);
            case "black_phantom" -> new MirageDecoyRenderer.Profile("black_phantom", BlackPhantomRenderer.ACCESSORIES);
            case "blood_phantom" -> new MirageDecoyRenderer.Profile("blood_phantom", BloodPhantomRenderer.ACCESSORIES);
            case "cherry_phantom" -> new MirageDecoyRenderer.Profile("cherry_phantom", CherryPhantomRenderer.ACCESSORIES);
            case "chrono_phantom" -> new MirageDecoyRenderer.Profile("chrono_phantom", ChronoPhantomRenderer.ACCESSORIES);
            case "dark_phantom" -> new MirageDecoyRenderer.Profile("dark_phantom", DarkPhantomRenderer.ACCESSORIES);
            case "demon_phantom" -> new MirageDecoyRenderer.Profile("demon_phantom", DemonPhantomRenderer.ACCESSORIES);
            case "earth_phantom" -> new MirageDecoyRenderer.Profile("earth_phantom", EarthPhantomRenderer.ACCESSORIES);
            case "echo_phantom" -> new MirageDecoyRenderer.Profile("echo_phantom", EchoPhantomRenderer.ACCESSORIES);
            case "end_phantom" -> new MirageDecoyRenderer.Profile("end_phantom", EndPhantomRenderer.ACCESSORIES);
            case "fire_phantom" -> new MirageDecoyRenderer.Profile("fire_phantom", FirePhantomRenderer.ACCESSORIES);
            case "frost_phantom" -> new MirageDecoyRenderer.Profile("frost_phantom", FrostPhantomRenderer.ACCESSORIES);
            case "gold_phantom" -> new MirageDecoyRenderer.Profile("gold_phantom", GoldPhantomRenderer.ACCESSORIES);
            case "golem_phantom" -> new MirageDecoyRenderer.Profile("golem_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES);
            case "gravity_phantom" -> new MirageDecoyRenderer.Profile("gravity_phantom", GravityPhantomRenderer.ACCESSORIES);
            case "husk_phantom" -> new MirageDecoyRenderer.Profile("husk_phantom", HuskPhantomRenderer.ACCESSORIES);
            case "mirage_phantom" -> new MirageDecoyRenderer.Profile("mirage_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES);
            case "omen_phantom" -> new MirageDecoyRenderer.Profile("omen_phantom", OmenPhantomRenderer.ACCESSORIES);
            case "phantom_dragon" -> new MirageDecoyRenderer.Profile("phantom_dragon", PhantomDragonRenderer.ACCESSORIES);
            case "phantom_king" -> new MirageDecoyRenderer.Profile("phantom_king", PhantomKingRenderer.ACCESSORIES);
            case "primordial_phantom" -> new MirageDecoyRenderer.Profile("primordial_phantom", PrimordialPhantomRenderer.ACCESSORIES);
            case "ravager_phantom" -> new MirageDecoyRenderer.Profile("ravager_phantom", RavagerPhantomRenderer.ACCESSORIES);
            case "reaper_phantom" -> new MirageDecoyRenderer.Profile("reaper_phantom", ReaperPhantomRenderer.ACCESSORIES);
            case "rift_phantom" -> new MirageDecoyRenderer.Profile("rift_phantom", RiftPhantomRenderer.ACCESSORIES);
            case "sand_phantom" -> new MirageDecoyRenderer.Profile("sand_phantom", SandPhantomRenderer.ACCESSORIES);
            case "sculk_phantom" -> new MirageDecoyRenderer.Profile("sculk_phantom", SculkPhantomRenderer.ACCESSORIES);
            case "sentinel_phantom" -> new MirageDecoyRenderer.Profile("sentinel_phantom", SentinelPhantomRenderer.ACCESSORIES);
            case "skeleton_phantom" -> new MirageDecoyRenderer.Profile("skeleton_phantom", SkeletonPhantomRenderer.ACCESSORIES);
            case "spectral_phantom" -> new MirageDecoyRenderer.Profile("spectral_phantom", SpectralPhantomRenderer.ACCESSORIES);
            case "storm_phantom" -> new MirageDecoyRenderer.Profile("storm_phantom", StormPhantomRenderer.ACCESSORIES);
            case "toxic_phantom" -> new MirageDecoyRenderer.Profile("toxic_phantom", ToxicPhantomRenderer.ACCESSORIES);
            case "vampire_phantom" -> new MirageDecoyRenderer.Profile("vampire_phantom", VampirePhantomRenderer.ACCESSORIES);
            case "violet_phantom" -> new MirageDecoyRenderer.Profile("violet_phantom", VioletPhantomRenderer.ACCESSORIES);
            case "void_phantom" -> new MirageDecoyRenderer.Profile("void_phantom", VoidPhantomRenderer.ACCESSORIES);
            case "water_phantom" -> new MirageDecoyRenderer.Profile("water_phantom", WaterPhantomRenderer.ACCESSORIES);
            case "wither_phantom" -> new MirageDecoyRenderer.Profile("wither_phantom", WitherPhantomRenderer.ACCESSORIES);
            case "zombie_phantom" -> new MirageDecoyRenderer.Profile("zombie_phantom", ZombiePhantomRenderer.ACCESSORIES);
            default -> new MirageDecoyRenderer.Profile("mirage_phantom", PhantomismAccessoryVisibility.NO_ACCESSORIES);
        };
    }
}
