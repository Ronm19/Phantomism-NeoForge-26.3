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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.EasterVfx;


public final class EasterEggProjectile extends Projectile implements ItemSupplier {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(EasterEggProjectile.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private UUID session;
    private int life;
    private double travelled = .7D;

    public EasterEggProjectile(EntityType<? extends EasterEggProjectile> type, Level level) { super(type, level); }
    public void configure(EasterPhantom source, UUID session, int style) { setOwner(source); this.session = session; entityData.set(STYLE, Math.clamp(style, 0, 3)); }
    public int style() { return entityData.get(STYLE); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { data.define(STYLE, 0); }
    @Override public ItemStack getItem() { return new ItemStack(Items.EGG); }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && getOwner() instanceof EasterPhantom source
                && entity instanceof LivingEntity target && source.validTarget(target);
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            if (!(getOwner() instanceof EasterPhantom source) || !source.enabled() || !source.session().equals(session)
                    || ++life > 32 || travelled >= EasterPhantom.EGG_RANGE) { discard(); return; }
            double remaining = EasterPhantom.EGG_RANGE - travelled;
            if (getDeltaMovement().length() > remaining) { setDeltaMovement(getDeltaMovement().normalize().scale(remaining)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
            if (life % 2 == 0) { EasterVfx.motes(level, position(), 2); }
        }
        super.tick();
        Vec3 previous = position();
        if (level() instanceof ServerLevel) {
            HitResult hit = collision();
            if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                hitTargetOrDeflectSelf(hit);
            } else { setPos(position().add(getDeltaMovement())); }
        } else { setPos(position().add(getDeltaMovement())); }
        updateRotation();
        travelled += position().distanceTo(previous);
    }

    private HitResult collision() {
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        HitResult closest = level().clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = closest.getType() == HitResult.Type.MISS ? end : closest.getLocation();
        double nearest = start.distanceToSqr(limit);
        for (Entity target : level().getEntities(this, getBoundingBox().expandTowards(getDeltaMovement()).inflate(.3D), this::canHitEntity)) {
            AABB bounds = target.getBoundingBox().inflate(.15D);
            Vec3 contact = bounds.contains(start) ? start : bounds.clip(start, limit).orElse(null);
            if (contact != null && start.distanceToSqr(contact) < nearest) {
                nearest = start.distanceToSqr(contact);
                closest = new EntityHitResult(target, contact);
            }
        }
        return closest;
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) { return; }
        if (getOwner() instanceof EasterPhantom source && source.session().equals(session)
                && hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) {
            source.eggImpact(level, target, hit.getLocation());
        } else if (getOwner() instanceof EasterPhantom source && source.session().equals(session)) { source.eggImpact(level, null, hit.getLocation()); }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = EasterPhantom.EGG_RANGE; }
}


