package io.github.ranger3462.dungeondelving.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmLiveRaids;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks live raids (DD-98): waves against a town's guards, scored by the damage each side does. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class LiveRaidTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void wavesComeAndATownThatBeatsThemHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = frontier(helper);
        try {
            Realm.Raid raid = RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();
            helper.assertTrue(RealmRaids.declare(realm, "riverhold", 0L).isEmpty(), "one raid on a town at a time");
            RealmLiveRaids.LiveRaid live = RealmLiveRaids.start(realm, raid, level);
            helper.assertTrue(RealmLiveRaids.isLive(raid) && live.wave() == 1, "the fight starts with wave 1");
            helper.assertTrue(RealmRaids.resolveDue(realm, Long.MAX_VALUE, net.minecraft.util.RandomSource.create(1)) == 0,
                    "a raid being fought isn't rolled");
            helper.assertTrue(live.raiders().size() == 4, "a military of 10 sends waves of 4: " + live.raiders().size());

            // Damage counts: a player hurting a raider is the town's; a player hurting a guard is a turncoat's.
            FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "defender"));
            Mob raider = RealmLiveRaids.raiders(live).get(0);
            raider.hurt(level.damageSources().playerAttack(player), 4.0f);
            helper.assertTrue(live.defenseScore() >= 3.9f, "hurting a raider counts for the town: " + live.defenseScore());

            for (int wave = 1; wave <= RealmLiveRaids.WAVES; wave++) {
                RealmLiveRaids.raiders(live).forEach(Mob::discard);
                RealmLiveRaids.tick(level.getServer(), realm, 20);
            }
            helper.assertFalse(RealmLiveRaids.isLive(raid), "after the last wave falls, the fight is over");
            helper.assertTrue(realm.raids().isEmpty() && realm.node("riverhold").orElseThrow().stat("damage") == 0, "and the town held");
            helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.text().contains("pitched fight")), "the news says it was fought");
        } finally {
            RealmLiveRaids.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void runningOutTheClockBehindIsASacking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = frontier(helper);
        try {
            Realm.Raid raid = RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();
            RealmLiveRaids.LiveRaid live = RealmLiveRaids.start(realm, raid, level);
            RealmLiveRaids.score(live, 200, 20);
            RealmLiveRaids.tick(level.getServer(), realm, RealmLiveRaids.TIME_LIMIT);
            helper.assertFalse(RealmLiveRaids.isLive(raid), "the fight ends when time runs out");
            RealmNode town = realm.node("riverhold").orElseThrow();
            helper.assertTrue(town.stat("damage") > 0 && town.stat("wealth") < 10, "raiders far ahead sack the town: " + town.stats());
            helper.assertTrue(level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(town.pos()).inflate(40),
                    mob -> mob.getPersistentData().contains(DungeonDelving.MODID + ":raider")).isEmpty(), "and the raiders are cleared away");
        } finally {
            RealmLiveRaids.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void aFightNoOneStaysForIsRolled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = frontier(helper);
        try {
            Realm.Raid raid = RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();
            RealmLiveRaids.start(realm, raid, level);
            RealmLiveRaids.tick(level.getServer(), realm, RealmLiveRaids.ALONE_LIMIT);
            helper.assertFalse(RealmLiveRaids.isLive(raid), "with no one there, the fight is given up");
            helper.assertTrue(realm.raids().isEmpty() && realm.log().stream().anyMatch(entry -> entry.text().contains("decided by the odds")),
                    "and rolled instead");
        } finally {
            RealmLiveRaids.clearAll();
        }
        helper.succeed();
    }

    /** A town on the test's ground, bordering a goblin warren of military 10. */
    private static Realm frontier(GameTestHelper helper) {
        Realm realm = new Realm();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        realm.add(new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, helper.getLevel().dimension(), at));
        realm.add(new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, helper.getLevel().dimension(), at.offset(200, 0, 0)));
        realm.link("riverhold", "warren");
        return realm;
    }
}
