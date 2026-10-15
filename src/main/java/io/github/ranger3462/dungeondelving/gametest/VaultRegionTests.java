package io.github.ranger3462.dungeondelving.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.vault.VaultDimension;
import io.github.ranger3462.dungeondelving.vault.VaultRegions;
import io.github.ranger3462.dungeondelving.vault.VaultRun;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the vault dimension type loads and that regions are handed out, released and reused correctly. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class VaultRegionTests {
    // These tests don't use the placed structure; the cap is just the smallest available piece.
    private static final String ANY_TEMPLATE = "vault/wall_cap_placeholder";

    /**
     * The game test server only creates the overworld, so this checks the vault's dimension type loaded from data
     * with the right settings. The dimension itself is checked in-game with dev/enter_vault.
     */
    @GameTest(template = ANY_TEMPLATE)
    public static void vaultDimensionTypeLoads(GameTestHelper helper) {
        var types = helper.getLevel().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        DimensionType vault = types.get(VaultDimension.VAULT.location());
        helper.assertTrue(vault != null, "vault dimension type should be loaded from data");
        helper.assertFalse(vault.hasSkyLight(), "vault should have no skylight");
        helper.assertFalse(vault.bedWorks(), "beds should not work in vaults");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void regionsAreReusedOnlyAfterWipe(GameTestHelper helper) {
        VaultRegions regions = new VaultRegions();
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
        Path firstFile = touch(folder.resolve("region").resolve("r." + VaultDimension.firstRegionFileX(first) + ".0.mca"));
        Path secondFile = touch(folder.resolve("region").resolve("r." + VaultDimension.firstRegionFileX(second) + ".0.mca"));
        List<Integer> wiped = wipe(regions, folder);

        helper.assertTrue(wiped.equals(List.of(first)), "only the released region should be wiped");
        helper.assertFalse(Files.exists(firstFile), "the released region's files should be deleted");
        helper.assertTrue(Files.exists(secondFile), "regions still in use must keep their files");
        helper.assertTrue(regions.allocate() == first, "a wiped region should be reused first");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void regionsSurviveSaveAndLoad(GameTestHelper helper) {
        VaultRegions regions = new VaultRegions();
        UUID player = UUID.randomUUID();
        regions.startRun(run(player, regions.allocate()));
        regions.release(regions.allocate());

        VaultRegions loaded = VaultRegions.load(regions.save(new CompoundTag()));
        helper.assertTrue(loaded.regionsCreated() == 2, "region count should be saved");
        helper.assertTrue(loaded.run(player).map(VaultRun::region).orElse(-1) == 0, "active runs should be saved");
        helper.assertTrue(loaded.releasedRegions().contains(1), "released regions should be saved");
        helper.succeed();
    }

    private static VaultRun run(UUID player, int region) {
        return new VaultRun(player, region, Level.OVERWORLD, 0, 64, 0, 0, 0);
    }

    private static Path tempDimensionFolder() {
        try {
            return Files.createTempDirectory("dungeondelving-vault-test");
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

    private static List<Integer> wipe(VaultRegions regions, Path folder) {
        try {
            return regions.wipeReleased(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
