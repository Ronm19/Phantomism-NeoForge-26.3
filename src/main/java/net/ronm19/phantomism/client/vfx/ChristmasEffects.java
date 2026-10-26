package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.ChristmasVfx;

public final class ChristmasEffects {
    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/halloween_energy.png");
    private ChristmasEffects() {}
    public static boolean supports(Identifier kind) { return ChristmasVfx.ALL.contains(kind); }
    private static void flake(VfxMesh.Builder mesh, Vec3 center, double radius, double turn, int color, float alpha) {
        for (int arm = 0; arm < 6; arm++) {
            double angle = turn + arm * Math.PI / 3;
            Vec3 tip = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            mesh.line(center, tip, Math.max(.035D, radius * .025D), color, alpha, ENERGY);
            Vec3 joint = center.lerp(tip, .65D);
            for (int side : new int[]{-1, 1}) {
                double branch = angle + side * Math.PI / 3;
                mesh.line(joint, joint.add(Math.cos(branch) * radius * .25D, 0, Math.sin(branch) * radius * .25D), Math.max(.03D, radius * .018D), color, alpha, ENERGY);
            }
        }
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration();
        float fade = (float)Math.clamp(Math.min(age / 4D, (duration - age) / 10D), 0, 1);
        Identifier kind = effect.packet.effect(); int gold = 0xFFD581, snow = 0xEAF6FF, red = 0xC32F46, green = 0x5EAD82;
        int segments = effect.quality <= 1 ? 16 : 32;
        if (kind.equals(ChristmasVfx.MIRACLE)) {
            double opening = Math.min(1, age / 22D);
            for (int tier = 0; tier < 3; tier++) { mesh.ring(center.add(0, .12D + tier * .08D, 0), (7 + tier * 3) * opening, .17D, true, age * .004D, tier == 1 ? red : gold, fade * .85F, segments, ENERGY); }
            flake(mesh, center.add(0, .2D, 0), 10 * opening, age * .004D, snow, fade * .55F);
            for (int strand = 0; strand < 3; strand++) {
                Vec3 previous = null;
                for (int step = 0; step <= segments; step++) {
                    double progress = (double)step / segments, angle = progress * Math.PI * 2 + strand * Math.PI * 2 / 3 + age * .035D;
                    Vec3 point = center.add(Math.cos(angle) * (5 + progress * 5), .5D + progress * 5, Math.sin(angle) * (5 + progress * 5));
                    if (previous != null) { mesh.line(previous, point, .12D, strand == 0 ? gold : strand == 1 ? red : green, fade * .45F, ENERGY); }
                    previous = point;
                }
            }
            for (int star = 0; star < 6; star++) {
                double turn = star * Math.PI / 3 - age * .025D;
                flake(mesh, center.add(Math.cos(turn) * 10, 1.5D + Math.sin(age * .06D + star), Math.sin(turn) * 10), .65D, turn, gold, fade * .8F);
            }
        } else if (kind.equals(ChristmasVfx.SNOWBALL) || kind.equals(ChristmasVfx.DIVE)) {
            var trail = effect.history(partial);
            for (int point = 1; point < trail.size(); point++) { mesh.beam(trail.get(point - 1), trail.get(point), kind.equals(ChristmasVfx.DIVE) ? .3D : .08D, snow, fade * .65F, ENERGY); }
        } else if (kind.equals(ChristmasVfx.GUARD) || kind.equals(ChristmasVfx.DEFEAT)) {
            for (int tier = 0; tier < 3; tier++) { mesh.ring(center.add(0, .25D + tier * .6D, 0), 1 + tier * .15D, .12D, true, age * .035D, tier % 2 == 0 ? gold : snow, fade * .7F, segments, ENERGY); }
            for (int orbit = 0; orbit < 4; orbit++) { double turn = age * .05D + orbit * Math.PI / 2; flake(mesh, center.add(Math.cos(turn) * 1.5D, .4D + orbit * .3D, Math.sin(turn) * 1.5D), .35D, turn, snow, fade * .8F); }
        } else {
            double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius() * Math.min(1, progress * 1.6D);
            mesh.ring(center.add(0, .2D, 0), Math.max(.2D, radius), .18D, true, 0, gold, fade * .85F, segments, ENERGY);
            flake(mesh, center.add(0, .22D, 0), Math.max(.2D, radius), age * .01D, snow, fade * .7F);
            if (kind.equals(ChristmasVfx.PULSE)) {
                for (int ray = 0; ray < 8; ray++) { double angle = ray * Math.PI / 4; mesh.line(center.add(0, 3, 0), center.add(Math.cos(angle) * 3, 3 + Math.sin(angle) * 3, 0), .18D, gold, fade, ENERGY); }
            }
        }
        return mesh.finish(List.of(), 1, ENERGY, gold);
    }
}
