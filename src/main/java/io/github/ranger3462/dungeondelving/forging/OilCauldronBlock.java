package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A cauldron of Quenching Oil: quenching in oil never cracks the piece. Pour a bottle of oil into an empty cauldron (or
 * one that isn't full) to add a level; each quench uses a level, and the last one leaves an empty cauldron. Part of a
 * level 3 forge.
 */
public class OilCauldronBlock extends AbstractCauldronBlock {
    public static final MapCodec<OilCauldronBlock> CODEC = simpleCodec(OilCauldronBlock::new);
    public static final int MAX_LEVEL = 3;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, MAX_LEVEL);
    /** What players can do to an oil cauldron with items in hand: pour in more oil. */
    public static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap("oil");

    public OilCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected MapCodec<? extends AbstractCauldronBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public boolean isFull(BlockState state) {
        return state.getValue(LEVEL) == MAX_LEVEL;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return (6.0 + state.getValue(LEVEL) * 3.0) / 16.0;
    }

    /** Sets up pouring oil into empty and oil cauldrons. Call once during common setup. */
    public static void registerInteractions() {
        CauldronInteraction pour = OilCauldronBlock::pourOil;
        CauldronInteraction.EMPTY.map().put(Forging.QUENCHING_OIL.get(), pour);
        INTERACTIONS.map().put(Forging.QUENCHING_OIL.get(), pour);
    }

    private static ItemInteractionResult pourOil(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        boolean empty = !state.is(Forging.OIL_CAULDRON.get());
        if (!empty && state.getValue(LEVEL) >= MAX_LEVEL) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            BlockState filled = empty ? Forging.OIL_CAULDRON.get().defaultBlockState() : state.setValue(LEVEL, state.getValue(LEVEL) + 1);
            level.setBlockAndUpdate(pos, filled);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Uses up one quench of oil; the last leaves an empty cauldron. */
    public static void useOil(Level level, BlockPos pos, BlockState state) {
        int oil = state.getValue(LEVEL);
        level.setBlockAndUpdate(pos, oil > 1 ? state.setValue(LEVEL, oil - 1) : Blocks.CAULDRON.defaultBlockState());
    }
}
