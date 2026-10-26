package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.CooldownDiscipline;
import net.ronm19.phantomism.entity.ai.PhantomAssignments;
import net.ronm19.phantomism.entity.ai.PhantomDuels;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.SporeVfx;

public final class SporePhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/spore_phantom");
    public static final int BREATH_RANGE = 42, BREATH_COOLDOWN = 200, MOUNTED_COOLDOWN = 6;
    public static final int BLOOM_RADIUS = 8, BLOOM_DURATION = 160, BLOOM_COOLDOWN = 400;
    public static final int DIVE_COOLDOWN = 240, STORM_RADIUS = 18, STORM_DURATION = 240, STORM_COOLDOWN = 1800;
    public static final int MAX_CLOUDS = 3, MAX_ZONE_TARGETS = 24;
    private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(SporePhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(SporePhantom.class, EntityDataSerializers.BOOLEAN);
    private static final int[] INTERVALS = {BREATH_COOLDOWN, BLOOM_COOLDOWN, DIVE_COOLDOWN, STORM_COOLDOWN};
    private static final String[] CLIPS = {"breath", "bloom", "dive", "storm"};
    private final int[] cooldowns = new int[4];
    private final ArrayDeque<Cloud> clouds = new ArrayDeque<>();
    private UUID session = UUID.randomUUID(), orderedTarget, ownerContext, diveTarget;
    private Vec3 bloomCenter, stormCenter;
    private int bloomTicks, stormTicks, diveTicks, meleeTicks, tameAttempts;
    private boolean mountedDive, visualOpen;

    public SporePhantom(EntityType<? extends SporePhantom> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 72).add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, .35D)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .4D);
    }

    public static boolean checkSpawnRules(EntityType<SporePhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D).inflate(.05D);
        return accessor.getLevel().dimension() == Level.OVERWORLD && box.minY >= accessor.getMinY() + 1 && box.maxY < accessor.getMaxY()
                && accessor.getBiome(position).is(ModTags.Biomes.SPORE_PHANTOM_SPAWNS)
                && accessor.hasChunksAt(BlockPos.containing(box.minX, box.minY - 1, box.minZ), BlockPos.containing(box.maxX, box.maxY + 1, box.maxZ))
                && !accessor.containsAnyLiquid(box) && accessor.noBlockCollision(null, box);
    }

    @Override protected Config.PhantomStats getConfiguredStats() { return Config.SPORE_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected int getFlightTurnRate() { return 14; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public boolean isUltimateAnimationActive() { return stormTicks > 0 || visualState() == 2 || super.isUltimateAnimationActive(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL, 0); data.define(DIVING, false); }
    public int visualState() { return entityData.get(VISUAL); }
    public UUID session() { return session; }
    public int cloudCount() { return clouds.size(); }
    public int bloomTicks() { return bloomTicks; }
    public int stormTicks() { return stormTicks; }
    public void visualStarted() { visualOpen = true; }

    public boolean enabled() {
        return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
                && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT)
                && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner);
    }

    public boolean recipient(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && (target == this || isTame()
                && (target == getOwner() || getTeam() != null && getTeam().isAlliedTo(target.getTeam())
                || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference())));
    }

    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
                && !recipient(target) && !isAlliedTo(target) && !(target instanceof AbstractPhantom)
                && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(this, target) && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
                && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }

    private boolean attacking(LivingEntity attacker, LivingEntity ward) {
        return ward != null && (ward.getLastHurtByMob() == attacker && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100
                || attacker instanceof Mob mob && mob.getTarget() == ward);
    }

    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        return safeEnemy(target) && (target.getUUID().equals(orderedTarget) || attacking(target, this)
                || attacking(target, getOwner()) || target instanceof Mob mob && recipient(mob.getTarget()));
    }

    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    @Override protected boolean prefersRangedCombat() { return true; }

    @Override public void setCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return; }
        orderedTarget = target.getUUID();
        super.setCombatTarget(target);
        if (getTarget() != target) { orderedTarget = null; }
    }

    public boolean line(Vec3 start, Vec3 end) {
        return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr())
                && isLoadedFlightBox(new AABB(start, end).inflate(.04D))
                && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(MAX_ZONE_TARGETS).toList();
    }

    public boolean damage(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        return enabled() && validTarget(target) && Float.isFinite(amount) && amount > 0 && target.hurtServer(level, source, amount);
    }

    public boolean breath(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || (mounted ? !(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider) : cooldowns[0] > 0)
                || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.6D));
        if (!line(getEyePosition(), muzzle)) { return false; }
        if (mounted && getControllingPassenger() instanceof Player rider) {
            Vec3 start = rider.getEyePosition(), end = start.add(direction.scale(BREATH_RANGE));
            level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.5D), this::safeEnemy).stream()
                    .filter(enemy -> enemy.getBoundingBox().inflate(.35D).clip(start, end).isPresent() && line(start, enemy.getEyePosition()))
                    .min(Comparator.comparingDouble(rider::distanceToSqr)).ifPresent(enemy -> orderedTarget = enemy.getUUID());
        }
        SporeBreath shot = PhantomismContent.SPORE_BREATH.get().create(level, EntitySpawnReason.TRIGGERED);
        if (shot == null) { return false; }
        shot.setOwner(this); shot.configure(session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 1.6F, 0);
        if (!level.addFreshEntity(shot)) { return false; }
        if (!mounted) { cooldowns[0] = BREATH_COOLDOWN; }
        action("breath");
        SporeVfx.emit(this, SporeVfx.BREATH, shot, muzzle, direction, 36, 1);
        return true;
    }

    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_COOLDOWN; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return breath(level, rider.getViewVector(1), true); }

    public void impact(ServerLevel level, LivingEntity target, Vec3 position) {
        if (!enabled()) { return; }
        if (target != null && damage(level, target, damageSources().mobAttack(this), 8)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
        }
        if (clouds.size() >= MAX_CLOUDS) { clouds.removeFirst(); }
        clouds.addLast(new Cloud(position));
        SporeVfx.emit(this, SporeVfx.CLOUD, null, position, Vec3.ZERO, 40, 2.5F);
    }

    public boolean bloom(ServerLevel level) {
        if (!enabled() || cooldowns[1] > 0 || bloomTicks > 0) { return false; }
        bloomCenter = position(); bloomTicks = BLOOM_DURATION; cooldowns[1] = BLOOM_COOLDOWN;
        action("bloom"); SporeVfx.emit(this, SporeVfx.BLOOM, null, bloomCenter, Vec3.ZERO, BLOOM_DURATION, BLOOM_RADIUS);
        return true;
    }

    public boolean storm(ServerLevel level) {
        if (!enabled() || cooldowns[3] > 0 || stormTicks > 0 || enemies(level, position(), STORM_RADIUS).isEmpty()) { return false; }
        stormCenter = position(); stormTicks = STORM_DURATION; cooldowns[3] = STORM_COOLDOWN;
        action("storm"); SporeVfx.emit(this, SporeVfx.STORM, null, stormCenter, Vec3.ZERO, STORM_DURATION, STORM_RADIUS);
        return true;
    }

    public boolean dive(ServerLevel level, LivingEntity target, boolean mounted) {
        if (!enabled() || cooldowns[2] > 0 || diveTicks > 0 || !validTarget(target) || distanceToSqr(target) > 18 * 18
                || !line(getEyePosition(), target.getEyePosition())) { return false; }
        cooldowns[2] = DIVE_COOLDOWN; diveTicks = 24; diveTarget = target.getUUID(); mountedDive = mounted;
        entityData.set(DIVING, !mounted); action("dive");
        SporeVfx.emit(this, SporeVfx.DIVE, this, position(), target.position().subtract(position()).normalize(), 24, 3);
        return true;
    }

    public boolean activateRiddenDive(Player rider) {
        if (!(level() instanceof ServerLevel level) || rider != getControllingPassenger() || !isOwnedBy(rider)) { return false; }
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(18), this::safeEnemy).stream()
                .filter(enemy -> distanceToSqr(enemy) <= 18 * 18 && line(getEyePosition(), enemy.getEyePosition())
                        && rider.getViewVector(1).dot(enemy.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target != null) { orderedTarget = target.getUUID(); }
        return target != null && dive(level, target, true);
    }

    @Override public void travel(Vec3 input) {
        if (entityData.get(DIVING) && getControllingPassenger() == null) { setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }

    private void stopDive() {
        boolean moving = diveTicks > 0 && !mountedDive;
        diveTicks = 0; diveTarget = null; mountedDive = false; entityData.set(DIVING, false);
        if (moving) { setDeltaMovement(Vec3.ZERO); }
    }

    private void tickDive(ServerLevel level) {
        if (diveTicks <= 0) { return; }
        LivingEntity target = level.getEntity(diveTarget) instanceof LivingEntity living ? living : null;
        if (!validTarget(target) || --diveTicks <= 0 || distanceToSqr(target) > 24 * 24
                || mountedDive && !(getControllingPassenger() instanceof Player rider && isOwnedBy(rider))) { stopDive(); return; }
        if (!mountedDive) {
            if (isVehicle()) { stopDive(); return; }
            Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            Vec3 step = direction.normalize().scale(Math.min(.8D, direction.length()));
            AABB sweep = getBoundingBox().expandTowards(step);
            if (!isLoadedFlightBox(sweep) || !level.noBlockCollision(this, sweep) || level.containsAnyLiquid(sweep)) { stopDive(); return; }
            move(MoverType.SELF, step); needsSync = true;
        }
        if (getBoundingBox().inflate(.8D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition())) {
            if (damage(level, target, damageSources().mobAttack(this), 14)) { target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this); }
            impact(level, null, target.position()); stopDive();
        }
    }

    private void zone(ServerLevel level, Vec3 center, double radius, boolean ultimate) {
        for (LivingEntity enemy : enemies(level, center, radius)) {
            if (ultimate && damage(level, enemy, damageSources().mobAttack(this), 3)) { enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), this); }
            enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this);
        }
        for (LivingEntity ward : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::recipient).stream()
                .filter(ward -> ward.position().distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), ward.getEyePosition()))
                .sorted(Comparator.comparingDouble(ward -> ward.position().distanceToSqr(center))).limit(MAX_ZONE_TARGETS).toList()) { ward.heal(1); }
    }

    private boolean needsRecovery(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BLOOM_RADIUS), this::recipient).stream()
                .filter(ward -> distanceToSqr(ward) <= BLOOM_RADIUS * BLOOM_RADIUS).limit(MAX_ZONE_TARGETS)
                .anyMatch(ward -> ward.getHealth() < ward.getMaxHealth() - 2 && line(getEyePosition(), ward.getEyePosition()));
    }

    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target)) { return true; }
        if (doHurtTarget(level, target)) { return true; }
        if (!isVehicle() && distanceToSqr(target) <= 18 * 18 && distanceToSqr(target) > 6 * 6 && dive(level, target, false)) { return true; }
        if (distanceToSqr(target) <= BREATH_RANGE * BREATH_RANGE && line(getEyePosition(), target.getEyePosition())) { breath(level, target.getEyePosition().subtract(getEyePosition()), false); }
        return true;
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!enabled() || meleeTicks > 0 || !(target instanceof LivingEntity enemy) || !getBoundingBox().inflate(.6D).intersects(enemy.getBoundingBox())
                || !line(getEyePosition(), enemy.getEyePosition()) || !damage(level, enemy, damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE))) { return false; }
        meleeTicks = 40; animate(net.ronm19.phantomism.animation.PhantomAnimationAction.BITE); return true;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) { return; }
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (meleeTicks > 0) { meleeTicks--; }
        UUID owner = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID();
        if (ownerContext != null && !ownerContext.equals(owner)) { endSpores(); }
        ownerContext = owner;
        if (!enabled()) { endSpores(); return; }
        tickDive(level);
        for (var iterator = clouds.iterator(); iterator.hasNext();) {
            Cloud cloud = iterator.next();
            if (--cloud.ticks % 20 == 0) { for (LivingEntity enemy : enemies(level, cloud.center, 2.5D)) { damage(level, enemy, damageSources().mobAttack(this), 1); } }
            if (cloud.ticks <= 0) { iterator.remove(); }
        }
        if (bloomTicks > 0) { if (--bloomTicks % 20 == 0) { zone(level, bloomCenter, BLOOM_RADIUS, false); } if (bloomTicks == 0) { bloomCenter = null; } }
        if (stormTicks > 0) { if (--stormTicks % 20 == 0) { zone(level, stormCenter, STORM_RADIUS, true); } if (stormTicks == 0) { stormCenter = null; } }
        entityData.set(VISUAL, stormTicks > 0 || bloomTicks > 0 ? 2 : validTarget(getTarget()) ? 1 : 0);
        if (tickCount % 40 == 0) { level.sendParticles(ParticleTypes.COMPOSTER, getX(), getY() + .4D, getZ(), 2, .4D, .1D, .4D, 0); }
        if (isNoAi() || tickCount % 20 != 0) { return; }
        if (validTarget(getTarget())) {
            if (enemies(level, position(), STORM_RADIUS).size() >= 3 || getHealth() < getMaxHealth() * .4F) { storm(level); }
            if (needsRecovery(level)) { bloom(level); }
            if (!isVehicle()) { prepareSpeciesCombatAction(level, getTarget()); }
        } else {
            if (isTame() && needsRecovery(level)) { bloom(level); }
            if (!isTame() && tickCount % 40 == 0) {
                Player visitor = level.getNearestPlayer(this, 12);
                if (visitor != null && !visitor.isSpectator() && line(getEyePosition(), visitor.getEyePosition())) { getLookControl().setLookAt(visitor, 10, 10); }
            }
        }
    }

    private void action(String clip) { triggerAnim("spore", clip); playSound(SoundEvents.BOGGED_AMBIENT, .35F, .8F); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        var controller = new AnimationController<SporePhantom>("spore", 3, state -> PlayState.STOP);
        for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.spore." + clip)); }
        controllers.add(controller);
    }

    public boolean isTamingMushroom(ItemStack stack) { return stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM); }
    @Override public TamingProfile preferredTaming() {
        return new TamingProfile(Items.RED_MUSHROOM, .5F, 3, "Red OR Brown Mushroom: 50% per use, guaranteed by eligible attempt 3. Both share trust progress. Phantom Staff: instant alternative.");
    }
    @Override public List<String> codexCooldowns() {
        return List.of("Spore Breath: 8 damage + Poison I (3 s), 42 blocks, 10 s AI / 0.3 s mounted; cloud 2.5 blocks, 2 s, 1 damage/s, max 3.",
                "Mushroom Bloom: 8 blocks / 8 s, heals allies 1 HP/s, Slowness I to enemies; cooldown 20 s.",
                "Decay Dive: 14 damage, Weakness I (3 s), maximum 18-block approach; cooldown 12 s. Mounted Ctrl arms impact without overriding rider movement.",
                "Fungal Storm: 18 blocks / 12 s, 3 damage/s, Weakness I + Slowness I; heals allies 1 HP/s; cooldown 90 s.");
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTame() && isAlive() && !player.isSpectator() && isTamingMushroom(player.getItemInHand(hand))) {
            if (!level().isClientSide()) {
                if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); }
                if (++tameAttempts >= preferredTaming().guaranteedAttempt() || getRandom().nextFloat() < preferredTaming().chance()) {
                    tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte)7);
                } else { level().broadcastEntityEvent(this, (byte)6); }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public void endSpores() {
        if (level() instanceof ServerLevel && visualOpen) { SporeVfx.cancel(this); session = UUID.randomUUID(); visualOpen = false; }
        clouds.clear(); bloomTicks = 0; stormTicks = 0; bloomCenter = null; stormCenter = null; orderedTarget = null; stopDive(); entityData.set(VISUAL, 0);
    }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && clouds != null) { endSpores(); } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (clouds != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endSpores(); } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (clouds != null && task != PhantomAssignments.Task.NONE && task != PhantomAssignments.Task.HUNT) { endSpores(); } }
    @Override public void remove(RemovalReason reason) { if (clouds != null) { endSpores(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        for (int index = 0; index < cooldowns.length; index++) { output.putInt("SporeCooldown" + index, cooldowns[index]); }
        output.putInt("SporeTamingAttempts", tameAttempts); output.putInt("SporeMelee", meleeTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); endSpores(); session = UUID.randomUUID();
        for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("SporeCooldown" + index, 0), 0, INTERVALS[index]); }
        tameAttempts = Math.clamp(input.getIntOr("SporeTamingAttempts", 0), 0, 3); meleeTicks = Math.clamp(input.getIntOr("SporeMelee", 0), 0, 40);
    }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() {
        List<CooldownDiscipline.Slot> slots = new ArrayList<>();
        for (int index = 0; index < 3; index++) { final int slot = index; slots.add(CooldownDiscipline.Slot.normal("spore_" + CLIPS[index], INTERVALS[index], () -> cooldowns[slot], value -> cooldowns[slot] = value)); }
        return List.copyOf(slots);
    }
    private static final class Cloud { private final Vec3 center; private int ticks = 40; private Cloud(Vec3 center) { this.center = center; } }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "breath" -> cooldowns[0];
            case "bloom" -> cooldowns[1];
            case "dive" -> cooldowns[2];
            case "storm" -> cooldowns[3];
            default -> -1;
        };
    }
    @Override protected void staffMountedCooldown(int ticks) { cooldowns[0] = ticks; }
}
