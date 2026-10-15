package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Cell;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Connection;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Room;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.RoomKind;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
public class DungeonLayoutTests {
    // These tests don't use the placed structure; the cap is just the smallest available piece.
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void layoutsAreConnectedAndInBounds(GameTestHelper helper) {
        for (long seed = 0; seed < 50; seed++) {
            int count = 1 + (int) (seed % 40);
            DungeonLayout layout = DungeonLayoutGenerator.generate(RandomSource.create(seed), count);
            String where = " (seed " + seed + ", " + count + " rooms)";

            helper.assertTrue(layout.rooms().size() == count, "layout should have the requested room count" + where);
            helper.assertTrue(layout.rooms().get(Cell.ORIGIN).kind() == RoomKind.START, "start room should be at the origin" + where);
            helper.assertTrue(layout.distancesFromStart().size() == count, "every room should be reachable from the start" + where);

            for (Cell cell : layout.rooms().keySet()) {
                helper.assertTrue(Math.abs(cell.x()) <= DungeonLayoutGenerator.MAX_RADIUS && Math.abs(cell.z()) <= DungeonLayoutGenerator.MAX_RADIUS,
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
        DungeonLayout a = DungeonLayoutGenerator.generate(RandomSource.create(1234), 20);
        DungeonLayout b = DungeonLayoutGenerator.generate(RandomSource.create(1234), 20);
        helper.assertTrue(a.equals(b), "the same seed should produce the same layout");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void roomCountIsCappedToRegion(GameTestHelper helper) {
        DungeonLayout layout = DungeonLayoutGenerator.generate(RandomSource.create(7), 10_000);
        helper.assertTrue(layout.rooms().size() == DungeonLayoutGenerator.maxRooms(), "room count should be capped to what fits in a region");
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
        DungeonLayout layout = new DungeonLayout(
                Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT),
                        east, new Room(east, RoomKind.COMMON, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK)),
                Set.of(Connection.between(Cell.ORIGIN, east)));
        DungeonPieces pieces = DungeonPieces.load(level.getServer());
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above(120);
        DungeonBuilder.plan(layout, pieces, center, RandomSource.create(1)).forEach(placement -> DungeonBuilder.place(level, placement, false));

        BlockPos a = DungeonBuilder.roomMin(center, Cell.ORIGIN);
        BlockPos b = DungeonBuilder.roomMin(center, east);
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

    /**
     * Builds two rooms joined east-west with their bedrock skin and checks there is bedrock directly behind every
     * outer surface, while the space beside the tunnel corridor and the insides of rooms and tunnels stay open.
     */
    @GameTest(template = ANY_TEMPLATE)
    public static void bedrockSkinSealsDungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cell east = new Cell(1, 0);
        DungeonLayout layout = new DungeonLayout(
                Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.NONE, Mirror.NONE),
                        east, new Room(east, RoomKind.COMMON, Rotation.NONE, Mirror.NONE)),
                Set.of(Connection.between(Cell.ORIGIN, east)));
        DungeonPieces pieces = DungeonPieces.load(level.getServer());
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above(120);
        DungeonBuilder.plan(layout, pieces, center, RandomSource.create(1)).forEach(placement -> DungeonBuilder.place(level, placement, false));
        DungeonBuilder.shell(layout, center).forEach(box -> DungeonBuilder.fillShell(level, box));

        BlockPos a = DungeonBuilder.roomMin(center, Cell.ORIGIN);
        BlockPos b = DungeonBuilder.roomMin(center, east);
        int mid = ROOM_SIZE / 2;
        int y = 5;
        int corridorX = a.getX() + ROOM_SIZE + TUNNEL_LENGTH / 2;
        try {
            // Rooms: under the floor and over the ceiling, reaching one block past the edges to seal corners.
            bedrock(helper, a.offset(mid, -1, mid), "under the room floor");
            bedrock(helper, a.offset(-1, -1, -1), "under the corner between two caps");
            bedrock(helper, a.offset(mid, ROOM_HEIGHT, mid), "over the room ceiling");
            // Caps: directly behind each one.
            bedrock(helper, a.offset(mid, y, -2), "behind the start room's north cap");
            bedrock(helper, a.offset(-2, y, mid), "behind the start room's west cap");
            bedrock(helper, b.offset(ROOM_SIZE + 1, y, mid), "behind the east room's east cap");
            // Tunnel: around the corridor and behind the end walls.
            bedrock(helper, new BlockPos(corridorX, a.getY() + y, a.getZ() + CORRIDOR_START - 1), "beside the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() + y, a.getZ() + CORRIDOR_START + CORRIDOR_WIDTH), "beside the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() - 1, a.getZ() + mid), "under the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() + CORRIDOR_HEIGHT, a.getZ() + mid), "over the corridor");
            bedrock(helper, a.offset(ROOM_SIZE + 1, y, 3), "behind the tunnel's end wall");
            bedrock(helper, b.offset(-2, y, ROOM_SIZE - 3), "behind the tunnel's other end wall");

            // A tight skin, not a solid block: the space beside the corridor and inside pieces stays open.
            helper.assertTrue(level.getBlockState(new BlockPos(corridorX, a.getY() + y, a.getZ() + 3)).isAir(), "the space beside the corridor should stay empty");
            helper.assertFalse(level.getBlockState(a.offset(mid, 0, mid)).is(Blocks.BEDROCK), "room floors should stay breakable");
            helper.assertTrue(level.getBlockState(a.offset(mid, 4, mid)).isAir(), "rooms should stay open inside");
            helper.assertTrue(level.getBlockState(new BlockPos(corridorX, a.getY() + DOOR_BOTTOM_Y, a.getZ() + DOOR_START + 2)).isAir(),
                    "the tunnel should stay open inside");
        } finally {
            clear(level, a.offset(-2, -1, -2), b.offset(ROOM_SIZE + 1, ROOM_HEIGHT, ROOM_SIZE + 1));
        }
        helper.succeed();
    }

