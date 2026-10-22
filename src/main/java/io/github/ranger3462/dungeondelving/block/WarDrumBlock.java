package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonWaves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The heart of a wave defense challenge room. Strike it to seal the room and call the waves; survive them all for the
 * room's key. Leave the block above it clear: the reward chest appears there. Can't be broken.
 */
public class WarDrumBlock extends Block {
    public static final MapCodec<WarDrumBlock> CODEC = simpleCodec(WarDrumBlock::new);

    public WarDrumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            DungeonWaves.strikeDrum(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
