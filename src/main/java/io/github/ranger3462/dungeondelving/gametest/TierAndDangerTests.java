package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.entity.TownGuard;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmLevels;
import io.github.ranger3462.dungeondelving.realm.RealmLiveRaids;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmRaids;
import io.github.ranger3462.dungeondelving.realm.RealmTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks town tiers (DD-99) and area danger levels (DD-100). */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class TierAndDangerTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void townsSpendWealthToGrowATier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = new Realm();
        RealmNode town = new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, level.dimension(), BlockPos.ZERO);
        realm.add(town);
        helper.assertTrue(RealmTiers.tier(town) == 1, "towns start as hamlets");
        helper.assertTrue(RealmTiers.grow(realm) == 0, "a town without wealth to spare doesn't grow");
        realm.setStat(town, "wealth", 16);
        helper.assertTrue(RealmTiers.grow(realm) == 1 && RealmTiers.tier(town) == 2, "with 10 to spend and 5 kept back, it grows");
        helper.assertTrue(town.stat("wealth") == 6 && town.stat("defense") == 10 + RealmTiers.DEFENSE_PER_TIER, "paying for it, and gaining defense");
        helper.assertTrue(realm.log().stream().anyMatch(entry -> entry.text().contains("grown into a village")), "with news");
        realm.setStat(town, "wealth", 100);
        realm.setStat(town, "damage", 5);
        helper.assertTrue(RealmTiers.grow(realm) == 0, "not while it's still rebuilding from a raid");
        realm.setStat(town, "damage", 0);
        for (int i = 0; i < 10; i++) {
            RealmTiers.grow(realm);
        }
        helper.assertTrue(RealmTiers.tier(town) == RealmTiers.MAX_TIER, "it tops out as a city");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void aBiggerTownsGuardsAreStrongerAndHigherLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = new Realm();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        RealmNode town = new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, level.dimension(), at);
        realm.add(town);
        realm.add(new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, level.dimension(), at.offset(200, 0, 0)));
        realm.link("riverhold", "warren");
        realm.setStat(town, "tier", 5);
        realm.setStat(town, "wealth", 0);
        Realm.Raid raid = RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();
        RealmLiveRaids.LiveRaid live = RealmLiveRaids.start(realm, raid, level);
        helper.runAfterDelay(3, () -> {
          try {
            TownGuard guard = (TownGuard) RealmLiveRaids.guards(live).get(0);
            helper.assertTrue(guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "a city's guards wear diamond, even with no wealth");
            helper.assertTrue(guard.getMaxHealth() == (float) TownGuard.health(RealmRaids.defense(realm, town), 5)
                    && guard.getMaxHealth() > TownGuard.health(RealmRaids.defense(realm, town), 1), "and are tougher: " + guard.getMaxHealth());
            int cityLevel = io.github.ranger3462.dungeondelving.character.MobLevels.level(guard);
            helper.assertTrue(cityLevel >= 10 && "riverhold Guard".equals(guard.getCustomName().getString()),
                    "a city's guards are well above a villager's level 2: " + cityLevel);
            helper.assertTrue(RealmLiveRaids.guardCount(30, 5) == 8 && RealmLiveRaids.guardCount(30, 1) == 4, "and more of them can turn out");
          } finally {
            RealmLiveRaids.clearAll();
          }
          helper.succeed();
        });
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void dangerRisesAwayFromTheKingdomAndNearStrongholds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Realm realm = new Realm();
        var dim = level.dimension();
        helper.assertTrue(RealmLevels.level(realm, dim, new BlockPos(5000, 64, 0)) == 0, "a world without a realm has no danger levels");
        realm.add(new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, dim, BlockPos.ZERO));
        RealmNode warren = new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, dim, new BlockPos(600, 64, 0));
        realm.add(warren);
        realm.setStat(warren, "military", 40);

        helper.assertTrue(RealmLevels.level(realm, dim, new BlockPos(30, 64, 0)) == 0, "the kingdom's ground is safe");
        int west = RealmLevels.level(realm, dim, new BlockPos(-600, 64, 0));
        helper.assertTrue(west == 3, "600 blocks out, away from the stronghold, it's level 3: " + west);
        int east = RealmLevels.level(realm, dim, new BlockPos(600, 64, 0));
        helper.assertTrue(east == 8, "at the stronghold of military 40, it's 3 + 5: " + east);
        RealmNode tower = new RealmNode("tower", RealmNode.Type.SITE, RealmNode.KINGDOM, dim, new BlockPos(560, 64, 0));
        realm.add(tower);
        int besideTower = RealmLevels.level(realm, dim, new BlockPos(600, 64, 0));
        helper.assertTrue(besideTower == 5, "a kingdom outpost beside it only takes away the distance part: " + besideTower);
        realm.remove("tower");

        realm.setOwner(warren, RealmNode.KINGDOM);
        int taken = RealmLevels.level(realm, dim, new BlockPos(600, 64, 0));
        helper.assertTrue(taken == 0, "once the stronghold's taken, its ground is the kingdom's: " + taken);
        realm.setOwner(warren, RealmNode.NONE);
        int razed = RealmLevels.level(realm, dim, new BlockPos(600, 64, 0));
        helper.assertTrue(razed == 3, "and if it's only cleared, the area calms to the wilds' level: " + razed);
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void levelsMakeMobsTougher(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        float health = zombie.getMaxHealth();
        RealmLevels.apply(zombie, 4);
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - health * 1.6f) < 0.01f && zombie.getHealth() == zombie.getMaxHealth(),
                "level 4 gives 60% more health, healed to it: " + zombie.getMaxHealth());
        helper.assertTrue(zombie.getArmorValue() >= 2, "and some armor");
        RealmLevels.apply(zombie, 4);
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - health * 1.6f) < 0.01f, "applying it twice doesn't stack");
        zombie.discard();
        helper.succeed();
    }
}
