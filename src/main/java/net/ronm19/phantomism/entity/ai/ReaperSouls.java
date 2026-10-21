package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ReaperPhantom;
import net.ronm19.phantomism.registry.PhantomismContent;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class ReaperSouls {
    private record Lease(WeakReference<ReaperPhantom> source,UUID session,long until,float factor){}
    private static final Map<LivingEntity,List<Lease>> FINALITY=new WeakHashMap<>(),ASSISTS=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> HEALED=new WeakHashMap<>(),HARVESTED=new WeakHashMap<>();
    private ReaperSouls(){}
    private static boolean live(LivingEntity target,Lease lease,boolean dead){ReaperPhantom source=lease.source().get();return source!=null&&source.enabled()&&source.session().equals(lease.session())&&source.level()==target.level()&&target.level().getGameTime()<lease.until()&&source.distanceToSqr(target)<=48*48&&(dead?source.eligibleSoul(target):source.validTarget(target));}
    public static void maintain(){for(var entries:List.of(FINALITY,ASSISTS)){var iterator=entries.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();entry.getValue().removeIf(lease->!live(entry.getKey(),lease,entries==ASSISTS));if(entry.getValue().isEmpty()){if(entries==FINALITY&&entry.getKey().isAlive()){entry.getKey().removeEffect(PhantomismContent.FINALITY);}iterator.remove();}}}HEALED.entrySet().removeIf(entry->!entry.getKey().isAlive()||entry.getKey().level().getGameTime()>=entry.getValue());HARVESTED.entrySet().removeIf(entry->entry.getKey().isRemoved()||entry.getKey().level().getGameTime()>=entry.getValue());}
    private static void lease(Map<LivingEntity,List<Lease>> map,ReaperPhantom source,LivingEntity target,int duration,float factor){if(map.size()>=256&&!map.containsKey(target)){return;}List<Lease> leases=map.computeIfAbsent(target,ignored->new ArrayList<>());leases.removeIf(previous->previous.source().get()==source&&previous.factor()>=factor||!live(target,previous,map==ASSISTS));if(leases.size()<8){leases.add(new Lease(new WeakReference<>(source),source.session(),target.level().getGameTime()+duration,factor));}}
    public static boolean finality(ReaperPhantom source,LivingEntity target,boolean strong){if(!source.enabled()||!source.validTarget(target)||source.distanceToSqr(target)>48*48||!target.canBeAffected(new MobEffectInstance(PhantomismContent.FINALITY,strong?120:60,strong?1:0))){return false;}target.addEffect(new MobEffectInstance(PhantomismContent.FINALITY,strong?120:60,strong?1:0),source);lease(FINALITY,source,target,strong?120:60,strong?.5F:.7F);return true;}
    public static float healingFactor(LivingEntity target){if(!target.hasEffect(PhantomismContent.FINALITY)){FINALITY.remove(target);return 1;}List<Lease> leases=FINALITY.get(target);if(leases==null){return 1;}leases.removeIf(lease->!live(target,lease,false));return leases.stream().map(Lease::factor).min(Float::compare).orElse(1F);}
    public static boolean regenerating(LivingEntity target){return target.hasEffect(MobEffects.REGENERATION)||HEALED.getOrDefault(target,Long.MIN_VALUE)>target.level().getGameTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void healed(LivingHealEvent event){if(!event.getEntity().level().isClientSide()&&event.getAmount()>0&&Float.isFinite(event.getAmount())&&(HEALED.size()<256||HEALED.containsKey(event.getEntity()))){HEALED.put(event.getEntity(),event.getEntity().level().getGameTime()+100);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event){if(event.getEntity() instanceof ReaperPhantom source&&source.bargainActive()&&event.getNewDamage()>0&&Float.isFinite(event.getNewDamage())&&!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)&&!event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)&&event.getSource().getEntity() instanceof LivingEntity attacker&&source.validTarget(attacker)){event.setNewDamage(event.getNewDamage()*.8F);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent.Post event){
        LivingEntity victim=event.getEntity();if(!(victim.level() instanceof ServerLevel level)||!Float.isFinite(event.getInflictedDamage())||event.getInflictedDamage()<=0){return;}
        if(event.getSource().getEntity() instanceof ReaperPhantom source&&source.enabled()){
            if(source.eligibleSoul(victim)&&event.getInflictedDamage()>=1&&!HARVESTED.containsKey(victim)&&HARVESTED.size()<512){lease(ASSISTS,source,victim,200,1);}
            if(victim.isAlive()&&source.validTarget(victim)){finality(source,victim,false);}
        }
        if(victim.isDeadOrDying()&&!HARVESTED.containsKey(victim)){
            List<Lease> assists=ASSISTS.remove(victim);if(assists!=null&&HARVESTED.size()<512){HARVESTED.put(victim,level.getGameTime()+600);for(Lease assist:assists){ReaperPhantom source=assist.source().get();if(source!=null&&live(victim,assist,true)){source.harvest(victim);}}}FINALITY.remove(victim);HEALED.remove(victim);
        }
        if(victim instanceof ReaperPhantom source&&source.isAlive()&&!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)&&event.getSource().getEntity() instanceof LivingEntity attacker){source.bargain(level,attacker);}
    }
    public static void release(ReaperPhantom source){for(var entries:List.of(FINALITY,ASSISTS)){var iterator=entries.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();entry.getValue().removeIf(lease->lease.source().get()==source);if(entry.getValue().isEmpty()){if(entries==FINALITY&&entry.getKey().isAlive()){entry.getKey().removeEffect(PhantomismContent.FINALITY);}iterator.remove();}}}}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof ReaperPhantom source){source.endReaper();}FINALITY.remove(event.getEntity());ASSISTS.remove(event.getEntity());HEALED.remove(event.getEntity());HARVESTED.remove(event.getEntity());}
}
