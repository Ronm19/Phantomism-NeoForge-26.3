package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.SolarVfx;

public final class SolarEffects {
    private SolarEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.SOLAR || form == VfxRegistry.Form.SUPERNOVA; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        if (TotalEclipseEffects.supports(effect.packet)) { return TotalEclipseEffects.build(effect, camera, partial, limit); }
        var definition = effect.definition; var kind = effect.packet.effect();
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration(), radius = effect.packet.radius();
        float fade = (float)Math.max(0, Math.min(1, Math.min(age / 3D, (duration - age) / 8D)));
        int gold = effect.packet.primary(), white = effect.packet.secondary(), segments = effect.quality == 0 ? 20 : 32;
        if (kind.equals(SolarVfx.BEAM)) {
            Vec3 end = center.add(effect.packet.direction());
            mesh.beam(center, end, Math.max(.12D, radius * 1.8D), gold, fade * .75F, definition.trail());
            mesh.beam(center, end, Math.max(.06D, radius * .65D), white, fade, definition.trail());
            mesh.ring(end, .45D + age * .03D, .08D, false, age * .1D, gold, fade, segments, definition.ring());
        } else if (kind.equals(SolarVfx.SUPERNOVA) || kind.equals(SolarVfx.ECLIPSE)) {
            double expansion = Math.min(1, age / (kind.equals(SolarVfx.ECLIPSE) ? 15D : 50D));
            double burst = kind.equals(SolarVfx.ECLIPSE) ? 1 : age >= 150 && age < 210 ? 1 + Math.sin((age - 150) / 60 * Math.PI) * .1D : .8D;
            double reach = radius * expansion * Math.min(1, (duration - age) / 20D);
            mesh.face(center.add(0, .15D, 0), Math.max(.1D, reach), age * .007D, true, gold, fade * .35F, definition.face());
            for (int tier = 0; tier < 5; tier++) {
                mesh.ring(center.add(0, 1 + tier * 1.6D, 0), Math.max(.1D, reach * (1 - tier * .12D) * burst), .08D,
                        true, age * .012D * (tier % 2 == 0 ? 1 : -1), tier % 2 == 0 ? gold : white, fade * .8F, segments, definition.ring());
            }
            mesh.face(center.add(0, 5 * expansion, 0), 1 + expansion * 2.5D, age * .02D, false, white, fade * .8F, definition.hand());
            if (age >= 50 && age < 150) {
                for (int index = 0; index < (effect.quality == 0 ? 4 : 7); index++) {
                    double angle = index * 2.399963D + Math.floor(age / 20) * .3D;
                    Vec3 point = center.add(Math.cos(angle) * reach * .7D, 0, Math.sin(angle) * reach * .7D);
                    mesh.beam(point.add(0, 10, 0), point, .12D, white, fade * .7F, definition.trail());
                }
            }
        } else if (kind.equals(SolarVfx.SANCTUARY) || kind.equals(SolarVfx.SALVATION)) {
            double opening = Math.min(1, age / 12D) * Math.min(1, (duration - age) / 10D);
            for (int tier = 0; tier < 5; tier++) {
                double height = tier * radius * .18D;
                double reach = radius * Math.sqrt(Math.max(.05D, 1 - tier * tier / 25D)) * opening;
                mesh.ring(center.add(0, height, 0), Math.max(.1D, reach), .07D, true, age * .008D, tier % 2 == 0 ? gold : white, fade * .65F, segments, definition.ring());
            }
            mesh.face(center.add(0, .08D, 0), radius * opening, -age * .003D, true, gold, fade * .25F, definition.face());
        } else if (kind.equals(SolarVfx.JUDGMENT)) {
            mesh.beam(center.add(0, 12, 0), center, .25D, white, fade, definition.trail());
            mesh.ring(center.add(0, .15D, 0), radius * Math.min(1, age / 8D), .08D, true, 0, gold, fade, segments, definition.ring());
        } else if (kind.equals(SolarVfx.BLITZ)) {
            var trail = effect.history(partial);
            for (int index = 1; index < trail.size(); index++) { mesh.beam(trail.get(index - 1), trail.get(index), .3D, gold, fade * .7F, definition.trail()); }
            mesh.ring(center, .65D, .1D, false, age * .1D, white, fade, segments, definition.ring());
        } else if (kind.equals(SolarVfx.CHARGE)) {
            mesh.face(center, .4D + age / duration, age * .05D, false, white, fade, definition.hand());
            mesh.ring(center, 1 + Math.sin(age * .1D) * .15D, .08D, false, age * .06D, gold, fade, segments, definition.ring());
        } else if (!kind.equals(SolarVfx.SCORCH)) {
            double reach = Math.max(.1D, radius * Math.min(1.1D, age / 12D));
            mesh.ring(center.add(0, .2D, 0), reach, .14D, true, age * .01D, gold, fade * .8F, segments, definition.ring());
            mesh.face(center.add(0, .8D, 0), Math.max(.2D, reach * .35D), age * .01D, false, white, fade * .7F, definition.hand());
        }
        for (int index = 0; index < (effect.quality == 0 ? 8 : 16); index++) {
            double angle = index * 2.399963D + age * .025D, reach = Math.min(radius, 5) * (.2D + index % 4 * .2D);
            Vec3 point = center.add(Math.cos(angle) * reach, (age * .025D + index * .13D) % 2, Math.sin(angle) * reach);
            mesh.face(point, .04D + index % 3 * .025D, age * .02D, false, index % 2 == 0 ? gold : white, fade * .7F, definition.hand());
        }
        return mesh.finish(List.of(), 1, definition.silhouette(), gold);
    }
}
