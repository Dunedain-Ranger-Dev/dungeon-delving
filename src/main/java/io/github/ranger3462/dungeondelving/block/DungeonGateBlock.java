package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
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

/** Right-click to start a dungeon run. Works anywhere outside the dungeon dimension. */
public class DungeonGateBlock extends Block {
    public static final MapCodec<DungeonGateBlock> CODEC = simpleCodec(DungeonGateBlock::new);

    public DungeonGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DungeonGateBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() == DungeonDimension.DUNGEON || DungeonRegions.get().run(player.getUUID()).isPresent()) {
            serverPlayer.sendSystemMessage(Component.literal("You're already in a dungeon.").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        // Choosing a higher tier comes with DD-32; the gate always opens tier 1 for now.
        DungeonTravel.Entry entry = DungeonTravel.enter(serverPlayer, RoomCounts.roll(DungeonRun.MIN_TIER, level.getRandom()), DungeonRun.MIN_TIER);
        serverPlayer.sendSystemMessage(Component.literal("You enter a dungeon of " + entry.layout().rooms().size()
                + " rooms. Find the loot and leave through the exit to keep it.").withStyle(ChatFormatting.GOLD));
        return InteractionResult.CONSUME;
    }
}
