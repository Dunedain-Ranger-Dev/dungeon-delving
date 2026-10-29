package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.economy.Bank;
import io.github.ranger3462.dungeondelving.economy.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class CoinTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonLootIsOnEconomyCraftsPriceList(GameTestHelper helper) {
        if (!Bank.available()) {
            helper.succeed();
            return;
        }
        com.google.gson.JsonObject world = new com.google.gson.JsonObject();
        com.google.gson.JsonObject edited = new com.google.gson.JsonObject();
        edited.addProperty("unit_sell", 999);
        world.add("dungeondelving:thraggite_ingot", edited);
        com.google.gson.JsonObject placeholder = new com.google.gson.JsonObject();
        placeholder.addProperty("unit_buy", 0);
        placeholder.addProperty("unit_sell", 0);
        world.add("dungeondelving:barrow_iron_ingot", placeholder);
        com.google.gson.JsonObject ours = new com.google.gson.JsonObject();
        com.google.gson.JsonObject price = new com.google.gson.JsonObject();
        price.addProperty("unit_buy", 0);
        price.addProperty("unit_sell", 7);
        ours.add("dungeondelving:thraggite_ingot", price);
        ours.add("dungeondelving:raw_thraggite", price);
        ours.add("dungeondelving:barrow_iron_ingot", price);
        java.util.Set<String> filled = new java.util.HashSet<>();
        int added = io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftPrices.merge(world, ours, filled);
        helper.assertTrue(added == 2 && world.getAsJsonObject("dungeondelving:thraggite_ingot").get("unit_sell").getAsInt() == 999,
                "missing prices are added; an admin's edit stays");
        helper.assertTrue(world.getAsJsonObject("dungeondelving:barrow_iron_ingot").get("unit_sell").getAsInt() == 7,
                "EconomyCraft's price-0 placeholders are filled in");
        world.getAsJsonObject("dungeondelving:barrow_iron_ingot").addProperty("unit_sell", 0);
        helper.assertTrue(io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftPrices.merge(world, ours, filled) == 0,
                "but only once: an admin zeroing a price later keeps it zeroed");
        try {
            java.nio.file.Path file = com.reazip.economycraft.util.EconomyPaths.configDir(helper.getLevel().getServer()).resolve("prices.json");
            String text = java.nio.file.Files.readString(file);
            com.google.gson.JsonObject prices = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            helper.assertTrue(prices.has("dungeondelving:thailite_ingot") && prices.has("dungeondelving:goblin_chief_staff")
                    && prices.getAsJsonObject("dungeondelving:thailite_ingot").get("unit_sell").getAsLong() > 0,
                    "the world's price list sells our dungeon loot");
        } catch (java.io.IOException e) {
            helper.fail("couldn't read EconomyCraft's prices: " + e);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonChestsAndBossesDropCoins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var tables = level.getServer().reloadableRegistries();
        for (String path : List.of("chests/tier_1/room", "chests/tier_4/objective", "bosses/zombie_lord/tier_1")) {
            var table = tables.getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    DungeonDelving.id(path)));
            long worth = 0;
            for (int roll = 0; roll < 20 && worth == 0; roll++) {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params)) {
                    worth += Coins.worth(stack);
                }
            }
            helper.assertTrue(worth > 0, path + " drops coins");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void coinsMakeChangeAndBank(GameTestHelper helper) {
        List<ItemStack> change = Coins.change(1234);
        helper.assertTrue(change.size() == 4 && change.get(0).is(Coins.PLATINUM.get()) && change.get(1).getCount() == 2
                && change.get(2).getCount() == 3 && change.get(3).getCount() == 4, "1,234 is 1 platinum, 2 gold, 3 silver, 4 copper");
        helper.assertTrue(Coins.change(64 * 1000 + 1000).get(0).getCount() == 64 && Coins.change(65_000).get(1).getCount() == 1, "in stacks of 64");
        helper.assertTrue(Coins.worth(new ItemStack(Coins.SILVER.get(), 7)) == 70 && Coins.worth(new ItemStack(Items.DIAMOND)) == 0, "a stack is worth its coins");
        if (Bank.available()) {
            FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "banker"));
            long before = Bank.balance(player);
            helper.assertTrue(Bank.deposit(player, 150) && Bank.balance(player) == before + 150, "coins deposit into EconomyCraft's balance");
            helper.assertTrue(Bank.withdraw(player, 100) && Bank.balance(player) == before + 50, "and come back out");
            helper.assertFalse(Bank.withdraw(player, before + 1000), "but not more than you have");
        }
        helper.succeed();
    }
}
