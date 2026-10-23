package io.github.ranger3462.dungeondelving.gate;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Gate Tuner: place it near a Dungeon Gate and put a dungeon stone in it (right-click with the stone; right-click
 * empty-handed to take it back). The next run started from that gate uses the stone up and is changed by it.
 */
public class GateTunerBlock extends BaseEntityBlock {
    public static final MapCodec<GateTunerBlock> CODEC = simpleCodec(GateTunerBlock::new);
    public static final BooleanProperty TUNED = BooleanProperty.create("tuned");

    public GateTunerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TUNED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TUNED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateTunerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof DungeonStoneItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GateTunerBlockEntity tuner) {
            if (!tuner.stone().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.dungeondelving.tuner_full").withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.SUCCESS;
            }
            tuner.setStone(stack);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GateTunerBlockEntity tuner) {
            if (tuner.stone().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.dungeondelving.tuner_empty").withStyle(ChatFormatting.GRAY), true);
            } else {
                ItemStack stone = tuner.take();
                if (!player.addItem(stone)) {
                    player.drop(stone, false);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking a tuner drops its stone. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GateTunerBlockEntity tuner && !tuner.stone().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, tuner.stone().copy());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
