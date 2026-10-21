package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.RoomKind;
import net.minecraft.util.RandomSource;

/**
 * What a dungeon run leans towards, rolled once per run. It decides what the rooms that aren't the start, the boss or
 * a challenge room become: ordinary rooms, quarries (packed with ore) or treasure rooms (extra chests). Odds come from
 * the {@code layoutTypes} config.
 */
public enum LayoutType {
    /** Mostly ordinary rooms, with the odd quarry or treasure room. */
    NORMAL(70, 20, 10),
    /** Mostly quarries: a run for ore. */
    RESOURCE(30, 65, 5),
    /** Lots of treasure rooms: a run for loot. */
    LOOT(40, 10, 50);

    public static final List<String> DEFAULTS = List.of("normal=50", "resource=25", "loot=25");

    private final int common;
    private final int quarry;
    private final int treasure;

    LayoutType(int common, int quarry, int treasure) {
        this.common = common;
        this.quarry = quarry;
        this.treasure = treasure;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LayoutType> byId(String id) {
        for (LayoutType type : values()) {
            if (type.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** What an ordinary room slot becomes in a dungeon of this type. */
    public RoomKind pickRoom(RandomSource random) {
        int roll = random.nextInt(common + quarry + treasure);
        if (roll < common) {
            return RoomKind.COMMON;
        }
        return roll < common + quarry ? RoomKind.QUARRY : RoomKind.TREASURE;
    }

    /** Reads one {@code "type=weight"} entry. Empty if it isn't valid. */
    public static Optional<Weighted> parse(String text) {
        String[] parts = text.trim().split("\\s*=\\s*");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int weight = Integer.parseInt(parts[1]);
            return weight >= 0 ? byId(parts[0]).map(type -> new Weighted(type, weight)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record Weighted(LayoutType type, int weight) {}

    /** Rolls a layout type from the config. */
    public static LayoutType roll(RandomSource random) {
        return roll(random, Config.LAYOUT_TYPES.get());
    }

    /** Rolls a layout type from {@code "type=weight"} entries; normal if none are usable. */
    public static LayoutType roll(RandomSource random, List<? extends String> entries) {
        List<Weighted> weights = entries.stream().map(LayoutType::parse).flatMap(Optional::stream).filter(w -> w.weight() > 0).toList();
        int total = weights.stream().mapToInt(Weighted::weight).sum();
        if (total == 0) {
            return NORMAL;
        }
        int roll = random.nextInt(total);
        for (Weighted weighted : weights) {
            roll -= weighted.weight();
            if (roll < 0) {
                return weighted.type();
            }
        }
        return NORMAL;
    }
}
