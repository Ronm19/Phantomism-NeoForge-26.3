package net.ronm19.phantomism.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.entity.SolarPhantom;

public final class SolarEnvironment {
    private SolarEnvironment() {}
    public static boolean permission(SolarPhantom solar, BlockPos position) {
        if (!(solar.level() instanceof ServerLevel level) || !solar.enabled() || !level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)
                || !(solar.getOwner() instanceof ServerPlayer owner) || !owner.getAbilities().mayBuild
                || owner.blockActionRestricted(level, position, owner.gameMode.getGameModeForPlayer()) || !level.mayInteract(owner, position)
                || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !EventHooks.canEntityGrief(level, solar)
                || !CommonHooks.canEntityDestroy(level, position, solar) || level.getBlockEntity(position) != null) { return false; }
        var state = level.getBlockState(position);
        return !CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), owner, position, state).isCanceled() && level.getBlockState(position) == state;
    }
    public static void tick(SolarPhantom solar) {
        if (!(solar.level() instanceof ServerLevel level)) { return; }
        if (Config.solarFlag(Config.SOLAR_AURA, true)) { level.sendParticles(ParticleTypes.END_ROD, solar.getX(), solar.getY() + .4D, solar.getZ(), solar.empowered() ? 3 : 1, .6D, .25D, .6D, .01D); }
        if (Config.solarFlag(Config.SOLAR_ILLUMINATION, true) && !solar.empowered()) {
            for (var ward : solar.allies(level, solar.position(), 8)) { if (ward instanceof ServerPlayer) { ward.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false), solar); } }
        }
        if (!solar.empowered() || !Config.solarFlag(Config.SOLAR_MELTING, false)) { return; }
        for (int offset = 0; offset < 4; offset++) {
            BlockPos position = solar.blockPosition().below().offset(offset % 2, 0, offset / 2);
            if (!level.hasChunkAt(position)) { continue; }
            var state = level.getBlockState(position);
            if (!(state.is(Blocks.SNOW) || state.is(Blocks.FROSTED_ICE)) || !permission(solar, position)) { continue; }
            level.setBlockAndUpdate(position, state.is(Blocks.SNOW) ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState()); break;
        }
    }
    public static boolean scorch(SolarPhantom solar, BlockPos position) {
        if (!(solar.level() instanceof ServerLevel level) || !Config.solarFlag(Config.SOLAR_SCORCHING, false)
                || !level.hasChunkAt(position) || !level.getBlockState(position).is(Blocks.GRASS_BLOCK) || !permission(solar, position)) { return false; }
        return level.setBlockAndUpdate(position, Blocks.DIRT.defaultBlockState());
    }
}
