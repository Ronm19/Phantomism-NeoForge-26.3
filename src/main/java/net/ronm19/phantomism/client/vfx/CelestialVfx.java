package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.AngelVfx;

public final class CelestialVfx {
    private CelestialVfx() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.CELESTIAL || form == VfxRegistry.Form.CELESTIAL_SMITE || form == VfxRegistry.Form.CELESTIAL_EYE; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.min(1, age / 5) * Math.min(1, (duration - age) / 18);
        double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius(); Vec3 center = effect.interpolated(partial);
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        int gold = effect.packet.primary(), pearl = effect.packet.secondary(), segments = 16 + effect.quality * 8;
        var kind = effect.packet.effect();
        if (definition.form() == VfxRegistry.Form.CELESTIAL_EYE) {
            double awakening = Math.min(1, age / 24D), conclusion = Math.min(1, (duration - age) / 24D), opening = awakening * conclusion;
            Vec3 eye = center.add(0, 7 + awakening * 2, 0);
            Vec3 right = camera.subtract(eye).cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
            Vec3 across = right.scale(5D * opening), up = new Vec3(0, 2.3D * opening * conclusion, 0);
            mesh.quad(definition.face(), eye.subtract(across).add(up), eye.add(across).add(up), eye.add(across).subtract(up), eye.subtract(across).subtract(up), gold, fade * .9F);
            mesh.ring(eye, 1.3D * opening, .055D, false, age * .012D, pearl, fade * .8F, segments, definition.trail());
            mesh.face(eye, .7D * opening, age * .018D, false, gold, fade * .85F, definition.hand());
            mesh.face(center.add(0, .08D, 0), 4D * opening, -age * .006D, true, gold, fade * .45F, definition.hand());
            if (age >= 24 && age <= 60) { mesh.ring(center.add(0, .25D, 0), radius * Math.clamp((age - 24) / 24, 0, 1), .12D, true, 0, gold, fade * .8F * (1 - Math.clamp((age - 48) / 12, 0, 1)), segments * 2, definition.trail()); }
            for (int band = 0; band < 3; band++) {
                double reach = (6 + band * 4) * opening;
                mesh.ring(center.add(0, 4 + band * 1.4D, 0), reach, .065D, true, age * .004D * (band % 2 == 0 ? 1 : -1), band % 2 == 0 ? gold : pearl, fade * .5F, segments, definition.trail());
            }
            for (int strand = 0; strand < 2; strand++) {
                var path = new ArrayList<Vec3>();
                for (int point = 0; point <= 24; point++) { double along = point / 24D, angle = along * Math.PI * 2 + age * .012D + strand * Math.PI; path.add(center.add(Math.cos(angle) * 5 * opening, .3D + along * 6 * opening, Math.sin(angle) * 5 * opening)); }
                mesh.ribbon(path, .11D, strand == 0 ? gold : pearl, fade * .65F, definition.trail());
                mesh.ribbon(path, .025D, pearl, fade * .9F, definition.trail());
            }
            for (int ray = 0; ray < effect.quality * 3; ray++) {
                double angle = ray * Math.PI * 2 / (effect.quality * 3) + age * .002D;
                Vec3 position = center.add(Math.cos(angle) * (3 + ray % 4) * opening, 0, Math.sin(angle) * (3 + ray % 4) * opening);
                mesh.line(position.add(0, .3D, 0), position.add(0, 5D * opening, 0), .035D, pearl, fade * .4F, definition.trail());
            }
            feathers(mesh, effect, center, age, 10D * opening, fade * .65F, effect.quality * 7);
        } else if (definition.form() == VfxRegistry.Form.CELESTIAL_SMITE) {
            Vec3 heading = effect.packet.direction().normalize();
            mesh.ribbon(effect.history(partial), .09D, gold, fade * .75F, definition.trail());
            mesh.ribbon(effect.history(partial), .025D, pearl, fade, definition.trail());
            mesh.line(center.subtract(heading.scale(.8D)), center.add(heading.scale(.7D)), .07D, gold, fade, definition.trail());
            mesh.line(center.subtract(heading.scale(.5D)), center.add(heading.scale(.9D)), .025D, pearl, fade, definition.trail());
            mesh.face(center, .3D, age * .04D, false, pearl, fade * .8F, definition.hand());
        } else if (kind.equals(AngelVfx.TOUCH)) {
            Vec3 direction = effect.packet.direction(); List<Vec3> points = new ArrayList<>();
            for (int point = 0; point <= 16; point++) { double along = point / 16D; points.add(center.add(direction.scale(along)).add(0, Math.sin(along * Math.PI) * 1.4D, 0)); }
            mesh.ribbon(points, .065D, gold, fade * .8F, definition.trail()); mesh.ribbon(points, .018D, pearl, fade, definition.trail());
            mesh.face(center.add(direction), .55D * Math.sin(Math.PI * progress), age * .015D, false, gold, fade, definition.hand());
            feathers(mesh, effect, center.add(direction), age, .7D, fade, 4);
        } else if (kind.equals(AngelVfx.SHIELD) || kind.equals(AngelVfx.BLESSING) || kind.equals(AngelVfx.INTERCEPT)) {
            for (int panel = 0; panel < 3; panel++) {
                double angle = panel * Math.PI * 2 / 3 + age * .008D; Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 position = center.add(radial.scale(1)).add(0, .8D, 0), side = radial.cross(new Vec3(0, 1, 0)).scale(.6D), up = new Vec3(0, .8D, 0);
                mesh.quad(definition.hand(), position.subtract(side).add(up), position.add(side).add(up), position.add(side).subtract(up), position.subtract(side).subtract(up), gold, fade * .27F);
                mesh.line(position.subtract(side).add(up.scale(.5D)), position.add(up), .035D, pearl, fade * .7F, definition.trail());
                mesh.line(position.add(up), position.add(side).add(up.scale(.5D)), .035D, pearl, fade * .7F, definition.trail());
                mesh.line(position.add(side).add(up.scale(.5D)), position.subtract(up), .035D, gold, fade * .6F, definition.trail());
                mesh.line(position.subtract(up), position.subtract(side).add(up.scale(.5D)), .035D, gold, fade * .6F, definition.trail());
            }
            mesh.face(center.add(0, 2.5D, 0), .4D, age * .01D, true, gold, fade * .75F, definition.hand());
        } else if (kind.equals(AngelVfx.TRAIL) || kind.equals(AngelVfx.DIVE)) {
            List<Vec3> history = kind.equals(AngelVfx.DIVE) ? effect.history(partial) : List.of(center.add(effect.packet.direction()), center);
            mesh.ribbon(history, .12D, gold, fade * .45F, definition.trail());
            mesh.ribbon(history.stream().map(point -> point.add(0, .12D, 0)).toList(), .035D, pearl, fade * .7F, definition.trail());
            feathers(mesh, effect, center, age, 1.4D, fade * .7F, 4);
        } else {
            mesh.ring(center.add(0, .2D, 0), radius * (.2D + progress), .035D, true, age * .01D, gold, fade * .6F, segments, definition.trail());
            mesh.face(center.add(0, .8D, 0), radius * .4D, -age * .012D, false, pearl, fade * .65F, definition.hand());
            feathers(mesh, effect, center, age, radius, fade * .7F, kind.equals(AngelVfx.PRESENCE) ? 3 : effect.quality * 4);
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), gold);
    }
    private static void feathers(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, float age, double radius, float fade, int count) {
        for (int feather = 0; feather < count; feather++) {
            double angle = feather * 2.399963D + age * .007D, distance = radius * (.3D + .7D * ((feather * 37) % 101) / 100D);
            double fall = (age * .025D + feather * .39D) % 3D;
            Vec3 position = center.add(Math.cos(angle) * distance, 3 - fall, Math.sin(angle) * distance);
            mesh.face(position, .13D + .05D * (feather % 3), Math.sin(age * .03D + feather) * .4D, false, feather % 3 == 0 ? effect.packet.secondary() : effect.packet.primary(), fade * (float) Math.sin(fall / 3 * Math.PI), effect.definition.ring());
        }
    }
}
