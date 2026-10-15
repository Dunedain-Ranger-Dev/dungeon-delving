package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Places dungeon pieces a few per server tick, so building a large dungeon doesn't freeze the server.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonBuildQueue {
    /** Pieces placed per tick. A room is about 27,000 blocks, so this keeps each tick's work small. */
    public static final int PIECES_PER_TICK = 4;

    private record Task(int region, Placement placement) {}

    private static final Deque<Task> TASKS = new ArrayDeque<>();

    private DungeonBuildQueue() {}

    public static void add(int region, List<Placement> placements) {
        placements.forEach(placement -> TASKS.add(new Task(region, placement)));
    }

    /** Drops any pieces still waiting for a region, e.g. when its run ends before the dungeon finished building. */
    public static void cancel(int region) {
        TASKS.removeIf(task -> task.region() == region);
    }

    public static int pending(int region) {
        return (int) TASKS.stream().filter(task -> task.region() == region).count();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        ServerLevel dungeon = event.getServer().getLevel(DungeonDimension.DUNGEON);
        if (dungeon == null) {
            TASKS.clear();
            return;
        }
        for (int i = 0; i < PIECES_PER_TICK && !TASKS.isEmpty(); i++) {
            Task task = TASKS.poll();
            try {
                DungeonBuilder.place(dungeon, task.placement());
            } catch (RuntimeException e) {
                DungeonDelving.LOGGER.error("Failed to place dungeon piece {} in region {}", task.placement().template(), task.region(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }
}
