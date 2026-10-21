package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.VampirePhantom;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class VampireInfluence {
    public enum Mode { MIST, HYPNOSIS }
    private static final Map<Mob, Lease> LEASES = new WeakHashMap<>();
    private static final Map<Mob, Long> RECOVERY = new WeakHashMap<>();
    private VampireInfluence() {}

    public static boolean family(VampirePhantom source, @Nullable LivingEntity target) {
        return target != null && (target == source || target instanceof Player || target instanceof AbstractPhantom
                || target instanceof OwnableEntity owned && owned.getOwnerReference() != null || source.isAlliedTo(target));
    }
    public static boolean eligible(Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && mob instanceof Enemy && !(mob instanceof AbstractPhantom) && !(mob instanceof Creeper)
                && !(mob instanceof OwnableEntity owned && owned.getOwnerReference() != null) && mob.getMaxHealth() < 80
                && mob.getType() != EntityTypes.WITHER && mob.getType() != EntityTypes.ENDER_DRAGON && mob.getType() != EntityTypes.WARDEN
                && mob.getType() != EntityTypes.ELDER_GUARDIAN && !mob.is(ModTags.EntityTypes.VAMPIRE_HYPNOSIS_IMMUNE);
    }
    private static @Nullable VampirePhantom source(Mob mob, Lease lease) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isRemoved() || level.getGameTime() >= lease.expires
                || !eligible(mob) || !(level.getEntity(lease.source) instanceof VampirePhantom vampire)) { return null; }
        return vampire.canInfluence() && vampire.combatSession().equals(lease.session) && vampire.isTame() == lease.tame
                && Objects.equals(lease.owner, vampire.getOwnerReference()) && vampire.distanceToSqr(mob) <= 16 * 16
                && vampire.getAssignments().permitsDefensivePursuit(mob) ? vampire : null;
    }
    public static boolean isControlledBy(VampirePhantom vampire, LivingEntity target) {
        if (!(target instanceof Mob mob)) { return false; }
        Lease lease = LEASES.get(mob); VampirePhantom owner = lease == null ? null : source(mob, lease);
        return owner != null && lease.mode == Mode.HYPNOSIS && (owner == vampire || owner.isTame() && vampire.isTame()
                && Objects.equals(owner.getOwnerReference(), vampire.getOwnerReference()));
    }
    public static boolean isHypnotized(Mob mob) { Lease lease = LEASES.get(mob); return lease != null && lease.mode == Mode.HYPNOSIS && source(mob, lease) != null; }
    private static boolean rival(VampirePhantom vampire, Mob actor, @Nullable LivingEntity target) {
        return target != null && target != actor && target.isAlive() && !target.isRemoved() && target.level() == actor.level() && target instanceof Enemy
                && !family(vampire, target) && !actor.isAlliedTo(target) && actor.canAttack(target) && !(target instanceof Creeper)
                && (!(target instanceof Mob mob) || !isHypnotized(mob)) && vampire.distanceToSqr(target) <= 16 * 16
                && actor.distanceToSqr(target) <= 12 * 12 && vampire.line((ServerLevel) actor.level(), actor.getEyePosition(), target.getBoundingBox().getCenter());
    }
    private static @Nullable LivingEntity choose(VampirePhantom vampire, Mob actor) {
        return actor.level().getEntitiesOfClass(Mob.class, actor.getBoundingBox().inflate(12), target -> rival(vampire, actor, target)).stream()
                .min(Comparator.comparingDouble(target -> actor.distanceToSqr(target))).orElse(null);
    }
    public static boolean apply(VampirePhantom vampire, Mob mob, Mode mode, int duration) {
        if (!(vampire.level() instanceof ServerLevel level) || mob.level() != level || !vampire.canInfluence() || !eligible(mob)
                || !vampire.isValidVampireTarget(mob) || vampire.distanceToSqr(mob) > 12 * 12 || !vampire.getAssignments().permitsDefensivePursuit(mob)
                || !vampire.line(level, vampire.getEyePosition(), mob.getEyePosition())) { return false; }
        Lease old = LEASES.get(mob);
        if (old != null) { if (source(mob, old) != null) { return false; } end(mob, old); }
        if (LEASES.size() >= 128 || RECOVERY.getOrDefault(mob, 0L) > level.getGameTime()) { return false; }
        if (mode == Mode.MIST && !family(vampire, mob.getTarget()) && !family(vampire, attackMemory(mob))) { return false; }
        var brain = mob.getBrain(); LivingEntity memory = attackMemory(mob);
        Snapshot snapshot = new Snapshot(identity(mob.getTarget()), identity(memory), memory == null ? 0 : brain.getTimeUntilExpiry(MemoryModuleType.ATTACK_TARGET), level.getGameTime());
        Lease lease = new Lease(vampire, mode, snapshot, level.getGameTime() + Math.clamp(duration, 20, mode == Mode.MIST ? 40 : 140));
        LEASES.put(mob, lease); enforce(mob, vampire, lease);
        PhantomVisualEffects.vampireRings(level, mob.getEyePosition(), mode == Mode.HYPNOSIS ? 1.1F : 0.7F); return true;
    }
    private static @Nullable UUID identity(@Nullable LivingEntity target) { return target == null ? null : target.getUUID(); }
    private static @Nullable LivingEntity attackMemory(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED) ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
    }
    private static void enforce(Mob mob, VampirePhantom vampire, Lease lease) {
        LivingEntity target = lease.rival == null ? null : ((ServerLevel) mob.level()).getEntity(lease.rival) instanceof LivingEntity living ? living : null;
        if (lease.mode == Mode.HYPNOSIS && !rival(vampire, mob, target)) {
            if (mob.level().getGameTime() >= lease.nextScan) { target = choose(vampire, mob); lease.nextScan = mob.level().getGameTime() + 10; }
            else { target = null; }
            lease.rival = identity(target);
        }
        if (lease.mode == Mode.MIST) {
            if (family(vampire, mob.getTarget())) { mob.setTarget(null); mob.getNavigation().stop(); }
            if (family(vampire, attackMemory(mob))) { mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); }
        } else {
            if (mob.getTarget() != target) { mob.setTarget(target); mob.getNavigation().stop(); }
            if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                if (target == null) { mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); }
                else { mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, Math.max(1, lease.expires - mob.level().getGameTime())); }
            }
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) { return; }
        Lease lease = LEASES.get(mob);
        if (lease == null) { if (RECOVERY.getOrDefault(mob, Long.MAX_VALUE) <= level.getGameTime()) { RECOVERY.remove(mob); } return; }
        VampirePhantom vampire = source(mob, lease);
        if (vampire == null) { end(mob, lease); return; }
        enforce(mob, vampire, lease);
        if (lease.mode == Mode.HYPNOSIS && mob.tickCount % 20 == 0) { PhantomVisualEffects.vampireRings(level, mob.getEyePosition(), 0.5F); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) { return; }
        Lease lease = LEASES.get(mob); VampirePhantom vampire = lease == null ? null : source(mob, lease);
        if (vampire == null || event.getNewAboutToBeSetTarget() == null) { return; }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (lease.mode == Mode.MIST ? family(vampire, target) : !target.getUUID().equals(lease.rival) || !rival(vampire, mob, target)) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob)) { return; }
        Lease lease = LEASES.get(mob); VampirePhantom vampire = lease == null ? null : source(mob, lease);
        if (vampire != null && (lease.mode == Mode.MIST ? family(vampire, event.getEntity()) : !rival(vampire, mob, event.getEntity()))) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void provoked(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getInflictedDamage() <= 0 || !(event.getSource().getEntity() instanceof LivingEntity actor)) { return; }
        Lease lease = LEASES.get(mob); VampirePhantom vampire = lease == null ? null : source(mob, lease);
        if (vampire != null && lease.mode == Mode.HYPNOSIS && family(vampire, actor)) { end(mob, lease); }
    }
    public static void release(VampirePhantom vampire) {
        for (var entry : java.util.List.copyOf(LEASES.entrySet())) { if (entry.getValue().source.equals(vampire.getUUID())) { end(entry.getKey(), entry.getValue()); } }
    }
    private static void end(Mob mob, Lease lease) {
        if (LEASES.get(mob) != lease) { return; }
        LEASES.remove(mob); RECOVERY.put(mob, mob.level().getGameTime() + (lease.mode == Mode.HYPNOSIS ? 200 : 120));
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isRemoved()) { return; }
        LivingEntity current = mob.getTarget(); LivingEntity memory = attackMemory(mob);
        boolean targetOwned = current == null || current.getUUID().equals(lease.rival);
        boolean memoryOwned = memory == null || memory.getUUID().equals(lease.rival);
        if (targetOwned) {
            LivingEntity restored = resolve(level, lease.snapshot.target); mob.setTarget(restored != null && mob.canAttack(restored) ? restored : null); mob.getNavigation().stop();
        }
        if (memoryOwned && mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            LivingEntity restored = resolve(level, lease.snapshot.memory); long lifetime = lease.snapshot.lifetime;
            long remaining = lifetime == Long.MAX_VALUE ? Long.MAX_VALUE : lifetime - (level.getGameTime() - lease.snapshot.started);
            if (restored != null && mob.canAttack(restored) && remaining > 0) {
                if (remaining == Long.MAX_VALUE) { mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, restored); }
                else { mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, restored, remaining); }
            } else { mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); }
        }
    }
    private static @Nullable LivingEntity resolve(ServerLevel level, @Nullable UUID identity) { return identity != null && level.getEntity(identity) instanceof LivingEntity target && target.isAlive() && !target.isRemoved() ? target : null; }
    private record Snapshot(@Nullable UUID target, @Nullable UUID memory, long lifetime, long started) {}
    private static final class Lease {
        private final UUID source;
        private final UUID session;
        private final @Nullable Object owner;
        private final boolean tame;
        private final Mode mode;
        private final Snapshot snapshot;
        private final long expires;
        private @Nullable UUID rival;
        private long nextScan;
        private Lease(VampirePhantom vampire, Mode mode, Snapshot snapshot, long expires) { this.source = vampire.getUUID(); this.session = vampire.combatSession(); this.owner = vampire.getOwnerReference(); this.tame = vampire.isTame(); this.mode = mode; this.snapshot = snapshot; this.expires = expires; }
    }
}
