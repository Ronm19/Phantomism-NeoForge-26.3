package net.ronm19.phantomism.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ronm19.phantomism.Phantomism;

public final class ArmorFractureEffect extends MobEffect {
    public ArmorFractureEffect() {
        super(MobEffectCategory.HARMFUL, 0xA8E63B);
        this.addAttributeModifier(Attributes.ARMOR, id("armor_fracture_armor"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.25D);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, id("armor_fracture_toughness"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> -0.15D);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path); }
}
