package io.github.ranger3462.dungeondelving.block;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonTravel;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
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
 * The way into dungeons. Use a Runic Key on it to open a dungeon of the key's tier; the key is always used up, even in
 * creative. Works anywhere outside the dungeon dimension.
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
        if (player instanceof ServerPlayer serverPlayer && canEnter(serverPlayer, level)) {
            stack.shrink(1); // Even in creative, so every run needs a freshly made key.
            DungeonTravel.Entry entry = DungeonTravel.enter(serverPlayer, RoomCounts.roll(key.tier(), level.getRandom()), key.tier());
            int keys = DungeonRegions.get().progress(entry.run().region()).map(progress -> progress.keysNeeded()).orElse(0);
            serverPlayer.sendSystemMessage(Component.translatable("message.dungeondelving.gate_entered", key.tier(), entry.layout().rooms().size(), keys)
                    .withStyle(ChatFormatting.GOLD));
            if (entry.type() != LayoutType.NORMAL) {
                serverPlayer.sendSystemMessage(Component.translatable("message.dungeondelving.layout." + entry.type().id()).withStyle(ChatFormatting.YELLOW));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Without a key, the gate explains what it needs. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && canEnter(serverPlayer, level)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dungeondelving.gate_needs_key").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canEnter(ServerPlayer player, Level level) {
        if (level.dimension() == DungeonDimension.DUNGEON || DungeonRegions.get().run(player.getUUID()).isPresent()) {
            player.sendSystemMessage(Component.translatable("message.dungeondelving.gate_already_inside").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
