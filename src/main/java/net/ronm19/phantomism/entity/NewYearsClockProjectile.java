package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.NewYearsVfx;

public final class NewYearsClockProjectile extends Projectile implements ItemSupplier {
    private UUID session;
    private int life;
    private double travelled = .8D;
    public NewYearsClockProjectile(EntityType<? extends NewYearsClockProjectile> type, Level level) { super(type, level); }
    public void configure(NewYearsPhantom source, UUID session) { setOwner(source); this.session = session; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {}
    @Override public ItemStack getItem() { return new ItemStack(Items.CLOCK); }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && getOwner() instanceof NewYearsPhantom source && entity instanceof LivingEntity target && source.validTarget(target);
    }
    private HitResult collision() {
        Vec3 start = position(), end = start.add(getDeltaMovement());
        HitResult closest = level().clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = closest.getType() == HitResult.Type.MISS ? end : closest.getLocation();
        double nearest = start.distanceToSqr(limit);
        for (Entity target : level().getEntities(this, getBoundingBox().expandTowards(getDeltaMovement()).inflate(.3D), this::canHitEntity)) {
            AABB bounds = target.getBoundingBox().inflate(.15D);
            Vec3 contact = bounds.contains(start) ? start : bounds.clip(start, limit).orElse(null);
            if (contact != null && start.distanceToSqr(contact) < nearest) { nearest = start.distanceToSqr(contact); closest = new EntityHitResult(target, contact); }
        }
        return closest;
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            if (!(getOwner() instanceof NewYearsPhantom source) || !source.enabled() || !source.session().equals(session)
                    || ++life > 30 || travelled >= NewYearsPhantom.BOLT_RANGE || !source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
            double remaining = NewYearsPhantom.BOLT_RANGE - travelled;
            if (getDeltaMovement().length() > remaining) { setDeltaMovement(getDeltaMovement().normalize().scale(remaining)); }
            if (life % 4 == 0) { NewYearsVfx.spark(level, position(), 1); }
        }
        super.tick(); Vec3 previous = position();
        if (level() instanceof ServerLevel) {
            HitResult hit = collision();
            if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) { setPos(hit.getLocation()); hitTargetOrDeflectSelf(hit); }
            else { setPos(position().add(getDeltaMovement())); }
        } else { setPos(position().add(getDeltaMovement())); }
        updateRotation(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) { return; }
        if (getOwner() instanceof NewYearsPhantom source && source.session().equals(session)) {
            source.clockImpact(level, hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target ? target : null, hit.getLocation());
        }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = NewYearsPhantom.BOLT_RANGE; }
}
