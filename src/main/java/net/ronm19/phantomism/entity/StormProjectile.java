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

public final class StormProjectile extends ThrowableItemProjectile {
    private int stormCharge;
    private double travelDistance;

    public StormProjectile(EntityType<? extends StormProjectile> type, Level level) {
        super(type, level);
    }

    public void setStormCharge(int charge) {
        this.stormCharge = Math.clamp(charge, 0, 3);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
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
        if (!this.level().isClientSide() && this.getOwner() instanceof StormPhantom phantom
                && !phantom.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.tickCount >= 80 || this.travelDistance >= 28.0D) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && this.getOwner() instanceof StormPhantom phantom
                && candidate instanceof LivingEntity target && phantom.isValidStormTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof StormPhantom phantom
                && hit.getEntity() instanceof LivingEntity target) {
            phantom.strikeJolt(level, target, this.stormCharge, this, this.position());
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                    10, 0.2D, 0.2D, 0.2D, 0.015D);
            this.playSound(SoundEvents.TRIDENT_HIT, 0.6F, 1.6F);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StormProjectileCharge", this.stormCharge);
        output.putDouble("StormProjectileTravel", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setStormCharge(input.getIntOr("StormProjectileCharge", input.getBooleanOr("StormProjectileCharged", false) ? 2 : 0));
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("StormProjectileTravel", 0.0D));
    }
}
