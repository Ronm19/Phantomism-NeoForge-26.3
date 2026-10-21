package net.ronm19.phantomism.entity.ai;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.*;
import net.ronm19.phantomism.vfx.PrimordialVfx;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class PrimordialSupport {
    public static final TagKey<MobEffect> CLEANSEABLE=TagKey.create(Registries.MOB_EFFECT,Identifier.fromNamespaceAndPath(Phantomism.MOD_ID,"primordial_cleanseable"));
    private record Lease(WeakReference<PrimordialPhantom> source,UUID session,long until,long recovery){}
    private static final Map<Mob,Lease> STUNS=new WeakHashMap<>();
    private static final Map<Projectile,Lease> CLAIMS=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> HEALS=new WeakHashMap<>();
    private PrimordialSupport(){}
    public static boolean cleanseable(MobEffectInstance effect){return effect.getEffect().value().getCategory()==MobEffectCategory.HARMFUL&&effect.getEffect().is(CLEANSEABLE);}
    private static boolean live(Entity target,Lease lease){var source=lease.source().get();return source!=null&&source.enabled()&&source.session().equals(lease.session())&&source.level()==target.level()&&!target.isRemoved()&&target.isAlive()&&target.level().getGameTime()<lease.recovery();}
    public static void maintain(){STUNS.entrySet().removeIf(entry->!live(entry.getKey(),entry.getValue())||!entry.getValue().source().get().validTarget(entry.getKey()));CLAIMS.entrySet().removeIf(entry->!live(entry.getKey(),entry.getValue()));HEALS.entrySet().removeIf(entry->!entry.getKey().isAlive()||entry.getKey().isRemoved()||entry.getKey().level().getGameTime()>=entry.getValue());}
    public static boolean stun(PrimordialPhantom source,LivingEntity target){maintain();if(!source.enabled()||!source.validTarget(target)||!(target instanceof Mob mob)||GravityControl.movementImmune(mob)||mob.isPassenger()||mob.isVehicle()||STUNS.containsKey(mob)||STUNS.size()>=256){return false;}long now=source.level().getGameTime();STUNS.put(mob,new Lease(new WeakReference<>(source),source.session(),now+20,now+60));return true;}
    public static boolean stunned(LivingEntity target){if(!(target instanceof Mob mob)){return false;}Lease lease=STUNS.get(mob);return lease!=null&&live(mob,lease)&&lease.source().get().validTarget(mob)&&mob.level().getGameTime()<lease.until();}
    public static boolean force(PrimordialPhantom source,LivingEntity target,Vec3 direction,double strength){if(!(source.level() instanceof ServerLevel level)||!source.enabled()||!source.validTarget(target)||!(target instanceof Mob mob)||GravityControl.movementImmune(mob)||mob.isPassenger()||mob.isVehicle()||!Double.isFinite(direction.lengthSqr())){return false;}double resistance=mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);Vec3 step=direction.multiply(1,0,1).normalize().scale(Math.clamp(strength,0,.4D)*Math.clamp(1-resistance,0,1));AABB box=mob.getBoundingBox().expandTowards(step);BlockPos floor=BlockPos.containing(mob.position().add(step)).below();if(step.lengthSqr()<.00001D||!source.isLoadedFlightBox(box)||!level.getWorldBorder().isWithinBounds(box)||!level.noCollision(mob,box)||level.containsAnyLiquid(box)||GravityControl.dangerous(level,floor)||!mob.isNoGravity()&&level.getBlockState(floor).getCollisionShape(level,floor).isEmpty()){return false;}mob.setDeltaMovement(step);mob.needsSync=true;return true;}
    public static boolean restore(PrimordialPhantom source,LivingEntity target){if(!source.recipient(target)||HEALS.getOrDefault(target,Long.MIN_VALUE)>target.level().getGameTime()||HEALS.size()>=256&&!HEALS.containsKey(target)){return false;}boolean changed=false;var effects=List.copyOf(target.getActiveEffects());for(var effect:effects){if(cleanseable(effect)){changed=target.removeEffect(effect.getEffect());break;}}float before=target.getHealth();if(before<target.getMaxHealth()){target.heal(2);}changed|=target.getHealth()>before;if(changed){HEALS.put(target,target.level().getGameTime()+40);PrimordialVfx.emit(source,PrimordialVfx.HEAL,target,target.position(),Vec3.ZERO,24,1);}return changed;}
    public static int intercept(PrimordialPhantom source,ServerLevel level){if(!source.wingActive()){return 0;}maintain();LivingEntity ward=source.ward();int count=0;for(Projectile shot:level.getEntitiesOfClass(Projectile.class,ward.getBoundingBox().inflate(6),shot->!shot.isRemoved()&&!shot.isInvulnerable()&&SentinelDefense.breakable(shot)).stream().sorted(Comparator.comparingDouble(ward::distanceToSqr)).limit(16).toList()){
            if(!(shot.getOwner() instanceof LivingEntity attacker)||!source.validTarget(attacker)||attacker instanceof Mob boss&&GravityControl.movementImmune(boss)||PhantomDuels.pair(ward,attacker)||shot.getDeltaMovement().lengthSqr()<.01D||!ward.getBoundingBox().inflate(.6D).clip(shot.position(),shot.position().add(shot.getDeltaMovement().scale(5))).isPresent()||!source.line(source.getEyePosition(),shot.position())||!source.line(shot.position(),ward.getEyePosition())||CLAIMS.containsKey(shot)||CLAIMS.size()>=128){continue;}
            var best=level.getEntitiesOfClass(PrimordialPhantom.class,ward.getBoundingBox().inflate(20),candidate->candidate.wingActive()&&candidate.ward()==ward).stream().min(Comparator.comparingDouble((PrimordialPhantom candidate)->candidate.distanceToSqr(shot)).thenComparingInt(Entity::getId)).orElse(null);if(best!=source||!source.spendWing()){continue;}long now=level.getGameTime();CLAIMS.put(shot,new Lease(new WeakReference<>(source),source.session(),now+8,now+8));Vec3 point=shot.position();shot.discard();PrimordialVfx.emit(source,PrimordialVfx.IMPACT,null,point,Vec3.ZERO,16,1.4F);if(++count>=2){break;}
        }return count;}
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event){if(!event.getEntity().level().isClientSide()&&event.getEntity() instanceof LivingEntity target&&stunned(target)){target.setDeltaMovement(Vec3.ZERO);target.needsSync=true;event.setCanceled(true);}}
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event){if(event.getSource().getEntity() instanceof LivingEntity attacker&&stunned(attacker)||event.getEntity() instanceof PrimordialPhantom source&&source.fallSafe()&&event.getSource().is(DamageTypes.FALL)){event.setCanceled(true);}}
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event){LivingEntity target=event.getEntity();if(!(target.level() instanceof ServerLevel level)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        if(target instanceof PrimordialPhantom source&&source.enabled()&&(event.getSource().is(DamageTypeTags.IS_FIRE)||event.getSource().is(DamageTypes.LIGHTNING_BOLT)||event.getSource().is(DamageTypes.FREEZE)||event.getSource().is(DamageTypes.WITHER)||event.getSource().is(DamageTypes.MAGIC)&&target.hasEffect(MobEffects.POISON))){event.setNewDamage(event.getNewDamage()*.5F);}
        if(event.getSource().getEntity() instanceof AbstractPhantom attacker&&!level.getEntitiesOfClass(PrimordialPhantom.class,attacker.getBoundingBox().inflate(64),source->source.empowers(attacker,target)).isEmpty()){event.setNewDamage(event.getNewDamage()*1.15F);}
        if(event.getSource().getEntity() instanceof LivingEntity attacker&&!PhantomDuels.pair(target,attacker)){float mitigation=0;for(var source:level.getEntitiesOfClass(PrimordialPhantom.class,target.getBoundingBox().inflate(64),source->source.enabled()&&source.safeEnemy(attacker))){mitigation=Math.max(mitigation,source.mitigation(target));}if(mitigation>0){event.setNewDamage(event.getNewDamage()*(1-mitigation));}}
    }
    public static void release(PrimordialPhantom source){STUNS.entrySet().removeIf(entry->entry.getValue().source().get()==source);CLAIMS.entrySet().removeIf(entry->entry.getValue().source().get()==source);}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof PrimordialPhantom source){source.endPrimordial();}STUNS.remove(event.getEntity());CLAIMS.remove(event.getEntity());HEALS.remove(event.getEntity());}
}
