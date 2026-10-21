package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.vfx.TitaniumVfx;

public final class GolemFist extends ThrowableItemProjectile {
    private UUID session;
    private double travelled;
    private int life;
    private boolean impacted;
    public GolemFist(EntityType<? extends GolemFist> type, Level level) { super(type, level); }
    public void configure(UUID session) { this.session = session; }
    @Override protected Item getDefaultItem() { return Items.IRON_INGOT; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof GolemPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof GolemPhantom source) || !source.enabled() || !source.session().equals(session) || travelled >= GolemPhantom.PUNCH_RANGE - .000001D || ++life > 40 || !Double.isFinite(getDeltaMovement().lengthSqr()) || !source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
            if (getDeltaMovement().length() > GolemPhantom.PUNCH_RANGE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(GolemPhantom.PUNCH_RANGE - travelled)); }
        }
        Vec3 before = position(); super.tick(); travelled += position().distanceTo(before);
    }
    @Override protected void onHit(HitResult hit) {
        if (impacted || !(level() instanceof ServerLevel level)) { return; } impacted = true;
        if (getOwner() instanceof GolemPhantom source && source.session().equals(session) && source.enabled()) { if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) { if (source.damage(level, target, damageSources().thrown(this, source), 20) > 0) { source.push(target, .7D, false); } } TitaniumVfx.emit(source, TitaniumVfx.HOOK, null, hit.getLocation(), getDeltaMovement().normalize(), 20, 3); }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = GolemPhantom.PUNCH_RANGE; }
}
