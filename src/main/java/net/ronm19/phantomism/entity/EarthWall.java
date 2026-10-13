package net.ronm19.phantomism.entity;

import com.mojang.math.Transformation;
import java.util.Optional;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class EarthWall extends Display.BlockDisplay {
    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(EarthWall.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> OWNER = SynchedEntityData.defineId(
            EarthWall.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private long expiresAt;

    public EarthWall(EntityType<? extends EarthWall> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(CASTER, -1);
        data.define(OWNER, Optional.empty());
    }

    public void configure(EarthPhantom caster, Vec3 position, Vec3 normal, int duration) {
        this.entityData.set(CASTER, caster.getId());
        this.entityData.set(OWNER, Optional.ofNullable(caster.getOwnerReference()));
        this.expiresAt = this.level().getGameTime() + duration;
        float rotation = Math.abs(normal.x) > Math.abs(normal.z) ? (normal.x > 0.0D ? -90.0F : 90.0F)
                : (normal.z > 0.0D ? 0.0F : 180.0F);
        this.setYRot(rotation);
        this.setPos(position);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, this.registryAccess());
        output.store(Display.BlockDisplay.TAG_BLOCK_STATE, BlockState.CODEC, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        output.store(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC,
                new Transformation(new Vector3f(-1.5F, 0.0F, -0.275F), null, new Vector3f(3.0F, 3.0F, 0.55F), null));
        output.putFloat(Display.TAG_WIDTH, 4.0F);
        output.putFloat(Display.TAG_HEIGHT, 3.0F);
        super.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, this.registryAccess(), output.buildResult()));
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        boolean sideways = Math.abs(Math.sin(Math.toRadians(this.getYRot()))) > 0.5D;
        double halfX = sideways ? 0.275D : 1.5D;
        double halfZ = sideways ? 1.5D : 0.275D;
        return new AABB(position.x - halfX, position.y, position.z - halfZ,
                position.x + halfX, position.y + 3.0D, position.z + halfZ);
    }

    public boolean isFriendly(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof AbstractPhantom || entity.getId() == this.entityData.get(CASTER)) {
            return true;
        }
        var owner = this.entityData.get(OWNER).orElse(null);
        if (owner != null && (owner.getUUID().equals(entity.getUUID())
                || entity instanceof OwnableEntity companion && owner.equals(companion.getOwnerReference()))) {
            return true;
        }
        Entity caster = this.level().getEntity(this.entityData.get(CASTER));
        return caster != null && caster.isAlliedTo(entity);
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return this.isAlive() && !this.isFriendly(other) && !(other instanceof Projectile);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshDimensions();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity caster = level.getEntity(this.entityData.get(CASTER));
        if (level.getGameTime() >= this.expiresAt || caster == null || !caster.isAlive()) {
            this.discard();
            return;
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(4.0D),
                projectile -> projectile.isAlive() && !this.isFriendly(projectile.getOwner()))) {
            Vec3 movement = projectile.getDeltaMovement();
            Vec3 start = projectile.position().subtract(movement);
            Vec3 end = projectile.position().add(movement);
            if (this.getBoundingBox().inflate(projectile.getBbWidth() * 0.5D).clip(start, end).isPresent()
                    || this.getBoundingBox().intersects(projectile.getBoundingBox())) {
                projectile.discard();
            }
        }
    }
}
