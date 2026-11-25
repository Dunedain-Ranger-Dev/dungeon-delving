package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.challenge.PuzzleBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A puzzle boss room's wrong answer (DD-58). Builders wire their puzzle's wrong answers into it: each time it's powered,
 * the puzzle is failed (the Undead Dwarf Archivist wakes; the Treasure Vault calls its guards). Can't be broken.
 */
public class PuzzleTrapBlock extends Block {
    public static final MapCodec<PuzzleTrapBlock> CODEC = simpleCodec(PuzzleTrapBlock::new);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public PuzzleTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        if (powered && level instanceof ServerLevel server) {
            PuzzleBosses.failed(server, pos);
        }
    }
}
