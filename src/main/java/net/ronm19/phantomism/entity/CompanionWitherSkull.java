package net.ronm19.phantomism.entity;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class CompanionWitherSkull extends WitherSkull {
    private @Nullable UUID session;
    private @Nullable UUID intended;
    private @Nullable Vec3 center;
    private Set<UUID> allocated = Set.of();
    private int delay;
    private int age;
    private double travelled;
    private boolean launched;
    private boolean storm;
    private boolean impacted;

    public CompanionWitherSkull(EntityType<? extends CompanionWitherSkull> type, Level level) { super(type, level); this.accelerationPower = 0; }
    public void configure(UUID session, UUID intended, int delay, boolean storm, @Nullable Vec3 center) {
        this.session = session; this.intended = intended; this.delay = Math.clamp(delay, 0, 70); this.storm = storm; this.center = center;
        this.travelled = storm ? 0 : 0.9D; this.setDeltaMovement(Vec3.ZERO);
        if (this.getOwner() instanceof WitherPhantom source) { this.allocated = Set.copyOf(source.allocation()); }
    }
    @Override protected float getInertia() { return 1; }
    @Override protected float getLiquidInertia() { return 1; }
    @Override protected net.minecraft.core.particles.@Nullable ParticleOptions getTrailParticle() { return null; }
    private boolean valid(WitherPhantom source, LivingEntity target) {
        return source.projectileTarget(target) && (this.center == null || target.position().distanceToSqr(this.center) <= 25 * 25)
                && source.distanceToSqr(target) <= 56 * 56;
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return this.launched && super.canHitEntity(entity) && this.getOwner() instanceof WitherPhantom source
                && entity instanceof LivingEntity target && this.valid(source, target);
    }
    private boolean launch(ServerLevel level, WitherPhantom source) {
        LivingEntity target = this.intended != null && level.getEntity(this.intended) instanceof LivingEntity enemy && this.valid(source, enemy)
                && source.line(level, this.position(), enemy.getEyePosition()) && (!this.storm || enemy.getY() < this.getY() - 0.5D)
                ? enemy : source.redirect(level, this.position(), this.center);
        if (target == null) { return false; }
        this.intended = target.getUUID();
        Vec3 aim = target.getBoundingBox().getCenter().subtract(this.position());
        if (aim.lengthSqr() < 0.01D || aim.lengthSqr() > WitherPhantom.SKULL_RANGE * WitherPhantom.SKULL_RANGE) { return false; }
        this.launched = true; this.setDeltaMovement(aim.normalize().scale(this.storm ? 1.4D : 1.3D)); this.needsSync = true; return true;
    }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel level) {
            if (++this.age > 130 || !(this.getOwner() instanceof WitherPhantom source) || !source.combatEnabled() || !source.session().equals(this.session)
                    || this.travelled >= WitherPhantom.SKULL_RANGE || !source.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))
                    || !level.getWorldBorder().isWithinBounds(this.getBoundingBox())) { this.discard(); return; }
            if (this.delay > 0) { this.delay--; this.setDeltaMovement(Vec3.ZERO); super.tick(); this.trail(level, true); return; }
            if (!this.launched && !this.launch(level, source)) { this.discard(); return; }
            double remaining = WitherPhantom.SKULL_RANGE - this.travelled;
            if (this.getDeltaMovement().length() > remaining) { this.setDeltaMovement(this.getDeltaMovement().normalize().scale(remaining)); }
            if (!source.isLoadedFlightBox(this.getBoundingBox().expandTowards(this.getDeltaMovement()))) { this.discard(); return; }
        }
        Vec3 previous = this.position(); super.tick(); this.travelled += this.position().distanceTo(previous);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            if (this.travelled >= WitherPhantom.SKULL_RANGE - 0.0001D) { this.discard(); } else { this.trail(level, false); }
        }
    }
    private void trail(ServerLevel level, boolean charging) {
        if (this.age % (charging ? 8 : 3) != 0) { return; }
        PhantomVisualEffects.orbit(level, PhantomVfxTheme.WITHER, this.position(), charging ? 0.45F : 0.2F, charging ? 2 : 1);
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.08D, 0.08D, 0.08D, 0.008D);
    }
    @Override protected void onHit(HitResult hit) {
        if (this.impacted || !(this.level() instanceof ServerLevel level) || !(this.getOwner() instanceof WitherPhantom source)
                || !source.combatEnabled() || !source.session().equals(this.session)) { return; }
        this.impacted = true;
        LivingEntity direct = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target ? target : null;
        if (direct != null && this.valid(source, direct)) { source.damage(level, direct, this.damageSources().witherSkull(this, source), 12, true); }
        Vec3 impact = hit.getLocation();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(1.8D), source::projectileTarget).stream().limit(16).toList()) {
            if (target != direct && !this.allocated.contains(target.getUUID()) && this.valid(source, target) && target.getBoundingBox().distanceToSqr(impact) <= 1.8D * 1.8D
                    && source.line(level, impact.add(0, 0.1D, 0), target.getEyePosition())) { source.damage(level, target, this.damageSources().witherSkull(this, source), 3, true); }
        }
        PhantomVisualEffects.impact(level, PhantomVfxTheme.WITHER, impact); PhantomVisualEffects.shockwave(level, PhantomVfxTheme.WITHER, impact, 2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y, impact.z, 4, 0.3D, 0.3D, 0.3D, 0.025D);
        this.discard();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.age = 131; this.session = null; }
}
