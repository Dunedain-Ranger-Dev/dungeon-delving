package io.github.ranger3462.dungeondelving.dungeon;

/**
 * The fixed dimensions every dungeon piece must follow, so any room fits any grid slot and doorways always line up.
 * <p>
 * Coordinates are local to a piece, counted from 0. Rooms and tunnels share a floor at y=0, and every doorway starts
 * at y=1, directly on top of that floor.
 * <p>
 * A room is the whole room, walls included, with a doorway hole in the middle of every side. A tunnel is a corridor
 * that runs from one room's doorway to the next; a cap is a plug that fills a doorway with no tunnel.
 */
public final class RoomSpec {
    // Rooms: 33 x 33 footprint, 13 tall, walls included.
    public static final int ROOM_SIZE = 33;
    public static final int ROOM_HEIGHT = 13;

    // Doorways: a 5 wide, 6 tall hole in the middle of each room wall, sitting on top of the floor.
    public static final int DOOR_WIDTH = 5;
    public static final int DOOR_HEIGHT = 6;
    public static final int DOOR_BOTTOM_Y = 1;
    /** First block of a doorway along a room side (blocks 14-18). */
    public static final int DOOR_START = (ROOM_SIZE - DOOR_WIDTH) / 2;

    // Caps: a plug exactly filling one doorway. 5 wide (along x), 6 tall, 1 deep (along z).
    public static final int CAP_WIDTH = DOOR_WIDTH;
    public static final int CAP_HEIGHT = DOOR_HEIGHT;
    public static final int CAP_DEPTH = 1;

    // Tunnels: a corridor between two rooms' doorways. 9 wide (along x), 9 tall, 33 long (along z).
    public static final int TUNNEL_WIDTH = 9;
    public static final int TUNNEL_HEIGHT = 9;
    public static final int TUNNEL_LENGTH = 33;
    /** First block of a tunnel across a room side: the corridor is centered on the doorway (blocks 12-20). */
    public static final int TUNNEL_START = (ROOM_SIZE - TUNNEL_WIDTH) / 2;
    /** First block of the doorway span across a tunnel's own width (blocks 2-6). */
    public static final int TUNNEL_DOOR_START = (TUNNEL_WIDTH - DOOR_WIDTH) / 2;

    /** Distance between neighboring rooms' corners on the dungeon grid: one room plus one tunnel. */
    public static final int GRID_PITCH = ROOM_SIZE + TUNNEL_LENGTH;

    private RoomSpec() {}

    /** Whether a position along a room side is within the doorway (blocks 14-18). */
    public static boolean inDoorSpan(int i) {
        return i >= DOOR_START && i < DOOR_START + DOOR_WIDTH;
    }

    public static boolean inDoorHeight(int y) {
        return y >= DOOR_BOTTOM_Y && y < DOOR_BOTTOM_Y + DOOR_HEIGHT;
    }
}
