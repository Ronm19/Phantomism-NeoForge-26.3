package net.ronm19.phantomism.client.vfx;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.StPatricksPhantom;
import net.ronm19.phantomism.vfx.StPatricksVfx;

public final class StPatricksEffects {
    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/halloween_energy.png");
    private static final Identifier CLOVER = Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/st_patricks_clover.png");
    private static final int GREEN = 0x70E88A, GOLD = 0xF7D07E, WHITE = 0xF1F9DD;
    private static final int[] RAINBOW = {0xE6A48C, 0xE8C080, 0xDCE78A, 0x8DDDAB, 0x8FCBD9, 0xAEADE0};
    private StPatricksEffects() {}
    public static boolean supports(Identifier effect) { return StPatricksVfx.ALL.contains(effect); }
    public static float light(StPatricksPhantom phantom, float partial) {
        VfxEffect effect = VfxManager.find(phantom.getUUID(), StPatricksVfx.SKY);
        if (effect == null || effect.packet.entityId() != phantom.getId() || !effect.packet.attached().equals(phantom.getUUID()) || phantom.isHolidayDefeated()) { return 0; }
        double age = effect.age(partial), duration = effect.packet.duration();
        double build = Math.clamp(age / 18D, 0, 1), fade = Math.clamp((duration - age) / 24D, 0, 1);
        return (float)(build * build * (3 - 2 * build) * fade * fade * (3 - 2 * fade) * .78D);
    }
    private static void clover(VfxMesh.Builder mesh, Vec3 center, Vec3 camera, double size, double turn, int color, float alpha) {
        mesh.face(center, size, turn, false, color, alpha, CLOVER);
    }
    private static void rainbow(VfxMesh.Builder mesh, Vec3 center, double radius, double age, float alpha) {
        for (int band = 0; band < 6; band++) {
            Vec3 previous = null;
            for (int point = 0; point <= 8; point++) {
                double angle = point * Math.PI / 8, r = radius + band * .09D;
                Vec3 current = center.add(Math.cos(angle) * r, Math.sin(angle) * r * .65D + age * .005D, 0);
                if (previous != null) { mesh.line(previous, current, .035D, RAINBOW[band], alpha, ENERGY); }
                previous = current;
            }
        }
    }
    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        VfxMesh.Builder mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        Vec3 center = effect.interpolated(partial); double age = effect.age(partial), duration = effect.packet.duration();
        float fade = (float)Math.clamp(Math.min(age / 5D, (duration - age) / 12D), 0, 1);
        Identifier kind = effect.packet.effect();
        if (kind.equals(StPatricksVfx.SKY)) {
            float opening = (float)Math.clamp(age / 22D, 0, 1);
            float action = fade * (float)Math.clamp((240 - age) / 10D, 0, 1);
            mesh.ring(center, 4.8D * opening, .07D, true, age * .002D, GOLD, fade * .4F, 24, ENERGY);
            mesh.ring(center.add(0, .12D, 0), 3.6D * opening, .06D, true, -age * .003D, GREEN, fade * .4F, 24, ENERGY);
            int count = effect.quality <= 1 ? 10 : 16;
            for (int index = 0; index < count; index++) {
                double angle = index * Math.PI * 2 / count + age * .004D;
                double phase = (age / 100D + index / (double)count) % 1;
                Vec3 point = center.add(Math.cos(angle) * (4 + phase * 7), 2 + phase * 9, Math.sin(angle) * (4 + phase * 7));
                clover(mesh, point, camera, .45D + phase * .3D, Math.sin(age * .02D + index) * .15D, index % 3 == 0 ? GOLD : GREEN, action * (float)Math.sin(phase * Math.PI) * .8F);
                if (index % 4 == 0) { mesh.line(point.add(0, -.6D, 0), point, .025D, WHITE, action * .25F, ENERGY); }
            }
            rainbow(mesh, center.add(0, 3, 0), 6 * opening, age, action * .22F);
            mesh.face(center.add(0, .4D, 0), .9D, 0, false, GREEN, fade * .055F * (camera.distanceToSqr(center) < 9 ? .2F : 1), ENERGY);
        } else if (kind.equals(StPatricksVfx.BURST) || kind.equals(StPatricksVfx.DASH)) {
            List<Vec3> history = effect.history(partial);
            for (int point = 1; point < history.size(); point++) {
                Vec3 start = history.get(point - 1), end = history.get(point);
                mesh.line(start, end, kind.equals(StPatricksVfx.DASH) ? .1D : .035D, GREEN, fade * .6F, ENERGY);
                if (kind.equals(StPatricksVfx.DASH)) {
                    mesh.line(start.add(0, .12D, 0), end.add(0, .12D, 0), .035D, RAINBOW[point % 6], fade * .3F, ENERGY);
                    if (point % 4 == 0) { clover(mesh, start, camera, .3D, age * .01D, GOLD, fade * .5F); }
                }
            }
        } else if (kind.equals(StPatricksVfx.AURA) || kind.equals(StPatricksVfx.TRICK) || kind.equals(StPatricksVfx.DEFEAT)) {
            double radius = kind.equals(StPatricksVfx.AURA) ? 2.8D : kind.equals(StPatricksVfx.DEFEAT) ? 1.3D : 4;
            mesh.ring(center.add(0, .25D, 0), radius, .08D, true, age * .01D, GOLD, fade * .55F, 24, ENERGY);
            int count = effect.quality <= 1 ? 4 : 8;
            for (int index = 0; index < count; index++) {
                double angle = age * .016D + index * Math.PI * 2 / count;
                clover(mesh, center.add(Math.cos(angle) * radius, .6D + Math.sin(angle * 2) * .3D, Math.sin(angle) * radius), camera, .4D, .12D * Math.sin(age * .02D), index % 2 == 0 ? GREEN : GOLD, fade * .7F);
            }
        } else if (kind.equals(StPatricksVfx.WAVE) || kind.equals(StPatricksVfx.FINAL)) {
            double progress = Math.min(1, age / duration), radius = effect.packet.radius() * (.15D + progress * .6D);
            mesh.ring(center.add(0, .1D, 0), radius, .09D, true, age * .005D, GOLD, fade * .6F, 32, ENERGY);
            for (int index = 0; index < 12; index++) {
                double angle = index * Math.PI / 6;
                clover(mesh, center.add(Math.cos(angle) * radius, .5D + Math.sin(progress * Math.PI) * 2, Math.sin(angle) * radius), camera, .5D, angle * .1D, index % 3 == 0 ? WHITE : GREEN, fade * .8F);
            }
            if (kind.equals(StPatricksVfx.FINAL)) { clover(mesh, center.add(0, 5, 0), camera, 2, 0, GOLD, fade * .7F); }
        } else {
            int count = kind.equals(StPatricksVfx.FAVOR) ? 3 : 6;
            for (int index = 0; index < count; index++) {
                double angle = index * Math.PI * 2 / count + age * .015D;
                clover(mesh, center.add(Math.cos(angle) * .7D, .4D + age * .025D, Math.sin(angle) * .7D), camera, .3D, 0, index % 2 == 0 ? GOLD : GREEN, fade * .8F);
            }
        }
        return mesh.finish(List.of(), 1, ENERGY, WHITE);
    }
}
