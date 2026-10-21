package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.RiftVfx;

public final class SpatialVfx {
    public static final int MAJOR_FRACTURES = 6;
    private SpatialVfx() {}
    public static boolean supports(VfxRegistry.Form form) { return switch (form) { case SPATIAL_PROJECTILE, TEAR, PORTAL, SHARDS, SPATIAL_LOCK, SPATIAL_ULTIMATE, SPATIAL_AFTERIMAGE, SPATIAL_IMPACT, SPATIAL_ANCHOR, SPATIAL_SENSE, SPATIAL_TRANSIT, SPATIAL_SLASH -> true; default -> false; }; }
    public static int requiredUltimateBudget(int quality) { return quality <= 1 ? 2048 : quality == 2 ? 3072 : 3584; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        if (effect.definition.form() == VfxRegistry.Form.SPATIAL_ULTIMATE && limit < requiredUltimateBudget(effect.quality)) { return List.of(); }
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), effect.definition.width(), effect.packet.direction());
        var preset = effect.definition; Vec3 center = effect.interpolated(partial); float age = effect.age(partial), duration = effect.packet.duration();
        float fade = (float) (RiftFractures.smooth(age / 3) * RiftFractures.smooth((duration - age) / 6));
        double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius(); long seed = effect.packet.seed();
        switch (preset.form()) {
            case SPATIAL_ULTIMATE -> ultimate(mesh, effect, center, age, fade);
            case PORTAL, TEAR -> {
                double opening = RiftFractures.smooth(age / (preset.form() == VfxRegistry.Form.PORTAL ? 12 : 4)) * RiftFractures.smooth((duration - age) / 10);
                RiftFractures.tear(mesh, effect, center.add(0, radius * 1.5D, 0), facing(effect, camera, center), radius * opening, radius * 1.7D * (.2D + .8D * opening), age, fade, seed, .15D);
                if (preset.form() == VfxRegistry.Form.PORTAL) {
                    Vec3 normal = facing(effect, camera, center); Vec3 right = normal.cross(new Vec3(0, 1, 0)).normalize();
                    double travel = age * .04D % 1;
                    for (int index = 0; index < 2; index++) { Vec3 point = center.add(0, .65D + index * .6D, 0).add(normal.scale((travel - .5D) * .65D));
                        mesh.beam(point.add(right.scale(-.22D)), point.add(normal.scale(.24D)), .024D, 0xA2F8FF, fade * .7F, preset.trail()); mesh.beam(point.add(right.scale(.22D)), point.add(normal.scale(.24D)), .024D, 0xA2F8FF, fade * .7F, preset.trail()); }
                }
            }
            case SPATIAL_PROJECTILE -> {
                List<Vec3> history = effect.history(partial);
                Vec3 axis = effect.packet.direction().normalize(), ribbonSide = axis.cross(new Vec3(0, 1, 0)).normalize(); if (ribbonSide.lengthSqr() < .01D) { ribbonSide = new Vec3(1, 0, 0); } Vec3 ribbonUp = axis.cross(ribbonSide).normalize();
                for (int strand = 0; strand < 2; strand++) {
                    List<Vec3> twisted = new ArrayList<>();
                    for (int index = 0; index < history.size(); index++) { double twist = age * .3D + index * .85D + strand * Math.PI; twisted.add(history.get(index).add(ribbonSide.scale(Math.cos(twist) * .1D)).add(ribbonUp.scale(Math.sin(twist) * .1D))); }
                    mesh.ribbon(twisted, strand == 0 ? .08D : .035D, strand == 0 ? 0xAD3DFF : 0x6EF8FF, fade, preset.trail());
                }
                mesh.ribbon(history, .035D, 0xFF54D7, fade, preset.trail()); mesh.face(center, .2D, age * .4D, false, 0xFF43D1, fade, preset.ring()); mesh.face(center, .09D, -age * .35D, false, 0xE2FFFF, fade, preset.ring());
                RiftFractures.shards(mesh, effect, center, .27D, .15D, age * 2, fade, 3 + effect.quality, seed);
            }
            case SPATIAL_TRANSIT -> transit(mesh, effect, center, camera, age, fade, progress);
            case SPATIAL_AFTERIMAGE -> {
                boolean dash = effect.packet.effect().equals(RiftVfx.DASH); List<Vec3> history = effect.history(partial);
                mesh.ribbon(history, dash ? .27D : .18D, 0x7E32D0, fade * .65F, preset.trail()); mesh.ribbon(history, .05D, 0x81F7FF, fade, preset.trail());
                for (int cut = 0; cut < 2 + effect.quality; cut++) { double tilt = cut * .55D + age * .03D; Vec3 start = center.add(Math.cos(tilt) * -.55D, -.2D, Math.sin(tilt) * -.55D); mesh.beam(start, center.add(Math.cos(tilt) * .65D, .85D, Math.sin(tilt) * .65D), .025D, cut % 2 == 0 ? 0xF752D8 : 0x96FAFF, fade, preset.trail()); }
                RiftFractures.shards(mesh, effect, center, .7D, .4D, age, fade, 3 + effect.quality, seed);
            }
            case SPATIAL_ANCHOR, SHARDS -> {
                double pulse = .9D + Math.sin(age * .08D) * .09D;
                mesh.face(center.add(0, .12D, 0), radius * .85D, -age * .012D, true, 0xD652E9, fade * .65F, preset.hand());
                RiftFractures.tear(mesh, effect, center.add(0, .75D, 0), camera.subtract(center).multiply(1, 0, 1), radius * .35D * pulse, radius * .65D, age, fade * .8F, seed, 0);
                for (int index = 0; index < 3; index++) { double angle = age * .03D + index * Math.PI * 2 / 3; Vec3 vertex = center.add(Math.cos(angle) * radius, .35D, Math.sin(angle) * radius); mesh.beam(vertex, center.add(0, 1.5D, 0), .02D, 0xF65AE3, fade * .7F, preset.trail()); }
            }
            case SPATIAL_SENSE -> {
                for (int index = 0; index < 3; index++) { double angle = index * Math.PI * 2 / 3 + age * .07D; Vec3 point = center.add(Math.cos(angle) * radius, 1.9D + Math.sin(age * .1D + index) * .1D, Math.sin(angle) * radius); mesh.face(point, .13D, -angle, false, index == 0 ? 0xA6EDFF : 0xBD69F2, fade * .7F, preset.hand()); }
            }
            case SPATIAL_LOCK -> {
                Vec3 base = center.add(0, -.05D, 0); double width = radius * .8D;
                for (int side = 0; side < 4; side++) {
                    double angle = side * Math.PI / 2 + Math.PI / 4; Vec3 corner = base.add(Math.cos(angle) * width, 0, Math.sin(angle) * width), next = base.add(Math.cos(angle + Math.PI / 2) * width, 0, Math.sin(angle + Math.PI / 2) * width);
                    mesh.beam(corner, corner.add(0, 2.5D, 0), .055D, 0xDE3BE6, fade, preset.trail()); mesh.beam(corner, next, .035D, 0x79F0FF, fade, preset.trail()); mesh.beam(corner.add(0, 2.5D, 0), next.add(0, 2.5D, 0), .035D, 0x79F0FF, fade, preset.trail());
                    double slide = age * .035D % 1; Vec3 vertex = corner.add(0, slide * 2.5D, 0); mesh.face(vertex, .14D, age * .1D, false, 0xCFFFFF, fade, preset.ring());
                    mesh.beam(corner.add(0, .3D + slide, 0), next.add(0, 1.4D + slide * .3D, 0), .015D, 0xB77AFF, fade * .8F, preset.trail());
                    if (effect.quality > 1) { float frame = (int) (age * .4F) % RiftFractures.SURFACE_FRAMES; mesh.quadUv(FLOW, corner.add(0, .4D, 0), next.add(0, .6D, 0), next.add(0, 2.2D, 0), corner.add(0, 2, 0), 0x9D75D3, fade * .28F, (frame + .015F) / RiftFractures.SURFACE_FRAMES, (frame + .985F) / RiftFractures.SURFACE_FRAMES); }
                }
            }
            case SPATIAL_IMPACT -> {
                boolean collapse = effect.packet.effect().equals(RiftVfx.COLLAPSE); double expansion = Math.pow(progress, .55D);
                RiftFractures.jaggedWave(mesh, effect, center, radius * expansion, age, fade * (collapse ? 1 : .8F), collapse ? 24 + effect.quality * 4 : 12 + effect.quality * 4);
                if (collapse) {
                    RiftFractures.jaggedWave(mesh, effect, center.add(0, .35D, 0), radius * Math.pow(Math.max(0, progress - .14D), .65D), age, fade * .65F, 24);
                    for (int index = 0; index < 6; index++) { Vec3 point = majorCenter(center, radius, index); Vec3 inward = point.lerp(center.add(0, .2D, 0), Math.min(1, progress * 2)); mesh.beam(inward, inward.lerp(center.add(0, .2D, 0), .35D), .075D * (1 - progress), 0xA9F9FF, fade, preset.trail()); }
                    RiftFractures.shards(mesh, effect, center.add(0, 1.5D, 0), radius * expansion, 3.5D * (1 - progress), age * 2, fade, effect.quality * 8, seed);
                    for (int index = 0; index < 12; index++) { double angle = index * Math.PI / 6; Vec3 start = center.add(Math.cos(angle) * radius * expansion, .1D, Math.sin(angle) * radius * expansion); Vec3 tip = start.add(Math.cos(angle) * .7D, (1 - progress) * (1.5D + index % 3), Math.sin(angle) * .7D); mesh.line(start, tip, .08D * (1 - progress), 0xEF67FC, fade * .8F, preset.trail()); mesh.face(tip, .14D * (1 - progress), angle + age * .15D, false, 0xC6FFFF, fade, preset.ring()); }
                } else { burst(mesh, effect, center, radius, progress, fade, effect.quality * 5); }
            }
            case SPATIAL_SLASH -> {
                for (int slash = 0; slash < 3; slash++) { Vec3 start = center.add(-radius * .7D + slash * .25D, -radius * .5D, -.15D * slash); Vec3 end = center.add(radius * .55D + slash * .2D, radius * .7D, .1D * slash); mesh.beam(start, end, .15D * (1 - progress), 0xAC30DB, fade, preset.trail()); mesh.beam(start, end, .025D, 0xAEFFFF, fade, preset.trail()); } burst(mesh, effect, center, radius, progress, fade, effect.quality * 4);
            }
            default -> { }
        }
        return mesh.finish(preset.form() == VfxRegistry.Form.SPATIAL_AFTERIMAGE ? effect.echoes() : List.of(), fade, preset.silhouette(), 0xBD72F4);
    }
    private static final net.minecraft.resources.Identifier FLOW = RiftFractures.FLOW;
    private static Vec3 facing(VfxEffect effect, Vec3 camera, Vec3 center) { return effect.packet.direction().lengthSqr() > .01D ? effect.packet.direction().normalize() : camera.subtract(center).normalize(); }
    public static Vec3 majorCenter(Vec3 center, double radius, int index) { double angle = index * Math.PI * 2 / MAJOR_FRACTURES + (index % 2 == 0 ? -.045D : .045D); double distance = radius * (.66D + index % 3 * .035D); return center.add(Math.cos(angle) * distance, 3.5D + index % 3 * .85D, Math.sin(angle) * distance); }
    private static void ultimate(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, float age, float fade) {
        double radius = effect.packet.radius(), duration = effect.packet.duration(); double charge = RiftFractures.smooth(age / 26), collapse = RiftFractures.smooth((age - (duration - 28)) / 28);
        mesh.face(center.add(0, .13D, 0), 4 + charge * 3, age * .007D, true, 0xB239D5, fade * .5F, effect.definition.hand());
        RiftFractures.jaggedWave(mesh, effect, center, 4 + charge * 3, age, fade * .42F, 12);
        for (int index = 0; index < MAJOR_FRACTURES; index++) {
            Vec3 point = majorCenter(center, radius, index); long seed = effect.packet.seed() + index * 7919L;
            double opening = RiftFractures.smooth((age - index * .9D) / 24) * (1 - RiftFractures.smooth((age - (duration - 28 + index * 1.1D)) / (28 - index * 1.1D)));
            double width = (2.15D + RiftFractures.noise(seed, 9) * .55D) * opening, height = (3.8D + RiftFractures.noise(seed, 10) * 1.1D) * (.07D + .93D * opening);
            RiftFractures.tear(mesh, effect, point, center.subtract(point).multiply(1, 0, 1), width, height, age, fade, seed, collapse);
            Vec3 next = majorCenter(center, radius, (index + 1) % MAJOR_FRACTURES);
            if (charge > .15D) { RiftFractures.link(mesh, effect, point.add(0, -.6D, 0), next.add(0, -.6D, 0), age, fade * (float) charge * (float) (1 - collapse * .7D), index, false); }
            if (effect.quality > 1 && index % 2 == 0) { RiftFractures.link(mesh, effect, point, majorCenter(center, radius, (index + 2) % MAJOR_FRACTURES), age, fade * .55F * (float) charge, index, true); }
            if (age < 28) { Vec3 gather = center.add(0, 1.4D, 0); RiftFractures.link(mesh, effect, point.lerp(gather, 1 - charge), gather, age, fade * (float) (1 - charge), index, true); }
            if (collapse > 0) { Vec3 end = point.lerp(center.add(0, 1.5D, 0), collapse); mesh.beam(point, end, .035D + collapse * .04D, 0xAEF9FF, fade * .8F, effect.definition.trail()); }
        }
        RiftFractures.shards(mesh, effect, center.add(0, 2.5D, 0), radius * (.42D + collapse * .2D), 1, age, fade * .65F, effect.quality * 4, effect.packet.seed());
        if (age < 28) { RiftFractures.shards(mesh, effect, center.add(0, 1.3D, 0), .6D + charge * .4D, .45D, age * 2, fade, 6, effect.packet.seed() + 101); }
    }
    private static void transit(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, Vec3 camera, float age, float fade, double progress) {
        var id = effect.packet.effect(); boolean arriving = id.equals(RiftVfx.RESCUE_IN) || id.equals(RiftVfx.THROW_IN) || id.equals(RiftVfx.TRAVEL_IN) || id.equals(RiftVfx.STEP_IN);
        boolean rescue = id.equals(RiftVfx.RESCUE_IN) || id.equals(RiftVfx.RESCUE_OUT), throwing = id.equals(RiftVfx.THROW_IN) || id.equals(RiftVfx.THROW_OUT);
        double opening = Math.sin(Math.PI * progress); Vec3 origin = center.add(0, 1, 0); double width = throwing ? .45D : rescue ? .78D : .62D;
        RiftFractures.tear(mesh, effect, origin, facing(effect, camera, center), width * opening, throwing ? 1.55D : 1.3D, age, fade, effect.packet.seed(), throwing ? .8D : .35D);
        if (!arriving) {
            Vec3 destination = effect.packet.position().add(effect.packet.direction()).add(0, 1, 0);
            RiftFractures.link(mesh, effect, origin, destination, age * 2, fade * .7F, 2, false);
            int strands = rescue ? 5 : throwing ? 3 : 2;
            for (int index = 0; index < strands; index++) { double angle = index * Math.PI * 2 / strands; Vec3 boundary = origin.add(Math.cos(angle) * width, Math.sin(angle) * 1.2D, 0); Vec3 compressed = boundary.lerp(origin, RiftFractures.smooth(progress * 1.5D)); mesh.beam(compressed, compressed.lerp(origin, .4D), .025D, rescue ? 0x9DF7FF : 0xFA43D8, fade, effect.definition.trail()); }
        } else { burst(mesh, effect, origin, rescue ? 1.2D : 1, progress, fade, (rescue ? 5 : 4) * effect.quality); }
    }
    private static void burst(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, double radius, double progress, float fade, int count) {
        for (int index = 0; index < count; index++) { double angle = index * Math.PI * 2 / Math.max(1, count); Vec3 direction = new Vec3(Math.cos(angle), Math.sin(index * 2.1D) * .6D, Math.sin(angle)); double distance = radius * (.2D + progress * 1.2D); mesh.beam(center.add(direction.scale(distance * .55D)), center.add(direction.scale(distance)), .025D * (1 - progress), index % 2 == 0 ? 0xAFFBFF : 0xE14BEF, fade, effect.definition.trail()); mesh.face(center.add(direction.scale(distance)), .04D * (1 - progress), ageRotation(progress, index), false, 0xACCEFF, fade, effect.definition.ring()); }
    }
    private static double ageRotation(double progress, int index) { return progress * Math.PI + index; }
}
