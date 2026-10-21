package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.vfx.DarkVfx;

public final class DarkProjectile extends ThrowableItemProjectile {
    private UUID session, targetId;
    private boolean blade, impacted;
    private float damage = 12;
    private double travelled = .8D;
    private int life;
    public DarkProjectile(EntityType<? extends DarkProjectile> type, Level level) { super(type, level); }
    public void configure(UUID session, boolean blade, UUID targetId, float damage) { this.session = session; this.blade = blade; this.targetId = targetId; this.damage = Math.clamp(damage, 1, 22); }
    public boolean isBlade() { return blade; }
    @Override protected Item getDefaultItem() { return Items.OBSIDIAN; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && getOwner() instanceof DarkPhantom source && entity instanceof LivingEntity target
                && source.validTarget(target) && (!blade || source.bladeValid(target));
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            double maximum = blade ? 16 : DarkPhantom.BOLT_RANGE;
            if (!(getOwner() instanceof DarkPhantom source) || !source.enabled() || !source.session().equals(session) || travelled >= maximum - .000001D || ++life > 80 || !Double.isFinite(getDeltaMovement().lengthSqr())) { discard(); return; }
            if (blade) {
                if (!(level.getEntity(targetId) instanceof LivingEntity target) || !source.bladeValid(target)) { discard(); return; }
                Vec3 aim = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(1.1D);
                setDeltaMovement(getDeltaMovement().scale(.75D).add(aim.scale(.25D)));
            }
            if (getDeltaMovement().length() > maximum - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(maximum - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (impacted || !(level() instanceof ServerLevel level)) { return; }
        impacted = true;
        if (getOwner() instanceof DarkPhantom source && source.session().equals(session) && source.enabled()) {
            if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target && (!blade || source.bladeValid(target))) {
                source.damage(level, target, damageSources().thrown(this, source), damage, !blade);
            }
            DarkVfx.emit(source, blade ? DarkVfx.BLADE_IMPACT : DarkVfx.IMPACT, null, hit.getLocation(), Vec3.ZERO, 18, blade ? 2 : 1.5F);
        }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); travelled = DarkPhantom.BOLT_RANGE; session = null; }
}
