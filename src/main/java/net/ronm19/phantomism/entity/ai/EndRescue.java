package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.EndPhantom;
import org.jspecify.annotations.Nullable;

public final class EndRescue {
    private final EndPhantom phantom;
    private @Nullable UUID victim;
    private @Nullable Vec3 landing;
    private int remaining;
    private int cooldown;

    public EndRescue(EndPhantom phantom) { this.phantom = phantom; }

    public boolean active() { return this.victim != null; }

    public boolean family(LivingEntity entity) {
        return entity != this.phantom && entity.isAlive() && !entity.isRemoved() && entity.level() == this.phantom.level()
                && (entity == this.phantom.getOwner() || entity instanceof OwnableEntity companion
                && this.phantom.getOwnerReference() != null && this.phantom.getOwnerReference().equals(companion.getOwnerReference()));
    }

    public boolean accepts(Entity passenger) {
        return this.active() && this.phantom.isTame() && !this.phantom.isBaby() && this.phantom.getPassengers().isEmpty()
                && passenger.getUUID().equals(this.victim) && passenger instanceof LivingEntity entity && this.family(entity);
    }

    public boolean endangered(ServerLevel level, LivingEntity entity) {
        if (!this.family(entity) || entity.isPassenger() || entity.onGround() || entity.getDeltaMovement().y >= -0.35D
                || entity.isInWater() || entity.isInLava() || entity.isFallFlying()
                || entity instanceof Player player && (player.isSpectator() || player.getAbilities().flying)) { return false; }
        var floor = EndTeleportSafety.support(level, entity, entity.position().add(0.0D, 0.2D, 0.0D), 48);
        return floor.isEmpty() || entity.getY() - floor.get().y > 10.0D
                && (entity.fallDistance > 4.0D || entity.getDeltaMovement().y < -0.55D);
    }

    private double urgency(ServerLevel level, LivingEntity entity) {
        double priority = EndTeleportSafety.support(level, entity, entity.position(), 48).isEmpty() ? 0 : 10000;
        return priority + (entity == this.phantom.getOwner() ? 0 : 1000) + this.phantom.distanceToSqr(entity);
    }

    public boolean tryStart(ServerLevel level) {
        if (this.active() || this.cooldown > 0 || this.phantom.isBaby() || !this.phantom.isTame()
                || !this.phantom.isAlive() || !this.phantom.getPassengers().isEmpty() || this.phantom.isPassenger()) { return false; }
        var candidate = level.getEntitiesOfClass(LivingEntity.class, this.phantom.getBoundingBox().inflate(56.0D),
                entity -> this.phantom.distanceToSqr(entity) <= 56.0D * 56.0D && this.endangered(level, entity))
                .stream().sorted(Comparator.comparingDouble(entity -> this.urgency(level, entity))).limit(4).toList();
        for (LivingEntity entity : candidate) {
            var safe = EndTeleportSafety.landing(level, this.phantom, entity.position(), 32);
            if (safe.isEmpty()) { continue; }
            this.victim = entity.getUUID();
            this.landing = safe.get();
            this.remaining = 160;
            this.phantom.setRescuing(true);
            this.phantom.getNavigation().stop();
            this.phantom.setDeltaMovement(Vec3.ZERO);
            this.phantom.showEndAction("rescue");
            return true;
        }
        this.cooldown = 8;
        return false;
    }

    public void tick(ServerLevel level) {
        if (this.cooldown > 0) { this.cooldown--; }
        if (!this.active()) {
            if (this.phantom.tickCount % 20 == 0 && this.phantom.getFirstPassenger() instanceof LivingEntity passenger
                    && !(passenger instanceof Player) && this.family(passenger)) {
                var safe = EndTeleportSafety.dismount(level, passenger, this.phantom.position());
                if (safe.isPresent()) {
                    passenger.stopRiding();
                    passenger.teleportTo(level, safe.get().x, safe.get().y, safe.get().z, java.util.Set.of(), passenger.getYRot(), passenger.getXRot(), true);
                    passenger.resetFallDistance();
                }
            }
            return;
        }
        Entity found = level.getEntity(this.victim);
        if (!(found instanceof LivingEntity entity) || !this.family(entity) || !this.phantom.isAlive()
                || this.phantom.isBaby() || --this.remaining <= 0 || this.landing == null) { this.cancel(); return; }
        if (entity.getVehicle() != this.phantom) {
            if (entity.isPassenger() || entity.onGround() || entity.getY() < level.getMinY() - 28
                    || this.phantom.distanceToSqr(entity) > 56.0D * 56.0D) { this.cancel(); return; }
            Vec3 interception = entity.position().add(entity.getDeltaMovement().scale(2.0D)).add(0.0D, -0.1D, 0.0D);
            if (this.phantom.distanceToSqr(entity) > 4.0D) { this.phantom.teleportForRescue(level, interception, entity); }
            if (this.phantom.getBoundingBox().inflate(1.25D).intersects(entity.getBoundingBox())
                    && EndTeleportSafety.boarding(level, this.phantom, entity)) {
                if (entity.startRiding(this.phantom, true, false)) {
                    entity.resetFallDistance();
                    entity.setDeltaMovement(Vec3.ZERO);
                    this.phantom.positionRider(entity);
                    this.phantom.showEndAction("catch");
                }
            } else {
                this.fly(level, interception, 0.95D);
            }
            return;
        }
        entity.resetFallDistance();
        if (!EndTeleportSafety.destination(level, this.phantom, this.landing, false, null)) {
            var replacement = EndTeleportSafety.landing(level, this.phantom, this.phantom.position(), 32);
            if (replacement.isEmpty()) { this.cancel(); return; }
            this.landing = replacement.get();
        }
        if (this.phantom.position().distanceToSqr(this.landing) < 1.0D) {
            if (!(entity instanceof Player)) {
                var dismount = EndTeleportSafety.dismount(level, entity, this.landing);
                if (dismount.isEmpty()) { return; }
                entity.stopRiding();
                entity.teleportTo(level, dismount.get().x, dismount.get().y, dismount.get().z, java.util.Set.of(), entity.getYRot(), entity.getXRot(), true);
            }
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), this.phantom);
            this.phantom.showEndAction("rescue_land");
            this.cancel();
            this.cooldown = 100;
            return;
        }
        if (this.phantom.position().distanceToSqr(this.landing) <= 25.0D * 25.0D
                && this.phantom.endTeleport(level, this.landing)) { return; }
        Vec3 route = new Vec3(this.landing.x, Math.max(this.phantom.getY(), this.landing.y + 3.0D), this.landing.z);
        if (this.phantom.position().subtract(this.landing).horizontalDistanceSqr() < 2.25D) { route = this.landing; }
        this.fly(level, route, 0.75D);
    }

    private void fly(ServerLevel level, Vec3 destination, double speed) {
        Vec3 offset = destination.subtract(this.phantom.position());
        Vec3 step = offset.length() > speed ? offset.normalize().scale(speed) : offset;
        if (!EndTeleportSafety.corridor(level, this.phantom, step, true)) {
            step = new Vec3(0.0D, 0.5D, 0.0D);
            if (!EndTeleportSafety.corridor(level, this.phantom, step, true)) { step = Vec3.ZERO; }
        }
        this.phantom.setDeltaMovement(step);
        this.phantom.syncVelocity = true;
    }

    public void cancel() {
        this.victim = null;
        this.landing = null;
        this.remaining = 0;
        this.cooldown = 40;
        this.phantom.setRescuing(false);
        this.phantom.setDeltaMovement(Vec3.ZERO);
    }
}
