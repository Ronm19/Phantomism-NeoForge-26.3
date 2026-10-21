package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.WitherPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class WitherCombatEffects {
    private static final Map<LivingEntity, Infection> INFECTED = new WeakHashMap<>();
    private static final String MARKER = "PhantomWitherLease";
    private WitherCombatEffects() {}
    public static void infect(WitherPhantom source, LivingEntity target) {
        if (!(target instanceof Enemy) || !source.validTarget(target) || target.hasEffect(MobEffects.WITHER) || INFECTED.size() >= 512) { return; }
        if (target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), source)) {
            INFECTED.put(target, new Infection(source.getUUID(), source.session(), target.getEffect(MobEffects.WITHER)));
            target.getPersistentData().putBoolean(MARKER, true);
        }
    }
    private static boolean active(LivingEntity target, Infection infection) {
        return target.level() instanceof ServerLevel level && level.getEntity(infection.source) instanceof WitherPhantom source
                && source.combatEnabled() && source.session().equals(infection.session) && source.validTarget(target);
    }
    private static void clear(LivingEntity target) {
        Infection infection = INFECTED.remove(target);
        if (infection != null && owned(target, infection)) { target.removeEffect(MobEffects.WITHER); }
        target.getPersistentData().remove(MARKER);
    }
    private static boolean owned(LivingEntity target, Infection infection) {
        return target.getEffect(MobEffects.WITHER) == infection.effect && infection.effect.getAmplifier() == 0 && infection.effect.getDuration() <= 80;
    }
    public static void release(WitherPhantom source) {
        for (var entry : Map.copyOf(INFECTED).entrySet()) { if (entry.getValue().source.equals(source.getUUID())) { clear(entry.getKey()); } }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity target)) { return; }
        Infection infection = INFECTED.get(target);
        if (infection != null && (!active(target, infection) || !owned(target, infection))) { clear(target); }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity target && target.getPersistentData().getBooleanOr(MARKER, false)) {
            var effect = target.getEffect(MobEffects.WITHER); if (effect != null && effect.getAmplifier() == 0 && effect.getDuration() <= 80) { target.removeEffect(MobEffects.WITHER); }
            target.getPersistentData().remove(MARKER);
        }
    }
    @SubscribeEvent public static void protect(LivingIncomingDamageEvent event) {
        Infection infection = INFECTED.get(event.getEntity());
        if (infection != null && event.getSource().is(DamageTypes.WITHER) && owned(event.getEntity(), infection) && !active(event.getEntity(), infection)) { event.setCanceled(true); clear(event.getEntity()); }
    }
    @SubscribeEvent public static void defend(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof WitherPhantom source && source.combatEnabled() && source.hasCarapace()
                && event.getSource().getEntity() instanceof LivingEntity enemy && source.validTarget(enemy)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { event.setNewDamage(event.getNewDamage() * 0.7F); }
    }
    @SubscribeEvent public static void calm(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof WitherSkeleton skeleton && event.getNewAboutToBeSetTarget() instanceof WitherPhantom phantom
                && !phantom.provokedBy(skeleton) && phantom.getTarget() != skeleton) { event.setCanceled(true); }
    }
    private record Infection(UUID source, UUID session, MobEffectInstance effect) {}
}
