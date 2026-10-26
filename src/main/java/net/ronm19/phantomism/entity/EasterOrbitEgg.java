package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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

public final class EasterOrbitEgg extends Display.BlockDisplay {
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(EasterOrbitEgg.class, EntityDataSerializers.INT);
    private UUID caster, session;
    private long expires;
    public EasterOrbitEgg(EntityType<? extends EasterOrbitEgg> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(STYLE, 0); }
    public int style() { return entityData.get(STYLE); }
    public static EasterOrbitEgg create(EasterPhantom source, Vec3 point, int duration, int style) {
        if (!(source.level() instanceof ServerLevel level) || !source.isLoadedFlightBox(new net.minecraft.world.phys.AABB(point, point).inflate(1))) { return null; }
        EasterOrbitEgg egg = PhantomismContent.EASTER_ORBIT_EGG.get().create(level, EntitySpawnReason.TRIGGERED);
        if (egg == null) { return null; }
        egg.caster = source.getUUID(); egg.session = source.session(); egg.expires = level.getGameTime() + Math.clamp(duration, 1, 124);
        egg.entityData.set(STYLE, Math.clamp(style, 0, 3));
        var data = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        data.putInt("teleport_duration", 4); data.putFloat(Display.TAG_WIDTH, 2); data.putFloat(Display.TAG_HEIGHT, 2);
        egg.readDisplay(data); egg.setPos(point);
        return level.addFreshEntity(egg) ? egg : null;
    }
    private void readDisplay(TagValueOutput data) { super.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), data.buildResult())); }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && (caster == null || level.getGameTime() >= expires
                || !(level.getEntity(caster) instanceof EasterPhantom source) || !source.enabled() || !source.session().equals(session))) { discard(); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); caster = null; expires = 0; }
}
