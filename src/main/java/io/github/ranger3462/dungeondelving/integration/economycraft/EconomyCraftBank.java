package io.github.ranger3462.dungeondelving.integration.economycraft;

import com.reazip.economycraft.api.v1.BalanceApi;
import com.reazip.economycraft.api.v1.EconomyCraftApi;
import com.reazip.economycraft.api.v1.MutationSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Coins in and out of EconomyCraft's balances. Only loaded when EconomyCraft is installed (see {@code Bank}). */
public final class EconomyCraftBank {
    private static final MutationSource COINS = new MutationSource("dungeondelving", "coins");

    private EconomyCraftBank() {}

    private static BalanceApi balances(MinecraftServer server) {
        return EconomyCraftApi.get(server).balances();
    }

    public static boolean deposit(ServerPlayer player, long amount) {
        return amount > 0 && balances(player.server).addMoney(player.getUUID(), amount, COINS).successful();
    }

    public static boolean withdraw(ServerPlayer player, long amount) {
        return amount > 0 && balances(player.server).removeMoney(player.getUUID(), amount, COINS).successful();
    }

    public static long balance(ServerPlayer player) {
        return balances(player.server).getBalance(player.getUUID());
    }

    public static String format(MinecraftServer server, long amount) {
        return EconomyCraftApi.get(server).formatMoney(amount);
    }
}
