package net.ronm19.phantomism.entity.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.IllagerFangs;
import net.ronm19.phantomism.entity.IllagerPhantom;

@EventBusSubscriber(modid = "phantomism")
public final class IllagerCharm {
    public static final int RADIUS = 24, MAX_RAIDERS = 256, MAX_PER_REFRESH = 16;
    private record Lease(UUID source, UUID session, UUID owner, long until) {}
    private static final Map<Raider, List<Lease>> LEASES = new WeakHashMap<>();
    private IllagerCharm() {}
    public static boolean eligible(Mob mob) {
        return mob instanceof Raider && !(mob instanceof AbstractPhantom) && mob.isAlive() && !mob.isRemoved()
                && BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft");
    }
    private static IllagerPhantom source(Raider mob, Lease lease) {
        if (!(mob.level() instanceof ServerLevel level) || !eligible(mob) || level.getGameTime() >= lease.until
                || !(level.getEntity(lease.source) instanceof IllagerPhantom source) || !source.canCharm()
                || !source.session().equals(lease.session) || !source.getOwnerReference().getUUID().equals(lease.owner)
                || source.distanceToSqr(mob) > RADIUS * RADIUS || hostile(source, mob)) { return null; }
        return source;
    }
    public static boolean hostile(IllagerPhantom source, Mob mob) {
        if (!eligible(mob) || source.getOwnerReference() == null) { return false; }
        var broken = mob.getPersistentData().getCompoundOrEmpty("PhantomismIllagerCharmBroken");
        return source.getTarget() == mob || broken.getLongOr(source.getOwnerReference().getUUID().toString(), 0) > mob.level().getGameTime();
    }
    public static void breakFor(IllagerPhantom source, LivingEntity target) {
        if (!(target instanceof Raider raider) || !eligible(raider) || source.getOwnerReference() == null) { return; }
        var broken = raider.getPersistentData().getCompoundOrEmpty("PhantomismIllagerCharmBroken");
        long now = raider.level().getGameTime();
        for (String key : List.copyOf(broken.keySet())) { if (broken.getLongOr(key, 0) <= now) { broken.remove(key); } }
        String owner = source.getOwnerReference().getUUID().toString();
        if (broken.size() < 8 || broken.contains(owner)) { broken.putLong(owner, now + 600); }
        raider.getPersistentData().put("PhantomismIllagerCharmBroken", broken);
        List<Lease> leases = LEASES.get(raider); if (leases != null) { leases.removeIf(lease -> lease.owner.toString().equals(owner)); if (leases.isEmpty()) { LEASES.remove(raider); } }
    }
    public static void refresh(IllagerPhantom source) {
        if (!(source.level() instanceof ServerLevel level) || !source.canCharm()) { return; }
        source.charmStarted();
        var raiders = level.getEntitiesOfClass(Raider.class, source.getBoundingBox().inflate(RADIUS), IllagerCharm::eligible).stream()
                .filter(mob -> source.distanceToSqr(mob) <= RADIUS * RADIUS).sorted(java.util.Comparator.comparingDouble(source::distanceToSqr)).limit(MAX_PER_REFRESH).toList();
        for (Raider raider : raiders) {
            if (hostile(source, raider)) { continue; }
            List<Lease> leases = LEASES.get(raider);
            if (leases == null) { if (LEASES.size() >= MAX_RAIDERS) { continue; } leases = new ArrayList<>(); LEASES.put(raider, leases); }
            UUID owner = source.getOwnerReference().getUUID(); leases.removeIf(lease -> lease.owner.equals(owner) || source(raider, lease) == null);
            if (leases.size() < 4) { leases.add(new Lease(source.getUUID(), source.session(), owner, level.getGameTime() + 30)); }
            enforce(raider);
            if (raider.tickCount % 40 == 0) { level.sendParticles(ParticleTypes.HAPPY_VILLAGER, raider.getX(), raider.getEyeY() + .2D, raider.getZ(), 1, .1D, .1D, .1D, 0); }
        }
    }
    public static boolean protectedFrom(Raider raider, LivingEntity target) {
        if (target == null) { return false; }
        List<Lease> leases = LEASES.get(raider); if (leases == null) { return false; }
        for (Lease lease : List.copyOf(leases)) { var source = source(raider, lease); if (source != null && source.recipient(target)) { return true; } }
        return false;
    }
    private static void enforce(Raider raider) {
        if (protectedFrom(raider, raider.getTarget())) { raider.setTarget(null); raider.getNavigation().stop(); }
        var brain = raider.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && protectedFrom(raider, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) { brain.eraseMemory(MemoryModuleType.ATTACK_TARGET); }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Raider raider) || raider.level().isClientSide()) { return; }
        List<Lease> leases = LEASES.get(raider); if (leases == null) { return; }
        leases.removeIf(lease -> source(raider, lease) == null);
        if (leases.isEmpty()) { LEASES.remove(raider); } else { enforce(raider); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Raider raider && protectedFrom(raider, event.getNewAboutToBeSetTarget())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void harmed(LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0 || !(event.getSource().getEntity() instanceof Raider attacker)) { return; }
        List<Lease> leases = LEASES.get(attacker); if (leases == null) { return; }
        for (Lease lease : List.copyOf(leases)) { IllagerPhantom source = source(attacker, lease); if (source != null && source.recipient(event.getEntity())) { source.retaliate(attacker); } }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void fangs(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof IllagerFangs fang)) { return; }
        IllagerPhantom source = fang.caster(); float amount = source == null ? 0 : source.fangAmount(fang.cast(), event.getEntity());
        if (amount <= 0) { event.setCanceled(true); } else { event.setAmount(amount); }
    }
    public static void release(IllagerPhantom source) {
        for (var entry : List.copyOf(LEASES.entrySet())) { entry.getValue().removeIf(lease -> lease.source.equals(source.getUUID())); if (entry.getValue().isEmpty()) { LEASES.remove(entry.getKey()); } }
    }
}
