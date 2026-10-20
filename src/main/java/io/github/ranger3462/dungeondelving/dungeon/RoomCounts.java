package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import net.minecraft.util.RandomSource;

/**
 * How many rooms a dungeon of each tier has: a range per tier from the {@code roomCounts} config, rolled fresh each
 * run. Every dungeon has at least {@link #MIN_ROOMS}: the start room and the boss room.
 */
public final class RoomCounts {
    /** The start room plus the boss room. */
    public static final int MIN_ROOMS = 2;
    /** The default ranges for tiers 1 to 4. */
    public static final List<String> DEFAULTS = List.of("4-6", "7-10", "11-15", "16-21");

    /** An inclusive range of room counts. */
    public record Range(int min, int max) {
        public int roll(RandomSource random) {
            return min + random.nextInt(max - min + 1);
        }
    }

    private RoomCounts() {}

    /** Rolls a room count for a tier from the config. */
    public static int roll(int tier, RandomSource random) {
        return forTier(Config.ROOM_COUNTS.get(), tier).roll(random);
    }

    /** The range for a tier (1 to 4) from a list of ranges, falling back to the default if the entry is missing or invalid. */
    public static Range forTier(List<? extends String> ranges, int tier) {
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        Optional<Range> configured = index < ranges.size() ? parse(ranges.get(index)) : Optional.empty();
        return configured.orElseGet(() -> parse(DEFAULTS.get(index)).orElseThrow());
    }

    /**
     * Reads a range like {@code "3-5"}, or a single number like {@code "4"}. Counts are kept between {@link #MIN_ROOMS}
     * and the most rooms a dungeon can fit. Empty if the text isn't a range.
     */
    public static Optional<Range> parse(String text) {
        String[] parts = text.trim().split("\\s*-\\s*");
        if (parts.length < 1 || parts.length > 2) {
            return Optional.empty();
        }
        try {
            int min = clamp(Integer.parseInt(parts[0]));
            int max = clamp(Integer.parseInt(parts[parts.length - 1]));
            return min <= max ? Optional.of(new Range(min, max)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int clamp(int rooms) {
        return Math.max(MIN_ROOMS, Math.min(DungeonLayoutGenerator.maxRooms(), rooms));
    }
}
