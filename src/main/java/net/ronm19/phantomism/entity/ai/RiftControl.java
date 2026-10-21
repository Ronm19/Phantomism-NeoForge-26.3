package net.ronm19.phantomism.entity.ai;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.RiftPhantom;
import net.ronm19.phantomism.util.ModTags;
import net.ronm19.phantomism.vfx.RiftVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class RiftControl {
    private record Lease(UUID source, UUID session, long expires, boolean lock) {}
    private static final Map<LivingEntity, Lease> LEASES = new WeakHashMap<>();
    private static final Identifier SLOW = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "rift_disruption");
    private static final Map<ServerLevel, Long> SWEEPS = new WeakHashMap<>();
    private RiftControl() {}
    public static boolean immune(LivingEntity target) { return target instanceof Mob mob && GravityControl.movementImmune(mob) || target.is(ModTags.EntityTypes.RIFT_TELEPORT_IMMUNE); }
    private static RiftPhantom caster(LivingEntity target, Lease lease) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target.isRemoved() || level.getGameTime() >= lease.expires || immune(target)) { return null; }
        return level.getEntity(lease.source) instanceof RiftPhantom source && source.controlsEnabled() && source.session().equals(lease.session) && source.validTarget(target) && source.distanceToSqr(target) <= 48 * 48 ? source : null;
    }
    public static boolean locked(LivingEntity target) { Lease lease = LEASES.get(target); if (lease == null) { return false; } if (caster(target, lease) == null) { release(target); return false; } return lease.lock; }
    public static boolean apply(RiftPhantom source, Mob target, boolean lock) {
        if (!source.controlsEnabled() || !source.validTarget(target) || immune(target) || LEASES.containsKey(target) || LEASES.size() >= 128
                || source.distanceToSqr(target) > (lock ? 24 * 24 : 39 * 39) || target.getPersistentData().getLongOr("RiftControlRecovery", Long.MIN_VALUE) > target.level().getGameTime()) { return false; }
        LEASES.put(target, new Lease(source.getUUID(), source.session(), target.level().getGameTime() + (lock ? 80 : 24), lock));
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.addOrUpdateTransientModifier(new AttributeModifier(SLOW, lock ? -.3D : -.25D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); } }
        return true;
    }
    private static void release(LivingEntity target) { if (LEASES.remove(target) == null) { return; } for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.removeModifier(SLOW); } } target.getPersistentData().putLong("RiftControlRecovery", target.level().getGameTime() + 60); }
    public static void release(RiftPhantom source) { for (var entry : Map.copyOf(LEASES).entrySet()) { if (entry.getValue().source.equals(source.getUUID())) { release(entry.getKey()); } } }
    public static void maintain(ServerLevel level) { long bucket = Math.floorDiv(level.getGameTime(), 20); if (SWEEPS.getOrDefault(level, Long.MIN_VALUE) == bucket) { return; } SWEEPS.put(level, bucket); for (var entry : Map.copyOf(LEASES).entrySet()) { if (entry.getKey().level() == level && caster(entry.getKey(), entry.getValue()) == null) { release(entry.getKey()); } } }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) { if (event.getEntity() instanceof LivingEntity target && !target.level().isClientSide()) { Lease lease = LEASES.get(target); if (lease != null && caster(target, lease) == null) { release(target); } } }
    @SubscribeEvent public static void teleport(EntityTeleportEvent event) { if (event.getEntity() instanceof LivingEntity target && locked(target)) { event.setCanceled(true); Lease lease = LEASES.get(target); RiftPhantom source = caster(target, lease); if (source != null && target.level().getGameTime() % 8 == 0) { RiftVfx.emit(source, RiftVfx.LOCK, target, target.position(), net.minecraft.world.phys.Vec3.ZERO, 12, 1); } } }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event) { if (event.getEntity() instanceof LivingEntity target && locked(target)) { event.setCanceled(true); } }
    @SubscribeEvent public static void danger(LivingDamageEvent.Pre event) {
        var ally = event.getEntity(); if (!(ally.level() instanceof ServerLevel level) || event.getNewDamage() < 4) { return; }
        var sources = level.getEntitiesOfClass(RiftPhantom.class, ally.getBoundingBox().inflate(24), source -> source.controlsEnabled() && source.acceptsAlly(ally));
        for (RiftPhantom source : sources.stream().sorted(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(ally))).limit(4).toList()) {
            if (event.getNewDamage() >= ally.getMaxHealth() * .2F || ally.getHealth() - event.getNewDamage() <= ally.getMaxHealth() * .35F) { if (source.rescue(level, ally, true)) { break; } }
        }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) { if (event.getLevel().isClientSide()) { return; } if (event.getEntity() instanceof RiftPhantom source) { release(source); } if (event.getEntity() instanceof LivingEntity target) { release(target); } }
}
