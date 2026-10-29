package net.ronm19.phantomism.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.entity.AbstractPhantom;

/** Loaded-area owner following. Combat and mounted movement keep their own controllers. */
public final class PhantomFollowNavigation {
    private final AbstractPhantom phantom;
    private Vec3 progress, destination;
    private int nextPlan, lastProgress;
    public PhantomFollowNavigation(AbstractPhantom phantom) { this.phantom = phantom; }
    public void reset() {
        if (this.destination != null) { this.phantom.getNavigation().stop(); }
        this.progress = null; this.destination = null; this.nextPlan = 0;
    }
    public void move(LivingEntity owner, double speed) {
        if (owner.level() != phantom.level() || !owner.isAlive() || phantom.isVehicle()
                || phantom.isPassenger() || phantom.getTarget() != null) { reset(); return; }
        int now = phantom.tickCount;
        if (progress == null || phantom.position().distanceToSqr(progress) >= .25D) {
            progress = phantom.position(); lastProgress = now;
        }
        // A trapped follower must recover, but Stay and combat never invoke this helper.
        if (now - lastProgress >= 60 && phantom.distanceToSqr(owner) > 36) {
            phantom.tryToTeleportToOwner(); reset(); return;
        }
        if (now < nextPlan) { return; }
        nextPlan = now + 10;
        Vec3 goal = owner.position().add(0, 3, 0);
        var targetBox = phantom.getBoundingBox().move(goal.subtract(phantom.position()));
        if (!phantom.isLoadedFlightBox(targetBox) || !phantom.level().noBlockCollision(phantom, targetBox)
                || phantom.level().containsAnyLiquid(targetBox)) { goal = owner.position().add(0, 1, 0); }
        // Navigation never requests a path through unloaded chunks.
        var region = phantom.getBoundingBox().expandTowards(goal.subtract(phantom.position())).inflate(8);
        if (destination != null && destination.distanceToSqr(goal) <= 4 && !phantom.getNavigation().isDone()) { return; }
        if (phantom.distanceToSqr(owner) <= 32 * 32 && phantom.isLoadedFlightBox(region)
                && phantom.getNavigation().moveTo(goal.x, goal.y, goal.z, speed)) {
            destination = goal; return;
        }
        phantom.flyToAssignment(goal, speed);
        destination = goal;
    }
}
