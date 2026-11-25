package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Growths on a faction's dungeon walls (DD-109): some rooms in a run come out overgrown, with a few patches of whatever
 * the faction's {@code dungeondelving:overgrowth/<faction>} block tag holds, e.g. Professions and Skills' Barrowmoss
 * in undead dungeons. A growth that clings to faces (like glow lichen) goes on any solid wall, floor or ceiling; any
 * other block sits on a floor. Only tier 2 and deeper dungeons are overgrown, more of their rooms the deeper they are.
 */
public final class Overgrowth {
    /** The chance a room is overgrown, by tier (index 0 is tier 1). */
    public static final double[] ROOM_CHANCE = { 0.0, 0.25, 0.35, 0.45 };
    /** Patches in an overgrown room. */
    public static final int MIN_PATCHES = 2, MAX_PATCHES = 4;
    /** Spots tried per patch before giving up (a room is mostly walls and air). */
    private static final int TRIES_PER_PATCH = 40;

    private Overgrowth() {}

    /** The blocks that grow in a faction's dungeons. */
    public static TagKey<Block> tag(String faction) {
        return TagKey.create(Registries.BLOCK, DungeonDelving.id("overgrowth/" + DungeonFactions.current(faction)));
    }

    /** Maybe overgrows a room just placed in a run of this tier held by this faction. Returns the patches grown. */
    public static int maybeGrow(ServerLevel level, BoundingBox room, RandomSource random, int tier, String faction) {
        if (faction == null || random.nextDouble() >= ROOM_CHANCE[DungeonRun.clampTier(tier) - 1]) {
            return 0;
        }
        return grow(level, room, random, faction, MIN_PATCHES + random.nextInt(MAX_PATCHES - MIN_PATCHES + 1));
    }

    /** Grows up to this many patches of the faction's growths in a room. Returns how many grew. */
    public static int grow(ServerLevel level, BoundingBox room, RandomSource random, String faction, int patches) {
        List<Block> growths = new ArrayList<>();
        level.registryAccess().registryOrThrow(Registries.BLOCK).getTag(tag(faction)).ifPresent(set -> set.stream().map(Holder::value).forEach(growths::add));
        if (growths.isEmpty()) {
            return 0;
        }
        int grown = 0;
        for (int tries = 0; tries < patches * TRIES_PER_PATCH && grown < patches; tries++) {
            BlockPos pos = new BlockPos(room.minX() + random.nextInt(room.getXSpan()), room.minY() + random.nextInt(room.getYSpan()),
                    room.minZ() + random.nextInt(room.getZSpan()));
            BlockState state = growthAt(level, pos, growths.get(random.nextInt(growths.size())), random);
            if (state != null) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                grown++;
            }
        }
        return grown;
    }

    /** The growth's state at an empty spot against a solid face, or null if it can't grow there. */
    private static BlockState growthAt(ServerLevel level, BlockPos pos, Block growth, RandomSource random) {
        if (!level.isEmptyBlock(pos)) {
            return null;
        }
        List<Direction> faces = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos against = pos.relative(direction);
            if (level.getBlockEntity(against) == null && level.getBlockState(against).isFaceSturdy(level, against, direction.getOpposite())) {
                faces.add(direction);
            }
        }
        if (faces.isEmpty()) {
            return null;
        }
        BlockState state;
        if (growth instanceof MultifaceBlock) {
            state = growth.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(faces.get(random.nextInt(faces.size()))), true);
        } else if (faces.contains(Direction.DOWN)) {
            state = growth.defaultBlockState();
        } else {
            return null;
        }
        return state.canSurvive(level, pos) ? state : null;
    }
}
