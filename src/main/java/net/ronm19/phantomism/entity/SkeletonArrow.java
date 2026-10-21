package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class SkeletonArrow extends Arrow {
    public static final double MAX_TRAVEL_DISTANCE = 72.0D;
    public enum Kind { PIERCING, FIRE, ICE, EXPLOSIVE, ANTI_UNDEAD }
    private @Nullable UUID attack;
    private @Nullable UUID intendedTarget;
    private Kind kind = Kind.PIERCING;
    private float impactDamage = 8;
    private boolean rain;
    private double travelled;
    private float healthBeforeHit;

    public SkeletonArrow(EntityType<? extends SkeletonArrow> type, Level level) { super(type, level); this.pickup = Pickup.DISALLOWED; }
    public void configure(SkeletonPhantom source, UUID attack, UUID target, Kind kind, float damage, boolean rain) {
        this.setOwner(source); this.attack = attack; this.intendedTarget = target; this.kind = kind; this.impactDamage = damage; this.rain = rain;
    }
    public Kind kind() { return this.kind; }
    public boolean isRainArrow() { return this.rain; }
    public @Nullable UUID attackIdentity() { return this.attack; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override public byte getPierceLevel() { return (byte) (this.rain ? 0 : 2); }
    private static @Nullable LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity target ? target : entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
    }
    @Override protected boolean canHitEntity(Entity entity) {
        LivingEntity target = living(entity);
        return super.canHitEntity(entity) && this.getOwner() instanceof SkeletonPhantom source && this.attack != null
                && source.isValidSkeletonTarget(target) && (!this.rain || target.getUUID().equals(this.intendedTarget))
                && source.attacks().mayHit(this.attack, target.getUUID());
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel) || !(this.getOwner() instanceof SkeletonPhantom source) || this.attack == null || this.isRemoved()) { return; }
        LivingEntity target = living(result.getEntity());
        if (!source.isValidSkeletonTarget(target) || !source.attacks().claimHit(this.attack, target.getUUID())) { this.discard(); return; }
        this.healthBeforeHit = target.getHealth() + target.getAbsorptionAmount();
        float speed = (float) this.getDeltaMovement().length();
        if (speed < 0.01F) { this.discard(); return; }
        float damage = this.impactDamage;
        if (this.kind == Kind.ANTI_UNDEAD && !target.is(net.minecraft.tags.EntityTypeTags.UNDEAD) && !target.isInvertedHealAndHarm()) { damage -= 2; }
        this.setBaseDamage((damage - 0.001D) / speed); this.setCritArrow(false);
        super.onHitEntity(result);
    }
    @Override protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (!(this.level() instanceof ServerLevel level) || !(this.getOwner() instanceof SkeletonPhantom source) || this.attack == null
                || !source.isValidSkeletonTarget(target) || target.getHealth() + target.getAbsorptionAmount() >= this.healthBeforeHit) { return; }
        source.fracture(target);
        if (this.kind == Kind.FIRE && !target.fireImmune()) { target.igniteForSeconds(3); }
        if (this.kind == Kind.ICE) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), source); }
        PhantomVisualEffects.impact(level, PhantomVfxTheme.SKELETON, target.getBoundingBox().getCenter());
        if (this.kind == Kind.EXPLOSIVE && !this.rain) {
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.SKELETON, target.position(), 2.5F);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5D), source::isValidSkeletonTarget)) {
                if (nearby == target || source.attacks().allocated(this.attack, nearby.getUUID()) > 0 || nearby.distanceToSqr(target) > 6.25D || !source.line(level, target.getEyePosition(), nearby.getEyePosition())
                        || !source.attacks().claimHit(this.attack, nearby.getUUID())) { continue; }
                if (nearby.hurtServer(level, this.damageSources().arrow(this, source), 3)) { source.fracture(nearby); }
            }
        }
    }
    @Override protected void onHitBlock(BlockHitResult result) { super.onHitBlock(result); if (!this.level().isClientSide()) { this.discard(); } }
    @Override public void tick() {
        if (!this.level().isClientSide() && (!(this.getOwner() instanceof SkeletonPhantom source) || !source.isAlive() || source.isBaby()
                || source.level() != this.level() || this.attack == null || !source.attacks().active(this.attack)
                || !source.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement())))) { this.discard(); return; }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (this.level() instanceof ServerLevel level) {
            if (this.tickCount >= 80 || this.travelled >= MAX_TRAVEL_DISTANCE || this.isInGround()) { this.discard(); }
            else if (!this.isRemoved() && this.tickCount % 2 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.SKELETON, this.position()); }
        }
    }
    @Override public void remove(RemovalReason reason) {
        if (this.getOwner() instanceof SkeletonPhantom source && this.attack != null) { source.attacks().release(this.attack, this.getUUID()); }
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("SkeletonArrowKind", this.kind.ordinal()); output.putBoolean("SkeletonRainArrow", this.rain);
        output.putFloat("SkeletonArrowDamage", this.impactDamage);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.kind = Kind.values()[Math.clamp(input.getIntOr("SkeletonArrowKind", 0), 0, Kind.values().length - 1)];
        this.rain = input.getBooleanOr("SkeletonRainArrow", false); this.impactDamage = Math.clamp(input.getFloatOr("SkeletonArrowDamage", 8), 1, 64);
        this.attack = null; this.pickup = Pickup.DISALLOWED;
    }
}
