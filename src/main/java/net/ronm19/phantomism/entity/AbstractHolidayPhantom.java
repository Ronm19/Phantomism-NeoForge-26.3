package net.ronm19.phantomism.entity;

import java.util.Optional;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.codex.TamingProfile;
import net.ronm19.phantomism.entity.holiday.HolidayCalendar;
import net.ronm19.phantomism.entity.holiday.HolidayFestival;

public abstract class AbstractHolidayPhantom extends AbstractPhantom {
    public static final int RECOVERY_TICKS = 40;
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(AbstractHolidayPhantom.class, EntityDataSerializers.BOOLEAN);
    private int recoveryTicks;
    private long defeatedAtTick;
    private boolean previousNoGravity;
    private Vec3 safeAnchor;

    protected AbstractHolidayPhantom(EntityType<? extends AbstractHolidayPhantom> type, Level level) { super(type, level); }
    public abstract HolidayFestival festival();
    public final boolean isHolidayDefeated() { return entityData.get(DEFEATED); }
    public final int holidayRecoveryTicks() { return recoveryTicks; }
    public final boolean holidayNaturalSpawningAvailable() { return HolidayCalendar.naturalSpawningAllowed(festival()); }
    public static boolean naturalHolidaySpawn(EntitySpawnReason reason) { return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION; }
    public static boolean festivalAllowsSpawn(HolidayFestival festival, EntitySpawnReason reason) {
        return !naturalHolidaySpawn(reason) || HolidayCalendar.naturalSpawningAllowed(festival);
    }
    protected void onHolidayDefeated() {}

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(DEFEATED, false); }
    @Override public boolean isClientAuthoritative() { return !isHolidayDefeated() && super.isClientAuthoritative(); }
    @Override protected boolean isLocalClientAuthoritative() { return !isHolidayDefeated() && super.isLocalClientAuthoritative(); }
    @Override public boolean canAddPassenger(Entity passenger) { return !isHolidayDefeated() && super.canAddPassenger(passenger); }
    @Override public void travel(Vec3 input) {
        if (isHolidayDefeated()) { setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        if (isHolidayDefeated()) { getNavigation().stop(); setDeltaMovement(Vec3.ZERO); return; }
        super.customServerAiStep(level);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !isHolidayDefeated() && super.doHurtTarget(level, target); }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isHolidayDefeated() && !source.is(DamageTypes.GENERIC_KILL)) { return false; }
        return super.hurtServer(level, source, amount);
    }

    @Override public void die(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            recoveryTicks = 0;
            entityData.set(DEFEATED, false);
            onHolidayDefeated();
            super.die(source);
            return;
        }
        if (isRemoved() || !(level() instanceof ServerLevel level)) { return; }
        if (isHolidayDefeated()) { setHealth(1); return; }
        previousNoGravity = isNoGravity();
        recoveryTicks = RECOVERY_TICKS;
        defeatedAtTick = level.getGameTime();
        entityData.set(DEFEATED, true);
        syncRecovery(true);
        setHealth(1);
        deathTime = 0;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        onHolidayDefeated();
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + .4D, getZ(), 10, .7D, .3D, .7D, .02D);
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel level && isHolidayDefeated()) {
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
            if (level.getGameTime() > defeatedAtTick && recoveryTicks > 0 && --recoveryTicks == 0) {
                if (getY() < level.getMinY() + 2 && safeAnchor != null) {
                    AABB destination = getBoundingBox().move(safeAnchor.subtract(position()));
                    if (isLoadedFlightBox(destination) && level.noCollision(this, destination) && !level.containsAnyLiquid(destination)) { setPos(safeAnchor); needsSync = true; }
                }
                setHealth(getMaxHealth());
                clearFire();
                setNoGravity(previousNoGravity);
                entityData.set(DEFEATED, false);
                syncRecovery(false);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + .5D, getZ(), 12, .7D, .4D, .7D, .02D);
            }
        }
        super.tick();
        if (level() instanceof ServerLevel level && !isHolidayDefeated() && isAlive() && tickCount % 20 == 0
                && getY() > level.getMinY() + 4 && getY() < level.getMaxY() - 4 && isLoadedFlightBox(getBoundingBox())
                && level.noBlockCollision(this, getBoundingBox()) && !level.containsAnyLiquid(getBoundingBox())) { safeAnchor = position(); }
    }

    @Override public TamingProfile preferredTaming() {
        Item item = festival().alternativeItems().getFirst();
        float chance = festival().tamingChance(item);
        return new TamingProfile(item, chance, chance >= 1 ? 1 : 0, "Holiday alternatives use their individual chance per attempt. Phantom Staff: instant.");
    }

    private void syncRecovery(boolean defeated) {
        if (level() instanceof ServerLevel && getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer rider && rider.connection != null) {
            rider.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(SynchedEntityData.DataValue.create(DEFEATED, defeated))));
            if (!defeated) { rider.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(this)); }
        }
    }
    @Override public Optional<Item> preferredLure() { return Optional.of(festival().alternativeItems().getFirst()); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        float chance = festival().tamingChance(stack.getItem());
        if (!isTame() && isAlive() && !isHolidayDefeated() && !player.isSpectator() && chance > 0) {
            if (!level().isClientSide()) {
                if (!player.isCreative()) { stack.shrink(1); }
                if (getRandom().nextFloat() < chance) {
                    tame(player);
                    clearCombatTarget(true);
                    setOrder(Order.FOLLOW);
                    level().broadcastEntityEvent(this, (byte)7);
                } else { level().broadcastEntityEvent(this, (byte)6); }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("HolidayRecoveryTicks", recoveryTicks);
        output.putBoolean("HolidayPreviousNoGravity", previousNoGravity);
        if (safeAnchor != null) { output.putDouble("HolidaySafeX", safeAnchor.x); output.putDouble("HolidaySafeY", safeAnchor.y); output.putDouble("HolidaySafeZ", safeAnchor.z); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        recoveryTicks = Math.clamp(input.getIntOr("HolidayRecoveryTicks", 0), 0, RECOVERY_TICKS);
        previousNoGravity = input.getBooleanOr("HolidayPreviousNoGravity", false);
        defeatedAtTick = level().getGameTime();
        entityData.set(DEFEATED, recoveryTicks > 0);
        if (recoveryTicks > 0) { setHealth(1); setNoGravity(true); setDeltaMovement(Vec3.ZERO); }
        double horizontal = input.getDoubleOr("HolidaySafeX", Double.NaN);
        double height = input.getDoubleOr("HolidaySafeY", Double.NaN);
        double depth = input.getDoubleOr("HolidaySafeZ", Double.NaN);
        safeAnchor = Double.isFinite(horizontal) && Double.isFinite(height) && Double.isFinite(depth) ? new Vec3(horizontal, height, depth) : null;
    }
}
