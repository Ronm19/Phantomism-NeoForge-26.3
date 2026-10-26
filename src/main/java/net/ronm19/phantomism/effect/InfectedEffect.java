package net.ronm19.phantomism.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;

public final class InfectedEffect extends MobEffect {
    public InfectedEffect() { super(MobEffectCategory.HARMFUL, 0x88C92D); }
    @Override public void removeAttributeModifiers(AttributeMap attributes){super.removeAttributeModifiers(attributes);AttributeInstance speed=attributes.getInstance(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"infected_mutation"));}}
}
