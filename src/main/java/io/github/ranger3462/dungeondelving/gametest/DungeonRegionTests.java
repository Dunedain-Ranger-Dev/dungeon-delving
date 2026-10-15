package io.github.ranger3462.dungeondelving.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the dungeon dimension type loads and that regions are handed out, released and reused correctly. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonRegionTests {
    // These tests don't use the placed structure; the cap is just the smallest available piece.
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    /**
     * The game test server only creates the overworld, so this checks the dungeon's dimension type loaded from data
     * with the right settings. The dimension itself is checked in-game with dev/enter_dungeon.
     */
    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonDimensionTypeLoads(GameTestHelper helper) {
        var types = helper.getLevel().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        DimensionType dungeon = types.get(DungeonDimension.DUNGEON.location());
        helper.assertTrue(dungeon != null, "dungeon dimension type should be loaded from data");
        helper.assertFalse(dungeon.hasSkyLight(), "dungeon should have no skylight");
        helper.assertFalse(dungeon.bedWorks(), "beds should not work in dungeons");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void regionsAreReusedOnlyAfterWipe(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        int first = regions.allocate();
        int second = regions.allocate();
        helper.assertTrue(first == 0 && second == 1, "new regions should be numbered from 0");

        regions.startRun(run(alice, first));
        regions.startRun(run(bob, second));
        regions.endRun(alice);
        helper.assertTrue(regions.releasedRegions().contains(first), "a finished run's region should wait to be wiped");
        helper.assertTrue(regions.allocate() == 2, "a region that hasn't been wiped must not be reused");

        Path folder = tempDimensionFolder();
        Path firstFile = touch(folder.resolve("region").resolve("r." + DungeonDimension.firstRegionFileX(first) + ".0.mca"));
        Path secondFile = touch(folder.resolve("region").resolve("r." + DungeonDimension.firstRegionFileX(second) + ".0.mca"));
        List<Integer> wiped = wipe(regions, folder);

        helper.assertTrue(wiped.equals(List.of(first)), "only the released region should be wiped");
        helper.assertFalse(Files.exists(firstFile), "the released region's files should be deleted");
        helper.assertTrue(Files.exists(secondFile), "regions still in use must keep their files");
        helper.assertTrue(regions.allocate() == first, "a wiped region should be reused first");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void regionsSurviveSaveAndLoad(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        UUID player = UUID.randomUUID();
        regions.startRun(run(player, regions.allocate()));
        regions.release(regions.allocate());

        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.regionsCreated() == 2, "region count should be saved");
        helper.assertTrue(loaded.run(player).map(DungeonRun::region).orElse(-1) == 0, "active runs should be saved");
        helper.assertTrue(loaded.releasedRegions().contains(1), "released regions should be saved");
        helper.succeed();
    }

    private static DungeonRun run(UUID player, int region) {
        return new DungeonRun(player, region, Level.OVERWORLD, 0, 64, 0, 0, 0);
    }

    private static Path tempDimensionFolder() {
        try {
            return Files.createTempDirectory("dungeondelving-dungeon-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path touch(Path file) {
        try {
            Files.createDirectories(file.getParent());
            return Files.write(file, new byte[] { 0 });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Integer> wipe(DungeonRegions regions, Path folder) {
        try {
            return regions.wipeReleased(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
