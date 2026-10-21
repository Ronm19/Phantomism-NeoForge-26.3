package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

public final class DemonMeteor extends SmallFireball {
    private UUID session, cast;
    private int age;
    private double travelled;
    private boolean impacted;
    public DemonMeteor(EntityType<? extends DemonMeteor> type, Level level) { super(type, level); accelerationPower = 0; }
    public void configure(UUID session, UUID cast, Vec3 velocity, boolean ultimate) { this.session = session; this.cast = cast; setDeltaMovement(velocity); }
    @Override protected float getInertia() { return 1; }
    @Override protected float getLiquidInertia() { return 1; }
    @Override protected net.minecraft.core.particles.ParticleOptions getTrailParticle() { return null; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && getOwner() instanceof DemonPhantom source && entity instanceof LivingEntity target && source.meteorCanHit(cast, target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            if (++age > 70 || !(getOwner() instanceof DemonPhantom source) || !source.session().equals(session) || !source.validBarrage(cast) || travelled >= 45
                    || !source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement())) || !level.getWorldBorder().isWithinBounds(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
            if (getDeltaMovement().length() > 45 - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(45 - travelled)); }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (impacted || !(level() instanceof ServerLevel level) || !(getOwner() instanceof DemonPhantom source) || !source.session().equals(session) || !source.validBarrage(cast)) { return; }
        impacted = true; LivingEntity direct = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living ? living : null;
        source.meteorImpact(level, this, cast, hit.getLocation(), direct); discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); age = 71; session = null; cast = null; }
}
