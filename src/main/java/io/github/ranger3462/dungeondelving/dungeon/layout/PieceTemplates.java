package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Builds a starting frame for a builder to work from: the parts of a piece the spec requires, in plain light gray
 * concrete to be replaced, with glowstone so the space is lit. Red wool outlines each doorway one block outside the
 * piece, marking space that must stay clear; being outside the piece, it is never exported.
 */
public final class PieceTemplates {
    private static final Block BASE = Blocks.LIGHT_GRAY_CONCRETE;
    private static final Block LIGHT = Blocks.GLOWSTONE;
    private static final Block KEEP_CLEAR = Blocks.RED_WOOL;

    private PieceTemplates() {}

    /** The first block that isn't air inside the piece's space, if any. Templates are only built on empty ground. */
    public static Optional<BlockPos> firstBlockInTheWay(Kind kind, ServerLevel level, BlockPos corner) {
        Vec3i size = kind.size();
        for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(size).offset(-1, -1, -1))) {
            if (!level.getBlockState(pos).isAir()) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    public static void build(Kind kind, ServerLevel level, BlockPos corner) {
        Builder build = new Builder(level, corner);
        if (kind.isRoom()) {
            room(build);
        } else if (kind == Kind.TUNNEL) {
            tunnel(build);
        } else {
            build.fill(0, 0, 0, WALL_LENGTH - 1, WALL_HEIGHT - 1, WALL_THICKNESS - 1, BASE);
        }
    }

    private static void room(Builder build) {
        int max = ROOM_SIZE - 1;
        int top = ROOM_HEIGHT - 1;
        build.fill(0, 0, 0, max, 0, max, BASE);
        build.fill(0, top, 0, max, top, max, BASE);
        for (int x = 6; x < max; x += 5) {
            for (int z = 6; z < max; z += 5) {
                build.set(x, top, z, LIGHT);
            }
        }
        // Outline each side's doorway spot from just outside the room.
        build.doorFrameAlongX(-1);
        build.doorFrameAlongX(ROOM_SIZE);
        build.doorFrameAlongZ(-1);
        build.doorFrameAlongZ(ROOM_SIZE);
    }

    private static void tunnel(Builder build) {
        int lastZ = TUNNEL_LENGTH - 1;
        int x1 = CORRIDOR_START;
        int x2 = CORRIDOR_START + CORRIDOR_WIDTH - 1;
        int top = CORRIDOR_HEIGHT - 1;
        for (int z : new int[] { 0, lastZ }) {
            build.fill(0, 0, z, WALL_LENGTH - 1, WALL_HEIGHT - 1, z, BASE);
            build.fill(DOOR_START, DOOR_BOTTOM_Y, z, DOOR_START + DOOR_WIDTH - 1, DOOR_BOTTOM_Y + DOOR_HEIGHT - 1, z, Blocks.AIR);
        }
        build.fill(x1, 0, 1, x2, top, lastZ - 1, BASE);
        build.fill(x1 + 1, 1, 1, x2 - 1, top - 1, lastZ - 1, Blocks.AIR);
        for (int z = 4; z < lastZ; z += 6) {
            build.set(TUNNEL_WIDTH / 2, top, z, LIGHT);
        }
        build.doorFrameAlongX(-1);
        build.doorFrameAlongX(TUNNEL_LENGTH);
    }

    private record Builder(ServerLevel level, BlockPos corner) {
        void set(int x, int y, int z, Block block) {
            level.setBlock(corner.offset(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            for (BlockPos pos : BlockPos.betweenClosed(corner.offset(x1, y1, z1), corner.offset(x2, y2, z2))) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        /** Red wool rectangle around a doorway on a face running along x, at the given z. Only fills empty space. */
        void doorFrameAlongX(int z) {
            frame((i, y) -> corner.offset(i, y, z));
        }

        /** Red wool rectangle around a doorway on a face running along z, at the given x. Only fills empty space. */
        void doorFrameAlongZ(int x) {
            frame((i, y) -> corner.offset(x, y, i));
        }

        private void frame(FacePos at) {
            int left = DOOR_START - 1;
            int right = DOOR_START + DOOR_WIDTH;
            int bottom = DOOR_BOTTOM_Y - 1;
            int top = DOOR_BOTTOM_Y + DOOR_HEIGHT;
            for (int i = left; i <= right; i++) {
                for (int y = bottom; y <= top; y++) {
                    boolean edge = i == left || i == right || y == bottom || y == top;
                    BlockPos pos = at.get(i, y);
                    if (edge && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, KEEP_CLEAR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private interface FacePos {
        BlockPos get(int along, int y);
    }
}
