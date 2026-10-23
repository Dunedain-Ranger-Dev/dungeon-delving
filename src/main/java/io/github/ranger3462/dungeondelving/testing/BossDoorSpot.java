package io.github.ranger3462.dungeondelving.testing;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where to stand to face a dungeon's boss door from outside, for the dev command that skips straight to the boss: in
 * the tunnel a couple of blocks out from the door's first doorway, on the doorway's floor.
 */
public final class BossDoorSpot {
    /** How far out from the door, in blocks. */
    public static final int DISTANCE = 2;

    /** Where to stand, and the way to face (toward the door). */
    public record Spot(BlockPos pos, Direction facing) {}

    private BossDoorSpot() {}

    public static Optional<Spot> find(DungeonProgress progress) {
        List<BlockPos> door = progress.door();
        if (door.isEmpty() || progress.bossSpawn() == null) {
            return Optional.empty();
        }
        // The boss room may have several doorways; take the one the first door block is in.
        BlockPos first = door.getFirst();
        List<BlockPos> doorway = door.stream()
                .filter(pos -> Math.abs(pos.getX() - first.getX()) <= RoomSpec.DOOR_WIDTH && Math.abs(pos.getZ() - first.getZ()) <= RoomSpec.DOOR_WIDTH)
                .toList();
        int minX = doorway.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = doorway.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minY = doorway.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = doorway.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = doorway.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        BlockPos middle = new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
        BlockPos room = progress.bossSpawn();
        // A doorway in a north or south wall runs along x; one in an east or west wall runs along z.
        Direction out = minZ == maxZ ? (middle.getZ() < room.getZ() ? Direction.NORTH : Direction.SOUTH)
                : (middle.getX() < room.getX() ? Direction.WEST : Direction.EAST);
        return Optional.of(new Spot(middle.relative(out, DISTANCE), out.getOpposite()));
    }
}
