package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;

public final class RiftFractures {
    public static final Identifier FLOW = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/vfx/rift_flow.png");
    public static final int SURFACE_FRAMES = 8;
    private RiftFractures() {}
    public static int segments(int quality) { return 6 + Math.clamp(quality, 1, 3) * 2; }
    public static double smooth(double value) { double blend = Math.clamp(value, 0, 1); return blend * blend * (3 - 2 * blend); }
    public static double noise(long seed, int index) { long mixed = (seed + index * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L; return ((mixed ^ mixed >>> 27) & 65535) / 65535D; }
    public static List<Vec3> shape(int quality, long seed, double rotation) {
        List<Vec3> points = new ArrayList<>(); int segments = segments(quality);
        for (int index = 0; index < segments; index++) {
            double angle = index * Math.PI * 2 / segments + rotation; double reach = .7D + noise(seed, index) * .27D;
            if (index == segments / 4 || index == segments * 3 / 4) { reach = .99D; }
            points.add(new Vec3(Math.cos(angle) * reach, Math.sin(angle) * reach, 0));
        } return points;
    }
    public static void tear(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, Vec3 normal, double width, double height, float age, float opacity, long seed, double stress) {
        if (width < .012D || height < .04D || opacity <= .001F) { return; }
        normal = normal.normalize(); if (normal.lengthSqr() < .01D) { normal = new Vec3(0, 0, 1); }
        Vec3 right = normal.cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
        Vec3 vertical = right.cross(normal).normalize(); double tilt = (noise(seed, 71) - .5D) * .5D;
        Vec3 horizontal = right.scale(Math.cos(tilt)).add(vertical.scale(Math.sin(tilt))).scale(width);
        Vec3 up = vertical.scale(Math.cos(tilt)).subtract(right.scale(Math.sin(tilt))).scale(height);
        List<Vec3> shape = shape(effect.quality, seed, 0); int frame = Math.floorMod((int) (age * .4F) + (int) (seed & 7), SURFACE_FRAMES);
        float top = (frame + .015F) / SURFACE_FRAMES, bottom = (frame + .985F) / SURFACE_FRAMES;
        mesh.fan(effect.definition.face(), center.subtract(normal.scale(.045D)), horizontal.scale(.94D), up.scale(.94D), shape, 0xFFFFFF, opacity * .87F, top, bottom);
        mesh.fan(FLOW, center.add(normal.scale(.04D)), horizontal.scale(.88D), up.scale(.91D), shape, 0xFFFFFF, opacity * (.48F + (float) stress * .22F), top, bottom);
        List<Vec3> boundary = shape.stream().map(point -> center.add(horizontal.scale(point.x)).add(up.scale(point.y))).toList();
        for (int index = 0; index < boundary.size(); index++) {
            Vec3 first = boundary.get(index), second = boundary.get((index + 1) % boundary.size());
            mesh.line(first, second, .28D + stress * .06D, 0x241036, opacity * .8F, effect.definition.trail());
            mesh.line(first, second, .22D + stress * .07D, 0x9D3FDB, opacity * .32F, effect.definition.trail());
            mesh.line(first, second, .105D + stress * .04D, index % 3 == 0 ? 0x9567FF : 0xFF36DA, opacity, effect.definition.trail());
            if (effect.quality > 1) { mesh.line(first.add(normal.scale(.012D)), second.add(normal.scale(.012D)), .032D, 0xB9FFFF, opacity, effect.definition.trail()); }
            if (index % 3 == 0) {
                double flow = (age * .06D + noise(seed, index + 80)) % 1; Vec3 point = first.lerp(second, flow);
                mesh.line(point, first.lerp(second, Math.min(1, flow + .22D)), .055D, 0xCEFFFF, opacity, effect.definition.trail());
                Vec3 outward = first.subtract(center).scale(.16D + stress * .1D), joint = first.add(outward);
                mesh.line(first, joint, .04D, 0xF679FF, opacity * .85F, effect.definition.trail());
                mesh.line(joint, joint.add(outward.scale(.65D)).add(normal.scale(.16D)), .022D, 0x9BF6FF, opacity * .8F, effect.definition.trail());
            }
        }
        for (int fold = 0; fold < 2; fold++) {
            List<Vec3> path = new ArrayList<>();
            for (int point = 0; point <= 5; point++) { double blend = point / 5D; path.add(center.add(up.scale((blend - .5D) * 1.4D)).add(horizontal.scale(Math.sin(blend * Math.PI * 2 + age * .055D + fold * Math.PI) * .3D)).add(normal.scale(.07D + fold * .035D))); }
            mesh.ribbon(path, .022D, fold == 0 ? 0x8B5CFF : 0x4FEEFF, opacity * .45F, effect.definition.trail());
        }
        shards(mesh, effect, center, width * (1.15D + stress * .8D), height * .7D, age, opacity, 3 + effect.quality, seed);
    }
    public static void shards(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, double radius, double height, float age, float opacity, int count, long seed) {
        for (int index = 0; index < count; index++) {
            double angle = index * Math.PI * 2 / Math.max(1, count) + age * (.02D + noise(seed, index) * .015D), distance = radius * (.8D + noise(seed, index + 11) * .3D);
            Vec3 point = center.add(Math.cos(angle) * distance, Math.sin(angle * 1.7D + index) * height, Math.sin(angle) * distance);
            mesh.face(point, .14D + noise(seed, index + 22) * .23D, angle + age * .09D, false, index % 3 == 0 ? 0xD5FFFF : index % 2 == 0 ? 0xAA8AFF : 0xFF66DF, opacity, effect.definition.ring());
        }
    }
    public static void jaggedWave(VfxMesh.Builder mesh, VfxEffect effect, Vec3 center, double radius, float age, float opacity, int segments) {
        List<Vec3> path = new ArrayList<>();
        for (int point = 0; point <= segments; point++) { double angle = point * Math.PI * 2 / segments; double jag = point % 2 == 0 ? 1 : .88D; path.add(center.add(Math.cos(angle) * radius * jag, .15D + Math.sin(angle * 3 + age * .03D) * .2D, Math.sin(angle) * radius * jag)); }
        for (int index = 1; index < path.size(); index++) { mesh.line(path.get(index - 1), path.get(index), .23D, 0x8F39E4, opacity * .65F, effect.definition.trail()); mesh.line(path.get(index - 1), path.get(index), .065D, 0xB7FFFF, opacity, effect.definition.trail()); }
    }
    public static void link(VfxMesh.Builder mesh, VfxEffect effect, Vec3 first, Vec3 second, float age, float opacity, int seed, boolean arc) {
        List<Vec3> path = new ArrayList<>(); int samples = arc ? 6 : 5 + effect.quality;
        for (int index = 0; index <= samples; index++) {
            double blend = index / (double) samples; double bend = Math.sin(blend * Math.PI);
            Vec3 point = first.lerp(second, blend).add(0, Math.sin(blend * Math.PI * 2 + age * .07D + seed) * bend * (arc ? .7D : .35D), Math.sin(blend * 5 + age * .08D) * bend * .22D);
            if (arc && index > 0 && index < samples) { point = point.add(((index + seed) % 2 == 0 ? .2D : -.2D), 0, 0); } path.add(point);
        }
        float pulse = .7F + (float) Math.sin(age * .09D + seed) * .2F;
        mesh.ribbon(path, arc ? .035D : .15D, arc ? 0xC2FFFF : 0xE84AF7, opacity * pulse, effect.definition.trail());
        if (!arc) { mesh.ribbon(path, .045D, 0xBFFFFF, opacity, effect.definition.trail()); }
    }
}
