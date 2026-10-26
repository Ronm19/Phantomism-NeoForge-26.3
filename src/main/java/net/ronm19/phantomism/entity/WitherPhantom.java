package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.entity.ai.WitherCombatEffects;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class WitherPhantom extends AbstractPhantom implements net.ronm19.phantomism.entity.ai.CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/wither_phantom");
    public static final double SKULL_RANGE = 45;
    public static final int STORM_SKULLS = 14;
    public static final int STORM_MINIMUM = 11;
    private static final int SKULL_COOLDOWN = 160;
    private static final int MOUNTED_SKULL_COOLDOWN = 6;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(WitherPhantom.class, EntityDataSerializers.BYTE);
    private static final String[] CLIPS = {"bite", "skull", "multishot", "siege", "rush", "carapace", "storm", "warn"};
    private UUID session = UUID.randomUUID();
    private int skullCooldown;
    private int multishotCooldown;
    private int siegeCooldown;
    private int biteCooldown;
    private int rushCooldown;
    private int carapaceCooldown;
    private int stormCooldown;
    private int rushTicks;
    private int carapaceTicks;
    private int stormTicks;
    private int actionTicks;
    private int warningCooldown;
    private int meleeCooldown;
    private int queueAge;
    private int queueIndex;
    private int biteWindup;
    private int combatStarted;
    private int progressTick;
    private double closest = Double.MAX_VALUE;
    private @Nullable UUID pursuit;
    private @Nullable UUID commandedSkeleton;
    private @Nullable UUID biting;
    private @Nullable Vec3 stormCenter;
    private List<UUID> siegePlan = List.of();
    private java.util.Set<UUID> allocation = java.util.Set.of();

    public WitherPhantom(EntityType<? extends WitherPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 108).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.65D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.425D).add(Attributes.FOLLOW_RANGE, 56);
    }
    public static boolean checkSpawnRules(EntityType<WitherPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        return level.getLevel().dimension() == Level.NETHER && level.getBiome(position).is(ModTags.Biomes.WITHER_PHANTOM_SPAWNS)
                && position.getY() < 120 && level.getFluidState(position).isEmpty()
                && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.WITHER_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getMeleeAttackCooldownTicks() { return 34; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_SKULL_COOLDOWN; }
    @Override public void travel(Vec3 input) { if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * 0.09D)); } else { super.travel(input); } }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, (byte) 0); }
    private void state(int flag, boolean enabled) { int current = this.entityData.get(STATE); this.entityData.set(STATE, (byte) (enabled ? current | flag : current & ~flag)); }
    public boolean isWitherRushing() { return (this.entityData.get(STATE) & 1) != 0; }
    public boolean hasCarapace() { return (this.entityData.get(STATE) & 2) != 0; }
    public boolean hasWitherStorm() { return (this.entityData.get(STATE) & 4) != 0; }
    @Override public boolean isUltimateAnimationActive() { return this.hasWitherStorm() || super.isUltimateAnimationActive(); }
    public UUID session() { return this.session; }
    public java.util.Set<UUID> allocation() { return this.allocation; }
    public boolean combatEnabled() {
        return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME
                && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT)
                && (!this.isTame() || this.getOwner() != null && this.getOwner().isAlive() && this.getOwner().level() == this.level());
    }
    public boolean provokedBy(LivingEntity target) {
        return target == this.getLastHurtByMob() && this.tickCount - this.getLastHurtByMobTimestamp() <= 200
                || this.getOwner() != null && (this.getOwner().getLastHurtByMob() == target && this.getOwner().tickCount - this.getOwner().getLastHurtByMobTimestamp() <= 200
                || this.getOwner().getLastHurtMob() == target && this.getOwner().tickCount - this.getOwner().getLastHurtMobTimestamp() <= 200);
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target instanceof Player player) { return !this.isTame() && !player.isCreative() && !player.isSpectator() && this.provokedBy(player) && this.distanceToSqr(player) <= 16 * 16; }
        if (target instanceof WitherSkeleton) { return this.provokedBy(target) || target.getUUID().equals(this.commandedSkeleton); }
        boolean threatening = this.provokedBy(target) || target instanceof Mob mob && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner());
        return target instanceof Enemy && (!(target instanceof NeutralMob) && target.getType() != net.minecraft.world.entity.EntityTypes.PIGLIN || threatening);
    }
    public boolean validTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && !(target instanceof AbstractPhantom) && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null)
                && target != this.getOwner() && !this.hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !this.isAlliedTo(target)
                && !target.isInvulnerable() && this.isSpeciesCombatTarget(target) && !this.isTargetSuppressed(target)
                && !(target instanceof net.minecraft.world.entity.monster.Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob));
    }
    public boolean projectileTarget(@Nullable LivingEntity target) {
        return this.validTarget(target) && (!(target instanceof WitherBoss boss) || boss.getInvulnerableTicks() == 0);
    }
    @Override public void setCombatTarget(LivingEntity target) {
        if (target instanceof WitherSkeleton && this.isTame()) { this.commandedSkeleton = target.getUUID(); }
        super.setCombatTarget(target);
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && this.isLoadedFlightBox(new AABB(start, end).inflate(0.05D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.validTarget(target) && !(target instanceof WitherSkeleton)
                && (!this.isTame() || this.getOrder() == Order.GUARD || target instanceof WitherBoss
                || target instanceof Mob mob && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner()));
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return this.priority(target); }
    private double priority(LivingEntity target) {
        double score = target instanceof WitherBoss ? -1000000 : target == this.getTarget() ? -100000 : 0;
        if (target instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == this || this.isAlliedTo(mob.getTarget()) || mob.getTarget() == this.getOwner())) { score -= 10000; }
        if (target instanceof RangedAttackMob) { score -= 1000; }
        return score - Math.min(500, target.getHealth() + target.getAbsorptionAmount() + target.getArmorValue() * 2) + this.distanceToSqr(target) * 0.01D;
    }
    public List<LivingEntity> skullTargets(ServerLevel level, Vec3 center, double radius, Vec3 forward) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::projectileTarget).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius && this.getAssignments().permitsDefensivePursuit(target))
                .filter(target -> forward.lengthSqr() < 0.01D || target.getEyePosition().subtract(this.getEyePosition()).normalize().dot(forward.normalize()) >= 0.25D)
                .filter(target -> this.line(level, this.getEyePosition(), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(this::priority).thenComparingInt(Entity::getId)).limit(64).toList();
    }
    public static int adaptiveBudget(int count, boolean rush) { return (int) Math.min(10L, Math.max(0L, (long) count) * (rush ? 2 : 1)); }
    private List<UUID> allocate(List<LivingEntity> targets, int budget) {
        if (targets.isEmpty()) { return List.of(); }
        List<UUID> result = new ArrayList<>();
        List<LivingEntity> reinforcements = targets.stream().sorted(Comparator.comparingDouble(target -> this.priority(target)
                + (target == this.getTarget() && !(target instanceof WitherBoss) ? 100000 : 0))).toList();
        for (int index = 0; index < budget; index++) { result.add((index < targets.size() ? targets.get(index) : reinforcements.get((index - targets.size()) % reinforcements.size())).getUUID()); }
        return List.copyOf(result);
    }
    private boolean busy() { return this.stormTicks > 0 || !this.siegePlan.isEmpty() || this.biteWindup > 0 || this.actionTicks > 0; }
    private boolean legitimateCombat() {
        LivingEntity target = this.getTarget();
        return this.combatEnabled() && this.validTarget(target) && this.distanceToSqr(target) <= 56 * 56
                && this.level() instanceof ServerLevel level && this.line(level, this.getEyePosition(), target.getEyePosition());
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!this.isTame() && stack.is(preferredTaming().item())) {
            if (!this.level().isClientSide()) { this.tame(player); this.clearCombatTarget(true); if (!player.isCreative()) { stack.shrink(1); } this.level().broadcastEntityEvent(this, (byte) 7); }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount, boolean projectile) {
        if (this.level() != level || !this.combatEnabled() || (projectile ? !this.projectileTarget(target) : !this.validTarget(target))
                || !this.getAssignments().permitsDefensivePursuit(target) || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float before = target.getHealth() + target.getAbsorptionAmount();
        float applied = target instanceof WitherBoss ? amount * 1.35F : amount;
        boolean hit = target.hurtServer(level, source, applied);
        float actual = hit ? Math.max(0, Math.min(applied, before - target.getHealth() - target.getAbsorptionAmount())) : 0;
        if (actual > 0) { this.progressTick = this.tickCount; WitherCombatEffects.infect(this, target); }
        return actual;
    }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return !effect.is(MobEffects.WITHER) && super.canBeAffected(effect); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypes.WITHER) || source.getEntity() == this) { return false; }
        boolean hit = super.hurtServer(level, source, amount);
        if (hit && amount >= 3 && source.getEntity() instanceof LivingEntity enemy && this.validTarget(enemy)) { this.activateCarapace(level, true); }
        return hit;
    }
    public boolean activateCarapace(ServerLevel level, boolean hitPressure) {
        if (this.level() != level || !this.combatEnabled() || this.carapaceCooldown > 0 || this.carapaceTicks > 0 || !hitPressure && !this.legitimateCombat()) { return false; }
        this.carapaceCooldown = 500; this.carapaceTicks = 100; this.state(2, true);
        if (!this.busy()) { this.action("carapace", 18); }
        PhantomVisualEffects.distortion(level, PhantomVfxTheme.WITHER, this.getBoundingBox().getCenter(), 2); return true;
    }
    public boolean activateRush(ServerLevel level) {
        if (this.level() != level || !this.legitimateCombat() || this.getHealth() > this.getMaxHealth() * 0.5F || this.rushCooldown > 0 || this.rushTicks > 0) { return false; }
        this.rushCooldown = 1000; this.rushTicks = 200; this.state(1, true);
        if (!this.busy()) { this.action("rush", 16); }
        PhantomVisualEffects.implosion(level, PhantomVfxTheme.WITHER, this.getBoundingBox().getCenter(), 2.2F); return true;
    }
    private boolean spawnSkull(ServerLevel level, LivingEntity target, Vec3 origin, int delay, boolean storm) {
        if (!this.projectileTarget(target) || !this.line(level, origin, target.getEyePosition())
                || !level.noBlockCollision(this, new AABB(origin, origin).inflate(0.2D)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(origin))) { return false; }
        CompanionWitherSkull skull = PhantomismContent.WITHER_PHANTOM_SKULL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (skull == null) { return false; }
        skull.setOwner(this); skull.setPos(origin); skull.configure(this.session, target.getUUID(), delay, storm, this.stormCenter);
        return level.addFreshEntity(skull);
    }
    private Vec3 muzzle(LivingEntity target) { return this.getEyePosition().add(target.getEyePosition().subtract(this.getEyePosition()).normalize().scale(0.9D)); }
    public int fireAdaptiveVolley(ServerLevel level, Vec3 forward, boolean multishot) {
        boolean ridden = this.getControllingPassenger() instanceof Player;
        if (this.level() != level || !this.combatEnabled() || this.busy() || (multishot ? this.multishotCooldown > 0
                : this.skullCooldown > SKULL_COOLDOWN - (ridden ? this.getRiddenProjectileAbilityCooldownTicks() : SKULL_COOLDOWN))) { return 0; }
        List<LivingEntity> targets = this.skullTargets(level, this.position(), SKULL_RANGE, forward);
        List<UUID> plan = this.allocate(targets, adaptiveBudget(targets.size(), this.isWitherRushing()));
        this.allocation = java.util.Set.copyOf(plan);
        int fired = 0;
        for (int index = 0; index < plan.size(); index++) {
            LivingEntity target = (LivingEntity) level.getEntity(plan.get(index));
            if (target != null && this.spawnSkull(level, target, this.muzzle(target), this.isWitherRushing() && index >= targets.size() ? 10 : 0, false)) { fired++; }
        }
        if (fired > 0) {
            if (multishot) { this.multishotCooldown = 240; } else { this.skullCooldown = SKULL_COOLDOWN; }
            this.action(multishot ? "multishot" : "skull", multishot ? 24 : ridden ? this.getRiddenProjectileAbilityCooldownTicks() : 16); this.playSound(SoundEvents.WITHER_SHOOT, 0.65F, 1.15F);
            if (ridden && this.getTarget() == null) { this.setCombatTarget(targets.getFirst()); }
        }
        return fired;
    }
    public boolean siegeBarrage(ServerLevel level) {
        if (this.level() != level || !this.legitimateCombat() || this.busy() || this.siegeCooldown > 0) { return false; }
        List<LivingEntity> targets = this.skullTargets(level, this.position(), SKULL_RANGE, Vec3.ZERO);
        if (targets.size() < 2) { return false; }
        this.siegePlan = this.allocate(targets, adaptiveBudget(targets.size(), this.isWitherRushing()));
        this.allocation = java.util.Set.copyOf(this.siegePlan);
        this.queueIndex = 0; this.queueAge = 0; this.siegeCooldown = 400; this.action("siege", 40); return true;
    }
    public @Nullable Vec3 stormOrigin(ServerLevel level, LivingEntity target, int index) {
        for (int height = 8; height >= 3; height--) {
            double angle = index * Math.PI * 0.765D;
            Vec3 origin = target.getEyePosition().add(Math.cos(angle) * 1.2D, height, Math.sin(angle) * 1.2D);
            AABB body = new AABB(origin, origin).inflate(0.25D);
            if (origin.y < level.getMaxY() && this.isLoadedFlightBox(body) && level.getWorldBorder().isWithinBounds(body)
                    && level.noBlockCollision(this, body) && !level.containsAnyLiquid(body) && this.line(level, origin, target.getEyePosition())) { return origin; }
        }
        return null;
    }
    public boolean witherStorm(ServerLevel level) {
        if (this.level() != level || this.stormCooldown > 0 || this.busy() || !this.legitimateCombat()) { return false; }
        List<LivingEntity> targets = this.skullTargets(level, this.position(), 25, Vec3.ZERO).stream().filter(target -> this.stormOrigin(level, target, 0) != null).toList();
        if (targets.size() < STORM_MINIMUM) { return false; }
        List<UUID> plan = this.allocate(targets, STORM_SKULLS);
        this.allocation = java.util.Set.copyOf(plan);
        List<Vec3> origins = new ArrayList<>();
        for (int index = 0; index < plan.size(); index++) {
            LivingEntity target = (LivingEntity) level.getEntity(plan.get(index));
            Vec3 origin = target == null ? null : this.stormOrigin(level, target, index);
            if (origin == null) { return false; } origins.add(origin);
        }
        this.stormCenter = this.position();
        List<CompanionWitherSkull> skulls = new ArrayList<>();
        for (int index = 0; index < STORM_SKULLS; index++) {
            LivingEntity target = (LivingEntity) level.getEntity(plan.get(index));
            CompanionWitherSkull skull = PhantomismContent.WITHER_PHANTOM_SKULL.get().create(level, EntitySpawnReason.TRIGGERED);
            if (skull == null || !this.projectileTarget(target)) { skulls.forEach(Entity::discard); this.stormCenter = null; return false; }
            skull.setOwner(this); skull.setPos(origins.get(index)); skull.configure(this.session, target.getUUID(), 30 + index * 2, true, this.stormCenter);
            skulls.add(skull);
        }
        for (CompanionWitherSkull skull : skulls) {
            if (!level.addFreshEntity(skull)) { skulls.forEach(Entity::discard); this.stormCenter = null; return false; }
        }
        this.stormCooldown = 1800; this.stormTicks = 240; this.state(4, true); this.action("storm", 60);
        PhantomVisualEffects.implosion(level, PhantomVfxTheme.WITHER, this.getEyePosition().add(0, 2, 0), 4);
        this.playSound(SoundEvents.WITHER_SPAWN, 0.7F, 0.8F); return true;
    }
    public @Nullable LivingEntity redirect(ServerLevel level, Vec3 origin, @Nullable Vec3 center) {
        return this.skullTargets(level, center == null ? this.position() : center, center == null ? SKULL_RANGE : 25, Vec3.ZERO).stream()
                .filter(target -> origin.distanceToSqr(target.getEyePosition()) <= SKULL_RANGE * SKULL_RANGE && (center == null || target.getY() < origin.y - 0.5D))
                .filter(target -> this.line(level, origin, target.getEyePosition())).findFirst().orElse(null);
    }
    public boolean witherBite(ServerLevel level, LivingEntity target) {
        if (this.level() != level || this.biteCooldown > 0 || this.busy() || !this.combatEnabled() || !this.validTarget(target) || this.distanceToSqr(target) > 25
                || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        this.biting = target.getUUID(); this.biteWindup = 6; this.biteCooldown = 100; this.action("bite", 14);
        if (this.getControllingPassenger() == null) {
            Vec3 step = target.position().subtract(this.position()).normalize().scale(0.55D);
            if (this.isLoadedFlightBox(this.getBoundingBox().expandTowards(step)) && level.noBlockCollision(this, this.getBoundingBox().expandTowards(step))) { this.setDeltaMovement(this.getDeltaMovement().add(step)); this.needsSync = true; }
        }
        return true;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || this.meleeCooldown > 0 || this.busy() || !this.combatEnabled() || !this.validTarget(target)
                || !this.getBoundingBox().inflate(1).intersects(target.getBoundingBox()) || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        this.meleeCooldown = 34; boolean hit = this.damage(level, target, this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), false) > 0;
        if (hit) { this.action("bite", 12); } return hit;
    }
    public boolean activateRiddenBite(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider)) { return false; }
        Vec3 eye = rider.getEyePosition(), end = eye.add(rider.getViewVector(1).scale(5));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(0.75D), this::validTarget).stream()
                .filter(enemy -> enemy.getBoundingBox().inflate(0.4D).clip(eye, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return target != null && this.witherBite(level, target);
    }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.fireAdaptiveVolley(level, rider.getViewVector(1), false) > 0; }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || !this.validTarget(target)) { this.clearCombatTarget(false); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuit)) { this.pursuit = target.getUUID(); this.combatStarted = this.tickCount; this.progressTick = this.tickCount; this.closest = distance; }
        if (distance < this.closest - 2) { this.closest = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.combatStarted > (target instanceof WitherBoss ? 3600 : 900) || this.tickCount - this.progressTick > 240) { this.clearCombatTarget(true); return true; }
        this.activateRush(level);
        if (this.busy()) { return true; }
        if (this.witherStorm(level)) { return true; }
        if (target instanceof WitherBoss boss && boss.getInvulnerableTicks() > 0) {
            if (distance < 16 * 16) { this.flyToAssignment(this.position().add(this.position().subtract(target.position()).normalize().scale(8)), 0.8D); } return true;
        }
        if (distance <= 16 && this.witherBite(level, target)) { return true; }
        if (distance <= SKULL_RANGE * SKULL_RANGE && this.line(level, this.getEyePosition(), target.getEyePosition())) {
            int count = this.skullTargets(level, this.position(), SKULL_RANGE, Vec3.ZERO).size();
            if (count >= 3 && this.siegeBarrage(level)) { return true; }
            if ((count >= 2 || target instanceof WitherBoss) && this.fireAdaptiveVolley(level, Vec3.ZERO, true) > 0) { return true; }
            if (this.fireAdaptiveVolley(level, Vec3.ZERO, false) > 0) { return true; }
        }
        if (distance < 8 * 8) {
            Vec3 retreat = this.position().add(this.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(5));
            if (this.line(level, this.position(), retreat)) { this.flyToAssignment(retreat, this.isWitherRushing() ? 1.0D : 0.8D); }
            if (distance < 9) { this.doHurtTarget(level, target); }
        } else if (distance > 20 * 20 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { this.flyToAssignment(target.position().add(0, 2, 0), this.isWitherRushing() ? 1.0D : 0.8D); }
        return true;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isTame() && this.getTarget() == null && this.combatEnabled() && this.tickCount % 20 == 0) {
            Player player = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 12, entity -> entity instanceof Player candidate && !candidate.isSpectator());
            if (player != null && this.line(level, this.getEyePosition(), player.getEyePosition())) {
                boolean lure = player.getMainHandItem().is(Items.WITHER_ROSE) || player.getOffhandItem().is(Items.WITHER_ROSE);
                this.getLookControl().setLookAt(player);
                if (lure && this.distanceToSqr(player) > 9) { this.flyToAssignment(player.position().add(0, 1.5D, 0), 0.6D); }
                else if (!lure && this.distanceToSqr(player) < 36 && this.warningCooldown == 0) { this.action("warn", 18); this.warningCooldown = 100; this.playSound(SoundEvents.WITHER_AMBIENT, 0.5F, 0.65F); }
            }
        }
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.skullCooldown = Math.max(0, this.skullCooldown - 1); this.multishotCooldown = Math.max(0, this.multishotCooldown - 1); this.siegeCooldown = Math.max(0, this.siegeCooldown - 1);
        this.biteCooldown = Math.max(0, this.biteCooldown - 1); this.rushCooldown = Math.max(0, this.rushCooldown - 1); this.carapaceCooldown = Math.max(0, this.carapaceCooldown - 1);
        this.stormCooldown = Math.max(0, this.stormCooldown - 1); this.meleeCooldown = Math.max(0, this.meleeCooldown - 1); this.actionTicks = Math.max(0, this.actionTicks - 1); this.warningCooldown = Math.max(0, this.warningCooldown - 1);
        if (!this.combatEnabled()) { this.cancel(); return; }
        if (this.rushTicks > 0 && --this.rushTicks == 0) { this.state(1, false); }
        if (this.carapaceTicks > 0 && --this.carapaceTicks == 0) { this.state(2, false); }
        if (this.stormTicks > 0) {
            if (--this.stormTicks == 0) { this.stormCenter = null; this.state(4, false); }
            else if (this.tickCount % 10 == 0 && this.stormCenter != null) { PhantomVisualEffects.witherStorm(level, this.stormCenter, this.stormTicks > 210); }
        }
        if (!this.siegePlan.isEmpty() && ++this.queueAge % 4 == 0) {
            UUID intended = this.siegePlan.get(this.queueIndex++);
            LivingEntity target = level.getEntity(intended) instanceof LivingEntity enemy && this.projectileTarget(enemy) && this.distanceToSqr(enemy) <= SKULL_RANGE * SKULL_RANGE ? enemy : this.redirect(level, this.getEyePosition(), null);
            if (target != null) { this.spawnSkull(level, target, this.muzzle(target), 0, false); }
            if (this.queueIndex >= this.siegePlan.size()) { this.siegePlan = List.of(); this.actionTicks = Math.max(this.actionTicks, 12); }
        }
        if (this.biteWindup > 0 && --this.biteWindup == 0) {
            if (this.biting != null && level.getEntity(this.biting) instanceof LivingEntity target && this.validTarget(target)
                    && this.getBoundingBox().inflate(1.2D).intersects(target.getBoundingBox()) && this.line(level, this.getEyePosition(), target.getEyePosition())) {
                if (this.damage(level, target, this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F, false) > 0) {
                    PhantomVisualEffects.impact(level, PhantomVfxTheme.WITHER, target.getBoundingBox().getCenter()); this.playSound(SoundEvents.PHANTOM_BITE, 0.8F, 0.7F);
                }
            }
            this.biting = null;
        }
        if (this.tickCount % 10 == 0) {
            if (this.getControllingPassenger() instanceof Player && this.legitimateCombat()) { this.activateRush(level); if (!this.busy()) { this.witherStorm(level); } }
            if (this.hasCarapace()) { PhantomVisualEffects.distortion(level, PhantomVfxTheme.WITHER, this.getBoundingBox().getCenter(), 1.5F); }
            if (this.isWitherRushing()) { PhantomVisualEffects.orbit(level, PhantomVfxTheme.WITHER, this.getBoundingBox().getCenter(), 1.6F, 5); }
        }
    }
    private void action(String name, int ticks) { this.actionTicks = Math.max(this.actionTicks, ticks); if (this.isAddedToLevel()) { this.triggerAnim("wither_action", name); } }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var controller = new AnimationController<WitherPhantom>("wither_action", 2, test -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.wither." + clip)); }
        registrar.add(controller.additiveAnimations());
    }
    private void cancel() { this.session = UUID.randomUUID(); this.siegePlan = List.of(); this.biting = null; this.biteWindup = 0; this.rushTicks = 0; this.carapaceTicks = 0; this.stormTicks = 0; this.stormCenter = null; this.state(1, false); this.state(2, false); this.state(4, false); WitherCombatEffects.release(this); }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); this.pursuit = null; this.commandedSkeleton = null; if (suppress) { this.cancel(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void die(DamageSource source) { this.cancel(); super.die(source); }
    @Override public void remove(RemovalReason reason) { this.cancel(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("WitherSkullCooldown", this.skullCooldown); output.putInt("WitherMultishotCooldown", this.multishotCooldown);
        output.putInt("WitherSiegeCooldown", this.siegeCooldown); output.putInt("WitherBiteCooldown", this.biteCooldown); output.putInt("WitherRushCooldown", this.rushCooldown);
        output.putInt("WitherCarapaceCooldown", this.carapaceCooldown); output.putInt("WitherStormCooldown", this.stormCooldown); output.putInt("WitherMeleeCooldown", this.meleeCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.cancel(); this.pursuit = null; this.actionTicks = 0;
        this.skullCooldown = Math.clamp(input.getIntOr("WitherSkullCooldown", 0), 0, SKULL_COOLDOWN); this.multishotCooldown = Math.clamp(input.getIntOr("WitherMultishotCooldown", 0), 0, 240);
        this.siegeCooldown = Math.clamp(input.getIntOr("WitherSiegeCooldown", 0), 0, 400); this.biteCooldown = Math.clamp(input.getIntOr("WitherBiteCooldown", 0), 0, 100);
        this.rushCooldown = Math.clamp(input.getIntOr("WitherRushCooldown", 0), 0, 1000); this.carapaceCooldown = Math.clamp(input.getIntOr("WitherCarapaceCooldown", 0), 0, 500);
        this.stormCooldown = Math.clamp(input.getIntOr("WitherStormCooldown", 0), 0, 1800); this.meleeCooldown = Math.clamp(input.getIntOr("WitherMeleeCooldown", 0), 0, 34);
    }

    @Override public java.util.List<net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot> temporalCooldowns() { return java.util.List.of(
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("wither_bite_cooldown", 100, () -> this.biteCooldown, value -> this.biteCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("wither_multishot_cooldown", 240, () -> this.multishotCooldown, value -> this.multishotCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("wither_siege_cooldown", 400, () -> this.siegeCooldown, value -> this.siegeCooldown = value)); }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.WITHER_ROSE, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Skull Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(SKULL_COOLDOWN), "Mounted Skull Cooldown: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(MOUNTED_SKULL_COOLDOWN));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "siege_barrage" -> this.siegeCooldown;
            case "activate_rush" -> this.rushCooldown;
            case "wither_storm" -> this.stormCooldown;
            default -> -1;
        };
    }
}
