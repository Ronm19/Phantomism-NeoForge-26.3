package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.effect.BleedingEffect;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class BloodPhantom extends AbstractPhantom implements net.ronm19.phantomism.entity.ai.CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = id("entity/blood_phantom");
    private static final Identifier HUNT = id("blood_relentless_hunt");
    private static final Identifier STRIKE = id("blood_crimson_strike");
    private int meleeCooldown;
    private int strikeCooldown;
    private int projectileCooldown;
    private int drainCooldown;
    private int huntCooldown;
    private int huntTicks;
    private int wingsCooldown;
    private int guardCooldown;
    private int guardTicks;
    private int shieldHealCooldown;
    private int dominionCooldown;
    private int dominionTicks;
    private int actionTicks;
    private int actionPriority;
    private int combatStarted;
    private int progressTick;
    private double closestDistance = Double.MAX_VALUE;
    private UUID combatSession = UUID.randomUUID();
    private @Nullable UUID pursuitTarget;
    private @Nullable UUID explicitTarget;
    private @Nullable UUID lureTarget;
    private @Nullable Vec3 dominionCenter;

    public BloodPhantom(EntityType<? extends BloodPhantom> type, Level level) { super(type, level); }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 72).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 3).add(Attributes.KNOCKBACK_RESISTANCE, 0.45D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D).add(Attributes.FLYING_SPEED, 0.4D).add(Attributes.FOLLOW_RANGE, 40);
    }
    public static boolean checkSpawnRules(EntityType<BloodPhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000 && time < 23000
                && level.getBiome(position).is(ModTags.Biomes.BLOOD_PHANTOM_SPAWNS)
                && position.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ())
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.BLOOD_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public void travel(Vec3 input) {
        if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * 0.09D)); }
        else { super.travel(input); }
    }
    public UUID combatSession() { return this.combatSession; }
    public boolean combatEnabled() {
        return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY
                && this.getOrder() != Order.RETURN_HOME && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT);
    }
    public boolean isRelentlessHunt() { return this.huntTicks > 0; }
    public boolean hasBloodShield() { return this.guardTicks > 0; }
    public boolean hasCrimsonDominion() { return this.dominionTicks > 0; }
    public static boolean lure(Player player) { return player.getMainHandItem().is(Items.CRIMSON_FUNGUS) || player.getOffhandItem().is(Items.CRIMSON_FUNGUS); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target instanceof Player || target instanceof AbstractPhantom || target instanceof OwnableEntity owned && owned.getOwnerReference() != null || this.isAlliedTo(target)) { return false; }
        boolean justified = target.getUUID().equals(this.explicitTarget) || target == this.getLastHurtByMob()
                || target instanceof Mob mob && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner());
        if ((target instanceof net.minecraft.world.entity.NeutralMob || target.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN) && !justified) { return false; }
        return target instanceof Enemy || justified;
    }
    public boolean isValidBloodTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == this.level()
                && !this.isTargetSuppressed(target) && this.isSpeciesCombatTarget(target)
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override public void setCombatTarget(LivingEntity target) {
        UUID previous = this.explicitTarget; this.explicitTarget = target.getUUID(); super.setCombatTarget(target);
        if (this.getTarget() != target) { this.explicitTarget = previous; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) {
        return this.isTame() && this.getOrder() == Order.GUARD && this.getOwner() != null && target instanceof Enemy && this.getOwner().distanceToSqr(target) <= 24 * 24;
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) {
        return this.distanceToSqr(target) + target.getHealth() * 2 - (target.getHealth() < target.getMaxHealth() * 0.35F ? 200 : 0)
                - (target instanceof Mob mob && this.getOwner() != null && mob.getTarget() == this.getOwner() ? 1000 : 0);
    }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) {
        return this.isLoadedFlightBox(new AABB(start, end).inflate(0.04D))
                && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private boolean closeEnough(ServerLevel level, LivingEntity target) {
        return this.distanceToSqr(target) <= 9 && this.getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox()) && this.line(level, this.getEyePosition(), target.getEyePosition());
    }
    private List<LivingEntity> targets(ServerLevel level, Vec3 center, double radius, int limit) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::isValidBloodTarget).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius && this.getAssignments().permitsDefensivePursuit(target)
                        && this.line(level, center.add(0, 0.8D, 0), target.getBoundingBox().getCenter()))
                .sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList();
    }
    public float hurtBloodTarget(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (this.level() != level || !this.combatEnabled() || !Float.isFinite(amount) || amount <= 0 || !this.isValidBloodTarget(target)
                || !this.getAssignments().permitsDefensivePursuit(target)) { return 0; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), amount) : target.hurtServer(level, source, amount);
        float actual = hit ? Math.max(0, Math.min(amount, previous - target.getHealth() - target.getAbsorptionAmount())) : 0;
        if (actual > 0) { this.progressTick = this.tickCount; }
        return actual;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !this.combatEnabled() || !this.isValidBloodTarget(target) || this.meleeCooldown > 0 || !this.closeEnough(level, target)) { return false; }
        boolean strike = this.strikeCooldown == 0;
        if (strike) { this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(STRIKE, 2, AttributeModifier.Operation.ADD_VALUE)); }
        boolean hit;
        try { hit = super.doHurtTarget(level, target); }
        finally { this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(STRIKE); }
        this.meleeCooldown = this.isRelentlessHunt() ? 20 : 30;
        if (hit) {
            this.progressTick = this.tickCount;
            if (strike) { this.strikeCooldown = 80; BleedingEffect.apply(this, target, 120, false); this.showAction("strike"); PhantomVisualEffects.crosscut(level, PhantomVfxTheme.BLOOD, target.getBoundingBox().getCenter()); }
        }
        return hit;
    }
    public boolean activateRiddenStrike(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider) || this.strikeCooldown > 0 || this.meleeCooldown > 0) { return false; }
        Vec3 start = rider.getEyePosition(); Vec3 end = start.add(rider.getViewVector(1).scale(3.5D));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4), this::isValidBloodTarget).stream()
                .filter(candidate -> candidate.getBoundingBox().inflate(0.5D).clip(start, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null || !this.doHurtTarget(level, target)) { return false; }
        if (target.isAlive()) { this.setCombatTarget(target); } return true;
    }
    public boolean launchBloodProjectile(ServerLevel level, Vec3 direction) {
        int interval = this.getControllingPassenger() instanceof Player ? 8 : 240;
        if (this.level() != level || !this.combatEnabled() || this.projectileCooldown > 240 - interval || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 0.01D) { return false; }
        Vec3 normalized = direction.normalize(); Vec3 muzzle = this.getEyePosition().add(normalized.scale(0.7D));
        if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        BloodProjectile projectile = PhantomismContent.BLOOD_PROJECTILE.get().create(level, EntitySpawnReason.TRIGGERED);
        projectile.setOwner(this); projectile.configure(this.combatSession); projectile.setPos(muzzle); projectile.shoot(normalized.x, normalized.y, normalized.z, 1.15F, 0);
        if (!level.addFreshEntity(projectile)) { return false; }
        this.projectileCooldown = 240; this.showAction("projectile"); this.playSound(SoundEvents.PHANTOM_BITE, 0.5F, 1.45F); return true;
    }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.launchBloodProjectile(level, rider.getViewVector(1)); }
    public boolean drainBlood(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.combatEnabled() || this.drainCooldown > 0 || !this.isValidBloodTarget(target) || this.distanceToSqr(target) > 144
                || !this.line(level, this.getEyePosition(), target.getEyePosition()) || !this.getAssignments().permitsDefensivePursuit(target)) { return false; }
        boolean bleeding = BleedingEffect.isActive(target);
        float actual = this.hurtBloodTarget(level, target, this.damageSources().mobAttack(this), 8);
        if (actual <= 0) { return false; }
        this.drainCooldown = 240; this.heal(actual * (bleeding ? 0.75F : 0.5F)); this.showAction("drain");
        PhantomVisualEffects.beam(level, PhantomVfxTheme.BLOOD, target.getBoundingBox().getCenter(), this.getBoundingBox().getCenter()); return true;
    }
    public boolean activateRelentlessHunt(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (this.level() != level || !this.combatEnabled() || this.huntCooldown > 0 || this.huntTicks > 0 || !this.isValidBloodTarget(target)
                || this.distanceToSqr(target) > 40 * 40 || target.getHealth() >= target.getMaxHealth() * 0.6F && !BleedingEffect.isActive(target)) { return false; }
        this.huntCooldown = 500; this.huntTicks = 160; this.applyHunt(); this.showAction("hunt"); PhantomVisualEffects.aura(level, PhantomVfxTheme.BLOOD, this.getBoundingBox().getCenter()); return true;
    }
    private void applyHunt() {
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(HUNT, 0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { this.getAttribute(attribute).addOrUpdateTransientModifier(new AttributeModifier(HUNT, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        this.getAttribute(Attributes.FOLLOW_RANGE).addOrUpdateTransientModifier(new AttributeModifier(HUNT, 8, AttributeModifier.Operation.ADD_VALUE));
    }
    private void endHunt() { this.huntTicks = 0; for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.FOLLOW_RANGE)) { this.getAttribute(attribute).removeModifier(HUNT); } }
    public boolean crimsonWings(ServerLevel level, Vec3 direction) {
        if (this.level() != level || !this.combatEnabled() || this.wingsCooldown > 0 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 0.01D) { return false; }
        Vec3 forward = direction.normalize(); boolean hit = false;
        for (LivingEntity target : this.targets(level, this.position(), 7, 12)) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter());
            if (offset.lengthSqr() > 0.01D && offset.normalize().dot(forward) < 0.707D) { continue; }
            if (this.hurtBloodTarget(level, target, this.damageSources().mobAttack(this), 6) > 0) { BleedingEffect.apply(this, target, 100, false); hit = true; }
        }
        if (!hit) { return false; }
        this.wingsCooldown = 240; this.showAction("wings"); PhantomVisualEffects.bloodWings(level, this.getBoundingBox().getCenter(), forward); return true;
    }
    public boolean activateBloodShield(ServerLevel level) {
        if (this.level() != level || !this.combatEnabled() || this.guardCooldown > 0 || this.guardTicks > 0) { return false; }
        this.guardCooldown = 480; this.guardTicks = 120; this.showAction("shield"); PhantomVisualEffects.aura(level, PhantomVfxTheme.BLOOD, this.getBoundingBox().getCenter()); return true;
    }
    public void healShield(float prevented) {
        if (this.combatEnabled() && this.guardTicks > 0 && this.shieldHealCooldown == 0 && Float.isFinite(prevented) && prevented > 0) { this.shieldHealCooldown = 20; this.heal(Math.min(2, prevented * 0.25F)); }
    }
    public boolean activateCrimsonDominion(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (this.level() != level || !this.combatEnabled() || this.dominionCooldown > 0 || this.dominionTicks > 0 || !this.isValidBloodTarget(target)
                || this.distanceToSqr(target) > 144 || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        BlockPos origin = this.blockPosition(); Vec3 center = null;
        for (int depth = 0; depth <= 8; depth++) {
            BlockPos floor = origin.below(depth + 1);
            if (!level.hasChunkAt(floor)) { break; }
            if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() && level.getFluidState(floor.above()).isEmpty()) { center = Vec3.atBottomCenterOf(floor.above()); break; }
        }
        if (center == null) { return false; }
        this.dominionCooldown = 1600; this.dominionTicks = 200; this.dominionCenter = center; this.showAction("dominion");
        PhantomVisualEffects.shockwave(level, PhantomVfxTheme.BLOOD, center.add(0, 0.1D, 0), 10); this.playSound(SoundEvents.PHANTOM_AMBIENT, 1.2F, 0.6F); return true;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hit = super.hurtServer(level, source, amount);
        if (hit && this.isAlive() && source.getEntity() instanceof LivingEntity attacker && this.isValidBloodTarget(attacker)
                && (amount >= 6 || this.getHealth() < this.getMaxHealth() * 0.65F)) { this.activateBloodShield(level); }
        return hit;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (this.getTarget() instanceof Player) { this.clearCombatTarget(false); }
        if (!this.isTame() && !this.isBaby() && this.combatEnabled()) {
            if (this.tickCount % 20 == 0 && this.getTarget() == null) {
                Player nearby = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(24), player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && lure(player) && !this.isTargetSuppressed(player) && this.line(level, this.getEyePosition(), player.getEyePosition())).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                this.lureTarget = nearby == null ? null : nearby.getUUID();
            }
        }
        super.customServerAiStep(level);
        if (!this.isTame() && this.combatEnabled() && this.getTarget() == null && this.lureTarget != null && level.getEntity(this.lureTarget) instanceof Player player
                && player.isAlive() && !player.isSpectator() && lure(player) && this.distanceToSqr(player) <= 576 && this.line(level, this.getEyePosition(), player.getEyePosition())) {
            if (this.distanceToSqr(player) > 9) { this.flyToAssignment(player.position().add(0, 1.6D, 0), 0.75D); }
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || !this.isValidBloodTarget(target)) { this.clearCombatTarget(true); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuitTarget)) { this.pursuitTarget = target.getUUID(); this.combatStarted = this.tickCount; this.progressTick = this.tickCount; this.closestDistance = distance; }
        if (distance < this.closestDistance - 2) { this.closestDistance = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.combatStarted > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        if (target.damageCooldownTime <= 10) {
            if (this.getHealth() < this.getMaxHealth() - 4 && this.drainBlood(level, target)) { return true; }
            if (this.wingsCooldown == 0 && this.targets(level, this.position(), 7, 3).size() >= 2 && this.crimsonWings(level, target.position().subtract(this.position()))) { return true; }
            if (this.closeEnough(level, target)) { this.doHurtTarget(level, target); return true; }
            if (distance <= BloodProjectile.MAX_TRAVEL_DISTANCE * BloodProjectile.MAX_TRAVEL_DISTANCE && this.line(level, this.getEyePosition(), target.getEyePosition()) && this.launchBloodProjectile(level, target.getBoundingBox().getCenter().subtract(this.getEyePosition()))) { return true; }
        }
        this.flyToAssignment(target.position().add(0, 1, 0), this.isRelentlessHunt() ? 1.15D : 1); return true;
    }
    private void bloodSense(ServerLevel level) {
        if (!this.combatEnabled()) { return; }
        List<LivingEntity> sensed = this.targets(level, this.position(), Math.min(48, this.getAttributeValue(Attributes.FOLLOW_RANGE)), 16);
        for (LivingEntity target : sensed) { if (target.getHealth() <= target.getMaxHealth() * 0.35F) { target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false), this); } }
        if (this.getTarget() == null && this.isTame() && this.getOrder() == Order.GUARD && this.getOwner() != null) {
            LivingEntity threat = sensed.stream().filter(target -> this.getOwner().distanceToSqr(target) <= 24 * 24 && this.getAssignments().permitsDefensivePursuit(target)).findFirst().orElse(null);
            if (threat != null) { this.setCombatTarget(threat); this.showAction("sense"); }
        }
    }
    private void tickArea(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (this.isValidBloodTarget(current) && this.distanceToSqr(current) <= 24 * 24) {
            boolean affected = false;
            for (LivingEntity target : this.targets(level, this.position(), 5, 12)) {
                if (this.dominionTicks > 0 && this.dominionCenter != null && target.position().distanceToSqr(this.dominionCenter) <= 100) { continue; }
                if (this.hurtBloodTarget(level, target, this.damageSources().mobAttack(this), 1) > 0) { BleedingEffect.apply(this, target, 60, false); affected = true; }
            }
            if (affected) { this.showAction("aura"); }
            PhantomVisualEffects.shockwave(level, PhantomVfxTheme.BLOOD, this.position().add(0, 0.1D, 0), 5);
        }
        if (this.dominionTicks > 0 && this.dominionCenter != null) {
            if (this.position().distanceToSqr(this.dominionCenter) > 24 * 24 || !this.isLoadedFlightBox(new AABB(this.dominionCenter, this.dominionCenter).inflate(10))) { this.dominionTicks = 0; this.dominionCenter = null; return; }
            float dealt = 0;
            for (LivingEntity target : this.targets(level, this.dominionCenter, 10, 12)) {
                float actual = this.hurtBloodTarget(level, target, this.damageSources().mobAttack(this), 3);
                if (actual > 0) { BleedingEffect.apply(this, target, 100, true); dealt += actual; }
            }
            if (dealt > 0) { this.heal(Math.min(2, dealt * 0.25F)); }
            PhantomVisualEffects.bloodDominion(level, this.dominionCenter);
        }
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.meleeCooldown = Math.max(0, this.meleeCooldown - 1); this.strikeCooldown = Math.max(0, this.strikeCooldown - 1); this.projectileCooldown = Math.max(0, this.projectileCooldown - 1);
        this.drainCooldown = Math.max(0, this.drainCooldown - 1); this.huntCooldown = Math.max(0, this.huntCooldown - 1); this.wingsCooldown = Math.max(0, this.wingsCooldown - 1);
        this.guardCooldown = Math.max(0, this.guardCooldown - 1); this.guardTicks = Math.max(0, this.guardTicks - 1); this.shieldHealCooldown = Math.max(0, this.shieldHealCooldown - 1);
        this.dominionCooldown = Math.max(0, this.dominionCooldown - 1);
        this.actionTicks = Math.max(0, this.actionTicks - 1);
        if (!this.combatEnabled()) { this.cancelActive(); return; }
        if (this.huntTicks > 0 && --this.huntTicks == 0) { this.endHunt(); }
        if (this.dominionTicks > 0 && --this.dominionTicks == 0) { this.dominionCenter = null; }
        if (this.tickCount % 20 == 0) {
            this.bloodSense(level); this.activateRelentlessHunt(level);
            LivingEntity target = this.getTarget();
            if (this.isValidBloodTarget(target) && this.distanceToSqr(target) <= 144 && (target.getMaxHealth() >= 80 || this.getHealth() < 24 || this.targets(level, this.position(), 10, 4).size() >= 3)) { this.activateCrimsonDominion(level); }
            this.tickArea(level);
            if (this.guardTicks > 0) { PhantomVisualEffects.aura(level, PhantomVfxTheme.BLOOD, this.getBoundingBox().getCenter()); }
        }
    }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var actions = new AnimationController<BloodPhantom>("blood_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"sense", "strike", "projectile", "aura", "drain", "hunt", "wings", "shield", "dominion"}) { actions.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.blood." + name)); }
        registrar.add(actions.additiveAnimations());
    }
    private void showAction(String name) {
        int priority = switch (name) { case "dominion" -> 4; case "shield" -> 3; case "hunt" -> 2; case "sense", "aura" -> 0; default -> 1; };
        if (this.level() instanceof ServerLevel && this.isAddedToLevel() && (this.actionTicks == 0 || priority >= this.actionPriority)) {
            this.actionPriority = priority; this.actionTicks = name.equals("dominion") ? 36 : name.equals("shield") ? 18 : name.equals("hunt") ? 16 : 12;
            this.triggerAnim("blood_action", name);
        }
    }
    private void cancelActive() { this.combatSession = UUID.randomUUID(); this.endHunt(); this.guardTicks = 0; this.dominionTicks = 0; this.dominionCenter = null; }
    @Override public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation); this.pursuitTarget = null; this.explicitTarget = null; this.closestDistance = Double.MAX_VALUE;
        if (suppressRetaliation) { this.cancelActive(); }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void die(DamageSource source) { this.cancelActive(); super.die(source); }
    @Override public void remove(RemovalReason reason) { this.cancelActive(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("BloodMeleeCooldown", this.meleeCooldown); output.putInt("BloodStrikeCooldown", this.strikeCooldown);
        output.putInt("BloodProjectileCooldown", this.projectileCooldown); output.putInt("BloodDrainCooldown", this.drainCooldown); output.putInt("BloodHuntCooldown", this.huntCooldown);
        output.putInt("BloodWingsCooldown", this.wingsCooldown); output.putInt("BloodShieldCooldown", this.guardCooldown); output.putInt("BloodDominionCooldown", this.dominionCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.cancelActive(); this.pursuitTarget = null; this.explicitTarget = null;
        this.meleeCooldown = Math.clamp(input.getIntOr("BloodMeleeCooldown", 0), 0, 30); this.strikeCooldown = Math.clamp(input.getIntOr("BloodStrikeCooldown", 0), 0, 80);
        this.projectileCooldown = Math.clamp(input.getIntOr("BloodProjectileCooldown", 0), 0, 240); this.drainCooldown = Math.clamp(input.getIntOr("BloodDrainCooldown", 0), 0, 240);
        this.huntCooldown = Math.clamp(input.getIntOr("BloodHuntCooldown", 0), 0, 500); this.wingsCooldown = Math.clamp(input.getIntOr("BloodWingsCooldown", 0), 0, 240);
        this.guardCooldown = Math.clamp(input.getIntOr("BloodShieldCooldown", 0), 0, 480); this.dominionCooldown = Math.clamp(input.getIntOr("BloodDominionCooldown", 0), 0, 1600);
    }

    @Override public java.util.List<net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot> temporalCooldowns() { return java.util.List.of(
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("blood_strike_cooldown", 80, () -> this.strikeCooldown, value -> this.strikeCooldown = value),
            net.ronm19.phantomism.entity.ai.CooldownDiscipline.Slot.normal("blood_wings_cooldown", 240, () -> this.wingsCooldown, value -> this.wingsCooldown = value)); }
}
