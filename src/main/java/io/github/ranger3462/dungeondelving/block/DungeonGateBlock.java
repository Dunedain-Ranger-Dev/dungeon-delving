package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.gate.DungeonGates;
import io.github.ranger3462.dungeondelving.gate.GateArea;
import io.github.ranger3462.dungeondelving.workshop.RunicKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The way into dungeons (DD-75). It checks the area around it ({@link GateArea}) for a Riftstone portal frame and Gate
 * Tuners. Use a Runic Key on it to open a rift of the key's tier in the frame (the key is always used up, even in
 * creative); stepping into the rift starts the run ({@link DungeonGates}). Right-click it empty-handed to see what it
 * has. Works anywhere outside the dungeon dimension.
 */
public class DungeonGateBlock extends Block {
    public static final MapCodec<DungeonGateBlock> CODEC = simpleCodec(DungeonGateBlock::new);

    public DungeonGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DungeonGateBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof RunicKeyItem key)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer && DungeonGates.canEnter(serverPlayer)) {
            switch (DungeonGates.light(level, pos, key.tier())) {
                case LIT -> {
                    stack.shrink(1); // Even in creative, so every run needs a freshly made key.
                    serverPlayer.sendSystemMessage(Component.translatable("message.dungeondelving.gate_lit", key.tier()).withStyle(ChatFormatting.GOLD));
                }
                case ALREADY_OPEN -> serverPlayer.displayClientMessage(
                        Component.translatable("message.dungeondelving.gate_already_open").withStyle(ChatFormatting.GRAY), true);
                case NO_FRAME -> serverPlayer.sendSystemMessage(Component.translatable("message.dungeondelving.gate_no_frame",
                        GateArea.MIN_WIDTH, GateArea.MAX_WIDTH, GateArea.MIN_HEIGHT, GateArea.MAX_HEIGHT, GateArea.RADIUS).withStyle(ChatFormatting.RED));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Without a key, the gate says what it has around it and what it needs. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(DungeonGates.status(level, pos).copy().withStyle(ChatFormatting.GRAY));
            serverPlayer.displayClientMessage(Component.translatable("message.dungeondelving.gate_needs_key").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
