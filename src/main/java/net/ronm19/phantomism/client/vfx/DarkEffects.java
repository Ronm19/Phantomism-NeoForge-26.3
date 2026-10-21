package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.DarkVfx;

public final class DarkEffects {
    private DarkEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.DARK || form == VfxRegistry.Form.DARK_BOLT || form == VfxRegistry.Form.DARK_BLADE || form == VfxRegistry.Form.DARK_HOUR; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; Identifier kind = effect.packet.effect(); float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.clamp(Math.min(age / 3, (duration - age) / 8), 0, 1); double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        Vec3 center = effect.interpolated(partial); int violet = effect.packet.primary(), light = effect.packet.secondary(), black = 0x180B24, crimson = 0xB43C91;
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        int quality = Math.clamp(effect.quality, 0, 2), segments = 16 + quality * 8;
        if (definition.form() == VfxRegistry.Form.DARK_HOUR) {
            double opening = Math.clamp(age / 36D, 0, 1), closing = Math.clamp((duration - age) / 24D, 0, 1), extent = Math.max(.1D, radius * opening * closing);
            mesh.face(center.add(0, .15D, 0), extent, age * .001D, true, violet, fade * .5F, definition.face());
            jagged(mesh, center.add(0, .25D, 0), extent, .16D, age * .002D, violet, fade * .9F, segments, definition.trail());
            jagged(mesh, center.add(0, .32D, 0), extent * .72D, .08D, -age * .004D, light, fade * .75F, segments, definition.trail());
            Vec3 emblem = center.add(0, 13 * opening, 0);
            mesh.face(emblem, 4 * opening * closing, -age * .005D, false, light, fade * .8F, definition.face());
            for (int fragment = 0; fragment < 6 + quality * 2; fragment++) {
                double angle = fragment * Math.PI * 2 / (6 + quality * 2) + age * .006D, reach = extent * .55D;
                Vec3 point = center.add(Math.cos(angle) * reach, 2 + Math.sin(age * .035D + fragment), Math.sin(angle) * reach);
                blade(mesh, point, .7D * opening * closing, angle, fragment % 3 == 0 ? crimson : violet, fade * .8F, definition.ring());
                if (age >= 36 && age < 180) {
                    List<Vec3> ribbon = new ArrayList<>();
                    for (int sample = 0; sample <= 10; sample++) { double fraction = sample / 10D, bend = angle + fraction * 1.4D; ribbon.add(center.add(Math.cos(bend) * reach * (1 - fraction), 1 + fraction * 12, Math.sin(bend) * reach * (1 - fraction))); }
                    mesh.ribbon(ribbon, .09D, light, fade * .6F, definition.trail());
                }
            }
            if (age < 40 || age > 176) { double pulse = age < 40 ? age / 40D : (age - 176) / 24D; jagged(mesh, center.add(0, .6D, 0), Math.max(.1D, radius * pulse), .24D, 0, light, fade * .9F, segments, definition.trail()); }
        } else if (definition.form() == VfxRegistry.Form.DARK_BOLT) {
            mesh.ribbon(effect.history(partial), .24D, black, fade, definition.trail()); mesh.ribbon(effect.history(partial), .07D, light, fade, definition.trail());
            mesh.face(center, .5D, age * .14D, false, violet, fade, definition.hand()); mesh.face(center, .2D, -age * .2D, false, light, fade, definition.hand());
            for (int shard = 0; shard < 3; shard++) { double angle = shard * Math.PI * 2 / 3 + age * .35D; blade(mesh, center.add(Math.cos(angle) * .3D, Math.sin(angle) * .3D, 0), .22D, angle, crimson, fade * .8F, definition.ring()); }
        } else if (definition.form() == VfxRegistry.Form.DARK_BLADE) {
            mesh.ribbon(effect.history(partial), .1D, violet, fade * .9F, definition.trail()); blade(mesh, center, 1.2D, age * .02D, light, fade, definition.ring());
        } else if (kind.equals(DarkVfx.REND)) {
            for (int slash = 0; slash < 3; slash++) {
                List<Vec3> points = new ArrayList<>();
                for (int sample = 0; sample <= 14; sample++) { double fraction = sample / 14D, angle = fraction * Math.PI * 1.3D - 2.1D + slash * .45D + progress * .7D; points.add(center.add(Math.cos(angle) * radius * .75D, .6D + slash * .3D + Math.sin(angle) * .65D, Math.sin(angle) * radius * .5D)); }
                mesh.ribbon(points, .13D * (1 - progress), slash == 1 ? light : violet, fade, definition.trail());
            }
        } else if (kind.equals(DarkVfx.RUSH)) {
            mesh.ribbon(effect.history(partial), .45D, black, fade * .9F, definition.trail()); mesh.ribbon(effect.history(partial), .12D, light, fade * .85F, definition.trail());
            for (int side : new int[]{-1, 1}) { List<Vec3> points = effect.history(partial).stream().map(point -> point.add(side * 1.15D, .25D, 0)).toList(); mesh.ribbon(points, .09D, violet, fade, definition.trail()); }
        } else if (kind.equals(DarkVfx.BUBBLE)) {
            for (int ring = 0; ring < 3; ring++) { mesh.ring(center.add(0, 1, 0), radius, .055D, ring == 0, age * .003D + ring * Math.PI / 3, ring == 0 ? light : violet, fade * .55F, segments, definition.trail()); }
            for (int arc = 0; arc < 4 + quality; arc++) {
                List<Vec3> points = new ArrayList<>(); double angle = arc * Math.PI * 2 / (4 + quality) + age * .004D;
                for (int sample = 0; sample <= 12; sample++) { double fraction = sample / 12D, reach = Math.cos(fraction * Math.PI / 2) * radius; points.add(center.add(Math.cos(angle) * reach, 1 + Math.sin(fraction * Math.PI / 2) * radius, Math.sin(angle) * reach)); }
                mesh.ribbon(points, .035D, light, fade * .32F, definition.trail());
            }
            for (int shard = 0; shard < 6; shard++) { double angle = shard * Math.PI / 3 + age * .01D; blade(mesh, center.add(Math.cos(angle) * radius * .8D, 2 + Math.sin(age * .06D + shard), Math.sin(angle) * radius * .8D), .25D, angle, violet, fade * .5F, definition.ring()); }
        } else if (kind.equals(DarkVfx.SHIELD) || kind.equals(DarkVfx.SHIELD_HIT)) {
            double assembly = Math.min(1, age / 6D), breakup = Math.max(0, (progress - .8D) * 5);
            for (int plate = 0; plate < 5; plate++) { double angle = plate * Math.PI * 2 / 5 + age * .008D; Vec3 point = center.add(Math.cos(angle) * (2.2D - assembly * .6D + breakup), 1 + (plate % 2) * .5D + breakup, Math.sin(angle) * (2.2D - assembly * .6D + breakup)); blade(mesh, point, kind.equals(DarkVfx.SHIELD_HIT) ? .9D : .75D, angle, plate % 2 == 0 ? violet : light, fade * .75F, definition.ring()); }
        } else if (kind.equals(DarkVfx.DISRUPT) || kind.equals(DarkVfx.MARK)) {
            mesh.face(center.add(0, 2.7D, 0), kind.equals(DarkVfx.MARK) ? .6D : .4D, age * .02D, false, light, fade * .8F, definition.face());
            if (kind.equals(DarkVfx.DISRUPT)) { for (int shard = 0; shard < 3; shard++) { double angle = shard * Math.PI * 2 / 3 + age * .15D; blade(mesh, center.add(Math.cos(angle) * .8D, 1.4D, Math.sin(angle) * .8D), .2D, angle, violet, fade * .7F, definition.ring()); } }
        } else if (kind.equals(DarkVfx.PRESSURE)) {
            for (int shard = 0; shard < (int) radius; shard++) { double angle = shard * Math.PI * 2 / radius + age * .025D; blade(mesh, center.add(Math.cos(angle) * 1.4D, .7D + Math.sin(angle * 2) * .25D, Math.sin(angle) * 1.4D), .3D, angle, shard == 2 ? crimson : violet, fade * .85F, definition.ring()); }
        } else {
            double reach = Math.max(.1D, radius * progress);
            jagged(mesh, center.add(0, .4D, 0), reach, kind.equals(DarkVfx.FINALE) ? .25D : .14D, age * .005D, light, fade * .9F, segments, definition.trail());
            if (kind.equals(DarkVfx.FINALE)) { jagged(mesh, center.add(0, 1D, 0), Math.max(.1D, reach * .65D), .13D, -age * .008D, violet, fade, segments, definition.trail()); }
            mesh.face(center.add(0, .75D, 0), Math.max(.1D, 1 - progress), -age * .06D, false, violet, fade * .8F, definition.hand());
            for (int fragment = 0; fragment < 5 + quality; fragment++) { double angle = fragment * Math.PI * 2 / (5 + quality); blade(mesh, center.add(Math.cos(angle) * reach * .7D, .5D + progress * 2, Math.sin(angle) * reach * .7D), (1 - progress) * .5D, angle, fragment % 2 == 0 ? black : light, fade, definition.ring()); }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), violet);
    }
    private static void jagged(VfxMesh.Builder mesh, Vec3 center, double radius, double width, double rotation, int color, float opacity, int segments, Identifier texture) {
        for (int segment = 0; segment < segments; segment++) {
            double start = rotation + segment * Math.PI * 2 / segments, end = start + Math.PI * 1.65D / segments, reach = radius * (segment % 2 == 0 ? 1 : .95D);
            Vec3 first = center.add(Math.cos(start) * reach, 0, Math.sin(start) * reach), second = center.add(Math.cos(end) * reach, 0, Math.sin(end) * reach);
            mesh.line(first, second, width, color, opacity, texture);
        }
    }
    private static void blade(VfxMesh.Builder mesh, Vec3 center, double size, double angle, int color, float opacity, Identifier texture) {
        double across = Math.cos(angle) * size * .32D, depth = Math.sin(angle) * size * .32D;
        Vec3 tip = center.add(0, -size * 1.5D, 0), upper = center.add(0, size, 0), left = center.add(-across, .3D * size, -depth), right = center.add(across, .3D * size, depth);
        mesh.quad(texture, tip, left, upper, right, color, opacity);
        mesh.quad(texture, tip, center.add(-depth, .3D * size, across), upper, center.add(depth, .3D * size, -across), color, opacity * .8F);
    }
}
