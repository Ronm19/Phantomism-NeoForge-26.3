package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.EclipsePhantom;
import net.ronm19.phantomism.vfx.EclipseVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class EclipseSupport {
    private record Shield(WeakReference<EclipsePhantom> source, UUID session, long until, float reduction) {}
    private static final Map<LivingEntity, Shield> SHIELDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> HEALS = new WeakHashMap<>();
    private EclipseSupport() {}
    public static void maintain() {
        SHIELDS.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue()));
        HEALS.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue());
    }
    private static boolean live(LivingEntity target, Shield shield) {
        EclipsePhantom source = shield.source().get();
        return source != null && source.enabled() && source.session().equals(shield.session()) && source.recipient(target)
                && source.distanceToSqr(target) <= 24 * 24 && target.level().getGameTime() < shield.until();
    }
    public static boolean shield(EclipsePhantom source, LivingEntity target, int duration, float reduction) {
        maintain(); if (!source.enabled() || !source.recipient(target) || source.distanceToSqr(target) > 24 * 24 || duration <= 0 || !Float.isFinite(reduction)
                || !source.line(source.getEyePosition(), target.getEyePosition()) || SHIELDS.size() >= 256 && !SHIELDS.containsKey(target)) { return false; }
        Shield existing = SHIELDS.get(target); float bounded = Math.clamp(reduction, 0, .4F);
        if (existing != null && existing.reduction() > bounded) { return false; }
        SHIELDS.put(target, new Shield(new WeakReference<>(source), source.session(), source.level().getGameTime() + Math.min(duration, 100), bounded)); return true;
    }
    public static float mitigation(LivingEntity target) { Shield shield = SHIELDS.get(target); return shield != null && live(target, shield) ? shield.reduction() : 0; }
    public static boolean heal(EclipsePhantom source, LivingEntity target, float amount, int interval) {
        maintain(); if (!source.enabled() || !source.recipient(target) || source.distanceToSqr(target) > 24 * 24 || !Float.isFinite(amount) || amount <= 0
                || !source.line(source.getEyePosition(), target.getEyePosition()) || HEALS.getOrDefault(target, Long.MIN_VALUE) > target.level().getGameTime()
                || HEALS.size() >= 256 && !HEALS.containsKey(target) || target.getHealth() >= target.getMaxHealth()) { return false; }
        HEALS.put(target, target.level().getGameTime() + Math.max(60, interval)); float before = target.getHealth(); target.heal(Math.min(4, amount));
        if (target.getHealth() <= before) { return false; } EclipseVfx.emit(source, EclipseVfx.HEAL, target, target.position(), Vec3.ZERO, 20, 1); return true;
    }
    public static void push(EclipsePhantom source, LivingEntity target, Vec3 direction, double amount) {
        if (!(target instanceof Mob mob) || !source.validTarget(target) || GravityControl.movementImmune(mob) || mob.isPassenger() || mob.isVehicle() || !Double.isFinite(direction.lengthSqr())) { return; }
        Vec3 flat = direction.multiply(1, 0, 1).normalize(); mob.knockback(Math.clamp(amount, 0, .4D), -flat.x, -flat.z, source.damageSources().mobAttack(source), 0);
    }
    public static boolean domainNearby(EclipsePhantom source) {
        return !source.level().getEntitiesOfClass(EclipsePhantom.class, source.getBoundingBox().inflate(40), other -> other != source && other.enabled() && other.totalTicks() > 0 && (source.recipient(other) || other.recipient(source))).isEmpty();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingDamageEvent.Pre event) {
        LivingEntity ward = event.getEntity(); float incoming = event.getNewDamage();
        if (!(ward.level() instanceof ServerLevel level) || !Float.isFinite(incoming) || incoming <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE) || !(event.getSource().getEntity() instanceof LivingEntity attacker) || PhantomDuels.pair(ward, attacker)) { return; }
        maintain();
        var candidates = level.getEntitiesOfClass(EclipsePhantom.class, ward.getBoundingBox().inflate(12), source -> source.enabled() && source.recipient(ward) && source.validTarget(attacker)
                && source.distanceToSqr(ward) <= 12 * 12 && source.line(source.getEyePosition(), ward.getEyePosition())).stream().sorted(Comparator.comparingDouble(source -> source.distanceToSqr(ward))).limit(8).toList();
        if (mitigation(ward) < .25F) { for (EclipsePhantom source : candidates) { if (source.hybridGuard(level, ward, attacker, incoming)) { break; } } }
        Shield shield = SHIELDS.get(ward);
        if (shield == null || !live(ward, shield)) { return; }
        EclipsePhantom source = shield.source().get(); if (!source.validTarget(attacker) || !source.line(source.getEyePosition(), ward.getEyePosition())) { return; }
        float floor = Math.min(incoming, event.getOriginalDamage() * .4F);
        event.setNewDamage(Math.max(floor, incoming * (1 - shield.reduction())));
        EclipseVfx.emit(source, EclipseVfx.IMPACT, null, ward.getBoundingBox().getCenter(), Vec3.ZERO, 12, 1.3F);
    }
    public static void release(EclipsePhantom source) { SHIELDS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; } SHIELDS.remove(event.getEntity()); HEALS.remove(event.getEntity());
        if (event.getEntity() instanceof EclipsePhantom source) { source.endEffects(); }
    }
}
