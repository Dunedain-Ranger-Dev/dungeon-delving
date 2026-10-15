package io.github.ranger3462.dungeondelving.datagen;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.common.hash.Hashing;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Generates simple test pieces that follow {@link io.github.ranger3462.dungeondelving.dungeon.RoomSpec}, so dungeon
 * generation can be built and tested before hand-built rooms exist.
 * <p>
 * Each kind of piece has its own material so alignment is easy to check in-game: rooms are light gray, tunnels
 * (including their doorway walls) are cyan, and caps are red. Gold blocks stand in for markers.
 * <p>
 * Output goes to {@code data/dungeondelving/structure/dungeon/}. Every file name ends in {@code _placeholder} so these can
 * be filtered out once real rooms take over.
 */
public class PlaceholderRoomProvider implements DataProvider {
    public static final String FOLDER = "dungeon";

    private static final Block ROOM = Blocks.LIGHT_GRAY_CONCRETE;
    private static final Block TUNNEL = Blocks.CYAN_CONCRETE;
    private static final Block CAP = Blocks.RED_CONCRETE;
    private static final Block MARKER = Blocks.GOLD_BLOCK;
    private static final Block LIGHT = Blocks.GLOWSTONE;

    private static final int MID = ROOM_SIZE / 2;
    private static final int MAX = ROOM_SIZE - 1;

    private final PackOutput.PathProvider structures;

