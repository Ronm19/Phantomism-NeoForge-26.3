package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.IllagerVfx;

public final class IllagerEffects {
    private IllagerEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.ILLAGER || form == VfxRegistry.Form.CONQUEST; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        var kind = effect.packet.effect(); Vec3 center = effect.interpolated(partial); double age = effect.age(partial), radius = effect.packet.radius();
        float fade = (float)Math.max(0, Math.min(1, Math.min(age / 3D, (effect.packet.duration() - age) / 8D)));
        int emerald = effect.packet.primary(), crimson = effect.packet.secondary(), segments = effect.quality == 0 ? 16 : 28;
        if (kind.equals(IllagerVfx.CROSSBOW)) {
            Vec3 forward = effect.packet.direction().lengthSqr() > .01D ? effect.packet.direction().normalize() : new Vec3(0, 0, 1);
            Vec3 side = forward.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < .01D) { side = new Vec3(1, 0, 0); } side = side.normalize();
            Vec3 middle = center.add(forward.scale(.8D)), left = middle.add(side.scale(1.2D)), right = middle.subtract(side.scale(1.2D)), stock = center.subtract(forward.scale(.8D));
            mesh.beam(stock, middle.add(forward.scale(.6D)), .07D, 0xD0CAD8, fade, definition.trail());
            mesh.beam(left, middle.add(forward.scale(.4D)), .07D, emerald, fade, definition.trail()); mesh.beam(right, middle.add(forward.scale(.4D)), .07D, emerald, fade, definition.trail());
            mesh.beam(left, stock, .025D, emerald, fade, definition.trail()); mesh.beam(right, stock, .025D, emerald, fade, definition.trail());
        } else if (kind.equals(IllagerVfx.WRATH) || kind.equals(IllagerVfx.EXECUTION)) {
            for (int index = 0; index < 12; index++) {
                double first = -.6D + index * .18D + age * .025D, next = first + .18D;
                Vec3 start = center.add(Math.cos(first) * radius, .3D + Math.sin(first) * 1.2D, Math.sin(first) * radius);
                Vec3 end = center.add(Math.cos(next) * radius, .3D + Math.sin(next) * 1.2D, Math.sin(next) * radius);
                mesh.beam(start, end, .12D, index % 3 == 0 ? 0xD0CAD8 : crimson, fade, definition.trail());
            }
            mesh.ring(center.add(0, .1D, 0), Math.max(.2D, radius * Math.min(1, age / 10D)), .09D, true, age * .02D, emerald, fade * .7F, segments, definition.ring());
        } else if (kind.equals(IllagerVfx.BOLT)) {
            Vec3 direction = effect.packet.direction().normalize(); mesh.beam(center.subtract(direction.scale(1.7D)), center, .11D, emerald, fade, definition.trail());
            mesh.face(center, .16D, age * .1D, false, 0xDBFFEC, fade, definition.hand());
        } else {
            boolean ultimate = kind.equals(IllagerVfx.CONQUEST); double opening = Math.min(1, age / (ultimate ? 25D : 8D));
            mesh.face(center.add(0, .12D, 0), Math.max(.1D, radius * opening), age * .004D, true, emerald, fade * .3F, definition.face());
            for (int tier = 0; tier < (ultimate ? 4 : 2); tier++) { mesh.ring(center.add(0, tier * 1.5D + .2D, 0), Math.max(.1D, radius * opening * (1 - tier * .12D)), .08D, true, age * .02D * (tier % 2 == 0 ? 1 : -1), tier % 2 == 0 ? emerald : crimson, fade, segments, definition.ring()); }
            if (ultimate) {
                for (int index = 0; index < 6; index++) {
                    double angle = index * Math.PI / 3; Vec3 point = center.add(Math.cos(angle) * radius * .7D, 0, Math.sin(angle) * radius * .7D);
                    mesh.beam(point, point.add(0, 6 * opening, 0), .08D, 0xD0CAD8, fade, definition.trail());
                    mesh.face(point.add(0, 4 * opening, 0), 1.2D, age * .01D, false, crimson, fade * .8F, definition.hand());
                }
            }
        }
        return mesh.finish(List.of(), 1, definition.silhouette(), emerald);
    }
}
