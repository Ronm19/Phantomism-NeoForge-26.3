package net.ronm19.phantomism.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.ai.*;
import net.ronm19.phantomism.entity.spawn.AutumnHabitat;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.AutumnVfx;

public final class AutumnPhantom extends AbstractPhantom implements CooldownDiscipline.Participant {
    public static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath("phantomism", "entity/autumn_phantom");
    public static final int BOLT_RANGE = 42, MOUNTED_INTERVAL = 6, GALE_DURATION = 160, GALE_RADIUS = 14;
    private static final int[] INTERVALS = {100, 200, 700, 600, 1500};
    private static final String[] KEYS = {"bolt", "rush", "guard", "gift", "gale"};
    private static final String[] CLIPS = {"bolt", "rush", "guard", "gift", "gale", "harvest", "rest"};
    private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(AutumnPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HARVEST_MODE = SynchedEntityData.defineId(AutumnPhantom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUSHING = SynchedEntityData.defineId(AutumnPhantom.class, EntityDataSerializers.BOOLEAN);
    private final int[] cooldowns = new int[5];
    private final Set<UUID> bolts = new LinkedHashSet<>();
    private final Map<UUID, Integer> galeVictims = new HashMap<>();
    private final AutumnHarvest harvest = new AutumnHarvest(this);
    private UUID session = UUID.randomUUID(), orderedTarget, ownerContext, rushTarget, rushRider;
    private Vec3 galeCenter;
    private int rushTicks, galeTicks, guardTicks, tameAttempts;
    private double rushTravelled;
    private boolean visualOpen, animationOpen, explicitSelection;

    public AutumnPhantom(EntityType<? extends AutumnPhantom> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 240).add(Attributes.ATTACK_DAMAGE, 15).add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .55D).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.MOVEMENT_SPEED, .3D).add(Attributes.FLYING_SPEED, .48D); }
    public static boolean checkSpawnRules(EntityType<AutumnPhantom> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (!(accessor instanceof ServerLevel level) || !AutumnHabitat.habitat(level, position)) { return false; }
        AABB box = type.getSpawnAABB(position.getX() + .5D, position.getY(), position.getZ() + .5D);
        return box.minY > level.getMinY() && box.maxY < level.getMaxY() && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.canSeeSky(position) && !level.containsAnyLiquid(box) && level.noBlockCollision(null, box);
    }
    @Override protected Config.PhantomStats getConfiguredStats() { return Config.AUTUMN_PHANTOM_STATS; }
    @Override public Identifier getPhantomAnimationResource() { return ANIMATIONS; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected int getFlightTurnRate() { return 19; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL, 0); data.define(HARVEST_MODE, 0); data.define(RUSHING, false); }
    @Override public boolean isClientAuthoritative() { return !entityData.get(RUSHING) && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !entityData.get(RUSHING) && super.isLocalClientAuthoritative(); }
    @Override public void travel(Vec3 input) { if (entityData.get(RUSHING)) { setDeltaMovement(Vec3.ZERO); return; } super.travel(input); }
    public int harvestMode() { return entityData.get(HARVEST_MODE); }
    public void harvestMode(AutumnHarvest.Mode mode) { entityData.set(HARVEST_MODE, mode.ordinal()); }
    @Override public boolean isUltimateAnimationActive() { return galeTicks > 0 || super.isUltimateAnimationActive(); }
    public int visual() { return entityData.get(VISUAL); }
    public UUID session() { return session; }
    public AutumnHarvest harvest() { return harvest; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public int galeTicks() { return galeTicks; }
    public int rushTicks() { return rushTicks; }
    public void visualStarted() { visualOpen = true; }
    public boolean enabled() { return isAlive() && !isRemoved() && !isBaby() && !PhantomHornRally.returning(this) && getOrder() != Order.STAY && getOrder() != Order.RETURN_HOME
            && (getTask() == PhantomAssignments.Task.NONE || getTask() == PhantomAssignments.Task.HUNT || getTask() == PhantomAssignments.Task.HARVEST)
            && (!isTame() || getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level() && level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner); }
    public boolean recipient(LivingEntity target) { return target != null && target.isAlive() && target.level() == level() && (target == this || isTame() && (target == getOwner()
            || getTeam() != null && getTeam().isAlliedTo(target.getTeam()) || target instanceof OwnableEntity pet && Objects.equals(getOwnerReference(), pet.getOwnerReference()) || CelestialBond.authorizedAlly(this, target))); }
    public boolean safeEnemy(LivingEntity target) { return target != null && target.isAlive() && !target.isRemoved() && target.level() == level() && !target.isInvulnerable()
            && !recipient(target) && !isAlliedTo(target) && !(target instanceof AbstractPhantom) && !hasPassenger(target) && !target.isPassengerOfSameVehicle(this)
            && (explicitSelection || !isTargetSuppressed(target)) && !PhantomDuels.excludesObserver(this, target)
            && !(target instanceof Player player && (player.isCreative() || player.isSpectator())) && !(target instanceof Mob mob && VampireInfluence.isHypnotized(mob))
            && !(target instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0)); }
    private boolean attacking(LivingEntity enemy, LivingEntity ward) { return ward != null && (ward.getLastHurtByMob() == enemy && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100 || enemy instanceof Mob mob && mob.getTarget() == ward); }
    private boolean engaged(LivingEntity target) { return target.getUUID().equals(orderedTarget) || attacking(target, this) || attacking(target, getOwner()) || target instanceof Mob mob && recipient(mob.getTarget()); }
    @Override protected boolean isSpeciesCombatTarget(LivingEntity target) { return safeEnemy(target) && (engaged(target) || getControllingPassenger() instanceof Player rider && isOwnedBy(rider)); }
    public boolean validTarget(LivingEntity target) { return isSpeciesCombatTarget(target) && getAssignments().permitsDefensivePursuit(target); }
    @Override protected boolean shouldHuntAutomatically(LivingEntity target, ServerLevel level) { return enabled() && validTarget(target) && line(getEyePosition(), target.getEyePosition()); }
    public boolean line(Vec3 start, Vec3 end) { return Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr()) && isLoadedFlightBox(new AABB(start, end).inflate(.1D))
            && level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS; }
    public List<LivingEntity> allies(ServerLevel level, double radius) { return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::recipient).stream().filter(ward -> distanceToSqr(ward) <= radius * radius && line(getEyePosition(), ward.getEyePosition())).sorted(Comparator.comparingDouble(this::distanceToSqr)).limit(16).toList(); }
    public List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) { return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), target -> validTarget(target) && engaged(target)).stream().filter(target -> target.distanceToSqr(center) <= radius * radius && line(center.add(0, .6D, 0), target.getEyePosition())).limit(16).toList(); }
    @Override public void setCombatTarget(LivingEntity target) { explicitSelection = true; try { if (safeEnemy(target)) { orderedTarget = target.getUUID(); super.setCombatTarget(target); } } finally { explicitSelection = false; } }
    public boolean damage(ServerLevel level, LivingEntity target, float amount) { return enabled() && validTarget(target) && amount > 0 && target.hurtServer(level, damageSources().mobAttack(this), amount); }
    public boolean bolt(ServerLevel level, LivingEntity target) {
        if (!enabled() || cooldowns[0] > 0 || !validTarget(target) || distanceToSqr(target) > BOLT_RANGE * BOLT_RANGE || !line(getEyePosition(), target.getEyePosition())) { return false; }
        return fire(level, target.getBoundingBox().getCenter().subtract(getEyePosition()), false);
    }
    public boolean fire(ServerLevel level, Vec3 aim, boolean mounted) {
        if (!enabled() || bolts.size() >= 16 || !Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < .001D || cooldowns[0] > 0
                || mounted && (!(getControllingPassenger() instanceof Player rider) || !isOwnedBy(rider))) { return false; }
        Vec3 direction = aim.normalize(), muzzle = getEyePosition().add(direction.scale(.7D)); if (!line(getEyePosition(), muzzle)) { return false; }
        AutumnBolt shot = PhantomismContent.AUTUMN_BOLT.get().create(level, EntitySpawnReason.TRIGGERED); if (shot == null) { return false; }
        shot.configure(this, session); shot.setPos(muzzle); shot.shoot(direction.x, direction.y, direction.z, 2F, 0);
        if (!level.addFreshEntity(shot)) { return false; } bolts.add(shot.getUUID()); cooldowns[0] = mounted ? 6 : 100;
        action("bolt"); AutumnVfx.emit(this, AutumnVfx.BOLT, shot, muzzle, direction, 24, 1); return true;
    }
    public void boltImpact(ServerLevel level, LivingEntity target, Vec3 point) { if (damage(level, target, 12)) { target.knockback(.35D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 12); AutumnVfx.emit(this, AutumnVfx.IMPACT, null, point, Vec3.ZERO, 16, 2); } }
    @Override protected int getRiddenProjectileAbilityCooldownTicks() { return MOUNTED_INTERVAL; }
    @Override protected boolean performRiddenProjectileAbility(ServerLevel level, Player rider) { return fire(level, rider.getViewVector(1), true); }
    public boolean activateRiddenRush(Player rider) { if (!(level() instanceof ServerLevel level) || getControllingPassenger() != rider || !isOwnedBy(rider)) { return false; } LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14), this::validTarget).stream().filter(enemy -> line(getEyePosition(), enemy.getEyePosition()) && enemy.position().subtract(getEyePosition()).normalize().dot(rider.getViewVector(1)) > .7D).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null); return rush(level, target); }
    public boolean rush(ServerLevel level, LivingEntity target) { if (!enabled() || cooldowns[1] > 0 || rushTicks > 0 || !validTarget(target) || distanceToSqr(target) > 196 || !line(getEyePosition(), target.getEyePosition())) { return false; } cooldowns[1] = 200; rushTarget = target.getUUID(); rushRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID(); rushTicks = 12; rushTravelled = 0; entityData.set(RUSHING, true); syncRushRider(false); setDeltaMovement(Vec3.ZERO); action("rush"); AutumnVfx.emit(this, AutumnVfx.RUSH, this, position(), getViewVector(1), 16, 3); return true; }
    private void stopRush() { boolean active = entityData.get(RUSHING); rushTicks = 0; rushTarget = null; rushRider = null; entityData.set(RUSHING, false); if (active) { setDeltaMovement(Vec3.ZERO); needsSync = true; syncRushRider(true); } }
    private void syncRushRider(boolean release) { if (level() instanceof ServerLevel && getControllingPassenger() instanceof ServerPlayer rider && rider.connection != null) { rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(RUSHING, entityData.get(RUSHING))))); if (release) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); } } }
    private void tickRush(ServerLevel level) {
        if (rushTicks <= 0) { return; } Entity entity = level.getEntity(rushTarget);
        if (!Objects.equals(rushRider, getControllingPassenger() == null ? null : getControllingPassenger().getUUID()) || !(entity instanceof LivingEntity target) || !validTarget(target) || !line(getEyePosition(), target.getEyePosition())) { stopRush(); return; }
        if (distanceToSqr(target) <= 6.25D) { if (damage(level, target, 18)) { target.knockback(.6D, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), 18); AutumnVfx.emit(this, AutumnVfx.IMPACT, null, target.position(), Vec3.ZERO, 18, 3); } stopRush(); return; }
        if (rushTravelled >= 14) { stopRush(); return; }
        Vec3 difference = target.position().subtract(position()), step = difference.normalize().scale(Math.min(14 - rushTravelled, Math.min(1.4D, difference.length())));
        if (!isLoadedFlightBox(getBoundingBox().expandTowards(step)) || !level.noBlockCollision(this, getBoundingBox().move(step)) || level.containsAnyLiquid(getBoundingBox().move(step))) { stopRush(); return; }
        Vec3 previous = position(); setDeltaMovement(Vec3.ZERO); move(MoverType.SELF, step); needsSync = true; for (Entity passenger : getPassengers()) { positionRider(passenger); } rushTravelled += position().distanceTo(previous); if (--rushTicks == 0) { stopRush(); }
    }
    public boolean threatened(ServerLevel level) { return !enemies(level, position(), 10).isEmpty() || allies(level, 10).stream().anyMatch(ward -> ward.getLastHurtByMob() != null && safeEnemy(ward.getLastHurtByMob()) && ward.tickCount - ward.getLastHurtByMobTimestamp() < 60); }
    public boolean guard(ServerLevel level) { if (!enabled() || cooldowns[2] > 0 || !threatened(level)) { return false; } cooldowns[2] = 700; guardTicks = 160; for (var ward : allies(level, 10)) { AutumnProtection.ward(this, ward, .25F, 160, 10); } action("guard"); AutumnVfx.emit(this, AutumnVfx.GUARD, this, position(), Vec3.ZERO, 160, 10); return true; }
    public boolean gift(ServerLevel level) { if (!enabled() || cooldowns[3] > 0) { return false; } var injured = allies(level, 10).stream().filter(ward -> ward.getHealth() <= ward.getMaxHealth() - 4).toList(); if (injured.isEmpty()) { return false; } cooldowns[3] = 600; for (var ward : injured) { float health = ward.getHealth(); ward.heal(8); if (ward.getHealth() > health) { AutumnVfx.emit(this, AutumnVfx.GIFT, ward, ward.position(), Vec3.ZERO, 28, 1.5F); } } action("gift"); return true; }
    public boolean gale(ServerLevel level) { if (!enabled() || cooldowns[4] > 0 || enemies(level, position(), 14).size() < 3) { return false; } cooldowns[4] = 1500; galeTicks = 160; galeCenter = position(); galeVictims.clear(); action("gale"); AutumnVfx.emit(this, AutumnVfx.GALE, null, galeCenter, Vec3.ZERO, 160, 14); return true; }
    private void tickGale(ServerLevel level) { if (galeTicks <= 0) { return; } if (distanceToSqr(galeCenter) > 400 || !isLoadedFlightBox(new AABB(galeCenter, galeCenter).inflate(1))) { endAutumn(); return; } if ((160 - galeTicks) % 20 == 0) {
            for (var enemy : enemies(level, galeCenter, 14)) { if (galeVictims.size() >= 16 && !galeVictims.containsKey(enemy.getUUID())) { continue; } int hits = galeVictims.getOrDefault(enemy.getUUID(), 0); if (hits < 8 && damage(level, enemy, 5)) { galeVictims.put(enemy.getUUID(), hits + 1); enemy.knockback(.18D, galeCenter.x - enemy.getX(), galeCenter.z - enemy.getZ(), damageSources().mobAttack(this), 5); } }
            for (var ward : allies(level, 14)) { if (ward.distanceToSqr(galeCenter) <= 196) { AutumnProtection.ward(this, ward, .1F, 25, 14); } }
        } if (--galeTicks == 0) { AutumnVfx.emit(this, AutumnVfx.FINALE, null, galeCenter, Vec3.ZERO, 24, 16); galeVictims.clear(); } }
    @Override protected boolean prepareSpeciesCombatAction(ServerLevel level, LivingEntity target) { if (!enabled() || !validTarget(target)) { return false; } if (guard(level) || gift(level) || gale(level)) { return true; } return distanceToSqr(target) > 49 ? bolt(level, target) : distanceToSqr(target) > 6.25D && rush(level, target); }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        for (int slot = 0; slot < cooldowns.length; slot++) { cooldowns[slot] = Math.max(0, cooldowns[slot] - 1); }
        bolts.removeIf(identity -> level.getEntity(identity) == null);
        UUID context = getOwnerReference() == null ? getUUID() : getOwnerReference().getUUID(); if (ownerContext != null && !ownerContext.equals(context)) { endAutumn(); harvest.disable(); } ownerContext = context;
        if (isAlive() && tickCount % 40 == 0) { AutumnVfx.emit(this, AutumnVfx.LEAVES, this, position(), Vec3.ZERO, 36, 2); harvest.sense(level); }
        if (!enabled()) { if (rushTicks > 0 || galeTicks > 0 || guardTicks > 0 || !bolts.isEmpty()) { endAutumn(); } return; }
        tickRush(level); tickGale(level); if (guardTicks > 0) { guardTicks--; }
        entityData.set(VISUAL, galeTicks > 0 ? 3 : guardTicks > 0 ? 2 : validTarget(getTarget()) ? 1 : 0);
        if (tickCount % 40 == 0 && (getOrder() == Order.GUARD || getOrder() == Order.GUARD_POSITION)) { for (var ward : allies(level, 8)) { AutumnProtection.ward(this, ward, .08F, 50, 8); } }
        if (!isNoAi() && tickCount % 20 == 0) { if (threatened(level)) { guard(level); } gift(level); if (!isVehicle() && validTarget(getTarget())) { prepareSpeciesCombatAction(level, getTarget()); } }
    }
    private void action(String clip) { animationOpen = true; triggerAnim("autumn", clip); playSound(switch (clip) { case "gale" -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value(); case "rush", "bolt" -> SoundEvents.BREEZE_SHOOT; case "gift" -> SoundEvents.AMETHYST_BLOCK_CHIME; case "guard" -> SoundEvents.AMETHYST_BLOCK_RESONATE; case "harvest" -> SoundEvents.AZALEA_LEAVES_BREAK; default -> SoundEvents.AZALEA_LEAVES_STEP; }, clip.equals("gale") ? .65F : .3F, clip.equals("gale") ? .8F : 1.1F); }
    public void harvesting() { action("harvest"); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { super.registerControllers(controllers); var controller = new AnimationController<AutumnPhantom>("autumn", 3, state -> PlayState.STOP); for (String clip : CLIPS) { controller.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.autumn." + clip)); } controllers.add(controller); }
    public void endAutumn() { boolean active = visualOpen || animationOpen || !bolts.isEmpty() || rushTicks > 0 || galeTicks > 0 || guardTicks > 0; if (level() instanceof ServerLevel level) { for (UUID identity : List.copyOf(bolts)) { Entity shot = level.getEntity(identity); if (shot != null) { shot.discard(); } } if (visualOpen) { AutumnVfx.cancel(this); } if (animationOpen) { stopTriggeredAnim("autumn", null); } } if (active) { session = UUID.randomUUID(); } visualOpen = false; animationOpen = false; bolts.clear(); galeVictims.clear(); stopRush(); galeTicks = 0; guardTicks = 0; entityData.set(VISUAL, 0); AutumnProtection.clear(this); }
    @Override public void clearCombatTarget(boolean suppress) { super.clearCombatTarget(suppress); if (suppress && cooldowns != null) { endAutumn(); orderedTarget = null; } }
    @Override public void setOrder(Order order) { super.setOrder(order); if (cooldowns != null && (order == Order.STAY || order == Order.RETURN_HOME)) { endAutumn(); orderedTarget = null; } }
    @Override public void remove(RemovalReason reason) { if (cooldowns != null) { endAutumn(); } super.remove(reason); }
    @Override protected void dropEquipment(ServerLevel level) { super.dropEquipment(level); harvest.drop(level); }
    @Override public TamingProfile preferredTaming() { return new TamingProfile(Items.APPLE, .5F, 3, "Apples: 50%, guaranteed third eligible attempt. Staff: instant."); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { var stack = player.getItemInHand(hand); if (!isTame() && isAlive() && !player.isSpectator() && stack.is(Items.APPLE)) { if (!level().isClientSide()) { tameAttempts++; if (!player.isCreative()) { stack.shrink(1); } if (tameAttempts >= 3 || getRandom().nextFloat() < .5F) { tame(player); clearCombatTarget(true); setOrder(Order.FOLLOW); level().broadcastEntityEvent(this, (byte) 7); } else { level().broadcastEntityEvent(this, (byte) 6); } } return InteractionResult.SUCCESS; } return super.mobInteract(player, hand); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); for (int slot = 0; slot < 5; slot++) { output.putInt("AutumnCooldown" + slot, cooldowns[slot]); } output.putInt("AutumnTamingAttempts", tameAttempts); harvest.save(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); endAutumn(); for (int slot = 0; slot < 5; slot++) { cooldowns[slot] = Math.clamp(input.getIntOr("AutumnCooldown" + slot, 0), 0, INTERVALS[slot]); } tameAttempts = Math.clamp(input.getIntOr("AutumnTamingAttempts", 0), 0, 3); harvest.load(input); }
    @Override public List<CooldownDiscipline.Slot> temporalCooldowns() { var result = new ArrayList<CooldownDiscipline.Slot>(); for (int index = 0; index < 4; index++) { final int slot = index; result.add(CooldownDiscipline.Slot.normal("autumn_" + KEYS[slot], INTERVALS[slot], () -> cooldowns[slot], value -> cooldowns[slot] = value)); } return List.copyOf(result); }
    @Override public int staffAbilityCooldown(String ability) { for (int slot = 0; slot < 5; slot++) { if (KEYS[slot].equals(ability)) { return cooldowns[slot]; } } return List.of("crops", "vegetation", "wood", "harvest_off").contains(ability) ? 0 : -1; }
    @Override public List<String> codexCooldowns() { return List.of("Autumn Bolt: 42 blocks, 5 seconds AI/Staff, 0.3 seconds mounted.", "Leaf Rush: 10 seconds. Seasonal Guard: 8 seconds, 35-second recovery.", "Harvest's Gift: 30 seconds. Autumn Gale: 8 seconds, 75-second recovery.", "Harvesting is disabled until an owner explicitly selects an approved area or individual wood block."); }
}
