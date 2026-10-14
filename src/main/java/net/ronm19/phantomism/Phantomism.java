package net.ronm19.phantomism;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.phantomism.entity.BlackPhantom;
import net.ronm19.phantomism.entity.FirePhantom;
import net.ronm19.phantomism.entity.FrostPhantom;
import net.ronm19.phantomism.entity.EarthPhantom;
import net.ronm19.phantomism.entity.WaterPhantom;
import net.ronm19.phantomism.entity.StormPhantom;
import net.ronm19.phantomism.entity.SandPhantom;
import net.ronm19.phantomism.entity.GoldPhantom;
import net.ronm19.phantomism.entity.CherryPhantom;
import net.ronm19.phantomism.network.PhantomismNetwork;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.registry.ModSpawnPlacements;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Phantomism.MOD_ID)
public class Phantomism {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "phantomism";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Phantomism(IEventBus modEventBus, ModContainer modContainer) {
        PhantomismContent.register(modEventBus);
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(ModSpawnPlacements::register);
        modEventBus.addListener(PhantomismNetwork::registerPayloads);

        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.SYNCED, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(PhantomismContent.BLACK_PHANTOM.get(), BlackPhantom.createAttributes().build());
        event.put(PhantomismContent.FIRE_PHANTOM.get(), FirePhantom.createAttributes().build());
        event.put(PhantomismContent.FROST_PHANTOM.get(), FrostPhantom.createAttributes().build());
        event.put(PhantomismContent.EARTH_PHANTOM.get(), EarthPhantom.createAttributes().build());
        event.put(PhantomismContent.WATER_PHANTOM.get(), WaterPhantom.createAttributes().build());
        event.put(PhantomismContent.STORM_PHANTOM.get(), StormPhantom.createAttributes().build());
        event.put(PhantomismContent.SAND_PHANTOM.get(), SandPhantom.createAttributes().build());
        event.put(PhantomismContent.GOLD_PHANTOM.get(), GoldPhantom.createAttributes().build());
        event.put(PhantomismContent.CHERRY_PHANTOM.get(), CherryPhantom.createAttributes().build());
        event.put(PhantomismContent.PHANTOM_DRAGON.get(), net.ronm19.phantomism.entity.PhantomDragon.createAttributes().build());
        event.put(PhantomismContent.VIOLET_PHANTOM.get(), net.ronm19.phantomism.entity.VioletPhantom.createAttributes().build());
        event.put(PhantomismContent.TOXIC_PHANTOM.get(), net.ronm19.phantomism.entity.ToxicPhantom.createAttributes().build());
        event.put(PhantomismContent.SCULK_PHANTOM.get(), net.ronm19.phantomism.entity.SculkPhantom.createAttributes().build());
        event.put(PhantomismContent.RAVAGER_PHANTOM.get(), net.ronm19.phantomism.entity.RavagerPhantom.createAttributes().build());
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob
                && event.getNewAboutToBeSetTarget() instanceof net.ronm19.phantomism.entity.AbstractPhantom phantom
                && phantom.isTargetSuppressed(mob)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)
                || !(mob.getTarget() instanceof net.ronm19.phantomism.entity.AbstractPhantom phantom)
                || !phantom.isTargetSuppressed(mob)) {
            return;
        }

        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            net.ronm19.phantomism.entity.AbstractPhantom.tickDismountFallProtection(event.getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (net.ronm19.phantomism.entity.AbstractPhantom.isDismountFallProtected(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFireProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof SmallFireball fireball
                && fireball.getOwner() instanceof FirePhantom phantom
                && (event.getRayTraceResult() instanceof BlockHitResult
                        || event.getRayTraceResult() instanceof EntityHitResult hit
                                && !phantom.canHitWithFireProjectile(hit.getEntity()))) {
            event.setCanceled(true);
            fireball.discard();
        }
    }
}
