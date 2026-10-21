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
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

public final class VioletLanceProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 48.0D;
    private double travelDistance;
    private float damage = 5.0F;

    public VioletLanceProjectile(EntityType<? extends VioletLanceProjectile> type, Level level) { super(type, level); }

    public void setDamage(float damage) { this.damage = Float.isFinite(damage) ? Math.clamp(damage, 0.0F, 10.0F) : 5.0F; }

    @Override
    protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }

    @Override
    protected double getDefaultGravity() { return 0.0D; }

    @Override
    protected float getAirDrag() { return 1.0F; }

    private static @Nullable LivingEntity livingTarget(Entity entity) {
        return entity instanceof LivingEntity target ? target
                : entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && this.getOwner() instanceof VioletPhantom violet
                && violet.isValidVioletTarget(livingTarget(entity));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && (!(this.getOwner() instanceof VioletPhantom violet) || !violet.isAlive()
                || violet.isBaby() || !violet.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement())))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (!this.level().isClientSide() && (this.tickCount >= 80 || this.travelDistance >= MAX_TRAVEL_DISTANCE)) { this.discard(); }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        LivingEntity target = livingTarget(hit.getEntity());
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && this.getOwner() instanceof VioletPhantom violet
                && violet.isAlive() && !violet.isBaby() && violet.isValidVioletTarget(target)
                && violet.hurtVioletTarget(level, target, this.damageSources().thrown(this, violet), this.damage)) {
            violet.recordLanceHit(target);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (this.isRemoved()) { return; }
        super.onHit(hit);
        if (!this.level().isClientSide()) { this.discard(); }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("VioletLanceTravel", this.travelDistance);
        output.putFloat("VioletLanceDamage", this.damage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double distance = input.getDoubleOr("VioletLanceTravel", 0.0D);
        this.travelDistance = Double.isFinite(distance) ? Math.clamp(distance, 0.0D, MAX_TRAVEL_DISTANCE) : MAX_TRAVEL_DISTANCE;
        this.setDamage(input.getFloatOr("VioletLanceDamage", 5.0F));
    }
}
