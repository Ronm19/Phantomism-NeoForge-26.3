package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.*;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class MirageIllusions {
    public static final int OWNER_LIMIT = 12, SERVER_LIMIT = 192;
    private static final Map<MinecraftServer, Map<UUID, WeakReference<MirageDecoy>>> DECOYS = new WeakHashMap<>();
    private static final Map<Mob, Claim> CLAIMS = new WeakHashMap<>();
    private static final Set<String> COMPATIBLE = Set.of("zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "wither_skeleton", "spider", "cave_spider", "pillager", "vindicator", "evoker", "witch", "silverfish", "endermite");
    private record Claim(WeakReference<MirageDecoy> decoy, WeakReference<LivingEntity> original, long until) {}
    private MirageIllusions() {}

    private static Map<UUID, WeakReference<MirageDecoy>> registry(ServerLevel level) {
        var entries = DECOYS.computeIfAbsent(level.getServer(), ignored -> new LinkedHashMap<>());
        entries.entrySet().removeIf(entry -> entry.getValue().get() == null || !entry.getValue().get().isAlive() || entry.getValue().get().isRemoved());
        return entries;
    }
    public static List<MirageDecoy> personal(MiragePhantom source) {
        if (!(source.level() instanceof ServerLevel level)) { return List.of(); }
        return registry(level).values().stream().map(WeakReference::get).filter(Objects::nonNull)
                .filter(copy -> copy.casterId().equals(source.getUUID()) && copy.session().equals(source.session())).toList();
    }
    public static MirageDecoy create(MiragePhantom source, AbstractPhantom disguise, MirageDecoy.Mode mode, Vec3 position, int duration) {
        if (!(source.level() instanceof ServerLevel level) || !source.enabled() || !source.authorizedDisguise(disguise)
                || !source.safePlacement(position, .95D, .8D) || personal(source).size() >= source.decoyLimit()) { return null; }
        var entries = registry(level);
        if (entries.size() >= SERVER_LIMIT || entries.values().stream().map(WeakReference::get).filter(Objects::nonNull).filter(copy -> copy.budgetOwner().equals(source.budgetOwner())).count() >= OWNER_LIMIT) { return null; }
        MirageDecoy copy = PhantomismContent.MIRAGE_DECOY.get().create(level, EntitySpawnReason.TRIGGERED);
        if (copy == null) { return null; }
        copy.configure(source, disguise, mode, position, Math.clamp(duration, 10, 240), personal(source).size());
        if (!level.addFreshEntity(copy)) { return null; }
        entries.put(copy.getUUID(), new WeakReference<>(copy));
        return copy;
    }
    public static void trim(MiragePhantom source, int maximum) {
        List<MirageDecoy> copies = personal(source);
        for (int index = maximum; index < copies.size(); index++) { copies.get(index).shatter(); }
    }
    public static void forget(MirageDecoy copy) {
        if (copy.level() instanceof ServerLevel level) { registry(level).remove(copy.getUUID()); }
        for (var entry : List.copyOf(CLAIMS.entrySet())) {
            Mob mob = entry.getKey(); Claim claim = entry.getValue();
            if (claim.decoy.get() != copy) { continue; }
            if (mob.isAlive() && !mob.isRemoved() && mob.getTarget() == copy) {
                LivingEntity original = claim.original.get();
                mob.setTarget(original != null && original.isAlive() && !original.isRemoved() && original.level() == mob.level() ? original : null);
                if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                    if (mob.getTarget() == null) { mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); }
                    else { mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, mob.getTarget()); }
                }
            }
            if (mob.isAlive() && !mob.isRemoved() && mob.level().getGameTime() < claim.until) {
                CLAIMS.put(mob, new Claim(new WeakReference<>(null), claim.original, claim.until));
            } else { CLAIMS.remove(mob); }
        }
    }
    public static boolean compatible(Mob mob) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id.getNamespace().equals("minecraft") && COMPATIBLE.contains(id.getPath()) && !(mob instanceof OwnableEntity);
    }
    public static boolean redirect(MiragePhantom source, Mob mob, MirageDecoy copy, boolean rescue) {
        if (!source.enabled() || !compatible(mob) || !source.validTarget(mob) || !copy.validFor(source) || source.distanceToSqr(mob) > 24 * 24
                || mob.distanceToSqr(copy) > 24 * 24 || !source.line(mob.getEyePosition(), copy.getEyePosition())) { return false; }
        LivingEntity original = mob.getTarget();
        if (original instanceof MirageDecoy) { return false; }
        if (!source.protects(original) || PhantomDuels.pair(mob, original)) { return false; }
        long now = source.level().getGameTime();
        Claim existing = CLAIMS.get(mob);
        if (existing != null && now < existing.until) { return false; }
        CLAIMS.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getKey().level().getGameTime() >= entry.getValue().until + 100);
        if (CLAIMS.size() >= 512) { return false; }
        double chance = .35D + (copy.mode() == MirageDecoy.Mode.PHYSICAL ? .15D : 0) + (source.inField(mob.position()) ? .25D : 0)
                + (mob.distanceToSqr(copy) < mob.distanceToSqr(original) ? .15D : 0) - (mob.getLastHurtByMob() == original ? .15D : 0);
        CLAIMS.put(mob, new Claim(new WeakReference<>(copy), new WeakReference<>(original), now + 60));
        if (!rescue && source.getRandom().nextDouble() >= Math.clamp(chance, .15D, .85D)) { return false; }
        mob.setTarget(copy);
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) { mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, copy); }
        return mob.getTarget() == copy;
    }
    public static boolean distracted(MiragePhantom source, LivingEntity enemy) {
        return enemy instanceof Mob mob && mob.getTarget() instanceof MirageDecoy copy && copy.validFor(source);
    }
    public static boolean eligibleProjectile(MiragePhantom source, Projectile projectile) {
        return projectile instanceof AbstractArrow && (projectile.getType() == EntityTypes.ARROW || projectile.getType() == EntityTypes.SPECTRAL_ARROW) && projectile.isAlive() && projectile.getOwner() instanceof LivingEntity attacker
                && source.validTarget(attacker) && !PhantomDuels.excludesObserver(source, attacker) && projectile.getDeltaMovement().lengthSqr() > .0025D;
    }
    @SubscribeEvent public static void projectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof MirageDecoy copy) {
            MiragePhantom source = copy.caster();
            if (source == null) { return; }
            if (event.getProjectile().getOwner() instanceof LivingEntity owner && source.ally(owner)) { event.setCanceled(true); }
            else if (copy.mode() == MirageDecoy.Mode.VISUAL) { copy.shatter(); event.setCanceled(true); }
        }
    }
    @SubscribeEvent public static void incoming(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof MirageDecoy || event.getNewDamage() <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || PhantomDuels.pair(victim, attacker)) { return; }
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity) && !(event.getSource().getDirectEntity() instanceof AbstractArrow)) { return; }
        if (event.getNewDamage() >= 6 || victim.getHealth() < victim.getMaxHealth() * .4F) {
            for (MiragePhantom source : level.getEntitiesOfClass(MiragePhantom.class, victim.getBoundingBox().inflate(20), candidate -> candidate != victim && candidate.enabled() && candidate.protects(victim)).stream().sorted(Comparator.comparingDouble(victim::distanceToSqr)).limit(4).toList()) {
                if (source.validTarget(attacker) && source.rescue(level, victim, attacker)) { break; }
            }
        }
        for (MirageDecoy copy : level.getEntitiesOfClass(MirageDecoy.class, victim.getBoundingBox().inflate(4), candidate -> candidate.mode() == MirageDecoy.Mode.PHYSICAL).stream().sorted(Comparator.comparingDouble(victim::distanceToSqr)).limit(12).toList()) {
            MiragePhantom caster = copy.caster();
            if (caster == null || !copy.validFor(caster) || !caster.protects(victim) || !caster.validTarget(attacker)
                    || !caster.line(attacker.getEyePosition(), victim.getEyePosition()) || !caster.line(copy.getEyePosition(), victim.getEyePosition())) { continue; }
            Vec3 start = event.getSource().getDirectEntity() instanceof Projectile shot ? shot.position().subtract(shot.getDeltaMovement()) : attacker.getEyePosition();
            if (copy.getBoundingBox().inflate(.45D).clip(start, victim.getBoundingBox().getCenter()).isEmpty()) { continue; }
            float absorbed = copy.absorb(event.getNewDamage());
            if (absorbed > 0) { event.setNewDamage(Math.max(0, event.getNewDamage() - absorbed)); return; }
        }
    }
}
