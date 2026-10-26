package net.ronm19.phantomism.entity.ai;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.InstrumentItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class PhantomHornRally {
    private static final double FLIGHT_RANGE_SQUARED = 40.0D * 40.0D;
    private static final Map<AbstractPhantom, Rally> RETURNING = new WeakHashMap<>();

    private PhantomHornRally() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void horn(LivingEntityUseItemEvent.Start event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator() || owner.wonGame
                || !(event.getItem().getItem() instanceof InstrumentItem) || !event.getItem().has(DataComponents.INSTRUMENT)
                || event.getDuration() <= 0 || owner.getCooldowns().isOnCooldown(event.getItem())) { return; }
        var followers = new ArrayList<AbstractPhantom>();
        for (ServerLevel level : owner.level().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AbstractPhantom phantom && callable(phantom, owner)) { followers.add(phantom); }
            }
        }
        for (AbstractPhantom phantom : followers) {
            phantom.interruptCombatForRecall();
            phantom.setTask(PhantomAssignments.Task.NONE);
            phantom.setOrder(AbstractPhantom.Order.FOLLOW);
            RETURNING.put(phantom, new Rally(owner.getUUID()));
            tick(phantom, (ServerLevel) phantom.level(), true);
        }
    }

    private static boolean callable(AbstractPhantom phantom, ServerPlayer owner) {
        return phantom.isAlive() && !phantom.isRemoved() && phantom.isTame() && phantom.isOwnedBy(owner)
                && !phantom.isPassenger() && !phantom.isVehicle() && !phantom.isLeashed();
    }

    public static boolean returning(AbstractPhantom phantom) {
        Rally rally = RETURNING.get(phantom);
        return rally != null && phantom.isAlive() && !phantom.isRemoved() && phantom.getOwnerReference() != null
                && rally.owner.equals(phantom.getOwnerReference().getUUID()) && phantom.getOrder() == AbstractPhantom.Order.FOLLOW
                && phantom.getTask() == PhantomAssignments.Task.NONE && !phantom.isPassenger() && !phantom.isVehicle() && !phantom.isLeashed();
    }

    public static void cancel(AbstractPhantom phantom) { RETURNING.remove(phantom); }

    public static boolean tick(AbstractPhantom phantom, ServerLevel level) {
        return tick(phantom, level, false);
    }

    private static boolean tick(AbstractPhantom phantom, ServerLevel level, boolean immediate) {
        Rally rally = RETURNING.get(phantom);
        if (rally == null) { return false; }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rally.owner);
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.wonGame || !owner.isAddedToLevel()
                || !returning(phantom) || ++rally.ticks > 600) { cancel(phantom); return false; }
        if (owner.level() != level || phantom.distanceToSqr(owner) > FLIGHT_RANGE_SQUARED) {
            AbstractPhantom moved = PhantomDimensionTravel.recallOwner(phantom, owner, immediate);
            if (moved != null) { cancel(phantom); cancel(moved); }
            return true;
        }
        if (phantom.distanceToSqr(owner) <= 36.0D) { cancel(phantom); return false; }
        phantom.getNavigation().stop();
        phantom.getMoveControl().setWantedPosition(owner.getX(), owner.getY() + 3.0D, owner.getZ(), 2.4D);
        return true;
    }

    private static final class Rally {
        private final UUID owner;
        private int ticks;

        private Rally(UUID owner) { this.owner = owner; }
    }
}
