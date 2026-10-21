package net.ronm19.phantomism.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class BoneFractureEffect extends MobEffect {
    public BoneFractureEffect() {
        super(MobEffectCategory.HARMFUL, 0xC8B4A3);
        this.addAttributeModifier(Attributes.ARMOR, Identifier.fromNamespaceAndPath("phantomism", "bone_fracture_armor"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.25D);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, Identifier.fromNamespaceAndPath("phantomism", "bone_fracture_toughness"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.15D);
    }
}
