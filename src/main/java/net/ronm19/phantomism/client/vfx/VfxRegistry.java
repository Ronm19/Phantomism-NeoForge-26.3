package net.ronm19.phantomism.client.vfx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.vfx.CinematicVfx;

public final class VfxRegistry {
    public enum Form { PROJECTILE, IMPACT, SUPPORT, AFTERIMAGE, REVERSE, SIGIL, STASIS, AREA, CINEMATIC, BEAM, SLASH, ORBIT, SPATIAL_PROJECTILE, TEAR, PORTAL, SHARDS, SPATIAL_LOCK, SPATIAL_ULTIMATE, SPATIAL_AFTERIMAGE, SPATIAL_IMPACT, SPATIAL_ANCHOR, SPATIAL_SENSE, SPATIAL_TRANSIT, SPATIAL_SLASH, GUARDIAN, BASTION, INFERNAL, INFERNAL_BREATH, INFERNAL_METEOR, APOCALYPSE, SPECTRAL, SPECTRAL_AFTERIMAGE, SPECTRAL_LANCE, ETHEREAL_PLANE, CELESTIAL, CELESTIAL_SMITE, CELESTIAL_EYE, OMEN, OMEN_BOLT, CALAMITY, ANCIENT, ANCIENT_STONE, FORGOTTEN_LAND, REAPER, REAPER_SHARD, LAST_BELL, PRIMORDIAL, PRIMORDIAL_STAR, PRIMAL_DOMAIN, VOID, VOID_BOLT, VOID_AFTERIMAGE, NULL_HORIZON, DARK, DARK_BOLT, DARK_BLADE, DARK_HOUR, ROYAL, KINGS_DOMINION, TITANIUM, TITANS_IMPACT, MIRAGE, HALL_OF_MIRRORS }
    public record Definition(Form form, Identifier face, Identifier ring, Identifier hand, Identifier trail, Identifier silhouette, VfxSequence sequence, float width) {
        public Definition { if (!Float.isFinite(width) || width <= 0 || width > 4) { throw new IllegalArgumentException("Invalid effect width"); } }
    }
    private static final Map<Identifier, Definition> DEFINITIONS = new HashMap<>();
    private VfxRegistry() {}
    public static void register(Identifier id, Definition definition) { if (DEFINITIONS.putIfAbsent(id, definition) != null) { throw new IllegalArgumentException("Duplicate VFX " + id); } }
    public static Definition get(Identifier id) { return DEFINITIONS.get(id); }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/vfx/" + name + ".png"); }
    public static boolean cinematic(Definition definition) { return definition.form() == Form.CINEMATIC || definition.form() == Form.SPATIAL_ULTIMATE || definition.form() == Form.BASTION || definition.form() == Form.APOCALYPSE || definition.form() == Form.ETHEREAL_PLANE || definition.form() == Form.CELESTIAL_EYE || definition.form() == Form.CALAMITY || definition.form() == Form.FORGOTTEN_LAND || definition.form() == Form.LAST_BELL || definition.form() == Form.PRIMAL_DOMAIN || definition.form() == Form.NULL_HORIZON || definition.form() == Form.DARK_HOUR || definition.form() == Form.KINGS_DOMINION || definition.form() == Form.TITANS_IMPACT || definition.form() == Form.HALL_OF_MIRRORS; }
    public static boolean afterimage(Definition definition) { return definition.form() == Form.VOID_AFTERIMAGE || definition.form() == Form.AFTERIMAGE || definition.form() == Form.SPATIAL_AFTERIMAGE || definition.form() == Form.SPECTRAL_AFTERIMAGE || definition.form() == Form.ETHEREAL_PLANE; }
    private static void spatial(Identifier id, Form form, VfxSequence sequence) { register(id, new Definition(form, texture("rift_surface"), texture("rift_shard"), texture("rift_sigil"), texture("rift_trail"), Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/rift_phantom.png"), sequence, 1)); }
    static {
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.MirageVfx.FORM, net.ronm19.phantomism.vfx.MirageVfx.BOLT, net.ronm19.phantomism.vfx.MirageVfx.FAKE_BOLT, net.ronm19.phantomism.vfx.MirageVfx.STRIKE, net.ronm19.phantomism.vfx.MirageVfx.RESCUE, net.ronm19.phantomism.vfx.MirageVfx.FIELD, net.ronm19.phantomism.vfx.MirageVfx.GUARD, net.ronm19.phantomism.vfx.MirageVfx.SHATTER, net.ronm19.phantomism.vfx.MirageVfx.OPENING, net.ronm19.phantomism.vfx.MirageVfx.HALL, net.ronm19.phantomism.vfx.MirageVfx.CONVERGE, net.ronm19.phantomism.vfx.MirageVfx.FINALE)) {
            register(effect, new Definition(effect.equals(net.ronm19.phantomism.vfx.MirageVfx.HALL) ? Form.HALL_OF_MIRRORS : Form.MIRAGE,
                    texture("mirage_sigil"), texture("mirage_panel"), texture("mirage_fragment"), texture("mirage_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/mirage_phantom.png"), new VfxSequence(40, 160, 40), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.TitaniumVfx.HOOK, net.ronm19.phantomism.vfx.TitaniumVfx.FIST, net.ronm19.phantomism.vfx.TitaniumVfx.SLAM, net.ronm19.phantomism.vfx.TitaniumVfx.RAM, net.ronm19.phantomism.vfx.TitaniumVfx.GUARD, net.ronm19.phantomism.vfx.TitaniumVfx.SHIELD_HIT, net.ronm19.phantomism.vfx.TitaniumVfx.MINE, net.ronm19.phantomism.vfx.TitaniumVfx.CHARGE, net.ronm19.phantomism.vfx.TitaniumVfx.IMPACT)) {
            register(effect, new Definition(effect.equals(net.ronm19.phantomism.vfx.TitaniumVfx.IMPACT) ? Form.TITANS_IMPACT : Form.TITANIUM,
                    texture("titanium_sigil"), texture("titanium_shield"), texture("titanium_fist"), texture("titanium_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/golem_phantom.png"), new VfxSequence(24, 156, 20), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.RoyalVfx.PRESENCE, net.ronm19.phantomism.vfx.RoyalVfx.RAM, net.ronm19.phantomism.vfx.RoyalVfx.RAM_IMPACT, net.ronm19.phantomism.vfx.RoyalVfx.JUDGMENT, net.ronm19.phantomism.vfx.RoyalVfx.STRIKE, net.ronm19.phantomism.vfx.RoyalVfx.STRIKE_ARC, net.ronm19.phantomism.vfx.RoyalVfx.ROAR, net.ronm19.phantomism.vfx.RoyalVfx.COMMAND, net.ronm19.phantomism.vfx.RoyalVfx.DIRECTIVE, net.ronm19.phantomism.vfx.RoyalVfx.RALLY, net.ronm19.phantomism.vfx.RoyalVfx.GUARD, net.ronm19.phantomism.vfx.RoyalVfx.INTERCEPT, net.ronm19.phantomism.vfx.RoyalVfx.DOMINION, net.ronm19.phantomism.vfx.RoyalVfx.MARK, net.ronm19.phantomism.vfx.RoyalVfx.FINALE, net.ronm19.phantomism.vfx.RoyalVfx.IMPACT, net.ronm19.phantomism.vfx.RoyalVfx.WARNING, net.ronm19.phantomism.vfx.RoyalVfx.HESITATE)) {
            register(effect, new Definition(effect.equals(net.ronm19.phantomism.vfx.RoyalVfx.DOMINION) ? Form.KINGS_DOMINION : Form.ROYAL,
                    texture("royal_seal"), texture("royal_standard"), texture("royal_crown"), texture("royal_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/phantom_king.png"), new VfxSequence(40, 236, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.DarkVfx.PRESSURE, net.ronm19.phantomism.vfx.DarkVfx.BOLT, net.ronm19.phantomism.vfx.DarkVfx.IMPACT, net.ronm19.phantomism.vfx.DarkVfx.REND, net.ronm19.phantomism.vfx.DarkVfx.RUSH, net.ronm19.phantomism.vfx.DarkVfx.RUSH_IMPACT, net.ronm19.phantomism.vfx.DarkVfx.BLACKOUT, net.ronm19.phantomism.vfx.DarkVfx.DISRUPT, net.ronm19.phantomism.vfx.DarkVfx.BUBBLE, net.ronm19.phantomism.vfx.DarkVfx.SHIELD, net.ronm19.phantomism.vfx.DarkVfx.SHIELD_HIT, net.ronm19.phantomism.vfx.DarkVfx.HOUR, net.ronm19.phantomism.vfx.DarkVfx.MARK, net.ronm19.phantomism.vfx.DarkVfx.BLADE, net.ronm19.phantomism.vfx.DarkVfx.BLADE_IMPACT, net.ronm19.phantomism.vfx.DarkVfx.FINALE, net.ronm19.phantomism.vfx.DarkVfx.WARNING)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.DarkVfx.HOUR) ? Form.DARK_HOUR : effect.equals(net.ronm19.phantomism.vfx.DarkVfx.BOLT) ? Form.DARK_BOLT : effect.equals(net.ronm19.phantomism.vfx.DarkVfx.BLADE) ? Form.DARK_BLADE : Form.DARK;
            register(effect, new Definition(form, texture("dark_sigil"), texture("dark_blade"), texture("dark_core"), texture("dark_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/dark_phantom.png"), new VfxSequence(40, 136, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.VoidVfx.FIELD, net.ronm19.phantomism.vfx.VoidVfx.BOLT, net.ronm19.phantomism.vfx.VoidVfx.IMPACT, net.ronm19.phantomism.vfx.VoidVfx.GRASP, net.ronm19.phantomism.vfx.VoidVfx.ANCHOR, net.ronm19.phantomism.vfx.VoidVfx.RESCUE, net.ronm19.phantomism.vfx.VoidVfx.PULSE, net.ronm19.phantomism.vfx.VoidVfx.SEAL, net.ronm19.phantomism.vfx.VoidVfx.STEP, net.ronm19.phantomism.vfx.VoidVfx.ARRIVAL, net.ronm19.phantomism.vfx.VoidVfx.SHIELD, net.ronm19.phantomism.vfx.VoidVfx.HORIZON, net.ronm19.phantomism.vfx.VoidVfx.WAVE, net.ronm19.phantomism.vfx.VoidVfx.MARK, net.ronm19.phantomism.vfx.VoidVfx.COLLAPSE)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.VoidVfx.HORIZON) ? Form.NULL_HORIZON : effect.equals(net.ronm19.phantomism.vfx.VoidVfx.BOLT) ? Form.VOID_BOLT : effect.equals(net.ronm19.phantomism.vfx.VoidVfx.STEP) ? Form.VOID_AFTERIMAGE : Form.VOID;
            register(effect, new Definition(form, texture("void_sigil"), texture("void_fracture"), texture("void_core"), texture("void_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"textures/entity/void_phantom.png"), new VfxSequence(45,165,30),1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.PrimordialVfx.AURA, net.ronm19.phantomism.vfx.PrimordialVfx.INSIGHT, net.ronm19.phantomism.vfx.PrimordialVfx.WING,
                net.ronm19.phantomism.vfx.PrimordialVfx.SHIFT, net.ronm19.phantomism.vfx.PrimordialVfx.ARRIVAL, net.ronm19.phantomism.vfx.PrimordialVfx.STRIKE, net.ronm19.phantomism.vfx.PrimordialVfx.STAR,
                net.ronm19.phantomism.vfx.PrimordialVfx.SHATTER, net.ronm19.phantomism.vfx.PrimordialVfx.RESTORATION, net.ronm19.phantomism.vfx.PrimordialVfx.HEAL, net.ronm19.phantomism.vfx.PrimordialVfx.NOVA,
                net.ronm19.phantomism.vfx.PrimordialVfx.DOMAIN, net.ronm19.phantomism.vfx.PrimordialVfx.JUDGMENT, net.ronm19.phantomism.vfx.PrimordialVfx.CONCLUSION, net.ronm19.phantomism.vfx.PrimordialVfx.IMPACT)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.PrimordialVfx.DOMAIN) ? Form.PRIMAL_DOMAIN : effect.equals(net.ronm19.phantomism.vfx.PrimordialVfx.STAR) ? Form.PRIMORDIAL_STAR : Form.PRIMORDIAL;
            register(effect, new Definition(form, texture("primordial_sigil"), texture("primordial_ruin"), texture("primordial_wing"), texture("primordial_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/primordial_phantom.png"), new VfxSequence(45, 215, 40), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.ReaperVfx.SENSE, net.ronm19.phantomism.vfx.ReaperVfx.HARVEST, net.ronm19.phantomism.vfx.ReaperVfx.SOULS,
                net.ronm19.phantomism.vfx.ReaperVfx.MARK, net.ronm19.phantomism.vfx.ReaperVfx.CUT, net.ronm19.phantomism.vfx.ReaperVfx.SEVER, net.ronm19.phantomism.vfx.ReaperVfx.PASSAGE,
                net.ronm19.phantomism.vfx.ReaperVfx.ARRIVAL, net.ronm19.phantomism.vfx.ReaperVfx.SHARD, net.ronm19.phantomism.vfx.ReaperVfx.BARGAIN, net.ronm19.phantomism.vfx.ReaperVfx.BELL,
                net.ronm19.phantomism.vfx.ReaperVfx.JUDGMENT, net.ronm19.phantomism.vfx.ReaperVfx.CONCLUSION, net.ronm19.phantomism.vfx.ReaperVfx.IMPACT)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.ReaperVfx.BELL) ? Form.LAST_BELL : effect.equals(net.ronm19.phantomism.vfx.ReaperVfx.SHARD) ? Form.REAPER_SHARD : Form.REAPER;
            register(effect, new Definition(form, texture("reaper_sigil"), texture("reaper_bell"), texture("reaper_soul"), texture("reaper_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/reaper_phantom.png"), new VfxSequence(60, 140, 40), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.AncientVfx.MARK, net.ronm19.phantomism.vfx.AncientVfx.STRIKE, net.ronm19.phantomism.vfx.AncientVfx.STONE,
                net.ronm19.phantomism.vfx.AncientVfx.SHIELD, net.ronm19.phantomism.vfx.AncientVfx.DIVE, net.ronm19.phantomism.vfx.AncientVfx.QUAKE, net.ronm19.phantomism.vfx.AncientVfx.SANCTUARY,
                net.ronm19.phantomism.vfx.AncientVfx.FORGOTTEN, net.ronm19.phantomism.vfx.AncientVfx.JUDGMENT, net.ronm19.phantomism.vfx.AncientVfx.CONCLUSION, net.ronm19.phantomism.vfx.AncientVfx.FRAGMENTS, net.ronm19.phantomism.vfx.AncientVfx.IMPACT)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.AncientVfx.FORGOTTEN) ? Form.FORGOTTEN_LAND : effect.equals(net.ronm19.phantomism.vfx.AncientVfx.STONE) ? Form.ANCIENT_STONE : Form.ANCIENT;
            register(effect, new Definition(form, texture("ancient_sigil"), texture("ancient_pillar"), texture("ancient_stone"), texture("ancient_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/ancient_phantom.png"), new VfxSequence(100, 150, 30), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.OmenVfx.BOLT, net.ronm19.phantomism.vfx.OmenVfx.MARK, net.ronm19.phantomism.vfx.OmenVfx.STANDARD,
                net.ronm19.phantomism.vfx.OmenVfx.SHIELD, net.ronm19.phantomism.vfx.OmenVfx.DIVE, net.ronm19.phantomism.vfx.OmenVfx.CALAMITY, net.ronm19.phantomism.vfx.OmenVfx.JUDGMENT,
                net.ronm19.phantomism.vfx.OmenVfx.CONCLUSION, net.ronm19.phantomism.vfx.OmenVfx.HARBINGER, net.ronm19.phantomism.vfx.OmenVfx.IMPACT, net.ronm19.phantomism.vfx.OmenVfx.REVEAL)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.OmenVfx.CALAMITY) ? Form.CALAMITY : effect.equals(net.ronm19.phantomism.vfx.OmenVfx.BOLT) ? Form.OMEN_BOLT : Form.OMEN;
            register(effect, new Definition(form, texture("omen_sigil"), texture("omen_banner"), texture("omen_crest"), texture("omen_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/omen_phantom.png"), new VfxSequence(60, 156, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.AngelVfx.TOUCH, net.ronm19.phantomism.vfx.AngelVfx.BLESSING, net.ronm19.phantomism.vfx.AngelVfx.TRAIL,
                net.ronm19.phantomism.vfx.AngelVfx.SMITE, net.ronm19.phantomism.vfx.AngelVfx.DIVE, net.ronm19.phantomism.vfx.AngelVfx.SHIELD, net.ronm19.phantomism.vfx.AngelVfx.INTERCEPT,
                net.ronm19.phantomism.vfx.AngelVfx.EYE, net.ronm19.phantomism.vfx.AngelVfx.CONCLUSION, net.ronm19.phantomism.vfx.AngelVfx.CLEANSE, net.ronm19.phantomism.vfx.AngelVfx.PRESENCE, net.ronm19.phantomism.vfx.AngelVfx.IMPACT)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.AngelVfx.EYE) ? Form.CELESTIAL_EYE : effect.equals(net.ronm19.phantomism.vfx.AngelVfx.SMITE) ? Form.CELESTIAL_SMITE : Form.CELESTIAL;
            register(effect, new Definition(form, texture("angel_eye"), texture("angel_feather"), texture("angel_sigil"), texture("angel_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/angel_phantom.png"), new VfxSequence(24, 192, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.SpectralVfx.STRIKE, net.ronm19.phantomism.vfx.SpectralVfx.DIVE,
                net.ronm19.phantomism.vfx.SpectralVfx.SLIP, net.ronm19.phantomism.vfx.SpectralVfx.MARK, net.ronm19.phantomism.vfx.SpectralVfx.PASSAGE,
                net.ronm19.phantomism.vfx.SpectralVfx.EMERGE, net.ronm19.phantomism.vfx.SpectralVfx.LANCE, net.ronm19.phantomism.vfx.SpectralVfx.IMPACT,
                net.ronm19.phantomism.vfx.SpectralVfx.GUARD, net.ronm19.phantomism.vfx.SpectralVfx.PLANE, net.ronm19.phantomism.vfx.SpectralVfx.FOLD)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.SpectralVfx.PLANE) ? Form.ETHEREAL_PLANE : effect.equals(net.ronm19.phantomism.vfx.SpectralVfx.LANCE) ? Form.SPECTRAL_LANCE
                    : effect.equals(net.ronm19.phantomism.vfx.SpectralVfx.DIVE) || effect.equals(net.ronm19.phantomism.vfx.SpectralVfx.SLIP) || effect.equals(net.ronm19.phantomism.vfx.SpectralVfx.PASSAGE) ? Form.SPECTRAL_AFTERIMAGE : Form.SPECTRAL;
            register(effect, new Definition(form, texture("spectral_glyph"), texture("spectral_wisp"), texture("spectral_flash"), texture("spectral_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/vfx/spectral_silhouette.png"), new VfxSequence(24, 192, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.SentinelVfx.REPULSE, net.ronm19.phantomism.vfx.SentinelVfx.CLEAVE,
                net.ronm19.phantomism.vfx.SentinelVfx.BREAK, net.ronm19.phantomism.vfx.SentinelVfx.PULSE, net.ronm19.phantomism.vfx.SentinelVfx.MARK,
                net.ronm19.phantomism.vfx.SentinelVfx.GUARD, net.ronm19.phantomism.vfx.SentinelVfx.BASTION, net.ronm19.phantomism.vfx.SentinelVfx.IMPACT)) {
            register(effect, new Definition(effect.equals(net.ronm19.phantomism.vfx.SentinelVfx.BASTION) ? Form.BASTION : Form.GUARDIAN,
                    texture("sentinel_crest"), texture("sentinel_panel"), texture("sentinel_crest"), texture("sentinel_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/sentinel_phantom.png"), new VfxSequence(28, 188, 24), 1));
        }
        for (Identifier effect : java.util.List.of(net.ronm19.phantomism.vfx.DemonVfx.AURA, net.ronm19.phantomism.vfx.DemonVfx.BITE,
                net.ronm19.phantomism.vfx.DemonVfx.BREATH, net.ronm19.phantomism.vfx.DemonVfx.DASH, net.ronm19.phantomism.vfx.DemonVfx.WAVE,
                net.ronm19.phantomism.vfx.DemonVfx.METEOR, net.ronm19.phantomism.vfx.DemonVfx.IMPACT, net.ronm19.phantomism.vfx.DemonVfx.DRAIN,
                net.ronm19.phantomism.vfx.DemonVfx.HOWL, net.ronm19.phantomism.vfx.DemonVfx.FORM, net.ronm19.phantomism.vfx.DemonVfx.APOCALYPSE, net.ronm19.phantomism.vfx.DemonVfx.FINALE)) {
            Form form = effect.equals(net.ronm19.phantomism.vfx.DemonVfx.APOCALYPSE) ? Form.APOCALYPSE : effect.equals(net.ronm19.phantomism.vfx.DemonVfx.BREATH) ? Form.INFERNAL_BREATH : effect.equals(net.ronm19.phantomism.vfx.DemonVfx.METEOR) ? Form.INFERNAL_METEOR : Form.INFERNAL;
            register(effect, new Definition(form, texture("demon_sigil"), texture("demon_flame"), texture("demon_core"), texture("demon_trail"),
                    Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/demon_phantom.png"), new VfxSequence(30, 240, 30), 1));
        }
        var shortSequence = new VfxSequence(4, 20, 8);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.BOLT, Form.SPATIAL_PROJECTILE, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.IMPACT, Form.SPATIAL_IMPACT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.TRANSFER, Form.TEAR, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.PORTAL, Form.PORTAL, new VfxSequence(10, 580, 10));
        spatial(net.ronm19.phantomism.vfx.RiftVfx.ANCHOR, Form.SPATIAL_ANCHOR, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.STEP, Form.SPATIAL_AFTERIMAGE, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.LOCK, Form.SPATIAL_LOCK, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.SENSE, Form.SPATIAL_SENSE, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.ULTIMATE, Form.SPATIAL_ULTIMATE, new VfxSequence(20, 200, 20));
        spatial(net.ronm19.phantomism.vfx.RiftVfx.COLLAPSE, Form.SPATIAL_IMPACT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.RESCUE_OUT, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.RESCUE_IN, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.THROW_OUT, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.THROW_IN, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.TRAVEL_OUT, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.TRAVEL_IN, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.STEP_OUT, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.STEP_IN, Form.SPATIAL_TRANSIT, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.DASH, Form.SPATIAL_AFTERIMAGE, shortSequence);
        spatial(net.ronm19.phantomism.vfx.RiftVfx.DASH_IMPACT, Form.SPATIAL_SLASH, shortSequence);
        Definition[] presets = new Definition[Form.values().length];
        for (Form form : Form.values()) { presets[form.ordinal()] = new Definition(form, texture("clock_face"), texture("energy_ring"), texture("clock_hand"), texture("energy_trail"), Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/entity/chrono_phantom.png"), form == Form.CINEMATIC ? new VfxSequence(20, 120, 16) : new VfxSequence(4, 28, 8), 1); }
        register(CinematicVfx.BOLT, presets[Form.PROJECTILE.ordinal()]); register(CinematicVfx.IMPACT, presets[Form.IMPACT.ordinal()]);
        register(CinematicVfx.ACCELERATE, presets[Form.SUPPORT.ordinal()]); register(CinematicVfx.SHIFT, presets[Form.AFTERIMAGE.ordinal()]);
        register(CinematicVfx.REWIND, presets[Form.REVERSE.ordinal()]); register(CinematicVfx.ANCHOR, presets[Form.SIGIL.ordinal()]);
        register(CinematicVfx.SLOW, presets[Form.AREA.ordinal()]); register(CinematicVfx.STOP, presets[Form.CINEMATIC.ordinal()]);
        register(CinematicVfx.FROZEN, presets[Form.STASIS.ordinal()]); register(CinematicVfx.DIVE, presets[Form.AFTERIMAGE.ordinal()]);
        register(CinematicVfx.AWARE, presets[Form.SIGIL.ordinal()]); register(CinematicVfx.DISCIPLINE, presets[Form.ORBIT.ordinal()]);
        register(CinematicVfx.BEAM, presets[Form.BEAM.ordinal()]); register(CinematicVfx.SLASH, presets[Form.SLASH.ordinal()]); register(CinematicVfx.ORBIT, presets[Form.ORBIT.ordinal()]);
    }
}
