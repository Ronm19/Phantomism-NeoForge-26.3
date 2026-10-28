package net.ronm19.phantomism.vfx;

import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.EarthPhantom;
import net.ronm19.phantomism.entity.FirePhantom;
import net.ronm19.phantomism.entity.FrostPhantom;
import net.ronm19.phantomism.entity.SandPhantom;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.WaterPhantom;

public enum PhantomVfxTheme {
    LUNAR(0x8DBAFF, 0xF1E8FF),
    INFERNAL_PHANTOM(0xFF651A, 0xFFDB7F),
    ILLAGER(0x4CE3AA, 0xB93554),
    BLACK(0xC690FF, 0x5530AC),
    FIRE(0xFFF16A, 0xF43C0A),
    FROST(0xD9FFFF, 0x38A9EB),
    EARTH(0xCEFC8A, 0xA77B35),
    WATER(0xB3FFFF, 0x2473ED),
    STORM(0xDDEBFF, 0x9964FF),
    SAND(0xFFE171, 0xC48630),
    GOLD(0xFFF7B0, 0xE3A51A),
    CHERRY(0xFFE4EC, 0xCE7491),
    DRAGON(0xE2B8FF, 0x532573),
    VIOLET(0xF0B3FF, 0x7820DB),
    TOXIC(0xD1FF59, 0x50791D),
    SCULK(0x64F4FF, 0x006E86),
    RAVAGER(0xFFD19A, 0xA84A2C),
    END(0xD4A0FF, 0x382258),
    ZOMBIE(0xBEDC72, 0x33452B),
    SKELETON(0xE3CBBB, 0x8F43D5),
    HUSK(0xFFE0A0, 0xAC713B),
    ECHO(0x9EEFFF, 0x1742A4),
    BLOOD(0xFF5361, 0x870C20),
    VAMPIRE(0xFF477B, 0x350C21),
    GRAVITY(0xB8A3FF, 0x28106F),
    WITHER(0x87F0FF, 0x07313B),
    CHRONO(0x83EFFF, 0xF2C567),
    RIFT(0xB98BFF, 0x5EE5FF),
    SENTINEL(0xC7F0FF, 0x5C7DA8),
    DEMON(0xFF203B, 0xFF9D38),
    SPECTRAL(0x9DEFFF, 0xA58AFF),
    ANGEL(0xFFE2A0, 0xE4F3FF),
    OMEN(0x1AF599, 0xB0FFE5),
    ANCIENT(0x60EADD, 0xD7BE85),
    REAPER(0xAE67EB, 0x77E8F5),
    PRIMORDIAL(0xA68AFF, 0x79EFFF),
    VOID(0x963CFA, 0xD6ADFF),
    DARK(0x6516B3, 0xDC89FA),
    KING(0xEFB73E, 0xFFECB0), GOLEM(0x20BBF5, 0xD8EEFA), MIRAGE(0xBE92EF, 0x72F5F5), ECLIPSE(0xFFC757, 0xAD6DFF), SHADOW(0x934BDF, 0xCEBDE8), PLAGUE(0x86C92D, 0xDBF79C), CRYSTAL(0xBA87FF, 0x8AECFF), OBSIDIAN(0x8D27D9, 0xE5B9FF), GALE(0x2FCBFF, 0xE5FAFF), SPORE(0x94C846, 0xE5E9B3), NEW_YEARS(0xFFF0C4, 0x8EADE8), VALENTINES(0xFFD1E2, 0xD6337A), CREATOR(0xFFFFFF, 0x191919),
    SOLAR(0xFFB52C, 0xFFF5DB), AUTUMN(0xED802D, 0xF7D786), HALLOWEEN(0xFF991F, 0xAD65ED),
    CHRISTMAS(0xFFE2A0, 0xB7283B), EASTER(0xE7A7CE, 0xA8D8C2), FIREWORK(0xF2F4FF, 0x3D8DFF), ST_PATRICKS(0x67DF7B, 0xF5D488);

    private final int color;
    private final int fadeColor;

    PhantomVfxTheme(int color, int fadeColor) {
        this.color = color;
        this.fadeColor = fadeColor;
    }

    public int color() {
        return this.color;
    }

    public int fadeColor() {
        return this.fadeColor;
    }

