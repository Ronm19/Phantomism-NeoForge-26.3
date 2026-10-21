package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AngelPhantom;
import net.ronm19.phantomism.vfx.AngelVfx;
import net.minecraft.world.phys.Vec3;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class AngelSupport {
    public static final TagKey<MobEffect> CLEANSEABLE = TagKey.create(Registries.MOB_EFFECT, Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "angel_cleanseable"));
    private record Claim(WeakReference<AngelPhantom> source, long until) {}
    private static final Map<LivingEntity, Claim> HEALING = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> RECENT_HEALING = new WeakHashMap<>();
    private static final Map<Entity, Claim> INTERCEPTS = new WeakHashMap<>();
    private static final Map<LivingEntity, Ward> WARDS = new WeakHashMap<>();
    private static final class Ward {
        private final WeakReference<AngelPhantom> source;
        private final long until;
        private final double range;
        private final boolean cleanse;
        private final float mitigation;
        private final Vec3 center;
        private float remaining;
        private Ward(AngelPhantom source, float mitigation, float capacity, int ticks, double range, boolean cleanse) { this.source = new WeakReference<>(source); this.mitigation = mitigation; remaining = capacity; until = source.level().getGameTime() + ticks; this.range = range; this.cleanse = cleanse; center = ticks == 240 && range == 24 && source.eyeTicks() > 0 ? source.eyeCenter() : null; }
    }
    private AngelSupport() {}
    public static boolean cleanseable(MobEffectInstance effect) { return effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && effect.getEffect().is(CLEANSEABLE); }
    private static boolean live(Entity target, Claim claim) { AngelPhantom source = claim.source().get(); return target.isAlive() && !target.isRemoved() && source != null && source.enabled() && source.level() == target.level() && target.level().getGameTime() < claim.until(); }
    private static void prune() { HEALING.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue())); INTERCEPTS.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue())); RECENT_HEALING.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue()); WARDS.entrySet().removeIf(entry -> !valid(entry.getKey(), entry.getValue())); }
    public static boolean claimHeal(AngelPhantom source, LivingEntity target) {
        prune(); if (!source.enabled() || !source.ally(target) || HEALING.size() >= 128 || HEALING.containsKey(target)) { return false; }
        if (RECENT_HEALING.getOrDefault(target, Long.MIN_VALUE) > target.level().getGameTime() && target.getHealth() > target.getMaxHealth() * .25F) { return false; }
        HEALING.put(target, new Claim(new WeakReference<>(source), target.level().getGameTime() + 2)); return true;
    }
    public static void releaseHeal(AngelPhantom source, LivingEntity target) { Claim claim = HEALING.get(target); if (claim != null && claim.source().get() == source) { HEALING.remove(target); } }
    @SubscribeEvent public static void healed(LivingHealEvent event) { if (!event.getEntity().level().isClientSide() && event.getAmount() > 0) { if (RECENT_HEALING.size() < 256 || RECENT_HEALING.containsKey(event.getEntity())) { RECENT_HEALING.put(event.getEntity(), event.getEntity().level().getGameTime() + 10); } } }
    public static boolean claimIntercept(AngelPhantom source, LivingEntity target, Entity attack) {
        prune(); if (attack == null || INTERCEPTS.size() >= 128 || INTERCEPTS.containsKey(attack)) { return false; }
        AngelPhantom best = source.level().getEntitiesOfClass(AngelPhantom.class, target.getBoundingBox().inflate(8), candidate -> candidate.canIntercept(target)).stream().min(Comparator.comparingDouble((AngelPhantom candidate) -> candidate.distanceToSqr(target)).thenComparingInt(Entity::getId)).orElse(null);
        if (best != source) { return false; } INTERCEPTS.put(attack, new Claim(new WeakReference<>(source), target.level().getGameTime() + 8)); return true;
    }
    private static boolean valid(LivingEntity target, Ward ward) { AngelPhantom source = ward.source.get(); return source != null && source.enabled() && source.ally(target) && source.level() == target.level() && target.level().getGameTime() < ward.until && ward.remaining > 0 && (ward.center == null ? source.distanceToSqr(target) : target.distanceToSqr(ward.center)) <= ward.range * ward.range; }
    public static boolean ward(AngelPhantom source, LivingEntity target, float mitigation, float capacity, int ticks, double range, boolean cleanse) {
        prune(); if (!source.recipient(target, range) || mitigation <= 0 || capacity <= 0 || WARDS.size() >= 256 && !WARDS.containsKey(target)) { return false; }
        Ward existing = WARDS.get(target);
        if (existing != null && existing.mitigation >= mitigation) { return false; }
        WARDS.put(target, new Ward(source, Math.clamp(mitigation, 0, .35F), Math.clamp(capacity, 0, 16), Math.clamp(ticks, 1, 240), range, cleanse)); return true;
    }
    @SubscribeEvent public static void effect(MobEffectEvent.Applicable event) {
        LivingEntity target = event.getEntity(); if (target.level().isClientSide() || !cleanseable(event.getEffectInstance())) { return; }
        Ward ward = WARDS.get(target);
        if (target instanceof AngelPhantom || target.getVehicle() instanceof AngelPhantom source && source.sanctuaryRider(target) || ward != null && ward.cleanse && valid(target, ward)) { event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY); }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || PhantomDuels.pair(target, attacker) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        prune();
        for (AngelPhantom source : level.getEntitiesOfClass(AngelPhantom.class, target.getBoundingBox().inflate(16), candidate -> candidate.enabled() && candidate.recipient(target, 16) && !candidate.ally(attacker)).stream().sorted(Comparator.comparingDouble((AngelPhantom candidate) -> candidate.distanceToSqr(target))).limit(8).toList()) {
            source.noticeIncoming(attacker, target);
            if (event.getNewDamage() >= 4 || target.getHealth() - event.getNewDamage() <= target.getMaxHealth() * .35F) {
                source.intercept(level, target, attacker, event.getSource().getDirectEntity() == null ? attacker : event.getSource().getDirectEntity()); source.shield(level, target, attacker); source.blessing(level, target, attacker);
            }
        }
        Ward ward = WARDS.get(target);
        if (ward == null || !valid(target, ward)) { return; }
        AngelPhantom source = ward.source.get(); if (source == null || !source.threatens(attacker, target) || !source.line(source.getEyePosition(), target.getEyePosition())) { return; }
        float blocked = Math.min(ward.remaining, event.getNewDamage() * ward.mitigation); ward.remaining -= blocked;
        event.setNewDamage(Math.max(event.getNewDamage() * .65F, event.getNewDamage() - blocked));
        AngelVfx.emit(source, AngelVfx.IMPACT, target, target.getBoundingBox().getCenter(), Vec3.ZERO, 12, .8F);
    }
    public static void release(AngelPhantom source) { HEALING.entrySet().removeIf(entry -> entry.getValue().source().get() == source); INTERCEPTS.entrySet().removeIf(entry -> entry.getValue().source().get() == source); WARDS.entrySet().removeIf(entry -> entry.getValue().source.get() == source); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) { if (event.getLevel().isClientSide()) { return; } if (event.getEntity() instanceof AngelPhantom source) { source.endSupport(); } INTERCEPTS.remove(event.getEntity()); HEALING.remove(event.getEntity()); WARDS.remove(event.getEntity()); RECENT_HEALING.remove(event.getEntity()); }
}
