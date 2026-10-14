package net.ronm19.phantomism.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ronm19.phantomism.Phantomism;

public final class SonicDisruptionEffect extends MobEffect {
    public SonicDisruptionEffect() {
        super(MobEffectCategory.HARMFUL, 0x64F4FF);
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE, Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sonic_disruption_damage"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.1D);
        this.addAttributeModifier(Attributes.FOLLOW_RANGE, Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "sonic_disruption_awareness"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.25D);
    }
}