    public static PhantomVfxTheme of(AbstractPhantom phantom) {
        if (phantom instanceof net.ronm19.phantomism.entity.AutumnPhantom) { return AUTUMN; }
        if (phantom instanceof net.ronm19.phantomism.entity.HalloweenPhantom) { return HALLOWEEN; }
        if (phantom instanceof net.ronm19.phantomism.entity.ChristmasPhantom) { return CHRISTMAS; }
        if (phantom instanceof net.ronm19.phantomism.entity.EasterPhantom) { return EASTER; }
        if (phantom instanceof net.ronm19.phantomism.entity.FireworkPhantom) { return FIREWORK; }
        if (phantom instanceof net.ronm19.phantomism.entity.StPatricksPhantom) { return ST_PATRICKS; }

        if (phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom) { return CREATOR; }
        if (phantom instanceof net.ronm19.phantomism.entity.ValentinesPhantom) { return VALENTINES; }
        if (phantom instanceof net.ronm19.phantomism.entity.NewYearsPhantom) { return NEW_YEARS; }
        if (phantom instanceof net.ronm19.phantomism.entity.LunarPhantom) { return LUNAR; }
        if (phantom instanceof net.ronm19.phantomism.entity.InfernalPhantom) { return INFERNAL_PHANTOM; }
        if (phantom instanceof net.ronm19.phantomism.entity.IllagerPhantom) { return ILLAGER; }
        if (phantom instanceof net.ronm19.phantomism.entity.SolarPhantom) { return SOLAR; }
        if (phantom instanceof net.ronm19.phantomism.entity.SporePhantom) { return SPORE; }
        if (phantom instanceof net.ronm19.phantomism.entity.GalePhantom) { return GALE; }
        if (phantom instanceof net.ronm19.phantomism.entity.ObsidianPhantom) { return OBSIDIAN; }
        if (phantom instanceof net.ronm19.phantomism.entity.CrystalPhantom) { return CRYSTAL; }
        if (phantom instanceof net.ronm19.phantomism.entity.PlaguePhantom) { return PLAGUE; }
        if (phantom instanceof net.ronm19.phantomism.entity.ShadowPhantom) { return SHADOW; }
        if (phantom instanceof net.ronm19.phantomism.entity.EclipsePhantom) { return ECLIPSE; }
        if (phantom instanceof net.ronm19.phantomism.entity.MiragePhantom) { return MIRAGE; }
        if (phantom instanceof net.ronm19.phantomism.entity.GolemPhantom) { return GOLEM; }
        if (phantom instanceof net.ronm19.phantomism.entity.PhantomKing) { return KING; }
        if (phantom instanceof net.ronm19.phantomism.entity.DarkPhantom) { return DARK; }
        if (phantom instanceof net.ronm19.phantomism.entity.VoidPhantom) { return VOID; }
        if (phantom instanceof net.ronm19.phantomism.entity.PrimordialPhantom) { return PRIMORDIAL; }
        if (phantom instanceof net.ronm19.phantomism.entity.ReaperPhantom) { return REAPER; }
        if (phantom instanceof net.ronm19.phantomism.entity.AncientPhantom) { return ANCIENT; }
        if (phantom instanceof net.ronm19.phantomism.entity.OmenPhantom) { return OMEN; }
        if (phantom instanceof net.ronm19.phantomism.entity.AngelPhantom) { return ANGEL; }
        if (phantom instanceof net.ronm19.phantomism.entity.SpectralPhantom) { return SPECTRAL; }
        if (phantom instanceof net.ronm19.phantomism.entity.DemonPhantom) { return DEMON; }
        if (phantom instanceof net.ronm19.phantomism.entity.SentinelPhantom) { return SENTINEL; }
        if (phantom instanceof net.ronm19.phantomism.entity.RiftPhantom) { return RIFT; }
        if (phantom instanceof net.ronm19.phantomism.entity.ChronoPhantom) { return CHRONO; }
        if (phantom instanceof net.ronm19.phantomism.entity.WitherPhantom) { return WITHER; }
        if (phantom instanceof net.ronm19.phantomism.entity.GravityPhantom) { return GRAVITY; }
        if (phantom instanceof net.ronm19.phantomism.entity.VampirePhantom) { return VAMPIRE; }
        if (phantom instanceof net.ronm19.phantomism.entity.BloodPhantom) { return BLOOD; }
        if (phantom instanceof net.ronm19.phantomism.entity.EchoPhantom) { return ECHO; }
        if (phantom instanceof net.ronm19.phantomism.entity.HuskPhantom) { return HUSK; }
        if (phantom instanceof net.ronm19.phantomism.entity.SkeletonPhantom) { return SKELETON; }
        if (phantom instanceof net.ronm19.phantomism.entity.ZombiePhantom) { return ZOMBIE; }
        if (phantom instanceof net.ronm19.phantomism.entity.EndPhantom) { return END; }
        if (phantom instanceof net.ronm19.phantomism.entity.RavagerPhantom) { return RAVAGER; }
        if (phantom instanceof net.ronm19.phantomism.entity.SculkPhantom) { return SCULK; }
        if (phantom instanceof net.ronm19.phantomism.entity.ToxicPhantom) { return TOXIC; }
        if (phantom instanceof net.ronm19.phantomism.entity.VioletPhantom) { return VIOLET; }
        if (phantom instanceof net.ronm19.phantomism.entity.PhantomDragon) { return DRAGON; }
        if (phantom instanceof net.ronm19.phantomism.entity.CherryPhantom) { return CHERRY; }
        if (phantom instanceof net.ronm19.phantomism.entity.GoldPhantom) { return GOLD; }
        return phantom instanceof FirePhantom ? FIRE : phantom instanceof FrostPhantom ? FROST
                : phantom instanceof EarthPhantom ? EARTH : phantom instanceof WaterPhantom ? WATER
                : phantom instanceof StormPhantom ? STORM : phantom instanceof SandPhantom ? SAND : BLACK;
    }
}