    /**
     * Treats every block that isn't bedrock as breakable and floods outward from inside the start room. With an
     * L-shaped layout (tunnels in both directions, caps and tunnels meeting at corners), the flood must never escape.
     */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 200)
    public static void bedrockSkinIsAirtight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cell east = new Cell(1, 0);
        Cell south = new Cell(0, 1);
        DungeonLayout layout = new DungeonLayout(
                Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.NONE, Mirror.NONE),
                        east, new Room(east, RoomKind.COMMON, Rotation.CLOCKWISE_90, Mirror.NONE),
                        south, new Room(south, RoomKind.OBJECTIVE, Rotation.NONE, Mirror.LEFT_RIGHT)),
                Set.of(Connection.between(Cell.ORIGIN, east), Connection.between(Cell.ORIGIN, south)));
        DungeonPieces pieces = DungeonPieces.load(level.getServer());
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above(120);
        DungeonBuilder.plan(layout, pieces, center, RandomSource.create(1)).forEach(placement -> DungeonBuilder.place(level, placement, false));
        DungeonBuilder.shell(layout, center).forEach(box -> DungeonBuilder.fillShell(level, box));

        BlockPos a = DungeonBuilder.roomMin(center, Cell.ORIGIN);
        BlockPos far = DungeonBuilder.roomMin(center, new Cell(1, 1)).offset(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        BlockPos min = a.offset(-4, -3, -4);
        BlockPos max = far.offset(4, 3, 4);
        try {
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            BlockPos start = a.offset(ROOM_SIZE / 2, 4, ROOM_SIZE / 2);
            seen.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (pos.getX() <= min.getX() || pos.getY() <= min.getY() || pos.getZ() <= min.getZ()
                        || pos.getX() >= max.getX() || pos.getY() >= max.getY() || pos.getZ() >= max.getZ()) {
                    helper.fail("The bedrock skin leaks: reached " + pos.toShortString() + " from inside the start room");
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (!seen.contains(next) && !level.getBlockState(next).is(Blocks.BEDROCK)) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
            helper.assertTrue(seen.size() > ROOM_SIZE * ROOM_SIZE, "the flood should have filled the dungeon's inside");
        } finally {
            clear(level, min, max);
        }
        helper.succeed();
    }

    private static void bedrock(GameTestHelper helper, BlockPos pos, String what) {
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.BEDROCK), what + " should be bedrock at " + pos.toShortString());
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
