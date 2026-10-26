package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

public final class InfernalBolt extends ThrowableItemProjectile {
    private UUID session;
    private boolean ultimate;
    private double travelled = .7D;
    private int life;
    public InfernalBolt(EntityType<? extends InfernalBolt> type, Level level) { super(type, level); }
    public void configure(InfernalPhantom source, UUID session, boolean ultimate) { setOwner(source); this.session = session; this.ultimate = ultimate; }
    public boolean ultimate() { return ultimate; }
    @Override protected Item getDefaultItem() { return Items.FIRE_CHARGE; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof InfernalPhantom source && entity instanceof LivingEntity living && source.validTarget(living); }
    @Override public void tick() { if (level() instanceof ServerLevel) { if (!(getOwner() instanceof InfernalPhantom source) || !source.enabled() || !source.session().equals(session) || ++life > 20 || travelled >= InfernalPhantom.BOLT_RANGE - .000001D || ultimate && source.ascendantTicks() == 0) { discard(); return; } if (getDeltaMovement().length() > InfernalPhantom.BOLT_RANGE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(InfernalPhantom.BOLT_RANGE - travelled)); } if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; } } Vec3 before = position(); super.tick(); travelled += position().distanceTo(before); }
    @Override protected void onHit(HitResult hit) { if (level() instanceof ServerLevel level && getOwner() instanceof InfernalPhantom source && source.session().equals(session)) { if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) { source.boltImpact(level, target, ultimate, hit.getLocation()); } discard(); } }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = InfernalPhantom.BOLT_RANGE; }
}
