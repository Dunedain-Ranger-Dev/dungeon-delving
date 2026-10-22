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
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Generates simple test pieces that follow {@link io.github.ranger3462.dungeondelving.dungeon.RoomSpec}, so dungeon
 * generation can be built and tested before hand-built rooms exist.
 * <p>
 * Each kind of piece has its own material so alignment is easy to check in-game: rooms (walls included) are light
 * gray, tunnels are cyan, and caps are red. Rooms include marker blocks so generated dungeons have
 * chests, mobs, ores, hunt targets and a boss.
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

    private static Block hunt() {
        return ModRegistries.HUNT_MARKER.get();
    }

    private static Block boss() {
        return ModRegistries.BOSS_MARKER.get();
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
        pieces.put("quarry_placeholder", quarryRoom());
        pieces.put("treasure_placeholder", treasureRoom());
        pieces.put("challenge_hunt_placeholder", huntRoom());
        pieces.put("challenge_puzzle_placeholder", puzzleRoom());
        pieces.put("challenge_relics_placeholder", relicRoom());
        pieces.put("boss_placeholder", bossRoom());
        pieces.put("boss_goblin_chief_placeholder", goblinChiefArena());
        pieces.put("boss_skeleton_lord_placeholder", skeletonLordHall());
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

    /** Ore mounds per dungeon ore in the start room. */
    static final int START_MOUNDS_PER_ORE = 3;

    /**
     * The center is kept clear: the exit block goes there and players arrive just south of it. For testing, the corners
     * hold small mounds of every dungeon ore (real ore blocks, not markers), so they're quick to find.
     */
    private static PieceBuilder startRoom() {
        PieceBuilder room = roomShell();
        // Four mound spots in each corner, clear of the doorway lanes.
        int[] spots = { 3, 7, MAX - 7, MAX - 3 };
        DungeonOre[] ores = DungeonOre.values();
        int mound = 0;
        for (int x : spots) {
            for (int z : spots) {
                if (mound < ores.length * START_MOUNDS_PER_ORE) {
                    oreMound(room, x, z, ores[mound % ores.length].ore.get());
                }
                mound++;
            }
        }
        return room;
    }

    /** A 3×3 mound of ore with one more block on top, centered on (x, z). */
    private static void oreMound(PieceBuilder room, int x, int z, Block ore) {
        room.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, ore);
        room.set(x, 2, z, ore);
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

    /**
     * A test room packed with resource markers: four thick pillars studded with them on every face, so a single room
     * shows what a dungeon tier's ores look like.
     */
    private static PieceBuilder quarryRoom() {
        PieceBuilder room = roomShell();
        for (int px : new int[] { 8, 24 }) {
            for (int pz : new int[] { 8, 24 }) {
                room.fill(px - 2, 1, pz - 2, px + 2, ROOM_HEIGHT - 2, pz + 2, ROOM);
                for (int y = 2; y <= 8; y += 2) {
                    for (int d = -2; d <= 2; d += 2) {
                        room.set(px + d, y, pz - 2, resource());
                        room.set(px + d, y, pz + 2, resource());
                        room.set(px - 2, y, pz + d, resource());
                        room.set(px + 2, y, pz + d, resource());
                    }
                }
            }
        }
        return room;
    }

    /**
     * A treasure room: a vault of chests on low plinths around the walls, with two reward chests on a dais in the
     * middle, guarded by two spawners.
     */
    private static PieceBuilder treasureRoom() {
        PieceBuilder room = roomShell();
        for (int[] spot : new int[][] { { 5, 5 }, { MAX - 5, 5 }, { 5, MAX - 5 }, { MAX - 5, MAX - 5 }, { 5, MID - 6 }, { MAX - 5, MID + 6 } }) {
            room.set(spot[0], 1, spot[1], ROOM);
            room.set(spot[0], 2, spot[1], loot());
        }
        room.fill(MID - 2, 1, MID - 2, MID + 2, 1, MID + 2, ROOM);
        room.set(MID - 1, 2, MID, objective());
        room.set(MID + 1, 2, MID, objective());
        room.set(MID, 1, 8, mob());
        room.set(MID, 1, MAX - 8, mob());
        return room;
    }

    /**
     * A hunt challenge room: three hunt targets among the cover, plus two spawners for ordinary mobs. Killing all three
     * targets drops a Dungeon Key.
     */
    private static PieceBuilder huntRoom() {
        PieceBuilder room = roomShell();
        int[][] cover = { { 6, 6 }, { 24, 6 }, { 6, 24 }, { 24, 24 } };
        for (int[] c : cover) {
            room.fill(c[0], 1, c[1], c[0] + 2, 3, c[1] + 2, ROOM);
        }
        room.set(9, 1, MID, hunt());
        room.set(MAX - 9, 1, MID, hunt());
        room.set(MID, 1, MAX - 9, hunt());
        room.set(MID, 1, 9, mob());
        room.set(MID, 1, MID, mob());
        return room;
    }

    /** How high the puzzle room's Puzzle Lock sits. */
    static final int PUZZLE_LOCK_Y = 8;

    /**
     * A puzzle challenge room, standing in for a builder's redstone puzzle: a Puzzle Lock on top of a pillar, with a
     * lever on it, reached by a spiral of stepping blocks. Flipping the lever solves the room. Two spawners keep it busy.
     */
    private static PieceBuilder puzzleRoom() {
        PieceBuilder room = roomShell();
        room.fill(MID, 1, MID, MID, PUZZLE_LOCK_Y - 1, MID, ROOM);
        room.set(MID, PUZZLE_LOCK_Y, MID, ModRegistries.PUZZLE_LOCK.get());
        room.set(MID, PUZZLE_LOCK_Y + 1, MID, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        int[][] steps = { { -2, -2 }, { 0, -2 }, { 2, -2 }, { 2, 0 }, { 2, 2 }, { 0, 2 }, { -2, 2 } };
        for (int i = 0; i < steps.length; i++) {
            room.set(MID + steps[i][0], 1 + i, MID + steps[i][1], ROOM);
        }
        room.set(8, 1, 8, mob());
        room.set(MAX - 8, 1, MAX - 8, mob());
        return room;
    }

    /**
     * A collection challenge room: a Relic Altar on a dais in the middle, and six spawners around it. Monsters killed
     * here drop the Relic Shards the altar wants.
     */
    private static PieceBuilder relicRoom() {
        PieceBuilder room = roomShell();
        room.fill(MID - 1, 1, MID - 1, MID + 1, 1, MID + 1, ROOM);
        room.set(MID, 2, MID, ModRegistries.RELIC_ALTAR.get());
        for (int[] spot : new int[][] { { 7, 7 }, { MAX - 7, 7 }, { 7, MAX - 7 }, { MAX - 7, MAX - 7 }, { MID - 8, MID }, { MID + 8, MID } }) {
            room.set(spot[0], 1, spot[1], mob());
        }
        return room;
    }

    /**
     * The Goblin Shaman Chief's arena: a raised dais in the middle for the chief, ringed by eight spawners that pour out
     * goblins once he appears, and low walls to duck behind.
     */
    private static PieceBuilder goblinChiefArena() {
        PieceBuilder room = roomShell();
        room.fill(MID - 2, 1, MID - 2, MID + 2, 2, MID + 2, ROOM);
        room.set(MID, 3, MID, boss());
        for (int[] spot : new int[][] { { 6, 6 }, { MID, 4 }, { MAX - 6, 6 }, { 4, MID - 6 }, { MAX - 4, MID + 6 }, { 6, MAX - 6 }, { MID, MAX - 4 },
                { MAX - 6, MAX - 6 } }) {
            room.set(spot[0], 1, spot[1], mob());
        }
        for (int[] wall : new int[][] { { 9, 11 }, { MAX - 11, 11 }, { 9, MAX - 11 }, { MAX - 11, MAX - 11 } }) {
            room.fill(wall[0], 1, wall[1], wall[0] + 2, 2, wall[1], ROOM);
        }
        return room;
    }

    /** The Skeleton Lord's hall: two rows of pillars, and a raised throne platform where he and his guard wait. */
    private static PieceBuilder skeletonLordHall() {
        PieceBuilder room = roomShell();
        for (int z = 6; z <= MAX - 6; z += 6) {
            room.fill(9, 1, z, 9, ROOM_HEIGHT - 2, z, ROOM);
            room.fill(MAX - 9, 1, z, MAX - 9, ROOM_HEIGHT - 2, z, ROOM);
        }
        room.fill(MID - 5, 1, MID - 5, MID + 5, 1, MID + 5, ROOM);
        room.set(MID, 2, MID, boss());
        return room;
    }

    /** The boss arena: open floor with a raised dais in the middle, where the boss appears once the door opens. */
    private static PieceBuilder bossRoom() {
        PieceBuilder room = roomShell();
        room.fill(MID - 3, 1, MID - 3, MID + 3, 1, MID + 3, ROOM);
        room.set(MID, 2, MID, boss());
        for (int px : new int[] { 7, MAX - 7 }) {
            for (int pz : new int[] { 7, MAX - 7 }) {
                room.fill(px, 1, pz, px, ROOM_HEIGHT - 2, pz, ROOM);
            }
        }
        return room;
    }

    /**
     * A room with walls, floor, ceiling and a doorway hole in the middle of every side, lit brightly for testing:
     * glowstone in the ceiling, set into the walls, and in the floor (13 blocks is too tall to light from above alone).
     */
    private static PieceBuilder roomShell() {
        int top = ROOM_HEIGHT - 1;
        PieceBuilder room = new PieceBuilder(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        room.fill(0, 0, 0, MAX, 0, MAX, ROOM);
        room.fill(0, top, 0, MAX, top, MAX, ROOM);
        room.fill(0, 1, 0, MAX, top - 1, 0, ROOM);
        room.fill(0, 1, MAX, MAX, top - 1, MAX, ROOM);
        room.fill(0, 1, 0, 0, top - 1, MAX, ROOM);
        room.fill(MAX, 1, 0, MAX, top - 1, MAX, ROOM);
        for (int x = 4; x < MAX; x += 4) {
            for (int z = 4; z < MAX; z += 4) {
                room.set(x, top, z, LIGHT);
            }
        }
        for (int x = 4; x < MAX; x += 6) {
            for (int z = 4; z < MAX; z += 6) {
                room.set(x, 0, z, LIGHT);
            }
        }
        // Wall lights, kept clear of the doorway frames (blocks 13-19 up to y=7).
        for (int i : new int[] { 4, 8, 24, 28 }) {
            for (int y : new int[] { 4, 9 }) {
                room.set(i, y, 0, LIGHT);
                room.set(i, y, MAX, LIGHT);
                room.set(0, y, i, LIGHT);
                room.set(MAX, y, i, LIGHT);
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
        for (int z = 2; z < TUNNEL_LENGTH; z += 3) {
            tunnel.set(TUNNEL_WIDTH / 2, maxY, z, LIGHT);
        }
        for (int z = 5; z < TUNNEL_LENGTH; z += 6) {
            tunnel.set(0, 3, z, LIGHT);
            tunnel.set(maxX, 3, z, LIGHT);
        }
        return tunnel;
    }
}
