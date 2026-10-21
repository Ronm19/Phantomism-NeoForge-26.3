package net.ronm19.phantomism.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.GolemPhantom;
import net.ronm19.phantomism.vfx.TitaniumVfx;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class GolemDefense {
    private GolemDefense() {}
    public static boolean ordinary(Projectile projectile) { return projectile.getType() == EntityTypes.ARROW && projectile instanceof Arrow arrow && arrow.getPickupItemStackOrigin().is(Items.ARROW) && arrow.getPickupItemStackOrigin().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY).equals(net.minecraft.world.item.alchemy.PotionContents.EMPTY); }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof GolemPhantom source) || !(source.level() instanceof ServerLevel) || !(event.getSource().getDirectEntity() instanceof Projectile projectile) || !ordinary(projectile)) { return; }
        event.setCanceled(true); projectile.discard(); source.notice(); TitaniumVfx.emit(source, TitaniumVfx.SHIELD_HIT, null, projectile.position(), Vec3.ZERO, 12, 1);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity(); if (!(victim.level() instanceof ServerLevel level) || event.getNewDamage() <= 0) { return; }
        if (victim instanceof GolemPhantom && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) && !event.getSource().is(DamageTypeTags.IS_EXPLOSION) && !event.getSource().is(DamageTypeTags.IS_PROJECTILE) && event.getSource().getDirectEntity() instanceof LivingEntity) { event.setNewDamage(event.getNewDamage() * .85F); }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || PhantomDuels.pair(victim, attacker)) { return; }
        for (GolemPhantom source : level.getEntitiesOfClass(GolemPhantom.class, victim.getBoundingBox().inflate(12), candidate -> candidate.enabled() && candidate.protects(victim)).stream().limit(8).toList()) {
            if (!source.safeEnemy(attacker) || PhantomDuels.excludesObserver(source, attacker)) { continue; }
            source.incomingThreat(attacker, victim); if (event.getNewDamage() >= 6 || victim.getHealth() < victim.getMaxHealth() * .4F) { source.guard(level); }
            if (source.covers(victim)) { event.setNewDamage(event.getNewDamage() * .55F); TitaniumVfx.emit(source, TitaniumVfx.SHIELD_HIT, null, victim.getBoundingBox().getCenter(), Vec3.ZERO, 12, 2); return; }
        }
    }
    public static void intercept(GolemPhantom source, ServerLevel level) {
        var projectiles = level.getEntitiesOfClass(Projectile.class, source.getBoundingBox().inflate(12), projectile -> ordinary(projectile) && projectile.getOwner() instanceof LivingEntity attacker && source.safeEnemy(attacker) && !source.ally(attacker)).stream().limit(24).toList();
        int broken = 0, danger = 0;
        for (Projectile projectile : projectiles) {
            if (!(projectile.getOwner() instanceof LivingEntity attacker) || projectile.getDeltaMovement().lengthSqr() < .01D) { continue; }
            boolean approaching = level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(12), source::protects).stream().limit(16).anyMatch(ward -> !PhantomDuels.pair(attacker, ward) && !PhantomDuels.excludesObserver(source, attacker) && ward.getBoundingBox().inflate(.7D).clip(projectile.position(), projectile.position().add(projectile.getDeltaMovement().scale(8))).isPresent());
            if (!approaching) { continue; } danger++;
            if (danger >= 2) { source.guard(level); }
            if (source.guardTicks() > 0 && source.line(source.getEyePosition(), projectile.position()) && broken++ < 4) { TitaniumVfx.emit(source, TitaniumVfx.SHIELD_HIT, null, projectile.position(), Vec3.ZERO, 12, 1); projectile.discard(); }
        }
    }
}
