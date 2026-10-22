package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bellows: placed next to a furnace or blast furnace, each pump (right-click) pushes along whatever the furnace is
 * heating. Part of a level 3 forge.
 */
public class BellowsBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BellowsBlock> CODEC = simpleCodec(BellowsBlock::new);
    /** Cooking progress each pump adds, in ticks. */
    public static final int PUMP_TICKS = 15;
    /** Ticks between pumps. */
    public static final int PUMP_COOLDOWN = 8;
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 10.0, 15.0);

    public BellowsBlock(Properties properties) {
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
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getCooldowns().isOnCooldown(Forging.BELLOWS_ITEM.get())) {
            return InteractionResult.CONSUME;
        }
        player.getCooldowns().addCooldown(Forging.BELLOWS_ITEM.get(), PUMP_COOLDOWN);
        if (level instanceof ServerLevel server) {
            boolean pushed = pump(server, pos);
            server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, pushed ? 1.6f : 1.9f);
            server.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Pushes along every lit furnace next to the bellows. True if any furnace was heating something. */
    public static boolean pump(ServerLevel level, BlockPos pos) {
        boolean pushed = false;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.getBlockEntity(next) instanceof AbstractFurnaceBlockEntity furnace && furnace.litTime > 0 && furnace.cookingProgress > 0
                    && furnace.cookingTotalTime > 0) {
                furnace.cookingProgress = Math.min(furnace.cookingProgress + PUMP_TICKS, furnace.cookingTotalTime - 1);
                furnace.setChanged();
                level.sendParticles(ParticleTypes.FLAME, next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
                pushed = true;
            }
        }
        return pushed;
    }
}
