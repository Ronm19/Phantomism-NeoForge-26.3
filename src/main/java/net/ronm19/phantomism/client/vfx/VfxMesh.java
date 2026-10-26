package net.ronm19.phantomism.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.model.PhantomismAdultModel;

public final class VfxMesh {
    private static ModelPart silhouette;
    private static ModelPart spectralSilhouette;
    private VfxMesh() {}
    public record Batch(Identifier texture, float[] vertices, int[] colors, List<VfxEffect.Pose> echoes, Vec3 camera, float opacity, Identifier skin, int tint) {
        public int count() { return this.vertices.length / 5 + this.echoes.size() * 1024; }
        public boolean voidEcho() { return this.skin.getNamespace().equals(Phantomism.MOD_ID) && this.skin.getPath().equals("textures/entity/void_phantom.png"); }
        public boolean spatialEcho() { return this.skin.getNamespace().equals(Phantomism.MOD_ID) && this.skin.getPath().equals("textures/entity/rift_phantom.png"); }
        public boolean spectralEcho() { return this.skin.getNamespace().equals(Phantomism.MOD_ID) && this.skin.getPath().equals("textures/vfx/spectral_silhouette.png"); }
        public int echoColor(int index) { int alpha = this.spectralEcho() ? (int) (this.opacity * (80 + index * 18)) : this.spatialEcho() ? (int) (this.opacity * 110 * (.6F + .4F * index / 3F)) : (int) (this.opacity * (index / 3F) * 65); return (alpha << 24) | this.tint; }
        public void submit(SubmitNodeCollector collector) {
            PoseStack stack = new PoseStack();
            collector.submitCustomGeometry(stack, this.texture.getNamespace().equals(Phantomism.MOD_ID) && ((this.texture.getPath().startsWith("textures/vfx/rift_") || this.texture.getPath().startsWith("textures/vfx/halloween_")) || this.texture.getPath().startsWith("textures/vfx/sentinel_") || this.texture.getPath().startsWith("textures/vfx/demon_") || (this.texture.getPath().startsWith("textures/vfx/spectral_") || (this.texture.getPath().startsWith("textures/vfx/angel_") || (this.texture.getPath().startsWith("textures/vfx/omen_") || (this.texture.getPath().startsWith("textures/vfx/ancient_") || (this.texture.getPath().startsWith("textures/vfx/reaper_") || (this.texture.getPath().startsWith("textures/vfx/primordial_") || (this.texture.getPath().startsWith("textures/vfx/void_") || (this.texture.getPath().startsWith("textures/vfx/dark_") || (this.texture.getPath().startsWith("textures/vfx/royal_") || (((((((((((this.texture.getPath().startsWith("textures/vfx/autumn_") || this.texture.getPath().startsWith("textures/vfx/lunar_")) || this.texture.getPath().startsWith("textures/vfx/infernal_phantom_")) || this.texture.getPath().startsWith("textures/vfx/illager_")) || this.texture.getPath().startsWith("textures/vfx/solar_")) || this.texture.getPath().startsWith("textures/vfx/spore_")) || this.texture.getPath().startsWith("textures/vfx/gale_")) || this.texture.getPath().startsWith("textures/vfx/obsidian_") || this.texture.getPath().startsWith("textures/vfx/crystal_") || this.texture.getPath().startsWith("textures/vfx/plague_")) || this.texture.getPath().startsWith("textures/vfx/shadow_")) || this.texture.getPath().startsWith("textures/vfx/eclipse_")) || this.texture.getPath().startsWith("textures/vfx/mirage_")) || this.texture.getPath().startsWith("textures/vfx/titanium_")))))))))))) ? RenderTypes.entityTranslucentEmissive(this.texture) : RenderTypes.entityTranslucent(this.texture), (pose, buffer) -> {
                for (int index = 0; index < this.colors.length; index++) {
                    int offset = index * 5;
                    buffer.addVertex(pose, this.vertices[offset], this.vertices[offset + 1], this.vertices[offset + 2]).setColor(this.colors[index])
                            .setUv(this.vertices[offset + 3], this.vertices[offset + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0, 1, 0);
                }
            });
            if (!this.echoes.isEmpty()) {
                ModelPart model = this.spectralEcho() || this.voidEcho() ? spectralModel() : model(); int index = 0;
                for (VfxEffect.Pose echo : this.echoes) {
                    PoseStack transform = new PoseStack(); Vec3 relative = echo.position().subtract(this.camera);
                    transform.translate(relative.x, relative.y + 1.5D, relative.z); transform.rotateDegrees(Axis.YP, 180 - echo.yaw()); transform.rotateDegrees(Axis.XP, echo.pitch()); transform.scale(-1, -1, 1);
                    int color = this.echoColor(++index);
                    collector.submitCustomGeometry(transform, this.spatialEcho() || this.spectralEcho() ? RenderTypes.entityTranslucentEmissive(this.skin) : RenderTypes.entityTranslucent(this.skin), (pose, buffer) -> {
                        PoseStack local = new PoseStack(); local.last().pose().set(pose.pose()); local.last().normal().set(pose.normal()); model.render(local, buffer, 15728880, OverlayTexture.NO_OVERLAY, color);
                    });
                }
            }
        }
    }
    private static ModelPart model() {
        if (silhouette == null) {
            silhouette = PhantomismAdultModel.createBodyLayer().bakeRoot(); ModelPart root = silhouette.getChild("root");
            root.getChild("body").getChild("mushrooms").visible = false;
            for (String hidden : List.of("fangs", "tendrils", "halo")) { root.getChild("head").getChild(hidden).visible = false; }
        }
        return silhouette;
    }
    private static ModelPart spectralModel() {
        if (spectralSilhouette == null) {
            spectralSilhouette = PhantomismAdultModel.createBodyLayer().bakeRoot();
            for (ModelPart part : spectralSilhouette.getAllParts()) {
                for (String hidden : List.of("mushrooms", "spikes", "tail_spikes", "horns", "fangs", "tendrils", "halo")) { if (part.hasChild(hidden)) { part.getChild(hidden).visible = false; } }
            }
        }
        return spectralSilhouette;
    }
    public static List<Batch> build(VfxEffect effect, Vec3 camera, float partial, int limit) {
        if (HalloweenEffects.supports(effect.packet.effect())) { return HalloweenEffects.build(effect, camera, partial, limit); }
        if (EasterEffects.supports(effect.packet.effect())) { return EasterEffects.build(effect, camera, partial, limit); }
        if (ChristmasEffects.supports(effect.packet.effect())) { return ChristmasEffects.build(effect, camera, partial, limit); }
        if (AutumnEffects.supports(effect.packet.effect())) { return AutumnEffects.build(effect, camera, partial, limit); }
        if (OminousVfx.supports(effect.definition.form())) { return OminousVfx.build(effect, camera, partial, limit); }
        if (AncientEffects.supports(effect.definition.form())) { return AncientEffects.build(effect, camera, partial, limit); }
        if (ReaperEffects.supports(effect.definition.form())) { return ReaperEffects.build(effect, camera, partial, limit); }
        if (LunarEffects.supports(effect.definition.form())) { return LunarEffects.build(effect, camera, partial, limit); }
        if (InfernalPhantomEffects.supports(effect.definition.form())) { return InfernalPhantomEffects.build(effect, camera, partial, limit); }
        if (IllagerEffects.supports(effect.definition.form())) { return IllagerEffects.build(effect, camera, partial, limit); }
        if (SolarEffects.supports(effect.definition.form())) { return SolarEffects.build(effect, camera, partial, limit); }
        if (SporeEffects.supports(effect.definition.form())) { return SporeEffects.build(effect, camera, partial, limit); }
        if (GaleEffects.supports(effect.definition.form())) { return GaleEffects.build(effect, camera, partial, limit); }
        if (ObsidianEffects.supports(effect.definition.form())) { return ObsidianEffects.build(effect, camera, partial, limit); }
        if (CrystalEffects.supports(effect.definition.form())) { return CrystalEffects.build(effect, camera, partial, limit); }
        if (PlagueEffects.supports(effect.definition.form())) { return PlagueEffects.build(effect, camera, partial, limit); }
        if (ShadowEffects.supports(effect.definition.form())) { return ShadowEffects.build(effect, camera, partial, limit); }
        if (EclipseEffects.supports(effect.definition.form())) { return EclipseEffects.build(effect, camera, partial, limit); }
        if (MirageEffects.supports(effect.definition.form())) { return MirageEffects.build(effect, camera, partial, limit); }
        if (TitaniumEffects.supports(effect.definition.form())) { return TitaniumEffects.build(effect, camera, partial, limit); }
        if (RoyalEffects.supports(effect.definition.form())) { return RoyalEffects.build(effect, camera, partial, limit); }
        if (DarkEffects.supports(effect.definition.form())) { return DarkEffects.build(effect, camera, partial, limit); }
        if (VoidEffects.supports(effect.definition.form())) { return VoidEffects.build(effect,camera,partial,limit); }
        if (PrimordialEffects.supports(effect.definition.form())) { return PrimordialEffects.build(effect, camera, partial, limit); }
        if (CelestialVfx.supports(effect.definition.form())) { return CelestialVfx.build(effect, camera, partial, limit); }
        if (SpectralEffects.supports(effect.definition.form())) { return SpectralEffects.build(effect, camera, partial, limit); }
        if (InfernalVfx.supports(effect.definition.form())) { return InfernalVfx.build(effect, camera, partial, limit); }
        if (GuardianVfx.supports(effect.definition.form())) { return GuardianVfx.build(effect, camera, partial, limit); }
        if (SpatialVfx.supports(effect.definition.form())) { return SpatialVfx.build(effect, camera, partial, limit); }
        Builder mesh = new Builder(camera, Math.max(0, limit), effect.definition.width(), effect.packet.direction()); Vec3 center = effect.interpolated(partial); float age = effect.age(partial); List<Vec3> trail = effect.history(partial);
        float progress = Math.clamp(age / effect.packet.duration(), 0, 1); float fade = Math.min(1, age / 3) * Math.min(1, (effect.packet.duration() - age) / 8);
        int primary = effect.packet.primary(), secondary = effect.packet.secondary(); double radius = effect.packet.radius();
        VfxRegistry.Definition preset = effect.definition; int segments = 16 + effect.quality * 8;
        switch (preset.form()) {
            case PROJECTILE -> {
                mesh.ribbon(trail, .13D, primary, fade * .8F, preset.trail()); mesh.ribbon(trail, .045D, 0xFFFFFF, fade, preset.trail());
                mesh.face(center, .22D, age * .35D, false, primary, fade, preset.ring()); mesh.face(center, radius, -age * .12D, false, secondary, fade, preset.face());
            }
            case AFTERIMAGE -> { mesh.ribbon(trail, .35D, primary, fade, preset.trail()); mesh.ring(center.add(0, .15D, 0), radius * (1 - progress * .5D), .045D, false, age * .2D, secondary, fade, segments, preset.trail()); }
            case IMPACT, SLASH -> {
                mesh.ring(center, radius * (.1D + progress * 1.6D), .08D * (1 - progress), false, 0, primary, fade, segments, preset.trail());
                mesh.face(center, radius * (1 - progress), age * .1D, false, secondary, fade, preset.face());
                for (int shard = 0; shard < effect.quality * 6; shard++) { double angle = shard * Math.PI * 2 / (effect.quality * 6); double distance = radius * (progress < .65F ? progress : 1.3F - progress);
                    Vec3 offset = new Vec3(Math.cos(angle) * distance, Math.sin(angle * 3) * distance * .3D, Math.sin(angle) * distance);
                    mesh.beam(center.add(offset.scale(.8D)), center.add(offset), .04D, primary, fade, preset.trail()); }
            }
            case REVERSE -> {
                mesh.face(center.add(0, .1D, 0), radius, -age * .1D, true, secondary, fade, preset.face());
                for (int strand = 0; strand < effect.quality * 2; strand++) {
                    List<Vec3> path = new ArrayList<>(); for (int point = 0; point < 16; point++) { double time = Math.clamp(1 - progress + point / 80D, 0, 1); double angle = time * Math.PI * 4 + strand * Math.PI;
                        path.add(center.add(Math.cos(angle) * radius * time, time * 2, Math.sin(angle) * radius * time)); }
                    mesh.ribbon(path, .06D, strand % 2 == 0 ? primary : secondary, fade, preset.trail());
                }
            }
            case CINEMATIC -> {
                VfxSequence sequence = preset.sequence(); float elapsed = age * (sequence.charge() + sequence.hold() + sequence.collapse()) / effect.packet.duration(); float opacity = sequence.opacity(elapsed); double turn = sequence.handAngle(elapsed);
                float release = sequence.charge(), resume = sequence.charge() + sequence.hold();
                double size = radius * .28D * Math.min(1, elapsed / Math.max(1, release));
                mesh.face(center.add(0, .2D, 0), size, elapsed < release || elapsed > resume ? turn * .08D : sequence.handAngle(release) * .08D, true, secondary, opacity * .6F, preset.face());
                mesh.face(center.add(0, .22D, 0), size, turn, true, primary, opacity, preset.hand());
                mesh.face(center.add(0, 3, 0), size * .6D, turn, false, secondary, opacity * .5F, preset.hand());
                double wave = radius * Math.clamp((elapsed - release) / 12, 0, 1);
                if (wave > 0) { mesh.ring(center.add(0, .3D, 0), wave, .12D, true, 0, primary, opacity * .7F, segments * 2, preset.trail()); }
                mesh.ring(center.add(0, .12D, 0), radius * Math.min(1, elapsed / Math.max(1, release + 4)), .055D, true, 0, secondary, opacity * .5F, segments * 2, preset.trail());
                for (int glyph = 0; glyph < effect.quality * 6; glyph++) {
                    double angle = glyph * Math.PI / (effect.quality * 3); double drift = elapsed < release ? elapsed * .025D : elapsed < resume ? release * .025D : release * .025D + (elapsed - resume) * .08D;
                    Vec3 orbit = center.add(Math.cos(angle) * (size + drift), 1 + glyph % 3, Math.sin(angle) * (size + drift));
                    mesh.face(orbit, .22D, elapsed < release || elapsed > resume ? turn : sequence.handAngle(release), false, glyph % 2 == 0 ? primary : secondary, opacity, preset.ring());
                }
            }
            case AREA -> {
                mesh.face(center.add(0, .1D, 0), radius, age * .006D, true, secondary, fade * .25F, preset.face());
                mesh.ring(center.add(0, .15D, 0), radius * (.85D + Math.sin(age * .07D) * .03D), .075D, true, age * .01D, primary, fade, segments * 2, preset.trail());
                mesh.face(center.add(0, .17D, 0), radius * .7D, age * .02D, true, primary, fade * .35F, preset.hand());
            }
            case SUPPORT, SIGIL, STASIS, ORBIT -> {
                boolean orbiting = preset.form() == VfxRegistry.Form.SUPPORT || preset.form() == VfxRegistry.Form.ORBIT;
                double rotation = preset.form() == VfxRegistry.Form.STASIS ? 0 : age;
                mesh.face(center.add(0, orbiting ? .2D : 2, 0), radius * .65D, rotation * .045D, orbiting, secondary, fade * .7F, preset.face());
                mesh.face(center.add(0, orbiting ? .22D : 2, 0), radius * .65D, rotation * .12D, orbiting, primary, fade, preset.hand());
                if (orbiting) { for (int index = 0; index < effect.quality * 2; index++) { double angle = age * .12D + index * Math.PI;
                    mesh.face(center.add(Math.cos(angle) * radius, 1 + Math.sin(age * .08D + index) * .4D, Math.sin(angle) * radius), .15D, -angle, false, primary, fade, preset.ring()); } mesh.ribbon(trail, .1D, primary, fade * .5F, preset.trail()); }
            }
            case BEAM -> { mesh.beam(center, center.add(effect.packet.direction()), radius * .12D, primary, fade * .6F, preset.trail()); mesh.beam(center, center.add(effect.packet.direction()), radius * .035D, secondary, fade, preset.trail()); }
        }
        return mesh.finish(preset.form() == VfxRegistry.Form.AFTERIMAGE ? effect.echoes() : List.of(), fade, preset.silhouette(), primary);
    }
    static final class Builder {
        private final Vec3 camera;
        private final int limit;
        private final float width;
        private final Vec3 direction;
        private int count;
        private final Map<Identifier, Buffer> buffers = new LinkedHashMap<>();
        Builder(Vec3 camera, int limit, float width, Vec3 direction) { this.camera = camera; this.limit = limit; this.width = width; this.direction = direction.normalize(); }
        void quad(Identifier texture, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int color, float opacity) {
            quadUv(texture, first, second, third, fourth, color, opacity, 0, 1);
        }
        void quadUv(Identifier texture, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int color, float opacity, float top, float bottom) {
            if (this.count + 4 > this.limit || opacity <= .001F) { return; }
            Buffer buffer = this.buffers.computeIfAbsent(texture, ignored -> new Buffer(this.limit)); int packed = ((int) (Math.clamp(opacity, 0, 1) * 255) << 24) | color;
            buffer.vertex(first.subtract(this.camera), 0, top, packed); buffer.vertex(second.subtract(this.camera), 1, top, packed); buffer.vertex(third.subtract(this.camera), 1, bottom, packed); buffer.vertex(fourth.subtract(this.camera), 0, bottom, packed); this.count += 4;
        }
        void fan(Identifier texture, Vec3 center, Vec3 horizontal, Vec3 vertical, List<Vec3> outline, int color, float opacity, float top, float bottom) {
            for (int index = 0; index < outline.size(); index++) {
                if (this.count + 4 > this.limit || opacity <= .001F) { return; }
                Vec3 first = outline.get(index), second = outline.get((index + 1) % outline.size());
                Buffer buffer = this.buffers.computeIfAbsent(texture, ignored -> new Buffer(this.limit)); int packed = ((int) (Math.clamp(opacity, 0, 1) * 255) << 24) | color;
                Vec3 firstPosition = center.add(horizontal.scale(first.x)).add(vertical.scale(first.y)).subtract(this.camera), secondPosition = center.add(horizontal.scale(second.x)).add(vertical.scale(second.y)).subtract(this.camera);
                float firstVertical = top + (float) (.5D - first.y * .5D) * (bottom - top), secondVertical = top + (float) (.5D - second.y * .5D) * (bottom - top);
                buffer.vertex(center.subtract(this.camera), .5F, (top + bottom) * .5F, packed);
                buffer.vertex(firstPosition, (float) (.5D + first.x * .5D), firstVertical, packed); buffer.vertex(secondPosition, (float) (.5D + second.x * .5D), secondVertical, packed); buffer.vertex(secondPosition, (float) (.5D + second.x * .5D), secondVertical, packed); this.count += 4;
            }
        }
        void face(Vec3 center, double radius, double rotation, boolean ground, int color, float opacity, Identifier texture) {
            Vec3 normal = this.direction.lengthSqr() > .01D ? this.direction : this.camera.subtract(center).normalize();
            Vec3 right = ground ? new Vec3(1, 0, 0) : normal.cross(new Vec3(0, 1, 0)).normalize(); if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); }
            Vec3 up = ground ? new Vec3(0, 0, 1) : right.cross(normal).normalize();
            Vec3 horizontal = right.scale(Math.cos(rotation) * radius).add(up.scale(Math.sin(rotation) * radius)); Vec3 vertical = up.scale(Math.cos(rotation) * radius).subtract(right.scale(Math.sin(rotation) * radius));
            this.quad(texture, center.subtract(horizontal).add(vertical), center.add(horizontal).add(vertical), center.add(horizontal).subtract(vertical), center.subtract(horizontal).subtract(vertical), color, opacity);
        }
        void ring(Vec3 center, double radius, double thickness, boolean ground, double rotation, int color, float opacity, int segments, Identifier texture) {
            if (radius <= 0) { return; }
            thickness *= this.width;
            Vec3 normal = this.direction.lengthSqr() > .01D ? this.direction : new Vec3(0, 0, 1); Vec3 right = ground ? new Vec3(1, 0, 0) : normal.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < .01D) { right = new Vec3(1, 0, 0); } Vec3 up = ground ? new Vec3(0, 0, 1) : right.cross(normal).normalize();
            for (int segment = 0; segment < segments; segment++) {
                double start = rotation + segment * Math.PI * 2 / segments, end = rotation + (segment + 1) * Math.PI * 2 / segments;
                Vec3 first = right.scale(Math.cos(start)).add(up.scale(Math.sin(start))), second = right.scale(Math.cos(end)).add(up.scale(Math.sin(end)));
                this.quad(texture, center.add(first.scale(radius - thickness)), center.add(second.scale(radius - thickness)), center.add(second.scale(radius + thickness)), center.add(first.scale(radius + thickness)), color, opacity);
            }
        }
        void beam(Vec3 start, Vec3 end, double width, int color, float opacity, Identifier texture) { this.ribbon(List.of(start, end), width, color, opacity, texture); }
        void line(Vec3 start, Vec3 end, double width, int color, float opacity, Identifier texture) {
            Vec3 tangent = end.subtract(start); if (tangent.lengthSqr() < .000001D) { return; }
            Vec3 side = tangent.cross(this.camera.subtract(start)).normalize(); if (side.lengthSqr() < .01D) { side = tangent.cross(new Vec3(0, 1, 0)).normalize(); } if (side.lengthSqr() < .01D) { side = new Vec3(1, 0, 0); }
            side = side.scale(width * this.width); this.quad(texture, start.add(side), end.add(side), end.subtract(side), start.subtract(side), color, opacity);
        }
        void ribbon(List<Vec3> points, double width, int color, float opacity, Identifier texture) {
            width *= this.width;
            if (points.size() < 2) { return; } Vec3 previousLeft = null, previousRight = null;
            for (int index = 0; index < points.size(); index++) {
                Vec3 current = points.get(index); Vec3 tangent = points.get(Math.min(index + 1, points.size() - 1)).subtract(points.get(Math.max(0, index - 1))).normalize();
                Vec3 side = tangent.cross(this.camera.subtract(current)).normalize().scale(width * (.2D + .8D * index / points.size()));
                Vec3 left = current.add(side), right = current.subtract(side);
                if (previousLeft != null) { this.quad(texture, previousLeft, left, right, previousRight, color, opacity * (.2F + .8F * index / points.size())); }
                previousLeft = left; previousRight = right;
            }
        }
        List<Batch> finish(List<VfxEffect.Pose> echoes, float opacity, Identifier skin, int tint) {
            int room = Math.max(0, (this.limit - this.count) / 1024); echoes = echoes.stream().limit(Math.min(3, room)).toList();
            List<Batch> result = new ArrayList<>(); boolean first = true;
            for (var entry : this.buffers.entrySet()) { Buffer buffer = entry.getValue(); result.add(new Batch(entry.getKey(), java.util.Arrays.copyOf(buffer.vertices, buffer.size * 5), java.util.Arrays.copyOf(buffer.colors, buffer.size), first ? echoes : List.of(), this.camera, opacity, skin, tint)); first = false; }
            return result;
        }
    }
    private static final class Buffer {
        private final float[] vertices; private final int[] colors; private int size;
        private Buffer(int capacity) { this.vertices = new float[capacity * 5]; this.colors = new int[capacity]; }
        private void vertex(Vec3 position, float horizontal, float vertical, int color) { int offset = this.size * 5; this.vertices[offset] = (float) position.x; this.vertices[offset + 1] = (float) position.y; this.vertices[offset + 2] = (float) position.z; this.vertices[offset + 3] = horizontal; this.vertices[offset + 4] = vertical; this.colors[this.size++] = color; }
    }
}
