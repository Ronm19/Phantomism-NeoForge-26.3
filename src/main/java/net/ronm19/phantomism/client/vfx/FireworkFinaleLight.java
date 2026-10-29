package net.ronm19.phantomism.client.vfx;

import net.ronm19.phantomism.entity.FireworkPhantom;
import net.ronm19.phantomism.vfx.FireworkVfx;

/** Client render illumination only; never updates the world's light engine. */
public final class FireworkFinaleLight {
    private FireworkFinaleLight() {}
    private static double smooth(double value) {
        double clamped = Math.clamp(value, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }
    public static float pulse(double age) {
        double distance = Math.floorMod((long)Math.floor(age - 20), 60) + (age - Math.floor(age)) - 30;
        double finale = (age - 235) / 9;
        return (float)(.16D * Math.exp(-distance * distance / 81D) + .2D * Math.exp(-finale * finale));
    }
    public static float strength(double age, int duration) {
        if (!Double.isFinite(age) || duration <= 0 || age <= 0 || age >= duration) { return 0; }
        return (float)(smooth(age / 18D) * smooth((duration - age) / 24D) * Math.min(.98D, .7D + pulse(age)));
    }
    public static float strength(FireworkPhantom phantom, float partial) {
        if (phantom.isRemoved() || phantom.isHolidayDefeated()) { return 0; }
        VfxEffect effect = VfxManager.find(phantom.getUUID(), FireworkVfx.FINALE);
        return effect == null || effect.packet.entityId() != phantom.getId() || !effect.packet.attached().equals(phantom.getUUID())
                ? 0 : strength(effect.age(partial), effect.packet.duration());
    }
    public static int blockLight(FireworkPhantom phantom) {
        float strength = strength(phantom, 0);
        return strength <= .001F ? 0 : Math.clamp(Math.round(strength * 12), 0, 11);
    }
}
