package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.vault.RoomSpec.*;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Places each placeholder piece in a real world and checks it against {@link io.github.ranger3462.dungeondelving.vault.RoomSpec}.
 * Run with {@code ./gradlew runGameTestServer}, or in-game with {@code /test runall}.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class PlaceholderRoomTests {

    @GameTest(template = "vault/start_placeholder")
    public static void startRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/common_pillars_placeholder")
    public static void pillarsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/common_steps_placeholder")
    public static void stepsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/common_cover_placeholder")
    public static void coverRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/common_walls_placeholder")
    public static void wallsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/objective_placeholder")
    public static void objectiveRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "vault/tunnel_placeholder")
    public static void tunnel(GameTestHelper helper) {
        Piece piece = Piece.of(helper);
        piece.checkSize(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
        int lastZ = TUNNEL_LENGTH - 1;
        // A doorway wall at each end: solid except for the doorway opening.
        for (int z : new int[] { 0, lastZ }) {
            for (int x = 0; x < WALL_LENGTH; x++) {
                for (int y = 0; y < WALL_HEIGHT; y++) {
                    if (inDoorSpan(x) && inDoorHeight(y)) {
                        piece.checkAir(x, y, z, "doorway must be open");
                    } else {
                        piece.checkSolid(x, y, z, "doorway wall must be solid outside the " + DOOR_WIDTH + "x" + DOOR_HEIGHT + " opening");
                    }
                }
            }
        }
        // The corridor between them: walkable all the way through the doorway span.
        for (int z = 1; z < lastZ; z++) {
            for (int x = DOOR_START; x < DOOR_START + DOOR_WIDTH; x++) {
                piece.checkSolid(x, 0, z, "corridor must have a floor");
                for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                    piece.checkAir(x, y, z, "corridor must be open in line with the doorways");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "vault/wall_cap_placeholder")
    public static void capWall(GameTestHelper helper) {
        Piece piece = Piece.of(helper);
        piece.checkSize(WALL_LENGTH, WALL_HEIGHT, WALL_THICKNESS);
        for (int x = 0; x < WALL_LENGTH; x++) {
            for (int y = 0; y < WALL_HEIGHT; y++) {
                piece.checkSolid(x, y, 0, "cap wall must be fully solid");
            }
        }
        helper.succeed();
    }

    /** Checks a room's size, that it has a full floor and ceiling, and that each doorway's spot on its edge is clear. */
    private static void checkRoom(GameTestHelper helper) {
        Piece piece = Piece.of(helper);
        piece.checkSize(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        int max = ROOM_SIZE - 1;
        for (int x = 0; x < ROOM_SIZE; x++) {
            for (int z = 0; z < ROOM_SIZE; z++) {
                piece.checkSolid(x, 0, z, "room must have a full floor");
                piece.checkSolid(x, ROOM_HEIGHT - 1, z, "room must have a full ceiling");
            }
        }
        // Any face can get a doorway, so the doorway spot on every edge must stay clear.
        for (int i = DOOR_START; i < DOOR_START + DOOR_WIDTH; i++) {
            for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                piece.checkAir(0, y, i, "west doorway spot must be clear");
                piece.checkAir(max, y, i, "east doorway spot must be clear");
                piece.checkAir(i, y, 0, "north doorway spot must be clear");
                piece.checkAir(i, y, max, "south doorway spot must be clear");
            }
        }
        helper.succeed();
    }

    /** A placed piece, addressed in the piece's own local coordinates. */
    private record Piece(GameTestHelper helper, BlockPos origin, AABB bounds) {
        static Piece of(GameTestHelper helper) {
            AABB bounds = helper.getBounds();
            return new Piece(helper, BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), bounds);
        }

        void checkSize(int x, int y, int z) {
            int sx = (int) Math.round(bounds.getXsize());
            int sy = (int) Math.round(bounds.getYsize());
            int sz = (int) Math.round(bounds.getZsize());
            helper.assertTrue(sx == x && sy == y && sz == z,
                    "Expected size " + x + "x" + y + "x" + z + " but was " + sx + "x" + sy + "x" + sz);
        }

        void checkAir(int x, int y, int z, String why) {
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(x, y, z)).isAir(),
                    why + " at (" + x + ", " + y + ", " + z + ")");
        }

        void checkSolid(int x, int y, int z, String why) {
            helper.assertFalse(helper.getLevel().getBlockState(origin.offset(x, y, z)).isAir(),
                    why + " at (" + x + ", " + y + ", " + z + ")");
        }
    }
}
