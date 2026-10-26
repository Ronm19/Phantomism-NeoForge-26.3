package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ObsidianPhantom;
import net.ronm19.phantomism.vfx.ObsidianVfx;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class ObsidianDefense {
    private static final Identifier ARMOR=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"obsidian_fracture"),SLOW=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"obsidian_wall_slow"),WEAK=Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"obsidian_wall_weak");
    private record Lease(WeakReference<ObsidianPhantom> source,UUID session,long until){}
    private static final Map<LivingEntity,Lease> FRACTURES=new WeakHashMap<>(),CONTROLS=new WeakHashMap<>();
    private ObsidianDefense(){}
    private static boolean live(LivingEntity target,Lease lease){ObsidianPhantom source=lease.source().get();return source!=null&&source.enabled()&&source.session().equals(lease.session())&&source.level()==target.level()&&source.validTarget(target)&&source.distanceToSqr(target)<=48*48&&target.level().getGameTime()<lease.until();}
    private static void remove(LivingEntity target,Identifier identifier,net.minecraft.core.Holder<Attribute> attribute){AttributeInstance instance=target.getAttribute(attribute);if(instance!=null){instance.removeModifier(identifier);}}
    private static void clearControl(LivingEntity target){remove(target,SLOW,Attributes.MOVEMENT_SPEED);remove(target,WEAK,Attributes.ATTACK_DAMAGE);}
    public static void maintain(){FRACTURES.entrySet().removeIf(entry->{if(live(entry.getKey(),entry.getValue())){return false;}remove(entry.getKey(),ARMOR,Attributes.ARMOR);return true;});CONTROLS.entrySet().removeIf(entry->{if(live(entry.getKey(),entry.getValue())){return false;}clearControl(entry.getKey());return true;});}
    private static boolean claim(Map<LivingEntity,Lease> leases,ObsidianPhantom source,LivingEntity target,int duration){maintain();if(!source.enabled()||!source.validTarget(target)||source.distanceToSqr(target)>48*48||target instanceof OwnableEntity||target instanceof net.minecraft.world.entity.player.Player||PhantomDuels.pair(source,target)||leases.size()>=256&&!leases.containsKey(target)){return false;}Lease previous=leases.get(target);if(previous!=null&&previous.source().get()!=source){return false;}leases.put(target,new Lease(new WeakReference<>(source),source.session(),source.level().getGameTime()+Math.clamp(duration,1,60)));return true;}
    public static void fracture(ObsidianPhantom source,LivingEntity target,int duration){if(claim(FRACTURES,source,target,duration)){AttributeInstance armor=target.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifier(ARMOR);armor.addTransientModifier(new AttributeModifier(ARMOR,target instanceof Mob mob&&GravityControl.movementImmune(mob)?-2:-4,AttributeModifier.Operation.ADD_VALUE));}}}
    public static void control(ObsidianPhantom source,LivingEntity target,int duration){if(!claim(CONTROLS,source,target,duration)){return;}clearControl(target);if(target instanceof Mob mob&&!GravityControl.movementImmune(mob)){AttributeInstance speed=target.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.addTransientModifier(new AttributeModifier(SLOW,-.2D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}AttributeInstance attack=target.getAttribute(Attributes.ATTACK_DAMAGE);if(attack!=null){attack.addTransientModifier(new AttributeModifier(WEAK,target instanceof Mob mob&&GravityControl.movementImmune(mob)?-.05D:-.1D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}
    public static void push(ObsidianPhantom source,LivingEntity target,double amount){if(!(target instanceof Mob mob)||!source.validTarget(target)||GravityControl.movementImmune(mob)||target.isPassenger()||target.isVehicle()){return;}Vec3 away=target.position().subtract(source.position()).multiply(1,0,1).normalize();if(away.lengthSqr()>.001D){mob.knockback(amount,-away.x,-away.z,source.damageSources().mobAttack(source),0);}}
    private static boolean accepts(ObsidianPhantom source,LivingEntity attacker,LivingEntity ward){return source.enabled()&&source.recipient(ward)&&(attacker==null||source.safeEnemy(attacker)&&!PhantomDuels.pair(ward,attacker)&&!PhantomDuels.excludesObserver(source,attacker));}
    public static void intercept(ObsidianPhantom source,ServerLevel level){
        if(!source.enabled()||!source.canAbsorb()||!source.anchored()&&source.shelterTicks()==0&&source.guardTicks()==0){return;}
        double radius=source.citadelTicks()>0?20:source.shelterTicks()>0?12:source.guardTicks()>0?10:6;Vec3 center=source.defenseCenter();if(center==null){return;}
        var wards=level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),source::recipient).stream().filter(ward->ward.position().distanceToSqr(center)<=radius*radius&&source.line(source.getEyePosition(),ward.getEyePosition())).limit(16).toList();
        for(Projectile projectile:level.getEntitiesOfClass(Projectile.class,new AABB(center,center).inflate(radius),GolemDefense::ordinary).stream().limit(24).toList()){
            if(!source.canAbsorb()){break;}if(!(projectile.getOwner() instanceof LivingEntity attacker)||!source.validTarget(attacker)||CrystalDefense.reflected(projectile)||!source.line(source.getEyePosition(),projectile.position())){continue;}
            Vec3 motion=projectile.getDeltaMovement();if(motion.lengthSqr()<.01D){continue;}
            boolean danger=wards.stream().anyMatch(ward->accepts(source,attacker,ward)&&ward.getBoundingBox().inflate(.5D).clip(projectile.position(),projectile.position().add(motion.scale(6))).isPresent());
            if(danger){projectile.discard();source.absorbed();ObsidianVfx.emit(source,ObsidianVfx.ABSORB,null,projectile.position(),Vec3.ZERO,14,1.5F);}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event){
        LivingEntity ward=event.getEntity();float incoming=event.getNewDamage();if(!(ward.level() instanceof ServerLevel level)||!Float.isFinite(incoming)||incoming<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        boolean explosion=event.getSource().is(DamageTypeTags.IS_EXPLOSION);LivingEntity attacker=event.getSource().getEntity() instanceof LivingEntity living?living:null;
        float reduction=ward instanceof ObsidianPhantom&&explosion?.85F:0;ObsidianPhantom strongest=null;
        for(ObsidianPhantom source:level.getEntitiesOfClass(ObsidianPhantom.class,ward.getBoundingBox().inflate(22),candidate->accepts(candidate,attacker,ward)).stream().limit(8).toList()){
            if(source.distanceToSqr(ward)<=144&&source.line(source.getEyePosition(),ward.getEyePosition())){source.incomingThreat(attacker,ward,incoming,explosion);}
            float offered=source.mitigation(ward,explosion);if(offered>reduction){reduction=offered;strongest=source;}
        }
        if(reduction>0){event.setNewDamage(Math.max(Math.min(incoming,event.getOriginalDamage()*.1F),incoming*(1-reduction)));if(strongest!=null){ObsidianVfx.emit(strongest,ObsidianVfx.ABSORB,ward,ward.getBoundingBox().getCenter(),Vec3.ZERO,12,1.5F);}}
    }
    public static void releaseControl(ObsidianPhantom source){CONTROLS.entrySet().removeIf(entry->{if(entry.getValue().source().get()!=source){return false;}clearControl(entry.getKey());return true;});}
    public static void release(ObsidianPhantom source){releaseControl(source);FRACTURES.entrySet().removeIf(entry->{if(entry.getValue().source().get()!=source){return false;}remove(entry.getKey(),ARMOR,Attributes.ARMOR);return true;});}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof ObsidianPhantom source){source.endObsidian();}if(event.getEntity() instanceof LivingEntity target){FRACTURES.remove(target);CONTROLS.remove(target);remove(target,ARMOR,Attributes.ARMOR);clearControl(target);}}
}
