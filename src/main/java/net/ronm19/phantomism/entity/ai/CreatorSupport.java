package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.CreatorPhantom;

@EventBusSubscriber(modid="phantomism")
public final class CreatorSupport {
    private record Lease(WeakReference<CreatorPhantom> source,UUID session,long expires) {}
    private static final Map<LivingEntity,Lease> PRESENCE=new WeakHashMap<>(),AEGIS=new WeakHashMap<>(),INTERRUPTED=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> HEALED=new WeakHashMap<>();
    private static final Identifier KNOCKBACK=Identifier.fromNamespaceAndPath("phantomism","creator_presence");
    private CreatorSupport() {}
    private static CreatorPhantom source(LivingEntity target,Lease lease,boolean ward,boolean hostile){
        CreatorPhantom source=lease==null?null:lease.source().get();
        double radius=source!=null&&ward&&source.evolutionTicks()>0?24:12;
        return source==null||!source.companionAvailable()||ward&&!source.enabled()||!source.session().equals(lease.session())||target.level()!=source.level()||!target.isAlive()||target.level().getGameTime()>=lease.expires()||source.distanceToSqr(target)>radius*radius||!source.line(source.getEyePosition(),target.getEyePosition())||(hostile?!source.validTarget(target):!source.ally(target))?null:source;
    }
    private static Lease lease(CreatorPhantom source,int duration){return new Lease(new WeakReference<>(source),source.session(),source.level().getGameTime()+duration);}
    public static void presence(CreatorPhantom source,LivingEntity target){
        if(!source.isTame()||!source.companionAvailable()||!(target instanceof AbstractPhantom phantom)||!phantom.isTame()||!source.ally(target)||source.distanceToSqr(target)>144){return;}
        if(source(target,PRESENCE.get(target),false,false)!=null&&PRESENCE.get(target).source().get()!=source){return;}PRESENCE.put(target,lease(source,30));
        var attr=target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(attr!=null&&!attr.hasModifier(KNOCKBACK)){attr.addTransientModifier(new AttributeModifier(KNOCKBACK,.2D,AttributeModifier.Operation.ADD_VALUE));}
    }
    public static boolean present(LivingEntity target){return source(target,PRESENCE.get(target),false,false)!=null;}
    public static void protect(CreatorPhantom source,LivingEntity target){
        double radius=source.evolutionTicks()>0?24:12;if(!source.enabled()||source.aegisTicks()<=0&&source.evolutionTicks()<=0||!source.ally(target)||source.distanceToSqr(target)>radius*radius){return;}
        var previous=source(target,AEGIS.get(target),true,false);if(previous!=null&&previous!=source&&previous.evolutionTicks()>0){return;}AEGIS.put(target,lease(source,15));
    }
    public static boolean protectedByAegis(LivingEntity target){return source(target,AEGIS.get(target),true,false)!=null;}
    public static boolean heal(CreatorPhantom source,LivingEntity target,float amount){
        long now=target.level().getGameTime();if(!source.enabled()||!source.ally(target)||source.distanceToSqr(target)>576||HEALED.getOrDefault(target,Long.MIN_VALUE)>now||!source.line(source.getEyePosition(),target.getEyePosition())||target.getHealth()>=target.getMaxHealth()){return false;}
        target.heal(Math.clamp(amount,0,4));HEALED.put(target,now+35);return true;
    }
    public static void interrupt(CreatorPhantom source,LivingEntity target,int duration){
        if(!source.validTarget(target)||!(target instanceof Mob mob)||GravityControl.movementImmune(mob)){return;}INTERRUPTED.put(target,lease(source,Math.clamp(duration,1,30)));enforce(mob);
    }
    private static boolean interrupted(Mob mob,LivingEntity target){CreatorPhantom caster=source(mob,INTERRUPTED.get(mob),true,true);return caster!=null&&caster.ally(target);}
    private static void enforce(Mob mob){if(interrupted(mob,mob.getTarget())){mob.setTarget(null);mob.getNavigation().stop();}var brain=mob.getBrain();if(brain.checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED)&&interrupted(mob,brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))){brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);}}
    private static void removePresence(LivingEntity target){PRESENCE.remove(target);var attr=target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(attr!=null){attr.removeModifier(KNOCKBACK);}}
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity target)||!(target.level() instanceof ServerLevel)){return;}
        if(PRESENCE.containsKey(target)&&!present(target)){removePresence(target);}if(AEGIS.containsKey(target)&&!protectedByAegis(target)){AEGIS.remove(target);}
        if(INTERRUPTED.containsKey(target)){if(source(target,INTERRUPTED.get(target),true,true)==null){INTERRUPTED.remove(target);}else if(target instanceof Mob mob&&target.tickCount%5==0){enforce(mob);}}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void targeting(LivingChangeTargetEvent event){if(event.getEntity() instanceof Mob mob&&interrupted(mob,event.getNewAboutToBeSetTarget())){event.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event){
        var target=event.getEntity();if(!(target.level() instanceof ServerLevel)||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        var ward=source(target,AEGIS.get(target),true,false);var presence=source(target,PRESENCE.get(target),false,false);var caster=ward!=null?ward:presence;
        if(caster==null){return;}
        boolean hostile=event.getSource().getEntity() instanceof LivingEntity attacker&&caster.safeEnemy(attacker);
        if(!hostile&&!(ward!=null&&event.getSource().is(DamageTypeTags.IS_EXPLOSION))){return;}
        if(StPatricksSupport.protectedByAura(target)||NewYearsSupport.protectedByShield(target)||ValentinesSupport.protectedByAura(target)){return;}
        event.setNewDamage(event.getNewDamage()*(ward!=null?(ward.evolutionTicks()>0?.6F:.65F):.9F));
    }
    public static void releaseAegis(CreatorPhantom source){AEGIS.entrySet().removeIf(e->e.getValue().source().get()==source);}
    public static void release(CreatorPhantom source){for(var target:java.util.List.copyOf(PRESENCE.keySet())){if(PRESENCE.get(target).source().get()==source){removePresence(target);}}releaseAegis(source);INTERRUPTED.entrySet().removeIf(e->e.getValue().source().get()==source);}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof CreatorPhantom caster){release(caster);}if(event.getEntity() instanceof LivingEntity target){removePresence(target);AEGIS.remove(target);INTERRUPTED.remove(target);HEALED.remove(target);}}
}
