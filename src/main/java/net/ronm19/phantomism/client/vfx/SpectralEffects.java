package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.vfx.SpectralVfx;

public final class SpectralEffects {
    private static final Identifier VEIL = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/vfx/spectral_veil.png");
    private SpectralEffects() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.SPECTRAL || form == VfxRegistry.Form.SPECTRAL_AFTERIMAGE || form == VfxRegistry.Form.SPECTRAL_LANCE || form == VfxRegistry.Form.ETHEREAL_PLANE; }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var definition = effect.definition; var kind = effect.packet.effect();
        float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.min(1, age / 4) * Math.min(1, (duration - age) / (kind.equals(SpectralVfx.PLANE) ? 24 : 8));
        double progress = Math.clamp(age / duration, 0, 1); Vec3 center = effect.interpolated(partial);
        int cyan = effect.packet.primary(), violet = effect.packet.secondary();
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction());
        List<VfxEffect.Pose> echoes = List.of();
        if (definition.form() == VfxRegistry.Form.SPECTRAL_LANCE) {
            Vec3 direction = effect.packet.direction().normalize(); if (direction.lengthSqr() < .01D) { direction = new Vec3(0, 0, 1); }
            Vec3 tip = center.add(direction.scale(.9D)), tail = center.subtract(direction.scale(1.3D));
            mesh.line(tail, tip, .11D, violet, fade * .9F, definition.trail());
            mesh.line(center.subtract(direction.scale(.7D)), tip, .04D, 0xE9FFFF, fade, definition.trail());
            Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
            Vec3 side = right.scale(.24D);
            mesh.quad(definition.face(), tip, center.add(side), center.subtract(direction.scale(.25D)), center.subtract(side), cyan, fade);
            mesh.ribbon(effect.history(partial), .12D, cyan, fade * .6F, definition.trail());
            for (int strand = 0; strand < 2; strand++) { wisp(mesh, tail, direction.scale(-1.5D), .12D, age + strand * 4, violet, fade * .45F, definition.trail()); }
        } else if (kind.equals(SpectralVfx.MARK)) {
            Vec3 glyph = center.add(0, 2.3D + Math.sin(age * .08D) * .12D, 0);
            mesh.face(glyph, .4D, Math.sin(age * .04D) * .2D, false, cyan, fade * .85F, definition.face());
            wisp(mesh, glyph.add(-.3D, 0, 0), new Vec3(.6D, 0, 0), .06D, age, violet, fade * .6F, definition.trail());
        } else if (kind.equals(SpectralVfx.STRIKE) || kind.equals(SpectralVfx.IMPACT)) {
            double radius = effect.packet.radius() * (.45D + progress * .7D);
            for (int slash = 0; slash < (kind.equals(SpectralVfx.STRIKE) ? 3 : 2); slash++) {
                arc(mesh, center, radius, slash * .45D + progress * 1.3D, cyan, violet, fade, definition.trail());
            }
            mesh.face(center, .45D * (1 - progress), age * .04D, false, 0xE7F6FF, fade * .75F, definition.hand());
        } else if (kind.equals(SpectralVfx.GUARD)) {
            double breath = 1 + Math.sin(age * .12D) * .025D;
            for (int layer = 0; layer < 2; layer++) {
                double radius = (1.3D + layer * .15D) * breath;
                arc(mesh, center.add(0, .6D, 0), radius, age * .012D + layer * .25D, cyan, violet, fade * .55F, definition.trail());
                mesh.face(center.add(layer == 0 ? -1.1D : 1.1D, .6D, .1D), .7D, layer == 0 ? -.5D : .5D, false, cyan, fade * .16F, VEIL);
            }
        } else if (definition.form() == VfxRegistry.Form.ETHEREAL_PLANE) {
            double charge = Math.clamp(age / 24, 0, 1), closing = Math.clamp((age - duration + 24) / 24, 0, 1);
            double radius = (1.1D + charge * 1.7D) * (1 - closing * .7D);
            float local = camera.distanceToSqr(center) < 4 ? .35F : 1;
            if (age < 28) { mesh.ring(center.add(0, .15D, 0), 1 + age * .11D, .035D, true, age * .015D, cyan, fade * (1 - age / 28) * .6F, 24, definition.trail()); }
            for (int wing = 0; wing < 2; wing++) {
                Vec3 edge = center.add(wing == 0 ? -1.3D : 1.3D, .55D, .1D);
                mesh.ring(edge, .75D + charge * .4D, .018D, false, age * (wing == 0 ? .014D : -.014D), violet, fade * .45F * local, 24, definition.trail());
                mesh.face(edge, radius * .8D, Math.sin(age * .025D + wing) * .15D, false, cyan, fade * .26F * local, VEIL);
            }
            int symbols = effect.quality > 1 ? 6 : 4;
            for (int index = 0; index < symbols; index++) {
                double angle = age * .017D + index * Math.PI * 2 / symbols;
                Vec3 symbol = center.add(Math.cos(angle) * radius, .8D + Math.sin(angle * 2) * .45D, Math.sin(angle) * radius);
                mesh.face(symbol, .16D + .04D * Math.sin(age * .09D + index), -angle, false, index % 2 == 0 ? cyan : violet, fade * .65F * local, definition.face());
                wisp(mesh, symbol, new Vec3(-Math.sin(angle), .2D, Math.cos(angle)).scale(.8D), .035D, age + index, cyan, fade * .4F * local, definition.trail());
            }
            mesh.ribbon(effect.history(partial), .17D, violet, fade * .6F, definition.trail());
            for (int index = 0; index < 3; index++) { wisp(mesh, center.add((index - 1) * .8D, .2D, .4D), new Vec3((index - 1) * .3D, .3D, -1.4D), .05D, age + index * 3, cyan, fade * .5F, definition.trail()); }
            echoes = effect.echoes();
        } else if (definition.form() == VfxRegistry.Form.SPECTRAL_AFTERIMAGE) {
            mesh.ribbon(effect.history(partial), .14D, cyan, fade * .65F, definition.trail());
            for (int strand = 0; strand < 3; strand++) { wisp(mesh, center.add((strand - 1) * .5D, .2D, .2D), new Vec3((strand - 1) * .2D, .15D, 1.6D), .045D, age + strand * 3, strand == 1 ? cyan : violet, fade * .65F, definition.trail()); }
            echoes = effect.echoes();
            if (kind.equals(SpectralVfx.SLIP)) {
                float yaw = echoes.isEmpty() ? 0 : echoes.getFirst().yaw();
                echoes = List.of(new VfxEffect.Pose(effect.packet.position(), yaw, 0), new VfxEffect.Pose(effect.packet.position().add(effect.packet.direction().scale(.45D)), yaw, 0));
            }
        } else {
            double radius = kind.equals(SpectralVfx.FOLD) ? 2.5D * (1 - progress) : .5D + progress * 1.5D;
            for (int index = 0; index < 4; index++) {
                double angle = index * Math.PI * .5D + age * .025D;
                Vec3 point = center.add(Math.cos(angle) * radius, .3D + progress * .7D, Math.sin(angle) * radius);
                wisp(mesh, point, new Vec3(-Math.sin(angle), .6D, Math.cos(angle)), .055D, age + index * 3, cyan, fade * .65F, definition.trail());
            }
        }
        float echoFade = fade * (camera.distanceToSqr(center) < 4 ? .4F : 1);
        return mesh.finish(echoes, echoFade, definition.silhouette(), 0xA6DFFF);
    }
    private static void arc(VfxMesh.Builder mesh, Vec3 center, double radius, double turn, int cyan, int violet, float fade, Identifier texture) {
        Vec3 previous = null;
        for (int segment = 0; segment <= 12; segment++) {
            double angle = -.9D + segment * 1.8D / 12 + turn;
            Vec3 point = center.add(Math.cos(angle) * radius, Math.sin(angle) * radius * .55D, Math.sin(angle * .7D) * .18D);
            if (previous != null) { mesh.line(previous, point, .035D + .025D * Math.sin(segment * Math.PI / 13), segment % 3 == 0 ? violet : cyan, fade * .8F, texture); }
            previous = point;
        }
    }
    private static void wisp(VfxMesh.Builder mesh, Vec3 start, Vec3 direction, double width, double phase, int color, float opacity, Identifier texture) {
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize(); if (side.lengthSqr() < .01D) { side = new Vec3(1, 0, 0); }
        List<Vec3> points = new ArrayList<>();
        for (int segment = 0; segment <= 8; segment++) {
            double progress = segment / 8D;
            points.add(start.add(direction.scale(progress)).add(side.scale(Math.sin(progress * 5 + phase * .12D) * .12D * progress)).add(0, Math.sin(progress * 4 - phase * .07D) * .12D, 0));
        }
        mesh.ribbon(points, width, color, opacity, texture);
    }
}
