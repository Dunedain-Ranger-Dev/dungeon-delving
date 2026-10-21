package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Where a dungeon's rooms sit on the grid and which neighbors are joined by tunnels. Cell (0, 0) is the start room.
 * This is just the plan; {@link DungeonBuilder} turns it into placed pieces.
 */
public record DungeonLayout(Map<Cell, Room> rooms, Set<Connection> connections) {

    public DungeonLayout {
        rooms = Collections.unmodifiableMap(rooms);
        connections = Collections.unmodifiableSet(connections);
    }

    /**
     * What a room is for: the entry with the exit, an ordinary room, a quarry packed with ore, a treasure room with
     * extra chests, a challenge room that gives a key when cleared, or the boss room behind the boss door.
     */
    public enum RoomKind {
        START, COMMON, QUARRY, TREASURE, CHALLENGE, BOSS
    }

    /** A grid position. Neighboring cells are one room plus one tunnel apart in the world. */
    public record Cell(int x, int z) {
        public static final Cell ORIGIN = new Cell(0, 0);

        public Cell neighbor(Direction direction) {
            return new Cell(x + direction.getStepX(), z + direction.getStepZ());
        }
    }

    /** A room slot: what kind of room goes here and how it is turned. */
    public record Room(Cell cell, RoomKind kind, Rotation rotation, Mirror mirror) {}

    /** A tunnel between two neighboring cells, stored with {@code from} always west or north of {@code to}. */
    public record Connection(Cell from, Cell to) {
        public static Connection between(Cell a, Cell b) {
            boolean aFirst = a.x() < b.x() || (a.x() == b.x() && a.z() < b.z());
            return aFirst ? new Connection(a, b) : new Connection(b, a);
        }

        /** True when the tunnel runs along x (east-west); false when it runs along z (north-south). */
        public boolean alongX() {
            return from.z() == to.z();
        }
    }

    public boolean connected(Cell cell, Direction direction) {
        return connections.contains(Connection.between(cell, cell.neighbor(direction)));
    }

    /** Steps from the start room to every room, walking through tunnels. Rooms that can't be reached are missing. */
    public Map<Cell, Integer> distancesFromStart() {
        Map<Cell, Integer> distances = new HashMap<>();
        Deque<Cell> queue = new ArrayDeque<>();
        distances.put(Cell.ORIGIN, 0);
        queue.add(Cell.ORIGIN);
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Cell next = cell.neighbor(direction);
                if (rooms.containsKey(next) && connected(cell, direction) && !distances.containsKey(next)) {
                    distances.put(next, distances.get(cell) + 1);
                    queue.add(next);
                }
            }
        }
        return distances;
    }
}
