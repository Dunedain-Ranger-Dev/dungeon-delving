package io.github.ranger3462.dungeondelving.gate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a Dungeon Gate has around it, like a Guild Ledger's workshop: a Riftstone portal frame and Gate Tuners, within
 * {@link #RADIUS} blocks across and from {@link #BELOW} below to {@link #ABOVE} above the gate.
 */
public record GateArea(BlockPos gate, Optional<Frame> frame, List<BlockPos> tuners, int tunerSlots) {
    public static final int RADIUS = 6;
    public static final int BELOW = 2;
    public static final int ABOVE = 6;
    /** How many tuners a gate uses before upgrades. */
    public static final int BASE_TUNER_SLOTS = 1;
    /** A frame's opening, inside the Riftstone: 2 to 5 wide, 3 to 6 tall. */
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 5;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 6;

    /** A Riftstone frame: the blocks of its opening, and which way it runs. */
    public record Frame(List<BlockPos> opening, Direction.Axis axis, int width, int height) {}

    /** The tuners the gate actually uses: the nearest ones, up to its tuner slots. */
    public List<BlockPos> usedTuners() {
        return tuners.subList(0, Math.min(tunerSlots, tuners.size()));
    }

    public static GateArea scan(Level level, BlockPos gate) {
        Optional<Frame> frame = Optional.empty();
        List<BlockPos> tuners = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(gate.offset(-RADIUS, -BELOW, -RADIUS), gate.offset(RADIUS, ABOVE, RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(DungeonGates.GATE_TUNER.get())) {
                tuners.add(pos.immutable());
            }
            if (frame.isEmpty() && open(state)) {
                frame = frameFrom(level, pos.immutable(), Direction.Axis.X).or(() -> frameFrom(level, pos.immutable(), Direction.Axis.Z));
            }
        }
        tuners.sort(Comparator.comparingDouble(pos -> pos.distSqr(gate)));
        return new GateArea(gate, frame, List.copyOf(tuners), BASE_TUNER_SLOTS);
    }

    private static boolean open(BlockState state) {
        return state.isAir() || state.is(DungeonGates.RIFT_PORTAL.get());
    }

    private static boolean riftstone(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModRegistries.RIFTSTONE.get());
    }

    /**
     * A frame whose opening has its bottom corner at {@code corner} (the end toward negative x or z), running along
     * {@code axis}: Riftstone under, over and at both sides of an open rectangle. The frame's corners can be anything.
     */
    static Optional<Frame> frameFrom(Level level, BlockPos corner, Direction.Axis axis) {
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        if (!riftstone(level, corner.below()) || !riftstone(level, corner.relative(along.getOpposite()))) {
            return Optional.empty();
        }
        int width = 0;
        while (width <= MAX_WIDTH && open(level.getBlockState(corner.relative(along, width))) && riftstone(level, corner.relative(along, width).below())) {
            width++;
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH || !riftstone(level, corner.relative(along, width))) {
            return Optional.empty();
        }
        int height = 1;
        while (height <= MAX_HEIGHT && rowOpen(level, corner.above(height), along, width)) {
            height++;
        }
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
            return Optional.empty();
        }
        for (int i = 0; i < width; i++) {
            if (!riftstone(level, corner.above(height).relative(along, i))) {
                return Optional.empty();
            }
        }
        List<BlockPos> opening = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int i = 0; i < width; i++) {
                opening.add(corner.above(y).relative(along, i));
            }
        }
        return Optional.of(new Frame(List.copyOf(opening), axis, width, height));
    }

    /** Whether a row of the opening is open, with Riftstone at both ends. */
    private static boolean rowOpen(Level level, BlockPos start, Direction along, int width) {
        if (!riftstone(level, start.relative(along.getOpposite())) || !riftstone(level, start.relative(along, width))) {
            return false;
        }
        for (int i = 0; i < width; i++) {
            if (!open(level.getBlockState(start.relative(along, i)))) {
                return false;
            }
        }
        return true;
    }
}
