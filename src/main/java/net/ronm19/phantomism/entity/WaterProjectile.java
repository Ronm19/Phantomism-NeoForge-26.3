package net.ronm19.phantomism.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

public final class WaterProjectile extends ThrowableItemProjectile {
    private double travelDistance;

    public WaterProjectile(EntityType<? extends WaterProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.PRISMARINE_CRYSTALS;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.getOwner() instanceof WaterPhantom phantom
                && !phantom.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        if (this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(1.0D / 0.8F));
        }
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.tickCount >= 80 || this.travelDistance >= 28.0D) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && this.getOwner() instanceof WaterPhantom phantom
                && candidate instanceof LivingEntity target && phantom.isValidWaterTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof WaterPhantom phantom
                && hit.getEntity() instanceof LivingEntity target && phantom.isValidWaterTarget(target)
                && target.hurtServer(level, this.damageSources().thrown(this, phantom), 5.0F)) {
            phantom.applyWaterKnockback(target, this.getDeltaMovement().normalize(), 0.45D);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 12, 0.25D, 0.25D, 0.25D, 0.04D);
            this.playSound(SoundEvents.PLAYER_SPLASH, 0.6F, 1.4F);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("WaterProjectileTravel", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("WaterProjectileTravel", 0.0D));
    }
}
