package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.entity.ai.VampireInfluence;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class VampirePhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = id("entity/vampire_phantom");
    private static final Identifier NIGHT = id("vampire_night");
    private static final Identifier RAGE = id("vampire_rage");
    private static final Identifier MOON = id("vampire_blood_moon");
    private static final EntityDataAccessor<Byte> STANCE = SynchedEntityData.defineId(VampirePhantom.class, EntityDataSerializers.BYTE);
    private int biteCooldown;
    private int boltCooldown;
    private int mistCooldown;
    private int mistTicks;
    private int hypnosisCooldown;
    private int rageCooldown;
    private int rageTicks;
    private int moonCooldown;
    private int moonTicks;
    private int regenerationCooldown;
    private float vigor;
    private long vigorExpires;
    private int actionTicks;
    private int actionPriority;
    private int combatStarted;
    private int progressTick;
    private double closestDistance = Double.MAX_VALUE;
    private UUID combatSession = UUID.randomUUID();
    private @Nullable UUID explicitTarget;
    private @Nullable UUID pursuitTarget;
    private @Nullable UUID watchTarget;

    public VampirePhantom(EntityType<? extends VampirePhantom> type, Level level) { super(type, level); }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 64).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FLYING_SPEED, 0.425D).add(Attributes.FOLLOW_RANGE, 36);
    }
    public static boolean checkSpawnRules(EntityType<VampirePhantom> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000 && time < 23000 && level.getBiome(position).is(ModTags.Biomes.VAMPIRE_PHANTOM_SPAWNS)
                && position.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ())
                && level.getFluidState(position).isEmpty() && level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.VAMPIRE_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override public void travel(Vec3 input) { if (this.getControllingPassenger() == null) { this.travelFlying(input, (float) (this.getSpeed() * 0.09D)); } else { super.travel(input); } }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STANCE, (byte) 0); }
    private boolean stance(int mask) { return (this.entityData.get(STANCE) & mask) != 0; }
    private void stance(int mask, boolean active) { int flags = this.entityData.get(STANCE); this.entityData.set(STANCE, (byte) (active ? flags | mask : flags & ~mask)); }
    public boolean isNightPredator() { return this.stance(1); }
    public boolean hasCrimsonMist() { return this.stance(2); }
    public boolean isVampireRaging() { return this.stance(4); }
    public boolean isBloodMoon() { return this.stance(8); }
    @Override public boolean isUltimateAnimationActive() { return this.isBloodMoon() || super.isUltimateAnimationActive(); }
    public UUID combatSession() { return this.combatSession; }
    public boolean combatEnabled() { return this.isAlive() && !this.isRemoved() && !this.isBaby() && this.getOrder() != Order.STAY && this.getOrder() != Order.RETURN_HOME && (this.getTask() == Task.NONE || this.getTask() == Task.HUNT); }
    public boolean canInfluence() { return this.combatEnabled() && (!this.isTame() || this.getOwner() != null && this.getOwner().isAlive() && this.getOwner().level() == this.level()); }
    public static boolean lure(Player player) { return player.getMainHandItem().is(Items.NETHER_WART) || player.getOffhandItem().is(Items.NETHER_WART); }
    private boolean provoked(Player player) { return this.getLastHurtByMob() == player && this.tickCount - this.getLastHurtByMobTimestamp() <= 200; }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (target instanceof AbstractPhantom || target instanceof OwnableEntity owned && owned.getOwnerReference() != null || this.isAlliedTo(target) || VampireInfluence.isControlledBy(this, target)) { return false; }
        if (target instanceof Player player) {
            Entity vehicle = player.getRootVehicle();
            return !this.isTame() && !player.isCreative() && !player.isSpectator() && this.provoked(player)
                    && !(vehicle instanceof OwnableEntity owned && owned.getOwnerReference() != null);
        }
        boolean justified = target.getUUID().equals(this.explicitTarget) || target == this.getLastHurtByMob()
                || target instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == this || this.getOwner() != null && mob.getTarget() == this.getOwner());
        if ((target instanceof net.minecraft.world.entity.NeutralMob || target.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN) && !justified) { return false; }
        return target instanceof Enemy || justified;
    }
    public boolean isValidVampireTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == this.level() && !this.isTargetSuppressed(target)
                && this.isSpeciesCombatTarget(target) && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
    }
    @Override public void setCombatTarget(LivingEntity target) {
        UUID previous = this.explicitTarget; this.explicitTarget = target.getUUID(); super.setCombatTarget(target);
        if (this.getTarget() != target) { this.explicitTarget = previous; }
    }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return this.isTame() && this.getOrder() == Order.GUARD && target instanceof Enemy && this.getOwner() != null && this.getOwner().distanceToSqr(target) <= 24 * 24; }
    private boolean ownerThreat(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        return owner != null && owner.isAlive() && owner.level() == this.level() && target.distanceToSqr(owner) <= 16 * 16
                && (target instanceof Mob mob && mob.getTarget() == owner || owner.getLastHurtByMob() == target && owner.tickCount - owner.getLastHurtByMobTimestamp() <= 100);
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return this.distanceToSqr(target) + target.getHealth() - (this.ownerThreat(target) ? 1200 : 0); }
    public boolean line(ServerLevel level, Vec3 start, Vec3 end) { return this.isLoadedFlightBox(new AABB(start, end).inflate(0.04D)) && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    private boolean closeEnough(ServerLevel level, LivingEntity target) { return this.distanceToSqr(target) <= 9 && this.getBoundingBox().inflate(1.1D).intersects(target.getBoundingBox()) && this.line(level, this.getEyePosition(), target.getEyePosition()); }
    public List<Mob> hostiles(ServerLevel level, double range, int limit) {
        return level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range), target -> target instanceof Enemy && this.isValidVampireTarget(target)
                && this.distanceToSqr(target) <= range * range && this.getAssignments().permitsDefensivePursuit(target) && this.line(level, this.getEyePosition(), target.getEyePosition()))
                .stream().sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList();
    }
    public float hurtVampireTarget(ServerLevel level, LivingEntity target, DamageSource damage, float amount, boolean bite) {
        if (this.level() != level || !this.combatEnabled() || !Float.isFinite(amount) || amount <= 0 || !this.isValidVampireTarget(target) || !this.getAssignments().permitsDefensivePursuit(target)) { return 0; }
        float previous = target.getHealth() + target.getAbsorptionAmount();
        boolean hit = target instanceof EnderDragon dragon && this.getOwner() instanceof Player owner
                ? dragon.head.hurtServer(level, this.damageSources().playerAttack(owner), amount) : target.hurtServer(level, damage, amount);
        float actual = hit ? Math.max(0, Math.min(amount, previous - target.getHealth() - target.getAbsorptionAmount())) : 0;
        if (actual > 0) {
            this.progressTick = this.tickCount;
            if (bite) { if (this.isAlive()) { this.heal(actual * (this.isBloodMoon() ? 0.5F : 0.35F)); } }
            else { this.vigor = Math.min(3, this.vigor + actual * (this.isBloodMoon() ? 0.25F : 0.15F)); this.vigorExpires = level.getGameTime() + 80; }
        }
        return actual;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !this.combatEnabled() || this.biteCooldown > 0 || !this.isValidVampireTarget(target) || !this.closeEnough(level, target)) { return false; }
        this.biteCooldown = this.isBloodMoon() ? 32 : this.isVampireRaging() ? 36 : this.isNightPredator() ? 44 : 50;
        float actual = this.hurtVampireTarget(level, target, this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + 2, true);
        if (actual <= 0) { return false; }
        this.showAction("bite"); PhantomVisualEffects.crosscut(level, PhantomVfxTheme.VAMPIRE, target.getBoundingBox().getCenter()); this.playSound(SoundEvents.PHANTOM_BITE, 0.65F, 1.35F); return true;
    }
    public boolean activateRiddenBite(Player rider) {
        if (!(this.level() instanceof ServerLevel level) || this.getControllingPassenger() != rider || !this.isOwnedBy(rider) || !this.combatEnabled() || this.biteCooldown > 0) { return false; }
        Vec3 start = rider.getEyePosition(); Vec3 end = start.add(rider.getViewVector(1).scale(3.5D));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4), this::isValidVampireTarget).stream()
                .filter(candidate -> candidate.getBoundingBox().inflate(0.5D).clip(start, end).isPresent()).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null || !this.doHurtTarget(level, target)) { return false; }
        if (target.isAlive()) { this.setCombatTarget(target); } return true;
    }
    public boolean launchCrimsonBolt(ServerLevel level, Vec3 direction) {
        int interval = this.getControllingPassenger() instanceof Player ? 8 : this.isBloodMoon() ? 80 : this.isVampireRaging() ? 100 : 160;
        if (this.level() != level || !this.combatEnabled() || this.boltCooldown > 160 - interval || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 0.01D) { return false; }
        Vec3 normalized = direction.normalize(); Vec3 muzzle = this.getEyePosition().add(normalized.scale(0.7D));
        if (!this.line(level, this.getEyePosition(), muzzle)) { return false; }
        VampireBolt bolt = PhantomismContent.VAMPIRE_BOLT.get().create(level, EntitySpawnReason.TRIGGERED);
        bolt.setOwner(this); bolt.configure(this.combatSession, this.isBloodMoon()); bolt.setPos(muzzle); bolt.shoot(normalized.x, normalized.y, normalized.z, 1.25F, 0);
        if (!level.addFreshEntity(bolt)) { return false; }
        this.boltCooldown = 160; this.showAction("cast"); PhantomVisualEffects.vampireRings(level, muzzle, 0.45F); this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.7F); return true;
    }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return this.launchCrimsonBolt(level, rider.getViewVector(1)); }
    public boolean activateCrimsonMist(ServerLevel level) {
        if (this.level() != level || !this.canInfluence() || this.mistCooldown > 0 || this.mistTicks > 0) { return false; }
        this.mistCooldown = 400; this.mistTicks = 80; this.stance(2, true);
        for (Mob mob : this.hostiles(level, this.isBloodMoon() ? 10 : 8, 6)) { VampireInfluence.apply(this, mob, VampireInfluence.Mode.MIST, 40); }
        this.showAction("mist"); PhantomVisualEffects.vampireMist(level, this.getBoundingBox().getCenter()); return true;
    }
    public boolean hypnotize(ServerLevel level) {
        if (this.level() != level || !this.canInfluence() || this.hypnosisCooldown > 0) { return false; }
        List<Mob> nearby = this.hostiles(level, 12, 16);
        int budget = Math.min(this.isBloodMoon() ? 4 : 3, Math.max(1, nearby.size() - 1)); int count = 0;
        for (Mob mob : nearby) { if (count >= budget) { break; } if (VampireInfluence.apply(this, mob, VampireInfluence.Mode.HYPNOSIS, this.isBloodMoon() ? 140 : 100)) { count++; } }
        if (count == 0) { return false; }
        this.hypnosisCooldown = 600; this.showAction("hypnosis"); PhantomVisualEffects.vampireRings(level, this.getEyePosition(), 1.8F);
        if (this.getTarget() != null && VampireInfluence.isControlledBy(this, this.getTarget())) { this.clearCombatTarget(false); this.hostiles(level, 24, 1).stream().findFirst().ifPresent(this::setCombatTarget); }
        return true;
    }
    public boolean activateVampireRage(ServerLevel level) {
        if (this.level() != level || !this.combatEnabled() || this.rageCooldown > 0 || this.rageTicks > 0) { return false; }
        LivingEntity current = this.getTarget(); LivingEntity owner = this.getOwner();
        Mob threat = owner != null && owner.isAlive() && owner.level() == level && this.distanceToSqr(owner) <= 24 * 24
                && owner.getHealth() <= owner.getMaxHealth() * 0.25F ? this.hostiles(level, 24, 16).stream().filter(this::ownerThreat).findFirst().orElse(null) : null;
        boolean selfDanger = this.getHealth() <= this.getMaxHealth() * 0.3F && this.isValidVampireTarget(current) && this.distanceToSqr(current) <= 24 * 24;
        if (!selfDanger && threat == null) { return false; }
        if (threat != null && threat != current) { this.setCombatTarget(threat); }
        this.rageCooldown = 900; this.rageTicks = 160; this.stance(4, true); this.applyState(RAGE, 0.2D, 0.15D); this.showAction("rage"); PhantomVisualEffects.aura(level, PhantomVfxTheme.VAMPIRE, this.getBoundingBox().getCenter()); return true;
    }
    public boolean activateBloodMoon(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (this.level() != level || !this.combatEnabled() || this.moonCooldown > 0 || this.moonTicks > 0 || !this.isValidVampireTarget(target)
                || this.distanceToSqr(target) > 24 * 24 || !this.getAssignments().permitsDefensivePursuit(target) || !this.line(level, this.getEyePosition(), target.getEyePosition())) { return false; }
        this.moonCooldown = 1600; this.moonTicks = 200; this.stance(8, true); this.applyState(MOON, 0.25D, 0.12D); this.showAction("blood_moon");
        PhantomVisualEffects.vampireMoon(level, this.getBoundingBox().getCenter()); this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1F, 0.5F); return true;
    }
    private void applyState(Identifier identity, double damage, double speed) {
        this.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(identity, damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { this.getAttribute(attribute).addOrUpdateTransientModifier(new AttributeModifier(identity, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
    }
    private void removeState(Identifier identity) { for (var attribute : List.of(Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.FOLLOW_RANGE)) { this.getAttribute(attribute).removeModifier(identity); } }
    private void updateNight(ServerLevel level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L); boolean night = level.dimension() == Level.OVERWORLD && time >= 13000 && time < 23000 && !this.isBaby();
        if (this.isNightPredator() == night) { return; }
        this.stance(1, night);
        if (night) { this.applyState(NIGHT, 0.1D, 0.12D); this.getAttribute(Attributes.FOLLOW_RANGE).addOrUpdateTransientModifier(new AttributeModifier(NIGHT, 8, AttributeModifier.Operation.ADD_VALUE)); }
        else { this.removeState(NIGHT); }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float incoming = this.hasCrimsonMist() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount * 0.85F : amount;
        boolean hit = super.hurtServer(level, source, incoming);
        if (hit && this.isAlive() && source.getEntity() instanceof LivingEntity attacker && this.isValidVampireTarget(attacker)) {
            if (amount >= 4 || this.getHealth() < this.getMaxHealth() * 0.65F) { this.activateCrimsonMist(level); }
        }
        return hit;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (this.getTarget() instanceof Player player && !this.isSpeciesCombatTarget(player)) { this.clearCombatTarget(false); }
        super.customServerAiStep(level);
        if (!this.isTame() && !this.isBaby() && this.combatEnabled() && this.getTarget() == null) {
            if (this.tickCount % 20 == 0) {
                this.watchTarget = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16), player -> player.isAlive() && !player.isSpectator()
                        && this.line(level, this.getEyePosition(), player.getEyePosition())).stream()
                        .min(Comparator.comparingDouble(player -> this.distanceToSqr(player) - (lure(player) ? 300 : 0))).map(Entity::getUUID).orElse(null);
            }
            if (this.watchTarget != null && level.getEntity(this.watchTarget) instanceof Player player && player.isAlive() && this.distanceToSqr(player) <= 256) {
                this.getLookControl().setLookAt(player);
                if (lure(player) && this.distanceToSqr(player) > 9 && this.line(level, this.getEyePosition(), player.getEyePosition())) { this.flyToAssignment(player.position().add(0, 1.6D, 0), 0.7D); }
            }
        }
    }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!this.combatEnabled() || !this.isValidVampireTarget(target)) { this.clearCombatTarget(false); return true; }
        double distance = this.distanceToSqr(target);
        if (!target.getUUID().equals(this.pursuitTarget)) { this.pursuitTarget = target.getUUID(); this.combatStarted = this.tickCount; this.progressTick = this.tickCount; this.closestDistance = distance; }
        if (distance < this.closestDistance - 2) { this.closestDistance = distance; this.progressTick = this.tickCount; }
        if (this.tickCount - this.combatStarted > 600 || this.tickCount - this.progressTick > 200) { this.clearCombatTarget(true); return true; }
        if (this.hypnosisCooldown == 0 && this.hostiles(level, 12, 3).size() >= 2 && this.hypnotize(level)) { return true; }
        if (target.damageCooldownTime <= 10) {
            if (this.closeEnough(level, target)) { this.doHurtTarget(level, target); return true; }
            if (distance <= VampireBolt.MAX_TRAVEL_DISTANCE * VampireBolt.MAX_TRAVEL_DISTANCE && this.line(level, this.getEyePosition(), target.getEyePosition()) && this.launchCrimsonBolt(level, target.getBoundingBox().getCenter().subtract(this.getEyePosition()))) { return true; }
        }
        if (this.biteCooldown == 0 || this.getHealth() < this.getMaxHealth() * 0.65F || this.ownerThreat(target)) { this.flyToAssignment(target.position().add(0, 1, 0), 0.95D); }
        else { this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0); }
        return true;
    }
    @Override public void tick() {
        super.tick(); if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()) { return; }
        this.biteCooldown = Math.max(0, this.biteCooldown - 1); this.boltCooldown = Math.max(0, this.boltCooldown - 1);
        this.mistCooldown = Math.max(0, this.mistCooldown - 1); this.hypnosisCooldown = Math.max(0, this.hypnosisCooldown - 1);
        this.rageCooldown = Math.max(0, this.rageCooldown - 1); this.moonCooldown = Math.max(0, this.moonCooldown - 1);
        this.regenerationCooldown = Math.max(0, this.regenerationCooldown - 1); this.actionTicks = Math.max(0, this.actionTicks - 1);
        if (this.tickCount % 10 == 0 || this.tickCount == 1) { this.updateNight(level); }
        if (!this.combatEnabled()) { this.cancelActive(); return; }
        if (this.mistTicks > 0 && --this.mistTicks == 0) { this.stance(2, false); }
        if (this.rageTicks > 0 && --this.rageTicks == 0) { this.stance(4, false); this.removeState(RAGE); }
        if (this.moonTicks > 0 && --this.moonTicks == 0) { this.stance(8, false); this.removeState(MOON); }
        if (this.vigor > 0 && level.getGameTime() > this.vigorExpires) { this.vigor = 0; }
        if (this.vigor > 0 && this.regenerationCooldown == 0 && this.getHealth() < this.getMaxHealth()) {
            float healing = Math.min(this.isBloodMoon() ? 1.25F : 1, this.vigor); this.vigor -= healing; this.regenerationCooldown = 40; this.heal(healing);
            PhantomVisualEffects.vampireRings(level, this.getBoundingBox().getCenter(), 0.55F); this.showAction("regenerate");
        }
        if (this.tickCount % 10 == 0) {
            this.activateVampireRage(level);
            LivingEntity target = this.getTarget();
            if (this.isValidVampireTarget(target) && (target.getMaxHealth() >= 80 || this.hostiles(level, 12, 4).size() >= 3 || this.isVampireRaging() && this.ownerThreat(target))) { this.activateBloodMoon(level); }
            if (this.hasCrimsonMist()) { PhantomVisualEffects.vampireMist(level, this.getBoundingBox().getCenter()); }
            if (this.isBloodMoon()) { PhantomVisualEffects.vampireRings(level, this.getBoundingBox().getCenter(), 1.6F); }
            else if (this.isVampireRaging()) { PhantomVisualEffects.trail(level, PhantomVfxTheme.VAMPIRE, this.position()); }
        }
    }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) {
        var actions = new AnimationController<VampirePhantom>("vampire_action", 2, test -> PlayState.STOP);
        for (String name : new String[]{"bite", "cast", "mist", "hypnosis", "rage", "blood_moon", "regenerate"}) { actions.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.vampire." + name)); }
        registrar.add(actions.additiveAnimations());
    }
    private void showAction(String name) {
        int priority = switch (name) { case "blood_moon" -> 4; case "rage" -> 3; case "hypnosis", "mist" -> 2; case "regenerate" -> 0; default -> 1; };
        if (this.level() instanceof ServerLevel && this.isAddedToLevel() && (this.actionTicks == 0 || priority >= this.actionPriority)) {
            this.actionPriority = priority; this.actionTicks = name.equals("blood_moon") ? 36 : name.equals("rage") ? 24 : 12; this.triggerAnim("vampire_action", name);
        }
    }
    private void cancelActive() {
        VampireInfluence.release(this); this.combatSession = UUID.randomUUID(); this.mistTicks = 0; this.rageTicks = 0; this.moonTicks = 0; this.vigor = 0;
        this.stance(2, false); this.stance(4, false); this.stance(8, false); this.removeState(RAGE); this.removeState(MOON);
    }
    @Override public void clearCombatTarget(boolean suppressRetaliation) {
        super.clearCombatTarget(suppressRetaliation); this.pursuitTarget = null; this.explicitTarget = null; this.closestDistance = Double.MAX_VALUE;
        if (suppressRetaliation) { this.cancelActive(); }
    }
    @Override public void setOrder(Order order) { super.setOrder(order); if (order == Order.STAY || order == Order.RETURN_HOME) { this.clearCombatTarget(true); } }
    @Override public void setTask(Task task) { super.setTask(task); if (task != Task.NONE) { this.clearCombatTarget(true); } }
    @Override public void die(DamageSource source) { this.cancelActive(); this.removeState(NIGHT); super.die(source); }
    @Override public void remove(RemovalReason reason) { this.cancelActive(); this.removeState(NIGHT); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("VampireBiteCooldown", this.biteCooldown); output.putInt("VampireBoltCooldown", this.boltCooldown);
        output.putInt("VampireMistCooldown", this.mistCooldown); output.putInt("VampireHypnosisCooldown", this.hypnosisCooldown);
        output.putInt("VampireRageCooldown", this.rageCooldown); output.putInt("VampireMoonCooldown", this.moonCooldown);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.cancelActive(); this.removeState(NIGHT); this.stance(1, false); this.pursuitTarget = null; this.explicitTarget = null;
        this.biteCooldown = Math.clamp(input.getIntOr("VampireBiteCooldown", 0), 0, 50); this.boltCooldown = Math.clamp(input.getIntOr("VampireBoltCooldown", 0), 0, 160);
        this.mistCooldown = Math.clamp(input.getIntOr("VampireMistCooldown", 0), 0, 400); this.hypnosisCooldown = Math.clamp(input.getIntOr("VampireHypnosisCooldown", 0), 0, 600);
        this.rageCooldown = Math.clamp(input.getIntOr("VampireRageCooldown", 0), 0, 900); this.moonCooldown = Math.clamp(input.getIntOr("VampireMoonCooldown", 0), 0, 1600);
    }
}
