package net.ronm19.phantomism.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MoonLance extends ThrowableItemProjectile {
    private final Set<UUID> hits = new HashSet<>();
    private UUID session;
    private boolean ultimate;
    private double travelled = .7D;
    private int life;
    public MoonLance(EntityType<? extends MoonLance> type, Level level) { super(type, level); }
    public void configure(LunarPhantom source, UUID session, boolean ultimate) { setOwner(source); this.session = session; this.ultimate = ultimate; }
    @Override protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && !hits.contains(entity.getUUID()) && getOwner() instanceof LunarPhantom source && entity instanceof LivingEntity target && source.validTarget(target); }
    @Override public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof LunarPhantom source) || !source.enabled() || !source.session().equals(session) || ++life > 24 || travelled >= LunarPhantom.LANCE_RANGE - .000001D || ultimate && source.ultimateTicks() == 0) { discard(); return; }
            if (getDeltaMovement().length() > LunarPhantom.LANCE_RANGE - travelled) { setDeltaMovement(getDeltaMovement().normalize().scale(LunarPhantom.LANCE_RANGE - travelled)); }
            if (!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))) { discard(); return; }
        }
        Vec3 previous = position(); super.tick(); travelled += position().distanceTo(previous);
    }
    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) { return; }
        if (!(getOwner() instanceof LunarPhantom source) || !source.session().equals(session)) { discard(); return; }
        if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target && hits.add(target.getUUID())) {
            source.lanceImpact(level, target, ultimate, hit.getLocation()); if (hits.size() >= 3) { discard(); }
        } else if (hit.getType() == HitResult.Type.BLOCK) { discard(); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); session = null; travelled = LunarPhantom.LANCE_RANGE; }
}
