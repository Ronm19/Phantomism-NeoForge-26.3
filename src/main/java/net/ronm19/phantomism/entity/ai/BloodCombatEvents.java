package net.ronm19.phantomism.entity.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.effect.BleedingEffect;
import net.ronm19.phantomism.entity.BloodPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class BloodCombatEvents {
    private static final Map<BloodPhantom, ShieldHit> HITS = new WeakHashMap<>();
    private BloodCombatEvents() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void healing(LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide() && Float.isFinite(event.getAmount()) && event.getAmount() > 0) { event.setAmount(event.getAmount() * Math.min(BleedingEffect.healingFactor(event.getEntity()), ReaperSouls.healingFactor(event.getEntity()))); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shield(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof BloodPhantom blood)) { return; }
        HITS.remove(blood);
        if (blood.level().isClientSide() || !blood.combatEnabled() || !blood.hasBloodShield() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getNewDamage() <= 0 || !Float.isFinite(event.getNewDamage())) { return; }
        float prevented = event.getNewDamage() * 0.3F; event.setNewDamage(event.getNewDamage() - prevented);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && blood.isValidBloodTarget(attacker)) { HITS.put(blood, new ShieldHit(event.getSource(), prevented, blood.level().getGameTime())); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shieldHeal(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof BloodPhantom blood)) { return; }
        ShieldHit hit = HITS.remove(blood);
        if (hit != null && hit.source == event.getSource() && hit.tick == blood.level().getGameTime() && event.getInflictedDamage() > 0 && blood.isAlive()) { blood.healShield(Math.min(hit.prevented, event.getInflictedDamage() * (0.3F / 0.7F))); }
    }
    private record ShieldHit(DamageSource source, float prevented, long tick) {}
}
