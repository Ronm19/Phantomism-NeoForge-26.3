package net.ronm19.phantomism.entity.ai;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.SpectralPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class SpectralSafety {
    private SpectralSafety() {}
    private static SpectralPhantom guardian(Entity entity) {
        return entity instanceof SpectralPhantom phantom ? phantom
                : entity.getVehicle() instanceof SpectralPhantom phantom && phantom.getControllingPassenger() == entity ? phantom : null;
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) { return; }
        if (event.getSource().is(SpectralPhantom.STRIKE) && event.getSource().getEntity() instanceof SpectralPhantom attacker && attacker.validTarget(event.getEntity())) {
            double bypass = Math.clamp(attacker.armorBypass(), 0, .5D);
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> (float) (reduction * (1 - bypass)));
        }
        SpectralPhantom source = guardian(event.getEntity()); if (source == null) { return; }
        if (source.isPhasing() && event.getSource().is(DamageTypes.IN_WALL)) { event.setCanceled(true); return; }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)
                || !(event.getSource().getEntity() instanceof LivingEntity threat) || !source.validTarget(threat)) { return; }
        if (event.getAmount() >= 4 || event.getEntity().getHealth() <= event.getEntity().getMaxHealth() * .35F) { source.guard((net.minecraft.server.level.ServerLevel) source.level(), threat); }
        if (source.mitigation() > 0) { event.setAmount(event.getAmount() * (1 - source.mitigation())); }
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        SpectralPhantom source = guardian(event.getEntity());
        if (source != null && !source.level().isClientSide() && event.getHealthDamage() >= 4 && event.getSource().getEntity() instanceof LivingEntity threat && source.validTarget(threat)) {
            source.slip((net.minecraft.server.level.ServerLevel) source.level(), threat);
        }
    }
    @SubscribeEvent public static void dismount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityBeingMounted() instanceof SpectralPhantom source) || !source.isPhasing()) { return; }
        if (!event.getLevel().isClientSide() && !source.passage().recall()) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof SpectralPhantom source && !source.level().isClientSide()) { source.passage().recall(); }
    }
    @SubscribeEvent public static void rider(EntityTickEvent.Post event) {
        if (event.getEntity().getVehicle() instanceof SpectralPhantom source) {
            event.getEntity().noPhysics = source.isPhasing() || event.getEntity().isSpectator();
            if (source.isPhasing()) { event.getEntity().resetFallDistance(); }
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        if (event.getEntity() instanceof SpectralPhantom source) { source.passage().recall(); source.passage().releaseTickets(); source.cancelCombat(); }
    }
}
