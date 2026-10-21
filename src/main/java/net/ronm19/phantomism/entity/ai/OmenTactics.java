package net.ronm19.phantomism.entity.ai;

import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.OmenPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class OmenTactics {
    private OmenTactics() {}
    public static void coordinate(OmenPhantom source, LivingEntity target) {
        if (!source.enabled() || !source.isTame() || !source.validTarget(target) || target instanceof OwnableEntity || !OmenPhantom.raider(target)) { return; }
        for (AbstractPhantom ally : source.level().getEntitiesOfClass(AbstractPhantom.class, source.getBoundingBox().inflate(28), source::crew).stream()
                .filter(ally -> ally != source && ally.isAlive() && !ally.isBaby() && !ally.isVehicle() && !ally.isPassenger())
                .filter(ally -> ally.getOrder() == AbstractPhantom.Order.FOLLOW && ally.getTask() == PhantomAssignments.Task.NONE)
                .filter(ally -> ally.getTarget() == null && !ally.isTargetSuppressed(target) && !PhantomDuels.excludesObserver(ally, target))
                .sorted(Comparator.comparingDouble(source::distanceToSqr)).limit(8).toList()) { ally.defendFamily(target); }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) { return; }
        if (event.getSource().getEntity() instanceof AbstractPhantom attacker && OmenPhantom.raider(target)) {
            boolean supported = !level.getEntitiesOfClass(OmenPhantom.class, attacker.getBoundingBox().inflate(56), source -> source.empowers(attacker, target)).isEmpty();
            if (supported) { event.setNewDamage(event.getNewDamage() * 1.15F); }
        }
        if (target instanceof OmenPhantom source && event.getSource().getEntity() instanceof LivingEntity attacker && source.safeEnemy(attacker)) {
            source.noticeIncoming(attacker);
            if (event.getNewDamage() >= 3 || source.getHealth() - event.getNewDamage() <= source.getMaxHealth() * .4F) { source.shield(level); }
            event.setNewDamage(Math.max(0, event.getNewDamage() - source.absorb(event.getNewDamage())));
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) { if (!event.getLevel().isClientSide() && event.getEntity() instanceof OmenPhantom source) { source.endOmen(); } }
}
