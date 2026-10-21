package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.TitaniumVfx;

public final class TitaniumEffects {
    private TitaniumEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.TITANIUM || form == VfxRegistry.Form.TITANS_IMPACT; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect(); float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.clamp(Math.min((age + 1) / 3, (duration - age) / 12), 0, 1); double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        Vec3 center = effect.interpolated(partial); int cyan = effect.packet.primary(), silver = effect.packet.secondary(), segments = 16 + Math.clamp(effect.quality, 0, 2) * 8;
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        if (kind.equals(TitaniumVfx.FIST)) {
            Vec3 forward = effect.packet.direction().lengthSqr() > .0001D ? effect.packet.direction().normalize() : new Vec3(0, 0, 1), side = forward.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < .0001D) { side = new Vec3(1, 0, 0); } else { side = side.normalize(); } Vec3 up = side.cross(forward).normalize();
            for (int finger = 0; finger < 4; finger++) { Vec3 knuckle = center.add(side.scale((finger - 1.5D) * .4D)); box(mesh, knuckle, forward, side, up, .5D, .18D, .35D, finger % 2 == 0 ? cyan : silver, fade * .9F, definition.hand()); }
            box(mesh, center.subtract(forward.scale(.65D)), forward, side, up, .55D, .85D, .4D, cyan, fade * .6F, definition.hand());
            box(mesh, center.add(side.scale(1)).subtract(forward.scale(.25D)), forward, side, up, .5D, .22D, .3D, silver, fade * .8F, definition.hand());
            mesh.ribbon(effect.history(partial), .65D, cyan, fade * .55F, definition.trail()); mesh.ribbon(effect.history(partial), .12D, silver, fade, definition.trail());
        } else if (kind.equals(TitaniumVfx.GUARD)) {
            double extent = Math.max(.1D, radius * Math.min(1, (age + 1) / 10D) * Math.min(1, (duration - age) / 16D));
            for (int band = 0; band < 5; band++) { double elevation = band * Math.PI / 10; mesh.ring(center.add(0, Math.sin(elevation) * extent, 0), Math.max(.1D, Math.cos(elevation) * extent), .035D, true, age * .001D, band % 2 == 0 ? cyan : silver, fade * .3F, segments, definition.trail()); }
            for (int panel = 0; panel < 8; panel++) { double angle = panel * Math.PI / 4; Vec3 anchor = center.add(Math.cos(angle) * extent, 2, Math.sin(angle) * extent), side = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(1.1D); mesh.quad(definition.ring(), anchor.subtract(side).add(0, 1.6D, 0), anchor.add(side).add(0, 1.6D, 0), anchor.add(side).add(0, -1.5D, 0), anchor.subtract(side).add(0, -1.5D, 0), cyan, fade * .25F); }
            mesh.face(center.add(0, .15D, 0), extent * .55D, 0, true, silver, fade * .3F, definition.face());
        } else if (definition.form() == VfxRegistry.Form.TITANS_IMPACT) {
            double burst = Math.clamp(age / 24D, 0, 1), decay = Math.min(1, (duration - age) / 24D);
            mesh.face(center.add(0, .12D, 0), radius * Math.max(.1D, burst) * decay, 0, true, cyan, fade * .6F, definition.face());
            for (int ring = 0; ring < 4; ring++) { double expansion = Math.clamp((age - ring * 4) / 26D, .01D, 1); mesh.ring(center.add(0, .25D + ring * .3D, 0), radius * expansion * decay, .2D - ring * .03D, true, 0, ring % 2 == 0 ? cyan : silver, fade * (float) Math.max(.1D, 1 - age / duration), segments, definition.trail()); }
            int debris = 8 + Math.clamp(effect.quality, 0, 2) * 4;
            for (int shard = 0; shard < debris; shard++) { double angle = shard * Math.PI * 2 / debris + effect.packet.seed() % 100 * .01D, distance = radius * (.3D + shard % 4 * .15D), height = Math.sin(Math.min(Math.PI, age / 40D * Math.PI)) * (2 + shard % 4); Vec3 point = center.add(Math.cos(angle) * distance * burst, height, Math.sin(angle) * distance * burst); mesh.face(point, .25D + shard % 3 * .12D, age * .04D + angle, false, silver, fade * .75F, definition.hand()); if (age < 48) { List<Vec3> pillar = List.of(point, point.add(0, (4 + shard % 5) * (1 - age / 48D), 0)); mesh.ribbon(pillar, .12D, cyan, fade * .85F, definition.trail()); } }
            if (age < 18) { mesh.face(center.add(0, 1, 0), Math.max(.1D, 4 * (1 - age / 18D)), 0, false, silver, fade, definition.ring()); }
        } else if (kind.equals(TitaniumVfx.RAM) || kind.equals(TitaniumVfx.CHARGE)) {
            mesh.ribbon(effect.history(partial), .7D, cyan, fade * .55F, definition.trail()); mesh.ribbon(effect.history(partial), .15D, silver, fade, definition.trail());
            for (int ring = 0; ring < 2; ring++) { mesh.ring(center.add(0, .3D + ring * .4D, 0), 2 + ring + Math.sin(age * .1D) * .2D, .12D, true, age * .01D, cyan, fade * .7F, segments, definition.trail()); }
            if (kind.equals(TitaniumVfx.CHARGE)) { mesh.face(center.add(0, -.1D, 0), 3, -age * .01D, true, silver, fade, definition.face()); }
        } else {
            double extent = Math.max(.1D, radius * (kind.equals(TitaniumVfx.MINE) ? .4D : progress));
            mesh.ring(center.add(0, .35D, 0), extent, .2D, true, 0, cyan, fade, segments, definition.trail());
            mesh.face(center.add(0, .5D, 0), Math.max(.1D, radius * .5D * (1 - progress)), age * .015D, false, silver, fade * .85F, kind.equals(TitaniumVfx.SHIELD_HIT) ? definition.ring() : definition.hand());
            for (int shard = 0; shard < 6; shard++) { double angle = shard * Math.PI / 3; mesh.face(center.add(Math.cos(angle) * extent, .4D + Math.sin(progress * Math.PI) * 1.5D, Math.sin(angle) * extent), .12D, angle + age * .1D, false, silver, fade, definition.hand()); }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), cyan);
    }
    private static void box(VfxMesh.Builder mesh, Vec3 center, Vec3 forward, Vec3 side, Vec3 up, double length, double width, double height, int color, float opacity, Identifier texture) {
        Vec3[] points = new Vec3[8]; for (int index = 0; index < 8; index++) { points[index] = center.add(forward.scale((index & 1) == 0 ? -length : length)).add(side.scale((index & 2) == 0 ? -width : width)).add(up.scale((index & 4) == 0 ? -height : height)); }
        for (int[] face : new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}}) { mesh.quad(texture, points[face[0]], points[face[1]], points[face[2]], points[face[3]], color, opacity); }
    }
}
