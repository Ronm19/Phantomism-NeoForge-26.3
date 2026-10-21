package net.ronm19.phantomism.client.vfx;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.DemonVfx;

public final class InfernalVfx {
    private static final net.minecraft.resources.Identifier SMOKE = net.minecraft.resources.Identifier.fromNamespaceAndPath(net.ronm19.phantomism.Phantomism.MOD_ID, "textures/vfx/demon_smoke.png");
    private InfernalVfx() {}
    public static boolean supports(VfxRegistry.Form form) { return form == VfxRegistry.Form.INFERNAL || form == VfxRegistry.Form.INFERNAL_BREATH || form == VfxRegistry.Form.INFERNAL_METEOR || form == VfxRegistry.Form.APOCALYPSE; }
    private static Entity attached(VfxEffect effect) {
        Minecraft client = Minecraft.getInstance(); if (client == null || client.level == null || effect.packet.entityId() < 0) { return null; }
        Entity entity = client.level.getEntity(effect.packet.entityId()); return entity != null && entity.getUUID().equals(effect.packet.attached()) ? entity : null;
    }
    public static net.minecraft.world.phys.AABB beamBounds(VfxEffect effect) {
        Entity source = attached(effect); Vec3 start = source == null ? effect.position() : source.getEyePosition();
        Vec3 direction = source != null && source.getControllingPassenger() instanceof Player rider ? rider.getViewVector(1).normalize().scale(18) : effect.packet.direction();
        Vec3 end = effect.definition.form() == VfxRegistry.Form.INFERNAL_BREATH ? start.add(direction) : effect.packet.position().add(effect.packet.direction());
        return new net.minecraft.world.phys.AABB(start, end).inflate(4.5D);
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var preset = effect.definition; float age = effect.age(partial), duration = effect.packet.duration();
        float fade = Math.min(1, age / 3) * Math.min(1, (duration - age) / (preset.form() == VfxRegistry.Form.APOCALYPSE ? 30 : 8));
        double progress = Math.clamp(age / duration, 0, 1), radius = effect.packet.radius(); Vec3 center = effect.interpolated(partial);
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, effect.packet.direction()); int red = effect.packet.primary(), fire = effect.packet.secondary(), segments = 16 + effect.quality * 8;
        var kind = effect.packet.effect();
        if (preset.form() == VfxRegistry.Form.INFERNAL_BREATH) {
            Entity source = attached(effect); Vec3 start = source == null ? center : source.getEyePosition(partial);
            Vec3 direction = source != null && source.getControllingPassenger() instanceof Player rider ? rider.getViewVector(partial).normalize() : effect.packet.direction().normalize();
            double reach = 18;
            if (source != null) { var hit = source.level().clip(new net.minecraft.world.level.ClipContext(start, start.add(direction.scale(18)), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, source)); reach = Math.max(.05D, Math.min(18, start.distanceTo(hit.getLocation()))); }
            Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); } Vec3 up = right.cross(direction).normalize();
            for (int strand = 0; strand < effect.quality + 2; strand++) {
                List<Vec3> path = new ArrayList<>();
                for (int point = 0; point <= 14; point++) {
                    double distance = point * reach / 14, angle = age * .35D + point * .8D + strand * Math.PI * 2 / (effect.quality + 2), spread = distance * .12D;
                    path.add(start.add(direction.scale(distance)).add(right.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)));
                }
                mesh.ribbon(path, .15D + strand * .035D, strand % 2 == 0 ? fire : red, fade * .7F, preset.trail());
            }
            for (int plume = 0; plume < 10 + effect.quality * 3; plume++) {
                double distance = ((plume * 1.31D + age * .6D) % reach), spin = plume * 2.4D + age * .08D;
                Vec3 point = start.add(direction.scale(distance)).add(right.scale(Math.cos(spin) * distance * .1D)).add(up.scale(Math.sin(spin) * distance * .08D));
                flame(mesh, camera, point, .3D + distance * .09D, .9D + distance * .16D, age + plume * 3, red, fade * .8F, preset);
                flame(mesh, camera, point, .13D + distance * .04D, .6D + distance * .1D, age + plume * 3 + 6, 0xFFE3AF, fade * .85F, preset);
            }
        } else if (preset.form() == VfxRegistry.Form.INFERNAL_METEOR) {
            mesh.ribbon(effect.history(partial), .45D, red, fade * .8F, preset.trail()); mesh.ribbon(effect.history(partial), .13D, 0xFFDAB1, fade, preset.trail());
            mesh.face(center, .42D, age * .2D, false, 0x180E1B, fade, preset.hand());
            mesh.face(center, .65D, -age * .1D, false, fire, fade * .9F, preset.hand());
            flame(mesh, camera, center.add(0, .25D, 0), .75D, 1.4D, age, red, fade, preset);
        } else if (preset.form() == VfxRegistry.Form.APOCALYPSE) {
            double charge = Math.min(1, age / 30), collapse = Math.min(1, (duration - age) / 30), reach = radius * charge;
            mesh.face(center.add(0, .08D, 0), Math.min(8, reach), age * -.004D, true, red, fade * .55F, preset.face());
            mesh.ring(center.add(0, .12D, 0), reach * collapse, .28D, true, age * .002D, red, fade * .8F, segments, preset.trail());
            mesh.ring(center.add(0, .15D, 0), reach * collapse, .045D, true, age * .002D, 0xFFC180, fade * .9F, segments, preset.trail());
            mesh.ring(center.add(0, .2D, 0), reach * collapse * .92D, .035D, true, -age * .004D, fire, fade * .65F, segments, preset.trail());
            for (int pillar = 0; pillar < 12; pillar++) {
                double angle = pillar * Math.PI / 6, distance = radius * (.63D + .13D * Math.sin(pillar * 3.7D));
                Vec3 point = center.add(Math.cos(angle) * distance, .12D, Math.sin(angle) * distance);
                double height = (6.5D + Math.sin(age * .14D + pillar) * 2D) * charge * collapse;
                flame(mesh, camera, point.add(0, height * .5D, 0), 1.2D, height, age + pillar * 7, red, fade * .8F, preset);
                flame(mesh, camera, point.add(0, height * .3D, 0), .6D, height * .6D, age + pillar * 7 + 6, fire, fade * .9F, preset);
                mesh.line(center.add(0, .1D, 0), point, .025D, red, fade * .4F, preset.trail());
            }
            float sigilFade = fade * Math.min(1, (duration - age) / 40);
            mesh.face(center.add(0, 8.5D * charge, 0), 7 * charge, -age * .006D, true, fire, sigilFade * .35F, preset.face());
            for (int wisp = 0; wisp < 8; wisp++) {
                double angle = wisp * Math.PI / 4 + age * .004D; Vec3 point = center.add(Math.cos(angle) * radius * .7D, 3 + (age * .04D + wisp) % 4, Math.sin(angle) * radius * .7D);
                mesh.face(point, 2.8D, age * .012D + wisp, false, 0x251C2B, fade * .4F, SMOKE);
            }
            for (int ember = 0; ember < effect.quality * 16; ember++) {
                double angle = ember * 2.39996D, distance = radius * Math.sqrt((ember + 1D) / (effect.quality * 16));
                Vec3 point = center.add(Math.cos(angle) * distance, (age * .08D + ember * 1.3D) % 7, Math.sin(angle) * distance);
                mesh.line(point, point.add(.04D, .22D, 0), .022D, fire, fade * .6F, preset.trail());
            }
        } else if (kind.equals(DemonVfx.DRAIN)) {
            Entity source = attached(effect); Vec3 start = source == null ? center : source.getEyePosition(partial), finish = effect.packet.position().add(effect.packet.direction());
            for (int strand = 0; strand < 3; strand++) {
                List<Vec3> points = new ArrayList<>(); Vec3 path = finish.subtract(start), side = path.cross(new Vec3(0, 1, 0)).normalize();
                for (int index = 0; index <= 16; index++) { double blend = index / 16D; points.add(start.add(path.scale(blend)).add(side.scale(Math.sin(index * .9D - age * .55D + strand * 2) * .35D * Math.sin(blend * Math.PI)))); }
                mesh.ribbon(points, strand == 0 ? .08D : .025D, strand == 0 ? red : 0x8AC9FF, fade, preset.trail());
            }
            mesh.face(finish, .55D, age * .2D, false, fire, fade, preset.hand());
        } else if (kind.equals(DemonVfx.DASH)) {
            mesh.ribbon(effect.history(partial), .55D, 0x150E1F, fade * .8F, preset.trail()); mesh.ribbon(effect.history(partial), .16D, red, fade, preset.trail());
            mesh.ribbon(effect.history(partial), .045D, fire, fade, preset.trail());
            for (int index = 0; index < Math.min(6, effect.history().size()); index++) { Vec3 point = effect.history().get(index); flame(mesh, camera, point, .3D, .8D, age + index * 5, red, fade * .6F, preset); }
        } else if (kind.equals(DemonVfx.BITE)) {
            Vec3 normal = camera.subtract(center).normalize(), right = normal.cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
            for (int slash = 0; slash < 3; slash++) {
                Vec3 start = center.subtract(right.scale(radius * .6D)).add(0, radius * (.2D + slash * .2D), 0), end = center.add(right.scale(radius * .6D)).add(0, -radius * (.3D - slash * .2D), 0);
                mesh.line(start, end, .12D, red, fade, preset.trail()); mesh.line(start, end, .03D, 0xFFD0A0, fade, preset.trail());
            }
        } else if (kind.equals(DemonVfx.AURA) || kind.equals(DemonVfx.FORM)) {
            mesh.face(center.add(0, .08D, 0), radius, -age * .01D, true, red, fade * .45F, preset.face());
            int count = kind.equals(DemonVfx.FORM) ? 8 : 4;
            for (int plume = 0; plume < count; plume++) {
                double angle = plume * Math.PI * 2 / count + age * .025D; Vec3 point = center.add(Math.cos(angle) * radius * .65D, .6D + .2D * Math.sin(age * .12D + plume), Math.sin(angle) * radius * .65D);
                flame(mesh, camera, point, .22D, .9D, age + plume * 4, plume % 3 == 0 ? fire : red, fade * .65F, preset);
            }
            if (kind.equals(DemonVfx.FORM)) { mesh.ring(center.add(0, .2D, 0), radius, .06D, true, age * .015D, fire, fade * .65F, segments, preset.trail()); }
        } else {
            double reach = radius * Math.min(1, progress * 1.6D);
            mesh.ring(center.add(0, .14D, 0), reach, .14D, true, 0, red, fade, segments, preset.trail());
            mesh.ring(center.add(0, .25D, 0), reach * .88D, .045D, true, 0, fire, fade * .85F, segments, preset.trail());
            for (int plume = 0; plume < 8 + effect.quality * 2; plume++) {
                double angle = plume * Math.PI * 2 / (8 + effect.quality * 2); Vec3 point = center.add(Math.cos(angle) * reach, .4D, Math.sin(angle) * reach);
                flame(mesh, camera, point, .25D + radius * .015D, kind.equals(DemonVfx.HOWL) ? .7D : 1.5D, age + plume * 2, plume % 2 == 0 ? fire : red, fade * .75F, preset);
            }
            if (kind.equals(DemonVfx.IMPACT)) { mesh.face(center.add(0, .03D, 0), radius * .8D, 0, true, 0x251321, fade * .6F, preset.face()); }
        }
        return mesh.finish(List.of(), fade, preset.silhouette(), red);
    }
    private static void flame(VfxMesh.Builder mesh, Vec3 camera, Vec3 center, double width, double height, float age, int color, float opacity, VfxRegistry.Definition preset) {
        Vec3 right = camera.subtract(center).cross(new Vec3(0, 1, 0)).normalize().scale(width); if (right.lengthSqr() < .000001D) { right = new Vec3(width, 0, 0); }
        Vec3 vertical = new Vec3(0, Math.max(.01D, height) * .5D, 0); int frame = Math.floorMod((int) (age / 3), 4);
        mesh.quadUv(preset.ring(), center.subtract(right).add(vertical), center.add(right).add(vertical), center.add(right).subtract(vertical), center.subtract(right).subtract(vertical), color, opacity, frame / 4F, (frame + 1) / 4F);
    }
}
