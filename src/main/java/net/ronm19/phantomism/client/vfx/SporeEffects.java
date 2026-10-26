package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.SporeVfx;

public final class SporeEffects {
    private SporeEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.SPORE || form == VfxRegistry.Form.FUNGAL_STORM; }

    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition;
        var kind = effect.packet.effect();
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        Vec3 center = effect.interpolated(partial);
        double age = effect.age(partial), duration = effect.packet.duration(), progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        float fade = (float)Math.max(0, Math.min(1, Math.min(age / 5D, (duration - age) / 12D)));
        int green = effect.packet.primary(), pale = effect.packet.secondary(), segments = effect.quality == 0 ? 20 : 32;
        boolean storm = kind.equals(SporeVfx.STORM), bloom = kind.equals(SporeVfx.BLOOM);
        if (storm || bloom) {
            double opening = Math.min(1, age / (storm ? 40D : 15D)), closing = Math.min(1, (duration - age) / 20D);
            double extent = radius * opening * closing;
            mesh.face(center.add(0, .12D, 0), Math.max(.1D, extent), age * .004D, true, green, fade * .35F, definition.face());
            for (int tier = 0; tier < (storm ? 5 : 2); tier++) {
                double height = .2D + tier * (storm ? 1.25D : .45D);
                double pulse = .85D + Math.sin(age * .08D + tier) * .08D;
                mesh.ring(center.add(0, height, 0), Math.max(.1D, extent * pulse * (1 - tier * .12D)), .07D,
                        true, age * .018D * (tier % 2 == 0 ? 1 : -1), tier % 2 == 0 ? green : pale, fade * .75F, segments, definition.ring());
            }
            for (int index = 0; index < (effect.quality == 0 ? 5 : 10); index++) {
                double angle = index * 2.399963D + effect.packet.seed() % 37;
                double reach = extent * (.3D + (index % 4) * .16D);
                Vec3 growth = center.add(Math.cos(angle) * reach, .65D * opening, Math.sin(angle) * reach);
                mesh.face(growth, .6D * opening, 0, false, 0xDCCC9B, fade * .8F, definition.hand());
            }
        } else if (kind.equals(SporeVfx.BREATH) || kind.equals(SporeVfx.DIVE)) {
            Vec3 direction = effect.packet.direction().lengthSqr() > .001D ? effect.packet.direction().normalize() : new Vec3(0, 0, 1);
            mesh.beam(center.subtract(direction.scale(2)), center, .2D, green, fade * .8F, definition.trail());
            mesh.ring(center, .4D + progress * .25D, .08D, false, age * .1D, pale, fade, segments, definition.ring());
        } else {
            mesh.ring(center.add(0, .2D, 0), Math.max(.1D, radius * (.4D + progress * .6D)), .06D,
                    true, age * .02D, green, fade * .6F, segments, definition.ring());
        }
        int motes = effect.quality == 0 ? 8 : 16;
        for (int index = 0; index < motes; index++) {
            double angle = index * 2.399963D + age * .025D;
            double reach = radius * (.2D + (index % 5) * .14D);
            double rise = (age * .03D + index * .173D) % 1;
            Vec3 point = center.add(Math.cos(angle) * reach, rise * (storm ? 6 : 1.8D), Math.sin(angle) * reach);
            mesh.face(point, .05D + (index % 3) * .018D, age * .01D, false, index % 2 == 0 ? green : pale, fade * .6F, definition.trail());
        }
        return mesh.finish(List.of(), 1, definition.silhouette(), green);
    }
}
