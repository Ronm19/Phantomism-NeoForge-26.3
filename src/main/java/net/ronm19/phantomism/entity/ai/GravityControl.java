package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.GravityPhantom;
import net.ronm19.phantomism.util.ModTags;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class GravityControl {
    public enum Mode { LIFT, THROW, LOCK }
    private static final Identifier LOCK = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "gravity_mass_lock");
    private static final Map<Mob, Lease> LEASES = new WeakHashMap<>();
    private static final Map<Mob, Long> RECOVERY = new WeakHashMap<>();
    private static final Map<Mob, Long> FORCED = new WeakHashMap<>();
    private static final Map<Mob, Long> FALL_SAFE = new WeakHashMap<>();
    private GravityControl() {}

    public static boolean movementImmune(Mob mob) {
        return mob.getMaxHealth() >= 80 || mob.getType() == EntityTypes.WITHER || mob.getType() == EntityTypes.ENDER_DRAGON
                || mob.getType() == EntityTypes.WARDEN || mob.getType() == EntityTypes.ELDER_GUARDIAN
                || mob.is(ModTags.EntityTypes.GRAVITY_MOVEMENT_IMMUNE) || mob.getBbWidth() > 3 || mob.getBbHeight() > 5;
    }
    public static boolean eligible(GravityPhantom source, Mob mob) {
        return source.validTarget(mob) && mob instanceof Enemy && !(mob instanceof AbstractPhantom)
                && !(mob instanceof OwnableEntity owned && owned.getOwnerReference() != null) && !mob.isPassenger() && !mob.isVehicle();
    }
    public static boolean controlled(Mob mob) { return LEASES.containsKey(mob); }
    public static boolean controlledBy(GravityPhantom source, Mob mob) { Lease lease = LEASES.get(mob); return lease != null && lease.source.equals(source.getUUID()); }
    public static boolean dangerous(ServerLevel level, BlockPos position) {
        var block = level.getBlockState(position);
        return !level.getFluidState(position).isEmpty() || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS)
                || block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE) || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE)
                || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.WITHER_ROSE);
    }
    private static boolean ground(GravityPhantom source, Mob mob, Vec3 feet, int depth) {
        ServerLevel level = (ServerLevel) mob.level();
        AABB box = mob.getBoundingBox().move(feet.subtract(mob.position()));
        for (double horizontal : new double[]{box.minX + 0.02D, box.maxX - 0.02D}) {
            for (double forward : new double[]{box.minZ + 0.02D, box.maxZ - 0.02D}) {
                boolean supported = false;
                for (int down = 1; down <= depth; down++) {
                    BlockPos below = BlockPos.containing(horizontal, feet.y - down + 0.01D, forward);
                    if (!level.hasChunkAt(below) || dangerous(level, below)) { return false; }
                    if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) { supported = true; break; }
                }
                if (!supported) { return false; }
            }
        }
        return true;
    }
    public static boolean safeStep(GravityPhantom source, Mob mob, Vec3 destination) {
        if (!(mob.level() instanceof ServerLevel level) || !Double.isFinite(destination.lengthSqr())) { return false; }
        Vec3 delta = destination.subtract(mob.position());
        AABB swept = mob.getBoundingBox().expandTowards(delta).deflate(0.001D);
        if (!source.isLoadedFlightBox(swept) || !level.getWorldBorder().isWithinBounds(swept)
                || !level.noCollision(mob, swept) || level.containsAnyLiquid(swept)) { return false; }
        for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            if (dangerous(level, position)) { return false; }
        }
        return ground(source, mob, destination, 8);
    }
    public static @Nullable Vec3 landing(GravityPhantom source, Mob mob, Vec3 desired) {
        if (!(mob.level() instanceof ServerLevel level) || !Double.isFinite(desired.lengthSqr()) || desired.distanceToSqr(mob.position()) > 100) { return null; }
        for (int height = 2; height >= -3; height--) {
            Vec3 candidate = new Vec3(desired.x, Math.floor(mob.getY()) + height, desired.z);
            if (safeStep(source, mob, candidate) && ground(source, mob, candidate, 1)) { return candidate; }
        }
        return null;
    }
    public static boolean apply(GravityPhantom source, Mob mob, Mode mode, @Nullable Vec3 destination) {
        if (!(mob.level() instanceof ServerLevel level) || !source.controlsEnabled() || !eligible(source, mob)
                || source.distanceToSqr(mob) > 24 * 24 || LEASES.containsKey(mob) || LEASES.size() >= 128
                || RECOVERY.getOrDefault(mob, Long.MIN_VALUE) > level.getGameTime()) { return false; }
        boolean immune = movementImmune(mob);
        if (mode != Mode.LOCK && (immune || mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.95D)) { return false; }
        if (mode != Mode.LOCK && (!ground(source, mob, mob.position(), 6) || !safeStep(source, mob, mob.position().add(0, 1.8D, 0)))) { return false; }
        if (mode == Mode.THROW) {
            if (destination == null || destination.distanceToSqr(mob.position()) < 4 || !safeStep(source, mob, destination) || !ground(source, mob, destination, 1)) { return false; }
            for (int step = 1; step <= 12; step++) {
                double progress = step / 12.0D;
                Vec3 sample = mob.position().lerp(destination, progress).add(0, Math.sin(progress * Math.PI) * 1.4D, 0);
                if (!safeStep(source, mob, sample)) { return false; }
            }
        }
        int duration = mode == Mode.LIFT ? 60 : mode == Mode.THROW ? 40 : immune ? 40 : 80;
        LEASES.put(mob, new Lease(source, mob, mode, level.getGameTime(), duration, destination));
        if (mode == Mode.LOCK) {
            var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) { speed.addOrUpdateTransientModifier(new AttributeModifier(LOCK, immune ? -0.15D : -0.55D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        } else { mob.getPersistentData().putBoolean("PhantomGravityOriginal", mob.isNoGravity()); mob.setNoGravity(true); mob.resetFallDistance(); FALL_SAFE.put(mob, level.getGameTime() + 220); }
        return true;
    }
    private static @Nullable GravityPhantom source(Mob mob, Lease lease) {
        if (!(mob.level() instanceof ServerLevel level) || level.getGameTime() >= lease.expires || !mob.isAlive() || mob.isRemoved()
                || !(level.getEntity(lease.source) instanceof GravityPhantom phantom)) { return null; }
        return phantom.controlsEnabled() && phantom.session().equals(lease.session) && Objects.equals(phantom.getOwnerReference(), lease.owner)
                && phantom.isTame() == lease.tame && eligible(phantom, mob) && phantom.distanceToSqr(mob) <= 32 * 32 ? phantom : null;
    }
    public static boolean compress(GravityPhantom source, Mob mob, int ticks) {
        if (!apply(source, mob, Mode.LOCK, null)) { return false; }
        Lease lease = LEASES.get(mob); lease.expires = mob.level().getGameTime() + Math.clamp(ticks, 1, 30); return true;
    }
    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) { return; }
        Lease lease = LEASES.get(mob);
        if (lease == null) { return; }
        GravityPhantom source = source(mob, lease);
        if (source == null || lease.mode != Mode.LOCK && movementImmune(mob)) { release(mob); return; }
        if (level.getGameTime() % 12 == 0) {
            if (lease.mode == Mode.LOCK) { net.ronm19.phantomism.vfx.PhantomVisualEffects.distortion(level, net.ronm19.phantomism.vfx.PhantomVfxTheme.GRAVITY, mob.position().add(0, 0.25D, 0), 0.65F); }
            else { net.ronm19.phantomism.vfx.PhantomVisualEffects.orbit(level, net.ronm19.phantomism.vfx.PhantomVfxTheme.GRAVITY, mob.getBoundingBox().getCenter(), 1, 3); }
        }
        if (lease.mode == Mode.LOCK) {
            if (!movementImmune(mob)) { Vec3 motion = mob.getDeltaMovement(); mob.setDeltaMovement(motion.x * 0.75D, motion.y, motion.z * 0.75D); }
            return;
        }
        double progress = Math.clamp((level.getGameTime() - lease.started + 1.0D) / lease.duration, 0, 1);
        Vec3 wanted;
        if (lease.mode == Mode.THROW) {
            wanted = lease.origin.lerp(lease.destination, progress).add(0, Math.sin(progress * Math.PI) * 1.4D, 0);
        } else {
            double rise = progress < 0.33D ? progress / 0.33D : progress < 0.66D ? 1 : (1 - progress) / 0.34D;
            wanted = lease.origin.add(0, Math.max(0, rise) * 1.8D, 0);
        }
        Vec3 offset = wanted.subtract(mob.position());
        Vec3 velocity = offset.length() > 0.35D ? offset.normalize().scale(0.35D) : offset;
        if (!safeStep(source, mob, mob.position().add(velocity))) { release(mob); return; }
        mob.setNoGravity(true); mob.resetFallDistance(); mob.setDeltaMovement(velocity); mob.needsSync = true;
    }
    public static boolean pull(GravityPhantom source, Mob mob, Vec3 center, double strength) {
        if (!(mob.level() instanceof ServerLevel level) || !source.controlsEnabled() || !eligible(source, mob) || movementImmune(mob) || LEASES.containsKey(mob)
                || FORCED.getOrDefault(mob, Long.MIN_VALUE) == level.getGameTime()) { return false; }
        double resistance = Math.clamp(1 - mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        Vec3 offset = center.subtract(mob.position()); if (offset.lengthSqr() < 0.16D || resistance < 0.05D) { return false; }
        Vec3 motion = mob.getDeltaMovement().add(offset.normalize().scale(Math.min(0.12D, strength) * resistance));
        double horizontal = motion.horizontalDistance();
        if (horizontal > 0.3D) { motion = new Vec3(motion.x * 0.3D / horizontal, motion.y, motion.z * 0.3D / horizontal); }
        motion = new Vec3(motion.x, Math.clamp(motion.y, -0.1D, 0.14D), motion.z);
        if (!safeStep(source, mob, mob.position().add(motion)) || !ground(source, mob, mob.position().add(motion), 3)) { return false; }
        FORCED.put(mob, level.getGameTime()); mob.setDeltaMovement(motion); mob.needsSync = true; return true;
    }
    public static void release(Mob mob) {
        Lease lease = LEASES.remove(mob); if (lease == null) { return; }
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) { speed.removeModifier(LOCK); }
        if (lease.mode != Mode.LOCK) { mob.setNoGravity(lease.noGravity); mob.getPersistentData().remove("PhantomGravityOriginal"); mob.setDeltaMovement(Vec3.ZERO); mob.resetFallDistance(); }
        RECOVERY.put(mob, mob.level().getGameTime() + 120);
    }
    public static void release(GravityPhantom source) {
        for (var entry : Map.copyOf(LEASES).entrySet()) { if (entry.getValue().source.equals(source.getUUID())) { release(entry.getKey()); } }
    }
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && mob.getPersistentData().contains("PhantomGravityOriginal")) {
            mob.setNoGravity(mob.getPersistentData().getBooleanOr("PhantomGravityOriginal", false)); mob.getPersistentData().remove("PhantomGravityOriginal");
            mob.resetFallDistance(); mob.setDeltaMovement(Vec3.ZERO); FALL_SAFE.put(mob, mob.level().getGameTime() + 160);
        }
    }
    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) { if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) { release(mob); } }
    @SubscribeEvent
    public static void defend(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof GravityPhantom source && source.hasGravityField() && source.controlsEnabled()
                && event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity enemy && source.validTarget(enemy)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) { event.setNewDamage(event.getNewDamage() * 0.75F); }
    }
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) { return; }
        Long expires = FALL_SAFE.get(mob);
        if (expires != null) { if (mob.level().getGameTime() <= expires) { event.setCanceled(true); mob.resetFallDistance(); } FALL_SAFE.remove(mob); }
    }
    @SubscribeEvent
    public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            Long expires = FALL_SAFE.get(mob);
            if (expires != null && !LEASES.containsKey(mob) && (mob.onGround() || mob.level().getGameTime() > expires || !mob.isAlive())) { FALL_SAFE.remove(mob); }
        }
    }
    private static final class Lease {
        private final UUID source;
        private final UUID session;
        private final Object owner;
        private final boolean tame;
        private final Mode mode;
        private final boolean noGravity;
        private final long started;
        private long expires;
        private final int duration;
        private final Vec3 origin;
        private final Vec3 destination;
        private Lease(GravityPhantom source, Mob mob, Mode mode, long started, int duration, @Nullable Vec3 destination) {
            this.source = source.getUUID(); this.session = source.session(); this.owner = source.getOwnerReference(); this.tame = source.isTame();
            this.mode = mode; this.noGravity = mob.isNoGravity(); this.started = started; this.duration = duration; this.expires = started + duration;
            this.origin = mob.position(); this.destination = destination == null ? this.origin : destination;
        }
    }
}
