package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.MirageVfx;

public final class MirageEffects {
    private MirageEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.MIRAGE || form == VfxRegistry.Form.HALL_OF_MIRRORS; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect(); var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration(), progress = Math.clamp(age / duration, 0, 1);
        float fade = (float) Math.max(0, Math.min(1, Math.min(age / 6D, (duration - age) / 12D))); int lavender = effect.packet.primary(), cyan = effect.packet.secondary();
        double radius = effect.packet.radius(); int segments = effect.quality == 0 ? 32 : 48;
        if (kind.equals(MirageVfx.HALL) || kind.equals(MirageVfx.FIELD)) {
            double grow = Math.min(1, age / (kind.equals(MirageVfx.HALL) ? 36 : 20)), collapse = Math.min(1, (duration - age) / 30D), extent = radius * grow * collapse;
            mesh.face(center.add(0, .08D, 0), Math.max(.1D, extent), age * .002D, true, lavender, fade * .32F, definition.face());
            for (int ring = 0; ring < 3; ring++) { mesh.ring(center.add(0, .12D + ring * .13D, 0), Math.max(.1D, extent * (1 - ring * .12D)), .10D + ring * .03D, true, age * .002D, ring % 2 == 0 ? cyan : lavender, fade * .65F, segments, definition.trail()); }
            int panels = kind.equals(MirageVfx.HALL) ? effect.quality == 0 ? 6 : 10 : 6;
            for (int index = 0; index < panels; index++) {
                double angle = index * Math.PI * 2 / panels + Math.sin(age * .006D) * .08D;
                Vec3 point = center.add(Math.cos(angle) * extent * .76D, 1.8D + Math.sin(age * .025D + index) * .5D, Math.sin(angle) * extent * .76D);
                Vec3 side = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(kind.equals(MirageVfx.HALL) ? 1.2D : .75D), up = new Vec3(0, 1.8D * grow * collapse, 0);
                mesh.quad(definition.ring(), point.subtract(side).subtract(up), point.add(side).subtract(up), point.add(side).add(up), point.subtract(side).add(up), index % 2 == 0 ? lavender : cyan, fade * .36F);
                if (kind.equals(MirageVfx.HALL)) { mesh.face(point.add(0, 2.4D, 0), .38D, angle + age * .01D, false, cyan, fade * .6F, definition.hand()); }
            }
            if (kind.equals(MirageVfx.HALL)) { mesh.ring(center.add(0, 7D * grow * collapse, 0), Math.max(.1D, extent * .55D), .15D, true, -age * .003D, lavender, fade * .55F, segments, definition.trail()); }
        } else if (kind.equals(MirageVfx.BOLT)) {
            mesh.ribbon(effect.history(partial), .32D, lavender, fade * .8F, definition.trail()); mesh.ribbon(effect.history(partial), .08D, cyan, fade, definition.trail());
            mesh.face(center, .35D, age * .15D, false, cyan, fade, definition.hand());
        } else if (kind.equals(MirageVfx.FAKE_BOLT)) {
            mesh.ribbon(List.of(center, center.add(effect.packet.direction().scale(progress))), .10D, lavender, fade * .5F, definition.trail());
        } else if (kind.equals(MirageVfx.STRIKE) || kind.equals(MirageVfx.RESCUE) || kind.equals(MirageVfx.GUARD)) {
            mesh.ribbon(effect.history(partial), .4D, cyan, fade * .6F, definition.trail());
            mesh.ring(center.add(0, .6D, 0), Math.max(.1D, radius * (.5D + progress)), .14D, kind.equals(MirageVfx.STRIKE), age * .04D, lavender, fade, segments, definition.trail());
            mesh.face(center.add(0, .6D, 0), radius * .55D, age * .02D, false, cyan, fade * .45F, definition.ring());
        } else {
            double expansion = kind.equals(MirageVfx.FORM) ? 1 - progress : progress;
            mesh.ring(center.add(0, .3D, 0), Math.max(.1D, radius * expansion), .16D, true, age * .03D, cyan, fade, segments, definition.trail());
            int shards = kind.equals(MirageVfx.FINALE) || kind.equals(MirageVfx.CONVERGE) ? 20 : 8;
            for (int index = 0; index < shards; index++) {
                double angle = index * Math.PI * 2 / shards + effect.packet.seed() % 91 * .03D;
                Vec3 point = center.add(Math.cos(angle) * radius * expansion, .6D + Math.sin(progress * Math.PI) * (1 + index % 3), Math.sin(angle) * radius * expansion);
                mesh.face(point, .12D + index % 3 * .08D, angle + age * .08D, false, index % 2 == 0 ? lavender : cyan, fade * .8F, definition.hand());
            }
            if (kind.equals(MirageVfx.FINALE)) { mesh.face(center.add(0, .15D, 0), radius, -progress * .3D, true, lavender, fade * .35F, definition.face()); }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), lavender);
    }
}
