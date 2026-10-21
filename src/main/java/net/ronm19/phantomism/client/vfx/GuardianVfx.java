package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.SentinelVfx;

public final class GuardianVfx {
    private GuardianVfx() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.GUARDIAN || form == VfxRegistry.Form.BASTION; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.min(1, age / 5) * Math.min(1, (duration - age) / 18);
        double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius(); Vec3 center = effect.interpolated(partial);
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        int primary = effect.packet.primary(), ice = effect.packet.secondary(), segments = 16 + effect.quality * 8;
        var kind = effect.packet.effect();
        if (definition.form() == VfxRegistry.Form.BASTION) {
            double opening = Math.min(1, age / 28), closing = Math.min(1, (duration - age) / 24), reach = radius * opening * closing;
            mesh.face(center.add(0, .06D, 0), Math.min(5, reach), age * .008D, true, primary, fade * .65F, definition.face());
            mesh.ring(center.add(0, .12D, 0), reach, .075D, true, 0, ice, fade * .7F, segments, definition.trail());
            for (int panel = 0; panel < 8; panel++) {
                double angle = panel * Math.PI / 4; Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 position = center.add(radial.scale(reach * .85D)).add(0, 2 + Math.min(1, age / 20), 0);
                shield(mesh, position, radial.cross(new Vec3(0, 1, 0)).scale(1.2D), new Vec3(0, 1.8D * opening * closing, 0), primary, ice, fade * .65F, definition);
            }
            for (int band = 1; band <= 4; band++) {
                double elevation = band * Math.PI / 10;
                mesh.ring(center.add(0, Math.sin(elevation) * reach * .5D, 0), Math.cos(elevation) * reach, .03D, true, age * .001D, primary, fade * .35F, segments, definition.trail());
            }
            for (int rib = 0; rib < 8; rib++) {
                double angle = rib * Math.PI / 4;
                for (int arc = 0; arc < 8; arc++) {
                    double start = arc * Math.PI / 16, end = (arc + 1) * Math.PI / 16;
                    Vec3 first = center.add(Math.cos(angle) * Math.cos(start) * reach, Math.sin(start) * reach * .5D, Math.sin(angle) * Math.cos(start) * reach);
                    Vec3 second = center.add(Math.cos(angle) * Math.cos(end) * reach, Math.sin(end) * reach * .5D, Math.sin(angle) * Math.cos(end) * reach);
                    mesh.line(first, second, .025D, ice, fade * (.4F + .1F * (float) Math.sin(age * .08D)), definition.trail());
                }
            }
            if (effect.quality > 1) {
                for (int band = 0; band < 4; band++) {
                    double bottom = band * Math.PI / 8, top = (band + 1) * Math.PI / 8;
                    for (int sector = 0; sector < 16; sector++) {
                        double start = sector * Math.PI / 8, end = (sector + 1) * Math.PI / 8;
                        mesh.quad(definition.ring(), dome(center, reach, start, bottom), dome(center, reach, end, bottom), dome(center, reach, end, top), dome(center, reach, start, top), primary, fade * .3F);
                    }
                }
            }
        } else if (kind.equals(SentinelVfx.GUARD)) {
            for (int panel = 0; panel < 4; panel++) {
                double angle = panel * Math.PI / 2 + age * .012D; Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                shield(mesh, center.add(radial.scale(1.1D)).add(0, 1, 0), radial.cross(new Vec3(0, 1, 0)).scale(.7D), new Vec3(0, .9D, 0), primary, ice, fade * .75F, definition);
            }
            mesh.ring(center.add(0, .06D, 0), 1.6D, .05D, true, 0, primary, fade * .5F, segments, definition.trail());
        } else if (kind.equals(SentinelVfx.MARK)) {
            mesh.face(center.add(0, 2.8D, 0), .4D, 0, false, ice, fade, definition.face());
        } else if (kind.equals(SentinelVfx.PULSE)) {
            mesh.ring(center.add(0, .2D, 0), radius * progress, .14D, true, 0, ice, fade, segments, definition.trail());
            mesh.ring(center.add(0, .5D, 0), radius * Math.max(0, progress - .1D), .045D, true, 0, primary, fade * .65F, segments, definition.trail());
        } else if (kind.equals(SentinelVfx.CLEAVE)) {
            for (int section = 0; section < segments / 2; section++) {
                double start = -Math.PI * .7D + section * Math.PI * 1.4D / (segments / 2), end = start + Math.PI * 1.4D / (segments / 2);
                Vec3 forward = effect.packet.direction().multiply(1, 0, 1).normalize(), side = forward.cross(new Vec3(0, 1, 0));
                Vec3 first = forward.scale(Math.cos(start)).add(side.scale(Math.sin(start))), second = forward.scale(Math.cos(end)).add(side.scale(Math.sin(end)));
                mesh.quad(definition.trail(), center.add(0, .7D, 0).add(first.scale(radius * .7D)), center.add(0, .7D, 0).add(second.scale(radius * .7D)),
                        center.add(0, .9D, 0).add(second.scale(radius)), center.add(0, .9D, 0).add(first.scale(radius)), ice, fade);
            }
        } else {
            Vec3 right = camera.subtract(center).normalize().cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
            shield(mesh, center, right.scale(radius * (.4D + .6D * progress)), new Vec3(0, radius * (.4D + .6D * progress), 0), primary, ice, fade, definition);
            for (int shard = 0; shard < 8; shard++) {
                double angle = shard * Math.PI / 4; Vec3 delta = right.scale(Math.cos(angle)).add(0, Math.sin(angle), 0).scale(progress * radius * 1.4D);
                mesh.line(center.add(delta.scale(.8D)), center.add(delta), .035D, ice, fade, definition.trail());
            }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), primary);
    }
    private static Vec3 dome(Vec3 center, double radius, double angle, double elevation) { return center.add(Math.cos(angle) * Math.cos(elevation) * radius, Math.sin(elevation) * radius * .5D, Math.sin(angle) * Math.cos(elevation) * radius); }
    private static void shield(VfxMesh.Builder mesh, Vec3 center, Vec3 right, Vec3 up, int color, int edge, float opacity, VfxRegistry.Definition definition) {
        List<Vec3> points = List.of(center.subtract(right).add(up.scale(.7D)), center.add(up), center.add(right).add(up.scale(.7D)), center.add(right.scale(.8D)).subtract(up.scale(.3D)), center.subtract(up), center.subtract(right.scale(.8D)).subtract(up.scale(.3D)));
        mesh.quad(definition.ring(), center.subtract(right).add(up), center.add(right).add(up), center.add(right).subtract(up), center.subtract(right).subtract(up), color, opacity * .35F);
        for (int index = 0; index < points.size(); index++) { mesh.line(points.get(index), points.get((index + 1) % points.size()), .045D, edge, opacity, definition.trail()); }
        mesh.line(center.add(up.scale(.5D)), center.subtract(up.scale(.5D)), .035D, edge, opacity * .8F, definition.trail());
    }
}
