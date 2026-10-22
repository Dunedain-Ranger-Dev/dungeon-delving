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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Dwarven Anvil: a forging anvil that widens the sweet spot when striking. Unlike a vanilla anvil it doesn't fall
 * or wear out, and it's only for forging (repairs still happen at a vanilla anvil). Part of a level 2 forge.
 */
public class DwarvenAnvilBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DwarvenAnvilBlock> CODEC = simpleCodec(DwarvenAnvilBlock::new);
    /** Extra sweet-spot width, as a share of the bar. */
    public static final float SWEET_SPOT_BONUS = 0.04f;
    private static final VoxelShape BASE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
    private static final VoxelShape X_SHAPE = Shapes.or(BASE, Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0), Block.box(4.0, 5.0, 6.0, 12.0, 10.0, 10.0),
            Block.box(0.0, 10.0, 3.0, 16.0, 16.0, 13.0));
    private static final VoxelShape Z_SHAPE = Shapes.or(BASE, Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0), Block.box(6.0, 5.0, 4.0, 10.0, 10.0, 12.0),
            Block.box(3.0, 10.0, 0.0, 13.0, 16.0, 16.0));

    public DwarvenAnvilBlock(Properties properties) {
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }
}
