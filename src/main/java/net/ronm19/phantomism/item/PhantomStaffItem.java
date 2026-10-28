package net.ronm19.phantomism.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomStaffItem extends Item {
    public PhantomStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractPhantom phantom)) {
            return InteractionResult.PASS;
        }

        if (phantom.isTame()) {
            return phantom.isOwnedBy(player) ? InteractionResult.PASS : InteractionResult.FAIL;
        }

        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom && net.ronm19.phantomism.CreatorCertification.acquisitionLocked()) { return InteractionResult.FAIL; }
        if (phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom && !player.isCreative()) { player.sendOverlayMessage(Component.translatable("message.phantomism.creator.eye_required")); return InteractionResult.FAIL; }
        if (phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && !net.ronm19.phantomism.progression.CreatorBonds.get(serverPlayer.level().getServer()).claim(player.getUUID(), phantom.getUUID())) { return InteractionResult.FAIL; }
        phantom.tame(player);
        phantom.setOrder(AbstractPhantom.Order.FOLLOW);
        player.sendOverlayMessage(Component.translatable("message.phantomism.tamed", phantom.getDisplayName()));
        return InteractionResult.SUCCESS_SERVER;
    }
}
