package net.ronm19.phantomism.entity.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.EchoPhantom;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class EchoTeamSupport {
    private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "perfect_resonance_team");
    private static final Map<LivingEntity, Map<java.util.UUID, Long>> LEASES = new WeakHashMap<>();
    private EchoTeamSupport() {}
    public static void pulse(EchoPhantom echo, ServerLevel level) {
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, echo.getBoundingBox().inflate(24),
                target -> target != echo && target.isAlive() && echo.acceptsAlly(target) && echo.distanceToSqr(target) <= 24 * 24).stream().limit(8).toList()) {
            LEASES.computeIfAbsent(ally, ignored -> new HashMap<>()).put(echo.getUUID(), level.getGameTime() + 30);
            var damage = ally.getAttribute(Attributes.ATTACK_DAMAGE); var speed = ally.getAttribute(Attributes.ATTACK_SPEED);
            if (damage != null) { damage.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, 0.05D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
            if (speed != null) { speed.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, 0.10D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
            net.ronm19.phantomism.vfx.PhantomVisualEffects.echoRings(level, ally.getBoundingBox().getCenter(), 0.75F, false);
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) { return; }
        Map<java.util.UUID, Long> leases = LEASES.get(target); if (leases == null) { return; }
        leases.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime() || !(level.getEntity(entry.getKey()) instanceof EchoPhantom echo)
                || !echo.isPerfectResonance() || !echo.combatEnabled() || !echo.acceptsAlly(target) || echo.distanceToSqr(target) > 24 * 24);
        if (leases.isEmpty()) { LEASES.remove(target); clear(target); }
    }
    public static void release(EchoPhantom source) {
        var iterator = LEASES.entrySet().iterator();
        while (iterator.hasNext()) { var entry = iterator.next(); entry.getValue().remove(source.getUUID()); if (entry.getValue().isEmpty()) { clear(entry.getKey()); iterator.remove(); } }
    }
    private static void clear(LivingEntity target) { for (var attribute : java.util.List.of(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED)) { var instance = target.getAttribute(attribute); if (instance != null) { instance.removeModifier(MODIFIER); } } }
}
