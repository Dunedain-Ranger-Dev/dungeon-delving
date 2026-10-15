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
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
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
 * Each kind of piece has its own material so alignment is easy to check in-game: rooms (walls included) are light
 * gray, tunnels are cyan, and caps are red. Rooms include marker blocks so generated dungeons have
 * chests, mobs, ores and an objective.
 * <p>
 * Output goes to {@code data/dungeondelving/structure/dungeon/}. Every file name ends in {@code _placeholder} so these can
 * be filtered out once real rooms take over.
 */
public class PlaceholderRoomProvider implements DataProvider {
    public static final String FOLDER = "dungeon";

    private static final Block ROOM = Blocks.LIGHT_GRAY_CONCRETE;
    private static final Block TUNNEL = Blocks.CYAN_CONCRETE;
    private static final Block CAP = Blocks.RED_CONCRETE;
    private static Block loot() {
        return ModRegistries.LOOT_MARKER.get();
    }

    private static Block mob() {
        return ModRegistries.MOB_MARKER.get();
    }

    private static Block resource() {
        return ModRegistries.RESOURCE_MARKER.get();
    }

    private static Block objective() {
        return ModRegistries.OBJECTIVE_MARKER.get();
    }
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
        // Kept empty: the exit block goes in the center and players arrive just south of it.
        return roomShell();
    }

    private static PieceBuilder pillarsRoom() {
        PieceBuilder room = roomShell();
        for (int px : new int[] { 8, 24 }) {
            for (int pz : new int[] { 8, 24 }) {
                room.fill(px - 1, 1, pz - 1, px + 1, ROOM_HEIGHT - 2, pz + 1, ROOM);
            }
        }
        room.set(3, 1, 3, loot());
        room.set(MAX - 3, 1, MAX - 3, loot());
        room.set(MID, 1, MID, mob());
        // Ore veins showing on the faces of two pillars.
        room.set(8, 3, 7, resource());
        room.set(24, 3, 25, resource());
        return room;
    }

    private static PieceBuilder stepsRoom() {
        PieceBuilder room = roomShell();
        // A three-step pyramid, each step one block high so players can climb it.
        room.fill(MID - 7, 1, MID - 7, MID + 7, 1, MID + 7, ROOM);
        room.fill(MID - 5, 2, MID - 5, MID + 5, 2, MID + 5, ROOM);
        room.fill(MID - 3, 3, MID - 3, MID + 3, 3, MID + 3, ROOM);
        room.set(MID, 4, MID, loot());
        room.set(4, 1, 4, mob());
        room.set(MAX - 4, 1, MAX - 4, mob());
        return room;
    }

    private static PieceBuilder coverRoom() {
        PieceBuilder room = roomShell();
        // Scattered 3-wide, 2-tall cover walls, kept clear of the doorway lanes.
        int[][] cover = { { 5, 5 }, { 25, 6 }, { 7, 24 }, { 24, 25 }, { 11, 10 }, { 20, 21 } };
        for (int[] c : cover) {
            room.fill(c[0], 1, c[1], c[0] + 2, 2, c[1], ROOM);
        }
        room.set(4, 1, 28, loot());
        room.set(28, 1, 4, loot());
        room.set(MID, 1, MID, mob());
        room.set(6, 2, 5, resource());
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
            room.set(lo + 1, 1, lo + 1, loot());
            room.set(hi - 1, 1, lo + 1, mob());
        }
        return room;
    }

    private static PieceBuilder objectiveRoom() {
        PieceBuilder room = roomShell();
        // A raised altar with the objective marker on top.
        room.fill(MID - 3, 1, MID - 3, MID + 3, 1, MID + 3, ROOM);
        room.fill(MID - 1, 2, MID - 1, MID + 1, 2, MID + 1, ROOM);
        room.set(MID, 3, MID, objective());
        room.set(MID - 6, 1, MID, mob());
        room.set(MID + 6, 1, MID, mob());
        return room;
    }

    /** A room with walls, floor, ceiling, ceiling lights, and a doorway hole in the middle of every side. */
    private static PieceBuilder roomShell() {
        int top = ROOM_HEIGHT - 1;
        PieceBuilder room = new PieceBuilder(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        room.fill(0, 0, 0, MAX, 0, MAX, ROOM);
        room.fill(0, top, 0, MAX, top, MAX, ROOM);
        room.fill(0, 1, 0, MAX, top - 1, 0, ROOM);
        room.fill(0, 1, MAX, MAX, top - 1, MAX, ROOM);
        room.fill(0, 1, 0, 0, top - 1, MAX, ROOM);
        room.fill(MAX, 1, 0, MAX, top - 1, MAX, ROOM);
        for (int x = 6; x < MAX; x += 5) {
            for (int z = 6; z < MAX; z += 5) {
                room.set(x, top, z, LIGHT);
            }
        }
        int doorEnd = DOOR_START + DOOR_WIDTH - 1;
        int doorTop = DOOR_BOTTOM_Y + DOOR_HEIGHT - 1;
        room.fill(DOOR_START, DOOR_BOTTOM_Y, 0, doorEnd, doorTop, 0, Blocks.AIR);
        room.fill(DOOR_START, DOOR_BOTTOM_Y, MAX, doorEnd, doorTop, MAX, Blocks.AIR);
        room.fill(0, DOOR_BOTTOM_Y, DOOR_START, 0, doorTop, doorEnd, Blocks.AIR);
        room.fill(MAX, DOOR_BOTTOM_Y, DOOR_START, MAX, doorTop, doorEnd, Blocks.AIR);
        return room;
    }

    // --- Caps and tunnels ---

    /** A plug that exactly fills one doorway. 5 wide (along x), 6 tall, 1 deep. */
    private static PieceBuilder capWall() {
        PieceBuilder cap = new PieceBuilder(CAP_WIDTH, CAP_HEIGHT, CAP_DEPTH);
        cap.fill(0, 0, 0, CAP_WIDTH - 1, CAP_HEIGHT - 1, CAP_DEPTH - 1, CAP);
        return cap;
    }

    /** A corridor running along z between two rooms' doorways. Both ends are open; the room walls frame them. */
    private static PieceBuilder tunnel() {
        PieceBuilder tunnel = new PieceBuilder(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
        int maxX = TUNNEL_WIDTH - 1;
        int maxY = TUNNEL_HEIGHT - 1;
        int maxZ = TUNNEL_LENGTH - 1;
        tunnel.fill(0, 0, 0, maxX, maxY, maxZ, TUNNEL);
        tunnel.fill(1, 1, 0, maxX - 1, maxY - 1, maxZ, Blocks.AIR);
        for (int z = 4; z < TUNNEL_LENGTH; z += 6) {
            tunnel.set(TUNNEL_WIDTH / 2, maxY, z, LIGHT);
        }
        return tunnel;
    }
}
