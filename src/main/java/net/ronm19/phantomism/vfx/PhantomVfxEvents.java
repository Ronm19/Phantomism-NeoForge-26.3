package net.ronm19.phantomism.vfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.BlackPhantom;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.WaterPhantom;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomVfxEvents {
    private static final Map<AbstractPhantom, UUID> OBSERVED_TARGETS = new WeakHashMap<>();
    private PhantomVfxEvents() {
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) {
            return;
        }
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof AbstractPhantom phantom
                && entity.tickCount % 4 == 0) {
            PhantomVisualEffects.trail(level, PhantomVfxTheme.of(phantom), entity.position());
        } else if (entity instanceof AbstractPhantom phantom && !phantom.isBaby()) {
            LivingEntity target = phantom.getTarget();
            UUID targetId = target != null && target.isAlive() ? target.getUUID() : null;
            UUID previous = OBSERVED_TARGETS.put(phantom, targetId);
            if (targetId != null && !targetId.equals(previous)) {
                PhantomVisualEffects.action(level, phantom, net.ronm19.phantomism.animation.PhantomAnimationAction.SENSE);
            }
            if (entity.tickCount % 20 == 0 && (phantom.hasEffect(MobEffects.RESISTANCE)
                    || phantom instanceof BlackPhantom black && black.isSkyKnightActive()
                    || phantom instanceof StormPhantom storm && storm.getStormCharge() > 0
                            && (phantom.getTarget() != null || phantom.isVehicle()))) {
                PhantomVisualEffects.aura(level, PhantomVfxTheme.of(phantom), phantom.position().add(0.0D, 0.5D, 0.0D));
            }
            if (phantom instanceof WaterPhantom && phantom.isInWater() && entity.tickCount % 10 == 0
                    && entity.getDeltaMovement().lengthSqr() > 0.02D) {
                PhantomVisualEffects.trail(level, PhantomVfxTheme.WATER, entity.position());
            }
            if (entity.tickCount % 8 == 0 && ((phantom.getPhantomAnimationFlags() & 1) != 0
                    || phantom instanceof BlackPhantom black && black.isSkyKnightActive())) {
                PhantomVisualEffects.trail(level, PhantomVfxTheme.of(phantom), entity.position());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void impact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !(projectile.getOwner() instanceof AbstractPhantom phantom)
                || event.getRayTraceResult().getType() == HitResult.Type.MISS) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && (!(hit.getEntity() instanceof LivingEntity target) || target instanceof AbstractPhantom
                        || phantom.isAlliedTo(target) || phantom.isTargetSuppressed(target))) {
            return;
        }
        PhantomVisualEffects.impact(level, PhantomVfxTheme.of(phantom), event.getRayTraceResult().getLocation());
    }
}
