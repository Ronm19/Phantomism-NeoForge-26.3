package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ChronoPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class TemporalControl {
    private enum Kind { ACCELERATE, SLOW, STOP }
    private record Lease(UUID source, UUID session, long expires, Kind kind) {}
    private static final Map<LivingEntity, Lease> BUFFS = new WeakHashMap<>();
    private static final Map<LivingEntity, Lease> SLOWS = new WeakHashMap<>();
    private static final Map<LivingEntity, Lease> STOPS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> RECOVERY = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> MAINTENANCE = new WeakHashMap<>();
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "temporal_accelerate");
    private static final Identifier SLOW = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "temporal_slow");
    private TemporalControl() {}
    public static void maintain(ServerLevel level) {
        long bucket = Math.floorDiv(level.getGameTime(), 20);
        if (MAINTENANCE.getOrDefault(level, Long.MIN_VALUE) == bucket) { return; } MAINTENANCE.put(level, bucket);
        for (var leases : java.util.List.of(BUFFS, SLOWS, STOPS)) { for (var entry : Map.copyOf(leases).entrySet()) { if (entry.getKey().level() == level && !valid(entry.getKey(), entry.getValue())) { release(entry.getKey(), leases); } } }
    }
    private static boolean valid(LivingEntity target, Lease lease) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target.isRemoved() || level.getGameTime() >= lease.expires) { return false; }
        return level.getEntity(lease.source) instanceof ChronoPhantom source && source.combatEnabled() && source.session().equals(lease.session)
                && source.distanceToSqr(target) <= 48 * 48 && (lease.kind == Kind.ACCELERATE ? source.acceptsAlly(target) : source.validTarget(target));
    }
    public static boolean accelerate(ChronoPhantom source, LivingEntity ally) {
        if (!source.combatEnabled() || !source.acceptsAlly(ally) || source.distanceToSqr(ally) > 24 * 24 || BUFFS.containsKey(ally) || BUFFS.size() >= 128) { return false; }
        BUFFS.put(ally, new Lease(source.getUUID(), source.session(), ally.level().getGameTime() + 120, Kind.ACCELERATE));
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.ATTACK_SPEED)) {
            var instance = ally.getAttribute(attribute);
            if (instance != null && instance.getValue() < 4) { instance.addOrUpdateTransientModifier(new AttributeModifier(SPEED, attribute == Attributes.ATTACK_SPEED ? .1D : .15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        }
        return true;
    }
    public static boolean slow(ChronoPhantom source, Mob target, int ticks) {
        if (!source.combatEnabled() || !source.validTarget(target) || source.distanceToSqr(target) > 39 * 39 || SLOWS.containsKey(target) || SLOWS.size() >= 128
                || RECOVERY.getOrDefault(target, Long.MIN_VALUE) > target.level().getGameTime()) { return false; }
        boolean boss = GravityControl.movementImmune(target);
        SLOWS.put(target, new Lease(source.getUUID(), source.session(), target.level().getGameTime() + Math.clamp(ticks, 1, boss ? 40 : 120), Kind.SLOW));
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) { speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW, boss ? -.1D : -.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
        return true;
    }
    public static boolean stop(ChronoPhantom source, Mob target) {
        return stop(source, target, source.position());
    }
    public static boolean stop(ChronoPhantom source, Mob target, Vec3 center) {
        if (!Double.isFinite(center.lengthSqr()) || source.position().distanceToSqr(center) > 32 * 32 || !source.combatEnabled() || !source.validTarget(target) || target.position().distanceToSqr(center) > 24 * 24 || GravityControl.movementImmune(target)
                || target.isPassenger() || target.isVehicle() || STOPS.containsKey(target) || STOPS.size() >= 128 || RECOVERY.getOrDefault(target, Long.MIN_VALUE) > target.level().getGameTime()) { return false; }
        STOPS.put(target, new Lease(source.getUUID(), source.session(), target.level().getGameTime() + 120, Kind.STOP));
        target.setDeltaMovement(Vec3.ZERO); target.needsSync = true; return true;
    }
    public static boolean stopped(LivingEntity target) {
        Lease lease = STOPS.get(target); if (lease == null) { return false; }
        if (!valid(target, lease) || !(target instanceof Mob mob) || GravityControl.movementImmune(mob)) { release(target, STOPS); return false; }
        return true;
    }
    private static void release(LivingEntity target, Map<LivingEntity, Lease> leases) {
        Lease removed = leases.remove(target); if (removed == null) { return; }
        if (removed.kind == Kind.ACCELERATE) {
            for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.ATTACK_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.removeModifier(SPEED); } }
        } else {
            if (removed.kind == Kind.SLOW) { var speed = target.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) { speed.removeModifier(SLOW); } }
            RECOVERY.put(target, target.level().getGameTime() + (removed.kind == Kind.STOP ? 160 : 60));
        }
    }
    public static void release(ChronoPhantom source) {
        for (var leases : java.util.List.of(BUFFS, SLOWS, STOPS)) { for (var entry : Map.copyOf(leases).entrySet()) { if (entry.getValue().source.equals(source.getUUID())) { release(entry.getKey(), leases); } } }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target.level().isClientSide()) { return; }
        for (var leases : java.util.List.of(BUFFS, SLOWS)) { Lease lease = leases.get(target); if (lease != null && !valid(target, lease)) { release(target, leases); } }
        if (stopped(target)) { target.setDeltaMovement(Vec3.ZERO); target.needsSync = true; event.setCanceled(true); }
    }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && stopped(attacker)) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void defense(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ChronoPhantom source && source.shiftFor(event.getSource(), event.getNewDamage())) { event.setNewDamage(event.getNewDamage() * .35F); }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof ChronoPhantom source) { release(source); }
        if (event.getEntity() instanceof LivingEntity target) { for (var leases : java.util.List.of(BUFFS, SLOWS, STOPS)) { release(target, leases); } RECOVERY.remove(target); }
    }
}
