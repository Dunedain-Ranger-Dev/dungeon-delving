package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.GRID_PITCH;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.ROOM_HEIGHT;
import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.ROOM_SIZE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
 * connection, a cap on every room face with no tunnel, and a bedrock shell around it all. The start room sits
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

    /** Bedrock shell sections are filled at most this many blocks along x and z at a time, to spread the work out. */
    private static final int SHELL_SECTION = 32;

    /**
     * The bedrock shell around a layout: a floor under the whole dungeon, a ceiling over it, and walls around its
     * outer edge, just outside the caps. Pieces stay breakable, but a hole never leads into the void. Returned as
     * small boxes so they can be filled a few per tick.
     */
    public static List<BoundingBox> shell(DungeonLayout layout, BlockPos center) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : layout.rooms().keySet()) {
            BlockPos min = roomMin(center, cell);
            minX = Math.min(minX, min.getX());
            minZ = Math.min(minZ, min.getZ());
            maxX = Math.max(maxX, min.getX() + ROOM_SIZE - 1);
            maxZ = Math.max(maxZ, min.getZ() + ROOM_SIZE - 1);
        }
        // Caps sit one block outside the rooms; the shell goes one block beyond that.
        minX -= 2;
        minZ -= 2;
        maxX += 2;
        maxZ += 2;
        int bottom = center.getY() - 1;
        int top = center.getY() + ROOM_HEIGHT;

        List<BoundingBox> boxes = new ArrayList<>();
        for (int y : new int[] { bottom, top }) {
            for (int x = minX; x <= maxX; x += SHELL_SECTION) {
                for (int z = minZ; z <= maxZ; z += SHELL_SECTION) {
                    boxes.add(new BoundingBox(x, y, z, Math.min(x + SHELL_SECTION - 1, maxX), y, Math.min(z + SHELL_SECTION - 1, maxZ)));
                }
            }
        }
        for (int x = minX; x <= maxX; x += SHELL_SECTION) {
            int x2 = Math.min(x + SHELL_SECTION - 1, maxX);
            boxes.add(new BoundingBox(x, bottom, minZ, x2, top, minZ));
            boxes.add(new BoundingBox(x, bottom, maxZ, x2, top, maxZ));
        }
        for (int z = minZ; z <= maxZ; z += SHELL_SECTION) {
            int z2 = Math.min(z + SHELL_SECTION - 1, maxZ);
            boxes.add(new BoundingBox(minX, bottom, z, minX, top, z2));
            boxes.add(new BoundingBox(maxX, bottom, z, maxX, top, z2));
        }
        return boxes;
    }

    /** Fills one shell section with bedrock. */
    public static void fillShell(ServerLevel level, BoundingBox box) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, bedrock, Block.UPDATE_CLIENTS);
        }
    }

    /** Places one piece so that, after turning, it exactly fills the space starting at {@code targetMin}. */
    public static void place(ServerLevel level, Placement placement) {
        StructureTemplate template = level.getStructureManager().get(placement.template())
                .orElseThrow(() -> new IllegalStateException("Missing dungeon piece " + placement.template()));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(placement.rotation()).setMirror(placement.mirror());
        BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
        BlockPos pos = placement.targetMin().offset(-box.minX(), -box.minY(), -box.minZ());
        template.placeInWorld(level, pos, pos, settings, level.getRandom(), Block.UPDATE_CLIENTS);
    }
}
