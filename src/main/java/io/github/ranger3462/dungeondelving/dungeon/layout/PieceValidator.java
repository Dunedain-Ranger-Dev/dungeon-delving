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
 * <li>Rooms: a full floor and ceiling, and every side's doorway spot clear.</li>
 * <li>Tunnels: a doorway open at both ends, with a floor and a clear path between them.</li>
 * <li>Caps: the doorway area fully blocked.</li>
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
        for (int i = DOOR_START; i < DOOR_START + DOOR_WIDTH; i++) {
            for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                check.air(i, y, 0, "North doorway spot is blocked");
                check.air(i, y, max, "South doorway spot is blocked");
                check.air(0, y, i, "West doorway spot is blocked");
                check.air(max, y, i, "East doorway spot is blocked");
            }
        }
    }

    private static void checkTunnel(Checker check) {
        int lastZ = TUNNEL_LENGTH - 1;
        for (int x = DOOR_START; x < DOOR_START + DOOR_WIDTH; x++) {
            for (int z = 0; z <= lastZ; z++) {
                check.solid(x, 0, z, "Tunnel floor has a hole");
                for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                    String where = z == 0 || z == lastZ ? "Tunnel doorway is blocked" : "Tunnel path is blocked";
                    check.air(x, y, z, where);
                }
            }
        }
        // The end walls must close off everything around the doorway, or players could walk out of the tunnel.
        for (int z : new int[] { 0, lastZ }) {
            check.solid(DOOR_START - 1, DOOR_BOTTOM_Y, z, "Tunnel end wall is open beside the doorway");
            check.solid(DOOR_START + DOOR_WIDTH, DOOR_BOTTOM_Y, z, "Tunnel end wall is open beside the doorway");
            check.solid(DOOR_START + DOOR_WIDTH / 2, DOOR_BOTTOM_Y + DOOR_HEIGHT, z, "Tunnel end wall is open above the doorway");
        }
    }

    private static void checkCap(Checker check) {
        for (int x = DOOR_START; x < DOOR_START + DOOR_WIDTH; x++) {
            for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                check.solid(x, y, 0, "Cap leaves the doorway open");
            }
        }
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
            if (level.getBlockState(corner.offset(x, y, z)).isAir()) {
                problems.add(new Problem(new BlockPos(x, y, z), message));
            }
        }

        void air(int x, int y, int z, String message) {
            if (!level.getBlockState(corner.offset(x, y, z)).isAir()) {
                problems.add(new Problem(new BlockPos(x, y, z), message));
            }
        }
    }
}
