package net.ronm19.phantomism.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.entity.ai.MirageIllusions;
import net.ronm19.phantomism.vfx.MirageVfx;

public final class MirageDecoy extends PathfinderMob implements GeoEntity {
    public enum Mode { VISUAL, PHYSICAL }
    private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PHYSICAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COMBAT = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GALE_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GALE_VARIANT = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OBSIDIAN_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OBSIDIAN_VARIANT = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRYSTAL_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRYSTAL_VARIANT = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PLAGUE_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHADOW_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ECLIPSE_VISUAL = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ATTACK = SynchedEntityData.defineId(MirageDecoy.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID casterId = new UUID(0, 0), session = new UUID(0, 0), budgetOwner = new UUID(0, 0);
    private Vec3 anchor = Vec3.ZERO;
    private long expires;
    private int formation;
    private boolean shattering;

    public MirageDecoy(EntityType<? extends MirageDecoy> type, Level level) { super(type, level); setNoGravity(true); setNoAi(true); this.xpReward = 0; }
    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(SPECIES, "mirage_phantom"); data.define(ANIMATION, "entity/mirage_phantom"); data.define(SIZE, 1F);
        data.define(BABY, false); data.define(PHYSICAL, false); data.define(COMBAT, false); data.define(ATTACK, false); data.define(ECLIPSE_VISUAL, 0); data.define(SHADOW_VISUAL, 0); data.define(PLAGUE_VISUAL, 0); data.define(GALE_VISUAL, 0); data.define(GALE_VARIANT, 0); data.define(OBSIDIAN_VISUAL, 0); data.define(OBSIDIAN_VARIANT, 0); data.define(CRYSTAL_VISUAL, 0); data.define(CRYSTAL_VARIANT, 0);
    }
    public void configure(MiragePhantom source, AbstractPhantom disguise, Mode mode, Vec3 position, int duration, int formation) {
        this.casterId = source.getUUID(); this.session = source.session(); this.budgetOwner = source.budgetOwner(); this.anchor = position; this.formation = formation;
        this.expires = level().getGameTime() + duration; setPos(position); setYRot(disguise.getYRot()); setYHeadRot(disguise.getYHeadRot());
        entityData.set(SPECIES, Arrays.stream(net.ronm19.phantomism.advancement.PhantomDiscovery.values()).filter(species -> species.entityType() == disguise.getType()).map(net.ronm19.phantomism.advancement.PhantomDiscovery::modelName).findFirst().orElse("mirage_phantom"));
        entityData.set(ANIMATION, disguise.getPhantomAnimationResource().getPath()); entityData.set(SIZE, Math.clamp(disguise.getScale(), .25F, 3F));
        entityData.set(GALE_VISUAL, disguise instanceof GalePhantom gale ? gale.visualState() : 0);
        entityData.set(GALE_VARIANT, disguise instanceof GalePhantom gale ? gale.variant() : 0);
        entityData.set(OBSIDIAN_VISUAL, disguise instanceof ObsidianPhantom obsidian ? obsidian.visualState() : 0);
        entityData.set(OBSIDIAN_VARIANT, disguise instanceof ObsidianPhantom obsidian ? obsidian.variant() : 0);
        entityData.set(CRYSTAL_VISUAL, disguise instanceof CrystalPhantom crystal ? crystal.visualState() : 0);
        entityData.set(CRYSTAL_VARIANT, disguise instanceof CrystalPhantom crystal ? crystal.variant() : 0);
        entityData.set(PLAGUE_VISUAL, disguise instanceof PlaguePhantom plague ? plague.visualState() : 0);
        entityData.set(SHADOW_VISUAL, disguise instanceof ShadowPhantom shadow ? shadow.visualState() : 0);
        entityData.set(ECLIPSE_VISUAL, disguise instanceof EclipsePhantom eclipse ? eclipse.visualState() : 0);
        entityData.set(BABY, disguise.isBaby()); entityData.set(PHYSICAL, mode == Mode.PHYSICAL); entityData.set(COMBAT, true); setHealth(mode == Mode.PHYSICAL ? 6 : 1);
    }
    public UUID casterId() { return casterId; }
    public UUID session() { return session; }
    public UUID budgetOwner() { return budgetOwner; }
    public String species() { return entityData.get(SPECIES); }
    public String animationPath() { return entityData.get(ANIMATION); }
    public float visualScale() { return entityData.get(SIZE) * (entityData.get(BABY) ? .6F : 1F); }
    public int galeVisual() { return entityData.get(GALE_VISUAL); }
    public int galeVariant() { return entityData.get(GALE_VARIANT); }
    public int obsidianVisual() { return entityData.get(OBSIDIAN_VISUAL); }
    public int obsidianVariant() { return entityData.get(OBSIDIAN_VARIANT); }
    public int crystalVisual() { return entityData.get(CRYSTAL_VISUAL); }
    public int crystalVariant() { return entityData.get(CRYSTAL_VARIANT); }
    public int plagueVisual() { return entityData.get(PLAGUE_VISUAL); }
    public int shadowVisual() { return entityData.get(SHADOW_VISUAL); }
    public int eclipseVisual() { return entityData.get(ECLIPSE_VISUAL); }
    public boolean copiedBaby() { return entityData.get(BABY); }
    public boolean combatEyes() { return entityData.get(COMBAT); }
    public boolean fakeAttack() { return entityData.get(ATTACK); }
    public Mode mode() { return entityData.get(PHYSICAL) ? Mode.PHYSICAL : Mode.VISUAL; }
    public MiragePhantom caster() { return level() instanceof ServerLevel level && level.getEntity(casterId) instanceof MiragePhantom source ? source : null; }
    public boolean validFor(MiragePhantom source) { return isAlive() && !isRemoved() && source.enabled() && source.level() == level() && casterId.equals(source.getUUID()) && session.equals(source.session()) && budgetOwner.equals(source.budgetOwner()) && level().getGameTime() < expires && distanceToSqr(source) <= 48 * 48; }
    @Override public boolean canBeCollidedWith(Entity other) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return isAlive() && !isRemoved(); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return false; }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean canAddPassenger(Entity passenger) { return false; }
    @Override protected boolean considersEntityAsAlly(Entity other) { MiragePhantom source = caster(); return source != null && other instanceof LivingEntity target && source.ally(target); }
    @Override protected void dropEquipment(ServerLevel level) {}
    @Override public boolean hurtServer(ServerLevel level, DamageSource damage, float amount) {
        MiragePhantom source = caster();
        if (source == null || !validFor(source) || !Float.isFinite(amount) || amount <= 0 || damage.getEntity() instanceof LivingEntity attacker && source.ally(attacker)) { return false; }
        if (mode() == Mode.VISUAL) { shatter(); } else { absorb(amount); }
        return true;
    }
    public float absorb(float amount) {
        if (mode() != Mode.PHYSICAL || !isAlive() || shattering || !Float.isFinite(amount) || amount <= 0) { return 0; }
        float absorbed = Math.min(getHealth(), amount); setHealth(getHealth() - absorbed);
        MiragePhantom source = caster(); if (source != null) { MirageVfx.emit(source, MirageVfx.SHATTER, null, position(), Vec3.ZERO, 14, 1.2F); }
        if (getHealth() <= 0) { shatter(); }
        return absorbed;
    }
    public void shatter() {
        if (shattering || isRemoved()) { return; } shattering = true;
        MiragePhantom source = caster(); if (source != null) { MirageVfx.emit(source, MirageVfx.SHATTER, null, position(), Vec3.ZERO, 18, visualScale()); }
        discard();
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) { return; }
        MiragePhantom source = caster(); if (source == null || !validFor(source)) { shatter(); return; }
        entityData.set(ATTACK, tickCount % 50 < 12);
        if (mode() == Mode.VISUAL) {
            double phase = tickCount * .035D + formation * Math.PI * .7D;
            Vec3 destination = anchor.add(Math.cos(phase) * .8D, Math.sin(phase * 1.3D) * .6D, Math.sin(phase) * .8D);
            Vec3 step = destination.subtract(position()); if (step.lengthSqr() > .02D) { step = step.normalize().scale(.12D); }
            if (source.safePlacement(position().add(step), .95D, .8D)) { move(MoverType.SELF, step); }
        }
        LivingEntity target = source.getTarget();
        if (target != null && source.validTarget(target)) { getLookControl().setLookAt(target); setYRot((float) (Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90)); setYBodyRot(getYRot()); setYHeadRot(getYRot()); }
        if (tickCount % 50 == 1 && target != null && source.validTarget(target) && source.line(getEyePosition(), target.getEyePosition())) {
            Vec3 beam = target.getBoundingBox().getCenter().subtract(getEyePosition()); if (beam.length() > 12) { beam = beam.normalize().scale(12); }
            MirageVfx.emit(source, MirageVfx.FAKE_BOLT, null, getEyePosition(), beam, 12, .4F);
        }
        if (mode() == Mode.PHYSICAL && tickCount % 2 == 0) {
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(3), shot -> MirageIllusions.eligibleProjectile(source, shot)).stream().limit(12).toList()) {
                Vec3 start = projectile.position().subtract(projectile.getDeltaMovement()), end = projectile.position().add(projectile.getDeltaMovement());
                if (getBoundingBox().inflate(.25D).clip(start, end).isEmpty() || !source.line(start, position())) { continue; }
                projectile.discard(); absorb(6); break;
            }
        }
    }
    @Override public void remove(RemovalReason reason) { MirageIllusions.forget(this); super.remove(reason); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); expires = 0; }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MirageDecoy>("reflection", 4, test -> test.setAndContinue(RawAnimation.begin().thenLoop(fakeAttack() ? net.ronm19.phantomism.entity.ai.MirageDisguiseAnimations.attack(animationPath()) : "animation.phantom.fly"))));
    }
}