    public PlaceholderRoomProvider(PackOutput output) {
        this.structures = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    /** Every placeholder piece, keyed by file name. */
    public static Map<String, PieceBuilder> pieces() {
        Map<String, PieceBuilder> pieces = new LinkedHashMap<>();
        pieces.put("start_placeholder", startRoom());
        pieces.put("common_pillars_placeholder", pillarsRoom());
        pieces.put("common_steps_placeholder", stepsRoom());
        pieces.put("common_cover_placeholder", coverRoom());
        pieces.put("common_walls_placeholder", wallsRoom());
        pieces.put("objective_placeholder", objectiveRoom());
        pieces.put("tunnel_placeholder", tunnel());
        pieces.put("cap_placeholder", capWall());
        return pieces;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.allOf(pieces().entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
            Path path = structures.file(DungeonDelving.id(FOLDER + "/" + entry.getKey()), "nbt");
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(entry.getValue().toNbt(), bytes);
                byte[] data = bytes.toByteArray();
                @SuppressWarnings("deprecation") // Vanilla data providers hash with SHA-1 as well.
                var hash = Hashing.sha1().hashBytes(data);
                output.writeIfNeeded(path, data, hash);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to save " + path, e);
            }
        })).toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Dungeon Delving placeholder rooms";
    }

    // --- Rooms ---

    private static PieceBuilder startRoom() {
        PieceBuilder room = roomShell();
        // Gold pad marks where players arrive.
        room.fill(MID - 2, 0, MID - 2, MID + 2, 0, MID + 2, MARKER);
        return room;
    }

    private static PieceBuilder pillarsRoom() {
        PieceBuilder room = roomShell();
        for (int px : new int[] { 8, 24 }) {
            for (int pz : new int[] { 8, 24 }) {
                room.fill(px - 1, 1, pz - 1, px + 1, ROOM_HEIGHT - 2, pz + 1, ROOM);
            }
        }
        room.set(3, 1, 3, MARKER);
        room.set(MAX - 3, 1, MAX - 3, MARKER);
        room.set(MID, 1, MID, MARKER);
        return room;
    }

    private static PieceBuilder stepsRoom() {
        PieceBuilder room = roomShell();
        // A three-step pyramid, each step one block high so players can climb it.
        room.fill(MID - 7, 1, MID - 7, MID + 7, 1, MID + 7, ROOM);
        room.fill(MID - 5, 2, MID - 5, MID + 5, 2, MID + 5, ROOM);
        room.fill(MID - 3, 3, MID - 3, MID + 3, 3, MID + 3, ROOM);
        room.set(MID, 4, MID, MARKER);
        return room;
    }

    private static PieceBuilder coverRoom() {
        PieceBuilder room = roomShell();
        // Scattered 3-wide, 2-tall cover walls, kept clear of the doorway lanes.
        int[][] cover = { { 5, 5 }, { 25, 6 }, { 7, 24 }, { 24, 25 }, { 11, 10 }, { 20, 21 } };
        for (int[] c : cover) {
            room.fill(c[0], 1, c[1], c[0] + 2, 2, c[1], ROOM);
        }
        room.set(4, 1, 28, MARKER);
        room.set(28, 1, 4, MARKER);
        room.set(MID, 1, MID, MARKER);
        return room;
    }

    private static PieceBuilder wallsRoom() {
        PieceBuilder room = roomShell();
        // A plus-shaped wall in each corner, leaving the center and doorway lanes open.
        for (int c : new int[] { 8, 24 }) {
            int lo = c == 8 ? 3 : 21;
            int hi = lo + 8;
            room.fill(c, 1, 3, c, 3, 11, ROOM);
            room.fill(c, 1, 21, c, 3, 29, ROOM);
            room.fill(3, 1, c, 11, 3, c, ROOM);
            room.fill(21, 1, c, 29, 3, c, ROOM);
            room.set(lo + 1, 1, lo + 1, MARKER);
            room.set(hi - 1, 1, lo + 1, MARKER);
        }
        return room;
    }

    private static PieceBuilder objectiveRoom() {
        PieceBuilder room = roomShell();
        // A raised altar with the objective marker on top.
        room.fill(MID - 3, 1, MID - 3, MID + 3, 1, MID + 3, ROOM);
        room.fill(MID - 1, 2, MID - 1, MID + 1, 2, MID + 1, ROOM);
        room.set(MID, 3, MID, MARKER);
        return room;
    }

    /** An open room: floor, ceiling and ceiling lights, with no walls. The walls come from separate wall pieces. */
    private static PieceBuilder roomShell() {
        int top = ROOM_HEIGHT - 1;
        PieceBuilder room = new PieceBuilder(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        room.fill(0, 0, 0, MAX, 0, MAX, ROOM);
        room.fill(0, top, 0, MAX, top, MAX, ROOM);
        for (int x = 6; x < MAX; x += 5) {
            for (int z = 6; z < MAX; z += 5) {
                room.set(x, top, z, LIGHT);
            }
        }
        return room;
    }

    // --- Caps and tunnels ---

    /** A wall covering one room face from the outside, with no exit. Runs along x, 1 deep along z. */
    private static PieceBuilder capWall() {
        PieceBuilder wall = new PieceBuilder(WALL_LENGTH, WALL_HEIGHT, WALL_THICKNESS);
        wall.fill(0, 0, 0, WALL_LENGTH - 1, WALL_HEIGHT - 1, WALL_THICKNESS - 1, CAP);
        return wall;
    }

    /**
     * A doorway wall at each end with a corridor between them, running along z. Only the walls and corridor are
     * placed; the rest of the piece's box is left untouched.
     */
    private static PieceBuilder tunnel() {
        PieceBuilder tunnel = new PieceBuilder(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
        int lastZ = TUNNEL_LENGTH - 1;
        tunnel.untouched(0, 0, 0, TUNNEL_WIDTH - 1, TUNNEL_HEIGHT - 1, lastZ);

        int doorEnd = DOOR_START + DOOR_WIDTH - 1;
        int doorTop = DOOR_BOTTOM_Y + DOOR_HEIGHT - 1;
        for (int z : new int[] { 0, lastZ }) {
            tunnel.fill(0, 0, z, WALL_LENGTH - 1, WALL_HEIGHT - 1, z, TUNNEL);
            tunnel.fill(DOOR_START, DOOR_BOTTOM_Y, z, doorEnd, doorTop, z, Blocks.AIR);
        }

        int x1 = CORRIDOR_START;
        int x2 = CORRIDOR_START + CORRIDOR_WIDTH - 1;
        int top = CORRIDOR_HEIGHT - 1;
        tunnel.fill(x1, 0, 1, x2, top, lastZ - 1, TUNNEL);
        tunnel.fill(x1 + 1, 1, 1, x2 - 1, top - 1, lastZ - 1, Blocks.AIR);
        for (int z = 4; z < lastZ; z += 6) {
            tunnel.set(TUNNEL_WIDTH / 2, top, z, LIGHT);
        }
        return tunnel;
    }
}
