package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Builds dungeons a few steps per server tick (pieces, then the bedrock shell), so building a large dungeon doesn't
 * freeze the server.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonBuildQueue {
    /** Pieces placed per tick. A room is about 27,000 blocks, so this keeps each tick's work small. */
    public static final int PIECES_PER_TICK = 4;

    /** One step of building a region: placing a piece or filling a shell section. */
    private record Task(int region, Consumer<ServerLevel> step, String description) {}

    private static final Deque<Task> TASKS = new ArrayDeque<>();

    private DungeonBuildQueue() {}

    public static void add(int region, List<Placement> placements) {
        placements.forEach(placement -> TASKS.add(new Task(region, level -> DungeonBuilder.place(level, placement), placement.template().toString())));
    }

    public static void addShell(int region, List<BoundingBox> sections) {
        sections.forEach(box -> TASKS.add(new Task(region, level -> DungeonBuilder.fillShell(level, box), "bedrock shell " + box)));
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
                task.step().accept(dungeon);
            } catch (RuntimeException e) {
                DungeonDelving.LOGGER.error("Failed to build {} in dungeon region {}", task.description(), task.region(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }
}
