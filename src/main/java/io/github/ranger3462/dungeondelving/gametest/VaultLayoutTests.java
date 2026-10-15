package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.vault.RoomSpec.*;

import java.util.Map;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.vault.layout.VaultBuilder;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout.Cell;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout.Connection;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout.Room;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout.RoomKind;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayoutGenerator;
import io.github.ranger3462.dungeondelving.vault.layout.VaultPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks generated layouts are sound, and that placed pieces line up in the world. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class VaultLayoutTests {
    // These tests don't use the placed structure; the cap is just the smallest available piece.
    private static final String ANY_TEMPLATE = "vault/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void layoutsAreConnectedAndInBounds(GameTestHelper helper) {
        for (long seed = 0; seed < 50; seed++) {
            int count = 1 + (int) (seed % 40);
            VaultLayout layout = VaultLayoutGenerator.generate(RandomSource.create(seed), count);
            String where = " (seed " + seed + ", " + count + " rooms)";

            helper.assertTrue(layout.rooms().size() == count, "layout should have the requested room count" + where);
            helper.assertTrue(layout.rooms().get(Cell.ORIGIN).kind() == RoomKind.START, "start room should be at the origin" + where);
            helper.assertTrue(layout.distancesFromStart().size() == count, "every room should be reachable from the start" + where);

            for (Cell cell : layout.rooms().keySet()) {
                helper.assertTrue(Math.abs(cell.x()) <= VaultLayoutGenerator.MAX_RADIUS && Math.abs(cell.z()) <= VaultLayoutGenerator.MAX_RADIUS,
                        "room " + cell + " is outside the region" + where);
            }
            for (Connection connection : layout.connections()) {
                int dx = connection.to().x() - connection.from().x();
                int dz = connection.to().z() - connection.from().z();
                helper.assertTrue(dx + dz == 1 && dx >= 0 && dz >= 0, "tunnels should only join neighbors " + connection + where);
                helper.assertTrue(layout.rooms().containsKey(connection.from()) && layout.rooms().containsKey(connection.to()),
                        "tunnels should only join existing rooms " + connection + where);
            }

            long objectives = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.OBJECTIVE).count();
            helper.assertTrue(objectives == (count > 1 ? 1 : 0), "there should be exactly one objective room" + where);
            if (count > 1) {
                Map<Cell, Integer> distances = layout.distancesFromStart();
                int farthest = distances.values().stream().max(Integer::compare).orElseThrow();
                Cell objective = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.OBJECTIVE).findFirst().orElseThrow().cell();
                helper.assertTrue(distances.get(objective) == farthest, "the objective should be the farthest room" + where);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void sameSeedGivesSameLayout(GameTestHelper helper) {
        VaultLayout a = VaultLayoutGenerator.generate(RandomSource.create(1234), 20);
        VaultLayout b = VaultLayoutGenerator.generate(RandomSource.create(1234), 20);
        helper.assertTrue(a.equals(b), "the same seed should produce the same layout");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void roomCountIsCappedToRegion(GameTestHelper helper) {
        VaultLayout layout = VaultLayoutGenerator.generate(RandomSource.create(7), 10_000);
        helper.assertTrue(layout.rooms().size() == VaultLayoutGenerator.maxRooms(), "room count should be capped to what fits in a region");
        helper.succeed();
    }

    /**
     * Builds two rooms joined east-west, both turned and mirrored, and checks the tunnel, doorways and caps all land
     * exactly in place. Built well above the test area and removed afterwards.
     */
    @GameTest(template = ANY_TEMPLATE)
    public static void placedPiecesLineUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cell east = new Cell(1, 0);
        VaultLayout layout = new VaultLayout(
                Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT),
                        east, new Room(east, RoomKind.COMMON, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK)),
                Set.of(Connection.between(Cell.ORIGIN, east)));
        VaultPieces pieces = VaultPieces.load(level.getServer().getResourceManager());
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above(120);
        VaultBuilder.plan(layout, pieces, center, RandomSource.create(1)).forEach(placement -> VaultBuilder.place(level, placement));

        BlockPos a = VaultBuilder.roomMin(center, Cell.ORIGIN);
        BlockPos b = VaultBuilder.roomMin(center, east);
        int doorMid = DOOR_START + DOOR_WIDTH / 2;
        int doorY = DOOR_BOTTOM_Y + 2;
        int aboveDoor = DOOR_BOTTOM_Y + DOOR_HEIGHT + 2;
        try {
            helper.assertTrue(b.getX() - a.getX() == GRID_PITCH, "rooms should be one grid pitch apart");
            for (BlockPos room : new BlockPos[] { a, b }) {
                solid(helper, room, "room floor corner");
                solid(helper, room.offset(ROOM_SIZE - 1, 0, ROOM_SIZE - 1), "opposite room floor corner");
                solid(helper, room.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, 0), "room ceiling corner");
            }

            // The tunnel's two doorway walls sit against each room's facing side.
            for (int wallX : new int[] { a.getX() + ROOM_SIZE, b.getX() - 1 }) {
                air(helper, new BlockPos(wallX, a.getY() + doorY, a.getZ() + doorMid), "doorway opening");
                solid(helper, new BlockPos(wallX, a.getY() + doorY, a.getZ() + DOOR_START - 1), "wall beside the doorway");
                solid(helper, new BlockPos(wallX, a.getY() + aboveDoor, a.getZ() + doorMid), "wall above the doorway");
                solid(helper, new BlockPos(wallX, a.getY(), a.getZ() + doorMid), "doorway threshold");
            }
            BlockPos corridor = new BlockPos(a.getX() + ROOM_SIZE + TUNNEL_LENGTH / 2, a.getY(), a.getZ() + doorMid);
            solid(helper, corridor, "corridor floor");
            air(helper, corridor.above(DOOR_BOTTOM_Y), "corridor walkway");

            // Every face without a tunnel is capped, including across the doorway spot.
            solid(helper, a.offset(doorMid, doorY, -1), "start room north cap");
            solid(helper, a.offset(doorMid, doorY, ROOM_SIZE), "start room south cap");
            solid(helper, a.offset(-1, doorY, doorMid), "start room west cap");
            solid(helper, b.offset(doorMid, doorY, -1), "east room north cap");
            solid(helper, b.offset(doorMid, doorY, ROOM_SIZE), "east room south cap");
            solid(helper, b.offset(ROOM_SIZE, doorY, doorMid), "east room east cap");
        } finally {
            clear(level, a.offset(-1, 0, -1), b.offset(ROOM_SIZE, ROOM_HEIGHT - 1, ROOM_SIZE));
        }
        helper.succeed();
    }

    private static void solid(GameTestHelper helper, BlockPos pos, String what) {
        helper.assertFalse(helper.getLevel().getBlockState(pos).isAir(), what + " should be solid at " + pos.toShortString());
    }

    private static void air(GameTestHelper helper, BlockPos pos, String what) {
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), what + " should be open at " + pos.toShortString());
    }

    private static void clear(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
