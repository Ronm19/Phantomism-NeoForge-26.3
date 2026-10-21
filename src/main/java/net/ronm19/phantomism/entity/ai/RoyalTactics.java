package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.PhantomKing;
import net.ronm19.phantomism.vfx.RoyalVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class RoyalTactics {
    private record Directive(WeakReference<PhantomKing> source, UUID session, WeakReference<LivingEntity> target, WeakReference<LivingEntity> previous, long until, int slot) {}
    private record Disruption(WeakReference<PhantomKing> source, UUID session, long until, boolean resistant) {}
    private record Claim(WeakReference<PhantomKing> source, long until) {}
    private static final Map<AbstractPhantom, Directive> DIRECTIVES = new WeakHashMap<>();
    private static final Set<AbstractPhantom> OWNER_CONTROLLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<AbstractPhantom, Long> NEXT_CREDIT = new WeakHashMap<>();
    private static final Map<LivingEntity, Disruption> DISRUPTIONS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> RECOVERY = new WeakHashMap<>();
    private static final Map<LivingEntity, Claim> CLAIMS = new WeakHashMap<>();
    private RoyalTactics() {}
    public static void ownerCommand(AbstractPhantom ally, boolean following) {
        DIRECTIVES.remove(ally);
        if (following) { OWNER_CONTROLLED.remove(ally); } else { OWNER_CONTROLLED.add(ally); }
    }
    private static boolean eligible(PhantomKing source, AbstractPhantom ally, LivingEntity target) {
        return source.enabled() && source.isTame() && source.crew(ally) && source.validTarget(target) && !(target instanceof OwnableEntity)
                && !(ally instanceof PhantomKing) && !ally.isBaby() && !ally.isVehicle() && !ally.isPassenger() && !OWNER_CONTROLLED.contains(ally)
                && ally.getOrder() == AbstractPhantom.Order.FOLLOW && ally.getTask() == PhantomAssignments.Task.NONE && !ally.isTargetSuppressed(target)
                && !PhantomDuels.excludesObserver(ally, target) && source.distanceToSqr(ally) <= 40 * 40 && ally.getAssignments().permitsDefensivePursuit(target);
    }
    private static boolean live(AbstractPhantom ally, Directive lease) {
        PhantomKing source = lease.source().get(); LivingEntity target = lease.target().get();
        return source != null && target != null && source.session().equals(lease.session()) && ally.level() == source.level()
                && ally.isAlive() && !ally.isRemoved() && ally.level().getGameTime() < lease.until() && eligible(source, ally, target) && ally.getTarget() == target;
    }
    public static int coordinate(PhantomKing source, LivingEntity target, int duration, boolean timing) {
        maintain(); if (!source.enabled() || !source.isTame() || !source.validTarget(target) || target instanceof OwnableEntity) { return 0; }
        int assigned = 0;
        for (AbstractPhantom ally : source.allies(source.dominionTicks() > 0 ? 40 : 24)) {
            if (!eligible(source, ally, target)) { continue; }
            Directive old = DIRECTIVES.get(ally);
            if (old != null && old.source().get() != source && live(ally, old)) { continue; }
            LivingEntity previous = old == null ? ally.getTarget() : old.previous().get();
            if (old == null && ally.getTarget() != null) { continue; }
            if (DIRECTIVES.size() >= 128 && old == null) { continue; }
            ally.setTarget(target);
            if (ally.getTarget() != target) { continue; }
            DIRECTIVES.put(ally, new Directive(new WeakReference<>(source), source.session(), new WeakReference<>(target), new WeakReference<>(previous), source.level().getGameTime() + Math.clamp(duration, 1, 120), assigned++));
            if (timing) { credit(ally); }
            if (old == null) { RoyalVfx.emit(source, RoyalVfx.DIRECTIVE, ally, ally.position(), Vec3.ZERO, Math.clamp(duration, 1, 120), 2); }
        }
        return assigned;
    }
    public static Vec3 orbit(AbstractPhantom ally, LivingEntity target, Vec3 fallback, double radius) {
        Directive lease = DIRECTIVES.get(ally);
        if (lease == null || lease.target().get() != target || !live(ally, lease)) { return fallback; }
        double angle = lease.slot() * Math.PI * 2 / 12 + Math.floorMod(ally.getUUID().hashCode(), 13) * .012D;
        double reach = Math.max(radius, target.getBbWidth() * .5D + ally.getBbWidth() + 2);
        Vec3 point = new Vec3(target.getX() + Math.cos(angle) * reach, fallback.y + (lease.slot() % 3) * .6D, target.getZ() + Math.sin(angle) * reach);
        Vec3 offset = point.subtract(ally.position()); if (offset.lengthSqr() > 16) { offset = offset.normalize().scale(4); }
        var box = ally.getBoundingBox().expandTowards(offset);
        return EndTeleportSafety.loaded((ServerLevel) ally.level(), box, false) && ally.level().noBlockCollision(ally, box) && !ally.level().containsAnyLiquid(box) ? point : fallback;
    }
    public static int credit(AbstractPhantom ally) {
        if (!(ally instanceof CooldownDiscipline.Participant participant) || NEXT_CREDIT.getOrDefault(ally, Long.MIN_VALUE) > ally.level().getGameTime()) { return 0; }
        int total = 0;
        for (var slot : participant.temporalCooldowns().stream().limit(24).toList()) {
            if (!CooldownDiscipline.eligible(slot)) { continue; }
            int remaining = slot.remaining().getAsInt(), reduction = Math.max(0, Math.min(8, Math.min(slot.duration() / 10, remaining - slot.floor())));
            if (reduction > 0) { slot.update().accept(remaining - reduction); total += reduction; }
        }
        if (total > 0) { NEXT_CREDIT.put(ally, ally.level().getGameTime() + 400); } return total;
    }
    public static void rally(PhantomKing source, int duration) {
        if (!source.enabled()) { return; }
        for (AbstractPhantom ally : source.allies(source.dominionTicks() > 0 ? 40 : 24)) {
            if (ally.getOrder() != AbstractPhantom.Order.FOLLOW || ally.getTask() != PhantomAssignments.Task.NONE || OWNER_CONTROLLED.contains(ally) || ally.isVehicle()) { continue; }
            if (ally.getTarget() != null && source.validTarget(ally.getTarget())) { credit(ally); RoyalVfx.emit(source, RoyalVfx.RALLY, ally, ally.position(), Vec3.ZERO, duration, 2); }
        }
    }
    public static boolean claim(PhantomKing source, LivingEntity victim) {
        Claim old = CLAIMS.get(victim); PhantomKing previous = old == null ? null : old.source().get();
        if (previous != null && previous != source && previous.enabled() && source.level().getGameTime() < old.until()) { return false; }
        if (CLAIMS.size() >= 128 && old == null) { return false; }
        CLAIMS.put(victim, new Claim(new WeakReference<>(source), source.level().getGameTime() + 20)); return true;
    }
    public static boolean disrupt(PhantomKing source, LivingEntity target, int duration) {
        maintain(); if (!source.enabled() || !source.validTarget(target) || RECOVERY.containsKey(target) || DISRUPTIONS.size() >= 256 || RECOVERY.size() >= 256) { return false; }
        boolean resistant = !(target instanceof Mob mob) || GravityControl.movementImmune(mob); long now = source.level().getGameTime(); int ticks = Math.clamp(duration, 1, 30);
        DISRUPTIONS.put(target, new Disruption(new WeakReference<>(source), source.session(), now + ticks, resistant)); RECOVERY.put(target, now + ticks + 80);
        RoyalVfx.emit(source, RoyalVfx.HESITATE, target, target.position(), Vec3.ZERO, ticks, 1); return true;
    }
    public static float offense(LivingEntity target) { Disruption lease = DISRUPTIONS.get(target); return lease != null && disruptionLive(target, lease) ? lease.resistant() ? .95F : .85F : 1; }
    private static boolean disruptionLive(LivingEntity target, Disruption lease) { PhantomKing source = lease.source().get(); return source != null && source.enabled() && source.session().equals(lease.session()) && source.validTarget(target) && source.level() == target.level() && target.level().getGameTime() < lease.until(); }
    public static void push(PhantomKing source, LivingEntity target, double force) {
        if (!(source.level() instanceof ServerLevel level) || !source.validTarget(target) || !(target instanceof Mob mob) || GravityControl.movementImmune(mob) || target.isPassenger() || target.isVehicle()) { return; }
        Vec3 away = target.position().subtract(source.position()).multiply(1, 0, 1).normalize();
        var box = target.getBoundingBox().expandTowards(away.scale(force)); BlockPosCheck floor = new BlockPosCheck(level, target.position().add(away.scale(force)));
        if (away.lengthSqr() < .01D || !EndTeleportSafety.loaded(level, box, false) || !level.noBlockCollision(target, box) || level.containsAnyLiquid(box) || !floor.safe()) { return; }
        target.knockback(force, -away.x, -away.z, source.damageSources().mobAttack(source), 0); target.needsSync = true;
    }
    private record BlockPosCheck(ServerLevel level, Vec3 position) {
        boolean safe() { var below = net.minecraft.core.BlockPos.containing(position).below(); return level.hasChunkAt(below) && !GravityControl.dangerous(level, below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty(); }
    }
    private static void restore(AbstractPhantom ally, Directive lease) {
        if (ally.isRemoved() || !ally.isAlive() || OWNER_CONTROLLED.contains(ally) || ally.getTarget() != lease.target().get()) { return; }
        LivingEntity previous = lease.previous().get(); ally.setTarget(previous != null && previous.isAlive() && previous.level() == ally.level() && !ally.isTargetSuppressed(previous) ? previous : null);
    }
    public static void releaseDirectives(PhantomKing source) {
        var iterator = DIRECTIVES.entrySet().iterator(); while (iterator.hasNext()) { var entry = iterator.next(); if (entry.getValue().source().get() == source) { restore(entry.getKey(), entry.getValue()); iterator.remove(); } }
    }
    public static void release(PhantomKing source) { releaseDirectives(source); DISRUPTIONS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); CLAIMS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); }
    public static void maintain() {
        var iterator = DIRECTIVES.entrySet().iterator(); while (iterator.hasNext()) { var entry = iterator.next(); if (!live(entry.getKey(), entry.getValue())) { restore(entry.getKey(), entry.getValue()); iterator.remove(); } }
        DISRUPTIONS.entrySet().removeIf(entry -> !disruptionLive(entry.getKey(), entry.getValue()));
        RECOVERY.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue());
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().source().get() == null || !entry.getValue().source().get().enabled() || entry.getKey().level().getGameTime() >= entry.getValue().until());
        NEXT_CREDIT.entrySet().removeIf(entry -> entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue());
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) { if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof AbstractPhantom ally && DIRECTIVES.containsKey(ally) && !live(ally, DIRECTIVES.get(ally))) { Directive lease = DIRECTIVES.remove(ally); restore(ally, lease); } }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE) || !(event.getSource().getEntity() instanceof LivingEntity attacker)) { return; }
        event.setNewDamage(event.getNewDamage() * offense(attacker));
        if (attacker instanceof AbstractPhantom phantom) {
            float offense = 0, judgment = 0;
            for (PhantomKing source : level.getEntitiesOfClass(PhantomKing.class, attacker.getBoundingBox().inflate(40), source -> source.enabled() && source.crew(phantom) && source.validTarget(victim))) {
                if (source != phantom && source.distanceToSqr(phantom) <= 24 * 24) { offense = Math.max(offense, .08F); }
                if (source.judges(victim)) { judgment = Math.max(judgment, victim instanceof Mob mob && GravityControl.movementImmune(mob) ? .05F : .1F); }
            }
            event.setNewDamage(event.getNewDamage() * (1 + offense + judgment));
        }
        float protection = 0;
        for (PhantomKing source : level.getEntitiesOfClass(PhantomKing.class, victim.getBoundingBox().inflate(40), source -> source.enabled() && source.crew(victim) && source.validTarget(attacker))) {
            if (event.getNewDamage() >= 4 && !PhantomDuels.pair(victim, attacker)) { source.guard(level, victim, attacker, event.getNewDamage()); }
            protection = Math.max(protection, source.protection(victim, attacker));
        }
        if (protection > 0) { event.setNewDamage(event.getNewDamage() * (1 - protection)); }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof PhantomKing source) { source.endCombat(); }
        if (event.getEntity() instanceof AbstractPhantom ally) { DIRECTIVES.remove(ally); OWNER_CONTROLLED.remove(ally); NEXT_CREDIT.remove(ally); }
        if (event.getEntity() instanceof LivingEntity target) { DISRUPTIONS.remove(target); RECOVERY.remove(target); CLAIMS.remove(target); }
    }
}
