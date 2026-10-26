package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.GalePhantom;
import net.ronm19.phantomism.vfx.GaleVfx;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class GaleWind {
    private static final Identifier TAILWIND=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"gale_tailwind"),SLOW=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"gale_pressure");
    private static final String DEFLECTED="phantomismGaleDeflected";
    private record Lease(WeakReference<GalePhantom> source,UUID session,long until,double strength){}
    private record Assistance(WeakReference<GalePhantom> source,UUID session,long until,MobEffectInstance applied,net.minecraft.core.Holder<MobEffect> effect){}
    private static final Map<LivingEntity,Lease> TAILWINDS=new WeakHashMap<>(),SLOWS=new WeakHashMap<>();
    private static final Map<LivingEntity,Assistance> ASSISTANCE=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> RESCUE_RECOVERY=new WeakHashMap<>();
    private static final Map<GalePhantom,Long> NEXT_VISUAL=new WeakHashMap<>();
    private GaleWind(){}
    private static boolean live(LivingEntity target,Lease lease,boolean friendly){GalePhantom source=lease.source().get();return source!=null&&source.enabled()&&source.session().equals(lease.session())&&source.level()==target.level()&&target.isAlive()&&target.level().getGameTime()<lease.until()&&(friendly?source.recipient(target):source.validTarget(target));}
    private static void remove(LivingEntity target,Identifier identifier,net.minecraft.core.Holder<Attribute> attribute){var instance=target.getAttribute(attribute);if(instance!=null){instance.removeModifier(identifier);}}
    private static void removeAssistance(LivingEntity target,Assistance lease){var effect=target.getEffect(lease.effect());if(effect==lease.applied()&&effect.getDuration()<=Math.max(0,lease.until()-target.level().getGameTime())+2){target.removeEffect(lease.effect());}}
    public static void maintain(){TAILWINDS.entrySet().removeIf(entry->{if(live(entry.getKey(),entry.getValue(),true)&&!entry.getKey().onGround()){return false;}remove(entry.getKey(),TAILWIND,Attributes.FLYING_SPEED);return true;});SLOWS.entrySet().removeIf(entry->{if(live(entry.getKey(),entry.getValue(),false)){return false;}remove(entry.getKey(),SLOW,Attributes.MOVEMENT_SPEED);return true;});ASSISTANCE.entrySet().removeIf(entry->{GalePhantom source=entry.getValue().source().get();if(source!=null&&source.enabled()&&source.session().equals(entry.getValue().session())&&source.recipient(entry.getKey())&&entry.getKey().level().getGameTime()<entry.getValue().until()){return false;}removeAssistance(entry.getKey(),entry.getValue());return true;});RESCUE_RECOVERY.entrySet().removeIf(entry->!entry.getKey().isAlive()||entry.getKey().level().getGameTime()>=entry.getValue());}
    public static void aura(GalePhantom source,ServerLevel level){
        if(!source.enabled()){return;}maintain();Vec3 center=source.domainTicks()>0?source.domainCenter():source.position();if(center==null){return;}double radius=source.domainTicks()>0?24:12,boost=source.domainTicks()>0?.2D:.12D;if(level.getGameTime()>=NEXT_VISUAL.getOrDefault(source,Long.MIN_VALUE)){NEXT_VISUAL.put(source,level.getGameTime()+60);GaleVfx.emit(source,GaleVfx.TAILWIND,source,source.position(),Vec3.ZERO,25,3);}
        for(AbstractPhantom target:level.getEntitiesOfClass(AbstractPhantom.class,new AABB(center,center).inflate(radius),source::recipient).stream().filter(target->!target.onGround()&&target.position().distanceToSqr(center)<=radius*radius&&source.line(source.getEyePosition(),target.getEyePosition())).limit(16).toList()){
            if(TAILWINDS.size()>=256&&!TAILWINDS.containsKey(target)){break;}Lease previous=TAILWINDS.get(target);if(previous!=null&&previous.source().get()!=source&&previous.strength()>=boost){continue;}
            var flight=target.getAttribute(Attributes.FLYING_SPEED);if(flight==null){continue;}flight.removeModifier(TAILWIND);flight.addTransientModifier(new AttributeModifier(TAILWIND,boost,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));TAILWINDS.put(target,new Lease(new WeakReference<>(source),source.session(),level.getGameTime()+25,boost));
        }
    }
    private static boolean clearMotion(GalePhantom source,LivingEntity target,Vec3 impulse){if(!Double.isFinite(impulse.lengthSqr())||impulse.lengthSqr()>4||target.level()!=source.level()){return false;}AABB swept=target.getBoundingBox().expandTowards(impulse).deflate(.001D);if(!source.isLoadedFlightBox(swept)||!source.level().getWorldBorder().isWithinBounds(swept)||!source.level().noBlockCollision(target,swept)||source.level().containsAnyLiquid(swept)){return false;}for(var block:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(swept.minX,swept.minY,swept.minZ),net.minecraft.core.BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))){if(GravityControl.dangerous((ServerLevel)source.level(),block)){return false;}}return true;}
    public static boolean push(GalePhantom source,LivingEntity target,Vec3 impulse){
        if(!source.enabled()||!source.validTarget(target)||!(target instanceof Mob mob)||target instanceof Player||target.isPassenger()||target.isVehicle()||GravityControl.movementImmune(mob)||source.distanceToSqr(target)>48*48||!source.line(source.getEyePosition(),target.getEyePosition())){return false;}
        double resistance=Math.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,1);Vec3 motion=target.getDeltaMovement().scale(.4D).add(impulse.scale(1-resistance));double horizontal=motion.horizontalDistance();if(horizontal>.35D){motion=new Vec3(motion.x*.35D/horizontal,motion.y,motion.z*.35D/horizontal);}motion=new Vec3(motion.x,Math.clamp(motion.y,-.2D,.3D),motion.z);if(!clearMotion(source,target,motion.scale(3))){return false;}target.setDeltaMovement(motion);target.needsSync=true;return true;
    }
    public static void slow(GalePhantom source,LivingEntity target,int duration){
        if(!source.enabled()||!source.validTarget(target)||!(target instanceof Mob mob)||target instanceof OwnableEntity||target instanceof Player||PhantomDuels.pair(source,target)||SLOWS.size()>=256&&!SLOWS.containsKey(target)){return;}
        maintain();Lease previous=SLOWS.get(target);if(previous!=null&&previous.source().get()!=source){return;}var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null){return;}double amount=GravityControl.movementImmune(mob)?-.05D:-.18D;speed.removeModifier(SLOW);speed.addTransientModifier(new AttributeModifier(SLOW,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));SLOWS.put(target,new Lease(new WeakReference<>(source),source.session(),source.level().getGameTime()+Math.clamp(duration,1,60),amount));
    }
    public static boolean endangered(GalePhantom source,LivingEntity target){return source.enabled()&&source.recipient(target)&&target!=source&&!target.onGround()&&!target.isPassenger()&&!target.isInWater()&&!target.isInLava()&&!target.isFallFlying()&&target.getDeltaMovement().y<-.2D&&(target.fallDistance>4||ASSISTANCE.containsKey(target))&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()||player.getAbilities().flying))&&source.distanceToSqr(target)<=24*24&&source.line(source.getEyePosition(),target.getEyePosition());}
    private static boolean effect(GalePhantom source,LivingEntity target,net.minecraft.core.Holder<MobEffect> type,int duration){
        if(ASSISTANCE.size()>=128&&!ASSISTANCE.containsKey(target)){return false;}var existing=target.getEffect(type);if(existing!=null&&existing.getDuration()>=duration/2){return false;}Assistance previous=ASSISTANCE.get(target);if(previous!=null&&previous.source().get()!=source&&existing!=null){return false;}MobEffectInstance applied=new MobEffectInstance(type,duration,0,false,false,true);if(!target.addEffect(applied,source)){return false;}var current=target.getEffect(type);if(current==null){return false;}ASSISTANCE.put(target,new Assistance(new WeakReference<>(source),source.session(),source.level().getGameTime()+duration,current,type));return true;
    }
    public static boolean rescue(GalePhantom source,LivingEntity target){
        maintain();if(!endangered(source,target)||RESCUE_RECOVERY.getOrDefault(target,Long.MIN_VALUE)>source.level().getGameTime()){return false;}if(!effect(source,target,MobEffects.SLOW_FALLING,120)){return false;}RESCUE_RECOVERY.put(target,source.level().getGameTime()+60);return true;
    }
    public static boolean assist(GalePhantom source,LivingEntity target){
        if(!source.enabled()||!source.recipient(target)||target==source||!source.line(source.getEyePosition(),target.getEyePosition())){return false;}if(endangered(source,target)){return rescue(source,target);}if(target instanceof Player&&target.onGround()&&clearMotion(source,target,new Vec3(0,1.4D,0))){return effect(source,target,MobEffects.JUMP_BOOST,40);}if(target instanceof AbstractPhantom&&target.getControllingPassenger()==null&&!target.onGround()&&target.getDeltaMovement().y<-.2D&&clearMotion(source,target,new Vec3(0,.7D,0))){target.setDeltaMovement(target.getDeltaMovement().multiply(1,0,1).add(0,.15D,0));target.needsSync=true;return true;}return false;
    }
    public static void deflect(GalePhantom source,ServerLevel level){
        if(!source.enabled()||!source.canDeflect()||source.crosswindTicks()==0&&source.domainTicks()==0){return;}Vec3 center=source.domainTicks()>0?source.domainCenter():source.position();if(center==null){return;}double radius=source.domainTicks()>0?24:12;var wards=level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),source::recipient).stream().filter(ward->ward.position().distanceToSqr(center)<=radius*radius&&source.line(source.getEyePosition(),ward.getEyePosition())).limit(16).toList();
        for(Projectile projectile:level.getEntitiesOfClass(Projectile.class,new AABB(center,center).inflate(radius),GolemDefense::ordinary).stream().limit(24).toList()){
            if(!source.canDeflect()){break;}if(!(projectile.getOwner() instanceof LivingEntity attacker)||!source.validTarget(attacker)||projectile.getPersistentData().getBooleanOr(DEFLECTED,false)||CrystalDefense.reflected(projectile)||!source.line(source.getEyePosition(),projectile.position())){continue;}Vec3 motion=projectile.getDeltaMovement();if(!Double.isFinite(motion.lengthSqr())||motion.lengthSqr()<.01D){continue;}if(wards.stream().noneMatch(ward->!PhantomDuels.pair(attacker,ward)&&ward.getBoundingBox().inflate(.5D).clip(projectile.position(),projectile.position().add(motion.scale(6))).isPresent())){continue;}
            Vec3 deflected=motion.length()>2?motion.normalize().scale(2):motion;projectile.getPersistentData().putBoolean(DEFLECTED,true);projectile.setDeltaMovement(-deflected.z*.55D,Math.clamp(deflected.y*.6D+.05D,-.5D,.5D),deflected.x*.55D);projectile.needsSync=true;source.deflected();GaleVfx.emit(source,GaleVfx.CROSSWIND,null,projectile.position(),projectile.getDeltaMovement(),16,2);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event){
        LivingEntity ward=event.getEntity();float incoming=event.getNewDamage();if(!(ward.level() instanceof ServerLevel level)||!Float.isFinite(incoming)||incoming<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}LivingEntity attacker=event.getSource().getEntity() instanceof LivingEntity living?living:null;float reduction=0;
        for(GalePhantom source:level.getEntitiesOfClass(GalePhantom.class,ward.getBoundingBox().inflate(26),candidate->candidate.enabled()&&candidate.recipient(ward)).stream().limit(8).toList()){
            if(attacker!=null&&(!source.safeEnemy(attacker)||PhantomDuels.pair(attacker,ward)||PhantomDuels.excludesObserver(source,attacker))){continue;}if(source.distanceToSqr(ward)<=144&&source.line(source.getEyePosition(),ward.getEyePosition())){source.incomingThreat(attacker,ward,incoming);}reduction=Math.max(reduction,source.mitigation(ward));
        }
        if(reduction>0){event.setNewDamage(Math.max(Math.min(incoming,event.getOriginalDamage()*.6F),incoming*(1-reduction)));}
    }
    public static void releaseControl(GalePhantom source){SLOWS.entrySet().removeIf(entry->{if(entry.getValue().source().get()!=source){return false;}remove(entry.getKey(),SLOW,Attributes.MOVEMENT_SPEED);return true;});}
    public static void release(GalePhantom source){NEXT_VISUAL.remove(source);releaseControl(source);TAILWINDS.entrySet().removeIf(entry->{if(entry.getValue().source().get()!=source){return false;}remove(entry.getKey(),TAILWIND,Attributes.FLYING_SPEED);return true;});ASSISTANCE.entrySet().removeIf(entry->{if(entry.getValue().source().get()!=source){return false;}removeAssistance(entry.getKey(),entry.getValue());return true;});}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof GalePhantom source){source.endGale();}if(event.getEntity() instanceof LivingEntity target){TAILWINDS.remove(target);SLOWS.remove(target);ASSISTANCE.remove(target);RESCUE_RECOVERY.remove(target);remove(target,TAILWIND,Attributes.FLYING_SPEED);remove(target,SLOW,Attributes.MOVEMENT_SPEED);}}
}
