package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

public final class IllagerSpectralVex extends Vex {
    private UUID caster, session;
    private @Nullable EntityReference<LivingEntity> protectedOwner;
    private int lifetime;
    private boolean conquest;
    public IllagerSpectralVex(EntityType<? extends IllagerSpectralVex> type, Level level) { super(type, level); }
    @Override protected void registerGoals() { super.registerGoals(); targetSelector.removeAllGoals(goal -> true); }
    public void configure(IllagerPhantom source, boolean conquest, int duration) {
        caster = source.getUUID(); session = source.session(); this.conquest = conquest; lifetime = Math.clamp(duration, 1, 400);
        LivingEntity owner = source.getOwner() == null ? source : source.getOwner(); protectedOwner = EntityReference.of(owner);
        setPersistenceRequired();
    }
    @Override public @Nullable EntityReference<LivingEntity> getOwnerReference() { return protectedOwner; }
    public @Nullable IllagerPhantom caster() {
        return level() instanceof ServerLevel level && caster != null && level.getEntity(caster) instanceof IllagerPhantom source
                && source.enabled() && source.session().equals(session) && source.distanceToSqr(this) <= 48 * 48 ? source : null;
    }
    public boolean conquestSummon() { return conquest; }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            IllagerPhantom source = caster();
            if (source == null || --lifetime < 0 || conquest && source.conquestTicks() <= 0) { discard(); return; }
            var target = source.summonTarget(); if (target != null && distanceToSqr(target) > 32 * 32) { target = null; }
            if (getTarget() != target) { setTarget(target); }
        }
        super.tick();
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { IllagerPhantom source = caster(); return source != null && target instanceof LivingEntity living && source.validTarget(living) && distanceToSqr(living) <= 4 && source.vexStrike(level, living, conquest); }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); lifetime = 0; caster = null; session = null; }
}
