package io.github.ranger3462.dungeondelving.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * The bank coins go into: EconomyCraft's balances, when it's installed. Everything EconomyCraft goes through
 * {@code EconomyCraftBank}, so it's never loaded without it.
 */
public final class Bank {
    private Bank() {}

    public static boolean available() {
        return ModList.get().isLoaded("economycraft");
    }

    /** Adds to a player's balance. Whether it went in. */
    public static boolean deposit(ServerPlayer player, long amount) {
        return available() && io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftBank.deposit(player, amount);
    }

    /** Takes from a player's balance, if they have enough. Whether it came out. */
    public static boolean withdraw(ServerPlayer player, long amount) {
        return available() && io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftBank.withdraw(player, amount);
    }

    public static long balance(ServerPlayer player) {
        return available() ? io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftBank.balance(player) : 0;
    }

    /** Money as EconomyCraft shows it ("$1,250"), or the bare number. */
    public static String format(MinecraftServer server, long amount) {
        return available() ? io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftBank.format(server, amount) : Long.toString(amount);
    }
}
