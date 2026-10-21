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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class DragonFireballProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 54.0D;
    private double travelDistance;
    private boolean empowered;
    private boolean exploded;

    public DragonFireballProjectile(EntityType<? extends DragonFireballProjectile> type, Level level) { super(type, level); }

    public void setEmpowered(boolean empowered) { this.empowered = empowered; }

    @Override
    protected Item getDefaultItem() { return Items.FIRE_CHARGE; }

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
        return super.canHitEntity(entity) && this.getOwner() instanceof PhantomDragon dragon
                && dragon.isValidDragonTarget(livingTarget(entity));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && (!(this.getOwner() instanceof PhantomDragon dragon) || !dragon.isAlive()
                || dragon.isBaby() || !dragon.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement())))) {
            this.discard();
            return;
        }
        Vec3 previous = this.position();
        super.tick();
        this.travelDistance += this.position().distanceTo(previous);
        if (!this.level().isClientSide() && (this.tickCount >= 80 || this.travelDistance >= MAX_TRAVEL_DISTANCE)) { this.discard(); }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || this.exploded) { return; }
        this.exploded = true;
        if (!(this.getOwner() instanceof PhantomDragon dragon) || !dragon.isAlive() || dragon.isBaby()) { this.discard(); return; }
        LivingEntity direct = hit instanceof EntityHitResult entityHit ? livingTarget(entityHit.getEntity()) : null;
        if (hit instanceof EntityHitResult && !dragon.isValidDragonTarget(direct)) { this.discard(); return; }
        if (direct != null) { dragon.hurtDragonTarget(level, direct, this.damageSources().thrown(this, dragon), this.empowered ? 8.0F : 6.0F); }
        Vec3 center = hit.getLocation();
        int affected = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(2.5D), dragon::isAreaEnemy)) {
            if (target != direct && target.position().distanceToSqr(center) <= 6.25D
                    && dragon.hasClearAttackLine(level, center, target.getEyePosition())) {
                dragon.hurtDragonTarget(level, target, this.damageSources().thrown(this, dragon), this.empowered ? 3.0F : 2.0F);
                if (++affected >= 8) { break; }
            }
        }
        PhantomVisualEffects.shockwave(level, PhantomVfxTheme.DRAGON, center, 1.8F);
        PhantomVisualEffects.impact(level, PhantomVfxTheme.DRAGON, center);
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("DragonFireballTravel", this.travelDistance);
        output.putBoolean("DragonFireballEmpowered", this.empowered);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double distance = input.getDoubleOr("DragonFireballTravel", 0.0D);
        this.travelDistance = Double.isFinite(distance) ? Math.clamp(distance, 0.0D, MAX_TRAVEL_DISTANCE) : MAX_TRAVEL_DISTANCE;
        this.empowered = input.getBooleanOr("DragonFireballEmpowered", false);
    }
}
