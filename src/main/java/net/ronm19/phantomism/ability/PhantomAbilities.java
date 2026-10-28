package net.ronm19.phantomism.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.codex.CodexFieldNotes;
import net.ronm19.phantomism.entity.*;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;

public final class PhantomAbilities {
    public enum Target { NONE, ENEMY, POSITION }
    public enum Status { READY, COOLING_DOWN, UNAVAILABLE }
    @FunctionalInterface public interface Activation<T extends AbstractPhantom> {
        boolean activate(T phantom, ServerLevel level, LivingEntity target, Vec3 position);
    }
    public record Ability(Identifier id, String key, String nameKey, String descriptionKey, Target target,
                          boolean mounted, boolean shot, boolean automatic, Activation<AbstractPhantom> activation) {
        public int remaining(AbstractPhantom phantom) { return automatic ? 0 : Math.max(0, phantom.staffAbilityCooldown(key)); }
    }
    public record View(String id, String nameKey, String descriptionKey, Target target, Status status,
                       int remaining, boolean automatic, String reason) {}
    private static final Map<Class<?>, List<Ability>> REGISTERED = new HashMap<>();
    static {
        register(BloodPhantom.class, "blood_phantom", "launch_blood_projectile", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.launchBloodProjectile(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(BloodPhantom.class, "blood_phantom", "drain_blood", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.drainBlood(level, target));
        register(BloodPhantom.class, "blood_phantom", "activate_relentless_hunt", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateRelentlessHunt(level));
        register(BloodPhantom.class, "blood_phantom", "crimson_wings", "3", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.crimsonWings(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(BloodPhantom.class, "blood_phantom", "activate_blood_shield", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateBloodShield(level));
        register(BloodPhantom.class, "blood_phantom", "activate_crimson_dominion", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateCrimsonDominion(level));
        register(EchoPhantom.class, "echo_phantom", "launch_pulse", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.launchPulse(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(EchoPhantom.class, "echo_phantom", "begin_strike", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.beginStrike(level, target));
        register(EchoPhantom.class, "echo_phantom", "activate_echo_guard", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateEchoGuard(level));
        register(EchoPhantom.class, "echo_phantom", "activate_perfect_resonance", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activatePerfectResonance(level));
        register(HuskPhantom.class, "husk_phantom", "begin_dive", "1", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.beginDive(level, target));
        register(HuskPhantom.class, "husk_phantom", "activate_relentless", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateRelentless(level));
        register(HuskPhantom.class, "husk_phantom", "activate_mummified_guard", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateMummifiedGuard(level));
        register(HuskPhantom.class, "husk_phantom", "activate_dead_land", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.activateDeadLand(level, target));
        register(VampirePhantom.class, "vampire_phantom", "launch_crimson_bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.launchCrimsonBolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(VampirePhantom.class, "vampire_phantom", "activate_crimson_mist", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateCrimsonMist(level));
        register(VampirePhantom.class, "vampire_phantom", "hypnotize", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.hypnotize(level));
        register(VampirePhantom.class, "vampire_phantom", "activate_vampire_rage", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateVampireRage(level));
        register(VampirePhantom.class, "vampire_phantom", "activate_blood_moon", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateBloodMoon(level));
        register(SkeletonPhantom.class, "skeleton_phantom", "rain_of_arrows", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.rainOfArrows(level));
        register(SkeletonPhantom.class, "skeleton_phantom", "eye_of_the_skeleton", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.eyeOfTheSkeleton(level));
        register(SkeletonPhantom.class, "skeleton_phantom", "bone_guard", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.boneGuard(level));
        register(RavagerPhantom.class, "ravager_phantom", "use_ram", "0", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.useRam(level, target));
        register(RavagerPhantom.class, "ravager_phantom", "use_roar", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.useRoar(level));
        register(RavagerPhantom.class, "ravager_phantom", "start_slam", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.startSlam(level, target));
        register(EndPhantom.class, "end_phantom", "blink", "0", Target.POSITION, false, false, (phantom, level, target, position) -> phantom.blink(level, position));
        register(EndPhantom.class, "end_phantom", "rift_strike", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.riftStrike(level, target));
        register(EndPhantom.class, "end_phantom", "end_shield", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.endShield(level));
        register(EndPhantom.class, "end_phantom", "eye_of_ender", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.eyeOfEnder(level));
        register(GravityPhantom.class, "gravity_phantom", "launch_well", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.launchWell(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(GravityPhantom.class, "gravity_phantom", "gravity_crush", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.gravityCrush(level, target));
        register(GravityPhantom.class, "gravity_phantom", "zero_lift", "2", Target.ENEMY, true, false, (phantom, level, target, position) -> target instanceof net.minecraft.world.entity.Mob mob && phantom.zeroLift(level, mob));
        register(GravityPhantom.class, "gravity_phantom", "orbital_throw", "3", Target.ENEMY, true, false, (phantom, level, target, position) -> target instanceof net.minecraft.world.entity.Mob mob && phantom.orbitalThrow(level, mob));
        register(GravityPhantom.class, "gravity_phantom", "mass_lock", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> target instanceof net.minecraft.world.entity.Mob mob && phantom.massLock(level, mob));
        register(GravityPhantom.class, "gravity_phantom", "gravity_field", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.gravityField(level));
        register(GravityPhantom.class, "gravity_phantom", "event_horizon", "6", Target.POSITION, true, false, (phantom, level, target, position) -> phantom.eventHorizon(level, position));
        register(WitherPhantom.class, "wither_phantom", "siege_barrage", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.siegeBarrage(level));
        register(WitherPhantom.class, "wither_phantom", "activate_rush", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.activateRush(level));
        register(WitherPhantom.class, "wither_phantom", "wither_storm", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.witherStorm(level));
        register(ZombiePhantom.class, "zombie_phantom", "launch_spit", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.launchSpit(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(ZombiePhantom.class, "zombie_phantom", "begin_dive", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.beginDive(level, target));
        register(ZombiePhantom.class, "zombie_phantom", "rotten_resilience", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.rottenResilience(level));
        register(ChronoPhantom.class, "chrono_phantom", "time_bolt", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.timeBolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(ChronoPhantom.class, "chrono_phantom", "accelerate", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.accelerate(level));
        register(ChronoPhantom.class, "chrono_phantom", "slow_field", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.slowField(level));
        register(ChronoPhantom.class, "chrono_phantom", "time_stop", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.timeStop(level));
        register(RiftPhantom.class, "rift_phantom", "rift_bolt", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.riftBolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(RiftPhantom.class, "rift_phantom", "dash", "1", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dash(level, target));
        register(RiftPhantom.class, "rift_phantom", "lock", "5", Target.ENEMY, true, false, (phantom, level, target, position) -> target instanceof net.minecraft.world.entity.Mob mob && phantom.lock(level, mob));
        register(RiftPhantom.class, "rift_phantom", "fractured_space", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.fracturedSpace(level));
        register(SentinelPhantom.class, "sentinel_phantom", "repulse", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.repulse(level, target));
        register(SentinelPhantom.class, "sentinel_phantom", "cleave", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.cleave(level));
        register(SentinelPhantom.class, "sentinel_phantom", "pulse", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.pulse(level));
        register(SentinelPhantom.class, "sentinel_phantom", "guard", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.guard(level));
        register(SentinelPhantom.class, "sentinel_phantom", "bastion", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.bastion(level));
        register(DemonPhantom.class, "demon_phantom", "drain", "0", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.drain(level, target));
        register(DemonPhantom.class, "demon_phantom", "breath", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.breath(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(DemonPhantom.class, "demon_phantom", "dash", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dash(level, target));
        register(DemonPhantom.class, "demon_phantom", "wave", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.wave(level));
        register(DemonPhantom.class, "demon_phantom", "flame_meteor", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.flameMeteor(level));
        register(DemonPhantom.class, "demon_phantom", "howl", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.howl(level));
        register(DemonPhantom.class, "demon_phantom", "demon_form", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.demonForm(level));
        register(DemonPhantom.class, "demon_phantom", "apocalypse", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.apocalypse(level));
        register(SpectralPhantom.class, "spectral_phantom", "strike", "0", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.strike(level, target));
        register(SpectralPhantom.class, "spectral_phantom", "lance", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.lance(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(SpectralPhantom.class, "spectral_phantom", "dive", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(SpectralPhantom.class, "spectral_phantom", "slip", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.slip(level, target));
        register(SpectralPhantom.class, "spectral_phantom", "mark", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.mark(level, target));
        register(SpectralPhantom.class, "spectral_phantom", "guard", "5", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.guard(level, target));
        register(SpectralPhantom.class, "spectral_phantom", "plane", "6", Target.NONE, false, false, (phantom, level, target, position) -> phantom.plane(level));
        register(AngelPhantom.class, "angel_phantom", "heal_trail", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.healTrail(level));
        register(AngelPhantom.class, "angel_phantom", "smite", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.smite(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(AngelPhantom.class, "angel_phantom", "dive", "4", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(AngelPhantom.class, "angel_phantom", "eye", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.eye(level));
        register(OmenPhantom.class, "omen_phantom", "mark", "0", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.mark(level, target));
        register(OmenPhantom.class, "omen_phantom", "standard", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.standard(level));
        register(OmenPhantom.class, "omen_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(OmenPhantom.class, "omen_phantom", "shield", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.shield(level));
        register(OmenPhantom.class, "omen_phantom", "dive", "4", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(OmenPhantom.class, "omen_phantom", "calamity", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.calamity(level));
        register(AncientPhantom.class, "ancient_phantom", "ruin_strike", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.ruinStrike(level, target));
        register(AncientPhantom.class, "ancient_phantom", "stone", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.stone(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(AncientPhantom.class, "ancient_phantom", "ruin_shield", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.ruinShield(level));
        register(AncientPhantom.class, "ancient_phantom", "sanctuary", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.sanctuary(level));
        register(AncientPhantom.class, "ancient_phantom", "dive", "5", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(AncientPhantom.class, "ancient_phantom", "forgotten_land", "6", Target.NONE, false, false, (phantom, level, target, position) -> phantom.forgottenLand(level));
        register(ReaperPhantom.class, "reaper_phantom", "mark", "0", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.mark(level, target));
        register(ReaperPhantom.class, "reaper_phantom", "soul_cut", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.soulCut(level, target));
        register(ReaperPhantom.class, "reaper_phantom", "mortal_sever", "2", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.mortalSever(level, target));
        register(ReaperPhantom.class, "reaper_phantom", "passage", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.passage(level, target));
        register(ReaperPhantom.class, "reaper_phantom", "shard", "4", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.shard(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(ReaperPhantom.class, "reaper_phantom", "last_bell", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.lastBell(level));
        register(PrimordialPhantom.class, "primordial_phantom", "primordial_strike", "1", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.primordialStrike(level, target));
        register(PrimordialPhantom.class, "primordial_phantom", "starfall", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.starfall(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(PrimordialPhantom.class, "primordial_phantom", "creation_wing", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.creationWing(level));
        register(PrimordialPhantom.class, "primordial_phantom", "restoration", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.restoration(level));
        register(PrimordialPhantom.class, "primordial_phantom", "cosmic_nova", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.cosmicNova(level));
        register(PrimordialPhantom.class, "primordial_phantom", "primal_domain", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.primalDomain(level));
        register(VoidPhantom.class, "void_phantom", "bolt", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(VoidPhantom.class, "void_phantom", "grasp", "2", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.grasp(level, target));
        register(VoidPhantom.class, "void_phantom", "anti_caster_pulse", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.antiCasterPulse(level));
        register(VoidPhantom.class, "void_phantom", "void_step", "5", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.voidStep(level, target));
        register(VoidPhantom.class, "void_phantom", "null_horizon", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.nullHorizon(level));
        register(DarkPhantom.class, "dark_phantom", "bolt", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition())));
        register(DarkPhantom.class, "dark_phantom", "rend", "2", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.rend(level, target));
        register(DarkPhantom.class, "dark_phantom", "rush", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.rush(level, target));
        register(DarkPhantom.class, "dark_phantom", "blackout", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.blackout(level));
        register(DarkPhantom.class, "dark_phantom", "dark_hour", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.darkHour(level));
        register(PhantomKing.class, "phantom_king", "ram", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.ram(level, target));
        register(PhantomKing.class, "phantom_king", "judgment", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.judgment(level, target));
        register(PhantomKing.class, "phantom_king", "roar", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.roar(level));
        register(PhantomKing.class, "phantom_king", "command", "6", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.command(level, target));
        register(PhantomKing.class, "phantom_king", "dominion", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.dominion(level));
        register(GolemPhantom.class, "golem_phantom", "ram", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.ram(level, target));
        register(GolemPhantom.class, "golem_phantom", "slam", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.slam(level));
        register(GolemPhantom.class, "golem_phantom", "punch", "5", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.punch(level, target, false));
        register(GolemPhantom.class, "golem_phantom", "guard", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.guard(level));
        register(GolemPhantom.class, "golem_phantom", "titan", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.titan(level));
        register(MiragePhantom.class, "mirage_phantom", "copies", "0", Target.NONE, true, false, (phantom, level, target, position) -> phantom.copies(level, false));
        register(MiragePhantom.class, "mirage_phantom", "phantom_double", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.phantomDouble(level, phantom));
        register(MiragePhantom.class, "mirage_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(MiragePhantom.class, "mirage_phantom", "illusory_strike", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.illusoryStrike(level, target));
        register(MiragePhantom.class, "mirage_phantom", "field", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.field(level));
        register(MiragePhantom.class, "mirage_phantom", "hall", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.hall(level));
        register(EclipsePhantom.class, "eclipse_phantom", "solar_pulse", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.solarPulse(level));
        register(EclipsePhantom.class, "eclipse_phantom", "lunar_fang", "2", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.lunarFang(level, target));
        register(EclipsePhantom.class, "eclipse_phantom", "bolt", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(EclipsePhantom.class, "eclipse_phantom", "hybrid_rend", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.hybridRend(level, target));
        register(EclipsePhantom.class, "eclipse_phantom", "total_eclipse", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.totalEclipse(level));
        register(ShadowPhantom.class, "shadow_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(ShadowPhantom.class, "shadow_phantom", "strike", "3", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.strike(level, target));
        register(ShadowPhantom.class, "shadow_phantom", "vanish", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.vanish(level));
        register(ShadowPhantom.class, "shadow_phantom", "recon", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.recon(level));
        register(ShadowPhantom.class, "shadow_phantom", "nightfall", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.nightfall(level));
        register(PlaguePhantom.class, "plague_phantom", "spit", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.spit(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(PlaguePhantom.class, "plague_phantom", "dive", "4", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(PlaguePhantom.class, "plague_phantom", "cloud", "5", Target.POSITION, true, false, (phantom, level, target, position) -> phantom.cloud(level, position));
        register(PlaguePhantom.class, "plague_phantom", "pandemic", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.pandemic(level));
        register(CrystalPhantom.class, "crystal_phantom", "shard", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.shard(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(CrystalPhantom.class, "crystal_phantom", "burst", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.burst(level, target));
        register(CrystalPhantom.class, "crystal_phantom", "counter", "5", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.counter(level, target));
        register(CrystalPhantom.class, "crystal_phantom", "barrier", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.barrier(level));
        register(CrystalPhantom.class, "crystal_phantom", "domain", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.domain(level));
        register(ObsidianPhantom.class, "obsidian_phantom", "shard", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.shard(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(ObsidianPhantom.class, "obsidian_phantom", "ram", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.ram(level, target));
        register(ObsidianPhantom.class, "obsidian_phantom", "slam", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.slam(level));
        register(ObsidianPhantom.class, "obsidian_phantom", "shelter", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.shelter(level));
        register(ObsidianPhantom.class, "obsidian_phantom", "shatter_armor", "6", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.shatterArmor(level, target));
        register(ObsidianPhantom.class, "obsidian_phantom", "citadel", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.citadel(level));
        register(GalePhantom.class, "gale_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(GalePhantom.class, "gale_phantom", "slipstream", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.slipstream(level));
        register(GalePhantom.class, "gale_phantom", "updraft", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.updraft(level));
        register(GalePhantom.class, "gale_phantom", "crosswind", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.crosswind(level));
        register(GalePhantom.class, "gale_phantom", "vortex", "6", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.vortex(level, target));
        register(GalePhantom.class, "gale_phantom", "dominion", "7", Target.NONE, false, false, (phantom, level, target, position) -> phantom.dominion(level));
        register(SporePhantom.class, "spore_phantom", "breath", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.breath(level, target.getEyePosition().subtract(phantom.getEyePosition()), false));
        register(SporePhantom.class, "spore_phantom", "bloom", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.bloom(level));
        register(SporePhantom.class, "spore_phantom", "dive", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.dive(level, target, false));
        register(SporePhantom.class, "spore_phantom", "storm", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.storm(level));
        register(LunarPhantom.class, "lunar_phantom", "lance", "5", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.lance(level, target));
        register(LunarPhantom.class, "lunar_phantom", "fang", "6", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.fang(level, target));
        register(LunarPhantom.class, "lunar_phantom", "rush", "7", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.rush(level, target));
        register(LunarPhantom.class, "lunar_phantom", "slash", "8", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.slash(level, target));
        register(LunarPhantom.class, "lunar_phantom", "moonbreak", "9", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.moonbreak(level, target));
        register(LunarPhantom.class, "lunar_phantom", "pulse", "10", Target.NONE, true, false, (phantom, level, target, position) -> phantom.pulse(level));
        register(LunarPhantom.class, "lunar_phantom", "ascendancy", "12", Target.NONE, true, false, (phantom, level, target, position) -> phantom.ascendancy(level));
        register(InfernalPhantom.class, "infernal_phantom", "bolt", "3", Target.ENEMY, false, true, (phantom, level, target, position) -> phantom.bolt(level, target));
        register(InfernalPhantom.class, "infernal_phantom", "hellrush", "4", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.hellrush(level, target));
        register(InfernalPhantom.class, "infernal_phantom", "field", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.field(level));
        register(InfernalPhantom.class, "infernal_phantom", "overheat", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.overheat(level));
        register(InfernalPhantom.class, "infernal_phantom", "ash", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.ash(level));
        register(InfernalPhantom.class, "infernal_phantom", "ascendant", "9", Target.NONE, true, false, (phantom, level, target, position) -> phantom.hellAscendant(level));
        register(IllagerPhantom.class, "illager_phantom", "barrage", "1", Target.ENEMY, false, true, (phantom, level, target, position) -> phantom.barrage(level, target));
        register(IllagerPhantom.class, "illager_phantom", "wrath", "2", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.wrath(level, target));
        register(IllagerPhantom.class, "illager_phantom", "dominion", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.dominion(level));
        register(IllagerPhantom.class, "illager_phantom", "conquest", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.conquest(level));
        register(SolarPhantom.class, "solar_phantom", "annihilation", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.annihilation(level, target, false));
        register(SolarPhantom.class, "solar_phantom", "charged", "1", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.annihilation(level, target, true));
        register(SolarPhantom.class, "solar_phantom", "sanctuary", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.sanctuary(level, false));
        register(SolarPhantom.class, "solar_phantom", "emergency", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.sanctuary(level, true));
        register(SolarPhantom.class, "solar_phantom", "blitz", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.blitz(level, target, false, false));
        register(SolarPhantom.class, "solar_phantom", "comet", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.blitz(level, target, true, false));
        register(SolarPhantom.class, "solar_phantom", "supernova", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.supernova(level, false));
        register(SolarPhantom.class, "solar_phantom", "salvation", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.supernova(level, true));
    }
    static {
        register(AutumnPhantom.class, "autumn_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target));
        register(HalloweenPhantom.class, "halloween_phantom", "gaze", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.gaze(level, target));
        register(net.ronm19.phantomism.entity.NewYearsPhantom.class, "new_years_phantom", "spark", "0", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.spark(level, target));
        register(net.ronm19.phantomism.entity.NewYearsPhantom.class, "new_years_phantom", "dash", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.dash(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.NewYearsPhantom.class, "new_years_phantom", "shield", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.shield(level));
        register(net.ronm19.phantomism.entity.NewYearsPhantom.class, "new_years_phantom", "ward", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.ward(level));
        register(net.ronm19.phantomism.entity.NewYearsPhantom.class, "new_years_phantom", "countdown", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.countdown(level));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "pulse", "1", Target.NONE, true, false, (phantom, level, target, position) -> phantom.pulse(level));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "step", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.step(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "aegis", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.aegis(level));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "genesis", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.genesis(level, target));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "beam", "5", Target.NONE, true, true, (phantom, level, target, position) -> phantom.adaptiveBeam(level));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "instinct", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.instinct(level));
        register(net.ronm19.phantomism.entity.CreatorPhantom.class, "creator_phantom", "evolution", "7", Target.NONE, true, false, (phantom, level, target, position) -> phantom.evolution(level));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "bolt", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "dash", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.dash(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "aura", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.aura(level));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "charm", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.charm(level));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "embrace", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.embrace(level));
        register(net.ronm19.phantomism.entity.ValentinesPhantom.class, "valentines_phantom", "serenade", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.serenade(level));
        register(net.ronm19.phantomism.entity.StPatricksPhantom.class, "st_patricks_phantom", "burst", "1", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.burst(level, target));
        register(net.ronm19.phantomism.entity.StPatricksPhantom.class, "st_patricks_phantom", "dash", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.dash(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.StPatricksPhantom.class, "st_patricks_phantom", "aura", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.aura(level));
        register(net.ronm19.phantomism.entity.StPatricksPhantom.class, "st_patricks_phantom", "trick", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.trick(level));
        register(net.ronm19.phantomism.entity.StPatricksPhantom.class, "st_patricks_phantom", "sky", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.sky(level));
        register(net.ronm19.phantomism.entity.FireworkPhantom.class, "firework_phantom", "bolt", "2", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.bolt(level, target));
        register(net.ronm19.phantomism.entity.FireworkPhantom.class, "firework_phantom", "rush", "3", Target.NONE, true, false, (phantom, level, target, position) -> phantom.rush(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.FireworkPhantom.class, "firework_phantom", "starburst", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.starburst(level));
        register(net.ronm19.phantomism.entity.FireworkPhantom.class, "firework_phantom", "guard", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.guard(level));
        register(net.ronm19.phantomism.entity.FireworkPhantom.class, "firework_phantom", "finale", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.finale(level));
        register(net.ronm19.phantomism.entity.EasterPhantom.class, "easter_phantom", "leap", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.leap(level, phantom.validTarget(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.EasterPhantom.class, "easter_phantom", "egg", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.egg(level, target));
        register(net.ronm19.phantomism.entity.EasterPhantom.class, "easter_phantom", "renewal", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.renewal(level));
        register(net.ronm19.phantomism.entity.EasterPhantom.class, "easter_phantom", "evade", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.evade(level, phantom.safeEnemy(phantom.getTarget()) ? phantom.getTarget() : null));
        register(net.ronm19.phantomism.entity.EasterPhantom.class, "easter_phantom", "awakening", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.awakening(level));
        register(ChristmasPhantom.class, "christmas_phantom", "gift", "2", Target.NONE, true, false, (phantom, level, target, position) -> phantom.gift(level));
        register(ChristmasPhantom.class, "christmas_phantom", "snowball", "3", Target.ENEMY, true, true, (phantom, level, target, position) -> phantom.snowball(level, target));
        register(ChristmasPhantom.class, "christmas_phantom", "dive", "4", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(ChristmasPhantom.class, "christmas_phantom", "guard", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.guard(level));
        register(ChristmasPhantom.class, "christmas_phantom", "miracle", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.miracle(level));
        register(HalloweenPhantom.class, "halloween_phantom", "dive", "3", Target.ENEMY, true, false, (phantom, level, target, position) -> phantom.dive(level, target));
        register(HalloweenPhantom.class, "halloween_phantom", "screech", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.screech(level));
        register(HalloweenPhantom.class, "halloween_phantom", "fright", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.nightOfFright(level));
        register(AutumnPhantom.class, "autumn_phantom", "rush", "3", Target.ENEMY, false, false, (phantom, level, target, position) -> phantom.rush(level, target));
        register(AutumnPhantom.class, "autumn_phantom", "guard", "4", Target.NONE, true, false, (phantom, level, target, position) -> phantom.guard(level));
        register(AutumnPhantom.class, "autumn_phantom", "gift", "5", Target.NONE, true, false, (phantom, level, target, position) -> phantom.gift(level));
        register(AutumnPhantom.class, "autumn_phantom", "gale", "6", Target.NONE, true, false, (phantom, level, target, position) -> phantom.gale(level));
        register(AutumnPhantom.class, "autumn_phantom", "crops", "5", Target.POSITION, false, false, (phantom, level, target, position) -> phantom.harvest().select(net.ronm19.phantomism.entity.ai.AutumnHarvest.Mode.CROPS, position.add(0, -.01D, 0)));
        register(AutumnPhantom.class, "autumn_phantom", "vegetation", "5", Target.POSITION, false, false, (phantom, level, target, position) -> phantom.harvest().select(net.ronm19.phantomism.entity.ai.AutumnHarvest.Mode.VEGETATION, position.add(0, -.01D, 0)));
        register(AutumnPhantom.class, "autumn_phantom", "wood", "5", Target.POSITION, false, false, (phantom, level, target, position) -> phantom.harvest().select(net.ronm19.phantomism.entity.ai.AutumnHarvest.Mode.WOOD, position.add(0, -.01D, 0)));
        register(AutumnPhantom.class, "autumn_phantom", "harvest_off", "5", Target.NONE, false, false, (phantom, level, target, position) -> phantom.harvest().select(net.ronm19.phantomism.entity.ai.AutumnHarvest.Mode.DISABLED, position));
    }
    private PhantomAbilities() {}

    private static <T extends AbstractPhantom> void register(Class<T> type, String species, String key, String guide,
            Target target, boolean mounted, boolean shot, Activation<T> activation) {
        String prefix = "codex.field." + species + ".ability." + guide;
        String name = type == AutumnPhantom.class && List.of("crops", "vegetation", "wood", "harvest_off").contains(key) ? "staff.ability.autumn." + key : type == SolarPhantom.class && List.of("charged", "emergency", "comet", "salvation").contains(key)
                ? "staff.ability.solar." + key : prefix + ".name";
        REGISTERED.computeIfAbsent(type, ignored -> new ArrayList<>()).add(new Ability(id(species + "/" + key), key,
                name, prefix + ".description", target, mounted, shot, false,
                (phantom, level, enemy, position) -> activation.activate(type.cast(phantom), level, enemy, position)));
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }

    public static List<Ability> abilities(AbstractPhantom phantom) {
        List<Ability> result = new ArrayList<>(REGISTERED.getOrDefault(phantom.getClass(), List.of()));
        var represented = new HashSet<String>();
        result.forEach(ability -> represented.add(ability.descriptionKey()));
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(phantom.getType()).getPath();
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            if (!discovery.modelName().equals(species)) { continue; }
            var guide = CodexFieldNotes.guide(discovery, phantom);
            if (guide == null) { break; }
            for (int index = 0; index < guide.abilities().size(); index++) {
                var note = guide.abilities().get(index);
                if (!represented.contains(note.descriptionKey())) {
                    result.add(new Ability(id(species + "/automatic_" + index), "", note.nameKey(), note.descriptionKey(),
                            Target.NONE, false, false, true, (source, level, enemy, position) -> false));
                }
            }
            break;
        }
        return List.copyOf(result);
    }

    public static boolean commandable(AbstractPhantom phantom) {
        return phantom.level() instanceof ServerLevel && phantom.isAlive() && !phantom.isRemoved() && !phantom.isBaby()
                && !(phantom instanceof AbstractHolidayPhantom holiday && holiday.isHolidayDefeated())
                && phantom.getOrder() != AbstractPhantom.Order.STAY && phantom.getOrder() != AbstractPhantom.Order.RETURN_HOME
                && (phantom.getTask() == PhantomAssignments.Task.NONE || phantom.getTask() == PhantomAssignments.Task.HUNT
                || phantom instanceof AutumnPhantom && phantom.getTask() == PhantomAssignments.Task.HARVEST);
    }

    public static View view(AbstractPhantom phantom, Ability ability) {
        int remaining = ability.remaining(phantom);
        String reason = ability.automatic() ? "staff.ability.automatic" : !commandable(phantom) ? "staff.ability.orders"
                : phantom.isVehicle() && !ability.mounted() ? "staff.ability.dismount" : "";
        return new View(ability.id().toString(), ability.nameKey(), ability.descriptionKey(), ability.target(),
                !reason.isEmpty() ? Status.UNAVAILABLE : remaining > 0 ? Status.COOLING_DOWN : Status.READY,
                remaining, ability.automatic(), reason);
    }

    /** A mounted shot must never shorten an existing AI/Staff recovery. */
    public static int mountedRecoveryFloor(AbstractPhantom phantom, int riderTicks) {
        return Math.max(riderTicks, REGISTERED.getOrDefault(phantom.getClass(), List.of()).stream()
                .filter(Ability::shot).mapToInt(ability -> ability.remaining(phantom)).max().orElse(0));
    }

    public static boolean mountedReady(AbstractPhantom phantom) {
        return REGISTERED.getOrDefault(phantom.getClass(), List.of()).stream().filter(Ability::shot)
                .allMatch(ability -> ability.remaining(phantom) == 0);
    }
}
