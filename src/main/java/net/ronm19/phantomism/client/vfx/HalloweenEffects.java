package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.HalloweenVfx;

public final class HalloweenEffects {
    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/halloween_energy.png");
    private static final Identifier GHOST = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/spectral_silhouette.png");
    private HalloweenEffects() {}
    public static boolean supports(Identifier kind) { return HalloweenVfx.ALL.contains(kind); }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        Vec3 center = effect.interpolated(partial);
        double age = effect.age(partial), duration = effect.packet.duration(), radius = effect.packet.radius();
        int quality = Math.max(1, Math.min(effect.quality, camera.distanceToSqr(center) > 48 * 48 ? 1 : 3));
        int segments = quality == 1 ? 16 : 32;
        float fade = (float)Math.clamp(Math.min(age / 3D, (duration - age) / 8D), 0, 1);
        int orange = 0xFF941F, candle = 0xFFE2A3, purple = 0xA562E1;
        Identifier kind = effect.packet.effect();
        if (kind.equals(HalloweenVfx.FRIGHT)) {
            double opening = Math.min(1, age / 20D);
            for (int tier = 0; tier < 3; tier++) {
                double extent = radius * (.45D + tier * .22D) * opening;
                mesh.ring(center.add(0, .12D + tier * .08D, 0), extent, .16D, true, age * .006D, tier == 1 ? purple : orange, fade * .9F, segments, ENERGY);
            }
            for (int ray = 0; ray < 8; ray++) {
                double angle = ray * Math.PI / 4 + age * .006D;
                Vec3 inner = center.add(Math.cos(angle) * radius * .3D, .18D, Math.sin(angle) * radius * .3D);
                Vec3 outer = center.add(Math.cos(angle) * radius * .91D, .18D, Math.sin(angle) * radius * .91D);
                mesh.line(inner, outer, .11D, ray % 2 == 0 ? candle : purple, fade * .7F, ENERGY);
            }
            int apparitions = 6;
            for (int orbit = 0; orbit < apparitions; orbit++) {
                double turn = age * .035D + orbit * Math.PI * 2 / apparitions;
                Vec3 point = center.add(Math.cos(turn) * radius * .63D, 2 + Math.sin(age * .09D + orbit) * .6D, Math.sin(turn) * radius * .63D);
                mesh.beam(point.add(0, -.5D, 0), point.add(0, -1.6D, 0), .22D, purple, fade * .5F, ENERGY);
                if (orbit % 2 == 0) { mesh.face(point.add(0, 1.7D, 0), 1.2D, turn * .05D, false, purple, fade * .48F, GHOST); }
            }
            for (int strand = 0; strand < 3; strand++) {
                Vec3 previous = null;
                for (int step = 0; step <= segments; step++) {
                    double progress = (double)step / segments;
                    double turn = progress * Math.PI * 2 + strand * Math.PI * 2 / 3 + age * .045D;
                    double spread = radius * (.35D + progress * .45D) * opening;
                    Vec3 point = center.add(Math.cos(turn) * spread, .35D + progress * 3.8D, Math.sin(turn) * spread);
                    if (previous != null) { mesh.line(previous, point, .15D, strand % 2 == 0 ? purple : orange, fade * .42F, ENERGY); }
                    previous = point;
                }
            }
            for (int pulse = 0; pulse < 4; pulse++) {
                double progress = (age - (pulse == 3 ? 93 : 28 + pulse * 20)) / 12D;
                if (progress >= 0 && progress <= 1) {
                    mesh.ring(center.add(0, .6D + progress, 0), Math.max(.2D, radius * progress), pulse == 3 ? .6D : .3D,
                            true, 0, pulse % 2 == 0 ? orange : purple, (float)(1 - progress) * fade, segments, ENERGY);
                }
            }
        } else if (kind.equals(HalloweenVfx.GAZE)) {
            var trail = effect.history(partial);
            for (int index = 1; index < trail.size(); index++) { mesh.beam(trail.get(index - 1), trail.get(index), .2D, orange, fade, ENERGY); }
            mesh.face(center, .18D, age * .12D, false, candle, fade * .35F, ENERGY);
        } else if (kind.equals(HalloweenVfx.DIVE)) {
            var trail = effect.history(partial);
            for (int index = 1; index < trail.size(); index++) { mesh.beam(trail.get(index - 1), trail.get(index), .45D, orange, fade * .8F, ENERGY); }
            mesh.ring(center, 1.1D, .15D, false, age * .1D, purple, fade * .75F, segments, ENERGY);
        } else if (kind.equals(HalloweenVfx.VEIL) || kind.equals(HalloweenVfx.DEFEAT)) {
            for (int tier = 0; tier < 3; tier++) { mesh.ring(center.add(0, tier * .6D, 0), 1.1D + tier * .2D, .18D, true, age * .04D, purple, fade * .6F, segments, ENERGY); }
            for (int ghost = 0; ghost < quality * 2; ghost++) {
                double turn = ghost * Math.PI / quality + age * .025D;
                mesh.face(center.add(Math.cos(turn) * 1.6D, .8D + Math.sin(turn), Math.sin(turn) * 1.6D), .55D, turn * .08D, false, purple, fade * .45F, GHOST);
            }
        } else {
            double progress = Math.clamp(age / duration, 0, 1);
            for (int tier = 0; tier < 3; tier++) { mesh.ring(center.add(0, tier * .4D, 0), Math.max(.1D, radius * Math.min(1, progress * 1.8D + tier * .1D)), .2D, true, 0, tier % 2 == 0 ? orange : purple, fade * .85F, segments, ENERGY); }
        }
        return mesh.finish(List.of(), 1, GHOST, orange);
    }
}
