package io.github.ranger3462.dwarvenvaults.vault;

/**
 * The fixed dimensions every vault piece must follow, so any room fits any grid slot and doorways always line up.
 * <p>
 * Coordinates are local to a piece, counted from 0. Rooms and tunnels share a floor at y=0, and every doorway
 * opening starts at y=1, directly on top of that floor.
 * <p>
 * Rooms have no walls of their own. The outer 1-block ring of a room is kept empty, and the generator fills each of
 * the four faces with a wall piece: a cap (solid, no exit) or a doorway wall (with an opening a tunnel attaches to).
 */
public final class RoomSpec {
    // Rooms: 33 x 33 footprint, 25 tall, outer ring left empty for walls.
    public static final int ROOM_SIZE = 33;
    public static final int ROOM_HEIGHT = 25;

    // Walls: one room face, 33 wide (along x), 25 tall, 1 deep (along z).
    public static final int WALL_LENGTH = ROOM_SIZE;
    public static final int WALL_HEIGHT = ROOM_HEIGHT;
    public static final int WALL_THICKNESS = 1;

    // Tunnels: 9 wide, 33 long (runs along z), 9 tall.
    public static final int TUNNEL_WIDTH = 9;
    public static final int TUNNEL_LENGTH = 33;
    public static final int TUNNEL_HEIGHT = 9;

    // Doorways: 5 wide, 6 tall, sitting on top of the 1-block floor.
    public static final int DOOR_WIDTH = 5;
    public static final int DOOR_HEIGHT = 6;
    public static final int DOOR_BOTTOM_Y = 1;

    /** First block of a doorway along a wall (blocks 14-18 on a 33-long wall). */
    public static final int WALL_DOOR_START = (WALL_LENGTH - DOOR_WIDTH) / 2;
    /** First block of a doorway across a tunnel's width (blocks 2-6 on a 9-wide tunnel). */
    public static final int TUNNEL_DOOR_START = (TUNNEL_WIDTH - DOOR_WIDTH) / 2;

    private RoomSpec() {}

    public static boolean inWallDoorSpan(int i) {
        return i >= WALL_DOOR_START && i < WALL_DOOR_START + DOOR_WIDTH;
    }

    public static boolean inTunnelDoorSpan(int i) {
        return i >= TUNNEL_DOOR_START && i < TUNNEL_DOOR_START + DOOR_WIDTH;
    }

    public static boolean inDoorHeight(int y) {
        return y >= DOOR_BOTTOM_Y && y < DOOR_BOTTOM_Y + DOOR_HEIGHT;
    }

    /**
     * Whether a room position is in the outer ring kept for walls. Between the floor (y=0) and ceiling
     * (y={@code ROOM_HEIGHT - 1}) these positions must stay empty; the wall pieces replace them.
     */
    public static boolean inRoomWallRing(int x, int z) {
        return x == 0 || z == 0 || x == ROOM_SIZE - 1 || z == ROOM_SIZE - 1;
    }
}
