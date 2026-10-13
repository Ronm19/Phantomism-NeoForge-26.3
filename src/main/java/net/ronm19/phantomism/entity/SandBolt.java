package net.ronm19.phantomism.entity;

import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SandBolt extends ThrowableItemProjectile {
    private double travelDistance;

    public SandBolt(EntityType<? extends SandBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SAND;
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
        if (!this.level().isClientSide() && this.getOwner() instanceof SandPhantom phantom
                && !phantom.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (this.level().isClientSide()) {
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.tickCount >= 80 || this.travelDistance >= 28.0D) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && this.getOwner() instanceof SandPhantom phantom
                && candidate instanceof LivingEntity target && phantom.isValidSandTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof SandPhantom phantom
                && hit.getEntity() instanceof LivingEntity target && phantom.isValidSandTarget(target)
                && target.hurtServer(level, this.damageSources().thrown(this, phantom), 5.0F)) {
            phantom.applySandDisruption(level, target, 60, 20);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.03D);
            this.playSound(SoundEvents.SAND_BREAK, 0.7F, 0.8F);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("SandBoltTravel", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("SandBoltTravel", 0.0D));
    }
}
