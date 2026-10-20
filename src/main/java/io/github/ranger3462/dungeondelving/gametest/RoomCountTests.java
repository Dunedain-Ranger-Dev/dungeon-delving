package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts.Range;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the per-tier room count ranges: reading them from the config, falling back to defaults, and rolling them. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RoomCountTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void rangesAreReadFromText(GameTestHelper helper) {
        helper.assertTrue(RoomCounts.parse("3-5").equals(Optional.of(new Range(3, 5))), "\"3-5\" should be 3 to 5 rooms");
        helper.assertTrue(RoomCounts.parse(" 6 - 9 ").equals(Optional.of(new Range(6, 9))), "spaces should be allowed");
        helper.assertTrue(RoomCounts.parse("4").equals(Optional.of(new Range(4, 4))), "a single number should be an exact count");
        helper.assertTrue(RoomCounts.parse("1-3").equals(Optional.of(new Range(2, 3))), "every dungeon needs a start and an objective room");
        helper.assertTrue(RoomCounts.parse("150-500").equals(Optional.of(new Range(150, DungeonLayoutGenerator.maxRooms()))),
                "counts should stop at what a dungeon region fits");
        for (String bad : new String[] { "", "five", "9-6", "1-2-3" }) {
            helper.assertTrue(RoomCounts.parse(bad).isEmpty(), "\"" + bad + "\" shouldn't be a valid range");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachTierRollsWithinItsRange(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        for (int tier = 1; tier <= 4; tier++) {
            Range range = RoomCounts.forTier(RoomCounts.DEFAULTS, tier);
            boolean sawMin = false;
            boolean sawMax = false;
            for (int i = 0; i < 500; i++) {
                int rooms = range.roll(random);
                helper.assertTrue(rooms >= range.min() && rooms <= range.max(), "tier " + tier + " rolled " + rooms + " rooms, outside " + range);
                sawMin |= rooms == range.min();
                sawMax |= rooms == range.max();
            }
            helper.assertTrue(sawMin && sawMax, "tier " + tier + " should roll both ends of " + range);
        }
        helper.assertTrue(RoomCounts.forTier(RoomCounts.DEFAULTS, 1).equals(new Range(4, 6)), "tier 1 defaults to 4-6 rooms");
        helper.assertTrue(RoomCounts.forTier(RoomCounts.DEFAULTS, 4).equals(new Range(16, 21)), "tier 4 defaults to 16-21 rooms");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void missingOrBrokenEntriesFallBackToDefaults(GameTestHelper helper) {
        List<String> partial = List.of("4-4", "nonsense");
        helper.assertTrue(RoomCounts.forTier(partial, 1).equals(new Range(4, 4)), "a configured tier should use its own range");
        helper.assertTrue(RoomCounts.forTier(partial, 2).equals(new Range(7, 10)), "a broken entry should fall back to the default");
        helper.assertTrue(RoomCounts.forTier(partial, 3).equals(new Range(11, 15)), "a missing entry should fall back to the default");
        helper.succeed();
    }
}
