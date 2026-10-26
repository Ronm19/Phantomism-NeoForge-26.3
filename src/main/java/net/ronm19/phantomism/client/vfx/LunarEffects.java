package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.LunarVfx;

public final class LunarEffects {
    private LunarEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.LUNAR || form == VfxRegistry.Form.FULL_MOON_ASCENDANCY; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        var kind = effect.packet.effect(); Vec3 center = effect.interpolated(partial);
        double age = effect.age(partial), radius = effect.packet.radius(), opening = Math.min(1, age / 12D);
        float fade = (float) Math.max(0, Math.min(1, Math.min(age / 2D, (effect.packet.duration() - age) / 8D)));
        int blue = effect.packet.primary(), silver = effect.packet.secondary(), segments = effect.quality == 0 ? 12 : 24;
        if (kind.equals(LunarVfx.LANCE)) {
            Vec3 direction = effect.packet.direction().normalize(); mesh.beam(center.subtract(direction.scale(1.8D)), center, .13D, blue, fade, definition.trail());
            mesh.face(center, .26D, age * .2D, false, silver, fade, definition.hand());
        } else if (kind.equals(LunarVfx.SLASH) || kind.equals(LunarVfx.FANG) || kind.equals(LunarVfx.RUSH)) {
            Vec3 forward = effect.packet.direction().lengthSqr() > .001D ? effect.packet.direction().normalize() : new Vec3(0, 0, 1);
            Vec3 side = new Vec3(-forward.z, 0, forward.x); if (side.lengthSqr() < .001D) { side = new Vec3(1, 0, 0); } else { side = side.normalize(); }
            int count = effect.quality == 0 ? 10 : 20;
            for (int index = 0; index < count; index++) {
                double first = -.95D + index * 1.9D / count, last = -.95D + (index + 1) * 1.9D / count;
                Vec3 start = center.add(forward.scale(Math.cos(first) * radius * opening)).add(side.scale(Math.sin(first) * radius * opening));
                Vec3 end = center.add(forward.scale(Math.cos(last) * radius * opening)).add(side.scale(Math.sin(last) * radius * opening));
                mesh.beam(start, end, .12D + .12D * Math.sin((index + .5D) * Math.PI / count), index % 3 == 0 ? silver : blue, fade, definition.trail());
            }
            if (kind.equals(LunarVfx.RUSH)) { mesh.beam(center.subtract(forward.scale(5)), center, .3D, blue, fade, definition.trail()); }
        } else {
            boolean ultimate = kind.equals(LunarVfx.ASCENDANCY), veil = kind.equals(LunarVfx.VEIL);
            int rings = ultimate ? 3 : veil ? 2 : 1;
            for (int tier = 0; tier < rings; tier++) {
                mesh.ring(center.add(0, tier * .45D, 0), Math.max(.15D, radius * opening * (1 - tier * .2D)), .1D, true, age * .012D, tier % 2 == 0 ? blue : silver, fade, segments, definition.ring());
            }
            if (ultimate) {
                Vec3 moon = center.add(0, 5 + Math.sin(age * .02D) * .3D, 0);
                mesh.face(moon, 3.5D * opening, 0, false, silver, fade * .8F, definition.face());
                mesh.ring(moon, Math.max(.15D, 4 * opening), .08D, false, age * .008D, blue, fade, segments, definition.ring());
                if (age >= 135 && age < 190) {
                    for (int blade = 0; blade < 4; blade++) { double angle = blade * Math.PI * .5D + age * .04D; Vec3 edge = center.add(Math.cos(angle) * radius, 1, Math.sin(angle) * radius); mesh.beam(center, edge, .16D, silver, fade * .6F, definition.trail()); }
                }
                if (age >= 215) { mesh.beam(moon, center, .7D, silver, fade, definition.trail()); }
            }
            int stars = effect.quality == 0 ? 5 : 12;
            for (int index = 0; index < stars; index++) { double angle = index * 2.399D + age * .008D, spread = radius * ((index % 5 + 1) / 6D); Vec3 point = center.add(Math.cos(angle) * spread * opening, (index * .6D + age * .025D) % 4, Math.sin(angle) * spread * opening); mesh.face(point, .12D, angle, false, silver, fade * .7F, definition.hand()); }
        }
        return mesh.finish(List.of(), 1, definition.silhouette(), blue);
    }
}
