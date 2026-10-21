package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.VoidPhantom;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class VoidSuppression {
    public enum Kind { FIELD, GRASP, PULSE, SEAL, HORIZON }
    public interface Interruptible { void interruptVoidCast(int delayTicks); }
    private record Lease(WeakReference<VoidPhantom> source,UUID session,Kind kind,long until,long recovery,boolean resistant){}
    private static final Map<LivingEntity,Lease> LEASES=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> RECOVERY=new WeakHashMap<>();
    private static final Identifier SLOW=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"void_suppression");
    private VoidSuppression(){}
    private static boolean live(LivingEntity target,Lease lease){var source=lease.source().get();return source!=null&&source.enabled()&&source.session().equals(lease.session())&&source.level()==target.level()&&source.validTarget(target)&&source.distanceToSqr(target)<=64*64;}
    private static void removeModifier(LivingEntity target){for(var attribute:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){var instance=target.getAttribute(attribute);if(instance!=null){instance.removeModifier(SLOW);}}}
    public static void maintain(){var iterator=LEASES.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();var target=entry.getKey();var lease=entry.getValue();if(!live(target,lease)||target.level().getGameTime()>=lease.until()){removeModifier(target);iterator.remove();}}RECOVERY.entrySet().removeIf(entry->entry.getKey().isRemoved()||!entry.getKey().isAlive()||entry.getKey().level().getGameTime()>=entry.getValue());}
    public static boolean active(LivingEntity target){var lease=LEASES.get(target);return lease!=null&&live(target,lease)&&target.level().getGameTime()<lease.until();}
    public static boolean sealedBy(VoidPhantom source,LivingEntity target){var lease=LEASES.get(target);return lease!=null&&lease.source().get()==source&&lease.kind()==Kind.SEAL&&active(target);}
    public static boolean compatible(LivingEntity target){return target instanceof SpellcasterIllager caster&&caster.isCastingSpell()||target instanceof Interruptible||target instanceof CooldownDiscipline.Participant;}
    public static boolean apply(VoidPhantom source,LivingEntity target,Kind kind,int duration){
        maintain();if(!(source.level() instanceof ServerLevel)||!source.enabled()||!source.validTarget(target)||source.distanceToSqr(target)>64*64||!source.line(source.getEyePosition(),target.getEyePosition())||RECOVERY.containsKey(target)||RECOVERY.size()>=256){return false;}
        boolean resistant=!(target instanceof Mob mob)||GravityControl.movementImmune(mob)||target.isVehicle()||target.isPassenger();long now=source.level().getGameTime();int ticks=Math.clamp(duration,1,80);
        LEASES.put(target,new Lease(new WeakReference<>(source),source.session(),kind,now+ticks,now+ticks+80,resistant));
        RECOVERY.put(target,now+ticks+80);
        double slow=resistant?0:kind==Kind.GRASP?.95D:kind==Kind.SEAL?.3D:kind==Kind.HORIZON?.2D:.1D;
        if(slow>0){for(var attribute:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){var instance=target.getAttribute(attribute);if(instance!=null){instance.addTransientModifier(new AttributeModifier(SLOW,-slow,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}}
        if(!resistant&&kind!=Kind.FIELD){
            if(target instanceof SpellcasterIllager caster&&caster.isCastingSpell()){for(var goal:List.copyOf(caster.getGoalSelector().getAvailableGoals())){if(goal.isRunning()){goal.stop();}}}
            if(target instanceof Interruptible caster){caster.interruptVoidCast(20);}
            if(target instanceof CooldownDiscipline.Participant participant){for(var slot:participant.temporalCooldowns().stream().limit(24).toList()){if(CooldownDiscipline.eligible(slot)){int remaining=slot.remaining().getAsInt();slot.update().accept(Math.clamp(Math.max(remaining,20),0,slot.duration()));}}}
        }
        return true;
    }
    public static float offense(LivingEntity target){if(!active(target)){return 1;}var lease=LEASES.get(target);return lease.resistant()?.95F:lease.kind()==Kind.SEAL?.75F:.85F;}
    public static boolean pull(VoidPhantom source,LivingEntity target,Vec3 center){
        if(!(source.level() instanceof ServerLevel level)||!source.validTarget(target)||!(target instanceof Mob mob)||GravityControl.movementImmune(mob)||mob.isPassenger()||mob.isVehicle()){return false;}
        double resistance=mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);Vec3 step=center.subtract(target.position()).multiply(1,0,1).normalize().scale(.15D*Math.clamp(1-resistance,0,1));var box=mob.getBoundingBox().expandTowards(step);var floor=net.minecraft.core.BlockPos.containing(mob.position().add(step)).below();if(step.lengthSqr()<.00001D||!EndTeleportSafety.loaded(level,box,false)||!level.noCollision(mob,box)||level.containsAnyLiquid(box)||GravityControl.dangerous(level,floor)||!mob.isNoGravity()&&level.getBlockState(floor).getCollisionShape(level,floor).isEmpty()){return false;}mob.setDeltaMovement(mob.getDeltaMovement().add(step));mob.needsSync=true;return true;
    }
    public static void release(VoidPhantom source){var iterator=LEASES.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(entry.getValue().source().get()==source){removeModifier(entry.getKey());iterator.remove();}}}
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event){if(event.getEntity().level().isClientSide()){return;}if(event.getEntity() instanceof VoidPhantom source&&source.level() instanceof ServerLevel level){source.rescue().tick(level);}if(event.getEntity() instanceof LivingEntity target){var lease=LEASES.get(target);if(lease!=null){if(!active(target)){maintain();}else if(!lease.resistant()&&lease.kind()==Kind.GRASP){target.setDeltaMovement(target.getDeltaMovement().multiply(.05D,1,.05D));target.needsSync=true;}}}}
    @SubscribeEvent public static void teleport(EntityTeleportEvent.EnderEntity event){if(event.getEntity() instanceof Mob target&&active(target)&&!LEASES.get(target).resistant()){event.setCanceled(true);}}
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event){if(event.getEntity() instanceof VoidPhantom source&&event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)&&source.level() instanceof ServerLevel level){source.rescue().tick(level);event.setCanceled(true);}}
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event){
        var target=event.getEntity();if(!(target.level() instanceof ServerLevel level)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        if(event.getSource().getEntity() instanceof LivingEntity attacker){event.setNewDamage(event.getNewDamage()*offense(attacker));if(!PhantomDuels.pair(target,attacker)){float protection=0;for(var source:level.getEntitiesOfClass(VoidPhantom.class,target.getBoundingBox().inflate(32),candidate->candidate.enabled()&&candidate.validTarget(attacker)&&candidate.recipient(target))){if(event.getNewDamage()>=4){source.shield(level,target);}protection=Math.max(protection,source.mitigation(target));}if(protection>0){event.setNewDamage(event.getNewDamage()*(1-protection));}}}
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof VoidPhantom source){source.endVoid();source.rescue().reset();}if(event.getEntity() instanceof LivingEntity target){removeModifier(target);LEASES.remove(target);RECOVERY.remove(target);}}
}
