package io.github.ranger3462.dungeondelving.economy;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Coins: real money you can hold, drop, chest and loot. Copper is worth 1, silver 10, gold 100, platinum 1,000, in the
 * same units as EconomyCraft's balances, so with it installed a coin deposits for its worth and {@code /coins withdraw}
 * turns balance back into coins (see {@link CoinItem} and {@link CoinCommands}). Without it they're still coins.
 */
public final class Coins {
    public static final DeferredItem<CoinItem> COPPER = coin("copper_coin", 1);
    public static final DeferredItem<CoinItem> SILVER = coin("silver_coin", 10);
    public static final DeferredItem<CoinItem> GOLD = coin("gold_coin", 100);
    public static final DeferredItem<CoinItem> PLATINUM = coin("platinum_coin", 1000);

    private Coins() {}

    private static DeferredItem<CoinItem> coin(String name, int value) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new CoinItem(value, properties), new Item.Properties().stacksTo(64));
    }

    /** Biggest first. */
    public static List<DeferredItem<CoinItem>> all() {
        return List.of(PLATINUM, GOLD, SILVER, COPPER);
    }

    /** An amount as the fewest coins: platinum first, then gold, silver and copper, in stacks of up to 64. */
    public static List<ItemStack> change(long amount) {
        List<ItemStack> stacks = new ArrayList<>();
        long left = Math.max(0, amount);
        for (DeferredItem<CoinItem> coin : all()) {
            long count = left / coin.get().value();
            left -= count * coin.get().value();
            while (count > 0) {
                int stack = (int) Math.min(64, count);
                stacks.add(new ItemStack(coin.get(), stack));
                count -= stack;
            }
        }
        return stacks;
    }

    /** What a stack of coins is worth (0 for anything else). */
    public static long worth(ItemStack stack) {
        return stack.getItem() instanceof CoinItem coin ? (long) coin.value() * stack.getCount() : 0;
    }

    /** Makes sure the coins are registered; call once while setting up registries. */
    public static void init() {}
}
