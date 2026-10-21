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

public final class FrostProjectile extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME_TICKS = 80;
    public static final double MAX_TRAVEL_DISTANCE = 36.0D;
    private double travelDistance;

    public FrostProjectile(EntityType<? extends FrostProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.BLUE_ICE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void tick() {
        Vec3 previousPosition = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previousPosition);
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
                    0.0D, 0.0D, 0.0D);
        } else if (this.tickCount >= MAX_LIFETIME_TICKS || this.travelDistance >= MAX_TRAVEL_DISTANCE) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate)
                && this.getOwner() instanceof FrostPhantom phantom
                && candidate instanceof LivingEntity livingTarget
                && phantom.isValidFrostTarget(livingTarget);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level
                && this.getOwner() instanceof FrostPhantom phantom
                && hit.getEntity() instanceof LivingEntity target
                && phantom.isValidFrostTarget(target)
                && target.hurtServer(level, this.damageSources().thrown(this, phantom), 4.0F)) {
            phantom.applyFrostControl(level, target);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
                    14, 0.3D, 0.3D, 0.3D, 0.025D);
            this.playSound(SoundEvents.GLASS_BREAK, 0.6F, 1.4F);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("FrostTravelDistance", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("FrostTravelDistance", 0.0D));
    }
}
