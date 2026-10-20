package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonChallenges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Seals the boss room. Use Dungeon Keys on it; once enough are used, the whole door opens and the boss appears. */
public class BossDoorBlock extends Block {
    public static final MapCodec<BossDoorBlock> CODEC = simpleCodec(BossDoorBlock::new);

    public BossDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            DungeonChallenges.useDoor(serverPlayer, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            DungeonChallenges.useDoor(serverPlayer, ItemStack.EMPTY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
