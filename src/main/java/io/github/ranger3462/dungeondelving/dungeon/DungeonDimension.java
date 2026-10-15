package io.github.ranger3462.dungeondelving.dungeon;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The dungeon dimension and how it is divided into regions. Each dungeon run gets its own region, laid out in a row
 * along x. A region is exactly two by two Minecraft region files (1024 x 1024 blocks), so a used region can be wiped
 * by deleting its files.
 */
public final class DungeonDimension {
    public static final ResourceKey<Level> DUNGEON = ResourceKey.create(Registries.DIMENSION, DungeonDelving.id("dungeon"));

    /** Width and depth of one dungeon region in blocks. Must be a multiple of {@link #REGION_FILE_SIZE}. */
    public static final int REGION_SIZE = 1024;
    /** Blocks covered by one Minecraft region (.mca) file along x or z. */
    public static final int REGION_FILE_SIZE = 512;
    /** The y level every dungeon piece's floor sits on. */
    public static final int FLOOR_Y = 64;

    private DungeonDimension() {}

    /** The minimum corner of a region. */
    public static BlockPos regionCorner(int region) {
        return new BlockPos(region * REGION_SIZE, FLOOR_Y, 0);
    }

    /** The center of a region, at floor level. The start room is placed here. */
    public static BlockPos regionCenter(int region) {
        return regionCorner(region).offset(REGION_SIZE / 2, 0, REGION_SIZE / 2);
    }

    /** The first region file x coordinate a region covers. */
    public static int firstRegionFileX(int region) {
        return region * REGION_SIZE / REGION_FILE_SIZE;
    }

    /** How many region files a region spans along each axis. */
    public static int regionFilesPerSide() {
        return REGION_SIZE / REGION_FILE_SIZE;
    }
}
