package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.GRID_PITCH;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Turns a {@link DungeonLayout} into concrete piece placements in the world: a room per cell, a tunnel per
 * connection, and a cap on every room face with no tunnel. The start room sits centered on {@code center}.
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
