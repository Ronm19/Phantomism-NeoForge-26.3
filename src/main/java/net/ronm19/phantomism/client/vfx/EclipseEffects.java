package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.EclipseVfx;

public final class EclipseEffects {
    private EclipseEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.ECLIPSE || form == VfxRegistry.Form.TOTAL_ECLIPSE; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect();
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration(), progress = Math.clamp(age / duration, 0, 1);
        float fade = (float) Math.max(0, Math.min(1, Math.min(age / 5D, (duration - age) / 10D)));
        int primary = effect.packet.primary(), secondary = effect.packet.secondary(), segments = effect.quality == 0 ? 24 : 40;
        double radius = effect.packet.radius();
        if (kind.equals(EclipseVfx.TOTAL)) {
            double align = Math.min(1, age / 40D), collapse = Math.min(1, (duration - age) / 24D), extent = radius * align * collapse;
            mesh.face(center.add(0, .08D, 0), Math.max(.1D, extent), age * .002D, true, EclipseVfx.SOLAR, fade * .23F, definition.face());
            mesh.ring(center.add(0, .12D, 0), Math.max(.1D, extent), .16D, true, age * .002D, EclipseVfx.SOLAR, fade * .7F, segments, definition.trail());
            mesh.ring(center.add(0, 10 * align, 0), Math.max(.1D, extent * .65D), .2D, true, -age * .003D, EclipseVfx.LUNAR, fade * .75F, segments, definition.trail());
            Vec3 sky = center.add(0, 13 * align, 0);
            mesh.face(sky, Math.max(.1D, extent * .42D), age * .003D, false, EclipseVfx.SOLAR, fade * .65F, definition.face());
            mesh.ring(sky, Math.max(.1D, extent * .42D), .2D, false, age * .004D, EclipseVfx.LUNAR, fade, segments, definition.trail());
            mesh.face(sky.add(0, 0, -.03D), Math.max(.1D, extent * .29D), 0, false, 0x100D21, fade * .9F, definition.hand());
            for (int index = 0; index < 8; index++) {
                double angle = index * Math.PI / 4 + age * .001D;
                Vec3 point = center.add(Math.cos(angle) * extent * .78D, 1.8D + Math.sin(age * .04D + index) * .35D, Math.sin(angle) * extent * .78D);
                mesh.face(point, .8D * align, angle, false, index % 2 == 0 ? EclipseVfx.SOLAR : EclipseVfx.LUNAR, fade * .55F, definition.ring());
                if (age >= 40) { mesh.ribbon(List.of(point, point.add(0, 3 * collapse, 0)), .07D, index % 2 == 0 ? EclipseVfx.SOLAR : EclipseVfx.LUNAR, fade * .4F, definition.trail()); }
            }
        } else if (kind.equals(EclipseVfx.BOLT) || kind.equals(EclipseVfx.RUSH)) {
            mesh.ribbon(effect.history(partial), kind.equals(EclipseVfx.BOLT) ? .24D : .5D, primary, fade, definition.trail());
            mesh.ribbon(effect.history(partial), .07D, secondary, fade * .7F, definition.trail());
            mesh.face(center, .4D, age * .15D, false, primary, fade, definition.ring());
        } else if (kind.equals(EclipseVfx.GUARD) || kind.equals(EclipseVfx.HYBRID_GUARD)) {
            double extent = kind.equals(EclipseVfx.GUARD) ? 2.3D : radius;
            mesh.ring(center.add(0, .12D, 0), extent, .12D, true, age * .01D, primary, fade * .7F, segments, definition.trail());
            for (int index = 0; index < 4; index++) {
                double angle = index * Math.PI / 2 + age * .012D;
                mesh.face(center.add(Math.cos(angle) * extent, .7D, Math.sin(angle) * extent), .55D, angle, false, index % 2 == 0 ? primary : secondary, fade * .65F, definition.ring());
            }
        } else if (kind.equals(EclipseVfx.FANG) || kind.equals(EclipseVfx.REND)) {
            mesh.face(center.add(0, .7D, 0), radius * (.5D + progress * .4D), progress * Math.PI, false, EclipseVfx.LUNAR, fade, definition.ring());
            if (kind.equals(EclipseVfx.REND)) { mesh.face(center.add(0, .72D, .02D), radius * (.5D + progress * .4D), -progress * Math.PI, false, EclipseVfx.SOLAR, fade, definition.ring()); }
            mesh.ring(center.add(0, .3D, 0), Math.max(.1D, radius * progress), .1D, true, age * .02D, primary, fade * .6F, segments, definition.trail());
        } else if (kind.equals(EclipseVfx.JUDGMENT)) {
            double converge = Math.max(.08D, 1 - progress * 2);
            for (int index = 0; index < 2; index++) { mesh.ring(center.add(0, 1 + converge * 7, 0), Math.max(.1D, radius * converge * (index == 0 ? 1 : .7D)), .2D, true, age * .05D * (index == 0 ? 1 : -1), index == 0 ? EclipseVfx.SOLAR : EclipseVfx.LUNAR, fade, segments, definition.trail()); }
            mesh.ribbon(List.of(center.add(0, 15 * (1 - progress), 0), center.add(0, .2D, 0)), .8D * Math.sin(progress * Math.PI), EclipseVfx.SOLAR, fade * .8F, definition.trail());
            mesh.ring(center.add(0, .15D, 0), Math.max(.1D, radius * progress), .32D, true, age * .01D, EclipseVfx.LUNAR, fade, segments, definition.trail());
        } else {
            mesh.ring(center.add(0, .3D, 0), Math.max(.1D, radius * progress), .12D, kind.equals(EclipseVfx.PULSE), age * .03D, primary, fade, segments, definition.trail());
            mesh.face(center.add(0, .35D, 0), Math.max(.1D, radius * .5D), -age * .03D, kind.equals(EclipseVfx.PULSE), secondary, fade * .6F, definition.face());
            for (int index = 0; index < 6; index++) { double angle = index * Math.PI / 3 + age * .02D; mesh.face(center.add(Math.cos(angle) * radius * progress, .5D + Math.sin(progress * Math.PI), Math.sin(angle) * radius * progress), .13D, angle, false, primary, fade, definition.hand()); }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), primary);
    }
}
