package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class VampireBolt extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 36.0D;
    private double travelled = 0.7D;
    private @Nullable UUID session;
    private boolean empowered;
    public VampireBolt(EntityType<? extends VampireBolt> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return Items.REDSTONE; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    public void configure(UUID session, boolean empowered) { this.session = session; this.empowered = empowered; }
    private static @Nullable LivingEntity living(Entity entity) { return entity instanceof LivingEntity target ? target : entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null; }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && this.getOwner() instanceof VampirePhantom vampire && vampire.combatSession().equals(this.session) && vampire.isValidVampireTarget(living(entity));
    }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel) {
            if (!(this.getOwner() instanceof VampirePhantom vampire) || !vampire.combatEnabled() || !vampire.combatSession().equals(this.session)
                    || !vampire.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement())) || this.travelled >= MAX_TRAVEL_DISTANCE || this.tickCount >= 40) { this.discard(); return; }
            if (this.travelled + this.getDeltaMovement().length() > MAX_TRAVEL_DISTANCE) { this.setDeltaMovement(this.getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - this.travelled)); }
        }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (this.level() instanceof ServerLevel level) {
            if (this.travelled >= MAX_TRAVEL_DISTANCE) { this.discard(); }
            else if (!this.isRemoved() && this.tickCount % 3 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.VAMPIRE, this.position()); if (this.empowered) { PhantomVisualEffects.vampireRings(level, this.position(), 0.35F); } }
        }
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && this.getOwner() instanceof VampirePhantom vampire) {
            LivingEntity target = living(result.getEntity());
            if (vampire.isValidVampireTarget(target) && vampire.hurtVampireTarget(level, target, this.damageSources().thrown(this, vampire), this.empowered ? 7 : 5, false) > 0) {
                PhantomVisualEffects.impact(level, PhantomVfxTheme.VAMPIRE, target.getBoundingBox().getCenter());
                if (target.isAlive() && vampire.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player) { vampire.setCombatTarget(target); }
            }
        }
    }
    @Override protected void onHit(HitResult result) { if (!this.isRemoved()) { super.onHit(result); if (!this.level().isClientSide()) { this.discard(); } } }
}
