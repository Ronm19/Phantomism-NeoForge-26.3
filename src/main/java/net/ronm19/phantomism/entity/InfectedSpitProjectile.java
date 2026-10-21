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

public final class InfectedSpitProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 42.0D;
    private double travelled;

    public InfectedSpitProjectile(EntityType<? extends InfectedSpitProjectile> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return Items.SLIME_BALL; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }

    private static @Nullable LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity target ? target : entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && this.getOwner() instanceof ZombiePhantom zombie && zombie.isValidZombieTarget(living(entity));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && (!(this.getOwner() instanceof ZombiePhantom zombie) || !zombie.isAlive() || zombie.isBaby()
                || zombie.level() != this.level() || !zombie.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement())))) { this.discard(); return; }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (!this.level().isClientSide() && (this.tickCount >= 60 || this.travelled >= MAX_TRAVEL_DISTANCE)) { this.discard(); }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result); LivingEntity target = living(result.getEntity());
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof ZombiePhantom zombie && zombie.isValidZombieTarget(target)
                && zombie.hurtInfectionTarget(level, target, this.damageSources().thrown(this, zombie), 5)) { zombie.infect(target, 100); }
    }

    @Override
    protected void onHit(HitResult result) { if (this.isRemoved()) { return; } super.onHit(result); if (!this.level().isClientSide()) { this.discard(); } }

    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putDouble("InfectedSpitTravel", this.travelled); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); double distance = input.getDoubleOr("InfectedSpitTravel", 0);
        this.travelled = Double.isFinite(distance) ? Math.clamp(distance, 0, MAX_TRAVEL_DISTANCE) : MAX_TRAVEL_DISTANCE;
    }
}
