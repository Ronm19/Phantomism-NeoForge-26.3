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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.ronm19.phantomism.entity.ai.EchoCombatEvents;
import net.ronm19.phantomism.entity.ai.EchoCombatEvents.Kind;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class EchoPulseProjectile extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE = 36.0D;
    private double travelled = 0.7D;
    private @Nullable UUID session;
    private float damage = 7;
    private Kind kind = Kind.PULSE;
    private UUID origin = UUID.randomUUID();
    private @Nullable UUID intended;
    private @Nullable UUID reservation;
    public EchoPulseProjectile(EntityType<? extends EchoPulseProjectile> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return Items.ECHO_SHARD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    public void configure(Kind kind, UUID origin, @Nullable UUID target, @Nullable UUID reservation) {
        this.kind = kind; this.origin = origin; this.intended = target; this.reservation = reservation;
        if (this.getOwner() instanceof EchoPhantom echo) { this.session = echo.combatSession(); }
        this.damage = EchoCombatEvents.generated(kind) ? 4 : 7;
    }
    private static @Nullable LivingEntity living(Entity entity) { return entity instanceof LivingEntity target ? target : entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null; }
    @Override protected boolean canHitEntity(Entity entity) {
        LivingEntity target = living(entity);
        return super.canHitEntity(entity) && this.getOwner() instanceof EchoPhantom echo && echo.combatSession().equals(this.session) && echo.isValidEchoTarget(target)
                && (this.intended == null || target.getUUID().equals(this.intended));
    }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel level) {
            if (!(this.getOwner() instanceof EchoPhantom echo) || !echo.combatEnabled() || !echo.combatSession().equals(this.session) || !echo.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))
                    || this.intended != null && (!(level.getEntity(this.intended) instanceof LivingEntity target) || !echo.isValidEchoTarget(target))
                    || this.reservation != null && (!(level.getEntity(this.intended) instanceof LivingEntity target) || !EchoCombatEvents.active(level, this.reservation, echo, target))) { this.discard(); return; }
            if (this.travelled >= MAX_TRAVEL_DISTANCE) { this.discard(); return; }
            if (this.travelled + this.getDeltaMovement().length() > MAX_TRAVEL_DISTANCE) { this.setDeltaMovement(this.getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE - this.travelled)); }
        }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (this.level() instanceof ServerLevel level) {
            if (this.tickCount >= 40 || this.travelled >= MAX_TRAVEL_DISTANCE) { this.discard(); }
            else if (!this.isRemoved() && this.tickCount % 3 == 0) { PhantomVisualEffects.echoRings(level, this.position(), 0.4F, false); }
        }
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && this.getOwner() instanceof EchoPhantom echo) {
            LivingEntity target = living(result.getEntity());
            if (echo.isValidEchoTarget(target) && echo.hurtEchoTarget(level, target, this.damageSources().thrown(this, echo), this.damage, this.kind, this.origin)) {
                PhantomVisualEffects.impact(level, PhantomVfxTheme.ECHO, target.getBoundingBox().getCenter());
                PhantomVisualEffects.echoRings(level, target.getBoundingBox().getCenter(), 1.6F, true);
            }
        }
    }
    @Override protected void onHit(HitResult result) { if (this.isRemoved()) { return; } super.onHit(result); if (!this.level().isClientSide()) { this.discard(); } }
    @Override public void remove(RemovalReason reason) { if (this.level() instanceof ServerLevel level) { EchoCombatEvents.release(level, this.reservation); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.travelled = MAX_TRAVEL_DISTANCE; }
}
