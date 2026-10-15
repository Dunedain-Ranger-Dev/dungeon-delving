package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Checks a built piece against {@link io.github.ranger3462.dungeondelving.dungeon.RoomSpec}: the parts that make
 * pieces fit together, not how they look. Used by {@code /dungeondelving room check} and by the game tests.
 * <ul>
 * <li>Rooms: a full floor and ceiling, an open doorway in the middle of every side, and wall framing each doorway.</li>
 * <li>Tunnels: a floor and a clear path the full length, in line with the doorways.</li>
 * <li>Caps: completely solid, so they fill a doorway.</li>
 * </ul>
 */
public final class PieceValidator {
    private PieceValidator() {}

    /** One thing wrong with a piece, at a position relative to its minimum corner. */
    public record Problem(BlockPos local, String message) {
        public String describe(BlockPos corner) {
            BlockPos at = corner.offset(local);
            return message + " at " + at.getX() + " " + at.getY() + " " + at.getZ();
        }
    }

    /** Checks the piece whose minimum corner is at {@code corner}. Returns every problem found, or none if it's valid. */
    public static List<Problem> validate(Kind kind, BlockGetter level, BlockPos corner) {
        Checker check = new Checker(level, corner);
        if (kind.isRoom()) {
            checkRoom(check);
        } else if (kind == Kind.TUNNEL) {
            checkTunnel(check);
        } else {
            checkCap(check);
        }
        return check.problems;
    }

    private static void checkRoom(Checker check) {
        int max = ROOM_SIZE - 1;
        for (int x = 0; x < ROOM_SIZE; x++) {
            for (int z = 0; z < ROOM_SIZE; z++) {
                check.solid(x, 0, z, "Floor has a hole");
                check.solid(x, ROOM_HEIGHT - 1, z, "Ceiling has a hole");
            }
        }
        checkDoorway(check, "North", (i, y) -> new int[] { i, y, 0 });
        checkDoorway(check, "South", (i, y) -> new int[] { i, y, max });
        checkDoorway(check, "West", (i, y) -> new int[] { 0, y, i });
        checkDoorway(check, "East", (i, y) -> new int[] { max, y, i });
    }

    /** A doorway hole must be open, with wall on both sides and above, so caps and tunnels fit it exactly. */
    private static void checkDoorway(Checker check, String side, SidePos at) {
        int top = DOOR_BOTTOM_Y + DOOR_HEIGHT;
        for (int i = DOOR_START; i < DOOR_START + DOOR_WIDTH; i++) {
            for (int y = DOOR_BOTTOM_Y; y < top; y++) {
                check.air(at.get(i, y), side + " doorway is blocked");
            }
            check.solid(at.get(i, top), side + " doorway needs wall above it");
        }
        for (int y = DOOR_BOTTOM_Y; y < top; y++) {
            check.solid(at.get(DOOR_START - 1, y), side + " doorway needs wall beside it");
            check.solid(at.get(DOOR_START + DOOR_WIDTH, y), side + " doorway needs wall beside it");
        }
    }

    private static void checkTunnel(Checker check) {
        for (int z = 0; z < TUNNEL_LENGTH; z++) {
            for (int x = TUNNEL_DOOR_START; x < TUNNEL_DOOR_START + DOOR_WIDTH; x++) {
                check.solid(new int[] { x, 0, z }, "Tunnel floor has a hole");
                for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                    check.air(new int[] { x, y, z }, "Tunnel path is blocked");
                }
            }
        }
    }

    private static void checkCap(Checker check) {
        for (int x = 0; x < CAP_WIDTH; x++) {
            for (int y = 0; y < CAP_HEIGHT; y++) {
                check.solid(new int[] { x, y, 0 }, "Cap has a gap");
            }
        }
    }

    private interface SidePos {
        int[] get(int along, int y);
    }

    private static final class Checker {
        private final BlockGetter level;
        private final BlockPos corner;
        private final List<Problem> problems = new ArrayList<>();

        Checker(BlockGetter level, BlockPos corner) {
            this.level = level;
            this.corner = corner;
        }

        void solid(int x, int y, int z, String message) {
            solid(new int[] { x, y, z }, message);
        }

        void solid(int[] p, String message) {
            if (level.getBlockState(corner.offset(p[0], p[1], p[2])).isAir()) {
                problems.add(new Problem(new BlockPos(p[0], p[1], p[2]), message));
            }
        }

        void air(int[] p, String message) {
            if (!level.getBlockState(corner.offset(p[0], p[1], p[2])).isAir()) {
                problems.add(new Problem(new BlockPos(p[0], p[1], p[2]), message));
            }
        }
    }
}
