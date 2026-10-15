package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.CORRIDOR_HEIGHT;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.CORRIDOR_START;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.CORRIDOR_WIDTH;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.GRID_PITCH;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.ROOM_HEIGHT;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.ROOM_SIZE;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.TUNNEL_LENGTH;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
 * connection, a cap on every room face with no tunnel, and a bedrock skin behind every outer surface. The start room sits
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
                case OBJECTIVE -> Kind.OBJECTIVE;
            };
            placements.add(new Placement(pieces.pick(kind, random), roomMin(center, room.cell()), room.rotation(), room.mirror()));
        }

        for (Connection connection : layout.connections().stream()
                .sorted(Comparator.comparingInt((Connection c) -> c.from().x()).thenComparingInt(c -> c.from().z())
                        .thenComparing(Connection::alongX))
                .toList()) {
            BlockPos fromMin = roomMin(center, connection.from());
            // Tunnel pieces run along z; turn them a quarter for east-west tunnels.
            if (connection.alongX()) {
                placements.add(new Placement(pieces.pick(Kind.TUNNEL, random), fromMin.offset(ROOM_SIZE, 0, 0), Rotation.CLOCKWISE_90, Mirror.NONE));
            } else {
                placements.add(new Placement(pieces.pick(Kind.TUNNEL, random), fromMin.offset(0, 0, ROOM_SIZE), Rotation.NONE, Mirror.NONE));
            }
        }

        for (Room room : rooms) {
            BlockPos min = roomMin(center, room.cell());
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                if (layout.connected(room.cell(), direction)) {
                    continue;
                }
                // Caps run along x; turn them a quarter for west and east faces.
                Placement cap = switch (direction) {
                    case NORTH -> new Placement(pieces.pick(Kind.CAP, random), min.offset(0, 0, -1), Rotation.NONE, Mirror.NONE);
                    case SOUTH -> new Placement(pieces.pick(Kind.CAP, random), min.offset(0, 0, ROOM_SIZE), Rotation.NONE, Mirror.NONE);
                    case WEST -> new Placement(pieces.pick(Kind.CAP, random), min.offset(-1, 0, 0), Rotation.CLOCKWISE_90, Mirror.NONE);
                    default -> new Placement(pieces.pick(Kind.CAP, random), min.offset(ROOM_SIZE, 0, 0), Rotation.CLOCKWISE_90, Mirror.NONE);
                };
                placements.add(cap);
            }
        }
        return placements;
    }

    /**
     * The bedrock skin around a layout: one layer directly behind every outer surface (under room floors, over room
     * ceilings, behind caps, around tunnel corridors, and behind tunnel end walls). Each layer reaches one block past
     * its edges, so the open corners between walls are sealed too. Pieces stay breakable, but breaking through one
     * always leads to bedrock, never into the void. Returned as small boxes so they can be filled a few per tick.
     */
    public static List<BoundingBox> shell(DungeonLayout layout, BlockPos center) {
        List<BoundingBox> boxes = new ArrayList<>();
        int below = -1;
        int above = ROOM_HEIGHT;
        int edge = ROOM_SIZE; // One block past a room's far side.

        for (Room room : layout.rooms().values()) {
            BlockPos min = roomMin(center, room.cell());
            boxes.add(box(min, -1, below, -1, edge, below, edge));
            boxes.add(box(min, -1, above, -1, edge, above, edge));
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                if (layout.connected(room.cell(), direction)) {
                    continue;
                }
                // Directly behind the cap, which sits one block outside the room.
                boxes.add(switch (direction) {
                    case NORTH -> box(min, -1, below, -2, edge, above, -2);
                    case SOUTH -> box(min, -1, below, edge + 1, edge, above, edge + 1);
                    case WEST -> box(min, -2, below, -1, -2, above, edge);
                    default -> box(min, edge + 1, below, -1, edge + 1, above, edge);
                });
            }
        }

        for (Connection connection : layout.connections()) {
            BlockPos fromMin = roomMin(center, connection.from());
            // Work in the tunnel's own frame (corridor along z), then turn it to match the tunnel's direction.
            BlockPos tunnelMin = connection.alongX() ? fromMin.offset(ROOM_SIZE, 0, 0) : fromMin.offset(0, 0, ROOM_SIZE);
            boolean turn = connection.alongX();
            int x1 = CORRIDOR_START - 1;
            int x2 = CORRIDOR_START + CORRIDOR_WIDTH;
            int z1 = 1;
            int z2 = TUNNEL_LENGTH - 2;
            int top = CORRIDOR_HEIGHT;
            // Around the corridor: under, over, and both sides.
            boxes.add(tunnelBox(tunnelMin, turn, x1, below, z1, x2, below, z2));
            boxes.add(tunnelBox(tunnelMin, turn, x1, top, z1, x2, top, z2));
            boxes.add(tunnelBox(tunnelMin, turn, x1, below, z1, x1, top, z2));
            boxes.add(tunnelBox(tunnelMin, turn, x2, below, z1, x2, top, z2));
            // Behind each end wall, wherever the corridor isn't: beside it, and above it.
            for (int z : new int[] { z1, z2 }) {
                boxes.add(tunnelBox(tunnelMin, turn, -1, below, z, x1, above, z));
                boxes.add(tunnelBox(tunnelMin, turn, x2, below, z, edge, above, z));
                boxes.add(tunnelBox(tunnelMin, turn, x1, top, z, x2, above, z));
            }
        }
        return boxes;
    }

    private static BoundingBox box(BlockPos origin, int x1, int y1, int z1, int x2, int y2, int z2) {
        return BoundingBox.fromCorners(origin.offset(x1, y1, z1), origin.offset(x2, y2, z2));
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
    public static void place(ServerLevel level, Placement placement) {
        place(level, placement, true);
    }

    /** Places one piece, optionally leaving its marker blocks as they are (useful for checking a piece's shape). */
    public static void place(ServerLevel level, Placement placement, boolean resolveMarkers) {
        StructureTemplate template = level.getStructureManager().get(placement.template())
                .orElseThrow(() -> new IllegalStateException("Missing dungeon piece " + placement.template()));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(placement.rotation()).setMirror(placement.mirror());
        BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
        BlockPos pos = placement.targetMin().offset(-box.minX(), -box.minY(), -box.minZ());
        template.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (resolveMarkers) {
            DungeonMarkers.resolve(level, template, pos, settings, level.getRandom(), DungeonMarkers.Rolls.fromConfig());
        }
    }
}
