package net.ronm19.phantomism.vfx;

import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.EarthPhantom;
import net.ronm19.phantomism.entity.FirePhantom;
import net.ronm19.phantomism.entity.FrostPhantom;
import net.ronm19.phantomism.entity.SandPhantom;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.WaterPhantom;

public enum PhantomVfxTheme {
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
    RAVAGER(0xFFD19A, 0xA84A2C);

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
