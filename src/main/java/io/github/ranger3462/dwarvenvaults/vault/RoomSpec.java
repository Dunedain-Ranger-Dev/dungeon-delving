package io.github.ranger3462.dwarvenvaults.vault;

/**
 * The fixed dimensions every vault piece must follow, so any room fits any grid slot and doorways always line up.
 * <p>
 * Coordinates are local to a piece, counted from 0. Rooms and tunnels share a floor at y=0, and every doorway
 * opening starts at y=1, directly on top of that floor.
 * <p>
 * Rooms have no walls of their own and use their whole footprint. Walls attach to the outside of each face: a cap
 * (solid, no exit) or one end of a tunnel piece, which carries a doorway wall at both ends. The corners between two
 * walls are left open.
 */
public final class RoomSpec {
    // Rooms: 33 x 33 footprint, 25 tall, no walls.
    public static final int ROOM_SIZE = 33;
    public static final int ROOM_HEIGHT = 25;

    // Walls: cover one room face from the outside. 33 long (along x), 25 tall, 1 deep (along z).
    public static final int WALL_LENGTH = ROOM_SIZE;
    public static final int WALL_HEIGHT = ROOM_HEIGHT;
    public static final int WALL_THICKNESS = 1;

    // Corridors: the walkway between two doorway walls. 9 wide, 9 tall, 33 long (along z).
    public static final int CORRIDOR_WIDTH = 9;
    public static final int CORRIDOR_HEIGHT = 9;
    public static final int CORRIDOR_LENGTH = 33;

    // Tunnel pieces: a doorway wall, a corridor, and another doorway wall, as one piece.
    public static final int TUNNEL_WIDTH = WALL_LENGTH;
    public static final int TUNNEL_HEIGHT = WALL_HEIGHT;
    public static final int TUNNEL_LENGTH = WALL_THICKNESS + CORRIDOR_LENGTH + WALL_THICKNESS;

    // Doorways: 5 wide, 6 tall, sitting on top of the floor.
    public static final int DOOR_WIDTH = 5;
    public static final int DOOR_HEIGHT = 6;
    public static final int DOOR_BOTTOM_Y = 1;

    /** First block of a doorway along a 33-long face (blocks 14-18). */
    public static final int DOOR_START = (WALL_LENGTH - DOOR_WIDTH) / 2;
    /** First block of the corridor across a tunnel piece's width (blocks 12-20). */
    public static final int CORRIDOR_START = (TUNNEL_WIDTH - CORRIDOR_WIDTH) / 2;

    private RoomSpec() {}

    public static boolean inDoorSpan(int i) {
        return i >= DOOR_START && i < DOOR_START + DOOR_WIDTH;
    }

    public static boolean inDoorHeight(int y) {
        return y >= DOOR_BOTTOM_Y && y < DOOR_BOTTOM_Y + DOOR_HEIGHT;
    }
}
