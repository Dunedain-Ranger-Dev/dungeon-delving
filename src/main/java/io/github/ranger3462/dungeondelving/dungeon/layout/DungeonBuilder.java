package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Cell;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Connection;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Room;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Turns a {@link DungeonLayout} into concrete piece placements in the world: a room per cell, a tunnel per
 * connection, a cap plugging every doorway with no tunnel, and a bedrock skin behind every outer surface. The start room sits
 * centered on {@code center}.
 */
public final class DungeonBuilder {
    private DungeonBuilder() {}

    /** One structure to place, positioned by the minimum corner of the space it should occupy once turned. */
    public record Placement(ResourceLocation template, BlockPos targetMin, Rotation rotation, Mirror mirror) {}

    /** The minimum corner of a cell's room. */
    public static BlockPos roomMin(BlockPos center, Cell cell) {
        return center.offset(cell.x() * GRID_PITCH - ROOM_SIZE / 2, 0, cell.z() * GRID_PITCH - ROOM_SIZE / 2);
    }

    /** Every placement for a layout, start room first, then the other rooms nearest-first, then tunnels and caps. */
    public static List<Placement> plan(DungeonLayout layout, DungeonPieces pieces, BlockPos center, RandomSource random) {
        List<Placement> placements = new ArrayList<>();
        Map<Cell, Integer> distances = layout.distancesFromStart();
        List<Room> rooms = layout.rooms().values().stream()
                .sorted(Comparator.comparingInt((Room room) -> distances.getOrDefault(room.cell(), Integer.MAX_VALUE))
                        .thenComparingInt(room -> room.cell().x())
                        .thenComparingInt(room -> room.cell().z()))
                .toList();

        for (Room room : rooms) {
            Kind kind = switch (room.kind()) {
                case START -> Kind.START;
                case COMMON -> Kind.COMMON;
                case CHALLENGE -> Kind.CHALLENGE;
                case BOSS -> Kind.BOSS;
            };
            placements.add(new Placement(pieces.pick(kind, random), roomMin(center, room.cell()), room.rotation(), room.mirror()));
        }

        for (Connection connection : layout.connections().stream()
                .sorted(Comparator.comparingInt((Connection c) -> c.from().x()).thenComparingInt(c -> c.from().z())
                        .thenComparing(Connection::alongX))
                .toList()) {
            BlockPos fromMin = roomMin(center, connection.from());
            // Tunnel pieces run along z; turn them a quarter for east-west tunnels. Either way the corridor starts
            // just outside the room and is centered on its doorway.
            if (connection.alongX()) {
                placements.add(new Placement(pieces.pick(Kind.TUNNEL, random), fromMin.offset(ROOM_SIZE, 0, TUNNEL_START), Rotation.CLOCKWISE_90, Mirror.NONE));
            } else {
                placements.add(new Placement(pieces.pick(Kind.TUNNEL, random), fromMin.offset(TUNNEL_START, 0, ROOM_SIZE), Rotation.NONE, Mirror.NONE));
            }
        }

        int max = ROOM_SIZE - 1;
        for (Room room : rooms) {
            BlockPos min = roomMin(center, room.cell());
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                if (layout.connected(room.cell(), direction)) {
                    continue;
                }
                // Caps plug the doorway hole in the room's wall. They run along x; turn them a quarter for west and east.
                Placement cap = switch (direction) {
                    case NORTH -> new Placement(pieces.pick(Kind.CAP, random), min.offset(DOOR_START, DOOR_BOTTOM_Y, 0), Rotation.NONE, Mirror.NONE);
                    case SOUTH -> new Placement(pieces.pick(Kind.CAP, random), min.offset(DOOR_START, DOOR_BOTTOM_Y, max), Rotation.NONE, Mirror.NONE);
                    case WEST -> new Placement(pieces.pick(Kind.CAP, random), min.offset(0, DOOR_BOTTOM_Y, DOOR_START), Rotation.CLOCKWISE_90, Mirror.NONE);
                    default -> new Placement(pieces.pick(Kind.CAP, random), min.offset(max, DOOR_BOTTOM_Y, DOOR_START), Rotation.CLOCKWISE_90, Mirror.NONE);
                };
                placements.add(cap);
            }
        }
        return placements;
    }

    /**
     * The blocks of the boss door: every doorway of the boss room that a tunnel leads into, filled so the room can't be
     * entered until the door opens. Empty if the layout has no boss room.
     */
    public static List<BlockPos> bossDoor(DungeonLayout layout, BlockPos center) {
        List<BlockPos> blocks = new ArrayList<>();
        int max = ROOM_SIZE - 1;
        layout.rooms().values().stream().filter(room -> room.kind() == DungeonLayout.RoomKind.BOSS).forEach(room -> {
            BlockPos min = roomMin(center, room.cell());
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                if (!layout.connected(room.cell(), direction)) {
                    continue;
                }
                for (int across = DOOR_START; across < DOOR_START + DOOR_WIDTH; across++) {
                    for (int y = DOOR_BOTTOM_Y; y < DOOR_BOTTOM_Y + DOOR_HEIGHT; y++) {
                        blocks.add(switch (direction) {
                            case NORTH -> min.offset(across, y, 0);
                            case SOUTH -> min.offset(across, y, max);
                            case WEST -> min.offset(0, y, across);
                            default -> min.offset(max, y, across);
                        });
                    }
                }
            }
        });
        return blocks;
    }

    /** The middle of the boss room's floor, where the boss appears if the room has no boss marker. */
    public static Optional<BlockPos> bossRoomCenter(DungeonLayout layout, BlockPos center) {
        return layout.rooms().values().stream().filter(room -> room.kind() == DungeonLayout.RoomKind.BOSS).findFirst()
                .map(room -> roomMin(center, room.cell()).offset(ROOM_SIZE / 2, 1, ROOM_SIZE / 2));
    }

    /**
     * The bedrock skin around a layout: one layer directly behind every outer surface: under room floors, over room
     * ceilings, outside room walls (leaving a gap where a tunnel attaches), and around tunnel corridors. Each layer
     * reaches one block past its edges, so corners are sealed too. Pieces stay breakable, but breaking through one
     * always leads to bedrock, never into the void. Returned as small boxes so they can be filled a few per tick.
     */
    public static List<BoundingBox> shell(DungeonLayout layout, BlockPos center) {
        List<BoundingBox> boxes = new ArrayList<>();
        int below = -1;
        int above = ROOM_HEIGHT;
        int edge = ROOM_SIZE; // One block past a room's far side.
        int tunnelEnd = TUNNEL_START + TUNNEL_WIDTH - 1;
        int tunnelTop = TUNNEL_HEIGHT - 1;

        for (Room room : layout.rooms().values()) {
            BlockPos min = roomMin(center, room.cell());
            boxes.add(box(min, -1, below, -1, edge, below, edge));
            boxes.add(box(min, -1, above, -1, edge, above, edge));
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                // Just outside the wall, in the room's own frame: "across" runs along the wall, "out" is the fixed plane.
                int out = direction == Direction.NORTH || direction == Direction.WEST ? -1 : edge;
                boolean alongX = direction == Direction.NORTH || direction == Direction.SOUTH;
                if (!layout.connected(room.cell(), direction)) {
                    boxes.add(sideBox(min, alongX, out, -1, below, edge, above));
                    continue;
                }
                // Leave the tunnel's cross-section open; seal everything around it.
                boxes.add(sideBox(min, alongX, out, -1, below, TUNNEL_START - 1, above));
                boxes.add(sideBox(min, alongX, out, tunnelEnd + 1, below, edge, above));
                boxes.add(sideBox(min, alongX, out, TUNNEL_START, tunnelTop + 1, tunnelEnd, above));
                boxes.add(sideBox(min, alongX, out, TUNNEL_START, below, tunnelEnd, below));
            }
        }

        for (Connection connection : layout.connections()) {
            BlockPos fromMin = roomMin(center, connection.from());
            // Work in the tunnel's own frame (corridor along z), then turn it to match the tunnel's direction.
            boolean turn = connection.alongX();
            BlockPos tunnelMin = turn ? fromMin.offset(ROOM_SIZE, 0, TUNNEL_START) : fromMin.offset(TUNNEL_START, 0, ROOM_SIZE);
            int x1 = -1;
            int x2 = TUNNEL_WIDTH;
            int lastZ = TUNNEL_LENGTH - 1;
            boxes.add(tunnelBox(tunnelMin, turn, x1, below, 0, x2, below, lastZ));
            boxes.add(tunnelBox(tunnelMin, turn, x1, TUNNEL_HEIGHT, 0, x2, TUNNEL_HEIGHT, lastZ));
            boxes.add(tunnelBox(tunnelMin, turn, x1, below, 0, x1, TUNNEL_HEIGHT, lastZ));
            boxes.add(tunnelBox(tunnelMin, turn, x2, below, 0, x2, TUNNEL_HEIGHT, lastZ));
        }
        return boxes;
    }

    private static BoundingBox box(BlockPos origin, int x1, int y1, int z1, int x2, int y2, int z2) {
        return BoundingBox.fromCorners(origin.offset(x1, y1, z1), origin.offset(x2, y2, z2));
    }

    /** A box in the plane just outside one room wall, from {@code a1} to {@code a2} along the wall. */
    private static BoundingBox sideBox(BlockPos roomMin, boolean alongX, int out, int a1, int y1, int a2, int y2) {
        return alongX ? box(roomMin, a1, y1, out, a2, y2, out) : box(roomMin, out, y1, a1, out, y2, a2);
    }

    /** A box given in a tunnel's own frame (x across, z along the corridor), placed in the world. */
    private static BoundingBox tunnelBox(BlockPos tunnelMin, boolean turn, int x1, int y1, int z1, int x2, int y2, int z2) {
        return turn ? box(tunnelMin, z1, y1, x1, z2, y2, x2) : box(tunnelMin, x1, y1, z1, x2, y2, z2);
    }

    /** Fills one shell section with bedrock. */
    public static void fillShell(ServerLevel level, BoundingBox box) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, bedrock, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Places one piece so that, after turning, it exactly fills the space starting at {@code targetMin}, then turns
     * its marker blocks into chests, mobs and ores.
     */
    public static void place(ServerLevel level, Placement placement, DungeonMarkers.Rolls rolls) {
        place(level, placement, rolls, true);
    }

    /** Places a piece as part of a tier 1 dungeon. */
    public static void place(ServerLevel level, Placement placement) {
        place(level, placement, DungeonMarkers.Rolls.fromConfig(1));
    }

    /** Places one piece, optionally leaving its marker blocks as they are (useful for checking a piece's shape). */
    public static void place(ServerLevel level, Placement placement, boolean resolveMarkers) {
        place(level, placement, DungeonMarkers.Rolls.fromConfig(1), resolveMarkers);
    }

    private static void place(ServerLevel level, Placement placement, DungeonMarkers.Rolls rolls, boolean resolveMarkers) {
        StructureTemplate template = level.getStructureManager().get(placement.template())
                .orElseThrow(() -> new IllegalStateException("Missing dungeon piece " + placement.template()));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(placement.rotation()).setMirror(placement.mirror());
        BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
        BlockPos pos = placement.targetMin().offset(-box.minX(), -box.minY(), -box.minZ());
        template.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (resolveMarkers) {
            DungeonMarkers.resolve(level, template, pos, settings, level.getRandom(), rolls, placement.targetMin());
        }
    }
}
