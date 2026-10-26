package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.registry.PhantomismContent;

public final class ChristmasGift extends Display.BlockDisplay {
    private static final EntityDataAccessor<Boolean> GREEN = SynchedEntityData.defineId(ChristmasGift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> OPEN = SynchedEntityData.defineId(ChristmasGift.class, EntityDataSerializers.FLOAT);
    private UUID caster, session;
    private int age, opening;
    private long expires;
    public ChristmasGift(EntityType<? extends ChristmasGift> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(GREEN, false); data.define(OPEN, 0F); }
    public boolean green() { return entityData.get(GREEN); }
    public float openProgress() { return entityData.get(OPEN); }
    public static ChristmasGift create(ChristmasPhantom caster, Vec3 point, int duration, int opening, boolean green) {
        if (!(caster.level() instanceof ServerLevel level) || !caster.isLoadedFlightBox(new net.minecraft.world.phys.AABB(point, point).inflate(1))) { return null; }
        ChristmasGift gift = PhantomismContent.CHRISTMAS_GIFT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (gift == null) { return null; }
        gift.caster = caster.getUUID(); gift.session = caster.session(); gift.opening = opening; gift.expires = level.getGameTime() + Math.clamp(duration, 1, 124);
        gift.entityData.set(GREEN, green);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        output.putInt("teleport_duration", 2); output.putFloat(Display.TAG_WIDTH, 2); output.putFloat(Display.TAG_HEIGHT, 2);
        output.store("brightness", Brightness.CODEC, new Brightness(15, 15));
        gift.readDisplay(output); gift.setPos(point);
        return level.addFreshEntity(gift) ? gift : null;
    }
    private void readDisplay(TagValueOutput output) { super.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), output.buildResult())); }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (caster == null || level.getGameTime() >= expires || !(level.getEntity(caster) instanceof ChristmasPhantom source)
                    || !source.enabled() || !source.session().equals(session)) { discard(); return; }
            entityData.set(OPEN, Math.clamp((++age - opening) / 10F, 0, 1));
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); caster = null; expires = 0; }
}
