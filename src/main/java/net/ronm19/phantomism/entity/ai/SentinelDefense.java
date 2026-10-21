package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SentinelPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class SentinelDefense {
    private record Claim(WeakReference<SentinelPhantom> source, long until) {}
    private static final Map<Entity, Claim> CLAIMS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REDIRECTING = ThreadLocal.withInitial(() -> false);
    private SentinelDefense() {}
    public static boolean claim(SentinelPhantom source, Entity attack, LivingEntity victim) {
        CLAIMS.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved()
                || entry.getValue().source.get() == null || !entry.getValue().source.get().enabled()
                || entry.getKey().level() != entry.getValue().source.get().level() || entry.getKey().level().getGameTime() >= entry.getValue().until);
        Claim existing = CLAIMS.get(attack);
        if (existing != null) { return existing.source.get() == source; }
        if (CLAIMS.size() >= 128) { return false; }
        SentinelPhantom best = source.level().getEntitiesOfClass(SentinelPhantom.class, victim.getBoundingBox().inflate(16),
                candidate -> candidate.canDefendAttack(attack, victim) && candidate.line(candidate.getEyePosition(), victim.getEyePosition()))
                .stream().min(Comparator.comparingDouble((SentinelPhantom candidate) -> candidate.distanceToSqr(victim) + (candidate.getMaxHealth() - candidate.getHealth()) * .2D)
                        .thenComparingInt(Entity::getId)).orElse(null);
        if (best != source) { return false; }
        CLAIMS.put(attack, new Claim(new WeakReference<>(source), source.level().getGameTime() + 8)); return true;
    }
    public static boolean breakable(Projectile projectile) {
        return projectile instanceof AbstractArrow && !(projectile instanceof ThrownTrident) || projectile instanceof Snowball;
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (REDIRECTING.get() || !(victim.level() instanceof ServerLevel level) || event.getNewDamage() <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || PhantomDuels.pair(victim, attacker)) { return; }
        var sources = level.getEntitiesOfClass(SentinelPhantom.class, victim.getBoundingBox().inflate(24), source -> source.enabled() && source.covers(victim) && source.acceptsAttack(attacker, victim));
        for (SentinelPhantom source : sources.stream().sorted(Comparator.comparingDouble((SentinelPhantom candidate) -> -candidate.mitigation(victim) * 1000 + candidate.distanceToSqr(victim))).limit(8).toList()) {
            if (!source.line(source.getEyePosition(), victim.getEyePosition())) { continue; }
            source.notice(attacker, victim);
            if (source.protectee() == victim && (event.getNewDamage() >= 4 || victim.getHealth() - event.getNewDamage() <= victim.getMaxHealth() * .3F)) { source.guard(level); }
            float damage = event.getNewDamage(); float blocked = damage * source.mitigation(victim);
            if (source != victim && source.protectee() == victim && source.distanceToSqr(victim) <= 9 && source.canIntercept(victim)
                    && claim(source, event.getSource().getDirectEntity() == null ? attacker : event.getSource().getDirectEntity(), victim)) {
                float redirect = Math.min(8, damage * .3F);
                REDIRECTING.set(true);
                try { if (source.hurtServer(level, event.getSource(), redirect)) { blocked = Math.min(damage * .6F, blocked + redirect); source.commitIntercept(victim); } }
                finally { REDIRECTING.set(false); }
            }
            if (blocked > 0) { event.setNewDamage(Math.max(damage * .4F, damage - blocked)); source.shieldImpact(victim); return; }
        }
    }
    public static void release(SentinelPhantom source) { CLAIMS.entrySet().removeIf(entry -> entry.getValue().source.get() == source); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        CLAIMS.remove(event.getEntity());
        if (event.getEntity() instanceof SentinelPhantom source) { source.endDefense(); }
        if (event.getEntity() instanceof LivingEntity living) { PhantomDuels.end(living); }
    }
}
