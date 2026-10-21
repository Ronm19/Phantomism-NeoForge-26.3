package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.entity.ai.RiftControl;
import net.ronm19.phantomism.vfx.RiftVfx;

public final class RiftBolt extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 39;
    private double travelled = .7D;
    private UUID session;
    public RiftBolt(EntityType<? extends RiftBolt> type, Level level) { super(type, level); }
    public void configure(UUID session) { this.session = session; }
    @Override protected Item getDefaultItem() { return Items.ENDER_PEARL; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && this.getOwner() instanceof RiftPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel) {
            if (!(this.getOwner() instanceof RiftPhantom source) || !source.controlsEnabled() || !source.session().equals(this.session) || this.travelled >= MAX_TRAVEL_DISTANCE || this.tickCount >= 40) { this.discard(); return; }
            if (this.travelled + this.getDeltaMovement().length() > MAX_TRAVEL_DISTANCE) { this.setDeltaMovement(this.getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - this.travelled)); }
            if (!source.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) { this.discard(); return; }
        }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof RiftPhantom source && result.getEntity() instanceof LivingEntity target
                && source.damage(level, target, 7, this.damageSources().thrown(this, source)) > 0) { if (target instanceof Mob mob) { RiftControl.apply(source, mob, false); } if (target.isAlive()) { source.setCombatTarget(target); } }
    }
    @Override protected void onHit(HitResult result) {
        if (this.isRemoved()) { return; } super.onHit(result);
        if (this.level() instanceof ServerLevel && this.getOwner() instanceof RiftPhantom source) { RiftVfx.emit(source, RiftVfx.IMPACT, null, result.getLocation(), this.getDeltaMovement().normalize(), 16, 1.1F); this.discard(); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.travelled = MAX_TRAVEL_DISTANCE; this.session = null; }
}
