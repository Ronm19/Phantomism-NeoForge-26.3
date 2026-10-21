package net.ronm19.phantomism.effect;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.HuskPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.util.ModTags;

public final class ExhaustionEffect extends MobEffect {
    private static final Map<LivingEntity, Long> APPLICATIONS = new WeakHashMap<>();

    public ExhaustionEffect() {
        super(MobEffectCategory.HARMFUL, 0xC89954);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, id("exhaustion_movement"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.075D * tiers(amplifier));
        this.addAttributeModifier(Attributes.ATTACK_SPEED, id("exhaustion_attack_speed"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.10D * tiers(amplifier));
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE, id("exhaustion_damage"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.06D * tiers(amplifier));
    }

    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("phantomism", name); }
    private static int tiers(int amplifier) { return Math.clamp(amplifier, 0, 2) + 1; }

    public static boolean apply(HuskPhantom source, LivingEntity target) {
        if (!(source.level() instanceof ServerLevel level) || !source.isAlive() || source.isBaby() || !source.isValidHuskTarget(target)) { return false; }
        MobEffectInstance previous = target.getEffect(PhantomismContent.EXHAUSTION);
        long now = level.getGameTime();
        long last = APPLICATIONS.getOrDefault(target, Long.MIN_VALUE / 2);
        int amplifier = previous == null ? 0 : Math.clamp(previous.getAmplifier(), 0, 2);
        boolean advance = previous == null || now - last >= 20;
        if (previous != null && advance) { amplifier = Math.min(2, amplifier + 1); }
        if (target.is(ModTags.EntityTypes.HUSK_EXHAUSTION_RESISTANT)) { amplifier = 0; }
        int duration = Config.huskExhaustionDuration();
        boolean applied = target.addEffect(new MobEffectInstance(PhantomismContent.EXHAUSTION, duration, amplifier), source);
        if (applied && advance) { APPLICATIONS.put(target, now); }
        return applied;
    }
}
