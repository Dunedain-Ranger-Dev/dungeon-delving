package io.github.ranger3462.dungeondelving.gate;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rift a Runic Key opens in a Riftstone frame near a Dungeon Gate. It remembers the key's tier; stepping in starts
 * a run of that tier and closes the rift. It falls apart if its frame is broken.
 */
public class RiftPortalBlock extends Block {
    public static final MapCodec<RiftPortalBlock> CODEC = simpleCodec(RiftPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", DungeonRun.MIN_TIER, DungeonRun.MAX_TIER);
    private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

    public RiftPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(TIER, DungeonRun.MIN_TIER));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TIER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    /** Falls apart when a block beside it in its plane is neither rift nor Riftstone. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean inPlane = direction.getAxis() == Direction.Axis.Y || direction.getAxis() == state.getValue(AXIS);
        if (inPlane && !neighbor.is(this) && !neighbor.is(ModRegistries.RIFTSTONE.get())) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player && !entity.isPassenger() && !entity.isVehicle()) {
            // Leave the dimension after this tick, not in the middle of the player's own movement.
            server.getServer().execute(() -> DungeonGates.enterRift(player, server, pos.immutable()));
        }
    }
}
