package net.ronm19.phantomism.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PetalProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 42.0D;
    private double travelDistance;

    public PetalProjectile(EntityType<? extends PetalProjectile> type, Level level) { super(type, level); }

    @Override
    protected Item getDefaultItem() { return Items.PINK_PETALS; }

    @Override
    protected double getDefaultGravity() { return 0.0D; }

    @Override
    protected float getAirDrag() { return 1.0F; }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.getOwner() instanceof CherryPhantom phantom
                && !phantom.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (!this.level().isClientSide() && (this.tickCount >= 80 || this.travelDistance >= MAX_TRAVEL_DISTANCE)) { this.discard(); }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && this.getOwner() instanceof CherryPhantom phantom
                && candidate instanceof LivingEntity target && phantom.isValidPetalTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof CherryPhantom phantom
                && hit.getEntity() instanceof LivingEntity target && phantom.isValidPetalTarget(target)) {
            target.hurtServer(level, this.damageSources().thrown(this, phantom), 4.0F);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!this.level().isClientSide()) { this.discard(); }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("PetalTravel", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("PetalTravel", 0.0D));
    }
}
