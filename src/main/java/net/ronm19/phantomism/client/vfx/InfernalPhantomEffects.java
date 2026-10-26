package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.InfernalPhantomVfx;

public final class InfernalPhantomEffects {
    private InfernalPhantomEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.INFERNAL_PHANTOM || form == VfxRegistry.Form.HELL_ASCENDANT; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        var kind = effect.packet.effect(); Vec3 center = effect.interpolated(partial); double age = effect.age(partial), radius = effect.packet.radius();
        float fade = (float)Math.max(0, Math.min(1, Math.min(age / 2D, (effect.packet.duration() - age) / 8D))); int fire = effect.packet.primary(), gold = effect.packet.secondary(), segments = effect.quality == 0 ? 12 : 24;
        if (kind.equals(InfernalPhantomVfx.BOLT)) {
            Vec3 direction = effect.packet.direction().normalize(); mesh.beam(center.subtract(direction.scale(1.4D)), center, .15D, fire, fade, definition.trail());
            mesh.face(center, .3D, age * .2D, false, gold, fade, definition.hand());
        } else if (kind.equals(InfernalPhantomVfx.RUSH)) {
            for (int index = 0; index < 10; index++) { double angle = index * .22D + age * .03D; mesh.beam(center.add(Math.cos(angle) * radius, Math.sin(angle), Math.sin(angle) * radius), center.add(Math.cos(angle + .22D) * radius, Math.sin(angle + .22D), Math.sin(angle + .22D) * radius), .14D, index % 3 == 0 ? gold : fire, fade, definition.trail()); }
            mesh.beam(center.subtract(effect.packet.direction().scale(4)), center, .35D, fire, fade, definition.trail());
        } else {
            boolean ultimate = kind.equals(InfernalPhantomVfx.ASCENDANT), ash = kind.equals(InfernalPhantomVfx.ASH), guard = kind.equals(InfernalPhantomVfx.GUARD);
            double opening = Math.min(1, age / (ultimate ? 30D : 8D)); int rings = ultimate ? 4 : guard ? 3 : 2;
            for (int tier = 0; tier < rings; tier++) { mesh.ring(center.add(0, guard ? tier - 1 : tier * .4D, 0), Math.max(.15D, radius * opening * (1 - tier * .14D)), .12D, true, age * .018D * (tier % 2 == 0 ? 1 : -1), ash ? 0x423239 : tier % 2 == 0 ? fire : gold, fade, segments, definition.ring()); }
            mesh.face(center.add(0, .15D, 0), Math.max(.1D, radius * opening), age * .004D, true, fire, fade * .28F, definition.face());
            int sparks = effect.quality == 0 ? 5 : 12;
            for (int index = 0; index < sparks; index++) { double angle = index * 2.399D + age * .01D, spread = radius * ((index % 5 + 1) / 6D); Vec3 point = center.add(Math.cos(angle) * spread * opening, (age * .06D + index * .5D) % 5, Math.sin(angle) * spread * opening); mesh.face(point, ash ? .7D : .14D, angle, false, ash ? 0x362B31 : gold, fade * .7F, definition.hand()); }
            if (kind.equals(InfernalPhantomVfx.ERUPTION) || ultimate && age > 110 && age < 195) {
                for (int index = 0; index < (ultimate ? 5 : 3); index++) { double angle = index * 2.4D; Vec3 base = center.add(Math.cos(angle) * radius * .65D, 0, Math.sin(angle) * radius * .65D); mesh.beam(base, base.add(0, 5 * opening, 0), .7D, fire, fade, definition.trail()); mesh.face(base.add(0, 2.5D, 0), 1, angle, false, gold, fade * .6F, definition.hand()); }
            }
        }
        return mesh.finish(List.of(), 1, definition.silhouette(), fire);
    }
}
