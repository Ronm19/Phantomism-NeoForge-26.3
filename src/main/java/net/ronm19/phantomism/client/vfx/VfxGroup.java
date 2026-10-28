package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

public final class VfxGroup extends ParticleGroup<VfxEffect> {
    public VfxGroup(ParticleEngine engine) { super(engine); }
    @Override public boolean add(Particle particle) { return this.size() < 64 && super.add(particle); }
    @Override public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick) {
        List<VfxMesh.Batch> batches = new ArrayList<>(); int vertices = 0;
        List<VfxEffect> ordered = new ArrayList<>(this.particles);
        ordered.sort(java.util.Comparator.comparingInt(effect -> VfxRegistry.cinematic(effect.definition) ? 0 : effect.definition.form() == VfxRegistry.Form.PROJECTILE ? 1 : 2));
        for (VfxEffect effect : ordered) {
            if (!effect.isAlive() || camera.position().distanceToSqr(effect.position()) > 80 * 80 || !frustum.isVisible(effect.bounds())) { continue; }
            int allowance = VfxRegistry.afterimage(effect.definition) ? 4096 : (effect.definition.form() == VfxRegistry.Form.SPATIAL_ULTIMATE || effect.definition.form() == VfxRegistry.Form.BASTION || effect.definition.form() == VfxRegistry.Form.APOCALYPSE || effect.definition.form() == VfxRegistry.Form.CELESTIAL_EYE || effect.definition.form() == VfxRegistry.Form.CALAMITY || effect.definition.form() == VfxRegistry.Form.FORGOTTEN_LAND || effect.definition.form() == VfxRegistry.Form.LAST_BELL || effect.definition.form() == VfxRegistry.Form.PRIMAL_DOMAIN || effect.definition.form() == VfxRegistry.Form.NULL_HORIZON || effect.definition.form() == VfxRegistry.Form.DARK_HOUR || effect.definition.form() == VfxRegistry.Form.KINGS_DOMINION || effect.definition.form() == VfxRegistry.Form.TITANS_IMPACT) ? 4096 : 1024;
            if (effect.packet.effect().equals(net.ronm19.phantomism.vfx.SolarVfx.ECLIPSE)) { allowance = 3072; }
            if (effect.packet.effect().equals(net.ronm19.phantomism.vfx.FireworkVfx.FINALE)) { allowance = 3072; }
            if (effect.packet.effect().equals(net.ronm19.phantomism.vfx.FireworkVfx.FINAL_BURST)) { allowance = 1536; }
            if (effect.packet.effect().equals(net.ronm19.phantomism.vfx.CreatorVfx.BEAM)) { allowance = 2816; }
            List<VfxMesh.Batch> generated = VfxMesh.build(effect, camera.position(), partialTick, Math.min(allowance, 8192 - vertices));
            for (VfxMesh.Batch batch : generated) { vertices += batch.count(); batches.add(batch); } if (vertices >= 8192) { break; }
        }
        List<VfxMesh.Batch> immutable = List.copyOf(batches);
        return new ParticleGroupRenderState() { @Override public void submit(SubmitNodeCollector collector, CameraRenderState cameraState) { for (VfxMesh.Batch batch : immutable) { batch.submit(collector); } } };
    }
}
