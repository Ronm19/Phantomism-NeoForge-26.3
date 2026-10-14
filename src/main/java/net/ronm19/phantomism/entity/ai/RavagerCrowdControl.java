package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.RavagerPhantom;
import net.ronm19.phantomism.util.ModTags;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class RavagerCrowdControl {
    private static final Map<Mob, Control> CONTROLS = new WeakHashMap<>();

    private RavagerCrowdControl() {}

    public static boolean fearImmune(LivingEntity target) {
        return target.is(ModTags.EntityTypes.RAVAGER_FEAR_IMMUNE) || target.getMaxHealth() >= 200.0F;
    }

    public static void fear(RavagerPhantom source, LivingEntity target, int ticks) {
        if (fearImmune(target) || !source.isAreaEnemy(target)) { return; }
        boolean strong = target.getMaxHealth() >= 80.0F || target.is(ModTags.EntityTypes.EARTH_CONTROL_RESISTANT);
        if (strong) {
            stagger(source, target, 12);
            return;
        }
        if (target instanceof Mob mob) { apply(source, mob, Math.clamp(ticks, 1, 60), true, false); }
    }

    public static void stagger(RavagerPhantom source, LivingEntity target, int ticks) {
        if (!source.isAreaEnemy(target)) { return; }
        int duration = fearImmune(target) ? Math.min(ticks, 6) : target.getMaxHealth() >= 80.0F ? Math.min(ticks, 12) : Math.clamp(ticks, 1, 30);
        if (target instanceof Mob mob) { apply(source, mob, duration, false, false); }
        else if (!fearImmune(target)) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0), source); }
    }

    public static void presence(RavagerPhantom source, Mob target, int ticks) {
        if (!source.isTame() || fearImmune(target) || !source.isAreaEnemy(target)) { return; }
        apply(source, target, Math.clamp(ticks, 1, 10), false, true);
    }

    private static void apply(RavagerPhantom source, Mob target, int ticks, boolean flee, boolean familyOnly) {
        if (!(target.level() instanceof ServerLevel level) || source.isBaby() || !source.isAlive()) { return; }
        Control previous = CONTROLS.get(target);
        if (previous != null && previous.expires > level.getGameTime() + ticks) { return; }
        if (previous != null) { restore(target, previous); }
        var brain = target.getBrain();
        WalkTarget original = brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
                ? brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) : null;
        long originalLifetime = original == null ? 0 : brain.getTimeUntilExpiry(MemoryModuleType.WALK_TARGET);
        Control control = new Control(source.getUUID(), level.getGameTime(), level.getGameTime() + ticks, flee, familyOnly, original, originalLifetime);
        CONTROLS.put(target, control);
        update(level, target, source, control);
    }

    private static void update(ServerLevel level, Mob target, RavagerPhantom source, Control control) {
        if (control.flee) {
            Vec3 away = target.position().subtract(source.position());
            if (away.horizontalDistanceSqr() < 0.01D) { away = new Vec3(1.0D, 0.0D, 0.0D); }
            Vec3 destination = target.position().add(new Vec3(away.x, 0.0D, away.z).normalize().scale(6.0D));
            var destinationBox = target.getBoundingBox().move(destination.subtract(target.position()));
            if (level.hasChunkAt(net.minecraft.core.BlockPos.containing(destination)) && level.getWorldBorder().isWithinBounds(destinationBox)
                    && level.noBlockCollision(target, destinationBox)) {
                if (target.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                    control.walk = new WalkTarget(destination, 1.15F, 1);
                    target.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, control.walk, Math.max(1L, control.expires - level.getGameTime()));
                }
                if (target.tickCount % 4 == 0) { target.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.15D); }
                target.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.15D);
                return;
            }
        }
        target.getNavigation().stop();
        target.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 0.0D);
    }

    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) { return; }
        Control control = CONTROLS.get(mob);
        if (control == null) { return; }
        var entity = level.getEntity(control.source);
        if (level.getGameTime() >= control.expires || !mob.isAlive() || !(entity instanceof RavagerPhantom source)
                || !source.isAlive() || !source.isAreaEnemy(mob) || source.distanceToSqr(mob) > 24.0D * 24.0D) {
            restore(mob, control);
            CONTROLS.remove(mob);
            return;
        }
        update(level, mob, (RavagerPhantom) entity, control);
    }

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob attacker) || !(attacker.level() instanceof ServerLevel level)) { return; }
        Control control = CONTROLS.get(attacker);
        if (control == null || level.getGameTime() >= control.expires) { return; }
        var entity = level.getEntity(control.source);
        if (entity instanceof RavagerPhantom source && source.isAlive() && source.isAreaEnemy(attacker)
                && source.distanceToSqr(attacker) <= 24.0D * 24.0D
                && (!control.familyOnly || event.getEntity() == source || source.isAlliedTo(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof RavagerPhantom source && !source.level().isClientSide()) {
            source.rewardFuryKill(event.getEntity());
        }
    }

    public static void release(RavagerPhantom source) {
        if (source.level().isClientSide()) { return; }
        CONTROLS.entrySet().removeIf(entry -> {
            if (!entry.getValue().source.equals(source.getUUID())) { return false; }
            restore(entry.getKey(), entry.getValue());
            return true;
        });
    }

    private static void restore(Mob target, Control control) {
        var brain = target.getBrain();
        if (control.walk != null && brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == control.walk) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            long elapsed = target.level().getGameTime() - control.started;
            if (control.original != null && control.originalLifetime > elapsed) {
                if (control.originalLifetime == Long.MAX_VALUE) { brain.setMemory(MemoryModuleType.WALK_TARGET, control.original); }
                else { brain.setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, control.original, control.originalLifetime - elapsed); }
            }
        }
        target.getNavigation().stop();
    }

    private static final class Control {
        private final UUID source;
        private final long started;
        private final long expires;
        private final boolean flee;
        private final boolean familyOnly;
        private final WalkTarget original;
        private final long originalLifetime;
        private WalkTarget walk;

        private Control(UUID source, long started, long expires, boolean flee, boolean familyOnly, WalkTarget original, long originalLifetime) {
            this.source = source;
            this.started = started;
            this.expires = expires;
            this.flee = flee;
            this.familyOnly = familyOnly;
            this.original = original;
            this.originalLifetime = originalLifetime;
        }
    }
}
