package io.github.ranger3462.dwarvenvaults.vault;

/**
 * The fixed dimensions every vault piece must follow, so any room fits any grid slot and doorways always line up.
 * <p>
 * Coordinates are local to a piece, counted from 0. Rooms and tunnels share a floor at y=0, and every doorway
 * opening starts at y=1, directly on top of that floor.
 */
public final class RoomSpec {
    // Rooms: 33 x 33 footprint, 25 tall, doorways centered on all four sides.
    public static final int ROOM_SIZE = 33;
    public static final int ROOM_HEIGHT = 25;

    // Tunnels: 9 wide, 33 long (runs along z), 9 tall.
    public static final int TUNNEL_WIDTH = 9;
    public static final int TUNNEL_LENGTH = 33;
    public static final int TUNNEL_HEIGHT = 9;

    // Doorways: 5 wide, 6 tall, sitting on top of the 1-block floor.
    public static final int DOOR_WIDTH = 5;
    public static final int DOOR_HEIGHT = 6;
    public static final int DOOR_BOTTOM_Y = 1;

    // Door plugs: fill one doorway, 1 block deep.
    public static final int PLUG_DEPTH = 1;

    /** First block of a doorway along a room face (blocks 14-18 on a 33-wide face). */
    public static final int ROOM_DOOR_START = (ROOM_SIZE - DOOR_WIDTH) / 2;
    /** First block of a doorway across a tunnel's width (blocks 2-6 on a 9-wide tunnel). */
    public static final int TUNNEL_DOOR_START = (TUNNEL_WIDTH - DOOR_WIDTH) / 2;

    private RoomSpec() {}

    public static boolean inRoomDoorSpan(int i) {
        return i >= ROOM_DOOR_START && i < ROOM_DOOR_START + DOOR_WIDTH;
    }

    public static boolean inTunnelDoorSpan(int i) {
        return i >= TUNNEL_DOOR_START && i < TUNNEL_DOOR_START + DOOR_WIDTH;
    }

    public static boolean inDoorHeight(int y) {
        return y >= DOOR_BOTTOM_Y && y < DOOR_BOTTOM_Y + DOOR_HEIGHT;
    }
}
