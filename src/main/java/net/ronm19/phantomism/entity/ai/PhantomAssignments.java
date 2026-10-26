package net.ronm19.phantomism.entity.ai;

import com.mojang.serialization.Codec;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomAssignments {
    private final AbstractPhantom phantom;
    private GlobalPos home;
    private GlobalPos guardPosition;
    private Task task = Task.NONE;
    private Vec3 activityCenter;
    private BlockPos grassTarget;
    private ItemEntity itemTarget;
    private long nextScan;
    private long targetExpires;
    private boolean areaCombat;
    private long pursuitExpires;
    private long resumeAt;
    private ItemStack cargo = ItemStack.EMPTY;
    private Vec3 homeFlightCenter;
    private long nextHomeCheck;
    private boolean returningFromPatrolCombat;

    public PhantomAssignments(AbstractPhantom phantom) {
        this.phantom = phantom;
    }

    public GlobalPos home() {
        return this.home;
    }

    public GlobalPos guardPosition() {
        return this.guardPosition;
    }

    public Task task() {
        return this.task;
    }

    public ItemStack cargo() {
        return this.cargo;
    }

    public void dropCargo(ServerLevel level) {
        if (!this.cargo.isEmpty()) {
            this.phantom.spawnAtLocation(level, this.cargo.copy());
            this.cargo = ItemStack.EMPTY;
        }
    }

    public void setHome(GlobalPos home) {
        this.home = home;
        this.homeFlightCenter = null;
        this.nextHomeCheck = 0L;
        this.returningFromPatrolCombat = false;
        this.resetWork();
    }

    public void setGuardPosition(GlobalPos position) {
        this.guardPosition = position;
        this.resetWork();
    }

    public void setTask(Task task) {
        this.task = task;
        this.activityCenter = this.phantom.position();
        this.resetWork();
        if (this.areaCombat) {
            this.phantom.clearCombatTarget(false);
        }
    }

    public void resetWork() {
        this.grassTarget = null;
        this.itemTarget = null;
        this.nextScan = this.phantom.level().getGameTime() + Math.floorMod(this.phantom.getUUID().hashCode(), 20);
    }

    public void movementChanged() {
        this.areaCombat = false;
        this.returningFromPatrolCombat = false;
        this.activityCenter = this.phantom.position();
        this.resetWork();
    }

    public void stopAreaCombat() {
        if (this.areaCombat && this.isHomePatrol()) {
            this.returningFromPatrolCombat = true;
        }
        this.areaCombat = false;
    }

    public boolean isHomePatrol() {
        return this.phantom.isTame() && this.phantom.getOrder() == AbstractPhantom.Order.WANDER
                && this.homeCenter() != null;
    }

    public boolean isReturningFromPatrolCombat() {
        return this.returningFromPatrolCombat;
    }

    public void finishPatrolReturn() {
        this.returningFromPatrolCombat = false;
    }

    public boolean isWithinHome(Vec3 position, double margin) {
        Vec3 center = this.homeCenter();
        double radius = Config.PHANTOM_HOME_RADIUS.get() + margin;
        return center != null && horizontalDistanceSquared(position, center) <= radius * radius;
    }

    public static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double deltaX = first.x - second.x;
        double deltaZ = first.z - second.z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    public Vec3 homeFlightCenter(ServerLevel level) {
        Vec3 center = this.homeCenter();
        if (center == null) {
            return null;
        }
        if (level.getGameTime() < this.nextHomeCheck && this.homeFlightCenter != null
                && this.safeFlightPosition(this.homeFlightCenter)) {
            return this.homeFlightCenter;
        }
        this.nextHomeCheck = level.getGameTime() + 100L;
        this.homeFlightCenter = null;
        double closest = Double.MAX_VALUE;
        for (int ring = 0; ring <= Math.min(16, Config.PHANTOM_HOME_RADIUS.get()); ring += 4) {
            for (int direction = 0; direction < (ring == 0 ? 1 : 8); direction++) {
                double angle = direction * Math.PI / 4.0D;
                double destinationX = center.x + Math.cos(angle) * ring;
                double destinationZ = center.z + Math.sin(angle) * ring;
                if (!this.loadedColumn(level, destinationX, destinationZ)) {
                    continue;
                }
                double height = Math.max(center.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        (int) Math.floor(destinationX), (int) Math.floor(destinationZ)) + 2.0D);
                for (int rise = 0; rise <= 4; rise++) {
                    Vec3 candidate = new Vec3(destinationX, height + rise, destinationZ);
                    double distance = candidate.distanceToSqr(center);
                    if (distance < closest && this.safeFlightPosition(candidate)) {
                        this.homeFlightCenter = candidate;
                        closest = distance;
                        break;
                    }
                }
            }
        }
        return this.homeFlightCenter;
    }

    public Vec3 patrolDestination(ServerLevel level, double destinationX, double destinationZ) {
        if (!this.loadedColumn(level, destinationX, destinationZ)) {
            return null;
        }
        double height = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(destinationX), (int) Math.floor(destinationZ)) + 2.0D;
        double destinationY = Math.max(this.phantom.getY() - 3.0D, Math.min(this.phantom.getY() + 5.0D, height));
        return new Vec3(destinationX, destinationY, destinationZ);
    }

    private boolean loadedColumn(ServerLevel level, double positionX, double positionZ) {
        return level.hasChunk(SectionPos.blockToSectionCoord((int) Math.floor(positionX)),
                SectionPos.blockToSectionCoord((int) Math.floor(positionZ)));
    }

    public boolean safeFlightPosition(Vec3 position) {
        AABB box = this.phantom.getBoundingBox().move(position.subtract(this.phantom.position()))
                .inflate(0.25D).expandTowards(0.0D, -1.0D, 0.0D);
        return this.phantom.isLoadedFlightBox(box) && this.phantom.level().noBlockCollision(this.phantom, box)
                && !this.phantom.level().containsAnyLiquid(box);
    }

    private boolean withinArea(Vec3 position, Vec3 center, double radius) {
        return (this.isHomePatrol() ? horizontalDistanceSquared(position, center) : position.distanceToSqr(center))
                <= radius * radius;
    }

    public Vec3 homeCenter() {
        return this.localCenter(this.home);
    }

    public Vec3 guardCenter() {
        return this.localCenter(this.guardPosition);
    }

    private Vec3 localCenter(GlobalPos position) {
        return position != null && position.dimension().equals(this.phantom.level().dimension())
                ? Vec3.atCenterOf(position.pos()).add(0.0D, 2.0D, 0.0D) : null;
    }

    public double radius() {
        if (this.phantom.getOrder() == AbstractPhantom.Order.STAY) {
            return 2.0D;
        }
        if (this.phantom.getOrder() == AbstractPhantom.Order.GUARD_POSITION) {
            return 16.0D;
        }
        if (this.phantom.getOrder() == AbstractPhantom.Order.FOLLOW
                || this.phantom.getOrder() == AbstractPhantom.Order.GUARD) {
            return 8.0D;
        }
        return this.isHomePatrol() ? Config.PHANTOM_HOME_RADIUS.get() : 30.0D;
    }

    private Vec3 center() {
        AbstractPhantom.Order order = this.phantom.getOrder();
        if (order == AbstractPhantom.Order.GUARD_POSITION) {
            return this.guardCenter();
        }
        if (order == AbstractPhantom.Order.FOLLOW || order == AbstractPhantom.Order.GUARD) {
            LivingEntity owner = this.phantom.getOwner();
            return owner != null && owner.level() == this.phantom.level() ? owner.position().add(0.0D, 2.0D, 0.0D) : null;
        }
        if (order == AbstractPhantom.Order.STAY) {
            return this.activityCenter;
        }
        Vec3 homeCenter = this.homeCenter();
        return homeCenter != null ? homeCenter : this.activityCenter;
    }

    public boolean permitsAutomaticTarget(LivingEntity target) {
        Vec3 center = this.center();
        double radius = this.radius();
        return this.phantom.isTame() && !this.phantom.isBaby()
                && (this.task == Task.HUNT || this.phantom.getOrder() == AbstractPhantom.Order.GUARD_POSITION || this.isHomePatrol())
                && !this.returningFromPatrolCombat
                && this.phantom.getOrder() != AbstractPhantom.Order.RETURN_HOME
                && this.phantom.level().getGameTime() >= this.resumeAt
                && target instanceof Enemy
                && !(target instanceof AbstractPhantom)
                && !(target instanceof OwnableEntity owned && owned.getOwnerReference() != null)
                && !this.phantom.isAlliedTo(target)
                && center != null && this.withinArea(target.position(), center, radius)
                && this.withinArea(this.phantom.position(), center, radius + 4.0D)
                && this.canSee(target);
    }

    public boolean permitsDefensivePursuit(LivingEntity target) {
        if (this.phantom.getOrder() != AbstractPhantom.Order.GUARD_POSITION && this.task != Task.HUNT && !this.isHomePatrol()) {
            return true;
        }
        Vec3 center = this.center();
        double boundary = this.radius() + 4.0D;
        if (center == null || this.phantom.level().getGameTime() < this.resumeAt
                || !this.withinArea(target.position(), center, boundary)
                || !this.withinArea(this.phantom.position(), center, boundary)) {
            return false;
        }
        this.beginAreaCombat();
        return true;
    }

    public void beginAreaCombat() {
        this.areaCombat = true;
        this.returningFromPatrolCombat = false;
        this.pursuitExpires = this.phantom.level().getGameTime() + 200L;
    }

    public boolean mustEndAreaCombat(LivingEntity target) {
        if (!this.areaCombat) {
            return false;
        }
        Vec3 center = this.center();
        double boundary = this.radius() + 4.0D;
        boolean ended = center == null || this.phantom.level().getGameTime() >= this.pursuitExpires
                || !this.withinArea(target.position(), center, boundary)
                || !this.withinArea(this.phantom.position(), center, boundary);
        if (ended) {
            this.resumeAt = this.phantom.level().getGameTime() + 60L;
        }
        return ended;
    }

    public boolean tick(ServerLevel level) {
        if (!this.phantom.isTame() || this.task == Task.NONE || this.task == Task.HUNT
                || this.phantom.getOrder() == AbstractPhantom.Order.RETURN_HOME || this.returningFromPatrolCombat) {
            return false;
        }
        LivingEntity owner = this.phantom.getOwner();
        Vec3 center = this.center();
        if (!(owner instanceof ServerPlayer player) || owner.level() != level || !owner.isAlive() || center == null) {
            return false;
        }
        if (!this.withinArea(this.phantom.position(), center, this.radius())) {
            this.resetWork();
            return false;
        }
        if (this.task == Task.HARVEST && this.phantom instanceof net.ronm19.phantomism.entity.AutumnPhantom autumn) { return autumn.harvest().tick(level, player); }
        return this.task == Task.HARVEST ? this.tickHarvest(level, player, center) : this.tickCollect(level, player, center);
    }

    private boolean tickHarvest(ServerLevel level, ServerPlayer owner, Vec3 center) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, this.phantom)) {
            this.grassTarget = null;
            return false;
        }
        long time = level.getGameTime();
        if (this.grassTarget != null && (time > this.targetExpires || !this.eligibleGrass(level, this.grassTarget, center))) {
            this.grassTarget = null;
        }
        if (this.grassTarget == null && time >= this.nextScan) {
            this.nextScan = time + 40L;
            BlockPos origin = this.phantom.blockPosition();
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-2, -4, -2), origin.offset(2, 1, 2))) {
                if (this.eligibleGrass(level, candidate, center)) {
                    this.grassTarget = candidate.immutable();
                    break;
                }
            }
            for (int attempt = 0; this.grassTarget == null && attempt < 48; attempt++) {
                int extent = (int) Math.min(30.0D, this.radius());
                BlockPos candidate = BlockPos.containing(this.isHomePatrol() ? this.phantom.position() : center).offset(
                        this.phantom.getRandom().nextInt(extent * 2 + 1) - extent,
                        this.phantom.getRandom().nextInt(13) - 6,
                        this.phantom.getRandom().nextInt(extent * 2 + 1) - extent);
                if (this.eligibleGrass(level, candidate, center)) {
                    this.grassTarget = candidate;
                }
            }
            this.targetExpires = time + 200L;
        }
        if (this.grassTarget == null) {
            return false;
        }
        Vec3 grass = Vec3.atCenterOf(this.grassTarget);
        if (this.phantom.position().distanceToSqr(grass) <= 6.25D && this.visible(level, grass, this.grassTarget)) {
            BlockPos position = this.grassTarget;
            if (level.mayInteract(owner, position)
                    && !CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, level.getBlockState(position)).isCanceled()
                    && CommonHooks.canEntityDestroy(level, position, this.phantom)) {
                level.destroyBlock(position, true, this.phantom);
            }
            this.grassTarget = null;
            this.nextScan = time + 40L;
            return false;
        }
        if (this.phantom.getOrder() == AbstractPhantom.Order.STAY) {
            return false;
        }
        this.phantom.flyToAssignment(grass.add(0.0D, 0.8D, 0.0D), 0.8D);
        return true;
    }

    private boolean eligibleGrass(ServerLevel level, BlockPos position, Vec3 center) {
        return position.getY() >= level.getMinY() && position.getY() <= level.getMaxY()
                && this.withinArea(Vec3.atCenterOf(position), center, this.radius())
                && level.hasChunk(SectionPos.blockToSectionCoord(position.getX()), SectionPos.blockToSectionCoord(position.getZ()))
                && level.getWorldBorder().isWithinBounds(position)
                && level.getBlockState(position).is(Blocks.SHORT_GRASS);
    }

    public void offerHarvest(ItemStack stack) {
        if (stack.isEmpty()) { return; }
        if (this.cargo.isEmpty()) { this.cargo = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())); }
        else if (ItemStack.isSameItemSameComponents(this.cargo, stack)) { int moved = Math.min(stack.getCount(), this.cargo.getMaxStackSize() - this.cargo.getCount()); this.cargo.grow(moved); stack.shrink(moved); }
    }
    public boolean deliverHarvest(ServerLevel level, ServerPlayer owner) { Vec3 center = this.center(); return !this.cargo.isEmpty() && center != null && this.tickCollect(level, owner, center); }
    private boolean tickCollect(ServerLevel level, ServerPlayer owner, Vec3 center) {
        if (!this.cargo.isEmpty()) {
            if (this.phantom.distanceToSqr(owner) <= 9.0D && this.canSee(owner)) {
                int capacity = this.deliveryCapacity(owner);
                if (capacity == 0) {
                    return false;
                }
                ItemStack delivery = this.cargo.copyWithCount(Math.min(capacity, this.cargo.getCount()));
                int offered = delivery.getCount();
                owner.getInventory().add(delivery);
                this.cargo.shrink(offered - delivery.getCount());
                owner.getInventory().setChanged();
                return false;
            }
            if (this.phantom.getOrder() != AbstractPhantom.Order.STAY
                    && this.withinArea(owner.position(), center, this.radius())) {
                this.phantom.flyToAssignment(owner.position().add(0.0D, 1.5D, 0.0D), 1.0D);
                return true;
            }
            return false;
        }
        long time = level.getGameTime();
        if (this.itemTarget != null && (time > this.targetExpires || !this.eligibleItem(this.itemTarget, owner, center))) {
            this.itemTarget = null;
        }
        if (this.itemTarget == null && time >= this.nextScan) {
            this.nextScan = time + 40L;
            Vec3 scanCenter = this.isHomePatrol() ? this.phantom.position() : center;
            this.itemTarget = level.getEntitiesOfClass(ItemEntity.class, new AABB(scanCenter, scanCenter).inflate(Math.min(30.0D, this.radius())),
                            item -> this.eligibleItem(item, owner, center))
                    .stream().min(Comparator.comparingDouble(this.phantom::distanceToSqr)).orElse(null);
            this.targetExpires = time + 200L;
        }
        if (this.itemTarget == null) {
            return false;
        }
        if (this.phantom.getBoundingBox().inflate(0.75D).intersects(this.itemTarget.getBoundingBox())
                && this.canSee(this.itemTarget)) {
            if (EventHooks.fireItemPickupPre(this.itemTarget, owner).canPickup().isFalse()
                    || !this.eligibleItem(this.itemTarget, owner, center)) {
                this.itemTarget = null;
                this.nextScan = time + 40L;
                return false;
            }
            ItemStack stack = this.itemTarget.getItem();
            int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack collected = stack.copyWithCount(amount);
            this.cargo = collected;
            this.phantom.take(this.itemTarget, amount);
            stack.shrink(amount);
            if (stack.isEmpty()) {
                this.itemTarget.discard();
            } else {
                this.itemTarget.setItem(stack.copy());
            }
            EventHooks.fireItemPickupPost(this.itemTarget, owner, collected.copy());
            this.itemTarget = null;
            return true;
        }
        if (this.phantom.getOrder() == AbstractPhantom.Order.STAY) {
            return false;
        }
        this.phantom.flyToAssignment(this.itemTarget.position(), 0.9D);
        return true;
    }

    private int deliveryCapacity(ServerPlayer owner) {
        int capacity = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack existing = owner.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                capacity += Math.min(64, this.cargo.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(existing, this.cargo)) {
                capacity += Math.max(0, Math.min(64, existing.getMaxStackSize()) - existing.getCount());
            }
        }
        return capacity;
    }

    private boolean eligibleItem(ItemEntity item, ServerPlayer owner, Vec3 center) {
        return item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay()
                && (item.getTarget() == null || item.getTarget().equals(owner.getUUID()))
                && !(item.getOwner() instanceof ServerPlayer thrower && thrower != owner)
                && this.withinArea(item.position(), center, this.radius())
                && this.canSee(item);
    }

    private boolean canSee(Entity target) {
        return this.phantom.isLoadedFlightBox(new AABB(this.phantom.getEyePosition(), target.getEyePosition()))
                && this.phantom.hasLineOfSight(target);
    }

    private boolean visible(ServerLevel level, Vec3 destination, BlockPos allowedHit) {
        if (!this.phantom.isLoadedFlightBox(new AABB(this.phantom.getEyePosition(), destination))) {
            return false;
        }
        var hit = level.clip(new ClipContext(this.phantom.getEyePosition(), destination,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.phantom));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(allowedHit);
    }

    public void save(ValueOutput output) {
        output.storeNullable("PhantomismHome", GlobalPos.CODEC, this.home);
        output.storeNullable("PhantomismGuardPosition", GlobalPos.CODEC, this.guardPosition);
        output.putString("PhantomismActivity", this.task.name());
        output.storeNullable("PhantomismActivityCenter", Vec3.CODEC, this.activityCenter);
        if (!this.cargo.isEmpty()) {
            output.store("PhantomismCargo", ItemStack.CODEC, this.cargo);
        }
    }

    public void load(ValueInput input) {
        this.home = input.read("PhantomismHome", GlobalPos.CODEC).orElse(null);
        this.guardPosition = input.read("PhantomismGuardPosition", GlobalPos.CODEC).orElse(null);
        try {
            this.task = Task.valueOf(input.read("PhantomismActivity", Codec.STRING).orElse(Task.NONE.name()));
        } catch (IllegalArgumentException exception) {
            this.task = Task.NONE;
        }
        this.activityCenter = input.read("PhantomismActivityCenter", Vec3.CODEC).orElse(this.phantom.position());
        this.cargo = input.read("PhantomismCargo", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.areaCombat = false;
        this.homeFlightCenter = null;
        this.nextHomeCheck = 0L;
        this.returningFromPatrolCombat = false;
        this.resetWork();
    }

    public enum Task {
        NONE,
        HARVEST,
        HUNT,
        COLLECT
    }
}
