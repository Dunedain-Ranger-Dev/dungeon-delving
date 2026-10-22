package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Quench Tub: quenching in oil instead of water never cracks the piece. Pour in Quenching Oil (each bottle is
 * {@link #LEVELS_PER_BOTTLE} quenches); each quench uses one. Part of a level 3 forge.
 */
public class QuenchTubBlock extends Block {
    public static final MapCodec<QuenchTubBlock> CODEC = simpleCodec(QuenchTubBlock::new);
    public static final int MAX_LEVEL = 8;
    public static final int LEVELS_PER_BOTTLE = 4;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX_LEVEL);
    private static final VoxelShape SHAPE = Shapes.join(Block.box(1.0, 0.0, 1.0, 15.0, 10.0, 15.0), Block.box(2.0, 1.0, 2.0, 14.0, 10.0, 14.0),
            BooleanOp.ONLY_FIRST);

    public QuenchTubBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(Forging.QUENCHING_OIL.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int oil = state.getValue(LEVEL);
        if (oil + LEVELS_PER_BOTTLE > MAX_LEVEL) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(LEVEL, oil + LEVELS_PER_BOTTLE), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!player.getInventory().add(bottle)) {
                    player.drop(bottle, false);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Uses up one quench of oil. False if the tub is dry. */
    public static boolean useOil(Level level, BlockPos pos, BlockState state) {
        int oil = state.getValue(LEVEL);
        if (oil <= 0) {
            return false;
        }
        level.setBlock(pos, state.setValue(LEVEL, oil - 1), Block.UPDATE_ALL);
        return true;
    }
}
