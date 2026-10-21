package io.github.ranger3462.dungeondelving.gametest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.RoomKind;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks layout types: their odds, what they make the leftover rooms into, and that their rooms exist. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class LayoutTypeTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void typesAreRolledByWeight(GameTestHelper helper) {
        RandomSource random = RandomSource.create(5);
        Map<LayoutType, Integer> counts = new EnumMap<>(LayoutType.class);
        int rolls = 4000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(LayoutType.roll(random, LayoutType.DEFAULTS), 1, Integer::sum);
        }
        expect(helper, counts.getOrDefault(LayoutType.NORMAL, 0) / (double) rolls, 0.50, "normal");
        expect(helper, counts.getOrDefault(LayoutType.RESOURCE, 0) / (double) rolls, 0.25, "resource");
        expect(helper, counts.getOrDefault(LayoutType.LOOT, 0) / (double) rolls, 0.25, "loot");
        helper.assertTrue(LayoutType.roll(random, List.of("loot=1")) == LayoutType.LOOT, "a single entry should always win");
        helper.assertTrue(LayoutType.roll(random, List.of("nonsense=4")) == LayoutType.NORMAL, "unusable entries fall back to normal");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachTypeLeansTheRightWay(GameTestHelper helper) {
        RandomSource random = RandomSource.create(9);
        for (LayoutType type : LayoutType.values()) {
            Map<RoomKind, Integer> counts = new EnumMap<>(RoomKind.class);
            for (int i = 0; i < 200; i++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(random, 16, 4, type);
                layout.rooms().values().forEach(room -> counts.merge(room.kind(), 1, Integer::sum));
                long challenges = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.CHALLENGE).count();
                helper.assertTrue(challenges == 4, type + " layouts should still have every challenge room");
            }
            int common = counts.getOrDefault(RoomKind.COMMON, 0);
            int quarry = counts.getOrDefault(RoomKind.QUARRY, 0);
            int treasure = counts.getOrDefault(RoomKind.TREASURE, 0);
            switch (type) {
                case NORMAL -> helper.assertTrue(common > quarry && common > treasure, "normal layouts should be mostly ordinary rooms");
                case RESOURCE -> helper.assertTrue(quarry > common && quarry > treasure, "resource layouts should be mostly quarries");
                case LOOT -> helper.assertTrue(treasure > quarry && treasure > common * 0.9, "loot layouts should have lots of treasure rooms");
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void quarryAndTreasureRoomsExist(GameTestHelper helper) {
        DungeonPieces pieces = DungeonPieces.load(helper.getLevel().getServer());
        helper.assertFalse(pieces.pool(Kind.QUARRY).isEmpty(), "there should be quarry rooms");
        helper.assertFalse(pieces.pool(Kind.TREASURE).isEmpty(), "there should be treasure rooms");
        var treasure = helper.getLevel().getStructureManager().get(DungeonPieces.id("treasure_placeholder")).orElseThrow();
        int chests = treasure.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModRegistries.LOOT_MARKER.get()).size()
                + treasure.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModRegistries.OBJECTIVE_MARKER.get()).size();
        helper.assertTrue(chests >= 6, "the treasure room should be full of chest markers, has " + chests);
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, double got, double share, String type) {
        helper.assertTrue(Math.abs(got - share) < 0.04, type + " should be rolled about " + Math.round(share * 100) + "% of the time, got "
                + Math.round(got * 100) + "%");
    }
}
