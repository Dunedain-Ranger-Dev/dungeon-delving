package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.DungeonTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Placed in each dungeon's start room. Right-click to end the run and keep everything found. */
public class DungeonExitBlock extends Block {
    public static final MapCodec<DungeonExitBlock> CODEC = simpleCodec(DungeonExitBlock::new);

    public DungeonExitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DungeonExitBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (DungeonTravel.exit(serverPlayer).isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("This exit doesn't lead anywhere for you.").withStyle(ChatFormatting.RED));
        }
        return InteractionResult.CONSUME;
    }
}
