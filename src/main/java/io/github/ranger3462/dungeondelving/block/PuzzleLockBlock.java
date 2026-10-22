package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonChallenges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The end of a puzzle challenge room. Builders wire their puzzle (vanilla redstone) into it; the first time it's
 * powered, it's solved: it drops a Dungeon Key and the room's spawners give way to a reward chest. Can't be broken.
 */
public class PuzzleLockBlock extends Block {
    public static final MapCodec<PuzzleLockBlock> CODEC = simpleCodec(PuzzleLockBlock::new);
    public static final BooleanProperty SOLVED = BooleanProperty.create("solved");

    public PuzzleLockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SOLVED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLVED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !state.getValue(SOLVED) && level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.setValue(SOLVED, true), Block.UPDATE_ALL);
            DungeonChallenges.puzzleSolved(server, pos);
        }
    }
}
