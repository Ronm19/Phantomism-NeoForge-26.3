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

public final class EarthBomb extends ThrowableItemProjectile {
    private double travelDistance;

    public EarthBomb(EntityType<? extends EarthBomb> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MOSS_BLOCK;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void tick() {
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (this.level().isClientSide()) {
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.tickCount >= 100 || this.travelDistance >= 28.0D) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && this.getOwner() instanceof EarthPhantom phantom
                && candidate instanceof LivingEntity target && phantom.isValidEarthTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof EarthPhantom phantom
                && hit.getEntity() instanceof LivingEntity target && phantom.isValidEarthTarget(target)
                && target.hurtServer(level, this.damageSources().thrown(this, phantom), 6.0F)) {
            phantom.applyStagger(level, target);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 18, 0.35D, 0.35D, 0.35D, 0.1D);
            this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.9F, 0.65F);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("EarthBombTravel", this.travelDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.travelDistance = Math.max(0.0D, input.getDoubleOr("EarthBombTravel", 0.0D));
    }
}
