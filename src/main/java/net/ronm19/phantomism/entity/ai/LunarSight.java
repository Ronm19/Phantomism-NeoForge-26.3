package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.phantomism.entity.LunarPhantom;

@EventBusSubscriber(modid = "phantomism")
public final class LunarSight {
    public static final double RADIUS = 30;
    private static final String SAVED = "phantomism:lunar_sight";
    private static final Map<ServerPlayer, Lease> LEASES = new WeakHashMap<>();

    private LunarSight() {}

    private static final class Lease {
        private MobEffectInstance aura;
        private MobEffectInstance underlying;
        private boolean changed;
        private CompoundTag saved;
    }

    private static Tag encode(ServerPlayer player, MobEffectInstance effect) {
        return MobEffectInstance.CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), effect).getOrThrow();
    }

    private static MobEffectInstance decode(ServerPlayer player, Tag tag) {
        return MobEffectInstance.CODEC.parse(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(null);
    }

    private static MobEffectInstance copy(ServerPlayer player, MobEffectInstance effect) {
        return effect == null ? null : decode(player, encode(player, effect));
    }

    private static CompoundTag elapsed(CompoundTag effect, int ticks) {
        CompoundTag result = effect.copy();
        result.getCompound("hidden_effect").ifPresent(hidden -> result.put("hidden_effect", elapsed(hidden, ticks)));
        int duration = result.getIntOr("duration", 0);
        if (duration != MobEffectInstance.INFINITE_DURATION) { result.putInt("duration", Math.max(0, duration - ticks)); }
        if (result.getIntOr("duration", 0) == 0 && result.getCompound("hidden_effect").isPresent()) {
            CompoundTag hidden = result.getCompoundOrEmpty("hidden_effect");
            Tag id = result.get("id");
            if (id != null) { hidden.put("id", id); }
            return hidden;
        }
        return result;
    }

    private static void save(ServerPlayer player, Lease lease) {
        lease.saved = new CompoundTag();
        if (lease.underlying != null) { lease.saved.put("effect", encode(player, lease.underlying)); }
        lease.saved.put("aura", encode(player, lease.aura));
        player.getPersistentData().put(SAVED, lease.saved);
    }

    private static void recover(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SAVED)) { return; }
        CompoundTag saved = data.getCompoundOrEmpty(SAVED);
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && encode(player, current).equals(saved.get("aura"))) {
            MobEffectInstance previous = decode(player, elapsed(saved.getCompoundOrEmpty("effect"), saved.getIntOr("elapsed", 0)));
            if (previous != null && !previous.endsWithin(0)) { player.forceAddEffect(previous, null); }
            else { player.removeEffect(MobEffects.NIGHT_VISION); }
        }
        data.remove(SAVED);
    }

    private static void apply(ServerPlayer player, Lease lease, LunarPhantom source) {
        MobEffectInstance aura = new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION,
                lease.underlying == null ? 0 : lease.underlying.getAmplifier(), true, false, true);
        player.forceAddEffect(aura, source);
        if (player.getEffect(MobEffects.NIGHT_VISION) == aura) {
            lease.aura = aura;
            lease.changed = false;
            save(player, lease);
            LEASES.put(player, lease);
        }
    }

    private static void release(ServerPlayer player) {
        Lease lease = LEASES.remove(player);
        if (lease == null) { recover(player); return; }
        if (player.getEffect(MobEffects.NIGHT_VISION) == lease.aura) {
            if (lease.underlying != null && !lease.underlying.endsWithin(0)) { player.forceAddEffect(lease.underlying, null); }
            else { player.removeEffect(MobEffects.NIGHT_VISION); }
        }
        player.getPersistentData().remove(SAVED);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        Lease lease = LEASES.get(player);
        if (lease == null) { recover(player); }
        LunarPhantom source = player.isAlive() && !player.isRemoved() ? player.level().getEntitiesOfClass(LunarPhantom.class,
                player.getBoundingBox().inflate(RADIUS), candidate -> candidate.isAlive() && !candidate.isRemoved()
                        && candidate.recipient(player) && candidate.distanceToSqr(player) <= RADIUS * RADIUS).stream().findFirst().orElse(null) : null;
        if (lease != null && lease.underlying != null) {
            if (!lease.underlying.tickServer(player.level(), player, () -> {})) { lease.underlying = null; }
            lease.saved.putInt("elapsed", lease.saved.getIntOr("elapsed", 0) + 1);
        }
        if (source == null) { release(player); return; }
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (lease == null) {
            lease = new Lease();
            lease.underlying = copy(player, current);
            apply(player, lease, source);
        } else if (current != lease.aura) {
            lease.underlying = copy(player, current);
            apply(player, lease, source);
        } else if (lease.changed) { apply(player, lease, source); }
    }

    @SubscribeEvent public static void added(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getEffectInstance().is(MobEffects.NIGHT_VISION)) { return; }
        Lease lease = LEASES.get(player);
        if (lease == null || event.getOldEffectInstance() != lease.aura) { return; }
        MobEffectInstance incoming = copy(player, event.getEffectInstance());
        if (lease.underlying == null) { lease.underlying = incoming; } else { lease.underlying.update(incoming); }
        lease.changed = true;
        save(player, lease);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { release(player); }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { release(player); }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { recover(player); }
    }

    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer replacement && original.getPersistentData().contains(SAVED)) {
                replacement.getPersistentData().put(SAVED, original.getPersistentData().getCompoundOrEmpty(SAVED).copy());
                recover(replacement);
            }
            release(original);
        }
        event.getEntity().getPersistentData().remove(SAVED);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (ServerPlayer player : LEASES.keySet().toArray(ServerPlayer[]::new)) { release(player); }
    }
}
