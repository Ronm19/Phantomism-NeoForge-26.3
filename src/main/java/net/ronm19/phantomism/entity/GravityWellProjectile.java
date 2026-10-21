package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;

public final class GravityWellProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 42;
    private double travelled = 0.7D;
    private UUID session;
    public GravityWellProjectile(EntityType<? extends GravityWellProjectile> type, Level level) { super(type, level); }
    public void configure(UUID session) { this.session = session; }
    @Override protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && this.getOwner() instanceof GravityPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel) {
            if (!(this.getOwner() instanceof GravityPhantom source) || !source.controlsEnabled() || !source.session().equals(this.session)
                    || this.travelled >= MAX_TRAVEL_DISTANCE || this.tickCount >= 60) { this.discard(); return; }
            if (this.travelled + this.getDeltaMovement().length() > MAX_TRAVEL_DISTANCE) { this.setDeltaMovement(this.getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - this.travelled)); }
            if (!source.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) { this.discard(); return; }
        }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            if (this.travelled >= MAX_TRAVEL_DISTANCE - 0.0001D) { if (this.getOwner() instanceof GravityPhantom source) { source.placeWell(level, this.position()); } this.discard(); }
            else if (this.tickCount % 3 == 0) { PhantomVisualEffects.orbit(level, PhantomVfxTheme.GRAVITY, this.position(), 0.35F, 3); }
        }
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof GravityPhantom source && result.getEntity() instanceof LivingEntity target) {
            if (source.damage(level, target, 5, this.damageSources().thrown(this, source)) > 0 && target.isAlive()) { source.setCombatTarget(target); }
        }
    }
    @Override protected void onHit(HitResult result) {
        if (this.isRemoved()) { return; } super.onHit(result);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof GravityPhantom source) {
            Vec3 center = result instanceof EntityHitResult entity ? entity.getEntity().position().add(0, 0.2D, 0) : result.getLocation();
            if (result instanceof BlockHitResult block) { center = center.add(block.getDirection().getStepX() * 0.25D, block.getDirection().getStepY() * 0.25D, block.getDirection().getStepZ() * 0.25D); }
            source.placeWell(level, center); PhantomVisualEffects.implosion(level, PhantomVfxTheme.GRAVITY, result.getLocation(), 1);
            this.discard();
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.travelled = MAX_TRAVEL_DISTANCE; this.session = null; }
}
