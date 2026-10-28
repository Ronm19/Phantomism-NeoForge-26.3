package net.ronm19.phantomism.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.SporePhantom;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class CodexFieldNotes {
    private record AbilityNote(String key, String cooldownHint) {}
    private record FieldNote(List<AbilityNote> abilities, List<String> defenses, List<String> notes) {}
    private static final Map<String, FieldNote> NOTES = Map.ofEntries(
            Map.entry("new_years_phantom", new FieldNote(List.of(new AbilityNote("0", "spark"), new AbilityNote("1", "dash"), new AbilityNote("2", "shield"), new AbilityNote("3", "ward"), new AbilityNote("4", "fortune"), new AbilityNote("5", "countdown")), List.of("codex.field.new_years_phantom.defense.0"), List.of("codex.field.new_years_phantom.note.0"))),
            Map.entry("creator_phantom", new FieldNote(List.of(new AbilityNote("0", "presence"), new AbilityNote("1", "pulse"), new AbilityNote("2", "step"), new AbilityNote("3", "aegis"), new AbilityNote("4", "genesis"), new AbilityNote("5", "beam"), new AbilityNote("6", "instinct"), new AbilityNote("7", "evolution")), List.of("codex.field.creator_phantom.defense.0"), List.of("codex.field.creator_phantom.note.0"))),
            Map.entry("valentines_phantom", new FieldNote(List.of(new AbilityNote("0", "bond"), new AbilityNote("1", "bolt"), new AbilityNote("2", "dash"), new AbilityNote("3", "aura"), new AbilityNote("4", "charm"), new AbilityNote("5", "embrace"), new AbilityNote("6", "serenade")), List.of("codex.field.valentines_phantom.defense.0"), List.of("codex.field.valentines_phantom.note.0"))),
            Map.entry("st_patricks_phantom", new FieldNote(List.of(new AbilityNote("0", "favor"), new AbilityNote("1", "burst"), new AbilityNote("2", "dash"), new AbilityNote("3", "aura"), new AbilityNote("4", "trick"), new AbilityNote("5", "sky")), List.of("codex.field.st_patricks_phantom.defense.0"), List.of("codex.field.st_patricks_phantom.note.0"))),
            Map.entry("firework_phantom", new FieldNote(List.of(new AbilityNote("0", "charge"), new AbilityNote("1", "spirit"), new AbilityNote("2", "bolt"), new AbilityNote("3", "rush"), new AbilityNote("4", "starburst"), new AbilityNote("5", "guard"), new AbilityNote("6", "finale")), List.of("codex.field.firework_phantom.defense.0"), List.of("codex.field.firework_phantom.note.0"))),
            Map.entry("easter_phantom", new FieldNote(List.of(new AbilityNote("0", "agility"), new AbilityNote("1", "spirit"), new AbilityNote("2", "leap"), new AbilityNote("3", "egg"), new AbilityNote("4", "renewal"), new AbilityNote("5", "evade"), new AbilityNote("6", "awakening")), List.of("codex.field.easter_phantom.defense.0"), List.of("codex.field.easter_phantom.note.0"))),
            Map.entry("christmas_phantom", new FieldNote(List.of(new AbilityNote("0", "cheer"), new AbilityNote("1", "spirit"), new AbilityNote("2", "gift"), new AbilityNote("3", "snowball"), new AbilityNote("4", "dive"), new AbilityNote("5", "guard"), new AbilityNote("6", "miracle")), List.of("codex.field.christmas_phantom.defense.0"), List.of("codex.field.christmas_phantom.note.0"))),
            Map.entry("halloween_phantom", new FieldNote(List.of(new AbilityNote("0", "presence"), new AbilityNote("1", "spirit"), new AbilityNote("2", "gaze"), new AbilityNote("3", "dive"), new AbilityNote("4", "screech"), new AbilityNote("5", "fright"), new AbilityNote("6", "veil")), List.of("codex.field.halloween_phantom.defense.0"), List.of("codex.field.halloween_phantom.note.0"))),
            Map.entry("autumn_phantom", new FieldNote(List.of(new AbilityNote("0", "leaves"), new AbilityNote("1", "sense"), new AbilityNote("2", "bolt"), new AbilityNote("3", "rush"), new AbilityNote("4", "guard"), new AbilityNote("5", "gift"), new AbilityNote("6", "gale")), List.of("codex.field.autumn_phantom.defense.0"), List.of("codex.field.autumn_phantom.note.0"))),
            Map.entry("lunar_phantom", new FieldNote(List.of(new AbilityNote("0", "charge"), new AbilityNote("1", "night_hunter"), new AbilityNote("2", "pressure"), new AbilityNote("3", "recovery"), new AbilityNote("4", "aura"), new AbilityNote("5", "lance"), new AbilityNote("6", "fang"), new AbilityNote("7", "rush"), new AbilityNote("8", "slash"), new AbilityNote("9", "moonbreak"), new AbilityNote("10", "pulse"), new AbilityNote("11", "veil"), new AbilityNote("12", "ascendancy")), List.of("codex.field.lunar_phantom.defense.0"), List.of("codex.field.lunar_phantom.note.0"))),
            Map.entry("infernal_phantom", new FieldNote(List.of(new AbilityNote("0", "heat"), new AbilityNote("1", "adaptation"), new AbilityNote("2", "pressure"), new AbilityNote("3", "bolt"), new AbilityNote("4", "hellrush"), new AbilityNote("5", "field"), new AbilityNote("6", "overheat"), new AbilityNote("7", "ash"), new AbilityNote("8", "guard"), new AbilityNote("9", "ascendant")), List.of("codex.field.infernal_phantom.defense.0"), List.of("codex.field.infernal_phantom.note.0"))),
            Map.entry("illager_phantom", new FieldNote(List.of(new AbilityNote("0", "charm"), new AbilityNote("1", "barrage"), new AbilityNote("2", "wrath"), new AbilityNote("3", "dominion"), new AbilityNote("4", "conquest")), List.of("codex.field.illager_phantom.defense.0"), List.of("codex.field.illager_phantom.note.0"))),
            Map.entry("solar_phantom", new FieldNote(List.of(new AbilityNote("0", "empowerment"), new AbilityNote("1", "annihilation"), new AbilityNote("2", "sanctuary"), new AbilityNote("3", "blitz"), new AbilityNote("4", "supernova")), List.of("codex.field.solar_phantom.defense.0"), List.of("codex.field.solar_phantom.note.0"))),
            Map.entry("black_phantom", new FieldNote(List.of(new AbilityNote("0", "show_hunter_sense"), new AbilityNote("1", "do_hurt_target"), new AbilityNote("2", "activate_shield"), new AbilityNote("3", "activate_sky_knight")), List.of(), List.of())),
            Map.entry("fire_phantom", new FieldNote(List.of(new AbilityNote("0", "do_hurt_target"), new AbilityNote("1", "launch_fire_projectile"), new AbilityNote("2", "tick_fire_veil"), new AbilityNote("3", "tick_fire_shield"), new AbilityNote("4", "tick_firestorm")), List.of("codex.field.fire_phantom.defense.0"), List.of())),
            Map.entry("frost_phantom", new FieldNote(List.of(new AbilityNote("0", "do_hurt_target"), new AbilityNote("1", "launch_ice_projectile"), new AbilityNote("2", "tick_ice_shield"), new AbilityNote("3", "tick_ice_storm")), List.of("codex.field.frost_phantom.defense.0"), List.of())),
            Map.entry("earth_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_bomb"), new AbilityNote("1", "activate_shield"), new AbilityNote("2", "create_wall"), new AbilityNote("3", "perform_earthquake"), new AbilityNote("4", "tick_blessing")), List.of(), List.of())),
            Map.entry("water_phantom", new FieldNote(List.of(new AbilityNote("0", "tick_water_blessing"), new AbilityNote("1", "launch_projectile"), new AbilityNote("2", "tick_water_shield"), new AbilityNote("3", "tick_hydro_pump")), List.of("codex.field.water_phantom.defense.0"), List.of())),
            Map.entry("storm_phantom", new FieldNote(List.of(new AbilityNote("0", "absorb_natural_lightning"), new AbilityNote("1", "launch_jolt"), new AbilityNote("2", "tick_thunder_shield"), new AbilityNote("3", "tick_thunderstorm")), List.of(), List.of())),
            Map.entry("sand_phantom", new FieldNote(List.of(new AbilityNote("0", "update_desert_adaptation"), new AbilityNote("1", "launch_sand_bolt"), new AbilityNote("2", "do_hurt_target"), new AbilityNote("3", "tick_sandstorm")), List.of(), List.of())),
            Map.entry("gold_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_gold_dust"), new AbilityNote("1", "activate_golden_shield"), new AbilityNote("2", "tick_gold_eye"), new AbilityNote("3", "scan_for_cave")), List.of(), List.of())),
            Map.entry("cherry_phantom", new FieldNote(List.of(new AbilityNote("0", "support_family"), new AbilityNote("1", "launch_petals"), new AbilityNote("2", "hurt_server"), new AbilityNote("3", "pulse_sanctuary")), List.of(), List.of())),
            Map.entry("phantom_dragon", new FieldNote(List.of(new AbilityNote("0", "do_hurt_target"), new AbilityNote("1", "tick_breath"), new AbilityNote("2", "launch_fireball"), new AbilityNote("3", "activate_fury"), new AbilityNote("4", "defend")), List.of(), List.of())),
            Map.entry("violet_phantom", new FieldNote(List.of(new AbilityNote("0", "record_combo_hit"), new AbilityNote("1", "launch_lance"), new AbilityNote("2", "crosscut"), new AbilityNote("3", "rupture"), new AbilityNote("4", "defend"), new AbilityNote("5", "activate_overdrive")), List.of(), List.of())),
            Map.entry("toxic_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_acid"), new AbilityNote("1", "do_hurt_target"), new AbilityNote("2", "hurt_server"), new AbilityNote("3", "acid_pulse"), new AbilityNote("4", "start_rain")), List.of("codex.field.toxic_phantom.defense.0"), List.of())),
            Map.entry("sculk_phantom", new FieldNote(List.of(new AbilityNote("0", "vibration_sense"), new AbilityNote("1", "sonic_beam"), new AbilityNote("2", "tick_silence"), new AbilityNote("3", "hurt_server")), List.of("codex.field.sculk_phantom.defense.0"), List.of())),
            Map.entry("ravager_phantom", new FieldNote(List.of(new AbilityNote("0", "use_ram"), new AbilityNote("1", "use_roar"), new AbilityNote("2", "finish_slam"), new AbilityNote("3", "start_charge"), new AbilityNote("4", "activate_fury")), List.of(), List.of("codex.field.ravager_phantom.note.0"))),
            Map.entry("end_phantom", new FieldNote(List.of(new AbilityNote("0", "blink"), new AbilityNote("1", "rift_strike"), new AbilityNote("2", "end_shield"), new AbilityNote("3", "eye_of_ender"), new AbilityNote("4", "teleport_for_rescue")), List.of(), List.of("codex.field.end_phantom.note.0"))),
            Map.entry("zombie_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_spit"), new AbilityNote("1", "do_hurt_target"), new AbilityNote("2", "begin_dive"), new AbilityNote("3", "rotten_resilience"), new AbilityNote("4", "try_revive")), List.of(), List.of("codex.field.zombie_phantom.note.0"))),
            Map.entry("skeleton_phantom", new FieldNote(List.of(new AbilityNote("0", "fire_adaptive_volley"), new AbilityNote("1", "rain_of_arrows"), new AbilityNote("2", "eye_of_the_skeleton"), new AbilityNote("3", "bone_guard"), new AbilityNote("4", "fracture")), List.of(), List.of())),
            Map.entry("husk_phantom", new FieldNote(List.of(new AbilityNote("0", "pulse_drought"), new AbilityNote("1", "begin_dive"), new AbilityNote("2", "activate_relentless"), new AbilityNote("3", "activate_mummified_guard"), new AbilityNote("4", "activate_dead_land")), List.of(), List.of())),
            Map.entry("echo_phantom", new FieldNote(List.of(new AbilityNote("0", "scan_echo_sense"), new AbilityNote("1", "launch_pulse"), new AbilityNote("2", "begin_strike"), new AbilityNote("3", "activate_echo_guard"), new AbilityNote("4", "activate_perfect_resonance")), List.of(), List.of())),
            Map.entry("blood_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_blood_projectile"), new AbilityNote("1", "drain_blood"), new AbilityNote("2", "activate_relentless_hunt"), new AbilityNote("3", "crimson_wings"), new AbilityNote("4", "activate_blood_shield"), new AbilityNote("5", "activate_crimson_dominion")), List.of(), List.of())),
            Map.entry("vampire_phantom", new FieldNote(List.of(new AbilityNote("0", "update_night"), new AbilityNote("1", "do_hurt_target"), new AbilityNote("2", "launch_crimson_bolt"), new AbilityNote("3", "activate_crimson_mist"), new AbilityNote("4", "hypnotize"), new AbilityNote("5", "activate_vampire_rage"), new AbilityNote("6", "activate_blood_moon")), List.of(), List.of())),
            Map.entry("gravity_phantom", new FieldNote(List.of(new AbilityNote("0", "launch_well"), new AbilityNote("1", "gravity_crush"), new AbilityNote("2", "zero_lift"), new AbilityNote("3", "orbital_throw"), new AbilityNote("4", "mass_lock"), new AbilityNote("5", "gravity_field"), new AbilityNote("6", "event_horizon")), List.of(), List.of())),
            Map.entry("wither_phantom", new FieldNote(List.of(new AbilityNote("0", "wither_bite"), new AbilityNote("1", "fire_adaptive_volley"), new AbilityNote("2", "siege_barrage"), new AbilityNote("3", "activate_rush"), new AbilityNote("4", "activate_carapace"), new AbilityNote("5", "wither_storm")), List.of("codex.field.wither_phantom.defense.0"), List.of())),
            Map.entry("chrono_phantom", new FieldNote(List.of(new AbilityNote("0", "time_bolt"), new AbilityNote("1", "accelerate"), new AbilityNote("2", "shift_for"), new AbilityNote("3", "rewind"), new AbilityNote("4", "anchor"), new AbilityNote("5", "slow_field"), new AbilityNote("6", "time_stop"), new AbilityNote("7", "tick")), List.of(), List.of())),
            Map.entry("rift_phantom", new FieldNote(List.of(new AbilityNote("0", "rift_bolt"), new AbilityNote("1", "dash"), new AbilityNote("2", "rescue"), new AbilityNote("3", "set_anchor"), new AbilityNote("4", "throw_enemy"), new AbilityNote("5", "lock"), new AbilityNote("6", "create_corridor"), new AbilityNote("7", "fractured_space")), List.of(), List.of("codex.field.rift_phantom.note.0"))),
            Map.entry("sentinel_phantom", new FieldNote(List.of(new AbilityNote("0", "intercept"), new AbilityNote("1", "repulse"), new AbilityNote("2", "cleave"), new AbilityNote("3", "pulse"), new AbilityNote("4", "guard"), new AbilityNote("5", "bastion")), List.of(), List.of())),
            Map.entry("demon_phantom", new FieldNote(List.of(new AbilityNote("0", "drain"), new AbilityNote("1", "breath"), new AbilityNote("2", "dash"), new AbilityNote("3", "wave"), new AbilityNote("4", "launch_meteor"), new AbilityNote("5", "howl"), new AbilityNote("6", "demon_form"), new AbilityNote("7", "apocalypse")), List.of(), List.of())),
            Map.entry("spectral_phantom", new FieldNote(List.of(new AbilityNote("0", "strike"), new AbilityNote("1", "lance"), new AbilityNote("2", "dive"), new AbilityNote("3", "slip"), new AbilityNote("4", "mark"), new AbilityNote("5", "guard"), new AbilityNote("6", "plane")), List.of(), List.of())),
            Map.entry("angel_phantom", new FieldNote(List.of(new AbilityNote("0", "touch"), new AbilityNote("1", "blessing"), new AbilityNote("2", "heal_trail"), new AbilityNote("3", "smite"), new AbilityNote("4", "dive"), new AbilityNote("5", "shield"), new AbilityNote("6", "eye")), List.of(), List.of("codex.field.angel_phantom.note.0"))),
            Map.entry("omen_phantom", new FieldNote(List.of(new AbilityNote("0", "mark"), new AbilityNote("1", "standard"), new AbilityNote("2", "bolt"), new AbilityNote("3", "shield"), new AbilityNote("4", "dive"), new AbilityNote("5", "calamity")), List.of(), List.of())),
            Map.entry("ancient_phantom", new FieldNote(List.of(new AbilityNote("0", "mark"), new AbilityNote("1", "ruin_strike"), new AbilityNote("2", "stone"), new AbilityNote("3", "ruin_shield"), new AbilityNote("4", "sanctuary"), new AbilityNote("5", "dive"), new AbilityNote("6", "forgotten_land")), List.of(), List.of())),
            Map.entry("reaper_phantom", new FieldNote(List.of(new AbilityNote("0", "mark"), new AbilityNote("1", "soul_cut"), new AbilityNote("2", "mortal_sever"), new AbilityNote("3", "passage"), new AbilityNote("4", "shard"), new AbilityNote("5", "harvest"), new AbilityNote("6", "bargain"), new AbilityNote("7", "last_bell")), List.of(), List.of())),
            Map.entry("primordial_phantom", new FieldNote(List.of(new AbilityNote("0", "mitigation"), new AbilityNote("1", "primordial_strike"), new AbilityNote("2", "starfall"), new AbilityNote("3", "creation_wing"), new AbilityNote("4", "reality_shift"), new AbilityNote("5", "restoration"), new AbilityNote("6", "cosmic_nova"), new AbilityNote("7", "primal_domain")), List.of(), List.of())),
            Map.entry("void_phantom", new FieldNote(List.of(new AbilityNote("0", "tick"), new AbilityNote("1", "bolt"), new AbilityNote("2", "grasp"), new AbilityNote("3", "tick"), new AbilityNote("4", "anti_caster_pulse"), new AbilityNote("5", "void_step"), new AbilityNote("6", "shield"), new AbilityNote("7", "null_horizon")), List.of(), List.of())),
            Map.entry("dark_phantom", new FieldNote(List.of(new AbilityNote("0", "successful_attack"), new AbilityNote("1", "bolt"), new AbilityNote("2", "rend"), new AbilityNote("3", "rush"), new AbilityNote("4", "blackout"), new AbilityNote("5", "bubble"), new AbilityNote("6", "dark_hour")), List.of("codex.field.dark_phantom.defense.0"), List.of())),
            Map.entry("phantom_king", new FieldNote(List.of(new AbilityNote("0", "tick"), new AbilityNote("1", "momentum"), new AbilityNote("2", "intercept"), new AbilityNote("3", "ram"), new AbilityNote("4", "judgment"), new AbilityNote("5", "roar"), new AbilityNote("6", "command"), new AbilityNote("7", "dominion")), List.of(), List.of("codex.field.phantom_king.note.0"))),
            Map.entry("golem_phantom", new FieldNote(List.of(new AbilityNote("0", "tick"), new AbilityNote("1", "incoming_threat"), new AbilityNote("2", "set_task"), new AbilityNote("3", "ram"), new AbilityNote("4", "slam"), new AbilityNote("5", "punch"), new AbilityNote("6", "guard"), new AbilityNote("7", "titan")), List.of("codex.field.golem_phantom.defense.0"), List.of("codex.field.golem_phantom.note.0"))),
            Map.entry("mirage_phantom", new FieldNote(List.of(new AbilityNote("0", "copies"), new AbilityNote("1", "phantom_double"), new AbilityNote("2", "bolt"), new AbilityNote("3", "illusory_strike"), new AbilityNote("4", "rescue"), new AbilityNote("5", "field"), new AbilityNote("6", "mirror_guard"), new AbilityNote("7", "hall")), List.of(), List.of())),
            Map.entry("eclipse_phantom", new FieldNote(List.of(new AbilityNote("0", "decide"), new AbilityNote("1", "solar_pulse"), new AbilityNote("2", "lunar_fang"), new AbilityNote("3", "bolt"), new AbilityNote("4", "hybrid_rend"), new AbilityNote("5", "hybrid_guard"), new AbilityNote("6", "total_eclipse")), List.of(), List.of())),
            Map.entry("shadow_phantom", new FieldNote(List.of(new AbilityNote("0", "conceals_from"), new AbilityNote("1", "isolated"), new AbilityNote("2", "bolt"), new AbilityNote("3", "strike"), new AbilityNote("4", "vanish"), new AbilityNote("5", "recon"), new AbilityNote("6", "guard"), new AbilityNote("7", "nightfall")), List.of(), List.of())),
            Map.entry("plague_phantom", new FieldNote(List.of(new AbilityNote("0", "infection_pulse"), new AbilityNote("1", "cluster"), new AbilityNote("2", "recipient"), new AbilityNote("3", "spit"), new AbilityNote("4", "dive"), new AbilityNote("5", "cloud"), new AbilityNote("6", "husk"), new AbilityNote("7", "pandemic")), List.of(), List.of())),
            Map.entry("crystal_phantom", new FieldNote(List.of(new AbilityNote("0", "tick"), new AbilityNote("1", "can_reflect"), new AbilityNote("2", "stored_impact"), new AbilityNote("3", "shard"), new AbilityNote("4", "burst"), new AbilityNote("5", "counter"), new AbilityNote("6", "barrier"), new AbilityNote("7", "domain")), List.of(), List.of())),
            Map.entry("obsidian_phantom", new FieldNote(List.of(new AbilityNote("0", "tick"), new AbilityNote("1", "shard"), new AbilityNote("2", "ram"), new AbilityNote("3", "slam"), new AbilityNote("4", "enter_bastion"), new AbilityNote("5", "shelter"), new AbilityNote("6", "shatter_armor"), new AbilityNote("7", "citadel")), List.of(), List.of())),
            Map.entry("gale_phantom", new FieldNote(List.of(new AbilityNote("0", "travel"), new AbilityNote("1", "rescue"), new AbilityNote("2", "bolt"), new AbilityNote("3", "slipstream"), new AbilityNote("4", "updraft"), new AbilityNote("5", "crosswind"), new AbilityNote("6", "vortex"), new AbilityNote("7", "dominion")), List.of(), List.of())),
            Map.entry("spore_phantom", new FieldNote(List.of(new AbilityNote("0", "breath"), new AbilityNote("1", "bloom"), new AbilityNote("2", "dive"), new AbilityNote("3", "storm")), List.of(), List.of("codex.field.spore_phantom.note.0")))
    );
    private CodexFieldNotes() {}

    public static CodexCatalog.Guide guide(PhantomDiscovery discovery, AbstractPhantom phantom) {
        String species = discovery.modelName(), prefix = "codex.field." + species;
        FieldNote note = NOTES.get(species);
        if (note == null) { return null; }
        var combat = new CodexCatalog.Combat(phantom.getMaxHealth(), phantom.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                phantom.getAttributeBaseValue(Attributes.ARMOR), prefix + ".role", note.defenses());
        List<CodexCatalog.Ability> abilities = new ArrayList<>();
        for (AbilityNote ability : note.abilities()) {
            int cooldown = 0;
            if (phantom instanceof CooldownDiscipline.Participant participant) {
                cooldown = participant.temporalCooldowns().stream().filter(slot -> slot.id().getPath().endsWith("_" + ability.cooldownHint()))
                        .mapToInt(CooldownDiscipline.Slot::duration).findFirst().orElse(0);
            }
            if (discovery == PhantomDiscovery.SPORE && ability.cooldownHint().equals("storm")) { cooldown = SporePhantom.STORM_COOLDOWN; }
            if (discovery == PhantomDiscovery.GALE && ability.cooldownHint().equals("dominion")) { cooldown = net.ronm19.phantomism.entity.GalePhantom.DOMAIN_COOLDOWN; }
            if (discovery == PhantomDiscovery.SOLAR && ability.cooldownHint().equals("supernova")) { cooldown = net.ronm19.phantomism.entity.SolarPhantom.ULTIMATE_COOLDOWN; }
            if (discovery == PhantomDiscovery.ILLAGER && ability.cooldownHint().equals("conquest")) { cooldown = net.ronm19.phantomism.entity.IllagerPhantom.CONQUEST_COOLDOWN; }
            if (discovery == PhantomDiscovery.INFERNAL && ability.cooldownHint().equals("ascendant")) { cooldown = net.ronm19.phantomism.entity.InfernalPhantom.ASCENDANT_COOLDOWN; }
            if (discovery == PhantomDiscovery.AUTUMN && ability.cooldownHint().equals("gale")) { cooldown = 1500; }
            if (discovery == PhantomDiscovery.HALLOWEEN && ability.cooldownHint().equals("fright")) { cooldown = net.ronm19.phantomism.Config.halloweenUltimateCooldown(); }
            if (discovery == PhantomDiscovery.LUNAR && ability.cooldownHint().equals("ascendancy")) { cooldown = net.ronm19.phantomism.entity.LunarPhantom.ULTIMATE_COOLDOWN; }
            abilities.add(new CodexCatalog.Ability(prefix + ".ability." + ability.key() + ".name", prefix + ".ability." + ability.key() + ".description", cooldown));
        }
        TamingProfile profile = phantom.preferredTaming();
        boolean staffOnly = profile.item() == PhantomismContent.PHANTOM_STAFF.get();
        List<Item> accepted = new ArrayList<>(List.of(profile.item()));
        if (phantom instanceof SporePhantom spore && spore.isTamingMushroom(new ItemStack(Items.BROWN_MUSHROOM))) { accepted.add(Items.BROWN_MUSHROOM); }
        if (phantom instanceof net.ronm19.phantomism.entity.SolarPhantom) { accepted.add(Items.GOLDEN_APPLE); }
        if (phantom instanceof net.ronm19.phantomism.entity.AbstractHolidayPhantom holiday) { accepted.addAll(holiday.festival().alternativeItems()); }
        List<String> items = accepted.stream().map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).distinct().toList();
        String special = switch (discovery) { case DARK, SPORE, PRIMORDIAL, ANCIENT, CREATOR -> prefix + ".taming"; default -> ""; };
        var taming = new CodexCatalog.Taming(items, profile.chance(), profile.guaranteedAttempt(), staffOnly,
                phantom.preferredLure().map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).orElse(""), special);
        if (phantom instanceof net.ronm19.phantomism.entity.SolarPhantom solar) {
            var foods = accepted.stream().map(item -> { var option = solar.tamingProfile(new ItemStack(item)); return new CodexCatalog.TamingFood(BuiltInRegistries.ITEM.getKey(item).toString(), option.chance(), option.guaranteedAttempt()); }).toList();
            taming = new CodexCatalog.Taming(items, profile.chance(), profile.guaranteedAttempt(), false, "", "", foods);
        }
        if (phantom instanceof net.ronm19.phantomism.entity.AbstractHolidayPhantom holiday) {
            var foods = holiday.festival().alternativeItems().stream().map(item -> {
                float chance = holiday.festival().tamingChance(item);
                return new CodexCatalog.TamingFood(BuiltInRegistries.ITEM.getKey(item).toString(), chance, chance >= 1 ? 1 : 0);
            }).toList();
            taming = new CodexCatalog.Taming(items, profile.chance(), profile.guaranteedAttempt(), false, "", "", foods);
        }
        boolean shot = switch (discovery) { case BLACK, END, HUSK, RAVAGER, SENTINEL, KING, DEMON -> false; default -> true; };
        var riding = new CodexCatalog.Riding(true, prefix + ".riding", discovery != PhantomDiscovery.BLACK,
                shot ? phantom.codexMountedCooldown() : 0);
        return new CodexCatalog.Guide(prefix + ".overview", combat, List.copyOf(abilities), taming, riding, note.notes());
    }
}
