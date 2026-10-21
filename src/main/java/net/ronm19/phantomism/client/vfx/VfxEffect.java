package net.ronm19.phantomism.client.vfx;

import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.VfxPacket;

public final class VfxEffect extends Particle {
    public static final ParticleRenderType GROUP = new ParticleRenderType("PHANTOM_CINEMATIC", "PV");
    public final VfxPacket packet;
    public final VfxRegistry.Definition definition;
    public final int quality;
    private final ArrayDeque<Vec3> history = new ArrayDeque<>();
    private final ArrayDeque<Pose> echoes = new ArrayDeque<>();
    private int missing;
    public record Pose(Vec3 position, float yaw, float pitch) {}
    public VfxEffect(ClientLevel level, VfxPacket packet, VfxRegistry.Definition definition, int quality) {
        super(level, packet.position().x, packet.position().y, packet.position().z); this.packet = packet; this.definition = definition; this.quality = quality; this.lifetime = packet.duration(); this.hasPhysics = false; this.history.add(packet.position());
        if (definition.form() == VfxRegistry.Form.SPATIAL_AFTERIMAGE || definition.form() == VfxRegistry.Form.SPECTRAL_AFTERIMAGE || definition.form() == VfxRegistry.Form.ETHEREAL_PLANE) { Entity attached = level == null ? null : level.getEntity(packet.entityId()); this.echoes.add(new Pose(packet.position(), attached == null ? 0 : attached.getYRot(), attached == null ? 0 : attached.getXRot())); }
    }
    @Override public ParticleRenderType getGroup() { return GROUP; }
    public boolean same(VfxPacket other) { return this.packet.effect().equals(other.effect()) && this.packet.caster().equals(other.caster()) && this.packet.session().equals(other.session()) && this.packet.entityId() == other.entityId() && (other.entityId() >= 0 || this.packet.position().distanceToSqr(other.position()) < .01D); }
    public float age(float partial) { return this.age + partial; }
    public Vec3 position() { return new Vec3(this.x, this.y, this.z); }
    public Vec3 interpolated(float partial) { double blend = Math.clamp(partial, 0, 1); return new Vec3(this.xo + (this.x - this.xo) * blend, this.yo + (this.y - this.yo) * blend, this.zo + (this.z - this.zo) * blend); }
    public List<Vec3> history(float partial) { var points = new java.util.ArrayList<>(this.history); if (points.size() > 1) { points.set(points.size() - 1, this.interpolated(partial)); } return points; }
    public List<Vec3> history() { return List.copyOf(this.history); }
    public List<Pose> echoes() { return List.copyOf(this.echoes); }
    public net.minecraft.world.phys.AABB bounds() {
        var box = new net.minecraft.world.phys.AABB(this.position(), this.position()).inflate(this.packet.radius() + 5);
        if (this.definition.form() == VfxRegistry.Form.INFERNAL_BREATH || this.packet.effect().equals(net.ronm19.phantomism.vfx.DemonVfx.DRAIN)) { box = box.minmax(InfernalVfx.beamBounds(this)); }
        if (this.packet.effect().equals(net.ronm19.phantomism.vfx.AngelVfx.TOUCH) || this.packet.effect().equals(net.ronm19.phantomism.vfx.AngelVfx.TRAIL)) { box = box.minmax(new net.minecraft.world.phys.AABB(this.packet.position(), this.packet.position().add(this.packet.direction())).inflate(3)); }
        if (this.packet.effect().equals(net.ronm19.phantomism.vfx.PrimordialVfx.SHIFT)) { box = box.minmax(new net.minecraft.world.phys.AABB(this.packet.position(), this.packet.position().add(this.packet.direction())).inflate(3)); }
        if (this.packet.effect().equals(net.ronm19.phantomism.vfx.VoidVfx.STEP) || this.packet.effect().equals(net.ronm19.phantomism.vfx.VoidVfx.RESCUE)) { box = box.minmax(new net.minecraft.world.phys.AABB(this.packet.position(),this.packet.position().add(this.packet.direction())).inflate(3)); }
        if (this.definition.form() == VfxRegistry.Form.SPATIAL_TRANSIT) { box = box.minmax(new net.minecraft.world.phys.AABB(this.packet.position(), this.packet.position().add(this.packet.direction())).inflate(this.packet.radius() + 3)); }
        if (this.packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.HARVEST) || this.packet.effect().equals(net.ronm19.phantomism.vfx.ReaperVfx.PASSAGE)) { box = box.minmax(new net.minecraft.world.phys.AABB(this.packet.position(), this.packet.position().add(this.packet.direction())).inflate(3)); }
        for (Vec3 point : this.history) { box = box.minmax(new net.minecraft.world.phys.AABB(point, point).inflate(.5D)); } return box;
    }
    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (Minecraft.getInstance().level != this.level || ++this.age >= this.lifetime) { this.remove(); return; }
        Entity attached = this.packet.entityId() < 0 ? null : this.level.getEntity(this.packet.entityId());
        if (attached != null && attached.getUUID().equals(this.packet.attached()) && !attached.isRemoved()) {
            if (this.age % 3 == 0 && VfxRegistry.afterimage(this.definition)) { while (this.echoes.size() >= 3) { this.echoes.removeFirst(); } this.echoes.addLast(new Pose(this.position(), attached.getYRot(), attached.getXRot())); }
            this.setPos(attached.getX(), attached.getY() + .2D, attached.getZ()); this.missing = 0;
        } else if (this.packet.entityId() >= 0 && ++this.missing > 8) { this.remove(); }
        if (this.history.isEmpty() || this.history.getLast().distanceToSqr(this.position()) > .0025D) { this.history.addLast(this.position()); }
        while (this.history.size() > 18) { this.history.removeFirst(); }
    }
}
