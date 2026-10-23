package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * A cap on how many hostile mobs a dungeon room's spawners keep alive at once, by faction and tier
 * ({@code roomMobCaps}). Each spawner only counts the mobs near itself, so a room with several of them (hunt and relic
 * rooms especially) could fill up faster than a player can fight back. Once a room holds its cap, its spawners skip
 * spawning until some die. Factions that win by numbers (the undead, weak goblins) get higher caps than orcs.
 */
public final class RoomMobCap {
    /** "faction=tier1,tier2,tier3,tier4" entries. */
    public static final List<String> DEFAULTS = List.of("undead=10,12,15,18", "goblins=10,13,16,20", "orcs=7,9,12,15");
    /** For a faction with no entry. */
    public static final List<Integer> FALLBACK = List.of(7, 9, 12, 15);

    private RoomMobCap() {}

    /** The most hostile mobs a room's spawners keep alive at once in a dungeon of this faction and tier. */
    public static int cap(String faction, int tier) {
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        List<Integer> caps = Config.ROOM_MOB_CAPS.get().stream().map(RoomMobCap::parse).flatMap(Optional::stream)
                .filter(entry -> entry.faction().equals(faction)).map(Entry::caps).findFirst().orElse(FALLBACK);
        return caps.get(index);
    }

    /** A faction's caps, one per tier. */
    public record Entry(String faction, List<Integer> caps) {}

    /** Parses a "faction=7,9,12,15" entry; empty if it's malformed. */
    public static Optional<Entry> parse(String text) {
        String[] parts = text.split("=");
        if (parts.length != 2 || parts[0].isBlank()) {
            return Optional.empty();
        }
        String[] numbers = parts[1].split(",");
        if (numbers.length != DungeonRun.MAX_TIER - DungeonRun.MIN_TIER + 1) {
            return Optional.empty();
        }
        List<Integer> caps = new ArrayList<>();
        for (String number : numbers) {
            try {
                int cap = Integer.parseInt(number.trim());
                if (cap <= 0) {
                    return Optional.empty();
                }
                caps.add(cap);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(new Entry(parts[0].trim(), List.copyOf(caps)));
    }

    /** How many hostile mobs are alive in the room around a position. */
    public static int count(ServerLevel level, BlockPos pos) {
        BlockPos min = DungeonDimension.roomMinAt(pos);
        AABB room = new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + RoomSpec.ROOM_SIZE, min.getY() + RoomSpec.ROOM_HEIGHT,
                min.getZ() + RoomSpec.ROOM_SIZE);
        return level.getEntitiesOfClass(Mob.class, room, mob -> mob.isAlive() && mob instanceof Enemy).size();
    }

    /** Whether the room around a position already holds its cap for this faction and tier. */
    public static boolean full(ServerLevel level, BlockPos pos, String faction, int tier) {
        return count(level, pos) >= cap(faction, tier);
    }
}
