package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ShadowPhantom;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class ShadowStealth {
    private record Lease(WeakReference<ShadowPhantom> source, UUID session, long until, long retry) {}
    private static final Map<Mob, Lease> LEASES = new WeakHashMap<>();
    private static final Set<String> COMPATIBLE = Set.of("zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "wither_skeleton", "spider", "cave_spider", "pillager", "vindicator", "evoker", "witch", "silverfish", "endermite");
    private ShadowStealth() {}
    public static boolean compatible(Mob mob) { var id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()); return id.getNamespace().equals("minecraft") && COMPATIBLE.contains(id.getPath()) && !(mob instanceof OwnableEntity); }
    private static boolean live(Mob observer, Lease lease) { var source=lease.source().get(); return source!=null && source.enabled() && source.session().equals(lease.session()) && source.level()==observer.level() && observer.isAlive() && !observer.isRemoved() && observer.level().getGameTime()<lease.retry(); }
    public static void maintain() { LEASES.entrySet().removeIf(entry->!live(entry.getKey(),entry.getValue())); }
    public static void disrupt(ShadowPhantom source, ServerLevel level) {
        if (!source.enabled()) { return; } maintain();
        for (Mob mob : level.getEntitiesOfClass(Mob.class,source.getBoundingBox().inflate(24),target->target.getTarget()==source&&source.concealsFrom(target)).stream().sorted(Comparator.comparingDouble(source::distanceToSqr)).limit(8).toList()) {
            long now=level.getGameTime(); if(LEASES.containsKey(mob)||LEASES.size()>=256){continue;}
            LEASES.put(mob,new Lease(new WeakReference<>(source),source.session(),now+20,now+100)); mob.setTarget(null);
            if(mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)&&mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)==source){mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);}
        }
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !(event.getNewAboutToBeSetTarget() instanceof ShadowPhantom source) || !source.concealsFrom(mob)) { return; }
        Lease lease=LEASES.get(mob); boolean disrupted=lease!=null&&live(mob,lease)&&lease.source().get()==source&&mob.level().getGameTime()<lease.until();
        double distance=source.distanceToSqr(mob), range=Math.max(4,mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)*.5D);
        if(disrupted || distance>range*range){event.setNewAboutToBeSetTarget(null);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ShadowPhantom source) || !(source.level() instanceof ServerLevel level) || !Float.isFinite(event.getNewDamage()) || event.getNewDamage()<=0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE) || !(event.getSource().getEntity() instanceof LivingEntity attacker) || !source.validTarget(attacker)) { return; }
        source.guard(level,attacker,event.getNewDamage()); float incoming=event.getNewDamage();float floor=Math.min(incoming,event.getOriginalDamage()*.4F);
        event.setNewDamage(Math.max(floor,incoming*(1-source.guardMitigation())));
    }
    public static void release(ShadowPhantom source) { LEASES.entrySet().removeIf(entry->entry.getValue().source().get()==source); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) { if(event.getLevel().isClientSide()){return;} LEASES.remove(event.getEntity());if(event.getEntity() instanceof ShadowPhantom source){source.endShadow();} }
}
