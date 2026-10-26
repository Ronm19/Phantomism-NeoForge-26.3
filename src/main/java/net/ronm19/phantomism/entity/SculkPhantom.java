package net.ronm19.phantomism.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.entity.spawn.SculkCitySpawner;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class SculkPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/sculk_phantom");
    private static final Identifier DIVE_DAMAGE = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sonic_dive_damage");
    private static final int BOOM_COOLDOWN = 10 * 20;
    private static final int SPREAD_COOLDOWN = 24 * 20;
    private static final int SPREAD_DURATION = 8 * 20;
    private static final int DIVE_COOLDOWN = 14 * 20;
    private static final int SHIELD_COOLDOWN = 28 * 20;
    private static final int SHIELD_DURATION = 10 * 20;
    private static final int SILENCE_COOLDOWN = 30 * 20;
    private static final int SILENCE_DURATION = 6 * 20;
    private static final int EYE_COOLDOWN = 80 * 20;
    private int boomCooldown;
    private int spreadCooldown;
    private int spreadTicks;
    private int diveCooldown;
    private int shieldCooldown;
    private int shieldTicks;
    private int silenceCooldown;
    private int silenceTicks;
    private int eyeCooldown;
    private int counterCooldown;
    private int abilityRecovery;
    private int recentDamageTicks;
    private @Nullable Vec3 spreadCenter;

    public SculkPhantom(EntityType<? extends SculkPhantom> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0D).add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 8.0D).add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<SculkPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        ServerLevel server = level.getLevel();
        if (!server.hasChunkAt(position) || !level.getFluidState(position).isEmpty()
                || !level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                || !level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()) { return false; }
        var city = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.ANCIENT_CITY);
        if (city == null) { return false; }
        var start = server.structureManager().getStructureAt(position, city);
        return start.isValid() && SculkCitySpawner.countWild(server, start) < SculkCitySpawner.MAX_WILD_PER_CITY;
    }

    @Override
    public int getMaxSpawnClusterSize() { return 2; }

    @Override
    protected Config.PhantomStats getConfiguredStats() { return Config.SCULK_PHANTOM_STATS; }

    @Override
    public Identifier getPhantomAnimationResource() { return ANIMATIONS; }

    @Override
    protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }

    @Override
    public boolean dampensVibrations() { return true; }

    @Override
    protected boolean preservesCombatTargetWhileRidden() { return true; }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.sonicBeam(level, rider.getViewVector(1.0F), false);
    }

    @Override
    protected int getMeleeAttackCooldownTicks() { return 65; }

    @Override
    protected int getAttackRecoveryTicks() { return 28; }

    private boolean threatensFamily(LivingEntity target) {
        return target == this.getLastHurtByMob() || target instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return !this.isBaby() && this.isTame() && this.getOrder() == Order.GUARD && target instanceof Enemy
                && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 28.0D * 28.0D
                && (!(target instanceof Warden) || this.threatensFamily(target));
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target) * (this.threatensFamily(target) ? 0.15D : target instanceof Warden ? 0.6D : 1.0D);
    }

    private boolean validTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && target != this && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target) && !this.isTargetSuppressed(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean areaEnemy(LivingEntity target) {
        return this.validTarget(target) && (target == this.getTarget() || target instanceof Enemy || this.threatensFamily(target));
    }

    private boolean clearLine(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.1D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, double height, boolean throughWalls) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, height, radius), enemy ->
                this.areaEnemy(enemy) && enemy.position().subtract(center).horizontalDistanceSqr() <= radius * radius
                        && Math.abs(enemy.getY() - center.y) <= height
                        && (throughWalls || this.clearLine(level, center.add(0.0D, 0.6D, 0.0D), enemy.getEyePosition())))
                .stream().sorted(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(center))).limit(16).toList();
    }

    private boolean hurtEnemy(ServerLevel level, LivingEntity enemy, float damage) {
        if (this.isBaby() || !this.isAlive() || !this.validTarget(enemy)) { return false; }
        float previous = enemy.getHealth() + enemy.getAbsorptionAmount();
        boolean hit = enemy instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), damage)
                : enemy.hurtServer(level, this.damageSources().mobAttack(this), damage);
        return hit && enemy.getHealth() + enemy.getAbsorptionAmount() < previous;
    }

    private void disrupt(LivingEntity enemy, int duration) {
        if (this.validTarget(enemy)) { enemy.addEffect(new MobEffectInstance(PhantomismContent.SONIC_DISRUPTION, duration), this); }
    }

    private Vec3 sonicOrigin(Vec3 direction) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot()).normalize();
        return this.position().add(forward.scale(1.25D)).add(0.0D, this.getBbHeight() * 0.65D, 0.0D).add(direction.scale(0.15D));
    }

    private boolean sonicBeam(ServerLevel level, Vec3 aim, boolean ultimate) {
        if (this.isBaby() || !this.isAlive() || aim.lengthSqr() < 1.0E-6D
                || !Double.isFinite(aim.x) || !Double.isFinite(aim.y) || !Double.isFinite(aim.z)) { return false; }
        Vec3 direction = aim.normalize();
        Vec3 start = this.sonicOrigin(direction);
        Vec3 end = start.add(direction.scale(ultimate ? 45.0D : 36.0D));
        if (!this.isLoadedFlightBox(new AABB(start, end).inflate(0.5D))) { return false; }
        var obstruction = level.clip(new ClipContext(this.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (obstruction.getType() != HitResult.Type.MISS) {
            end = obstruction.getLocation();
            if (end.subtract(start).dot(direction) <= 0.0D) { return false; }
        }
        Vec3 finish = end;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, finish).inflate(ultimate ? 0.85D : 0.45D), enemy ->
                this.areaEnemy(enemy) && enemy.getBoundingBox().inflate(ultimate ? 0.85D : 0.45D).clip(start, finish).isPresent())
                .stream().sorted(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(start))).limit(ultimate ? 12 : 8).toList();
        boolean counterUsed = false;
        for (LivingEntity enemy : victims) {
            float damage = ultimate ? 20.0F : 7.0F;
            if (enemy instanceof Warden) { damage *= ultimate ? 2.0F : 1.5F; }
            if (this.hurtEnemy(level, enemy, damage)) {
                this.disrupt(enemy, ultimate ? 100 : 60);
                if (enemy instanceof Warden && this.counterCooldown == 0 && !counterUsed) {
                    enemy.push(direction.x * 1.1D, 0.18D, direction.z * 1.1D);
                    counterUsed = true;
                } else if (!(enemy instanceof Warden) && enemy.getMaxHealth() < 80.0F) {
                    double push = 0.65D * (1.0D - enemy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    enemy.push(direction.x * push, Math.max(0.0D, direction.y) * push + 0.08D, direction.z * push);
                }
                PhantomVisualEffects.impact(level, PhantomVfxTheme.SCULK, enemy.getBoundingBox().getCenter());
            }
        }
        if (counterUsed) { this.counterCooldown = 10 * 20; }
        this.animate(ultimate ? PhantomAnimationAction.ULTIMATE : PhantomAnimationAction.PROJECTILE);
        PhantomVisualEffects.sonicBeam(level, start, finish, ultimate);
        this.playSound(SoundEvents.WARDEN_SONIC_BOOM, ultimate ? 1.0F : 0.65F, ultimate ? 0.75F : 1.3F);
        return true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isBaby() || !(target instanceof LivingEntity enemy) || !this.validTarget(enemy)) { return false; }
        boolean dive = this.isDivingStrike() && this.diveCooldown == 0;
        if (dive) { this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(DIVE_DAMAGE, 4.0D, AttributeModifier.Operation.ADD_VALUE)); }
        float previous = enemy.getHealth() + enemy.getAbsorptionAmount();
        boolean hit;
        try {
            hit = enemy instanceof EnderDragon ? this.hurtEnemy(level, enemy, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                    : super.doHurtTarget(level, target);
        } finally { if (dive) { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(DIVE_DAMAGE); } }
        hit &= enemy.getHealth() + enemy.getAbsorptionAmount() < previous;
        if (hit && dive) {
            this.diveCooldown = DIVE_COOLDOWN;
            this.disrupt(enemy, 60);
            if (!(enemy instanceof Warden) && enemy.getMaxHealth() < 80.0F) {
                Vec3 away = enemy.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                enemy.push(away.x * 0.8D, 0.15D, away.z * 0.8D);
            }
            for (LivingEntity nearby : this.enemies(level, enemy.position(), 3.0D, 2.5D, false)) {
                if (nearby != enemy && this.hurtEnemy(level, nearby, 4.0F)) {
                    this.disrupt(nearby, 40);
                    if (!(nearby instanceof Warden) && nearby.getMaxHealth() < 80.0F) {
                        Vec3 away = nearby.position().subtract(enemy.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                        nearby.push(away.x * 0.6D, 0.12D, away.z * 0.6D);
                    }
                }
            }
            this.animate(PhantomAnimationAction.CHARGE);
            PhantomVisualEffects.impact(level, PhantomVfxTheme.SCULK, enemy.position().add(0.0D, 0.5D, 0.0D));
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.SCULK, enemy.position(), 3.0F);
        }
        return hit;
    }

    public boolean isSculkShieldActive() { return this.shieldTicks > 0 && this.hasEffect(MobEffects.RESISTANCE); }

    public boolean isSilencing(LivingEntity enemy) {
        return this.silenceTicks > 0 && !this.isBaby() && this.isAlive() && this.areaEnemy(enemy)
                && this.distanceToSqr(enemy) <= 10.0D * 10.0D && this.level() instanceof ServerLevel level
                && this.clearLine(level, this.getEyePosition(), enemy.getEyePosition());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isSculkShieldActive() && source.getEntity() instanceof LivingEntity) {
            damage *= source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_EFFECTS) ? 0.25F : 5.0F / 12.0F;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) { this.recentDamageTicks = 60; }
        return hurt;
    }

    @Override
    protected boolean prepareCombatApproach(ServerLevel level, LivingEntity target) {
        boolean charging = target instanceof Warden warden && warden.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
        if (!(target instanceof Warden) && this.diveCooldown == 0 && this.abilityRecovery == 0 && this.distanceToSqr(target) < 144.0D) { return false; }
        if (target instanceof Warden && this.isSculkShieldActive() && !charging && this.diveCooldown == 0 && this.boomCooldown > 0) { return false; }
        Vec3 away = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (away.lengthSqr() < 0.01D) { away = new Vec3(1.0D, 0.0D, 0.0D); }
        Vec3 side = new Vec3(-away.z, 0.0D, away.x).scale(Math.sin(this.tickCount * 0.045D + this.getId()) * 2.0D);
        Vec3 destination = target.position().add(away.scale(target instanceof Warden ? 19.0D : 12.0D)).add(side).add(0.0D, 3.0D, 0.0D);
        if (this.isLoadedFlightBox(this.getBoundingBox().move(destination.subtract(this.position()))) && level.noBlockCollision(this,
                this.getBoundingBox().move(destination.subtract(this.position())))) {
            this.moveControl.setWantedPosition(destination.x, destination.y, destination.z, charging ? 1.4D : 1.1D);
            return true;
        }
        return false;
    }

    @Override
    public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation);
        this.cancelFields();
    }

    private void cancelFields() { this.spreadTicks = 0; this.spreadCenter = null; this.silenceTicks = 0; }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) { return; }
        this.boomCooldown = Math.max(0, this.boomCooldown - 1);
        this.spreadCooldown = Math.max(0, this.spreadCooldown - 1);
        this.diveCooldown = Math.max(0, this.diveCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.shieldTicks = Math.max(0, this.shieldTicks - 1);
        this.silenceCooldown = Math.max(0, this.silenceCooldown - 1);
        this.eyeCooldown = Math.max(0, this.eyeCooldown - 1);
        this.counterCooldown = Math.max(0, this.counterCooldown - 1);
        this.abilityRecovery = Math.max(0, this.abilityRecovery - 1);
        this.recentDamageTicks = Math.max(0, this.recentDamageTicks - 1);
        if (this.isBaby() || !this.isAlive()) { this.cancelFields(); this.shieldTicks = 0; return; }
        if (this.isTame() && this.tickCount % 40 == 0) { this.vibrationSense(level); }
        this.tickFields(level);
        LivingEntity target = this.getTarget();
        if (!this.validTarget(target) || !this.hasLineOfSight(target)) { return; }
        if (this.tickCount % 10 == 0) { this.planAbilities(level, target); }
        if (this.getControllingPassenger() == null && this.boomCooldown == 0 && this.abilityRecovery == 0
                && this.distanceToSqr(target) <= 36.0D * 36.0D
                && this.sonicBeam(level, target.getEyePosition().subtract(this.sonicOrigin(this.getLookAngle())), false)) {
            this.boomCooldown = BOOM_COOLDOWN;
            this.abilityRecovery = 10;
        }
    }

    private void vibrationSense(ServerLevel level) {
        List<LivingEntity> sensed = this.enemies(level, this.position(), 28.0D, 16.0D, true).stream().limit(8).toList();
        for (LivingEntity enemy : sensed) { enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50), this); }
        if (!sensed.isEmpty() && this.tickCount % 80 == 0) { this.animate(PhantomAnimationAction.SENSE); }
    }

    private void planAbilities(ServerLevel level, LivingEntity target) {
        boolean wardenCharging = target instanceof Warden warden && warden.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
        if (this.shieldCooldown == 0 && (wardenCharging || target instanceof Warden && this.distanceToSqr(target) < 225.0D
                || this.recentDamageTicks > 0 || this.getHealth() < this.getMaxHealth() * 0.6F)
                && !this.hasEffect(MobEffects.RESISTANCE)) {
            this.shieldCooldown = SHIELD_COOLDOWN;
            this.shieldTicks = SHIELD_DURATION;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_DURATION, 1), this);
            this.animate(PhantomAnimationAction.SHIELD);
        }
        if (this.silenceCooldown == 0 && this.distanceToSqr(target) <= 100.0D
                && (wardenCharging || target instanceof Warden || this.enemies(level, this.position(), 10.0D, 6.0D, false).size() >= 3)) {
            this.silenceCooldown = SILENCE_COOLDOWN;
            this.silenceTicks = SILENCE_DURATION;
            this.animate(PhantomAnimationAction.BARRIER);
            this.tickSilence(level);
        }
        if (this.abilityRecovery > 0) { return; }
        if (this.eyeCooldown == 0 && this.distanceToSqr(target) <= 45.0D * 45.0D
                && (target instanceof Warden || target.getMaxHealth() >= 80.0F || this.alignedEnemies(level, target) >= 3)
                && this.sonicBeam(level, target.getEyePosition().subtract(this.sonicOrigin(this.getLookAngle())), true)) {
            this.eyeCooldown = EYE_COOLDOWN;
            this.abilityRecovery = 25;
        } else if (this.spreadCooldown == 0 && (this.enemies(level, target.position(), 6.0D, 3.0D, false).size() >= 2
                || target instanceof Warden && this.threatensFamily(target))) {
            var ground = level.clip(new ClipContext(target.position().add(0.0D, 1.0D, 0.0D), target.position().add(0.0D, -5.0D, 0.0D),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (ground.getType() == HitResult.Type.BLOCK) {
                this.spreadCenter = ground.getLocation().add(0.0D, 0.1D, 0.0D);
                this.spreadCooldown = SPREAD_COOLDOWN;
                this.spreadTicks = SPREAD_DURATION;
                this.abilityRecovery = 15;
                this.animate(PhantomAnimationAction.EMPOWER);
            }
        }
    }

    private int alignedEnemies(ServerLevel level, LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 finish = start.add(target.getEyePosition().subtract(start).normalize().scale(45.0D));
        return (int) this.enemies(level, this.position(), 45.0D, 12.0D, false).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(0.85D).clip(start, finish).isPresent()).count();
    }

    private boolean validCenter(@Nullable Vec3 center) {
        return center != null && Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
                && Math.abs(center.x) < 30_000_000 && Math.abs(center.z) < 30_000_000 && this.distanceToSqr(center) < 64.0D * 64.0D
                && this.isLoadedFlightBox(new AABB(center, center).inflate(6.0D, 4.0D, 6.0D));
    }

    private void tickFields(ServerLevel level) {
        if (this.spreadTicks > 0) {
            if (!this.validCenter(this.spreadCenter)) { this.spreadTicks = 0; this.spreadCenter = null; }
            else {
                if (this.spreadTicks % 20 == 0) {
                    for (LivingEntity enemy : this.enemies(level, this.spreadCenter, 6.0D, 3.0D, false)) {
                        if (this.hurtEnemy(level, enemy, 2.0F)) {
                            this.disrupt(enemy, 40);
                            if (!(enemy instanceof Warden) && enemy.getMaxHealth() < 80.0F) {
                                enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30), this);
                            }
                        }
                    }
                    PhantomVisualEffects.sculkSpread(level, this.spreadCenter);
                }
                if (--this.spreadTicks == 0) { this.spreadCenter = null; }
            }
        }
        if (this.silenceTicks > 0) {
            if (this.silenceTicks % 10 == 0) { this.tickSilence(level); }
            if (this.silenceTicks % 20 == 0) { PhantomVisualEffects.shockwave(level, PhantomVfxTheme.SCULK, this.position(), 5.0F); }
            this.silenceTicks--;
        }
        if ((this.getPhantomAnimationFlags() & 1) != 0 && this.tickCount % 4 == 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.SCULK, this.position()); }
    }

    private void tickSilence(ServerLevel level) {
        for (LivingEntity enemy : this.enemies(level, this.position(), 10.0D, 6.0D, false)) {
            if (!this.isSilencing(enemy)) { continue; }
            this.disrupt(enemy, 30);
            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30), this);
            if (enemy instanceof Warden warden) {
                warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, 20L);
                warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, Unit.INSTANCE, 12L);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SonicBoomCooldown", this.boomCooldown);
        output.putInt("SculkSpreadCooldown", this.spreadCooldown);
        output.putInt("SculkSpreadTicks", this.spreadTicks);
        output.putInt("SonicDiveCooldown", this.diveCooldown);
        output.putInt("SculkShieldCooldown", this.shieldCooldown);
        output.putInt("SculkShieldTicks", this.shieldTicks);
        output.putInt("SilenceCooldown", this.silenceCooldown);
        output.putInt("SilenceTicks", this.silenceTicks);
        output.putInt("SculkEyeCooldown", this.eyeCooldown);
        output.putInt("WardenCounterCooldown", this.counterCooldown);
        output.storeNullable("SculkSpreadCenter", Vec3.CODEC, this.spreadCenter);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.boomCooldown = Math.clamp(input.getIntOr("SonicBoomCooldown", 0), 0, BOOM_COOLDOWN);
        this.spreadCooldown = Math.clamp(input.getIntOr("SculkSpreadCooldown", 0), 0, SPREAD_COOLDOWN);
        this.diveCooldown = Math.clamp(input.getIntOr("SonicDiveCooldown", 0), 0, DIVE_COOLDOWN);
        this.shieldCooldown = Math.clamp(input.getIntOr("SculkShieldCooldown", 0), 0, SHIELD_COOLDOWN);
        this.shieldTicks = this.isBaby() ? 0 : Math.clamp(input.getIntOr("SculkShieldTicks", 0), 0, SHIELD_DURATION);
        this.silenceCooldown = Math.clamp(input.getIntOr("SilenceCooldown", 0), 0, SILENCE_COOLDOWN);
        this.silenceTicks = this.isBaby() ? 0 : Math.clamp(input.getIntOr("SilenceTicks", 0), 0, SILENCE_DURATION);
        this.eyeCooldown = Math.clamp(input.getIntOr("SculkEyeCooldown", 0), 0, EYE_COOLDOWN);
        this.counterCooldown = Math.clamp(input.getIntOr("WardenCounterCooldown", 0), 0, 10 * 20);
        this.spreadCenter = input.read("SculkSpreadCenter", Vec3.CODEC).orElse(null);
        this.spreadTicks = this.isBaby() || !this.validCenter(this.spreadCenter) ? 0
                : Math.clamp(input.getIntOr("SculkSpreadTicks", 0), 0, SPREAD_DURATION);
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Boom Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(BOOM_COOLDOWN), "Spread Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(SPREAD_COOLDOWN), "Dive Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(DIVE_COOLDOWN), "Shield Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(SHIELD_COOLDOWN), "Silence Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(SILENCE_COOLDOWN), "Eye Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(EYE_COOLDOWN));
    }
}
