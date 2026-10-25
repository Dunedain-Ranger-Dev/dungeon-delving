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
 * A crafting station with no state of its own, facing the way it was placed: the Spinning Wheel, Chopping Block, Wood
 * Lathe and the workshop upgrades (Fleshing Beam, Great Loom, Dye Works, Treadle Lathe, Bowyer's Bench). What each does
 * is in {@link ForgeActions} and {@link CraftWorkshop}; this is only its block.
 */
public class CraftStationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CraftStationBlock> CODEC = simpleCodec(CraftStationBlock::new);
    private final VoxelShape shape;

    public CraftStationBlock(Properties properties) {
        this(properties, Shapes.block());
    }

    public CraftStationBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
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
        return shape;
    }
}
