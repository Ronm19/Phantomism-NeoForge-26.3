package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.ShadowVfx;

public final class ShadowEffects {
    private ShadowEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.SHADOW || form == VfxRegistry.Form.NIGHTFALL; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect(); var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration(), progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius();
        float fade = (float) Math.max(0, Math.min(1, Math.min(age / 4D, (duration - age) / 10D))); int violet = effect.packet.primary(), silver = effect.packet.secondary(), segments = effect.quality == 0 ? 24 : 40;
        if (kind.equals(ShadowVfx.NIGHTFALL)) {
            double opening = Math.min(1, age / 36D), closing = Math.min(1, (duration - age) / 24D), extent = radius * opening * closing;
            mesh.face(center.add(0, 6, 0), Math.max(.1D, extent * .58D), age * .002D, false, violet, fade * .8F, definition.ring());
            mesh.face(center.add(0, .08D, 0), Math.max(.1D, extent * .7D), -age * .002D, true, violet, fade * .18F, definition.face());
            mesh.ring(center.add(0, .1D, 0), Math.max(.1D, extent * .7D), .1D, true, age * .006D, violet, fade * .6F, segments, definition.trail());
            int blades = effect.quality == 0 ? 6 : 10;
            for (int index = 0; index < blades; index++) {
                double angle = index * Math.PI * 2 / blades + age * .022D, speed = Math.sin(age * .08D + index);
                Vec3 point = center.add(Math.cos(angle) * extent * .6D, 1.5D + speed, Math.sin(angle) * extent * .6D);
                mesh.ribbon(List.of(point.add(-Math.cos(angle) * 1.3D, .5D, -Math.sin(angle) * 1.3D), point), .09D, index % 3 == 0 ? silver : violet, fade * .7F, definition.trail());
                mesh.face(point, .5D, angle + age * .05D, false, violet, fade * .5F, definition.ring());
            }
            mesh.ribbon(effect.history(partial), .3D, violet, fade * .65F, definition.trail());
        } else if (kind.equals(ShadowVfx.BOLT) || kind.equals(ShadowVfx.DIVE)) {
            mesh.ribbon(effect.history(partial), kind.equals(ShadowVfx.DIVE) ? .55D : .22D, violet, fade, definition.trail());
            mesh.ribbon(effect.history(partial), .06D, silver, fade * .8F, definition.trail()); mesh.face(center, .4D, age * .15D, false, violet, fade, definition.hand());
        } else if (kind.equals(ShadowVfx.MARK)) {
            mesh.face(center, radius, age * .03D, false, violet, fade * .65F, definition.face());
        } else if (kind.equals(ShadowVfx.STEP)) {
            mesh.ribbon(List.of(center, center.add(effect.packet.direction().scale(progress))), .38D * (1 - progress), violet, fade * .6F, definition.trail());
            mesh.face(center.add(effect.packet.direction().scale(progress)), 1.4D * (1 - progress), age * .1D, false, violet, fade * .6F, definition.ring());
        } else if (kind.equals(ShadowVfx.STRIKE)) {
            mesh.face(center.add(0, .6D, 0), radius * (.6D + progress * .4D), progress * 2, false, violet, fade, definition.ring());
            mesh.ring(center.add(0, .3D, 0), Math.max(.1D, progress * radius * .6D), .08D, true, age * .01D, silver, fade * .65F, segments, definition.trail());
        } else if (kind.equals(ShadowVfx.RECON)) {
            mesh.ring(center.add(0, .1D, 0), Math.max(.1D, progress * radius), .08D, true, 0, violet, fade * .6F, segments, definition.trail());
        } else if (kind.equals(ShadowVfx.GUARD)) {
            mesh.face(center.add(0, .6D, 0), radius, -age * .05D, false, violet, fade * .6F, definition.ring()); mesh.ribbon(effect.history(partial), .25D, silver, fade * .5F, definition.trail());
        } else {
            boolean finale = kind.equals(ShadowVfx.FINALE); double expansion = kind.equals(ShadowVfx.VANISH) ? 1 - progress : progress;
            mesh.ring(center.add(0, .15D, 0), Math.max(.1D, expansion * radius), finale ? .22D : .12D, true, age * .02D, violet, fade * .8F, segments, definition.trail());
            for (int index = 0; index < (finale ? 16 : 6); index++) { double angle = index * Math.PI * 2 / (finale ? 16 : 6); Vec3 point = center.add(Math.cos(angle) * radius * expansion, .4D + Math.sin(progress * Math.PI) * (index % 3 + 1), Math.sin(angle) * radius * expansion); mesh.face(point, .12D + index % 3 * .08D, angle + progress * 2, false, index % 4 == 0 ? silver : violet, fade * .8F, definition.hand()); }
            if (finale) { mesh.face(center.add(0, .2D, 0), radius, age * .015D, true, violet, fade * .3F, definition.face()); }
        }
        return mesh.finish(List.of(), fade, definition.silhouette(), violet);
    }
}
