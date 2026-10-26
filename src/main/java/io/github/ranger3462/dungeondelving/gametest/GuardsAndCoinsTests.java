package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.economy.Bank;
import io.github.ranger3462.dungeondelving.economy.Coins;
import io.github.ranger3462.dungeondelving.entity.TownGuard;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmGuard;
import io.github.ranger3462.dungeondelving.realm.RealmLiveRaids;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmRaids;
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

/** Checks guard roles and stronghold news (DD-98, DD-100), and coins (DD-93). */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class GuardsAndCoinsTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void townsSendSwordsmenCrossbowmenAndBattlemages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = new Realm();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        RealmNode town = new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, level.dimension(), at);
        realm.add(town);
        realm.add(new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, level.dimension(), at.offset(200, 0, 0)));
        realm.link("riverhold", "warren");
        realm.setStat(town, "tier", 3);
        try {
            Realm.Raid raid = RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();
            RealmLiveRaids.LiveRaid live = RealmLiveRaids.start(realm, raid, level);
            List<Mob> guards = RealmLiveRaids.guards(live);
            int[] split = RealmLiveRaids.roles(RealmLiveRaids.guardCount(10, 3), 3);
            helper.assertTrue(guards.size() == 6 && guards.stream().allMatch(guard -> guard instanceof RealmGuard), "a tier 3 town sends 6 guards: " + guards.size());
            long crossbowmen = guards.stream().filter(guard -> guard instanceof TownGuard townGuard && townGuard.isCrossbowman()).count();
            helper.assertTrue(crossbowmen == split[1] && crossbowmen >= 1, "some with crossbows: " + crossbowmen);
            helper.assertTrue(guards.stream().filter(guard -> guard instanceof TownGuard townGuard && townGuard.isCrossbowman())
                    .allMatch(guard -> guard.getMainHandItem().is(Items.CROSSBOW) && guard.getOffhandItem().isEmpty()), "who carry crossbows, not shields");
            long mages = guards.stream().filter(guard -> !(guard instanceof TownGuard)).count();
            helper.assertTrue(RealmLiveRaids.mageSpawner == null || (mages == split[2] && mages >= 1),
                    "and, with Iron's Spells, battlemages: " + mages);
            helper.assertTrue(guards.get(0) instanceof TownGuard first && !first.isCrossbowman(), "swordsmen turn out first");
            helper.assertTrue(RealmLiveRaids.roles(4, 1)[2] == 0, "a hamlet has no battlemages");
        } finally {
            RealmLiveRaids.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void strongholdsChangingHandsIsNews(GameTestHelper helper) {
        Realm realm = new Realm();
        RealmNode warren = new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, helper.getLevel().dimension(), BlockPos.ZERO);
        realm.add(warren);
        realm.setOwner(warren, RealmNode.KINGDOM);
        helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.news() && entry.text().contains("taken by the kingdom: the land around it calms")),
                "taking a stronghold calms the land");
        realm.setOwner(warren, DungeonFactions.ORCS);
        helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.news() && entry.text().contains("orcs have taken warren: the land around it grows more dangerous")),
                "monsters taking it back makes it dangerous again");
        helper.succeed();
    }

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
        com.google.gson.JsonObject ours = new com.google.gson.JsonObject();
        ours.add("dungeondelving:thraggite_ingot", new com.google.gson.JsonObject());
        ours.add("dungeondelving:raw_thraggite", new com.google.gson.JsonObject());
        int added = io.github.ranger3462.dungeondelving.integration.economycraft.EconomyCraftPrices.merge(world, ours);
        helper.assertTrue(added == 1 && world.getAsJsonObject("dungeondelving:thraggite_ingot").get("unit_sell").getAsInt() == 999,
                "only missing prices are added; an admin's edit stays");
        try {
            java.nio.file.Path file = com.reazip.economycraft.util.EconomyPaths.configDir(helper.getLevel().getServer()).resolve("prices.json");
            String text = java.nio.file.Files.readString(file);
            helper.assertTrue(text.contains("\"dungeondelving:thailite_ingot\"") && text.contains("\"dungeondelving:goblin_chief_staff\""),
                    "the world's price list has our dungeon loot");
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
