package net.ronm19.phantomism.entity;

import net.ronm19.phantomism.animation.PhantomAnimationAction;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class EarthPhantom extends AbstractPhantom {
    private static final int BLESSING_INTERVAL = 100;
    private static final int BLESSING_RADIUS = 6;
    private static final int BLESSING_ATTEMPTS = 96;
    private static final int BLESSING_GROWTH_OPPORTUNITIES = 4;
    private static final int BOMB_COOLDOWN = 12 * 20;
    private static final double BOMB_RANGE = 28.0D;
    private static final int STAGGER_DURATION = 5 * 20;
    private static final int STAGGER_RECOVERY = 6 * 20;
    private static final String STAGGER_IMMUNITY = "PhantomismEarthStaggerImmuneUntil";
    private static final int SHIELD_COOLDOWN = 22 * 20;
    private static final int SHIELD_RESISTANCE_DURATION = 7 * 20;
    private static final int SHIELD_KNOCKBACK_DURATION = 10 * 20;
    private static final int WALL_COOLDOWN = 30 * 20;
    private static final int WALL_DURATION = 8 * 20;
    private static final int QUAKE_COOLDOWN = 75 * 20;
    private static final double QUAKE_RADIUS = 15.0D;
    private static final Identifier SHIELD_KNOCKBACK = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "earth_shield_knockback");

    private int bombCooldown;
    private int shieldCooldown;
    private int shieldTicks;
    private int wallCooldown;
    private int quakeCooldown;
    private int abilityRecovery;
    private int recentHeavyDamage;
    private long nextBlessing;
    private int repositionTicks;
    private Vec3 repositionDestination;

    public EarthPhantom(EntityType<? extends EarthPhantom> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 44.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 6.0D).add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean checkSpawnRules(EntityType<EarthPhantom> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getBiome(position).is(ModTags.Biomes.EARTH_PHANTOM_SPAWNS)
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).isAir();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected Config.PhantomStats getConfiguredStats() {
        return Config.EARTH_PHANTOM_STATS;
    }

    @Override
    protected boolean preservesCombatTargetWhileRidden() {
        return true;
    }

    @Override
    protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) {
        return this.launchBomb(level, rider.getViewVector(1.0F));
    }

    @Override
    protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        LivingEntity owner = this.getOwner();
        return this.isTame() && !this.isBaby() && this.getOrder() == Order.GUARD && owner != null
                && target instanceof Enemy && owner.distanceToSqr(target) <= 24.0D * 24.0D;
    }

    @Override
    protected double getAutomaticTargetPriority(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        double priority = owner != null ? owner.distanceToSqr(target) : this.distanceToSqr(target);
        return this.threatensFamily(target) ? priority * 0.25D : priority;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
        float previousHealth = this.getHealth();
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && previousHealth - this.getHealth() >= 3.0F) {
            this.recentHeavyDamage = 60;
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bombCooldown = Math.max(0, this.bombCooldown - 1);
        this.shieldCooldown = Math.max(0, this.shieldCooldown - 1);
        this.shieldTicks = Math.max(0, this.shieldTicks - 1);
        this.wallCooldown = Math.max(0, this.wallCooldown - 1);
        this.quakeCooldown = Math.max(0, this.quakeCooldown - 1);
        this.abilityRecovery = Math.max(0, this.abilityRecovery - 1);
        this.recentHeavyDamage = Math.max(0, this.recentHeavyDamage - 1);
        this.updateShield();
        if (this.isBaby()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (this.getControllingPassenger() instanceof Player rider && !this.isValidEarthTarget(target)
                && rider.getLastHurtByMob() != null
                && rider.tickCount - rider.getLastHurtByMobTimestamp() <= 40
                && this.isValidEarthTarget(rider.getLastHurtByMob())) {
            this.setTarget(rider.getLastHurtByMob());
            target = this.getTarget();
        }
        if (!this.isValidEarthTarget(target)) {
            this.repositionTicks = 0;
            this.tickBlessing(level);
            return;
        }
        if (this.repositionTicks > 0 && this.repositionDestination != null && !this.isVehicle()
                && this.getOrder() != Order.STAY && this.getOrder() != Order.GUARD_POSITION && this.getOrder() != Order.RETURN_HOME) {
            this.repositionTicks--;
            this.flyToAssignment(this.repositionDestination, 1.1D);
        }
        if (this.abilityRecovery > 0) {
            return;
        }
        boolean endangered = this.recentHeavyDamage > 0 || this.getHealth() <= this.getMaxHealth() * 0.55F
                || this.threatensFamily(target) && this.distanceToSqr(target) <= 36.0D;
        if (endangered && this.shieldCooldown == 0 && this.shieldTicks == 0) {
            this.activateShield();
            return;
        }
        LivingEntity protectedAlly = this.protectedAlly(target);
        boolean rangedThreat = target.getMainHandItem().is(Items.BOW) || target.getMainHandItem().is(Items.CROSSBOW);
        if (this.wallCooldown == 0 && (rangedThreat && this.threatensFamily(target)
                || this.getHealth() <= this.getMaxHealth() * 0.4F
                || protectedAlly != this && protectedAlly.getHealth() <= protectedAlly.getMaxHealth() * 0.4F)) {
            if (this.createWall(level, target, protectedAlly)) {
                return;
            }
        }
        if (this.quakeCooldown == 0 && this.performEarthquake(level)) {
            return;
        }
        if (!this.isVehicle() && this.bombCooldown == 0 && this.distanceToSqr(target) <= BOMB_RANGE * BOMB_RANGE
                && this.hasLineOfSight(target) && this.canStagger(level, target)
                && (this.distanceToSqr(target) >= 36.0D || this.threatensFamily(target))) {
            if (this.launchBomb(level, target.getEyePosition().subtract(this.projectileOrigin()))) {
                this.bombCooldown = BOMB_COOLDOWN;
                this.abilityRecovery = 20;
            }
        }
    }

    boolean isValidEarthTarget(LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved()
                && !(target instanceof AbstractPhantom) && !this.isAlliedTo(target)
                && !(target instanceof Player player && (this.isOwnedBy(player) || player.isSpectator()))
                && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null
                        && owned.getOwnerReference().equals(this.getOwnerReference()))
                && !this.isTargetSuppressed(target);
    }

    private boolean threatensFamily(LivingEntity threat) {
        if (!(threat instanceof Mob mob) || mob.getTarget() == null) {
            return threat == this.getLastHurtByMob();
        }
        LivingEntity victim = mob.getTarget();
        return victim == this || victim == this.getOwner() || this.isAlliedTo(victim)
                || victim instanceof OwnableEntity owned && owned.getOwnerReference() != null
                        && owned.getOwnerReference().equals(this.getOwnerReference());
    }

    private LivingEntity protectedAlly(LivingEntity threat) {
        if (threat instanceof Mob mob && this.threatensFamily(threat)) {
            LivingEntity victim = mob.getTarget();
            if (victim != null && victim.isAlive() && this.distanceToSqr(victim) <= 64.0D) {
                return victim;
            }
        }
        LivingEntity owner = this.getOwner();
        return owner != null && owner.isAlive() && owner.level() == this.level() && this.distanceToSqr(owner) <= 64.0D ? owner : this;
    }

    private void activateShield() {
        this.shieldTicks = SHIELD_KNOCKBACK_DURATION;
        this.shieldCooldown = SHIELD_COOLDOWN;
        this.animate(PhantomAnimationAction.SHIELD);
        this.abilityRecovery = 20;
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_RESISTANCE_DURATION, 1));
        this.updateShield();
        this.playSound(SoundEvents.STONE_PLACE, 1.0F, 0.65F);
    }

    private void updateShield() {
        var resistance = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (this.shieldTicks > 0) {
            if (!resistance.hasModifier(SHIELD_KNOCKBACK)) {
                resistance.addTransientModifier(new AttributeModifier(SHIELD_KNOCKBACK, 0.75D, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            resistance.removeModifier(SHIELD_KNOCKBACK);
        }
    }

    private Vec3 projectileOrigin() {
        double yaw = Math.toRadians(this.getYRot());
        double offset = this.getBbWidth() * 0.6D + 0.15D;
        return this.position().add(-Math.sin(yaw) * offset, this.getBbHeight() * 0.6D, Math.cos(yaw) * offset);
    }

    private boolean launchBomb(ServerLevel level, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        EarthBomb bomb = new EarthBomb(PhantomismContent.EARTH_BOMB.get(), level);
        bomb.setOwner(this);
        bomb.setPos(this.projectileOrigin());
        bomb.shoot(direction.x, direction.y, direction.z, 1.25F, 0.0F);
        if (!level.addFreshEntity(bomb)) {
            return false;
        }
        this.playSound(SoundEvents.SNOWBALL_THROW, 0.9F, 0.45F);
        this.animate(PhantomAnimationAction.PROJECTILE);
        return true;
    }

    private boolean canStagger(ServerLevel level, LivingEntity target) {
        return this.isValidEarthTarget(target) && !target.hasEffect(PhantomismContent.FROSTBOUND)
                && target.getPersistentData().getLongOr(STAGGER_IMMUNITY, 0L) <= level.getGameTime();
    }

    boolean applyStagger(ServerLevel level, LivingEntity target) {
        if (!this.canStagger(level, target)) {
            return false;
        }
        boolean resistant = target.is(ModTags.EntityTypes.EARTH_CONTROL_RESISTANT) || target.getMaxHealth() >= 80.0F;
        int duration = resistant ? 40 : STAGGER_DURATION;
        if (!target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, resistant ? 0 : 3), this)) {
            return false;
        }
        target.getPersistentData().putLong(STAGGER_IMMUNITY, level.getGameTime() + duration + STAGGER_RECOVERY);
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(movement.multiply(resistant ? 0.75D : 0.1D, 1.0D, resistant ? 0.75D : 0.1D));
        if (target instanceof Mob mob && !resistant) {
            mob.getNavigation().stop();
        }
        return true;
    }

    private boolean createWall(ServerLevel level, LivingEntity threat, LivingEntity ally) {
        Vec3 direction = threat.position().subtract(ally.position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 16.0D || direction.lengthSqr() > BOMB_RANGE * BOMB_RANGE) {
            return false;
        }
        direction = direction.normalize();
        EarthWall wall = new EarthWall(PhantomismContent.EARTH_WALL.get(), level);
        if (!this.positionWall(level, wall, threat, ally, direction) || !level.addFreshEntity(wall)) {
            wall.discard();
            return false;
        }
        this.wallCooldown = WALL_COOLDOWN;
        net.ronm19.phantomism.vfx.PhantomVisualEffects.aura(level,
                net.ronm19.phantomism.vfx.PhantomVfxTheme.EARTH, wall.position().add(0.0D, 1.0D, 0.0D));
        this.animate(PhantomAnimationAction.BARRIER);
        this.abilityRecovery = 30;
        this.heal(4.0F);
        if (ally != this) {
            ally.heal(2.0F);
        }
        Vec3 retreat = this.position().subtract(direction.scale(4.0D)).add(0.0D, 1.0D, 0.0D);
        AABB clearance = this.getBoundingBox().move(retreat.subtract(this.position()));
        if (!this.isVehicle() && this.isLoadedFlightBox(clearance) && level.noBlockCollision(this, clearance)) {
            this.repositionDestination = retreat;
            this.repositionTicks = 40;
        }
        this.playSound(SoundEvents.STONE_PLACE, 1.1F, 0.5F);
        return true;
    }

    private boolean positionWall(ServerLevel level, EarthWall wall, LivingEntity threat, LivingEntity ally, Vec3 direction) {
        for (double distance : new double[]{2.5D, 3.5D, 1.75D}) {
            Vec3 candidate = ally.position().add(direction.scale(distance));
            wall.configure(this, candidate, direction, WALL_DURATION);
            double ground = this.wallGroundHeight(level, wall.getBoundingBox(), ally.getY());
            if (!Double.isFinite(ground)) {
                continue;
            }
            wall.setPos(candidate.x, ground, candidate.z);
            AABB bounds = wall.getBoundingBox();
            if (!this.isLoadedFlightBox(bounds) || !level.noBlockCollision(wall, bounds)
                    || level.containsAnyLiquid(bounds) || bounds.intersects(ally.getBoundingBox())
                    || bounds.intersects(this.getBoundingBox()) || bounds.intersects(threat.getBoundingBox())
                    || bounds.clip(ally.getEyePosition(), threat.getEyePosition()).isEmpty()
                    || !level.getEntities(wall, bounds, entity -> entity.isAlive()
                            && (entity instanceof LivingEntity || entity instanceof EarthWall)).isEmpty()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private double wallGroundHeight(ServerLevel level, AABB bounds, double allyHeight) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double[] samplesX = {bounds.minX + 0.05D, bounds.getCenter().x, bounds.maxX - 0.05D};
        double[] samplesZ = {bounds.minZ + 0.05D, bounds.getCenter().z, bounds.maxZ - 0.05D};
        for (double sampleX : samplesX) {
            for (double sampleZ : samplesZ) {
                double surface = this.wallGroundHeightAt(level, sampleX, sampleZ, allyHeight + 0.75D, allyHeight - 4.0D);
                if (!Double.isFinite(surface)) {
                    return Double.NaN;
                }
                minimum = Math.min(minimum, surface);
                maximum = Math.max(maximum, surface);
            }
        }
        return maximum - minimum <= 0.625D ? maximum : Double.NaN;
    }

    private double wallGroundHeightAt(ServerLevel level, double positionX, double positionZ, double maximum, double minimum) {
        for (int height = (int) Math.floor(maximum); height >= (int) Math.floor(minimum); height--) {
            BlockPos position = BlockPos.containing(positionX, height, positionZ);
            if (!this.isLoadedFlightBox(new AABB(position)) || !level.getFluidState(position).isEmpty()) {
                return Double.NaN;
            }
            double highestSurface = Double.NEGATIVE_INFINITY;
            for (AABB support : level.getBlockState(position).getCollisionShape(level, position).toAabbs()) {
                double surface = height + support.maxY;
                if (positionX >= position.getX() + support.minX && positionX <= position.getX() + support.maxX
                        && positionZ >= position.getZ() + support.minZ && positionZ <= position.getZ() + support.maxZ
                        && surface <= maximum && surface >= minimum) {
                    highestSurface = Math.max(highestSurface, surface);
                }
            }
            if (Double.isFinite(highestSurface)) {
                return highestSurface;
            }
        }
        return Double.NaN;
    }

    private Vec3 quakeCenter(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        for (int depth = 0; depth <= 8; depth++) {
            BlockPos ground = origin.below(depth);
            if (!this.isLoadedFlightBox(new AABB(ground))) {
                return null;
            }
            if (level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) {
                return new Vec3(this.getX(), ground.getY() + 1.0D, this.getZ());
            }
        }
        return null;
    }

    private boolean performEarthquake(ServerLevel level) {
        Vec3 center = this.quakeCenter(level);
        if (center == null) {
            return false;
        }
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(QUAKE_RADIUS, 3.0D, QUAKE_RADIUS),
                target -> target instanceof Enemy && this.isValidEarthTarget(target)
                        && Math.abs(target.getY() - center.y) <= 3.0D
                        && net.ronm19.phantomism.entity.ai.PhantomAssignments.horizontalDistanceSquared(target.position(), center) <= QUAKE_RADIUS * QUAKE_RADIUS
                        && this.hasLineOfSight(target));
        if (enemies.size() < 3 || enemies.stream().noneMatch(target -> this.threatensFamily(target)
                || this.protectedAlly(target).distanceToSqr(target) <= 64.0D)) {
            return false;
        }
        this.quakeCooldown = QUAKE_COOLDOWN;
        this.animate(PhantomAnimationAction.ULTIMATE);
        this.abilityRecovery = 40;
        for (LivingEntity enemy : enemies) {
            boolean resistant = enemy.is(ModTags.EntityTypes.EARTH_CONTROL_RESISTANT) || enemy.getMaxHealth() >= 80.0F;
            if (!enemy.hurtServer(level, this.damageSources().mobAttack(this), resistant ? 4.0F : 8.0F)) {
                continue;
            }
            double susceptibility = Math.max(0.0D, 1.0D - enemy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 outward = enemy.position().subtract(center).multiply(1.0D, 0.0D, 1.0D).normalize();
            double strength = (resistant ? 0.2D : 0.8D) * susceptibility;
            double lift = (resistant ? 0.25D : 1.3D) * susceptibility;
            Vec3 motion = enemy.getDeltaMovement();
            enemy.setDeltaMovement(motion.add(outward.scale(strength)).with(net.minecraft.core.Direction.Axis.Y, Math.max(motion.y, lift)));
            enemy.syncVelocity = true;
        }
        net.ronm19.phantomism.vfx.PhantomVisualEffects.shockwave(level,
                net.ronm19.phantomism.vfx.PhantomVfxTheme.EARTH, center.add(0.0D, 0.15D, 0.0D), 6.0F);
        for (int segment = 0; segment < 32; segment++) {
            double angle = segment * Math.PI / 16.0D;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    center.x + Math.cos(angle) * 7.0D, center.y + 0.15D, center.z + Math.sin(angle) * 7.0D,
                    3, 0.3D, 0.2D, 0.3D, 0.15D);
        }
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.55F);
        return true;
    }

    private void tickBlessing(ServerLevel level) {
        if (level.getGameTime() < this.nextBlessing || this.isVehicle()
                || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, this)) {
            return;
        }
        this.nextBlessing = level.getGameTime() + BLESSING_INTERVAL;
        int opportunities = 0;
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        for (int attempt = 0; attempt < BLESSING_ATTEMPTS && opportunities < BLESSING_GROWTH_OPPORTUNITIES; attempt++) {
            int positionX = this.blockPosition().getX() + this.getRandom().nextInt(BLESSING_RADIUS * 2 + 1) - BLESSING_RADIUS;
            int positionZ = this.blockPosition().getZ() + this.getRandom().nextInt(BLESSING_RADIUS * 2 + 1) - BLESSING_RADIUS;
            if (!level.hasChunk(net.minecraft.core.SectionPos.blockToSectionCoord(positionX), net.minecraft.core.SectionPos.blockToSectionCoord(positionZ))) {
                continue;
            }
            BlockPos position = new BlockPos(positionX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, positionX, positionZ), positionZ);
            if (!visited.add(position) || Math.abs(position.getY() - this.getY()) > 10.0D
                    || !this.isLoadedFlightBox(new AABB(position).inflate(1.0D))) {
                continue;
            }
            BlockState state = level.getBlockState(position);
            if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)
                    || !level.getBlockState(position.below()).is(Blocks.FARMLAND)
                    || this.getOwner() instanceof ServerPlayer owner && !level.mayInteract(owner, position)) {
                continue;
            }
            var hit = level.clip(new ClipContext(this.getEyePosition(), Vec3.atCenterOf(position),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(position)) {
                continue;
            }
            opportunities++;
            state.randomTick(level, position, this.getRandom());
            if (!level.getBlockState(position).equals(state)) {
                this.animate(PhantomAnimationAction.BLESSING);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, positionX + 0.5D, position.getY() + 0.6D, positionZ + 0.5D,
                        2, 0.2D, 0.15D, 0.2D, 0.0D);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("EarthBombCooldown", this.bombCooldown);
        output.putInt("EarthShieldCooldown", this.shieldCooldown);
        output.putInt("EarthShieldTicks", this.shieldTicks);
        output.putInt("EarthWallCooldown", this.wallCooldown);
        output.putInt("EarthquakeCooldown", this.quakeCooldown);
        output.putLong("EarthBlessingNext", this.nextBlessing);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.bombCooldown = Math.min(BOMB_COOLDOWN, Math.max(0, input.getIntOr("EarthBombCooldown", 0)));
        this.shieldCooldown = Math.min(SHIELD_COOLDOWN, Math.max(0, input.getIntOr("EarthShieldCooldown", 0)));
        this.shieldTicks = Math.min(SHIELD_KNOCKBACK_DURATION, Math.max(0, input.getIntOr("EarthShieldTicks", 0)));
        this.wallCooldown = Math.min(WALL_COOLDOWN, Math.max(0, input.getIntOr("EarthWallCooldown", 0)));
        this.quakeCooldown = Math.min(QUAKE_COOLDOWN, Math.max(0, input.getIntOr("EarthquakeCooldown", 0)));
        this.nextBlessing = input.getLongOr("EarthBlessingNext", 0L);
    }
}
