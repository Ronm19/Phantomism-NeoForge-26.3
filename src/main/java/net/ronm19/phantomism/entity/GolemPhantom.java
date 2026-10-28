package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.TitaniumVfx;

public final class GolemPhantom extends AbstractPhantom {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "entity/golem_phantom");
    public static final int PUNCH_RANGE = 42, GUARD_DURATION = 160, GUARD_RADIUS = 12, IMPACT_DURATION = 200, IMPACT_RADIUS = 16;
    private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(GolemPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FORCED = SynchedEntityData.defineId(GolemPhantom.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> WORK = SynchedEntityData.defineId(GolemPhantom.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> CARGO = SynchedEntityData.defineId(GolemPhantom.class, EntityDataSerializers.BYTE);
    private static final int[] INTERVALS = {280, 200, 240, 600, 2200};
    private static final String[] CLIPS = {"hook", "fist", "ram", "slam", "followup", "guard", "excavate", "obsidian", "collect", "deliver", "earthbreaker", "skyfall"};
    private final int[] cooldowns = new int[5];
    private final GolemExcavation excavation = new GolemExcavation(this);
    private UUID session = UUID.randomUUID(), ordered, motionTarget, motionRider, comboTarget, ownerContext, aggressor;
    private int angerTicks, actionTicks, meleeTicks, guardTicks, motionTicks, windup, impactTicks, comboTicks, threatTicks, mountedPunchTicks;
    private boolean skyfall, impacted;
    private Vec3 impactCenter;

    public GolemPhantom(EntityType<? extends GolemPhantom> type, Level level) { super(type, level); refreshDimensions(); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 400).add(Attributes.ATTACK_DAMAGE, 16).add(Attributes.ARMOR, 30)
                .add(Attributes.ARMOR_TOUGHNESS, 12).add(Attributes.KNOCKBACK_RESISTANCE, .95D).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.MOVEMENT_SPEED, .2D).add(Attributes.FLYING_SPEED, .3D).add(Attributes.SCALE, 1.8D);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.GOLEM_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override protected int getMeleeAttackCooldownTicks() { return 40; }
    @Override protected boolean preservesCombatTargetWhileRidden() { return true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(ANGRY, false); builder.define(FORCED, false); builder.define(WORK, "idle"); builder.define(CARGO, (byte) 0); }
    @Override public Vec3 getPassengerRidingPosition(Entity passenger) { return position().add(new Vec3(0, .44D * getScale(), .22D * getScale()).yRot((float) Math.toRadians(-getYRot()))); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(FORCED) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(FORCED) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(FORCED)) { setDeltaMovement(Vec3.ZERO); return; } if (getControllingPassenger() == null) { travelFlying(input, (float) (getSpeed() * .09D)); } else { super.travel(input); } }
    public boolean angry() { return entityData.get(ANGRY); }
    public UUID session() { return session; }
    public GolemExcavation excavation() { return excavation; }
    public String workStatus() { return entityData.get(WORK); }
    public int occupiedCargoSlots() { return Byte.toUnsignedInt(entityData.get(CARGO)); }
    @Override public void tame(Player owner) { super.tame(owner); setOrder(Order.FOLLOW); }
    public int guardTicks() { return guardTicks; }
    public int impactTicks() { return impactTicks; }
    public int mountedPunchCooldown() { return mountedPunchTicks; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public boolean enabled() { return isAlive() && !isRemoved() && !isBaby() && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME && (!isTame() || getOwner() != null && getOwner().isAlive() && getOwner().level() == level()); }
    public boolean ally(LivingEntity target) {
        return target != null && (target == this || target == getOwner() || target instanceof Villager || target instanceof IronGolem || isAlliedTo(target)
                || target instanceof AbstractPhantom phantom && (Objects.equals(getOwnerReference(), phantom.getOwnerReference()) || isAlliedTo(phantom))
                || isTame() && target instanceof OwnableEntity owned && Objects.equals(getOwnerReference(), owned.getOwnerReference()));
    }
    public boolean protects(LivingEntity target) { return target != null && target.isAlive() && target.level() == level() && ally(target); }
    public static boolean recent(LivingEntity attacker, LivingEntity victim) { return victim != null && (victim.getLastHurtByMob() == attacker && victim.tickCount - victim.getLastHurtByMobTimestamp() < 100 || attacker instanceof Mob mob && mob.getTarget() == victim); }
    public boolean threatens(LivingEntity attacker, LivingEntity victim) { return protects(victim) && !PhantomDuels.pair(attacker, victim) && !PhantomDuels.excludesObserver(this, attacker) && recent(attacker, victim); }
    public boolean safeEnemy(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !ally(target) && !target.isInvulnerable()
                && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this) && !isTargetSuppressed(target) && !PhantomDuels.excludesObserver(this, target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob));
    }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) {
        if (!safeEnemy(target)) { return false; }
        boolean provoked = target.getUUID().equals(ordered) || threatTicks > 0 && target.getUUID().equals(aggressor) || recent(target, this) || threatens(target, getOwner()) || PhantomDuels.pair(this, target);
        if (target instanceof Player || target instanceof OwnableEntity owned && owned.getOwnerReference() != null || target instanceof AbstractPhantom) { return provoked; }
        return provoked || target instanceof Enemy && !(target instanceof NeutralMob) && target.getType() != EntityTypes.PIGLIN;
    }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && (!excavation.active() || threatens(target, getOwner()) || recent(target, this)); }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (excavation != null && excavation.busy() && enabled() && !validTarget(getTarget()) && !isVehicle()) { if (tickCount % 20 == 0) { getConfiguredStats().apply(this); } getNavigation().stop(); return; }
        super.customServerAiStep(level);
    }
    @Override protected double getAutomaticTargetPriority(LivingEntity target) { return (threatens(target, getOwner()) ? -1000 : 0) + distanceToSqr(target); }
    @Override public void setCombatTarget(LivingEntity target) { if (!safeEnemy(target) || !PhantomDuels.begin(this, target)) { return; } excavation.cancel(); ordered = target.getUUID(); super.setCombatTarget(target); }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.04D)) && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, int limit) { return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::validTarget).stream().filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, 1, 0), target.getEyePosition())).sorted(Comparator.comparingDouble(this::getAutomaticTargetPriority)).limit(limit).toList(); }
    public float damage(ServerLevel level, LivingEntity target, DamageSource source, float amount) { if (!enabled() || !validTarget(target) || !Float.isFinite(amount) || amount <= 0) { return 0; } float before = target.getHealth() + target.getAbsorptionAmount(); if (!target.hurtServer(level, source, amount)) { return 0; } notice(); return Math.max(0, before - target.getHealth() - target.getAbsorptionAmount()); }
    public void notice() { angerTicks = 100; entityData.set(ANGRY, true); }
    public void notice(LivingEntity attacker, LivingEntity victim) { if (enabled() && safeEnemy(attacker) && threatens(attacker, victim)) { aggressor = attacker.getUUID(); threatTicks = 100; notice(); if (ordered == null || !validTarget(getTarget())) { setTarget(attacker); } if (!isVehicle() && victim != this && distanceToSqr(victim) > 16 && distanceToSqr(victim) < 24 * 24) { Vec3 toward = attacker.position().subtract(victim.position()).normalize(); flyToAssignment(victim.position().add(toward.scale(3)).add(0, 1, 0), 1.3D); } } }
    public void incomingThreat(LivingEntity attacker, LivingEntity victim) {
        if (!enabled() || !protects(victim) || !safeEnemy(attacker) || PhantomDuels.pair(attacker, victim)) { return; }
        aggressor = attacker.getUUID(); threatTicks = 100; notice(); if (ordered == null || !validTarget(getTarget())) { setTarget(attacker); }
    }
    public void push(LivingEntity target, double strength, boolean launch) { if (!validTarget(target) || target instanceof Mob mob && GravityControl.movementImmune(mob)) { return; } Vec3 direction = position().subtract(target.position()); target.knockback(Math.min(1.2D, strength), direction.x, direction.z, damageSources().mobAttack(this), 0); if (launch && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < .7D) { Vec3 lift = new Vec3(0, .55D * (1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)), 0); if (isLoadedFlightBox(target.getBoundingBox().expandTowards(lift)) && level().noBlockCollision(target, target.getBoundingBox().expandTowards(lift))) { target.setDeltaMovement(target.getDeltaMovement().add(lift)); target.needsSync = true; } } }
    public boolean punch(ServerLevel level, LivingEntity target, boolean mounted) {
        if (!enabled() || (mounted ? mountedPunchTicks : cooldowns[2]) > 0 || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider)) || !validTarget(target) || distanceToSqr(target) > PUNCH_RANGE * PUNCH_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        if (distanceToSqr(target) <= 25) {
            if (damage(level, target, damageSources().mobAttack(this), 24) <= 0) { return false; } push(target, .9D, false);
            for (LivingEntity nearby : enemies(level, target.position(), 3, 6)) { if (nearby != target) { damage(level, nearby, damageSources().mobAttack(this), 6); push(nearby, .4D, false); } }
            action("hook", 6); TitaniumVfx.emit(this, TitaniumVfx.HOOK, null, target.position(), target.position().subtract(position()).normalize(), 18, 3);
        } else {
            Vec3 direction = target.getBoundingBox().getCenter().subtract(getEyePosition()).normalize();
            GolemFist fist = PhantomismContent.GOLEM_FIST.get().create(level, EntitySpawnReason.TRIGGERED); if (fist == null) { return false; }
            fist.setOwner(this); fist.configure(session); fist.setPos(getEyePosition()); fist.setDeltaMovement(direction.scale(1.5D));
            if (!level.addFreshEntity(fist)) { return false; }
            action("fist", 6); TitaniumVfx.emit(this, TitaniumVfx.FIST, fist, fist.position(), direction, 32, 2);
        }
        if (mounted) { mountedPunchTicks = 6; } else { cooldowns[2] = INTERVALS[2]; } notice(); sound(SoundEvents.IRON_GOLEM_ATTACK, .8F, .55F); return true;
    }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return 6; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { LivingEntity target = aimed(level, rider, PUNCH_RANGE); return target != null && punch(level, target, true); }
    private LivingEntity aimed(ServerLevel level, Player rider, double range) { return enemies(level, position(), range, 32).stream().filter(target -> rider.getViewVector(1).dot(target.getEyePosition().subtract(rider.getEyePosition()).normalize()) > .5D).findFirst().orElse(null); }
    public boolean activateRiddenDive(Player rider) { if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; } LivingEntity target = aimed(level, rider, 24); return target != null && (distanceToSqr(target) <= 36 ? slam(level) : ram(level, target)); }
    public boolean safeStep(Vec3 step) {
        if (!Double.isFinite(step.lengthSqr()) || step.lengthSqr() > 16) { return false; }
        for (Entity member : getSelfAndPassengers().limit(9).toList()) { AABB box = member.getBoundingBox().expandTowards(step); if (!isLoadedFlightBox(box) || !level().getWorldBorder().isWithinBounds(box) || !level().noBlockCollision(member, box) || level().containsAnyLiquid(box)) { return false; } if (level() instanceof ServerLevel server) { for (net.minecraft.core.BlockPos point : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ), net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ))) { if (GravityControl.dangerous(server, point)) { return false; } } } }
        return true;
    }
    private void forceMotion(boolean enabled) {
        boolean previous = entityData.get(FORCED); entityData.set(FORCED, enabled);
        if (getControllingPassenger() instanceof ServerPlayer rider && rider.connection != null && previous != enabled) { rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(FORCED, enabled)))); if (!enabled) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); } }
        if (!enabled) { motionTicks = 0; motionTarget = null; motionRider = null; setDeltaMovement(Vec3.ZERO); needsSync = true; }
    }
    public boolean ram(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || actionTicks > 0 || motionTicks > 0 || impactTicks > 0 || !validTarget(target) || distanceToSqr(target) > 24 * 24 || (!line(getEyePosition(), target.getEyePosition()) && !excavation.ramAuthorized())) { return false; }
        motionTarget = target.getUUID(); motionRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); motionTicks = 28; excavation.beginRam(); windup = 10; skyfall = false; cooldowns[0] = 280; action("ram", 30); forceMotion(true); notice(); TitaniumVfx.emit(this, TitaniumVfx.RAM, this, position(), target.position().subtract(position()).normalize(), 30, 3); return true;
    }
    public boolean slam(ServerLevel level) {
        if (!enabled() || cooldowns[1] > 0 || actionTicks > 0 || enemies(level, position(), 6, 1).isEmpty()) { return false; }
        cooldowns[1] = 200; action("slam", 18); notice();
        for (LivingEntity target : enemies(level, position(), 6, 12)) { if (damage(level, target, damageSources().mobAttack(this), 22) > 0) { push(target, .7D, true); if (comboTarget == null && target.isAlive() && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < .7D && !(target instanceof Mob mob && GravityControl.movementImmune(mob)) && getHealth() > getMaxHealth() * .7F && !isVehicle()) { comboTarget = target.getUUID(); comboTicks = 30; } } }
        TitaniumVfx.emit(this, TitaniumVfx.SLAM, null, position(), Vec3.ZERO, 30, 6); sound(SoundEvents.GENERIC_EXPLODE.value(), .7F, .65F); return true;
    }
    public boolean guard(ServerLevel level) { if (!enabled() || cooldowns[3] > 0 || guardTicks > 0) { return false; } guardTicks = GUARD_DURATION; cooldowns[3] = 600; action("guard", 16); notice(); TitaniumVfx.emit(this, TitaniumVfx.GUARD, this, position(), Vec3.ZERO, GUARD_DURATION, GUARD_RADIUS); sound(SoundEvents.ANVIL_LAND, .6F, .65F); return true; }
    public boolean covers(LivingEntity victim) { return enabled() && guardTicks > 0 && protects(victim) && distanceToSqr(victim) <= GUARD_RADIUS * GUARD_RADIUS && line(getEyePosition(), victim.getEyePosition()); }
    public boolean titan(ServerLevel level) {
        if (!enabled() || cooldowns[4] > 0 || impactTicks > 0 || motionTicks > 0 || actionTicks > 0 || enemies(level, position(), 24, 1).isEmpty()) { return false; }
        cooldowns[4] = 2200; impactTicks = IMPACT_DURATION; windup = 24; impacted = false; skyfall = !onGround(); impactCenter = position(); notice();
        motionRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); forceMotion(true); action(skyfall ? "skyfall" : "earthbreaker", 40);
        TitaniumVfx.emit(this, TitaniumVfx.CHARGE, this, position(), new Vec3(0, skyfall ? -1 : 1, 0), 36, 4); sound(SoundEvents.IRON_GOLEM_REPAIR, 1, .5F); return true;
    }
    private void impact(ServerLevel level) {
        impacted = true; impactCenter = position(); forceMotion(false);
        for (LivingEntity target : enemies(level, impactCenter, IMPACT_RADIUS, 24)) { if (damage(level, target, damageSources().mobAttack(this), 40) > 0) { push(target, 1.1D, true); if (!(target instanceof Mob mob && GravityControl.movementImmune(mob))) { target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 20, 0)); } } }
        TitaniumVfx.emit(this, TitaniumVfx.IMPACT, null, impactCenter, new Vec3(0, skyfall ? -1 : 1, 0), Math.max(1, impactTicks), IMPACT_RADIUS); sound(SoundEvents.GENERIC_EXPLODE.value(), 1, .5F); sound(SoundEvents.ANVIL_LAND, 1, .5F);
    }
    private void tickMotion(ServerLevel level) {
        if (entityData.get(FORCED) && motionRider != null && (getControllingPassenger() == null || !motionRider.equals(getControllingPassenger().getUUID()))) { forceMotion(false); impactTicks = 0; return; }
        if (impactTicks > 0) {
            impactTicks--; if (!impacted) { if (windup > 0) { windup--; setDeltaMovement(Vec3.ZERO); } else if (!skyfall || impactTicks <= 140) { impact(level); } else { Vec3 step = new Vec3(0, -.9D, 0); if (!safeStep(step)) { impact(level); } else { move(MoverType.SELF, step); needsSync = true; for (Entity passenger : getPassengers()) { positionRider(passenger); } } } }
            if (impactTicks == 0) { forceMotion(false); impactCenter = null; } return;
        }
        if (motionTicks <= 0) { return; }
        if (!(level.getEntity(motionTarget) instanceof LivingEntity target) || !validTarget(target)) { forceMotion(false); return; }
        if (--motionTicks == 0) { forceMotion(false); return; } if (windup > 0) { windup--; setDeltaMovement(Vec3.ZERO); return; }
        Vec3 step = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(.3D);
        for (int sample = 0; sample < 4; sample++) {
            if (getBoundingBox().inflate(.5D).intersects(target.getBoundingBox()) && line(getEyePosition(), target.getEyePosition())) { if (damage(level, target, damageSources().mobAttack(this), 32) > 0) { push(target, 1.1D, false); TitaniumVfx.emit(this, TitaniumVfx.SLAM, null, target.position(), step.normalize(), 24, 4); sound(SoundEvents.IRON_GOLEM_ATTACK, 1, .5F); } forceMotion(false); return; }
            if (!safeStep(step)) { excavation.breakRamObstacles(level, step); if (!safeStep(step)) { forceMotion(false); return; } }
            move(MoverType.SELF, step); setDeltaMovement(Vec3.ZERO); needsSync = true; for (Entity passenger : getPassengers()) { positionRider(passenger); }
        }
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity victim) { if (!(victim instanceof LivingEntity target) || !enabled() || meleeTicks > 0 || actionTicks > 0 || !validTarget(target) || !getBoundingBox().inflate(1).intersects(target.getBoundingBox()) || !line(getEyePosition(), target.getEyePosition())) { return false; } if (damage(level, target, damageSources().mobAttack(this), 16) <= 0) { return false; } meleeTicks = 40; action("hook", 12); TitaniumVfx.emit(this, TitaniumVfx.HOOK, null, target.position(), Vec3.ZERO, 16, 2); return true; }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) {
        if (!enabled() || !validTarget(target) || actionTicks > 0 || motionTicks > 0 || impactTicks > 0 && !impacted) { return true; }
        if (getHealth() < getMaxHealth() * .4F) { guard(level); if (getOwner() != null && distanceToSqr(getOwner()) > 25 && !isVehicle()) { flyToAssignment(getOwner().position().add(0, 2, 0), 1.1D); return true; } }
        if (!isVehicle() && enemies(level, position(), 16, 3).size() >= 3 && titan(level)) { return true; }
        if (distanceToSqr(target) <= 25 && slam(level)) { return true; }
        if (punch(level, target, false)) { return true; }
        if (!isVehicle() && getHealth() > getMaxHealth() * .7F && distanceToSqr(target) > 36 && ram(level, target)) { return true; }
        return false;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        entityData.set(WORK, excavation.status()); entityData.set(CARGO, (byte) excavation.cargo().getItems().stream().filter(stack -> !stack.isEmpty()).count());
        UUID current = getOwner() == null ? null : getOwner().getUUID(); if (ownerContext != null && !ownerContext.equals(current)) { stopActions(); } ownerContext = current;
        for (int index = 0; index < cooldowns.length; index++) { if (cooldowns[index] > 0) { cooldowns[index]--; } }
        if (mountedPunchTicks > 0) { mountedPunchTicks--; } if (actionTicks > 0) { actionTicks--; } if (meleeTicks > 0) { meleeTicks--; } if (guardTicks > 0) { guardTicks--; }
        if (threatTicks > 0 && --threatTicks == 0) { aggressor = null; } if (angerTicks > 0) { angerTicks--; } if (validTarget(getTarget()) && enabled()) { notice(); } else if (angerTicks == 0) { entityData.set(ANGRY, false); }
        if (isAlive() && tickCount % 100 == 0 && getHealth() < getMaxHealth()) { heal(2); }
        if (!enabled()) { if (entityData.get(FORCED) || guardTicks > 0) { stopActions(); } return; }
        tickMotion(level);
        tickCombo(level);
        if (tickCount % 10 == 0) {
            for (LivingEntity ward : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24), this::protects).stream().limit(16).toList()) {
                LivingEntity attacker = ward.getLastHurtByMob(); if (attacker != null && threatens(attacker, ward)) { notice(attacker, ward); if (ward == getOwner() || ward.getHealth() < ward.getMaxHealth() * .5F) { guard(level); } }
            }
            for (LivingEntity attacker : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24), this::safeEnemy).stream().limit(24).toList()) {
                if (attacker instanceof Mob mob && mob.getTarget() != null && threatens(attacker, mob.getTarget())) { notice(attacker, mob.getTarget()); if (mob.getTarget() == getOwner() || mob.getTarget().getHealth() < mob.getTarget().getMaxHealth() * .4F) { guard(level); } }
            }
            GolemDefense.intercept(this, level);
            if (!isVehicle() && validTarget(getTarget())) { prepareSpeciesCombatAction(level, getTarget()); }
        }
        if (!validTarget(getTarget()) && !isVehicle() && impactTicks == 0 && motionTicks == 0) { excavation.tick(level); }
    }
    private void tickCombo(ServerLevel level) {
        if (comboTicks <= 0) { return; } comboTicks--;
        LivingEntity target = comboTarget != null && level.getEntity(comboTarget) instanceof LivingEntity living ? living : null;
        boolean safe = validTarget(target) && distanceToSqr(target) <= 64 && !isVehicle() && line(getEyePosition(), target.getEyePosition()) && getHealth() > getMaxHealth() * .7F
                && (getOwner() == null || distanceToSqr(getOwner()) < 16 * 16 && !recent(target, getOwner()));
        if (!safe || comboTicks == 0) { comboTicks = 0; comboTarget = null; return; }
        if (comboTicks > 20) { return; }
        if (getBoundingBox().inflate(2).intersects(target.getBoundingBox()) && target.damageCooldownTime <= 10) {
            if (damage(level, target, damageSources().mobAttack(this), 10) > 0) { action("followup", 12); TitaniumVfx.emit(this, TitaniumVfx.HOOK, null, target.getBoundingBox().getCenter(), Vec3.ZERO, 16, 2); }
            comboTicks = 0; comboTarget = null;
        } else if (comboTicks % 3 == 0 && safeStep(target.position().subtract(position()).normalize().scale(.4D))) { flyToAssignment(target.position(), 1.2D); }
    }
    public void action(String clip, int ticks) { actionTicks = Math.max(actionTicks, ticks); if (isAddedToLevel()) { triggerAnim("golem", clip); } }
    private void sound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) { if (level() instanceof ServerLevel level) { level.playSound(null, blockPosition(), sound, SoundSource.NEUTRAL, volume, pitch); } }
    @Override protected void registerSpeciesAnimationControllers(AnimatableManager.ControllerRegistrar registrar) { var controller = new AnimationController<GolemPhantom>("golem", 2, state -> PlayState.STOP); for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.golem." + clip)); } registrar.add(controller.additiveAnimations()); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { if (!isTame() && isAlive() && !player.isSpectator() && player.getItemInHand(hand).is(preferredTaming().item())) { if (!level().isClientSide()) { if (!player.isCreative()) { player.getItemInHand(hand).shrink(1); } tame(player); clearCombatTarget(true); level().broadcastEntityEvent(this, (byte) 7); } return InteractionResult.SUCCESS; } return super.mobInteract(player, hand); }
    public void stopActions() { if (session != null && level() instanceof ServerLevel) { TitaniumVfx.cancel(this); } session = UUID.randomUUID(); forceMotion(false); guardTicks = 0; impactTicks = 0; comboTicks = 0; comboTarget = null; actionTicks = 0; ordered = null; aggressor = null; threatTicks = 0; }
    @Override public void setOrder(Order order) { super.setOrder(order); if (excavation != null) { excavation.cancel(); if (order == Order.STAY || order == Order.RETURN_HOME) { stopActions(); } } }
    @Override public void setTask(PhantomAssignments.Task task) { super.setTask(task); if (excavation != null) { excavation.cancel(); } }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && excavation != null) { excavation.cancel(); stopActions(); } }
    @Override public void die(DamageSource source) { excavation.dropCargo(); stopActions(); super.die(source); }
    @Override public void remove(RemovalReason reason) { if (excavation != null) { stopActions(); } super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int index = 0; index < cooldowns.length; index++) { output.putInt("TitaniumCooldown" + index, cooldowns[index]); } output.putInt("TitaniumAnger", angerTicks); output.putInt("TitaniumMountedPunch", mountedPunchTicks); excavation.save(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); stopActions(); for (int index = 0; index < cooldowns.length; index++) { cooldowns[index] = Math.clamp(input.getIntOr("TitaniumCooldown" + index, 0), 0, INTERVALS[index]); } mountedPunchTicks = Math.clamp(input.getIntOr("TitaniumMountedPunch", 0), 0, 6); angerTicks = Math.clamp(input.getIntOr("TitaniumAnger", 0), 0, 100); entityData.set(ANGRY, angerTicks > 0); excavation.load(input); }

    @Override
    public net.ronm19.phantomism.codex.TamingProfile preferredTaming() {
        return new net.ronm19.phantomism.codex.TamingProfile(Items.IRON_BLOCK, 1F, 1, "Use on an untamed Phantom. No item-based temptation goal is registered.");
    }

    @Override
    public java.util.List<String> codexCooldowns() {
        return java.util.List.of("Hook: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[0]), "Fist: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[1]), "Ram: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[2]), "Slam: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[3]), "Followup: " + net.ronm19.phantomism.codex.CodexCatalog.seconds(INTERVALS[4]));
    }

    @Override public int staffAbilityCooldown(String ability) {
        return switch (ability) {
            case "ram" -> cooldowns[0];
            case "slam" -> cooldowns[1];
            case "punch" -> cooldowns[2];
            case "guard" -> cooldowns[3];
            case "titan" -> cooldowns[4];
            default -> -1;
        };
    }
    // Mounted Punch already has its own timer; it must not rewrite AI Punch recovery.
    @Override protected void staffMountedCooldown(int ticks) {}
}
