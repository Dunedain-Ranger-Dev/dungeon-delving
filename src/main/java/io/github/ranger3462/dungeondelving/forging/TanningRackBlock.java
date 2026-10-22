package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Tanning Rack: a frame a hide is stretched on to be scraped clean. Hold leather and right-click it to scrape it into
 * refined leather (a {@link TimingBar.Kind#SCRAPE} pass). Doesn't need a forge.
 */
public class TanningRackBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TanningRackBlock> CODEC = simpleCodec(TanningRackBlock::new);
    private static final VoxelShape X_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    private static final VoxelShape Z_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);

    public TanningRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }
}
