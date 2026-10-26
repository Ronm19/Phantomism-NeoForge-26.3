package net.ronm19.phantomism.entity;

import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class IllagerBolt extends ThrowableItemProjectile {
    private UUID session, cast;
    private final Set<UUID> victims = new HashSet<>();
    private double travelled = .7D;
    private int life;
    private boolean piercing;
    public IllagerBolt(EntityType<? extends IllagerBolt> type, Level level) { super(type, level); }
    public void configure(IllagerPhantom source, UUID session, UUID cast, boolean piercing) { setOwner(source); this.session = session; this.cast = cast; this.piercing = piercing; }
    @Override protected Item getDefaultItem() { return Items.EMERALD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && !victims.contains(entity.getUUID()) && getOwner() instanceof IllagerPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof IllagerPhantom source) || !source.enabled() || !source.session().equals(session) || ++life > 40 || travelled >= IllagerPhantom.BARRAGE_RANGE - .000001D) { discard(); return; }
            if (getDeltaMovement().length() > IllagerPhantom.BARRAGE_RANGE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(IllagerPhantom.BARRAGE_RANGE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 before = position(); super.tick(); travelled += position().distanceTo(before);
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof IllagerPhantom source) || !source.session().equals(session)) { return; }
        if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target && victims.add(target.getUUID())) {
            source.boltImpact(level, cast, target, hit.getLocation()); if (piercing && victims.size() < 3) { setPos(hit.getLocation().add(getDeltaMovement().normalize().scale(.3D))); return; }
        }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = IllagerPhantom.BARRAGE_RANGE; }
}
