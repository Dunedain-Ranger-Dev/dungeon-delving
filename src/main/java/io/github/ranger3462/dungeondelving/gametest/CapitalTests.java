package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmCapitals;
import io.github.ranger3462.dungeondelving.realm.RealmLevels;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks nation capitals (DD-101). */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class CapitalTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void capitalsCantBeTakenAndStayDangerous(GameTestHelper helper) {
        var dim = helper.getLevel().dimension();
        helper.assertTrue(Realm.ownerProblem(RealmNode.Type.CAPITAL, RealmNode.KINGDOM, false).isPresent()
                && Realm.ownerProblem(RealmNode.Type.CAPITAL, RealmNode.NONE, false).isPresent(), "a capital can't be taken or cleared");
        helper.assertTrue(Realm.ownerProblem(RealmNode.Type.CAPITAL, DungeonFactions.ORCS, true).isEmpty(), "but a nation can found one");
        Realm realm = new Realm();
        realm.add(new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, dim, BlockPos.ZERO));
        RealmNode capital = new RealmNode("grimhold", RealmNode.Type.CAPITAL, DungeonFactions.ORCS, dim, new BlockPos(150, 64, 0));
        realm.add(capital);
        realm.setStat(capital, "military", 0);
        int level = RealmLevels.level(realm, dim, new BlockPos(150, 64, 0));
        helper.assertTrue(level >= RealmLevels.CAPITAL_FLOOR, "even with no military, a capital's heart is dangerous: " + level);
        helper.assertTrue(realm.nations().contains(DungeonFactions.ORCS), "a nation with only its capital still acts");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aPushedBackNationRalliesAndSendsItsChampion(GameTestHelper helper) {
        var dim = helper.getLevel().dimension();
        Realm realm = new Realm();
        realm.add(new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, dim, BlockPos.ZERO));
        RealmNode capital = new RealmNode("grimhold", RealmNode.Type.CAPITAL, DungeonFactions.ORCS, dim, new BlockPos(400, 64, 0));
        realm.add(capital);
        RealmNode camp = new RealmNode("camp", RealmNode.Type.STRONGHOLD, DungeonFactions.ORCS, dim, new BlockPos(200, 64, 0));
        realm.add(camp);
        for (int turn = 0; turn < RealmCapitals.RALLY_TURNS + 1; turn++) {
            helper.assertTrue(RealmCapitals.rally(realm, 0L) == 0, "while it holds a stronghold, its capital waits");
        }
        realm.setOwner(camp, RealmNode.KINGDOM);
        helper.assertTrue(RealmCapitals.pushedBack(realm, DungeonFactions.ORCS), "with its last stronghold taken, it's pushed back");
        int marched = 0;
        for (int turn = 0; turn < RealmCapitals.RALLY_TURNS; turn++) {
            marched += RealmCapitals.rally(realm, 0L);
        }
        helper.assertTrue(marched == 1 && realm.raids().size() == 1, "after " + RealmCapitals.RALLY_TURNS + " turns rallying, its champion marches");
        Realm.Raid raid = realm.raids().get(0);
        helper.assertTrue(raid.town().equals("riverhold") && RealmCapitals.isChampionRaid(realm, raid), "on the nearest town");
        helper.assertTrue(RealmCapitals.rally(realm, 0L) == 0, "one champion at a time");

        int before = capital.stat("military");
        RealmRaids.resolve(realm, raid, 20, 1);
        helper.assertTrue(capital.stat("military") >= before - 1 + RealmCapitals.VICTORY_MILITARY - 1, "a winning champion lets it rebuild faster");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void killingTheChampionSetsTheNationBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = Realm.get(level.getServer());
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        RealmNode capital = new RealmNode("test_capital_" + Math.abs(at.asLong() % 100000), RealmNode.Type.CAPITAL, DungeonFactions.ORCS, level.dimension(), at);
        realm.add(capital);
        try {
            realm.setStat(capital, "military", 60);
            Mob champion = RealmCapitals.spawnChampion(level, at, capital);
            helper.assertTrue(DungeonBosses.isBoss(champion) && champion.getCustomName().getString().contains("Champion of the orcs"),
                    "the champion is one of its nation's bosses");
            champion.kill();
            helper.assertTrue(capital.stat("military") == 40, "killing it costs the capital a third of its military: " + capital.stat("military"));
            helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.text().contains("slew the champion of the orcs")), "with news");
        } finally {
            realm.remove(capital.id);
            level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at).inflate(16)).forEach(Mob::discard);
        }
        helper.succeed();
    }
}
