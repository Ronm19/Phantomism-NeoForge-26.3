package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

public final class IllagerFangs extends EvokerFangs {
    private UUID caster, session, cast;
    private int delay, lifetime = 60;
    public IllagerFangs(EntityType<? extends IllagerFangs> type, Level level) { super(type, level); }
    public void configure(IllagerPhantom source, UUID cast, UUID session, int delay) { caster = source.getUUID(); this.cast = cast; this.session = session; this.delay = Math.clamp(delay, 0, 12); }
    public UUID cast() { return cast; }
    public @Nullable IllagerPhantom caster() {
        return level() instanceof ServerLevel level && caster != null && level.getEntity(caster) instanceof IllagerPhantom source
                && source.enabled() && source.session().equals(session) && source.distanceToSqr(this) <= 40 * 40 ? source : null;
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel) { if (caster() == null || --lifetime <= 0) { discard(); return; } if (delay-- > 0) { return; } }
        super.tick();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); caster = null; session = null; lifetime = 0; }
}
