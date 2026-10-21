package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.RoyalVfx;

public final class RoyalEffects {
    private RoyalEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.ROYAL || form == VfxRegistry.Form.KINGS_DOMINION; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect(); float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.clamp(Math.min(age / 4, (duration - age) / 10), 0, 1); double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        int gold = effect.packet.primary(), white = effect.packet.secondary(), blue = 0x273EBC, segments = 16 + Math.clamp(effect.quality, 0, 3) * 8;
        Vec3 center = effect.interpolated(partial); var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        if (definition.form() == VfxRegistry.Form.KINGS_DOMINION) {
            double opening = Math.clamp(age / 40D, 0, 1), closing = Math.clamp((duration - age) / 24D, 0, 1), extent = Math.max(.1D, radius * opening * closing);
            mesh.face(center.add(0, .15D, 0), extent, age * .001D, true, gold, fade * .55F, definition.face());
            for (int ring = 0; ring < 3; ring++) { mesh.ring(center.add(0, .25D + ring * .12D, 0), Math.max(.1D, extent * (1 - ring * .22D)), ring == 0 ? .2D : .09D, true, age * (ring % 2 == 0 ? .002D : -.003D), ring == 1 ? white : gold, fade * .85F, segments, definition.trail()); }
            Vec3 coronation = center.add(0, 11 + Math.sin(age * .025D) * .35D, 0);
            crown(mesh, coronation, Math.max(.1D, 4.5D * opening * closing), age * .004D, gold, fade * .95F, definition.hand());
            mesh.ring(coronation.add(0, -1, 0), Math.max(.1D, 6 * opening * closing), .1D, true, -age * .006D, white, fade * .7F, segments, definition.trail());
            int standards = 4 + Math.clamp(effect.quality, 0, 2);
            for (int standard = 0; standard < standards; standard++) {
                double angle = standard * Math.PI * 2 / standards, reach = extent * .78D; Vec3 anchor = center.add(Math.cos(angle) * reach, 1, Math.sin(angle) * reach);
                float bannerFade = fade * (float) (age < 60 ? 1 : Math.clamp((110 - age) / 50D, 0, 1));
                if (bannerFade > 0) { Vec3 side = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(1.6D); mesh.quad(definition.ring(), anchor.subtract(side).add(0, 7 * opening, 0), anchor.add(side).add(0, 7 * opening, 0), anchor.add(side).add(0, 2 * opening, 0), anchor.subtract(side).add(0, 2 * opening, 0), 0xFFFFFF, bannerFade * .8F); }
                if (age >= 40 && age < 276) {
                    List<Vec3> trail = new ArrayList<>();
                    for (int sample = 0; sample <= 10; sample++) { double fraction = sample / 10D, bend = angle + fraction * 1.1D + Math.sin(age * .015D) * .2D; trail.add(center.add(Math.cos(bend) * reach * (1 - fraction), .5D + fraction * 10, Math.sin(bend) * reach * (1 - fraction))); }
                    mesh.ribbon(trail, .1D, gold, fade * .65F, definition.trail()); mesh.ribbon(trail, .035D, white, fade * .85F, definition.trail());
                }
            }
            if (age > 276) { double finalWave = (age - 276) / 24D; mesh.ring(center.add(0, .8D, 0), Math.max(.1D, radius * finalWave), .3D, true, 0, white, fade, segments, definition.trail()); }
        } else if (kind.equals(RoyalVfx.RAM) || kind.equals(RoyalVfx.INTERCEPT)) {
            mesh.ribbon(effect.history(partial), .55D, blue, fade * .8F, definition.trail()); mesh.ribbon(effect.history(partial), .2D, gold, fade, definition.trail());
            for (int side : new int[]{-1, 1}) { mesh.ribbon(effect.history(partial).stream().map(point -> point.add(side * 1.9D, .35D, 0)).toList(), .11D, white, fade * .9F, definition.trail()); }
            crown(mesh, center.add(0, 2.4D, 0), .75D, age * .02D, gold, fade * .7F, definition.hand());
        } else if (kind.equals(RoyalVfx.STRIKE) || kind.equals(RoyalVfx.STRIKE_ARC)) {
            for (int arc = 0; arc < 2; arc++) {
                List<Vec3> points = new ArrayList<>();
                for (int sample = 0; sample <= 16; sample++) { double angle = sample / 16D * Math.PI * 1.4D - 2.3D + progress * .8D; points.add(center.add(Math.cos(angle) * radius, .8D + Math.sin(angle) * (arc == 0 ? .9D : -.5D), Math.sin(angle) * radius * .65D)); }
                mesh.ribbon(points, arc == 0 ? .2D : .07D, arc == 0 ? gold : white, fade, definition.trail());
            }
            crown(mesh, center.add(0, 1D, 0), (1 - progress) * .9D, age * .05D, white, fade, definition.hand());
        } else if (kind.equals(RoyalVfx.JUDGMENT) || kind.equals(RoyalVfx.MARK) || kind.equals(RoyalVfx.HESITATE)) {
            Vec3 mark = center.add(0, 3.2D + Math.sin(age * .08D) * .1D, 0); crown(mesh, mark, kind.equals(RoyalVfx.HESITATE) ? .35D : .6D, age * .012D, gold, fade * .9F, definition.hand());
            if (kind.equals(RoyalVfx.JUDGMENT)) { mesh.face(center.add(0, .15D, 0), 1.3D, -age * .008D, true, gold, fade * .6F, definition.face()); }
        } else if (kind.equals(RoyalVfx.GUARD)) {
            for (int facet = 0; facet < 6; facet++) {
                double angle = facet * Math.PI / 3 + age * .004D; Vec3 point = center.add(Math.cos(angle) * 3, 1.8D, Math.sin(angle) * 3), side = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(.7D);
                mesh.quad(definition.hand(), point.subtract(side).add(0, .8D, 0), point.add(0, 1.5D, 0), point.add(side).add(0, .8D, 0), point.add(0, -.8D, 0), facet % 2 == 0 ? gold : white, fade * .55F);
            }
            crown(mesh, center.add(0, 1.4D, 0), 2.8D, age * .004D, gold, fade * .45F, definition.hand());
        } else if (kind.equals(RoyalVfx.PRESENCE) || kind.equals(RoyalVfx.RALLY) || kind.equals(RoyalVfx.DIRECTIVE)) {
            boolean presence = kind.equals(RoyalVfx.PRESENCE); double intensity = presence ? Math.clamp(radius - 2, 1, 4) : 1;
            if (!presence) { mesh.ribbon(effect.history(partial), .09D, gold, fade * .9F, definition.trail()); }
            crown(mesh, center.add(0, presence ? 2.8D : 1.8D, 0), presence ? .6D + intensity * .08D : .4D, age * .012D, gold, fade * (presence ? .45F : .65F), definition.hand());
            for (int mote = 0; mote < (presence ? (int) intensity + 1 : 2); mote++) { double angle = mote * Math.PI * 2 / (intensity + 1) + age * .04D; mesh.face(center.add(Math.cos(angle) * 2, .4D + progress * 1.5D, Math.sin(angle) * 2), .12D, angle, false, white, fade * .75F, definition.hand()); }
        } else {
            for (int wave = 0; wave < (kind.equals(RoyalVfx.ROAR) || kind.equals(RoyalVfx.FINALE) ? 3 : 1); wave++) {
                double reach = Math.max(.1D, radius * Math.clamp(progress * (1.25D - wave * .2D), 0, 1));
                mesh.ring(center.add(0, .4D + wave * .7D, 0), reach, wave == 0 ? .24D : .09D, true, age * .003D, wave == 1 ? blue : gold, fade * .85F, segments, definition.trail());
            }
            if (kind.equals(RoyalVfx.FINALE)) { mesh.face(center.add(0, .12D, 0), radius * (1 - progress * .3D), -age * .002D, true, gold, fade * .8F, definition.face()); }
            crown(mesh, center.add(0, 2.5D + progress * 2, 0), (1 - progress) * Math.min(3, radius * .4D), age * .02D, white, fade * .9F, definition.hand());
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), gold);
    }
    private static void crown(VfxMesh.Builder mesh, Vec3 center, double radius, double rotation, int color, float opacity, Identifier texture) {
        if (radius <= .001D) { return; }
        int points = 8;
        for (int point = 0; point < points; point++) {
            double start = rotation + point * Math.PI * 2 / points, end = rotation + (point + 1) * Math.PI * 2 / points, middle = (start + end) * .5D;
            Vec3 left = center.add(Math.cos(start) * radius, 0, Math.sin(start) * radius), right = center.add(Math.cos(end) * radius, 0, Math.sin(end) * radius);
            mesh.quad(texture, left, right, right.add(0, radius * .22D, 0), left.add(0, radius * .22D, 0), color, opacity);
            Vec3 peak = center.add(Math.cos(middle) * radius * 1.08D, radius * (point % 2 == 0 ? .8D : .55D), Math.sin(middle) * radius * 1.08D);
            mesh.quad(texture, left.add(0, radius * .2D, 0), peak, peak, right.add(0, radius * .2D, 0), color, opacity);
        }
    }
}
