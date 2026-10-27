package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmMap;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the realm menu's data: what's sent, and which places are in trouble. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RealmMapTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void theMenuShowsWhereTheKingdomIsInTrouble(GameTestHelper helper) {
        var dim = helper.getLevel().dimension();
        Realm realm = new Realm();
        RealmNode safe = new RealmNode("haven", RealmNode.Type.TOWN, RealmNode.KINGDOM, dim, new BlockPos(-300, 64, 0));
        realm.add(safe);
        realm.setStat(safe, "tier", 2);
        RealmNode town = new RealmNode("riverhold", RealmNode.Type.TOWN, RealmNode.KINGDOM, dim, BlockPos.ZERO);
        realm.add(town);
        RealmNode warren = new RealmNode("warren", RealmNode.Type.STRONGHOLD, DungeonFactions.GOBLINS, dim, new BlockPos(200, 64, 0));
        realm.add(warren);
        RealmNode lost = new RealmNode("millbrook", RealmNode.Type.TOWN, DungeonFactions.GOBLINS, dim, new BlockPos(300, 64, 0));
        realm.add(lost);
        realm.link("haven", "riverhold");
        realm.link("riverhold", "warren");
        realm.link("warren", "millbrook");
        realm.setStat(warren, "military", 60);
        realm.setStat(town, "morale", 2);
        realm.setStat(town, "upgrading", 3);
        RealmRaids.declare(realm, "riverhold", 0L).orElseThrow();

        helper.assertTrue(RealmMap.warnings(realm, safe).isEmpty(), "a safe town behind the border has no warnings: " + RealmMap.warnings(realm, safe));
        var warnings = String.join(" | ", RealmMap.warnings(realm, town));
        helper.assertTrue(warnings.contains("Raid at dusk: the goblins of warren") && warnings.contains("In ruins") && warnings.contains("Low morale")
                && warnings.contains("Outmatched: warren has 60 military"), "the border town's troubles: " + warnings);
        helper.assertTrue(RealmMap.notes(realm, town).get(0).contains("3 days left"), "its upgrade is a note");
        helper.assertTrue(RealmMap.warnings(realm, lost).equals(java.util.List.of("Taken by the goblins")), "a town lost to the goblins is a warning");
        helper.assertTrue(RealmMap.warnings(realm, warren).isEmpty(), "the monsters' own places aren't the kingdom's trouble");

        CompoundTag tag = RealmMap.build(realm);
        helper.assertTrue(tag.getList("nodes", Tag.TAG_COMPOUND).size() == 4 && tag.getList("links", Tag.TAG_COMPOUND).size() == 3
                && tag.getList("raids", Tag.TAG_COMPOUND).size() == 1, "every place, link and raid is sent");
        CompoundTag goblins = tag.getList("nations", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertTrue(goblins.getString("id").equals(DungeonFactions.GOBLINS) && goblins.getInt("military") == 60 && goblins.getInt("towns") == 1,
                "with how each nation stands");
        helper.assertTrue(RealmMap.build(realm).equals(tag), "two builds of an unchanged realm match, so it's only re-sent on a change");
        helper.succeed();
    }
}
