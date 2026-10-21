package net.ronm19.phantomism.effect;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.ronm19.phantomism.entity.BloodPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.vfx.PhantomVisualEffects;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;
import org.jspecify.annotations.Nullable;

public final class BleedingEffect extends MobEffect {
    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();
    public BleedingEffect() { super(MobEffectCategory.HARMFUL, 0xD82436); }
    public static boolean apply(BloodPhantom source, LivingEntity target, int duration, boolean strong) {
        if (!(source.level() instanceof ServerLevel level) || !source.combatEnabled() || !source.isValidBloodTarget(target)) { return false; }
        int ticks = Math.clamp(duration, 20, 120);
        if (!target.addEffect(new MobEffectInstance(PhantomismContent.BLEEDING, ticks, strong ? 1 : 0), source)) { return false; }
        MobEffectInstance active = target.getEffect(PhantomismContent.BLEEDING);
        MARKS.put(target, new Mark(source.getUUID(), source.combatSession(), level.getGameTime() + (active == null ? ticks : active.getDuration()))); return true;
    }
    private static @Nullable BloodPhantom source(LivingEntity target) {
        Mark mark = MARKS.get(target);
        if (mark != null && target.level() instanceof ServerLevel level && level.getGameTime() <= mark.expires
                && level.getEntity(mark.source) instanceof BloodPhantom blood && blood.combatSession().equals(mark.session)
                && blood.combatEnabled() && blood.isValidBloodTarget(target) && blood.distanceToSqr(target) <= 48 * 48
                && blood.getAssignments().permitsDefensivePursuit(target)) { return blood; }
        MARKS.remove(target); return null;
    }
    public static boolean isActive(LivingEntity target) { return target.hasEffect(PhantomismContent.BLEEDING) && source(target) != null; }
    public static float healingFactor(LivingEntity target) {
        MobEffectInstance effect = target.getEffect(PhantomismContent.BLEEDING);
        return effect != null && source(target) != null ? effect.getAmplifier() > 0 ? 0.5F : 0.75F : 1;
    }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity target, int amplifier) {
        BloodPhantom blood = source(target); if (blood == null) { return false; }
        if (blood.hurtBloodTarget(level, target, blood.damageSources().mobAttack(blood), amplifier > 0 ? 2 : 1) > 0) { PhantomVisualEffects.trail(level, PhantomVfxTheme.BLOOD, target.getBoundingBox().getCenter()); }
        return target.isAlive();
    }
    private record Mark(UUID source, UUID session, long expires) {}
}
