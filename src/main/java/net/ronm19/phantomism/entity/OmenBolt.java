package net.ronm19.phantomism.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.vfx.OmenVfx;

public final class OmenBolt extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 42;
    private UUID session;
    private double travelled = .7D;
    private int life;
    private final Set<UUID> hit = new HashSet<>();
    public OmenBolt(EntityType<? extends OmenBolt> type, Level level) { super(type, level); }
    public void configure(UUID session) { this.session = session; }
    @Override protected Item getDefaultItem() { return Items.EMERALD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && !hit.contains(entity.getUUID()) && getOwner() instanceof OmenPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof OmenPhantom source) || !source.enabled() || !source.session().equals(session) || travelled >= MAX_TRAVEL_DISTANCE - .000001D || ++life > 40 || hit.size() >= 3) { discard(); return; }
            if (getDeltaMovement().length() > MAX_TRAVEL_DISTANCE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult result) {
        if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof OmenPhantom source) || !source.session().equals(session)) { return; }
        if (result instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target && source.validTarget(target) && hit.add(target.getUUID())) {
            if (source.damage(level, target, damageSources().thrown(this, source), 9) > 0) { OmenVfx.emit(source, OmenVfx.REVEAL, target, target.position(), Vec3.ZERO, 40, 1); }
            OmenVfx.emit(source, OmenVfx.IMPACT, null, result.getLocation(), getDeltaMovement().normalize(), 18, 1.4F);
            if (hit.size() >= 3) { discard(); }
        } else if (result instanceof BlockHitResult) { OmenVfx.emit(source, OmenVfx.IMPACT, null, result.getLocation(), Vec3.ZERO, 18, 1); discard(); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); travelled = MAX_TRAVEL_DISTANCE; session = null; }
}
