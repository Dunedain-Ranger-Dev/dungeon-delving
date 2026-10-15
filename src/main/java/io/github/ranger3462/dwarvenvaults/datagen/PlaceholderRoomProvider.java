package io.github.ranger3462.dwarvenvaults.datagen;

import static io.github.ranger3462.dwarvenvaults.vault.RoomSpec.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.common.hash.Hashing;

import io.github.ranger3462.dwarvenvaults.DwarvenVaults;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Generates simple test pieces that follow {@link io.github.ranger3462.dwarvenvaults.vault.RoomSpec}, so vault
 * generation can be built and tested before hand-built rooms exist. They use the builder palette: white wool walls,
 * blue wool accents, gray wool floors, and gold blocks as marker stand-ins.
 * <p>
 * Output goes to {@code data/dwarvenvaults/structure/vault/}. Every file name ends in {@code _placeholder} so these can
 * be filtered out once real rooms take over.
 */
public class PlaceholderRoomProvider implements DataProvider {
    public static final String FOLDER = "vault";

    private static final Block WALL = Blocks.WHITE_WOOL;
    private static final Block ACCENT = Blocks.BLUE_WOOL;
    private static final Block FLOOR = Blocks.GRAY_WOOL;
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
        pieces.put("plug_placeholder", doorPlug());
        return pieces;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.allOf(pieces().entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
            Path path = structures.file(DwarvenVaults.id(FOLDER + "/" + entry.getKey()), "nbt");
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
        return "Dwarven Vaults placeholder rooms";
    }

    // --- Rooms ---

    private static PieceBuilder startRoom() {
        PieceBuilder room = roomShell();
        // Blue pad marks where players arrive.
        room.fill(MID - 2, 0, MID - 2, MID + 2, 0, MID + 2, ACCENT);
        return room;
    }

    private static PieceBuilder pillarsRoom() {
        PieceBuilder room = roomShell();
        for (int px : new int[] { 8, 24 }) {
            for (int pz : new int[] { 8, 24 }) {
                room.fill(px - 1, 1, pz - 1, px + 1, ROOM_HEIGHT - 2, pz + 1, ACCENT);
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
        room.fill(MID - 7, 1, MID - 7, MID + 7, 1, MID + 7, FLOOR);
        room.fill(MID - 5, 2, MID - 5, MID + 5, 2, MID + 5, FLOOR);
        room.fill(MID - 3, 3, MID - 3, MID + 3, 3, MID + 3, FLOOR);
        room.set(MID, 4, MID, MARKER);
        return room;
    }

    private static PieceBuilder coverRoom() {
        PieceBuilder room = roomShell();
        // Scattered 3-wide, 2-tall cover walls, kept clear of the doorway lanes.
        int[][] cover = { { 5, 5 }, { 25, 6 }, { 7, 24 }, { 24, 25 }, { 11, 10 }, { 20, 21 } };
        for (int[] c : cover) {
            room.fill(c[0], 1, c[1], c[0] + 2, 2, c[1], ACCENT);
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
            room.fill(c, 1, 3, c, 3, 11, ACCENT);
            room.fill(c, 1, 21, c, 3, 29, ACCENT);
            room.fill(3, 1, c, 11, 3, c, ACCENT);
            room.fill(21, 1, c, 29, 3, c, ACCENT);
            room.set(lo + 1, 1, lo + 1, MARKER);
            room.set(hi - 1, 1, lo + 1, MARKER);
        }
        return room;
    }

    private static PieceBuilder objectiveRoom() {
        PieceBuilder room = roomShell();
        // A raised altar with the objective marker on top.
        room.fill(MID - 3, 1, MID - 3, MID + 3, 1, MID + 3, ACCENT);
        room.fill(MID - 1, 2, MID - 1, MID + 1, 2, MID + 1, ACCENT);
        room.set(MID, 3, MID, MARKER);
        return room;
    }

    // --- Tunnel and plug ---

    private static PieceBuilder tunnel() {
        // Runs along z. Both ends are open; the room walls they connect to frame the doorway.
        PieceBuilder tunnel = new PieceBuilder(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
        int maxX = TUNNEL_WIDTH - 1;
        int maxY = TUNNEL_HEIGHT - 1;
        int maxZ = TUNNEL_LENGTH - 1;
        tunnel.fill(0, 0, 0, maxX, 0, maxZ, FLOOR);
        tunnel.fill(0, maxY, 0, maxX, maxY, maxZ, WALL);
        tunnel.fill(0, 1, 0, 0, maxY - 1, maxZ, WALL);
        tunnel.fill(maxX, 1, 0, maxX, maxY - 1, maxZ, WALL);
        for (int z = 4; z < TUNNEL_LENGTH; z += 6) {
            tunnel.set(TUNNEL_WIDTH / 2, maxY, z, LIGHT);
        }
        return tunnel;
    }

    private static PieceBuilder doorPlug() {
        PieceBuilder plug = new PieceBuilder(DOOR_WIDTH, DOOR_HEIGHT, PLUG_DEPTH);
        plug.fill(0, 0, 0, DOOR_WIDTH - 1, DOOR_HEIGHT - 1, PLUG_DEPTH - 1, WALL);
        return plug;
    }

    /** An empty room: gray floor, white walls and ceiling, ceiling lights, and all four doorways open. */
    private static PieceBuilder roomShell() {
        int top = ROOM_HEIGHT - 1;
        PieceBuilder room = new PieceBuilder(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        room.fill(0, 0, 0, MAX, 0, MAX, FLOOR);
        room.fill(0, top, 0, MAX, top, MAX, WALL);
        room.fill(0, 1, 0, 0, top - 1, MAX, WALL);
        room.fill(MAX, 1, 0, MAX, top - 1, MAX, WALL);
        room.fill(0, 1, 0, MAX, top - 1, 0, WALL);
        room.fill(0, 1, MAX, MAX, top - 1, MAX, WALL);

        for (int x = 6; x < MAX; x += 5) {
            for (int z = 6; z < MAX; z += 5) {
                room.set(x, top, z, LIGHT);
            }
        }

        int doorEnd = ROOM_DOOR_START + DOOR_WIDTH - 1;
        int doorTop = DOOR_BOTTOM_Y + DOOR_HEIGHT - 1;
        room.fill(0, DOOR_BOTTOM_Y, ROOM_DOOR_START, 0, doorTop, doorEnd, Blocks.AIR);
        room.fill(MAX, DOOR_BOTTOM_Y, ROOM_DOOR_START, MAX, doorTop, doorEnd, Blocks.AIR);
        room.fill(ROOM_DOOR_START, DOOR_BOTTOM_Y, 0, doorEnd, doorTop, 0, Blocks.AIR);
        room.fill(ROOM_DOOR_START, DOOR_BOTTOM_Y, MAX, doorEnd, doorTop, MAX, Blocks.AIR);
        return room;
    }
}
