package net.ronm19.phantomism.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.PlaguePhantom;

@EventBusSubscriber(modid=Phantomism.MOD_ID)
public final class PlagueControl {
    private PlagueControl(){}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Pre event){
        if(!(event.getEntity().level() instanceof ServerLevel level)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)){return;}
        if(event.getSource().getEntity() instanceof LivingEntity attacker){event.setNewDamage(event.getNewDamage()*PlagueOutbreak.offense(attacker));if(event.getEntity() instanceof PlaguePhantom source&&source.validTarget(attacker)){source.husk(level,attacker,event.getNewDamage());float incoming=event.getNewDamage();event.setNewDamage(Math.max(Math.min(incoming,event.getOriginalDamage()*.4F),incoming*(1-source.huskMitigation())));}}
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity().level().isClientSide()){return;}PlaguePhantom source=PlagueOutbreak.responsible(event.getEntity());if(source!=null){source.outbreak().death(event.getEntity());}PlagueOutbreak.orphan(event.getEntity());}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel().isClientSide()){return;}if(event.getEntity() instanceof PlaguePhantom source){source.endPlague();}else if(event.getEntity() instanceof LivingEntity target){PlagueOutbreak.orphan(target);}}
}
