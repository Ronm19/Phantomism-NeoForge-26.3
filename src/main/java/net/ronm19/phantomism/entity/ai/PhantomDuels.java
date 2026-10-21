package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomDuels {
    private record Duel(WeakReference<LivingEntity> opponent, long expires) {}
    private static final Map<LivingEntity, Duel> DUELS = new WeakHashMap<>();
    private PhantomDuels() {}
    public static boolean begin(AbstractPhantom attacker, LivingEntity target) {
        if (!(target instanceof OwnableEntity owned) || owned.getOwnerReference() == null || owned.getOwnerReference().equals(attacker.getOwnerReference())) { return true; }
        if (!attacker.isTame() || !attacker.isAlive() || !target.isAlive() || attacker.level() != target.level() || attacker.distanceToSqr(target) > 56 * 56) { return false; }
        DUELS.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue()));
        end(attacker); end(target);
        if (DUELS.size() + 2 > 128) { return false; }
        long until = attacker.level().getGameTime() + 600;
        DUELS.put(attacker, new Duel(new WeakReference<>(target), until));
        DUELS.put(target, new Duel(new WeakReference<>(attacker), until));
        return true;
    }
    private static boolean live(LivingEntity entity, Duel duel) {
        LivingEntity other = duel.opponent.get();
        return entity.isAlive() && !entity.isRemoved() && other != null && other.isAlive() && !other.isRemoved()
                && entity.level() == other.level() && entity.level().getGameTime() < duel.expires;
    }
    public static boolean pair(LivingEntity first, LivingEntity second) {
        Duel duel = DUELS.get(first);
        if (duel == null) { return false; }
        if (!live(first, duel)) { DUELS.remove(first); return false; }
        return duel.opponent.get() == second;
    }
    public static boolean excludesObserver(LivingEntity observer, LivingEntity target) {
        Duel duel = DUELS.get(target);
        if (duel == null) { return false; }
        if (!live(target, duel)) { DUELS.remove(target); return false; }
        LivingEntity owner = observer instanceof AbstractPhantom phantom ? phantom.getOwner() : null;
        if (target.getLastHurtMob() == owner && owner != null && target.tickCount - target.getLastHurtMobTimestamp() < 100) { return false; }
        return duel.opponent.get() != observer;
    }
    public static void tick(AbstractPhantom source) {
        Duel duel = DUELS.get(source);
        if (duel == null) { return; }
        if (!live(source, duel)) { end(source); return; }
        LivingEntity target = duel.opponent.get();
        if (source.getTarget() == target && source.distanceToSqr(target) <= 56 * 56) {
            long until = source.level().getGameTime() + 100;
            DUELS.put(source, new Duel(new WeakReference<>(target), until));
            DUELS.put(target, new Duel(new WeakReference<>(source), until));
        }
    }
    public static void end(LivingEntity entity) {
        Duel duel = DUELS.remove(entity);
        LivingEntity other = duel == null ? null : duel.opponent.get();
        Duel counterpart = other == null ? null : DUELS.get(other);
        if (counterpart != null && counterpart.opponent.get() == entity) { DUELS.remove(other); }
    }
}
