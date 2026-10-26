package io.github.ranger3462.dungeondelving.economy;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A coin worth {@link #value()}. With EconomyCraft installed, using a stack deposits it all into your balance. */
public class CoinItem extends Item {
    private final int value;

    public CoinItem(int value, Properties properties) {
        super(properties);
        this.value = value;
    }

    public int value() {
        return value;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!Bank.available()) {
            player.displayClientMessage(Component.literal("There's no bank to deposit coins in.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        long worth = Coins.worth(stack);
        if (!Bank.deposit(serverPlayer, worth)) {
            player.displayClientMessage(Component.literal("Couldn't deposit those coins.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.setCount(0);
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.literal("Deposited " + Bank.format(serverPlayer.server, worth) + ". Balance: "
                + Bank.format(serverPlayer.server, Bank.balance(serverPlayer))).withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Worth " + value + (stack.getCount() > 1 ? " (this stack: " + Coins.worth(stack) + ")" : "")).withStyle(ChatFormatting.GOLD));
        if (Bank.available()) {
            tooltip.add(Component.literal("Use to deposit the stack into your balance").withStyle(ChatFormatting.GRAY));
        }
    }
}
