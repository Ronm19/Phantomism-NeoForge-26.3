package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.EchoPhantom;
import net.ronm19.phantomism.entity.SkeletonArrow;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class EchoCombatEvents {
    public enum Kind { ORIGINAL, ORDINARY, PULSE, STRIKE, AID, CHAIN, ULTIMATE_DAMAGE, SUPPORT }
    private static final Map<LivingDamageEvent.Post, UUID> EVENTS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Reservation>> RESERVATIONS = new WeakHashMap<>();
    private static final ThreadLocal<Integer> FOLLOWUP_DEPTH = ThreadLocal.withInitial(() -> 0);
    private EchoCombatEvents() {}

    public static final class EchoDamage extends DamageSource {
        public final EchoPhantom echo;
        public final Kind kind;
        public final UUID origin;
        public EchoDamage(DamageSource original, EchoPhantom echo, Kind kind, UUID origin) {
            super(original.typeHolder(), original.getDirectEntity(), original.getEntity(), original.getSourcePosition());
            this.echo = echo; this.kind = kind; this.origin = origin;
        }
    }
    public static Kind classify(DamageSource damage) {
        if (damage instanceof EchoDamage echo) { return echo.kind; }
        if (damage.getDirectEntity() instanceof SkeletonArrow arrow && arrow.isRainArrow()) { return Kind.ULTIMATE_DAMAGE; }
        if (damage.getEntity() instanceof EchoPhantom) { return Kind.ORDINARY; }
        if (damage.getEntity() instanceof AbstractPhantom phantom && phantom.isUltimateAnimationActive()) { return Kind.ULTIMATE_DAMAGE; }
        return Kind.ORIGINAL;
    }
    public static boolean generated(Kind kind) { return kind == Kind.STRIKE || kind == Kind.AID || kind == Kind.CHAIN; }
    public static boolean hurt(EchoPhantom echo, LivingEntity target, DamageSource damage, float amount, Kind kind, UUID origin) {
        boolean followup = generated(kind);
        if (followup) { FOLLOWUP_DEPTH.set(FOLLOWUP_DEPTH.get() + 1); }
        try { return target.hurtServer((ServerLevel) echo.level(), new EchoDamage(damage, echo, kind, origin), amount); }
        finally { if (followup) { int remaining = FOLLOWUP_DEPTH.get() - 1; if (remaining == 0) { FOLLOWUP_DEPTH.remove(); } else { FOLLOWUP_DEPTH.set(remaining); } } }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getInflictedDamage() <= 0 || FOLLOWUP_DEPTH.get() > 0
                || event.getSource().is(DamageTypes.THORNS) || EVENTS.containsKey(event)) { return; }
        Kind kind = classify(event.getSource());
        if (generated(kind) || kind == Kind.SUPPORT) { return; }
        UUID identity = UUID.randomUUID(); EVENTS.put(event, identity);
        LivingEntity target = event.getEntity();
        LivingEntity actor = event.getSource() instanceof EchoDamage damage ? damage.echo
                : event.getSource().getEntity() instanceof LivingEntity attacker ? attacker : null;
        if (actor == null || actor.level() != level || !actor.isAlive()) { return; }
        if (actor instanceof EchoPhantom echo) {
            if (echo.canObserveTarget(target)) { echo.earnCharge(level); }
            return;
        }
        if (!(actor instanceof net.minecraft.world.entity.player.Player) && !(actor instanceof AbstractPhantom)) { return; }
        boolean offered = false;
        for (EchoPhantom echo : level.getEntitiesOfClass(EchoPhantom.class, target.getBoundingBox().inflate(48),
                candidate -> candidate.acceptsAlly(actor) && candidate.canObserveTarget(target) && candidate.combatEnabled()
                        && candidate.distanceToSqr(target) <= 48 * 48 && candidate.distanceToSqr(actor) <= 48 * 48)
                .stream().sorted(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(target))).limit(8).toList()) {
            echo.earnCharge(level);
            if (!offered) { offered = echo.offerOpportunity(level, target, actor, identity, kind); }
        }
    }

    private static Map<UUID, Reservation> reservations(ServerLevel level) {
        Map<UUID, Reservation> reservations = RESERVATIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        reservations.values().removeIf(value -> value.expires <= level.getGameTime()); return reservations;
    }
    public static @Nullable UUID reserve(EchoPhantom source, LivingEntity target, float estimatedDamage) {
        ServerLevel level = (ServerLevel) source.level(); Map<UUID, Reservation> reservations = reservations(level);
        if (reservations.size() >= 128 || !Float.isFinite(estimatedDamage) || estimatedDamage <= 0
                || target.getHealth() + target.getAbsorptionAmount() - pending(level, target.getUUID()) < estimatedDamage) { return null; }
        UUID identity = UUID.randomUUID(); reservations.put(identity, new Reservation(source.getUUID(), target.getUUID(), estimatedDamage, level.getGameTime() + 80)); return identity;
    }
    public static float pending(ServerLevel level, UUID target) {
        return (float) reservations(level).values().stream().filter(value -> value.target.equals(target)).mapToDouble(Reservation::damage).sum();
    }
    public static boolean active(ServerLevel level, UUID reservation, EchoPhantom owner, LivingEntity target) {
        Reservation value = reservations(level).get(reservation);
        return value != null && value.owner.equals(owner.getUUID()) && value.target.equals(target.getUUID());
    }
    public static void release(ServerLevel level, @Nullable UUID reservation) { if (reservation != null) { reservations(level).remove(reservation); } }
    public static void release(EchoPhantom owner) { if (owner.level() instanceof ServerLevel level) { reservations(level).values().removeIf(value -> value.owner.equals(owner.getUUID())); } }
    private record Reservation(UUID owner, UUID target, float damage, long expires) {}
}
