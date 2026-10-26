package net.ronm19.phantomism.client.vfx;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.SolarPhantom;
import net.ronm19.phantomism.vfx.SolarVfx;
import net.ronm19.phantomism.vfx.VfxPacket;

public final class TotalEclipseEffects {
    public static final Identifier DISC = texture("disc"), MOTE = texture("mote"), SUN = texture("sun"), MOON = texture("moon");
    private TotalEclipseEffects() {}
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath("phantomism", "textures/vfx/solar_eclipse_" + name + ".png"); }
    public static boolean supports(VfxPacket packet) { return packet.effect().equals(SolarVfx.ECLIPSE) || packet.effect().equals(SolarVfx.ECLIPSE_RESTORATION); }

    public static final class State {
        private SolarPhantom solar;
        private AbstractPhantom lunar;
        private UUID solarOwner, lunarOwner;
        private int missing;

        public Vec3 solar(VfxPacket packet, float partial) { return solar == null ? packet.position().add(-3, 1, 0) : solar.getPosition(partial).add(0, .6D, 0); }
        public Vec3 lunar(VfxPacket packet, float partial) { return lunar == null ? packet.position().add(3, 1, 0) : lunar.getPosition(partial).add(0, .6D, 0); }
        private static UUID owner(AbstractPhantom source) { return source.getOwnerReference() == null ? null : source.getOwnerReference().getUUID(); }
        public boolean tick(ClientLevel level, VfxPacket packet, int age) {
            if (solar == null) { for (Entity candidate : level.entitiesForRendering()) { if (candidate instanceof SolarPhantom source && candidate.getUUID().equals(packet.caster())) { solar = source; solarOwner = owner(source); break; } } }
            if (lunar == null && level.getEntity(packet.entityId()) instanceof AbstractPhantom source && source.getUUID().equals(packet.attached())) {
                lunar = source; lunarOwner = owner(source);
            }
            if (solar == null || lunar == null) { return ++missing <= 8; }
            if (!solar.isAlive() || !lunar.isAlive() || solar.isRemoved() || lunar.isRemoved() || solar.level() != level || lunar.level() != level
                    || !java.util.Objects.equals(owner(solar), solarOwner) || !java.util.Objects.equals(owner(lunar), lunarOwner)
                    || solar.getOrder() == AbstractPhantom.Order.STAY || lunar.getOrder() == AbstractPhantom.Order.STAY
                    || solar.getOrder() == AbstractPhantom.Order.RETURN_HOME || lunar.getOrder() == AbstractPhantom.Order.RETURN_HOME) { return false; }
            if (age == 1) { sound(level, packet.position(), SoundEvents.BEACON_AMBIENT, .35F, .7F); sound(level, packet.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .5F, .75F); }
            if (age == 2) { sound(level, packet.position(), SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.25F); }
            if (age == 5 && packet.direction().x > 0) { sound(level, packet.position(), SoundEvents.AMETHYST_BLOCK_CHIME, .45F, 1.35F); }
            if (age == 9 && packet.direction().y > 0) { sound(level, packet.position(), SoundEvents.BEACON_ACTIVATE, .4F, 1.25F); }
            if (age == 49) { sound(level, packet.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .45F, 1.5F); }
            return true;
        }
        private static void sound(ClientLevel level, Vec3 point, SoundEvent sound, float volume, float pitch) { level.playLocalSound(point.x, point.y, point.z, sound, SoundSource.NEUTRAL, volume, pitch, false); }
    }

    public static List<VfxMesh.Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        var mesh = new VfxMesh.Builder(camera, Math.max(0, limit), 1, Vec3.ZERO);
        double age = effect.age(partial), duration = effect.packet.duration(), radius = effect.packet.radius();
        float fade = (float) Math.clamp(Math.min(age / 3D, (duration - age) / 12D), 0, 1);
        int quality = Math.max(1, Math.min(effect.quality, camera.distanceToSqr(effect.position()) > 36 * 36 ? 1 : 3));
        int segments = quality == 1 ? 12 : quality == 2 ? 20 : 28;
        int gold = 0xFFC65C, blue = 0xA8CEFF, white = 0xFFF4D9;
        Vec3 ground = effect.packet.position().add(0, .15D, 0);
        var preset = effect.definition;
        if (effect.packet.effect().equals(SolarVfx.ECLIPSE_RESTORATION)) {
            Vec3 center = effect.interpolated(partial);
            mesh.beam(center.add(0, 7, 0), center, .32D, gold, fade * .32F, preset.trail());
            mesh.beam(center.add(0, 7, 0), center, .1D, white, fade * .8F, preset.trail());
            mesh.ring(center.add(0, .08D, 0), 1.2D, .07D, true, age * .04D, blue, fade * .6F, segments, preset.ring());
            motes(mesh, center, 1.1D, age, quality * 4, gold, blue, fade);
        } else {
            State state = effect.eclipseState();
            Vec3 solar = state.solar(effect.packet, partial), lunar = state.lunar(effect.packet, partial);
            Vec3 eclipse = solar.lerp(lunar, .5D).add(0, Math.max(10, radius * .65D), 0);
            double opening = Math.clamp(age / 12D, .05D, 1), size = (5 + radius * .12D) * opening;
            mesh.face(eclipse, size, age * .004D, false, 0xFFFFFF, fade * .95F, DISC);
            mesh.face(eclipse, size * 1.12D, -age * .005D, false, gold, fade * .12F, preset.face());
            orbit(mesh, camera, eclipse, size * .95D, age * .012D, gold, fade, segments, preset.trail());
            orbit(mesh, camera, eclipse, size * 1.06D, -age * .009D, blue, fade * .8F, segments, preset.trail());
            double wave = Math.clamp(age / 22D, .01D, 1.16D);
            float shock = fade * (float) Math.clamp(1 - age / 32D, 0, 1);
            mesh.ring(ground, radius * wave, .2D, true, age * .01D, gold, shock, segments, preset.ring());
            mesh.ring(ground.add(0, .12D, 0), radius * wave * 1.12D, .15D, true, -age * .015D, blue, shock, segments, preset.ring());
            orbit(mesh, camera, ground.add(0, 1, 0), radius * wave * .55D, age * .03D, blue, shock * .5F, segments, preset.trail());
            if (age < 12 || age > 48) { mesh.face(ground.add(0, 1, 0), 2 + opening, age * .02D, false, white, fade * .14F, preset.hand()); }
            stream(mesh, solar, eclipse, age * .13D, gold, fade * .8F, quality * 8, preset.trail());
            stream(mesh, lunar, eclipse, -age * .13D + Math.PI, blue, fade * .8F, quality * 8, preset.trail());
            mesh.ring(solar, 1.4D, .08D, true, age * .08D, gold, fade * .8F, segments, preset.ring());
            mesh.ring(lunar, 1.4D, .08D, true, -age * .08D, blue, fade * .8F, segments, preset.ring());
            mesh.face(solar, 1.2D, age * .02D, false, gold, fade * .28F, preset.hand());
            mesh.face(lunar, 1.2D, -age * .02D, false, blue, fade * .28F, preset.hand());
            if (effect.packet.direction().y > 0) { dome(mesh, ground, radius, age, fade, segments, quality, preset.trail()); }
            motes(mesh, eclipse, size * 1.3D, age, quality * 8, gold, blue, fade);
        }
        return mesh.finish(List.of(), 1, preset.silhouette(), gold);
    }

    private static void stream(VfxMesh.Builder mesh, Vec3 start, Vec3 end, double turn, int color, float opacity, int count, Identifier texture) {
        Vec3 previous = start;
        for (int step = 1; step <= count; step++) {
            double progress = (double) step / count, spread = Math.sin(progress * Math.PI) * .7D;
            Vec3 next = start.lerp(end, progress).add(Math.cos(turn + progress * 12) * spread, 0, Math.sin(turn + progress * 12) * spread);
            mesh.line(previous, next, .07D + .05D * progress, color, opacity, texture); previous = next;
        }
    }

    private static void orbit(VfxMesh.Builder mesh, Vec3 camera, Vec3 center, double radius, double turn, int color, float opacity, int segments, Identifier texture) {
        Vec3 normal = camera.subtract(center).normalize(), side = normal.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < .01D) { side = new Vec3(1, 0, 0); }
        Vec3 up = side.cross(normal).normalize();
        for (int step = 0; step < segments; step++) {
            if (step % 7 == 6) { continue; }
            double first = turn + step * Math.PI * 2 / segments, last = turn + (step + .8D) * Math.PI * 2 / segments;
            Vec3 start = center.add(side.scale(Math.cos(first) * radius)).add(up.scale(Math.sin(first) * radius));
            Vec3 end = center.add(side.scale(Math.cos(last) * radius)).add(up.scale(Math.sin(last) * radius));
            mesh.line(start, end, .06D, color, opacity, texture);
        }
    }

    private static Vec3 surface(Vec3 center, double radius, double latitude, double longitude) {
        return center.add(Math.cos(longitude) * Math.cos(latitude) * radius, Math.sin(latitude) * radius * .6D, Math.sin(longitude) * Math.cos(latitude) * radius);
    }

    private static void dome(VfxMesh.Builder mesh, Vec3 center, double radius, double age, float opacity, int segments, int quality, Identifier texture) {
        int bands = quality + 2;
        float pulse = (float) (.65D + .2D * Math.exp(-Math.pow((age - 9) / 4, 2)));
        for (int band = 0; band < bands; band++) {
            double latitude = band * Math.PI * .5D / bands, next = (band + 1) * Math.PI * .5D / bands;
            mesh.ring(center.add(0, Math.sin(latitude) * radius * .6D, 0), Math.cos(latitude) * radius, .05D, true, age * .006D, band % 2 == 0 ? 0xFFC65C : 0xA8CEFF, opacity * pulse, segments, texture);
            if (quality == 1) { continue; }
            for (int step = 0; step < segments; step++) {
                double first = step * Math.PI * 2 / segments, last = (step + 1) * Math.PI * 2 / segments;
                mesh.quad(texture, surface(center, radius, latitude, first), surface(center, radius, latitude, last), surface(center, radius, next, last), surface(center, radius, next, first), 0x183462, opacity * .025F);
            }
        }
        for (int rib = 0; rib < quality * 2; rib++) {
            double turn = rib * Math.PI / quality + age * .006D;
            Vec3 previous = surface(center, radius, 0, turn);
            for (int step = 1; step <= 8; step++) { Vec3 next = surface(center, radius, step * Math.PI / 16D, turn); mesh.line(previous, next, .045D, rib % 2 == 0 ? 0xFFC65C : 0xA8CEFF, opacity * .5F, texture); previous = next; }
        }
        motes(mesh, center.add(0, 1, 0), radius * .8D, age, quality * 4, 0xFFC65C, 0xA8CEFF, opacity * .6F);
    }

    private static void motes(VfxMesh.Builder mesh, Vec3 center, double radius, double age, int count, int primary, int secondary, float opacity) {
        for (int index = 0; index < count; index++) {
            double angle = index * 2.399963D + age * .012D, spread = radius * (.4D + index % 5 * .12D);
            Vec3 point = center.add(Math.cos(angle) * spread, Math.sin(angle * 1.7D + age * .025D) * radius * .4D, Math.sin(angle) * spread);
            mesh.face(point, .15D + index % 3 * .04D, angle, false, index % 2 == 0 ? primary : secondary, opacity * .7F, index % 4 == 0 ? SUN : index % 4 == 2 ? MOON : MOTE);
        }
    }
}
