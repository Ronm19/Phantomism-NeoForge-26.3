package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.vfx.SpectralVfx;

public final class SpectralLance extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 26;
    private UUID session;
    private double travelled = .7D;
    private int life;
    private boolean impacted;
    public SpectralLance(EntityType<? extends SpectralLance> type, Level level) { super(type, level); }
    public void configure(UUID session) { this.session = session; }
    @Override protected Item getDefaultItem() { return Items.GHAST_TEAR; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof SpectralPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof SpectralPhantom source) || !source.combatEnabled() || !source.session().equals(session) || travelled >= MAX_TRAVEL_DISTANCE - .000001D || ++life > 40) { discard(); return; }
            if (getDeltaMovement().length() > MAX_TRAVEL_DISTANCE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (impacted || !(level() instanceof ServerLevel level)) { return; } impacted = true;
        if (getOwner() instanceof SpectralPhantom source && source.session().equals(session)) {
            if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) {
                if (source.damage(level, target, damageSources().thrown(this, source), 9) > 0 && target.isAlive()) { source.setCombatTarget(target); }
            }
            SpectralVfx.emit(source, SpectralVfx.IMPACT, null, hit.getLocation(), getDeltaMovement().normalize(), 18, 1.1F);
        }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); travelled = MAX_TRAVEL_DISTANCE; session = null; }
}
