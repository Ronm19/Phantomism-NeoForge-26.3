package net.ronm19.phantomism.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;

public final class FrostboundEffect extends MobEffect {
    public FrostboundEffect() {
        super(MobEffectCategory.HARMFUL, 0x9DF7FF);
        Identifier modifier = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "frostbound");
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, modifier, -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.FLYING_SPEED, modifier, -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (!entity.canFreeze()) {
            return false;
        }
        entity.setTicksFrozen(entity.getTicksRequiredToFreeze());
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, entity.isNoGravity() ? 0.0D : Math.min(0.0D, movement.y), 0.0D);
        return true;
    }
}
