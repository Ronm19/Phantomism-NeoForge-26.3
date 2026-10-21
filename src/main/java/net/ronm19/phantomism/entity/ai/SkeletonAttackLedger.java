package net.ronm19.phantomism.entity.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkeletonAttackLedger {
    public static final int VOLLEY_LIMIT = 6;
    public static final int RAIN_LIMIT = 30;
    public static final int RAIN_TARGET_LIMIT = 6;
    private final Map<UUID, Attack> attacks = new HashMap<>();

    public UUID begin(boolean rain, int tick) {
        UUID identity = UUID.randomUUID();
        this.attacks.put(identity, new Attack(rain, tick + 200));
        return identity;
    }

    public boolean active(UUID attack) { return this.attacks.containsKey(attack); }
    public int issued(UUID attack) { Attack state = this.attacks.get(attack); return state == null ? 0 : state.issued; }
    public int allocated(UUID attack, UUID target) { Attack state = this.attacks.get(attack); return state == null ? 0 : state.allocated.getOrDefault(target, 0); }
    public float pending(UUID attack, UUID target) {
        Attack state = this.attacks.get(attack);
        return state == null ? 0 : (float) state.pending.values().stream().filter(reservation -> reservation.target.equals(target)).mapToDouble(Reservation::damage).sum();
    }

    public boolean reserve(UUID attack, UUID arrow, UUID target, float damage) {
        Attack state = this.attacks.get(attack);
        if (state == null || !Float.isFinite(damage) || damage <= 0 || state.issued >= (state.rain ? RAIN_LIMIT : VOLLEY_LIMIT)
                || state.allocated.getOrDefault(target, 0) >= (state.rain ? RAIN_TARGET_LIMIT : 1) || state.pending.containsKey(arrow)) { return false; }
        state.issued++; state.allocated.merge(target, 1, Integer::sum); state.pending.put(arrow, new Reservation(target, damage));
        return true;
    }

    public void failedSpawn(UUID attack, UUID arrow) {
        Attack state = this.attacks.get(attack);
        Reservation reservation = state == null ? null : state.pending.remove(arrow);
        if (reservation != null) { state.issued--; state.allocated.computeIfPresent(reservation.target, (target, count) -> count <= 1 ? null : count - 1); }
    }

    public void release(UUID attack, UUID arrow) { Attack state = this.attacks.get(attack); if (state != null) { state.pending.remove(arrow); } }
    public boolean mayHit(UUID attack, UUID target) {
        Attack state = this.attacks.get(attack);
        return state != null && state.hits.getOrDefault(target, 0) < (state.rain ? RAIN_TARGET_LIMIT : 1);
    }
    public boolean claimHit(UUID attack, UUID target) {
        if (!this.mayHit(attack, target)) { return false; }
        this.attacks.get(attack).hits.merge(target, 1, Integer::sum); return true;
    }
    public void tick(int tick) { this.attacks.values().removeIf(attack -> tick >= attack.expires); }
    public void clear() { this.attacks.clear(); }
    private record Reservation(UUID target, float damage) {}
    private static final class Attack {
        private final boolean rain;
        private final int expires;
        private int issued;
        private final Map<UUID, Integer> allocated = new HashMap<>();
        private final Map<UUID, Integer> hits = new HashMap<>();
        private final Map<UUID, Reservation> pending = new HashMap<>();
        private Attack(boolean rain, int expires) { this.rain = rain; this.expires = expires; }
    }
}
