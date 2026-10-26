package net.ronm19.phantomism.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.ronm19.phantomism.codex.CodexService;

public final class PhantomCodexItem extends Item {
    public PhantomCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            CodexService.send(serverPlayer, true);
        }
        return InteractionResult.SUCCESS;
    }
}
