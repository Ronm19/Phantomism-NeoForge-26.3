package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.DarkPhantom;
import net.ronm19.phantomism.vfx.DarkVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class DarkControl {
    private record Lease(WeakReference<DarkPhantom> source, UUID session, long until, boolean ultimate, boolean resistant) {}
    private static final Map<LivingEntity, Lease> ACTIVE = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> RECOVERY = new WeakHashMap<>();
    private DarkControl() {}
    private static boolean live(LivingEntity target, Lease lease) {
        DarkPhantom source = lease.source().get();
        return source != null && source.enabled() && source.session().equals(lease.session()) && source.level() == target.level()
                && source.validTarget(target) && source.distanceToSqr(target) <= 56 * 56 && target.level().getGameTime() < lease.until()
                && (!lease.ultimate() || source.hourTicks() > 0);
    }
    public static void maintain() {
        ACTIVE.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue()));
        RECOVERY.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue());
    }
    public static boolean active(LivingEntity target) { Lease lease = ACTIVE.get(target); return lease != null && live(target, lease); }
    public static boolean apply(DarkPhantom source, LivingEntity target, int duration, boolean ultimate) {
        maintain();
        if (!(source.level() instanceof ServerLevel) || !source.enabled() || !source.validTarget(target) || RECOVERY.containsKey(target)
                || ACTIVE.size() >= 256 || RECOVERY.size() >= 256 || !source.line(source.getEyePosition(), target.getEyePosition())) { return false; }
        boolean resistant = !(target instanceof Mob mob) || GravityControl.movementImmune(mob);
        long now = source.level().getGameTime(); int ticks = Math.clamp(duration, 1, 40);
        ACTIVE.put(target, new Lease(new WeakReference<>(source), source.session(), now + ticks, ultimate, resistant));
        RECOVERY.put(target, now + ticks + 80); DarkVfx.emit(source, DarkVfx.DISRUPT, target, target.position(), Vec3.ZERO, ticks, resistant ? .65F : 1); return true;
    }
    public static float offense(LivingEntity attacker) { return active(attacker) ? ACTIVE.get(attacker).resistant() ? .95F : .8F : 1; }
    public static void release(DarkPhantom source, boolean ultimateOnly) { ACTIVE.entrySet().removeIf(entry -> entry.getValue().source().get() == source && (!ultimateOnly || entry.getValue().ultimate())); }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof LivingEntity target && ACTIVE.containsKey(target) && !active(target)) { ACTIVE.remove(target); }
    }
    @SubscribeEvent public static void projectile(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile) || !(projectile.getOwner() instanceof LivingEntity attacker)
                || !active(attacker) || ACTIVE.get(attacker).resistant() || !(projectile instanceof AbstractArrow || projectile instanceof SmallFireball)) { return; }
        Vec3 motion = projectile.getDeltaMovement();
        if (Double.isFinite(motion.lengthSqr()) && motion.lengthSqr() > .01D) {
            double angle = (projectile.getId() % 2 == 0 ? 1 : -1) * .16D;
            projectile.setDeltaMovement(new Vec3(motion.x * Math.cos(angle) - motion.z * Math.sin(angle), motion.y + motion.length() * .04D, motion.x * Math.sin(angle) + motion.z * Math.cos(angle))); projectile.needsSync = true;
        }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)) { return; }
        event.setNewDamage(event.getNewDamage() * offense(attacker));
        float bubble = 0;
        for (DarkPhantom source : level.getEntitiesOfClass(DarkPhantom.class, victim.getBoundingBox().inflate(8), candidate -> candidate.enabled() && candidate.ally(victim) && candidate.validTarget(attacker))) {
            if (!PhantomDuels.pair(victim, attacker) && event.getNewDamage() >= 4) { source.bubble(level, victim, attacker); }
            bubble = Math.max(bubble, source.bubbleMitigation(victim, attacker));
        }
        float reduced = event.getNewDamage() * bubble;
        if (victim instanceof DarkPhantom source && source.validTarget(attacker)) {
            source.shield(level, attacker, event.getNewDamage()); reduced = Math.max(reduced, source.absorb(event.getNewDamage()));
        }
        event.setNewDamage(Math.max(0, event.getNewDamage() - reduced));
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof DarkPhantom source) { source.endCombat(); }
        if (event.getEntity() instanceof LivingEntity target) { ACTIVE.remove(target); RECOVERY.remove(target); }
    }
}
