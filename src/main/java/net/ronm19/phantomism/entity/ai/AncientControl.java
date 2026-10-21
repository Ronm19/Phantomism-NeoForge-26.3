package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.AncientPhantom;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class AncientControl {
    private record Stun(WeakReference<AncientPhantom> source,UUID session,long until,long recovery){}
    private static final Map<Mob,Stun> STUNS=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> HEALS=new WeakHashMap<>();
    private AncientControl(){}
    public static boolean controlImmune(LivingEntity target){return !(target instanceof Mob mob)||GravityControl.movementImmune(mob)||target.getType()==EntityTypes.WITHER||target.getType()==EntityTypes.ENDER_DRAGON;}
    private static boolean valid(Mob target,Stun stun){AncientPhantom source=stun.source().get();return source!=null&&source.enabled()&&source.session().equals(stun.session())&&source.validTarget(target)&&source.level()==target.level()&&source.distanceToSqr(target)<=64*64;}
    public static void maintain(){STUNS.entrySet().removeIf(entry->!valid(entry.getKey(),entry.getValue())||entry.getKey().level().getGameTime()>=entry.getValue().recovery());HEALS.entrySet().removeIf(entry->!entry.getKey().isAlive()||entry.getKey().isRemoved()||entry.getKey().level().getGameTime()>=entry.getValue());}
    public static boolean stun(AncientPhantom source,LivingEntity target,int ticks){maintain();if(!source.enabled()||!source.validTarget(target)||controlImmune(target)||!(target instanceof Mob mob)||STUNS.containsKey(mob)||STUNS.size()>=256){return false;}long now=source.level().getGameTime();STUNS.put(mob,new Stun(new WeakReference<>(source),source.session(),now+Math.clamp(ticks,1,20),now+60));return true;}
    public static boolean stunned(LivingEntity target){if(!(target instanceof Mob mob)){return false;}Stun stun=STUNS.get(mob);return stun!=null&&valid(mob,stun)&&mob.level().getGameTime()<stun.until();}
    public static boolean heal(AncientPhantom source,LivingEntity target){if(!source.enabled()||!source.ally(target)||target.getHealth()>=target.getMaxHealth()||HEALS.getOrDefault(target,Long.MIN_VALUE)>target.level().getGameTime()||HEALS.size()>=256&&!HEALS.containsKey(target)){return false;}float before=target.getHealth();target.heal(1);if(target.getHealth()<=before){return false;}HEALS.put(target,target.level().getGameTime()+40);return true;}
    public static void coordinate(AncientPhantom source,LivingEntity target){if(!source.enabled()||!source.isTame()||!source.validTarget(target)||target instanceof OwnableEntity){return;}for(AbstractPhantom ally:source.level().getEntitiesOfClass(AbstractPhantom.class,source.getBoundingBox().inflate(28),source::crew).stream().filter(ally->ally!=source&&!ally.isBaby()&&!ally.isVehicle()&&!ally.isPassenger()&&ally.getOrder()==AbstractPhantom.Order.FOLLOW&&ally.getTask()==PhantomAssignments.Task.NONE&&ally.getTarget()==null&&!PhantomDuels.excludesObserver(ally,target)).limit(8).toList()){ally.defendFamily(target);}}
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event){if(!event.getEntity().level().isClientSide()&&event.getEntity() instanceof LivingEntity target&&stunned(target)){target.setDeltaMovement(Vec3.ZERO);target.needsSync=true;event.setCanceled(true);}}
    @SubscribeEvent public static void attack(LivingIncomingDamageEvent event){if(event.getSource().getEntity() instanceof LivingEntity attacker&&stunned(attacker)){event.setCanceled(true);}}
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event){
        LivingEntity target=event.getEntity();if(!(target.level() instanceof ServerLevel level)||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        if(event.getSource().getEntity() instanceof AbstractPhantom attacker&&!level.getEntitiesOfClass(AncientPhantom.class,attacker.getBoundingBox().inflate(64),source->source.empowers(attacker,target)).isEmpty()){event.setNewDamage(event.getNewDamage()*1.1F);}
        if(event.getSource().getEntity() instanceof LivingEntity attacker&&!PhantomDuels.pair(target,attacker)){
            boolean protectedTarget=!level.getEntitiesOfClass(AncientPhantom.class,target.getBoundingBox().inflate(64),source->source.protects(target)&&source.safeEnemy(attacker)).isEmpty();if(protectedTarget){event.setNewDamage(event.getNewDamage()*.8F);}
        }
    }
    public static void release(AncientPhantom source){STUNS.entrySet().removeIf(entry->entry.getValue().source().get()==source);}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof AncientPhantom source){source.endAncient();}STUNS.remove(event.getEntity());HEALS.remove(event.getEntity());}
}
