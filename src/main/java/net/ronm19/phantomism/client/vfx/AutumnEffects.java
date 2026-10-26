package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.AutumnVfx;

public final class AutumnEffects {
    private AutumnEffects() {}
    public static boolean supports(Identifier kind) { return AutumnVfx.ALL.contains(kind); }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO); var preset = effect.definition; var kind = effect.packet.effect();
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration(), radius = effect.packet.radius();
        int quality = Math.max(1, Math.min(effect.quality, camera.distanceToSqr(center) > 36 * 36 ? 1 : 3));
        int segments = quality == 1 ? 12 : 24;
        float fade = (float) Math.clamp(Math.min(age / 3D, (duration - age) / 10D), 0, 1);
        int amber = 0xEDA13E, cream = 0xF9DF99, rust = 0xB74624, green = 0xA9C772;
        if (kind.equals(AutumnVfx.BOLT)) {
            Vec3 direction = effect.packet.direction().normalize(); mesh.beam(center.subtract(direction.scale(1.6D)), center, .12D, amber, fade, preset.trail());
            mesh.face(center, .35D, age * .12D, false, cream, fade, preset.hand());
        } else if (kind.equals(AutumnVfx.GALE)) {
            double opening = Math.min(1, age / 16D);
            for (int strand = 0; strand < 3; strand++) {
                Vec3 previous = null;
                for (int step = 0; step <= segments; step++) {
                    double progress = (double) step / segments, turn = progress * Math.PI * 4 + age * .055D + strand * Math.PI * 2 / 3;
                    double spread = radius * (.28D + progress * .65D) * opening;
                    Vec3 point = center.add(Math.cos(turn) * spread, progress * 10, Math.sin(turn) * spread);
                    if (previous != null) { mesh.line(previous, point, .1D, strand % 2 == 0 ? amber : cream, fade * .65F, preset.trail()); }
                    if (step % 2 == 0) { mesh.face(point, .35D, turn, false, step % 3 == 0 ? rust : amber, fade * .8F, preset.hand()); }
                    previous = point;
                }
            }
            for (int tier = 0; tier < 3; tier++) { mesh.ring(center.add(0, tier * 2.5D, 0), radius * (.35D + tier * .22D) * opening, .09D, true, age * .02D, tier % 2 == 0 ? amber : cream, fade * .65F, segments, preset.ring()); }
        } else if (kind.equals(AutumnVfx.GUARD)) {
            for (int tier = 0; tier < 4; tier++) { mesh.ring(center.add(0, tier * radius * .2D, 0), radius * Math.sqrt(1 - tier * tier / 16D), .06D, true, age * .01D, tier % 2 == 0 ? amber : cream, fade * .6F, segments, preset.ring()); }
        } else if (kind.equals(AutumnVfx.RUSH)) {
            var trail = effect.history(partial); for (int index = 1; index < trail.size(); index++) { mesh.beam(trail.get(index - 1), trail.get(index), .3D, amber, fade, preset.trail()); }
            mesh.ring(center, 1.1D, .1D, false, age * .1D, cream, fade * .7F, segments, preset.ring());
        } else if (!kind.equals(AutumnVfx.LEAVES)) {
            double opening = kind.equals(AutumnVfx.FINALE) || kind.equals(AutumnVfx.IMPACT) ? age / duration : Math.min(1, age / 8D);
            mesh.ring(center.add(0, .15D, 0), Math.max(.15D, radius * opening), .09D, true, age * .02D, kind.equals(AutumnVfx.GIFT) ? green : amber, fade * .7F, segments, preset.ring());
            if (kind.equals(AutumnVfx.GIFT)) { mesh.beam(center, center.add(0, 3, 0), .13D, green, fade * .5F, preset.trail()); }
        }
        int leaves = kind.equals(AutumnVfx.LEAVES) ? quality * 3 : quality * 6;
        for (int index = 0; index < leaves; index++) {
            double angle = index * 2.399963D + age * .025D, spread = Math.min(radius, 5) * (.25D + index % 4 * .2D), falling = 2 - (age * .035D + index * .3D) % 3;
            Vec3 point = center.add(Math.cos(angle) * spread, falling, Math.sin(angle) * spread);
            mesh.face(point, .12D + index % 3 * .045D, angle, false, kind.equals(AutumnVfx.GIFT) ? green : index % 3 == 0 ? rust : index % 3 == 1 ? amber : cream, fade * .75F, preset.hand());
        }
        return mesh.finish(List.of(), 1, preset.silhouette(), amber);
    }
}
