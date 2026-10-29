package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.FireworkVfx;

public final class FireworkEffects {
    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/halloween_energy.png");
    private static final int[] COLORS = {0xFF5269, 0xF3F7FF, 0x509CFF};
    private FireworkEffects() {}
    public static boolean supports(Identifier kind) { return FireworkVfx.ALL.contains(kind); }
    private static void star(VfxMesh.Builder mesh, Vec3 center, double size, double turn, int color, float alpha, boolean vertical) {
        Vec3 previous = null;
        for (int point = 0; point <= 10; point++) {
            double angle = turn + point * Math.PI / 5, radius = size * (point % 2 == 0 ? 1 : .42D);
            Vec3 vertex = vertical ? center.add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0) : center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            if (previous != null) { mesh.line(previous, vertex, Math.max(.035D, size * .055D), color, alpha, ENERGY); }
            previous = vertex;
        }
    }
    private static void shell(VfxMesh.Builder mesh, Vec3 center, double radius, double progress, int color, float alpha, int spokes) {
        shell(mesh, center, radius, progress, color, alpha, spokes, false);
    }
    private static void shell(VfxMesh.Builder mesh, Vec3 center, double radius, double progress, int color, float alpha, int spokes, boolean illuminated) {
        for (int ray = 0; ray < spokes; ray++) {
            double height = 1 - 2D * (ray + .5D) / spokes, angle = ray * 2.3999632297D;
            Vec3 direction = new Vec3(Math.cos(angle) * Math.sqrt(1 - height * height), height, Math.sin(angle) * Math.sqrt(1 - height * height));
            Vec3 tip = center.add(direction.scale(radius * (.25D + progress * .75D))).add(0, -progress * progress * 1.2D, 0);
            Vec3 tail = center.add(direction.scale(radius * Math.max(.05D, progress - .18D))).add(0, -progress * progress * 1.2D, 0);
            mesh.beam(tail, tip, illuminated ? .085D : .065D, color, illuminated ? Math.min(1, alpha * 1.12F) : alpha, ENERGY);
            if (illuminated) { mesh.line(tail, tip, .016D, 0xFFF4D5, alpha * .32F, ENERGY); }
            if (ray % 4 == 0) { star(mesh, tip, .18D + radius * .03D, angle + progress, color, alpha, true); }
        }
    }
    private static void illumination(VfxMesh.Builder mesh, VfxEffect effect, Vec3 camera, float partial) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(effect.packet.entityId());
        if (!(entity instanceof net.ronm19.phantomism.entity.FireworkPhantom phantom) || !phantom.getUUID().equals(effect.packet.attached())
                || phantom.isHolidayDefeated() || phantom.isInvisible()) { return; }
        double age = effect.age(partial);
        float strength = FireworkFinaleLight.strength(age, effect.packet.duration());
        Vec3 body = phantom.getPosition(partial).add(0, .3D, 0);
        float near = camera.distanceToSqr(body) < 9 ? .2F : 1;
        mesh.face(body, .85D, 0, false, 0x6CAEFF, strength * .09F * near, ENERGY);
        mesh.face(body.add(0, .12D, 0), .48D, 0, false, 0xFFF1D1, strength * .08F * near, ENERGY);
        double yaw = Math.toRadians(phantom.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        for (int side : new int[]{-1, 1}) {
            Vec3 tip = body.add(right.scale(side * 1.9D)).add(0, Math.sin(age * .2D) * .22D, 0);
            for (int spark = 0; spark < 3; spark++) {
                double drift = (age / 36D + spark / 3D) % 1;
                Vec3 point = tip.subtract(forward.scale(.1D + drift * .8D)).add(0, -drift * .22D, 0);
                float alpha = strength * (float)Math.sin(drift * Math.PI) * .45F;
                mesh.face(point, .075D + .035D * (1 - drift), age * .012D, false, spark == 0 ? 0xFFD587 : spark == 1 ? 0xEAF6FF : 0x69B2FF, alpha, ENERGY);
            }
            mesh.face(tip, .18D, 0, false, 0x8AC4FF, strength * .18F, ENERGY);
        }
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration();
        float fade = (float)Math.clamp(Math.min(age / 3D, (duration - age) / 8D), 0, 1); Identifier kind = effect.packet.effect();
        int spokes = effect.quality <= 1 ? 12 : 24;
        if (kind.equals(FireworkVfx.FINALE)) {
            illumination(mesh, effect, camera, partial);
            double opening = Math.min(1, age / 18D);
            float fireworkFade = fade * (float)Math.clamp((240 - age) / 8D, 0, 1);
            mesh.ring(center.add(0, .15D, 0), 4.5D * opening, .12D, true, age * .006D, COLORS[2], fade * .5F, 24, ENERGY);
            for (int burst = 0; burst < 6; burst++) {
                double angle = burst * Math.PI / 3, phase = ((age + burst * 13) % 48) / 48D;
                Vec3 point = center.add(Math.cos(angle) * 7, 6 + burst % 3 * 2, Math.sin(angle) * 7);
                float alpha = fireworkFade * (float)Math.sin(phase * Math.PI);
                shell(mesh, point, 3.2D * opening, phase, COLORS[burst % 3], alpha, spokes, true);
                star(mesh, point, .45D, angle + age * .02D, 0xFFDC91, alpha, true);
                mesh.face(point, .55D, 0, false, 0xFFF2D3, alpha * FireworkFinaleLight.pulse(age) * .55F, ENERGY);
            }
        } else if (kind.equals(FireworkVfx.BOLT) || kind.equals(FireworkVfx.RUSH)) {
            var history = effect.history(partial);
            for (int point = 1; point < history.size(); point++) { mesh.beam(history.get(point - 1), history.get(point), kind.equals(FireworkVfx.RUSH) ? .28D : .08D, COLORS[point % 3], fade * .75F * point / Math.max(1, history.size()), ENERGY); }
        } else if (kind.equals(FireworkVfx.GUARD) || kind.equals(FireworkVfx.DEFEAT)) {
            for (int ring = 0; ring < 3; ring++) { mesh.ring(center.add(0, .3D + ring * .55D, 0), 1.2D + Math.sin(age * .08D + ring) * .1D, .095D, true, age * .04D, COLORS[ring], fade * .65F, 24, ENERGY); }
            for (int point = 0; point < 4; point++) { double angle = age * .045D + point * Math.PI / 2; star(mesh, center.add(Math.cos(angle) * 1.5D, .7D + point * .18D, Math.sin(angle) * 1.5D), .35D, age * .02D, point % 2 == 0 ? COLORS[1] : COLORS[2], fade, true); }
        } else if (kind.equals(FireworkVfx.FINAL_BURST)) {
            for (int burst = 0; burst < 3; burst++) {
                Vec3 point = center.add((burst - 1) * 7, burst == 1 ? 6 : 3, 0);
                shell(mesh, point, 6, Math.min(1, age / 20D), COLORS[burst], fade, spokes, true);
                star(mesh, point, 1.8D + age * .07D, age * .01D, 0xFFE0A0, fade * .85F, true);
                if (burst == 1) { mesh.face(point, .85D, 0, false, 0xFFF0C5, fade * .24F, ENERGY); }
            }
        } else {
            double radius = kind.equals(FireworkVfx.VOLLEY) ? 4 : Math.min(8, effect.packet.radius());
            shell(mesh, center.add(0, .8D, 0), radius, Math.min(1, age / duration * 1.3D), COLORS[Math.floorMod((int)effect.packet.seed(), 3)], fade, spokes);
            star(mesh, center.add(0, 1, 0), radius * Math.min(1, age / 10D), age * .02D, COLORS[1], fade * .8F, false);
        }
        return mesh.finish(List.of(), 1, ENERGY, COLORS[1]);
    }
}
