package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

public final class AutumnBolt extends ThrowableItemProjectile {
    private UUID session;
    private double travelled = .7D;
    private int life;
    public AutumnBolt(EntityType<? extends AutumnBolt> type, Level level) { super(type, level); }
    public void configure(AutumnPhantom source, UUID session) { setOwner(source); this.session = session; }
    @Override protected Item getDefaultItem() { return Items.OAK_LEAVES; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof AutumnPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof AutumnPhantom source) || !source.enabled() || !source.session().equals(session) || ++life > 24 || travelled >= AutumnPhantom.BOLT_RANGE - .000001D) { discard(); return; }
            if (getDeltaMovement().length() > AutumnPhantom.BOLT_RANGE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(AutumnPhantom.BOLT_RANGE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) { return; }
        if (getOwner() instanceof AutumnPhantom source && source.session().equals(session) && hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) { source.boltImpact(level, target, hit.getLocation()); }
        discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = AutumnPhantom.BOLT_RANGE; }
}
