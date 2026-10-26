package io.github.ranger3462.dungeondelving.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /coins}, when there's a bank (EconomyCraft):
 * <ul>
 * <li>{@code /coins deposit}: every coin you carry goes into your balance.</li>
 * <li>{@code /coins withdraw <amount>}: that much comes out of your balance as the fewest coins.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class CoinCommands {
    private CoinCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!Bank.available()) {
            return;
        }
        event.getDispatcher().register(Commands.literal("coins")
                .then(Commands.literal("deposit").executes(CoinCommands::deposit))
                .then(Commands.literal("withdraw").then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000_000L))
                        .executes(CoinCommands::withdraw))));
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            total += Coins.worth(stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            total += Coins.worth(stack);
        }
        if (total == 0) {
            return fail(context, "You aren't carrying any coins.");
        }
        if (!Bank.deposit(player, total)) {
            return fail(context, "Couldn't deposit your coins.");
        }
        player.getInventory().items.forEach(stack -> {
            if (Coins.worth(stack) > 0) {
                stack.setCount(0);
            }
        });
        player.getInventory().offhand.forEach(stack -> {
            if (Coins.worth(stack) > 0) {
                stack.setCount(0);
            }
        });
        long deposited = total;
        context.getSource().sendSuccess(() -> Component.literal("Deposited " + Bank.format(player.server, deposited) + ". Balance: "
                + Bank.format(player.server, Bank.balance(player))).withStyle(ChatFormatting.GOLD), false);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long amount = LongArgumentType.getLong(context, "amount");
        if (!Bank.withdraw(player, amount)) {
            return fail(context, "You don't have " + Bank.format(player.server, amount) + " (balance: " + Bank.format(player.server, Bank.balance(player)) + ").");
        }
        for (ItemStack coins : Coins.change(amount)) {
            if (!player.getInventory().add(coins)) {
                player.drop(coins, false);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal("Withdrew " + Bank.format(player.server, amount) + " in coins. Balance: "
                + Bank.format(player.server, Bank.balance(player))).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
