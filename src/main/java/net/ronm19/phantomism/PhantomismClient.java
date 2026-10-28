package net.ronm19.phantomism;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.ronm19.phantomism.client.renderer.BlackPhantomRenderer;
import net.ronm19.phantomism.client.renderer.PhantomGeoRenderer;
import net.ronm19.phantomism.client.renderer.FirePhantomRenderer;
import net.ronm19.phantomism.client.renderer.FrostPhantomRenderer;
import net.ronm19.phantomism.client.renderer.EarthPhantomRenderer;
import net.ronm19.phantomism.client.renderer.EarthWallRenderer;
import net.ronm19.phantomism.client.renderer.WaterPhantomRenderer;
import net.ronm19.phantomism.client.renderer.StormPhantomRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.ronm19.phantomism.client.model.PhantomismAdultModel;
import net.ronm19.phantomism.client.model.PhantomismBabyModel;
import net.ronm19.phantomism.client.screen.PhantomStaffScreen;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.network.PhantomismNetwork.Action;
import net.ronm19.phantomism.network.PhantomismNetwork.RiddenProjectileAbilityPayload;
import net.ronm19.phantomism.network.PhantomismNetwork.StaffCommandPayload;
import net.ronm19.phantomism.network.PhantomismNetwork.BlockCommandPayload;
import net.ronm19.phantomism.registry.PhantomismContent;
import net.ronm19.phantomism.registry.ModParticles;
import net.ronm19.phantomism.client.particle.SandDustParticle;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Phantomism.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Phantomism.MOD_ID, value = Dist.CLIENT)
public class PhantomismClient {
    private static final net.ronm19.phantomism.client.screen.InfernalHeldFire INFERNAL_HELD_FIRE = new net.ronm19.phantomism.client.screen.InfernalHeldFire();
    private static final net.ronm19.phantomism.client.screen.CreatorHeldFire CREATOR_HELD_FIRE = new net.ronm19.phantomism.client.screen.CreatorHeldFire();
    private static final KeyMapping RANGED_ABILITY_KEY = new KeyMapping(
            "key.phantomism.ranged_ability",
            InputConstants.KEY_G,
            KeyMapping.Category.MISC
    );
    private static final KeyMapping OMEN_MELEE_KEY = new KeyMapping("key.phantomism.omen_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping ANCIENT_MELEE_KEY = new KeyMapping("key.phantomism.ancient_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping REAPER_MELEE_KEY = new KeyMapping("key.phantomism.reaper_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping PRIMORDIAL_MELEE_KEY = new KeyMapping("key.phantomism.primordial_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping AUTUMN_MELEE_KEY = new KeyMapping("key.phantomism.autumn_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping HALLOWEEN_MELEE_KEY = new KeyMapping("key.phantomism.halloween_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping NEW_YEARS_MELEE_KEY = new KeyMapping("key.phantomism.new_years_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping CREATOR_MELEE_KEY = new KeyMapping("key.phantomism.creator_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping VALENTINES_MELEE_KEY = new KeyMapping("key.phantomism.valentines_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping ST_PATRICKS_MELEE_KEY = new KeyMapping("key.phantomism.st_patricks_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping FIREWORK_MELEE_KEY = new KeyMapping("key.phantomism.firework_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping EASTER_MELEE_KEY = new KeyMapping("key.phantomism.easter_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping CHRISTMAS_MELEE_KEY = new KeyMapping("key.phantomism.christmas_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping LUNAR_MELEE_KEY = new KeyMapping("key.phantomism.lunar_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping INFERNAL_MELEE_KEY = new KeyMapping("key.phantomism.infernal_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping ILLAGER_MELEE_KEY = new KeyMapping("key.phantomism.illager_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping SOLAR_MELEE_KEY = new KeyMapping("key.phantomism.solar_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping SPORE_MELEE_KEY = new KeyMapping("key.phantomism.spore_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping GALE_MELEE_KEY = new KeyMapping("key.phantomism.gale_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping OBSIDIAN_MELEE_KEY = new KeyMapping("key.phantomism.obsidian_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping CRYSTAL_MELEE_KEY = new KeyMapping("key.phantomism.crystal_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping PLAGUE_MELEE_KEY = new KeyMapping("key.phantomism.plague_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping SHADOW_MELEE_KEY = new KeyMapping("key.phantomism.shadow_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping ECLIPSE_MELEE_KEY = new KeyMapping("key.phantomism.eclipse_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping MIRAGE_MELEE_KEY = new KeyMapping("key.phantomism.mirage_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping GOLEM_MELEE_KEY = new KeyMapping("key.phantomism.golem_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping KING_MELEE_KEY = new KeyMapping("key.phantomism.king_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping DARK_MELEE_KEY = new KeyMapping("key.phantomism.dark_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping VOID_MELEE_KEY = new KeyMapping("key.phantomism.void_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping ANGEL_MELEE_KEY = new KeyMapping("key.phantomism.angel_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping SPECTRAL_MELEE_KEY = new KeyMapping("key.phantomism.spectral_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping DEMON_MELEE_KEY = new KeyMapping("key.phantomism.demon_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static final KeyMapping SENTINEL_MELEE_KEY = new KeyMapping("key.phantomism.sentinel_melee", InputConstants.KEY_LCONTROL, KeyMapping.Category.MISC);
    private static int attackTargetRecipientId = -2;
    private static int blockRecipientId = -2;
    private static Action blockSelectionAction;
    private static Action targetSelectionAction = Action.ATTACK_TARGET;
    private static net.minecraft.client.multiplayer.ClientLevel selectionLevel;
    private static long selectionExpires;

    public PhantomismClient(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, net.ronm19.phantomism.client.vfx.VfxQualitySettings.SPEC);
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(PhantomismClient::onStaffRightClick);
        NeoForge.EVENT_BUS.addListener(PhantomismClient::onStaffEntityClick);
        NeoForge.EVENT_BUS.addListener(PhantomismClient::onTargetSelectionMouse);
        NeoForge.EVENT_BUS.addListener(PhantomismClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PhantomismClient::onRidingInput);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {

    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RANGED_ABILITY_KEY);
        event.register(SENTINEL_MELEE_KEY);
        event.register(DEMON_MELEE_KEY);
        event.register(SPECTRAL_MELEE_KEY);
        event.register(ANGEL_MELEE_KEY);
        event.register(VOID_MELEE_KEY);
        event.register(DARK_MELEE_KEY);
        event.register(KING_MELEE_KEY);
        event.register(AUTUMN_MELEE_KEY);
        event.register(HALLOWEEN_MELEE_KEY);
        event.register(NEW_YEARS_MELEE_KEY);
        event.register(CREATOR_MELEE_KEY);
        event.register(VALENTINES_MELEE_KEY);
        event.register(ST_PATRICKS_MELEE_KEY);
        event.register(FIREWORK_MELEE_KEY);
        event.register(EASTER_MELEE_KEY);
        event.register(CHRISTMAS_MELEE_KEY);
        event.register(LUNAR_MELEE_KEY);
        event.register(INFERNAL_MELEE_KEY);
        event.register(ILLAGER_MELEE_KEY);
        event.register(SOLAR_MELEE_KEY);
        event.register(SPORE_MELEE_KEY);
        event.register(GALE_MELEE_KEY);
        event.register(OBSIDIAN_MELEE_KEY);
        event.register(CRYSTAL_MELEE_KEY);
        event.register(PLAGUE_MELEE_KEY);
        event.register(SHADOW_MELEE_KEY);
        event.register(ECLIPSE_MELEE_KEY);
        event.register(MIRAGE_MELEE_KEY);
        event.register(GOLEM_MELEE_KEY);
        event.register(PRIMORDIAL_MELEE_KEY);
        event.register(REAPER_MELEE_KEY);
        event.register(ANCIENT_MELEE_KEY);
        event.register(OMEN_MELEE_KEY);
    }

    @SubscribeEvent
    static void onMountedChargeKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == InputConstants.PRESS
                && (event.getKey() == InputConstants.KEY_LCONTROL || event.getKey() == InputConstants.KEY_RCONTROL)
                && minecraft.gui.screen() == null && minecraft.player != null
                && minecraft.player.getVehicle() instanceof AbstractPhantom phantom) {
            if (phantom instanceof net.ronm19.phantomism.entity.RavagerPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSiegeChargePayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.EchoPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenEchoStrikePayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.BloodPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenBloodStrikePayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.VampirePhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenVampireBitePayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.GravityPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenGravityCrushPayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.RiftPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenRiftDashPayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.ChronoPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenChronoDivePayload(phantom.getId()));
            } else if (phantom instanceof net.ronm19.phantomism.entity.WitherPhantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenWitherBitePayload(phantom.getId()));
            }
        }
    }

    @SubscribeEvent
    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SANDSTORM_DUST.get(), SandDustParticle.Provider::new);
        event.registerSpriteSet(ModParticles.PHANTOM_ENERGY.get(), net.ronm19.phantomism.client.particle.PhantomEnergyParticle.Provider::new);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((attackTargetRecipientId != -2 || blockSelectionAction != null)
                && (minecraft.player == null || minecraft.level != selectionLevel
                || minecraft.level.getGameTime() > selectionExpires
                || !isHoldingStaff(minecraft.player))) {
            clearStaffSelection();
        }
        while (OMEN_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.OmenPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenOmenDivePayload(phantom.getId()));
            }
        }
        while (ANCIENT_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.AncientPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenAncientDivePayload(phantom.getId()));
            }
        }
        while (REAPER_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.ReaperPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenReaperDivePayload(phantom.getId()));
            }
        }
        while (PRIMORDIAL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.PrimordialPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenPrimordialDivePayload(phantom.getId()));
            }
        }
        while (AUTUMN_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.AutumnPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenAutumnRushPayload(phantom.getId()));
            }
        }
        while (HALLOWEEN_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.HalloweenPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenHalloweenDivePayload(phantom.getId()));
            }
        }
        while (NEW_YEARS_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.NewYearsPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenNewYearsDashPayload(phantom.getId()));
            }
        }
        while (CREATOR_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.CreatorPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenCreatorStepPayload(phantom.getId()));
            }
        }
        while (VALENTINES_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.ValentinesPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenValentinesDashPayload(phantom.getId()));
            }
        }
        while (ST_PATRICKS_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.StPatricksPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenStPatricksDashPayload(phantom.getId()));
            }
        }
        while (FIREWORK_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.FireworkPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenFireworkRushPayload(phantom.getId()));
            }
        }
        while (EASTER_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.EasterPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenEasterLeapPayload(phantom.getId()));
            }
        }
        while (CHRISTMAS_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.ChristmasPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenChristmasDivePayload(phantom.getId()));
            }
        }
        while (LUNAR_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.LunarPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenLunarFangPayload(phantom.getId()));
            }
        }
        while (INFERNAL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.InfernalPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenInfernalHellrushPayload(phantom.getId()));
            }
        }
        while (ILLAGER_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.IllagerPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenIllagerWrathPayload(phantom.getId()));
            }
        }
        while (SOLAR_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.SolarPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSolarBlitzPayload(phantom.getId()));
            }
        }
        while (SPORE_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.SporePhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSporeDivePayload(phantom.getId()));
            }
        }
        while (GALE_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.GalePhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenGaleRushPayload(phantom.getId()));
            }
        }
        while (OBSIDIAN_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.ObsidianPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenObsidianRamPayload(phantom.getId()));
            }
        }
        while (CRYSTAL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.CrystalPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenCrystalCounterPayload(phantom.getId()));
            }
        }
        while (PLAGUE_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.PlaguePhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenPlagueDivePayload(phantom.getId()));
            }
        }
        while (SHADOW_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.ShadowPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenShadowDivePayload(phantom.getId()));
            }
        }
        while (ECLIPSE_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.EclipsePhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenEclipseDivePayload(phantom.getId()));
            }
        }
        while (MIRAGE_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.MiragePhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenMirageDivePayload(phantom.getId()));
            }
        }
        while (GOLEM_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.GolemPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenGolemDivePayload(phantom.getId()));
            }
        }
        while (KING_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.PhantomKing phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenKingDivePayload(phantom.getId()));
            }
        }
        while (DARK_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.DarkPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenDarkDivePayload(phantom.getId()));
            }
        }
        while (VOID_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.VoidPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenVoidDivePayload(phantom.getId()));
            }
        }
        while (ANGEL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.AngelPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenAngelDivePayload(phantom.getId()));
            }
        }
        while (SPECTRAL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.SpectralPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSpectralDivePayload(phantom.getId()));
            }
        }
        while (DEMON_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.DemonPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenDemonStrikePayload(phantom.getId()));
            }
        }
        while (SENTINEL_MELEE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.SentinelPhantom phantom) {
                ClientPacketDistributor.sendToServer(new net.ronm19.phantomism.network.PhantomismNetwork.RiddenSentinelRepulsePayload(phantom.getId()));
            }
        }
        while (RANGED_ABILITY_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null
                    && minecraft.player != null
                    && minecraft.player.getVehicle() instanceof AbstractPhantom phantom) {
                if (phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom) {
                    if (minecraft.level != null && phantom.getControllingPassenger() == minecraft.player && phantom.isOwnedBy(minecraft.player)
                            && CREATOR_HELD_FIRE.poll(true, phantom.getUUID(), minecraft.level, minecraft.level.getGameTime())) {
                        ClientPacketDistributor.sendToServer(new RiddenProjectileAbilityPayload(phantom.getId()));
                    }
                } else if (!(phantom instanceof net.ronm19.phantomism.entity.InfernalPhantom)
                        || minecraft.level != null && phantom.getControllingPassenger() == minecraft.player && phantom.isOwnedBy(minecraft.player)
                        && INFERNAL_HELD_FIRE.poll(true, phantom.getUUID(), minecraft.level, minecraft.level.getGameTime())) {
                    ClientPacketDistributor.sendToServer(new RiddenProjectileAbilityPayload(phantom.getId()));
                }
            }
        }
        var rapidPhantom = minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.InfernalPhantom infernal ? infernal : null;
        boolean held = minecraft.gui.screen() == null && rapidPhantom != null && rapidPhantom.getControllingPassenger() == minecraft.player && rapidPhantom.isOwnedBy(minecraft.player) && RANGED_ABILITY_KEY.isDown();
        if (INFERNAL_HELD_FIRE.poll(held, rapidPhantom == null ? null : rapidPhantom.getUUID(), minecraft.level, minecraft.level == null ? 0 : minecraft.level.getGameTime())) { ClientPacketDistributor.sendToServer(new RiddenProjectileAbilityPayload(rapidPhantom.getId())); }
        var creator = minecraft.player != null && minecraft.player.getVehicle() instanceof net.ronm19.phantomism.entity.CreatorPhantom aethar ? aethar : null;
        boolean creatorHeld = minecraft.gui.screen() == null && creator != null && creator.getControllingPassenger() == minecraft.player && creator.isOwnedBy(minecraft.player) && RANGED_ABILITY_KEY.isDown();
        if (CREATOR_HELD_FIRE.poll(creatorHeld, creator == null ? null : creator.getUUID(), minecraft.level, minecraft.level == null ? 0 : minecraft.level.getGameTime())) { ClientPacketDistributor.sendToServer(new RiddenProjectileAbilityPayload(creator.getId())); }
    }

    static void onRidingInput(MovementInputUpdateEvent event) {
        if (event.getEntity().getVehicle() instanceof AbstractPhantom phantom
                && phantom.getControllingPassenger() == event.getEntity()) {
            phantom.updateClientRidingInput(event.getInput().keyPresses);
        }
    }

    static void onStaffRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player
                || !event.getItemStack().is(PhantomismContent.PHANTOM_STAFF.get())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (attackTargetRecipientId != -2 || blockSelectionAction != null || minecraft.hitResult instanceof EntityHitResult) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        minecraft.setScreenAndShow(new PhantomStaffScreen());
    }

    static void onTargetSelectionMouse(InputEvent.MouseButton.Pre event) {
        if ((attackTargetRecipientId == -2 && blockSelectionAction == null)
                || event.getAction() != InputConstants.PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.gui.screen() != null) {
            return;
        }
        if (player == null || minecraft.level == null || !isHoldingStaff(player)) {
            clearStaffSelection();
            return;
        }
        if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            event.setCanceled(true);
            clearStaffSelection();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("screen.phantomism.staff.selection_canceled"));
            return;
        }
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }

        event.setCanceled(true);
        Vec3 start = player.getEyePosition();
        if (blockSelectionAction != null) {
            var hit = player.level().clip(new ClipContext(start, start.add(player.getViewVector(1.0F).scale(6.0D)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            if (blockSelectionAction == Action.SET_HOME
                    && (!player.isShiftKeyDown() || !(player.level().getBlockState(hit.getBlockPos()).getBlock()
                    instanceof net.minecraft.world.level.block.BedBlock))) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("screen.phantomism.staff.select_home"));
                return;
            }
            ClientPacketDistributor.sendToServer(new BlockCommandPayload(blockSelectionAction.networkId(), blockRecipientId, hit.getBlockPos()));
            clearStaffSelection();
            return;
        }
        Vec3 end = start.add(player.getViewVector(1.0F).scale(32.0D));
        BlockHitResult blockHit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        Vec3 ray = end.subtract(start);
        AABB searchArea = player.getBoundingBox().expandTowards(ray).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                searchArea,
                entity -> entity != player
                        && EntitySelector.CAN_BE_PICKED.test(entity)
                        && entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && (targetSelectionAction == Action.RIFT_SEND_ALLY ? livingEntity instanceof net.minecraft.world.entity.OwnableEntity owned && java.util.Objects.equals(owned.getOwnerReference(), net.minecraft.world.entity.EntityReference.of(player)) : !player.isAlliedTo(livingEntity)),
                start.distanceToSqr(end)
        );
        if (entityHit != null) {
            ClientPacketDistributor.sendToServer(new StaffCommandPayload(
                    targetSelectionAction.networkId(),
                    attackTargetRecipientId,
                    entityHit.getEntity().getId()
            ));
            clearStaffSelection();
        }
    }

    static void onStaffEntityClick(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player
                || !player.getItemInHand(event.getHand()).is(PhantomismContent.PHANTOM_STAFF.get())
                || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        if (attackTargetRecipientId != -2) {
            if (!isValidPlayerTarget(player, target)) {
                return;
            }
            ClientPacketDistributor.sendToServer(new StaffCommandPayload(
                    targetSelectionAction.networkId(),
                    attackTargetRecipientId,
                    target.getId()
            ));
            clearStaffSelection();
        } else if (!(target instanceof AbstractPhantom) && isValidPlayerTarget(player, target)) {
            ClientPacketDistributor.sendToServer(new StaffCommandPayload(
                    Action.ATTACK_TARGET.networkId(),
                    -1,
                    target.getId()
            ));
        } else {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean isValidPlayerTarget(Player player, LivingEntity target) {
        if (targetSelectionAction == Action.RIFT_SEND_ALLY) { return target.isAlive() && target instanceof net.minecraft.world.entity.OwnableEntity owned && java.util.Objects.equals(owned.getOwnerReference(), net.minecraft.world.entity.EntityReference.of(player)); }
        return target != player
                && target.isAlive()
                && !(target instanceof AbstractPhantom)
                && !player.isAlliedTo(target);
    }

    public static void beginAttackTargetSelection(int recipientId) {
        clearStaffSelection();
        attackTargetRecipientId = recipientId;
        startSelectionTimer();
    }

    public static void beginRiftCompanionSelection(int recipientId) { beginAttackTargetSelection(recipientId); targetSelectionAction = Action.RIFT_SEND_ALLY; }

    public static void beginBlockSelection(Action action, int recipientId) {
        clearStaffSelection();
        blockSelectionAction = action;
        blockRecipientId = recipientId;
        startSelectionTimer();
    }

    private static void startSelectionTimer() {
        selectionLevel = Minecraft.getInstance().level;
        selectionExpires = selectionLevel == null ? 0L : selectionLevel.getGameTime() + 1200L;
    }

    private static void clearStaffSelection() {
        attackTargetRecipientId = -2;
        targetSelectionAction = Action.ATTACK_TARGET;
        blockSelectionAction = null;
        blockRecipientId = -2;
        selectionLevel = null;
    }

    public static void cancelStaffSelection() { clearStaffSelection(); }

    private static boolean isHoldingStaff(Player player) {
        return player.getMainHandItem().is(PhantomismContent.PHANTOM_STAFF.get())
                || player.getOffhandItem().is(PhantomismContent.PHANTOM_STAFF.get());
    }

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PhantomismAdultModel.LAYER_LOCATION, PhantomismAdultModel::createBodyLayer);
        event.registerLayerDefinition(PhantomismBabyModel.LAYER_LOCATION, PhantomismBabyModel::createBodyLayer);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PhantomismContent.BLACK_PHANTOM.get(),
                context -> new PhantomGeoRenderer<>(context, "black_phantom", BlackPhantomRenderer.ACCESSORIES));
        event.registerEntityRenderer(PhantomismContent.FIRE_PHANTOM.get(),
                context -> new PhantomGeoRenderer<>(context, "fire_phantom", FirePhantomRenderer.ACCESSORIES));
        event.registerEntityRenderer(PhantomismContent.FROST_PHANTOM.get(),
                context -> new PhantomGeoRenderer<>(context, "frost_phantom", FrostPhantomRenderer.ACCESSORIES));
        event.registerEntityRenderer(PhantomismContent.EARTH_PHANTOM.get(),
                context -> new PhantomGeoRenderer<>(context, "earth_phantom", EarthPhantomRenderer.ACCESSORIES));
        event.registerEntityRenderer(PhantomismContent.EARTH_WALL.get(), EarthWallRenderer::new);
        event.registerEntityRenderer(PhantomismContent.WATER_PHANTOM.get(), WaterPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.STORM_PHANTOM.get(), StormPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SAND_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.SandPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SAND_BOLT.get(),
                context -> new ThrownItemRenderer<>(context, 0.6F, false));
        event.registerEntityRenderer(PhantomismContent.GOLD_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.GoldPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GOLD_DUST.get(),
                context -> new ThrownItemRenderer<>(context, 0.65F, true));
        event.registerEntityRenderer(PhantomismContent.CHERRY_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.CherryPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PETAL_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.45F, true));
        event.registerEntityRenderer(PhantomismContent.PHANTOM_DRAGON.get(),
                net.ronm19.phantomism.client.renderer.PhantomDragonRenderer::new);
        event.registerEntityRenderer(PhantomismContent.DRAGON_FIREBALL.get(),
                context -> new ThrownItemRenderer<>(context, 0.8F, true));
        event.registerEntityRenderer(PhantomismContent.VIOLET_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.VioletPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VIOLET_LANCE.get(),
                context -> new ThrownItemRenderer<>(context, 0.55F, true));
        event.registerEntityRenderer(PhantomismContent.TOXIC_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.ToxicPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SCULK_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.SculkPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.RAVAGER_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.RavagerPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.END_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.EndPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ZOMBIE_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.ZombiePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.INFECTED_SPIT.get(),
                context -> new ThrownItemRenderer<>(context, 0.35F, true));
        event.registerEntityRenderer(PhantomismContent.SKELETON_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.SkeletonPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ECHO_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.EchoPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.BLOOD_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.BloodPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VAMPIRE_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.VampirePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GRAVITY_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.GravityPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.OMEN_PHANTOM.get(), net.ronm19.phantomism.client.renderer.OmenPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.OMEN_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ANCIENT_PHANTOM.get(), net.ronm19.phantomism.client.renderer.AncientPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ANCIENT_STONE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.REAPER_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ReaperPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.REAPER_SHARD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PRIMORDIAL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.PrimordialPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PRIMORDIAL_STAR.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.AUTUMN_PHANTOM.get(), net.ronm19.phantomism.client.renderer.AutumnPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.HALLOWEEN_PHANTOM.get(), net.ronm19.phantomism.client.renderer.HalloweenPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.NEW_YEARS_PHANTOM.get(), net.ronm19.phantomism.client.renderer.NewYearsPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CREATOR_PHANTOM.get(), net.ronm19.phantomism.client.renderer.CreatorPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VALENTINES_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ValentinesPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ST_PATRICKS_PHANTOM.get(), net.ronm19.phantomism.client.renderer.StPatricksPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.NEW_YEARS_CLOCK_PROJECTILE.get(), net.ronm19.phantomism.client.renderer.NewYearsClockRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VALENTINES_HEART_PROJECTILE.get(), net.ronm19.phantomism.client.renderer.ValentinesHeartRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ST_PATRICKS_SHAMROCK_PROJECTILE.get(), net.ronm19.phantomism.client.renderer.StPatricksShamrockRenderer::new);
        event.registerEntityRenderer(PhantomismContent.FIREWORK_PHANTOM.get(), net.ronm19.phantomism.client.renderer.FireworkPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.FIREWORK_ROCKET_PROJECTILE.get(), net.ronm19.phantomism.client.renderer.FireworkRocketRenderer::new);
        event.registerEntityRenderer(PhantomismContent.EASTER_PHANTOM.get(), net.ronm19.phantomism.client.renderer.EasterPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CHRISTMAS_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ChristmasPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.EASTER_EGG_PROJECTILE.get(), net.ronm19.phantomism.client.renderer.EasterEggProjectileRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CHRISTMAS_SNOWBALL.get(), net.ronm19.phantomism.client.renderer.ChristmasSnowballRenderer::new);
        event.registerEntityRenderer(PhantomismContent.EASTER_ORBIT_EGG.get(), net.ronm19.phantomism.client.renderer.EasterOrbitEggRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CHRISTMAS_GIFT.get(), net.ronm19.phantomism.client.renderer.ChristmasGiftRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PUMPKIN_GAZE.get(), net.ronm19.phantomism.client.renderer.PumpkinGazeRenderer::new);
        event.registerEntityRenderer(PhantomismContent.LUNAR_PHANTOM.get(), net.ronm19.phantomism.client.renderer.LunarPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.AUTUMN_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.MOON_LANCE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.INFERNAL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.InfernalPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.INFERNAL_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ILLAGER_PHANTOM.get(), net.ronm19.phantomism.client.renderer.IllagerPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ILLAGER_BOLT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ILLAGER_SPECTRAL_VEX.get(), net.minecraft.client.renderer.entity.VexRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ILLAGER_FANGS.get(), net.minecraft.client.renderer.entity.EvokerFangsRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SOLAR_PHANTOM.get(), net.ronm19.phantomism.client.renderer.SolarPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SPORE_PHANTOM.get(), net.ronm19.phantomism.client.renderer.SporePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SPORE_BREATH.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GALE_PHANTOM.get(), net.ronm19.phantomism.client.renderer.GalePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.WIND_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.OBSIDIAN_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ObsidianPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.OBSIDIAN_SHARD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CRYSTAL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.CrystalPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CRYSTAL_SHARD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PLAGUE_PHANTOM.get(), net.ronm19.phantomism.client.renderer.PlaguePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PLAGUE_SPIT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SHADOW_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ShadowPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SHADOW_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ECLIPSE_PHANTOM.get(), net.ronm19.phantomism.client.renderer.EclipsePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ECLIPSE_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.MIRAGE_PHANTOM.get(), net.ronm19.phantomism.client.renderer.MiragePhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.MIRAGE_DECOY.get(), net.ronm19.phantomism.client.renderer.MirageDecoyRenderer::new);
        event.registerEntityRenderer(PhantomismContent.MIRAGE_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GOLEM_PHANTOM.get(), net.ronm19.phantomism.client.renderer.GolemPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GOLEM_FIST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.PHANTOM_KING.get(), net.ronm19.phantomism.client.renderer.PhantomKingRenderer::new);
        event.registerEntityRenderer(PhantomismContent.DARK_PHANTOM.get(), net.ronm19.phantomism.client.renderer.DarkPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.DARK_PROJECTILE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VOID_PHANTOM.get(), net.ronm19.phantomism.client.renderer.VoidPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.VOID_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ANGEL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.AngelPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ANGEL_SMITE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SPECTRAL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.SpectralPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SPECTRAL_LANCE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.DEMON_PHANTOM.get(), net.ronm19.phantomism.client.renderer.DemonPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.DEMON_METEOR.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SENTINEL_PHANTOM.get(), net.ronm19.phantomism.client.renderer.SentinelPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.RIFT_PHANTOM.get(), net.ronm19.phantomism.client.renderer.RiftPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.RIFT_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(PhantomismContent.CHRONO_PHANTOM.get(), net.ronm19.phantomism.client.renderer.ChronoPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.TIME_BOLT.get(), context -> new ThrownItemRenderer<>(context, .18F, true));
        event.registerEntityRenderer(PhantomismContent.WITHER_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.WitherPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.WITHER_PHANTOM_SKULL.get(), net.minecraft.client.renderer.entity.WitherSkullRenderer::new);
        event.registerEntityRenderer(PhantomismContent.GRAVITY_WELL.get(),
                context -> new ThrownItemRenderer<>(context, 0.2F, true));
        event.registerEntityRenderer(PhantomismContent.VAMPIRE_BOLT.get(),
                context -> new ThrownItemRenderer<>(context, 0.18F, true));
        event.registerEntityRenderer(PhantomismContent.BLOOD_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.22F, true));
        event.registerEntityRenderer(PhantomismContent.ECHO_PULSE.get(),
                context -> new ThrownItemRenderer<>(context, 0.25F, true));
        event.registerEntityRenderer(PhantomismContent.HUSK_PHANTOM.get(),
                net.ronm19.phantomism.client.renderer.HuskPhantomRenderer::new);
        event.registerEntityRenderer(PhantomismContent.SKELETON_ARROW.get(),
                net.ronm19.phantomism.client.renderer.SkeletonArrowRenderer::new);
        event.registerEntityRenderer(PhantomismContent.ACID_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.55F, true));
        event.registerEntityRenderer(PhantomismContent.STORM_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.7F, true));
        event.registerEntityRenderer(PhantomismContent.WATER_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.8F, true));
        event.registerEntityRenderer(PhantomismContent.EARTH_BOMB.get(),
                context -> new ThrownItemRenderer<>(context, 0.8F, true));
        event.registerEntityRenderer(PhantomismContent.FROST_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.8F, true));
    }
}
