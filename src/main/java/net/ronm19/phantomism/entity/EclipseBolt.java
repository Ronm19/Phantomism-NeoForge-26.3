package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.entity.ai.EclipseSupport;
import net.ronm19.phantomism.vfx.EclipseVfx;

public final class EclipseBolt extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 42;
    private UUID session;
    private EclipsePhantom.Stance stance = EclipsePhantom.Stance.SOLAR;
    private double travelled = .6D;
    private int life;
    public EclipseBolt(EntityType<? extends EclipseBolt> type, Level level) { super(type, level); }
    public void configure(UUID session, EclipsePhantom.Stance stance) { this.session = session; this.stance = stance; }
    @Override protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof EclipsePhantom source && entity instanceof LivingEntity enemy && source.validTarget(enemy); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof EclipsePhantom source) || !source.enabled() || !source.session().equals(session) || travelled >= MAX_TRAVEL_DISTANCE - .000001D || ++life > 40) { discard(); return; }
            if (getDeltaMovement().length() > MAX_TRAVEL_DISTANCE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof EclipsePhantom source) || !source.session().equals(session)) { return; }
        if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target && source.validTarget(target)
                && source.damage(level, target, damageSources().thrown(this, source), 12) > 0) {
            if (stance == EclipsePhantom.Stance.SOLAR) { EclipseSupport.shield(source, source, 20, .1F); }
            else { EclipseSupport.push(source, target, getDeltaMovement(), .2D); }
        }
        if (hit instanceof EntityHitResult || hit instanceof BlockHitResult) { EclipseVfx.emit(source, EclipseVfx.IMPACT, null, hit.getLocation(), Vec3.ZERO, 18, 1.4F); discard(); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); travelled = MAX_TRAVEL_DISTANCE; session = null; }
}
