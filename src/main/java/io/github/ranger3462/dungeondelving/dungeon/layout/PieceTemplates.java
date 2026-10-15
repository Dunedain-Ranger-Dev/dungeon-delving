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
 * piece, marking the opening that must stay clear; being outside the piece, it is never exported.
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
            build.fill(0, 0, 0, CAP_WIDTH - 1, CAP_HEIGHT - 1, CAP_DEPTH - 1, BASE);
        }
    }

    private static void room(Builder build) {
        int max = ROOM_SIZE - 1;
        int top = ROOM_HEIGHT - 1;
        build.fill(0, 0, 0, max, 0, max, BASE);
        build.fill(0, top, 0, max, top, max, BASE);
        build.fill(0, 1, 0, max, top - 1, 0, BASE);
        build.fill(0, 1, max, max, top - 1, max, BASE);
        build.fill(0, 1, 0, 0, top - 1, max, BASE);
        build.fill(max, 1, 0, max, top - 1, max, BASE);
        for (int x = 6; x < max; x += 5) {
            for (int z = 6; z < max; z += 5) {
                build.set(x, top, z, LIGHT);
            }
        }
        // Cut the doorway holes and outline each from just outside the room.
        int doorEnd = DOOR_START + DOOR_WIDTH - 1;
        int doorTop = DOOR_BOTTOM_Y + DOOR_HEIGHT - 1;
        build.fill(DOOR_START, DOOR_BOTTOM_Y, 0, doorEnd, doorTop, 0, Blocks.AIR);
        build.fill(DOOR_START, DOOR_BOTTOM_Y, max, doorEnd, doorTop, max, Blocks.AIR);
        build.fill(0, DOOR_BOTTOM_Y, DOOR_START, 0, doorTop, doorEnd, Blocks.AIR);
        build.fill(max, DOOR_BOTTOM_Y, DOOR_START, max, doorTop, doorEnd, Blocks.AIR);
        build.doorFrameAlongX(-1, DOOR_START);
        build.doorFrameAlongX(ROOM_SIZE, DOOR_START);
        build.doorFrameAlongZ(-1, DOOR_START);
        build.doorFrameAlongZ(ROOM_SIZE, DOOR_START);
    }

    private static void tunnel(Builder build) {
        int maxX = TUNNEL_WIDTH - 1;
        int maxY = TUNNEL_HEIGHT - 1;
        int maxZ = TUNNEL_LENGTH - 1;
        build.fill(0, 0, 0, maxX, maxY, maxZ, BASE);
        build.fill(1, 1, 0, maxX - 1, maxY - 1, maxZ, Blocks.AIR);
        for (int z = 4; z < TUNNEL_LENGTH; z += 6) {
            build.set(TUNNEL_WIDTH / 2, maxY, z, LIGHT);
        }
        build.doorFrameAlongX(-1, TUNNEL_DOOR_START);
        build.doorFrameAlongX(TUNNEL_LENGTH, TUNNEL_DOOR_START);
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
        void doorFrameAlongX(int z, int doorStart) {
            frame(doorStart, (i, y) -> corner.offset(i, y, z));
        }

        /** Red wool rectangle around a doorway on a face running along z, at the given x. Only fills empty space. */
        void doorFrameAlongZ(int x, int doorStart) {
            frame(doorStart, (i, y) -> corner.offset(x, y, i));
        }

        private void frame(int doorStart, FacePos at) {
            int left = doorStart - 1;
            int right = doorStart + DOOR_WIDTH;
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
