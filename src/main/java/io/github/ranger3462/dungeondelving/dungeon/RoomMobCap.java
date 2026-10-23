package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;

import io.github.ranger3462.dungeondelving.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * A cap on how many hostile mobs a dungeon room's spawners keep alive at once, by tier ({@code roomMobCaps}). Each
 * spawner only counts the mobs near itself, so a room with several of them (hunt and relic rooms especially) could fill
 * up faster than a player can fight back. Once a room holds its cap, its spawners skip spawning until some die.
 */
public final class RoomMobCap {
    /** Tiers 1 to 4. */
    public static final List<Integer> DEFAULTS = List.of(7, 9, 12, 15);

    private RoomMobCap() {}

    /** The most hostile mobs a room's spawners keep alive at once in a dungeon of this tier. */
    public static int cap(int tier) {
        List<? extends Integer> caps = Config.ROOM_MOB_CAPS.get();
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        return index < caps.size() ? caps.get(index) : DEFAULTS.get(index);
    }

    /** How many hostile mobs are alive in the room around a position. */
    public static int count(ServerLevel level, BlockPos pos) {
        BlockPos min = DungeonDimension.roomMinAt(pos);
        AABB room = new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + RoomSpec.ROOM_SIZE, min.getY() + RoomSpec.ROOM_HEIGHT,
                min.getZ() + RoomSpec.ROOM_SIZE);
        return level.getEntitiesOfClass(Mob.class, room, mob -> mob.isAlive() && mob instanceof Enemy).size();
    }

    /** Whether the room around a position already holds its cap for this tier. */
    public static boolean full(ServerLevel level, BlockPos pos, int tier) {
        return count(level, pos) >= cap(tier);
    }
}
