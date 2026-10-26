package net.ronm19.phantomism.entity.ai;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.SolarPhantom;
import net.ronm19.phantomism.vfx.TotalEclipseVfx;

public final class CelestialBond {
    public interface LunarParticipant {
        AbstractPhantom phantom();
        boolean celestialUltimateActive();
        boolean prefersCelestialSalvation();
        boolean reserveEclipse(UUID solar, int recoveryTicks);
        default boolean celestialCooperationEnabled() { return true; }
    }
    private static final Identifier LUNAR = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "lunar_phantom");
    private CelestialBond() {}
    private static UUID owner(AbstractPhantom phantom) { return phantom.getOwnerReference() == null ? null : phantom.getOwnerReference().getUUID(); }
    public static boolean authorizedAlly(SolarPhantom solar, LivingEntity target) { return authorizedAlly((AbstractPhantom) solar, target); }
    public static boolean authorizedAlly(AbstractPhantom solar, LivingEntity target) {
        if (!(solar.level() instanceof ServerLevel level) || !solar.isTame() || !(target instanceof ServerPlayer || target instanceof OwnableEntity)) { return false; }
        UUID second = target instanceof ServerPlayer player ? player.getUUID() : ((OwnableEntity)target).getOwnerReference() == null ? null : ((OwnableEntity)target).getOwnerReference().getUUID();
        return second != null && CelestialPacts.get(level.getServer()).allows(owner(solar), second, level.getServer().overworld().getGameTime());
    }
    public static LunarParticipant partner(SolarPhantom solar) {
        if (!(solar.level() instanceof ServerLevel level) || !solar.enabled() || !solar.isTame() || !BuiltInRegistries.ENTITY_TYPE.containsKey(LUNAR)) { return null; }
        return level.getEntitiesOfClass(AbstractPhantom.class, solar.getBoundingBox().inflate(24), phantom -> phantom instanceof LunarParticipant lunar
                && lunar.phantom() == phantom && lunar.celestialCooperationEnabled() && BuiltInRegistries.ENTITY_TYPE.getKey(phantom.getType()).equals(LUNAR) && phantom.isAlive() && !phantom.isBaby()
                && phantom.getOrder() != AbstractPhantom.Order.STAY && phantom.getOrder() != AbstractPhantom.Order.RETURN_HOME && phantom.isTame()
                && solar.distanceToSqr(phantom) <= 24 * 24 && solar.line(solar.getEyePosition(), phantom.getEyePosition())
                && CelestialPacts.get(level.getServer()).allows(owner(solar), owner(phantom), level.getServer().overworld().getGameTime())).stream()
                .map(phantom -> (LunarParticipant)phantom).findFirst().orElse(null);
    }
    public static boolean offensive(SolarPhantom solar) { return solar.validTarget(solar.getTarget()) && partner(solar) != null; }
    public static boolean protective(SolarPhantom solar) { return solar.getHealth() < solar.getMaxHealth() * .5F && partner(solar) != null; }
    public static void tick(SolarPhantom solar) {
        if (!(solar.level() instanceof ServerLevel level)) { return; }
        LunarParticipant lunar = partner(solar); if (lunar == null) { return; }
        if (solar.tickCount % 100 == 0) { solar.recognizeCelestialPartner(lunar.phantom()); }
        if (solar.enemies(level, solar.position(), 14).isEmpty()) { return; }
        CelestialPacts.get(level.getServer()).touchBattle(owner(solar), owner(lunar.phantom()), level.getServer().overworld().getGameTime());
        activate(solar, lunar);
    }
    public static boolean activate(SolarPhantom solar, LunarParticipant lunar) {
        if (!(solar.level() instanceof ServerLevel level) || lunar == null || partner(solar) != lunar || solar.getPersistentData().getIntOr("SolarEclipseCooldown", 0) > 0) { return false; }
        boolean enhanced = solar.ultimateTicks() > 0 && lunar.celestialUltimateActive();
        boolean rescue = solar.needsEmergency(level) || lunar.prefersCelestialSalvation();
        int radius = enhanced ? rescue ? 16 : solar.enemies(level, solar.position(), 18).size() >= 4 ? 18 : 16 : 14;
        var enemies = solar.enemies(level, solar.position(), radius);
        if (enemies.isEmpty() || !lunar.reserveEclipse(solar.getUUID(), 1200)) { return false; }
        solar.getPersistentData().putInt("SolarEclipseCooldown", 1200);
        float damage = enhanced ? rescue ? 12 : radius == 18 ? 40 : 30 : 25;
        float healing = enhanced ? rescue ? 24 : radius == 18 ? 0 : 16 : 12;
        for (LivingEntity enemy : enemies) { solar.damage(level, enemy, damage); }
        int healingBeams = 0;
        boolean protectedArea = false;
        for (LivingEntity ward : solar.allies(level, solar.position(), radius)) {
            float previousHealth = ward.getHealth();
            ward.heal(healing);
            protectedArea |= ward.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, enhanced ? 200 : 160, enhanced && rescue ? 1 : 0), solar);
            if (ward.getHealth() > previousHealth && healingBeams < TotalEclipseVfx.MAX_HEALING_BEAMS) { TotalEclipseVfx.restored(solar, ward); healingBeams++; }
        }
        TotalEclipseVfx.start(solar, lunar.phantom(), healingBeams, protectedArea, radius);
        award(solar.getOwner() instanceof ServerPlayer player ? player : null);
        if (!Objects.equals(owner(solar), owner(lunar.phantom()))) { award(lunar.phantom().getOwner() instanceof ServerPlayer player ? player : null); }
        return true;
    }
    private static void award(ServerPlayer player) {
        if (player == null) { return; }
        var advancement = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "when_sun_and_moon_unite"));
        if (advancement != null) { player.getAdvancements().award(advancement, "bond"); }
    }
}
