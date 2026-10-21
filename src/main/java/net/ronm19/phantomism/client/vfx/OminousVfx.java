package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.OmenVfx;

public final class OminousVfx {
    private OminousVfx() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.OMEN || form == VfxRegistry.Form.OMEN_BOLT || form == VfxRegistry.Form.CALAMITY; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect();
        float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.clamp(Math.min(age / 5, (duration - age) / 16), 0, 1);
        double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        Vec3 center = effect.interpolated(partial);
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        int emerald = effect.packet.primary(), mint = effect.packet.secondary(), segments = 16 + effect.quality * 8;
        if (definition.form() == VfxRegistry.Form.CALAMITY) {
            double opening = Math.min(1, age / 30D), closure = Math.min(1, (duration - age) / 24D);
            Vec3 crown = center.add(0, 11 + opening * 2 - (1 - closure) * 5, 0);
            mesh.face(crown, 9 * opening * closure, age * .004D, false, emerald, fade * .85F, definition.face());
            mesh.ring(crown, 10 * opening * closure, .09D, false, -age * .006D, mint, fade * .85F, segments, definition.trail());
            mesh.face(center.add(0, .1D, 0), radius * .45D * opening, -age * .003D, true, emerald, fade * .18F, definition.face());
            if (age > 30) {
                float condemnation = (float)Math.min(1, (age - 30) / 30D) * fade;
                for (int banner = 0; banner < 6; banner++) {
                    double angle = banner * Math.PI / 3;
                    Vec3 post = center.add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12);
                    banner(mesh, effect, post, angle, age, condemnation * .55F);
                    mesh.line(post.add(0, 2, 0), crown.add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5), .025D, emerald, condemnation * .25F, definition.trail());
                }
            }
            for (int wave : new int[]{60, 110, 160}) {
                double elapsed = age - wave;
                if (elapsed >= 0 && elapsed < 30) {
                    float opacity = fade * (float)(1 - elapsed / 30);
                    mesh.ring(center.add(0, .28D + elapsed * .015D, 0), Math.max(.1D, radius * elapsed / 30), .1D, true, 0, mint, opacity * .8F, segments, definition.trail());
                    mesh.ring(crown, 5 + elapsed * .2D, .065D, false, elapsed * .006D, emerald, opacity * .7F, segments, definition.trail());
                }
            }
            if (age >= 160 && age < 216) {
                for (int node = 0; node < 6; node++) {
                    double angle = node * Math.PI / 3;
                    Vec3 position = center.add(Math.cos(angle) * 7, 2 + Math.sin(age * .04D + node) * .2D, Math.sin(angle) * 7);
                    mesh.face(position, .65D, 0, false, mint, fade * .65F, definition.hand());
                    mesh.line(position, center.add(0, 3, 0), .02D, emerald, fade * .2F, definition.trail());
                }
            }
            if (age >= 216) {
                double contraction = (240 - age) / 24D;
                mesh.ring(center.add(0, 1 + contraction * 3, 0), Math.max(.1D, radius * contraction), .1D, true, age * .01D, mint, fade * .7F, segments, definition.trail());
                mesh.line(center.add(0, .2D, 0), crown, .08D * contraction, emerald, fade * .45F, definition.trail());
            }
        } else if (definition.form() == VfxRegistry.Form.OMEN_BOLT) {
            Vec3 heading = effect.packet.direction().normalize();
            mesh.ribbon(effect.history(partial), .09D, emerald, fade * .8F, definition.trail());
            mesh.ribbon(effect.history(partial), .025D, mint, fade, definition.trail());
            mesh.line(center.subtract(heading.scale(.8D)), center.add(heading.scale(.9D)), .06D, emerald, fade, definition.trail());
            mesh.face(center, .25D, age * .1D, false, mint, fade, definition.hand());
        } else if (kind.equals(OmenVfx.MARK) || kind.equals(OmenVfx.REVEAL)) {
            Vec3 crest = center.add(0, 3, 0);
            mesh.face(crest, kind.equals(OmenVfx.MARK) ? .7D : .45D, 0, false, emerald, fade * .8F, definition.hand());
            mesh.ring(crest, .85D, .025D, false, age * .012D, mint, fade * .55F, segments, definition.trail());
        } else if (kind.equals(OmenVfx.STANDARD)) {
            banner(mesh, effect, center, age * .001D, age, fade);
            mesh.ring(center.add(0, .12D, 0), radius, .04D, true, 0, emerald, fade * .25F, segments, definition.trail());
            mesh.face(center.add(0, .08D, 0), 3.5D, -age * .004D, true, emerald, fade * .35F, definition.face());
        } else if (kind.equals(OmenVfx.SHIELD)) {
            for (int panel = 0; panel < 5; panel++) {
                double angle = panel * Math.PI * 2 / 5 + age * .006D;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 position = center.add(radial.scale(1.3D)).add(0, .8D, 0), side = radial.cross(new Vec3(0, 1, 0)).scale(.65D), up = new Vec3(0, .9D, 0);
                mesh.quad(definition.hand(), position.subtract(side).add(up), position.add(side).add(up), position.add(side).subtract(up), position.subtract(side).subtract(up), emerald, fade * .25F);
                mesh.line(position.subtract(side).add(up), position.add(side).add(up), .035D, mint, fade * .7F, definition.trail());
            }
        } else if (kind.equals(OmenVfx.JUDGMENT)) {
            mesh.line(center.add(0, 14 * (1 - progress), 0), center, .13D * (1 - progress), mint, fade * .85F, definition.trail());
            mesh.face(center.add(0, .13D, 0), 1.5D + progress * 2, age * .01D, true, emerald, fade * .65F, definition.face());
            mesh.ring(center.add(0, .22D, 0), .2D + progress * 3, .06D, true, 0, mint, fade * .7F, segments, definition.trail());
        } else if (kind.equals(OmenVfx.DIVE)) {
            mesh.ribbon(effect.history(partial), .22D, emerald, fade * .65F, definition.trail());
            mesh.ribbon(effect.history(partial).stream().map(point -> point.add(0, .12D, 0)).toList(), .04D, mint, fade, definition.trail());
        } else {
            mesh.ring(center.add(0, .3D, 0), Math.max(.1D, radius * (.15D + progress)), .05D, true, age * .01D, emerald, fade * .65F, segments, definition.trail());
            mesh.face(center.add(0, .8D, 0), Math.min(2, radius) * Math.sin(progress * Math.PI), -age * .02D, false, mint, fade * .6F, definition.hand());
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), emerald);
    }
    private static void banner(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, double angle, float age, float fade) {
        Vec3 side = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(1.2D), top = center.add(0, 5, 0), bottom = center.add(Math.sin(age * .06D) * .2D, 2, 0);
        mesh.line(center, top.add(0, .6D, 0), .035D, effect.packet.secondary(), fade * .7F, effect.definition.trail());
        mesh.quad(effect.definition.ring(), top.subtract(side), top.add(side), bottom.add(side), bottom.subtract(side), effect.packet.primary(), fade * .7F);
        mesh.face(top.add(0, .8D, 0), .45D, 0, false, effect.packet.secondary(), fade * .65F, effect.definition.hand());
    }
}
