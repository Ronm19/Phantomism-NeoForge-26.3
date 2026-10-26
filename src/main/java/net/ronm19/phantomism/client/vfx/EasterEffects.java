package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.EasterVfx;

public final class EasterEffects {
    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/halloween_energy.png");
    private static final int LAVENDER = 0xBEA4DF, PINK = 0xF3AFD0, MINT = 0x9DDAC0, GOLD = 0xFFE2A1, IVORY = 0xFFF4E1;
    private EasterEffects() {}
    public static boolean supports(Identifier kind) { return EasterVfx.ALL.contains(kind); }
    private static void flower(VfxMesh.Builder mesh, Vec3 center, double size, double turn, int petals, int color, float alpha, int detail) {
        Vec3 previous = null;
        for (int step = 0; step <= detail; step++) {
            double angle = (double)step / detail * Math.PI * 2, radius = size * (.72D + .28D * Math.cos(petals * angle));
            Vec3 point = center.add(Math.cos(angle + turn) * radius, 0, Math.sin(angle + turn) * radius);
            if (previous != null) { mesh.line(previous, point, Math.max(.04D, size * .019D), color, alpha, ENERGY); }
            previous = point;
        }
        mesh.ring(center.add(0, .02D, 0), size * .16D, Math.max(.035D, size * .025D), true, turn, GOLD, alpha, Math.max(8, detail / 3), ENERGY);
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration();
        float fade = (float)Math.clamp(Math.min(age / 4D, (duration - age) / 9D), 0, 1);
        Identifier kind = effect.packet.effect(); int detail = effect.quality <= 1 ? 30 : 60;
        if (kind.equals(EasterVfx.AWAKENING)) {
            double unfold = Math.min(1, age / 24D);
            flower(mesh, center.add(0, .16D, 0), 11 * unfold, age * .003D, 5, PINK, fade * .8F, detail);
            flower(mesh, center.add(0, .19D, 0), 8 * unfold, -age * .005D, 6, LAVENDER, fade * .75F, detail);
            mesh.ring(center.add(0, .12D, 0), 11.5D * unfold, .11D, true, -age * .004D, MINT, fade * .6F, detail, ENERGY);
            for (int petal = 0; petal < 6; petal++) {
                double angle = petal * Math.PI / 3 + age * .018D;
                Vec3 point = center.add(Math.cos(angle) * 8, .6D + Math.sin(age * .05D + petal) * .3D, Math.sin(angle) * 8);
                flower(mesh, point, .8D, angle, 5, petal % 2 == 0 ? IVORY : PINK, fade, 20);
                Vec3 high = center.add(Math.cos(angle + .5D) * 4.5D, 4 + Math.sin(age * .04D + petal), Math.sin(angle + .5D) * 4.5D);
                mesh.line(point, high, .045D, MINT, fade * .3F, ENERGY);
            }
        } else if (kind.equals(EasterVfx.LEAP) || kind.equals(EasterVfx.EVADE) || kind.equals(EasterVfx.EGG)) {
            var trail = effect.history(partial);
            for (int index = 1; index < trail.size(); index++) {
                float opacity = fade * .7F * index / Math.max(1, trail.size());
                mesh.beam(trail.get(index - 1), trail.get(index), kind.equals(EasterVfx.EGG) ? .07D : .24D, index % 2 == 0 ? MINT : PINK, opacity, ENERGY);
                if (!kind.equals(EasterVfx.EGG) && index % 3 == 0) {
                    Vec3 point = trail.get(index);
                    mesh.line(point.add(-1.5D, .3D, .2D), point.add(0, .08D, -.3D), .06D, LAVENDER, opacity * .7F, ENERGY);
                    mesh.line(point.add(0, .08D, -.3D), point.add(1.5D, .3D, .2D), .06D, LAVENDER, opacity * .7F, ENERGY);
                }
            }
        } else if (kind.equals(EasterVfx.RENEWAL) || kind.equals(EasterVfx.DEFEAT)) {
            for (int petal = 0; petal < 5; petal++) {
                double angle = age * .02D + petal * Math.PI * 2 / 5;
                flower(mesh, center.add(Math.cos(angle) * 1.5D, .12D, Math.sin(angle) * 1.5D), .55D, -angle, 5, PINK, fade * .8F, 20);
                mesh.line(center.add(Math.cos(angle) * .6D, .2D, Math.sin(angle) * .6D), center.add(Math.cos(angle + .4D) * .4D, 1.8D, Math.sin(angle + .4D) * .4D), .055D, GOLD, fade * .65F, ENERGY);
            }
        } else {
            double progress = Math.clamp(age / duration, 0, 1), radius = Math.max(.25D, effect.packet.radius() * Math.min(1, progress * 1.8D));
            flower(mesh, center.add(0, .22D, 0), radius, age * .01D, kind.equals(EasterVfx.BLOSSOM) ? 8 : 5, PINK, fade * .85F, detail);
            flower(mesh, center.add(0, .18D, 0), radius * .7D, -age * .015D, 5, MINT, fade * .65F, detail);
            if (kind.equals(EasterVfx.BLOSSOM)) {
                for (int ray = 0; ray < 8; ray++) {
                    double angle = ray * Math.PI / 4;
                    Vec3 point = center.add(Math.cos(angle) * radius * .8D, 1 + (1 - progress) * 3, Math.sin(angle) * radius * .8D);
                    flower(mesh, point, .7D, angle, 5, ray % 2 == 0 ? IVORY : GOLD, fade, 20);
                }
            }
        }
        return mesh.finish(List.of(), 1, ENERGY, GOLD);
    }
}
