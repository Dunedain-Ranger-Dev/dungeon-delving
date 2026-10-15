package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Cell;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Connection;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Room;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.RoomKind;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Grows a dungeon layout outward from the start room. Each new room is attached to an existing one by a tunnel, so
 * the rooms always form a connected tree; a few extra tunnels between neighboring rooms then add loops. The room
 * farthest from the start becomes the objective room.
 */
public final class DungeonLayoutGenerator {
    public static final int DEFAULT_ROOMS = 15;
    /** How far from the start a room may be, in cells. Keeps a whole dungeon inside one 1024-block region. */
    public static final int MAX_RADIUS = 6;
    /** Chance that two neighboring rooms not yet joined get an extra tunnel, creating a loop. */
    public static final float LOOP_CHANCE = 0.15f;

    private static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private DungeonLayoutGenerator() {}

    public static int maxRooms() {
        int side = MAX_RADIUS * 2 + 1;
        return side * side;
    }

    public static DungeonLayout generate(RandomSource random, int roomCount) {
        int target = Math.max(1, Math.min(roomCount, maxRooms()));
        List<Cell> cells = new ArrayList<>();
        Set<Connection> connections = new HashSet<>();
        Set<Cell> taken = new HashSet<>();
        cells.add(Cell.ORIGIN);
        taken.add(Cell.ORIGIN);

        while (cells.size() < target) {
            Cell from = cells.get(random.nextInt(cells.size()));
            Cell next = from.neighbor(DIRECTIONS[random.nextInt(DIRECTIONS.length)]);
            if (taken.contains(next) || !inBounds(next)) {
                continue;
            }
            cells.add(next);
            taken.add(next);
            connections.add(Connection.between(from, next));
        }

        // Extra tunnels between neighbors that aren't joined yet, so the dungeon isn't a pure dead-end tree.
        for (Cell cell : cells) {
            for (Direction direction : new Direction[] { Direction.EAST, Direction.SOUTH }) {
                Cell neighbor = cell.neighbor(direction);
                if (taken.contains(neighbor) && random.nextFloat() < LOOP_CHANCE) {
                    connections.add(Connection.between(cell, neighbor));
                }
            }
        }

        Map<Cell, Room> rooms = new LinkedHashMap<>();
        for (Cell cell : cells) {
            rooms.put(cell, new Room(cell, RoomKind.COMMON, randomRotation(random), randomMirror(random)));
        }
        rooms.put(Cell.ORIGIN, new Room(Cell.ORIGIN, RoomKind.START, randomRotation(random), randomMirror(random)));

        if (cells.size() > 1) {
            DungeonLayout plan = new DungeonLayout(rooms, connections);
            Cell farthest = plan.distancesFromStart().entrySet().stream()
                    .max(Map.Entry.<Cell, Integer>comparingByValue().thenComparing(e -> e.getKey(), cellOrder()))
                    .orElseThrow()
                    .getKey();
            Room old = rooms.get(farthest);
            rooms.put(farthest, new Room(farthest, RoomKind.OBJECTIVE, old.rotation(), old.mirror()));
        }
        return new DungeonLayout(rooms, connections);
    }

    private static boolean inBounds(Cell cell) {
        return Math.abs(cell.x()) <= MAX_RADIUS && Math.abs(cell.z()) <= MAX_RADIUS;
    }

    /** A fixed tie-breaker, so the same seed always picks the same objective room. */
    private static Comparator<Cell> cellOrder() {
        return Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z);
    }

    private static Rotation randomRotation(RandomSource random) {
        return Rotation.values()[random.nextInt(Rotation.values().length)];
    }

    private static Mirror randomMirror(RandomSource random) {
        return Mirror.values()[random.nextInt(Mirror.values().length)];
    }
}
