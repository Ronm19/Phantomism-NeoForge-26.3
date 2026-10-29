package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ValentinesPhantom;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class ValentinesSupport {
    private record Lease(WeakReference<ValentinesPhantom> caster,UUID session,long expires) {}
    private static final Map<LivingEntity,Lease> BONDS=new WeakHashMap<>(),WARDS=new WeakHashMap<>(),CHARMS=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> HEAL_REST=new WeakHashMap<>();
    private ValentinesSupport() {}
    private static ValentinesPhantom source(LivingEntity target,Lease lease,boolean combat,boolean hostile) {
        var caster=lease==null?null:lease.caster().get();
        return caster==null || !caster.companionAvailable() || combat&&!caster.enabled() || !caster.session().equals(lease.session())
            || caster.level()!=target.level() || !target.isAlive() || target.isRemoved() || target.level().getGameTime()>=lease.expires()
            || caster.distanceToSqr(target)>144 || !caster.line(caster.getEyePosition(),target.getEyePosition())
            || (hostile?!caster.validTarget(target):!caster.ally(target)) ? null : caster;
    }
    private static Lease lease(ValentinesPhantom caster,int ticks){return new Lease(new WeakReference<>(caster),caster.session(),caster.level().getGameTime()+ticks);}
    public static void bond(ValentinesPhantom caster,LivingEntity target) {
        if(!caster.isTame() || !caster.companionAvailable() || !caster.ally(target) || caster.distanceToSqr(target)>144 || !caster.line(caster.getEyePosition(),target.getEyePosition())){return;}
        var previous=BONDS.get(target);if(source(target,previous,false,false)!=null && previous.caster().get()!=caster){return;}
        BONDS.put(target,lease(caster,30));
    }
    public static boolean bonded(LivingEntity target){return source(target,BONDS.get(target),false,false)!=null;}
    public static void protect(ValentinesPhantom caster,LivingEntity target) {
        if(!caster.enabled() || caster.auraTicks()<=0 || !caster.ally(target) || caster.distanceToSqr(target)>144){return;}
        var previous=WARDS.get(target);if(source(target,previous,true,false)!=null && previous.caster().get()!=caster){return;}
        WARDS.put(target,lease(caster,Math.min(15,caster.auraTicks())));
    }
    public static boolean protectedByAura(LivingEntity target){var caster=source(target,WARDS.get(target),true,false);return caster!=null && caster.auraTicks()>0;}
    public static boolean heal(ValentinesPhantom caster,LivingEntity target,float amount) {
        long now=target.level().getGameTime();
        if(!caster.enabled() || !caster.ally(target) || caster.distanceToSqr(target)>400 || target.getHealth()>=target.getMaxHealth()
            || !caster.line(caster.getEyePosition(),target.getEyePosition()) || HEAL_REST.getOrDefault(target,Long.MIN_VALUE)>now){return false;}
        float before=target.getHealth();target.heal(Math.clamp(amount,0,4));
        if(target.getHealth()<=before){return false;}HEAL_REST.put(target,now+35);return true;
    }
    public static boolean needsCare(LivingEntity target){return target.getHealth()<target.getMaxHealth() || target.hasEffect(MobEffects.POISON) || target.hasEffect(MobEffects.WEAKNESS) || target.hasEffect(MobEffects.SLOWNESS);}
    public static boolean cleanse(ValentinesPhantom caster,LivingEntity target) {
        if(!caster.enabled() || !caster.ally(target) || caster.distanceToSqr(target)>144 || !caster.line(caster.getEyePosition(),target.getEyePosition())){return false;}
        boolean helped=false;
        for(var effect:List.of(MobEffects.POISON,MobEffects.WEAKNESS,MobEffects.SLOWNESS)){helped|=target.removeEffect(effect);}
        return helped;
    }
    public static void distract(ValentinesPhantom caster,LivingEntity target,int duration) {
        if(!caster.enabled() || !caster.validTarget(target) || caster.distanceToSqr(target)>144){return;}
        boolean boss=target instanceof Mob mob&&GravityControl.movementImmune(mob);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,boss?20:duration,0),caster);
        if(!(target instanceof Mob mob)||boss){return;}
        var previous=CHARMS.get(target);if(source(target,previous,true,true)!=null&&previous.caster().get()!=caster){return;}
        CHARMS.put(target,lease(caster,Math.clamp(duration,1,60)));enforce(mob);
    }
    public static boolean distractedFrom(Mob mob,LivingEntity target){var caster=source(mob,CHARMS.get(mob),true,true);return caster!=null&&caster.ally(target);}
    private static void enforce(Mob mob) {
        if(distractedFrom(mob,mob.getTarget())){mob.setTarget(null);mob.getNavigation().stop();}
        var brain=mob.getBrain();
        if(brain.checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED)&&distractedFrom(mob,brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))){brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);}
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(!(event.getEntity() instanceof LivingEntity target)||!(target.level() instanceof ServerLevel)){return;}
        if(BONDS.containsKey(target)&&!bonded(target)){BONDS.remove(target);}
        if(WARDS.containsKey(target)&&!protectedByAura(target)){WARDS.remove(target);}
        if(CHARMS.containsKey(target)){
            if(source(target,CHARMS.get(target),true,true)==null){CHARMS.remove(target);}
            else if(target instanceof Mob mob&&target.tickCount%5==0){enforce(mob);}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void targeting(LivingChangeTargetEvent event){if(event.getEntity() instanceof Mob mob&&distractedFrom(mob,event.getNewAboutToBeSetTarget())){event.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target=event.getEntity();
        if(!(target.level() instanceof ServerLevel)||event.getNewDamage()<=0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        LivingEntity attacker=event.getSource().getEntity() instanceof LivingEntity living?living:null;
        var ward=source(target,WARDS.get(target),true,false);var bond=source(target,BONDS.get(target),false,false);
        float multiplier=1;
        if(ward!=null && ward.auraTicks()>0 && (attacker!=null&&ward.safeEnemy(attacker)||event.getSource().is(DamageTypeTags.IS_EXPLOSION))){multiplier=.8F;}
        else if(bond!=null && attacker!=null && bond.safeEnemy(attacker)){multiplier=.9F;}
        // Preserve the strongest equivalent ward; never multiply the same family.
        if(StPatricksSupport.protectedByAura(target)||NewYearsSupport.protectedByShield(target)){return;}
        if(multiplier<1){event.setNewDamage(event.getNewDamage()*multiplier);}
    }
    public static void releaseAura(ValentinesPhantom caster){WARDS.entrySet().removeIf(e->e.getValue().caster().get()==caster);}
    public static void release(ValentinesPhantom caster){BONDS.entrySet().removeIf(e->e.getValue().caster().get()==caster);releaseAura(caster);CHARMS.entrySet().removeIf(e->e.getValue().caster().get()==caster);}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof ValentinesPhantom caster){release(caster);}if(event.getEntity() instanceof LivingEntity target){BONDS.remove(target);WARDS.remove(target);CHARMS.remove(target);HEAL_REST.remove(target);}}
}
