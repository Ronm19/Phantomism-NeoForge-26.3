package net.ronm19.phantomism.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomFamilyProtectionEvents {
    private PhantomFamilyProtectionEvents() {
    }

    @SubscribeEvent
    public static void onFamilyHurt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || event.getInflictedDamage() <= 0.0F
                || !(victim instanceof OwnableEntity companion) || companion.getOwnerReference() == null
                || (victim instanceof TamableAnimal animal && !animal.isTame())
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (PhantomDuels.pair(victim, attacker)) { return; }
        var owner = companion.getOwnerReference();
        if (owner.matches(attacker) || attacker instanceof AbstractPhantom || victim.isAlliedTo(attacker)
                || (attacker instanceof OwnableEntity owned && owner.equals(owned.getOwnerReference()))) {
            return;
        }
        double radius = Config.FAMILY_DEFENSE_RADIUS.get();
        for (AbstractPhantom defender : level.getEntitiesOfClass(AbstractPhantom.class,
                victim.getBoundingBox().inflate(radius), phantom -> phantom.isAlive() && phantom.isTame()
                        && owner.equals(phantom.getOwnerReference()) && phantom.distanceToSqr(victim) <= radius * radius)) {
            defender.defendFamily(attacker);
        }
    }
}
