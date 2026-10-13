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
    private static final KeyMapping RANGED_ABILITY_KEY = new KeyMapping(
            "key.phantomism.ranged_ability",
            InputConstants.KEY_G,
            KeyMapping.Category.MISC
    );
    private static int attackTargetRecipientId = -2;
    private static int blockRecipientId = -2;
    private static Action blockSelectionAction;
    private static net.minecraft.client.multiplayer.ClientLevel selectionLevel;
    private static long selectionExpires;

    public PhantomismClient(ModContainer container) {
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
        while (RANGED_ABILITY_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null
                    && minecraft.player != null
                    && minecraft.player.getVehicle() instanceof AbstractPhantom phantom) {
                ClientPacketDistributor.sendToServer(new RiddenProjectileAbilityPayload(phantom.getId()));
            }
        }
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
            ClientPacketDistributor.sendToServer(new BlockCommandPayload(blockSelectionAction.ordinal(), blockRecipientId, hit.getBlockPos()));
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
                        && !player.isAlliedTo(livingEntity),
                start.distanceToSqr(end)
        );
        if (entityHit != null) {
            ClientPacketDistributor.sendToServer(new StaffCommandPayload(
                    Action.ATTACK_TARGET.ordinal(),
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
                    Action.ATTACK_TARGET.ordinal(),
                    attackTargetRecipientId,
                    target.getId()
            ));
            clearStaffSelection();
        } else if (!(target instanceof AbstractPhantom) && isValidPlayerTarget(player, target)) {
            ClientPacketDistributor.sendToServer(new StaffCommandPayload(
                    Action.ATTACK_TARGET.ordinal(),
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
        blockSelectionAction = null;
        blockRecipientId = -2;
        selectionLevel = null;
    }

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
