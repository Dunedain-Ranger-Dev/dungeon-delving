package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuildQueue;
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
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
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

            long objectives = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.BOSS).count();
            helper.assertTrue(objectives == (count > 1 ? 1 : 0), "there should be exactly one boss room" + where);
            if (count > 1) {
                Map<Cell, Integer> distances = layout.distancesFromStart();
                int farthest = distances.values().stream().max(Integer::compare).orElseThrow();
                Cell objective = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.BOSS).findFirst().orElseThrow().cell();
                helper.assertTrue(distances.get(objective) == farthest, "the boss room should be the farthest room" + where);
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

            // Where the tunnel attaches, each room's doorway is open, framed by its own wall.
            for (int wallX : new int[] { a.getX() + ROOM_SIZE - 1, b.getX() }) {
                air(helper, new BlockPos(wallX, a.getY() + doorY, a.getZ() + doorMid), "doorway opening");
                solid(helper, new BlockPos(wallX, a.getY() + doorY, a.getZ() + DOOR_START - 1), "room wall beside the doorway");
                solid(helper, new BlockPos(wallX, a.getY() + aboveDoor, a.getZ() + doorMid), "room wall above the doorway");
            }
            BlockPos corridor = new BlockPos(a.getX() + ROOM_SIZE + TUNNEL_LENGTH / 2, a.getY(), a.getZ() + doorMid);
            solid(helper, corridor, "corridor floor");
            air(helper, corridor.above(DOOR_BOTTOM_Y), "corridor walkway");
            solid(helper, new BlockPos(corridor.getX(), a.getY() + doorY, a.getZ() + TUNNEL_START), "corridor wall");

            // Every doorway without a tunnel is plugged by a cap.
            int max = ROOM_SIZE - 1;
            solid(helper, a.offset(doorMid, doorY, 0), "start room north cap");
            solid(helper, a.offset(doorMid, doorY, max), "start room south cap");
            solid(helper, a.offset(0, doorY, doorMid), "start room west cap");
            solid(helper, b.offset(doorMid, doorY, 0), "east room north cap");
            solid(helper, b.offset(doorMid, doorY, max), "east room south cap");
            solid(helper, b.offset(max, doorY, doorMid), "east room east cap");
        } finally {
            clear(level, a, b.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, ROOM_SIZE - 1));
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
            // Rooms: under the floor, over the ceiling, and just outside each wall, reaching a block past the edges.
            bedrock(helper, a.offset(mid, -1, mid), "under the room floor");
            bedrock(helper, a.offset(-1, -1, -1), "under the corner between two walls");
            bedrock(helper, a.offset(mid, ROOM_HEIGHT, mid), "over the room ceiling");
            bedrock(helper, a.offset(mid, y, -1), "outside the start room's north wall");
            bedrock(helper, a.offset(-1, y, mid), "outside the start room's west wall");
            bedrock(helper, b.offset(ROOM_SIZE, y, mid), "outside the east room's east wall");
            // Where the tunnel attaches, the wall's skin leaves the corridor open but seals around it.
            bedrock(helper, a.offset(ROOM_SIZE, y, 3), "outside the start room's east wall, beside the tunnel");
            bedrock(helper, a.offset(ROOM_SIZE, TUNNEL_HEIGHT + 1, mid), "outside the start room's east wall, above the tunnel");
            bedrock(helper, b.offset(-1, y, ROOM_SIZE - 3), "outside the east room's west wall, beside the tunnel");
            // Tunnel: under, over and both sides of the corridor.
            bedrock(helper, new BlockPos(corridorX, a.getY() + y, a.getZ() + TUNNEL_START - 1), "beside the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() + y, a.getZ() + TUNNEL_START + TUNNEL_WIDTH), "beside the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() - 1, a.getZ() + mid), "under the corridor");
            bedrock(helper, new BlockPos(corridorX, a.getY() + TUNNEL_HEIGHT, a.getZ() + mid), "over the corridor");

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
                        south, new Room(south, RoomKind.BOSS, Rotation.NONE, Mirror.LEFT_RIGHT)),
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

    /** Another mod's piece set loads from its own folder, by the same prefixes (here, Dungeon Delving's own, by folder). */
    @GameTest(template = ANY_TEMPLATE)
    public static void piecesLoadFromAnyFolder(GameTestHelper helper) {
        DungeonPieces byFolder = DungeonPieces.load(helper.getLevel().getServer(), DungeonDelving.MODID, DungeonPieces.FOLDER);
        DungeonPieces own = DungeonPieces.load(helper.getLevel().getServer());
        for (DungeonPieces.Kind kind : DungeonPieces.Kind.values()) {
            helper.assertTrue(own.pool(kind).containsAll(byFolder.pool(kind)) && !byFolder.pool(kind).isEmpty(),
                    kind + " pieces should load by folder: " + byFolder.pool(kind));
        }
        helper.succeed();
    }

    /** The build queue builds in whichever level it's given, not just the dungeon dimension (Deep Mines builds in its own). */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 100)
    public static void buildQueueBuildsInAnyLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonLayout layout = new DungeonLayout(Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.NONE, Mirror.NONE)), Set.of());
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above(120);
        int region = -1;
        DungeonBuildQueue.add(level.dimension(), region,
                DungeonBuilder.plan(layout, DungeonPieces.load(level.getServer()), center, RandomSource.create(1)), DungeonMarkers.Rolls.fromConfig(1));
        BlockPos min = DungeonBuilder.roomMin(center, Cell.ORIGIN);
        helper.succeedWhen(() -> {
            helper.assertTrue(DungeonBuildQueue.pending(level.dimension(), region) == 0, "the queue should finish the room");
            solid(helper, min, "room floor corner");
            solid(helper, min.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, ROOM_SIZE - 1), "opposite room ceiling corner");
            clear(level, min.offset(-1, -1, -1), min.offset(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE));
        });
    }

    /**
     * Light from a build is worked out before its chunks unload, and reaches across chunk borders (DD-116): a lamp the
     * queue places beside a room, far from the test grid where nothing else keeps chunks loaded, lights the air
     * around it, fading by one a block, past the next chunk's border. The check waits long enough for chunks nothing
     * holds to have unloaded, and loads them again to read their light.
     */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 400)
    public static void builtLightReachesAcrossChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int region = -7;
        BlockPos center = new BlockPos(8192, helper.absolutePos(BlockPos.ZERO).getY() + 120, 8192);
        DungeonLayout layout = new DungeonLayout(Map.of(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, Rotation.NONE, Mirror.NONE)), Set.of());
        DungeonBuildQueue.add(level.dimension(), region,
                DungeonBuilder.plan(layout, DungeonPieces.load(level.getServer()), center, RandomSource.create(1)), DungeonMarkers.Rolls.fromConfig(1));
        // Three blocks short of a chunk border, well clear of the room.
        BlockPos min = DungeonBuilder.roomMin(center, Cell.ORIGIN);
        int border = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(min.getX() - 16));
        BlockPos lamp = new BlockPos(border - 3, min.getY() + 5, min.getZ() - 8);
        DungeonBuildQueue.addBlocks(level.dimension(), region, List.of(lamp), Blocks.GLOWSTONE.defaultBlockState(), "test lamp");
        helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
            helper.assertTrue(DungeonBuildQueue.pending(level.dimension(), region) == 0, "the queue should finish");
            helper.assertFalse(level.getChunkSource().getLightEngine().hasLightWork(), "the light engine should catch up");
            for (int step = 1; step <= 8; step++) {
                BlockPos pos = lamp.east(step);
                level.getChunk(pos);
                int light = level.getBrightness(LightLayer.BLOCK, pos);
                helper.assertTrue(light >= 15 - step, "block light " + step + " east of the lamp should be at least " + (15 - step) + ", not " + light);
            }
            level.setBlock(lamp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }));
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
