package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmTurns;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the realm's turns (DD-95): the real-time clock and its catch-up cap, and each side's actions. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RealmTurnTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void turnsComeOnARealTimeClock(GameTestHelper helper) {
        Realm realm = frontier();
        RandomSource random = RandomSource.create(1);
        long length = RealmTurns.turnMillis();
        long start = 1_000_000L;
        helper.assertTrue(RealmTurns.catchUp(realm, start, random) == 0 && realm.turn() == 0, "the clock starts, with no turn yet");
        helper.assertTrue(RealmTurns.catchUp(realm, start + length - 1, random) == 0, "no turn before a turn's length has passed");
        helper.assertTrue(RealmTurns.catchUp(realm, start + length, random) == 1 && realm.turn() == 1, "one turn after one length");
        helper.assertTrue(RealmTurns.untilNext(realm, start + length) == length, "and the next is a whole length away");
        int cap = Config.REALM_CATCH_UP_TURNS.get();
        int taken = RealmTurns.catchUp(realm, start + length * (cap + 50L), random);
        helper.assertTrue(taken == cap, "a long time away catches up only " + cap + " turns, took " + taken);
        helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.text().contains("quiet for")), "and the log says how many were skipped");
        helper.assertTrue(RealmTurns.untilNext(realm, start + length * (cap + 50L)) == length, "the clock is caught up after");

        Realm empty = new Realm();
        RealmTurns.catchUp(empty, start, random);
        helper.assertTrue(RealmTurns.catchUp(empty, start + length * 5, random) == 0 && empty.turn() == 0, "a world with no realm takes no turns");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachSideActsOnItsSituation(GameTestHelper helper) {
        Realm realm = frontier();
        RealmNode riverhold = realm.node("riverhold").orElseThrow();
        RealmNode warren = realm.node("warren").orElseThrow();
        helper.assertTrue(RealmTurns.borderTowns(realm).size() == 2 && RealmTurns.threat(realm) == 10, "two border towns face 10 goblin military");

        RealmTurns.kingdomAct(realm, RealmTurns.KingdomAction.REINFORCE);
        helper.assertTrue(riverhold.stat("defense") == 12 && realm.node("capital").orElseThrow().stat("defense") == 10,
                "reinforcing strengthens border towns only");
        RealmTurns.kingdomAct(realm, RealmTurns.KingdomAction.MUSTER);
        helper.assertTrue(realm.kingdomStat("army") == 13, "mustering grows the army");
        RealmTurns.kingdomAct(realm, RealmTurns.KingdomAction.GATHER_WEALTH);
        helper.assertTrue(realm.kingdomStat("treasury") == 13 && riverhold.stat("wealth") == 11, "gathering wealth fills the treasury and the towns");

        RealmTurns.nationAct(realm, DungeonFactions.GOBLINS, RealmTurns.NationAction.BUILD_MILITARY);
        helper.assertTrue(warren.stat("military") == 12, "building military strengthens the strongholds");
        realm.setStat(realm.node("millbrook").orElseThrow(), "defense", 8);
        helper.assertTrue(RealmTurns.raidTarget(realm, DungeonFactions.GOBLINS).orElseThrow().getValue().id.equals("millbrook"),
                "the goblins would raid the weaker town (millbrook, 8 defense against riverhold's 12)");
        RealmTurns.nationAct(realm, DungeonFactions.GOBLINS, RealmTurns.NationAction.RAID);
        helper.assertTrue(realm.raids().size() == 1 && realm.raids().get(0).town().equals("millbrook"), "a raid is declared");
        helper.assertTrue(RealmTurns.nationWeights(realm, DungeonFactions.GOBLINS).get(RealmTurns.NationAction.RAID) == 0,
                "and no second raid while one is pending");

        Realm reloaded = realm.reloaded(helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.raids().size() == 1 && reloaded.kingdomStat("army") == 13 && !reloaded.log().isEmpty(),
                "raids, the kingdom's stats and the log are saved");

        Realm alone = new Realm();
        alone.add(node("hermitage", RealmNode.Type.TOWN, RealmNode.KINGDOM, 0));
        helper.assertTrue(RealmTurns.kingdomWeights(alone).get(RealmTurns.KingdomAction.REINFORCE) == 0, "with no border, the kingdom never reinforces");
        for (int i = 0; i < 20; i++) {
            RealmTurns.takeTurn(alone, RandomSource.create(i));
        }
        helper.assertTrue(alone.turn() == 20 && alone.nations().isEmpty(), "turns run with no monster nation at all");
        helper.succeed();
    }

    /** Two border towns and an inland capital, against one goblin warren. */
    private static Realm frontier() {
        Realm realm = new Realm();
        realm.add(node("capital", RealmNode.Type.TOWN, RealmNode.KINGDOM, 0));
        realm.add(node("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, 1));
        realm.add(node("millbrook", RealmNode.Type.TOWN, RealmNode.KINGDOM, 2));
        realm.add(node("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, 3));
        realm.link("capital", "riverhold");
        realm.link("capital", "millbrook");
        realm.link("riverhold", "warren");
        realm.link("millbrook", "warren");
        return realm;
    }

    private static RealmNode node(String id, RealmNode.Type type, String owner, int x) {
        return new RealmNode(id, type, owner, Level.OVERWORLD, new BlockPos(x * 50, 64, 0));
    }
}
