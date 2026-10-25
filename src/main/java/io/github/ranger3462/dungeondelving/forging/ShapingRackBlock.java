package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

/**
 * The Shaping Rack (Leatherworking and Weaving): lay a piece on it, then work it. Tanned leather is cut with shears or a
 * knife ({@link TimingBar.Kind#CUT}) into leather panels; fulled cloth is sewn with string ({@link TimingBar.Kind#SEW})
 * into cloth panels. Right-click with an empty hand to take the piece back.
 */
public class ShapingRackBlock extends BaseEntityBlock {
    public static final MapCodec<ShapingRackBlock> CODEC = simpleCodec(ShapingRackBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Knives (Farmer's Delight and others share this tag). */
    public static final TagKey<Item> KNIVES = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "tools/knife"));
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

    public ShapingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShapingRackBlockEntity(pos, state);
    }

    /** What can lie on the rack: tanned leather (to cut) and fulled cloth (to sew). */
    public static boolean isPiece(ItemStack stack) {
        return stack.is(Forging.REFINED_LEATHER.get()) || stack.is(Forging.PADDED_CLOTH.get());
    }

    /** What cuts leather on the rack: shears or a knife. */
    public static boolean isCuttingTool(ItemStack stack) {
        return stack.is(Items.SHEARS) || stack.is(Tags.Items.TOOLS_SHEAR) || stack.is(KNIVES);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof ShapingRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack piece = rack.piece();
        if (piece.isEmpty() && isPiece(stack)) {
            if (!level.isClientSide()) {
                rack.setPiece(stack);
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        TimingBar.Kind kind = piece.is(Forging.REFINED_LEATHER.get()) && isCuttingTool(stack) ? TimingBar.Kind.CUT
                : piece.is(Forging.PADDED_CLOTH.get()) && stack.is(Items.STRING) ? TimingBar.Kind.SEW : null;
        if (kind == null) {
            if (!piece.isEmpty() && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable(piece.is(Forging.REFINED_LEATHER.get())
                        ? "message.dungeondelving.rack.needs_blade" : "message.dungeondelving.rack.needs_string").withStyle(ChatFormatting.YELLOW), true);
            }
            return piece.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            ForgeActions.begin(serverPlayer, serverLevel, pos, state, stack, kind, serverLevel.getGameTime());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ShapingRackBlockEntity rack && !rack.piece().isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack taken = rack.take();
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ShapingRackBlockEntity rack && !rack.piece().isEmpty()) {
            Block.popResource(level, pos, rack.piece());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
