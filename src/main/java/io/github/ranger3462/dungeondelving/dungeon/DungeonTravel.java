package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuildQueue;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Moves players into and out of dungeon runs. */
public final class DungeonTravel {
    private DungeonTravel() {}

    /** The result of entering a dungeon: the run, and the layout being built for it. */
    public record Entry(DungeonRun run, DungeonLayout layout) {}

    /**
     * Starts a run in a fresh region: generates a layout, places the start room right away, queues the rest to be
     * built over the next few ticks, and sends the player into the start room.
     */
    public static Entry enter(ServerPlayer player, int roomCount) {
        ServerLevel dungeon = dungeonLevel(player);
        DungeonRegions regions = DungeonRegions.get();
        int region = regions.allocate();
        BlockPos center = DungeonDimension.regionCenter(region);

        DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon.getRandom(), roomCount);
        DungeonPieces pieces = DungeonPieces.load(player.server);
        List<Placement> placements = DungeonBuilder.plan(layout, pieces, center, dungeon.getRandom());
        DungeonBuilder.place(dungeon, placements.getFirst());
        DungeonBuildQueue.add(region, placements.subList(1, placements.size()));

        DungeonRun run = new DungeonRun(player.getUUID(), region, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        player.teleportTo(dungeon, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, player.getYRot(), player.getXRot());
        return new Entry(run, layout);
    }

    /** Ends the player's run and sends them back to where they entered from. */
    public static Optional<DungeonRun> leave(ServerPlayer player) {
        Optional<DungeonRun> ended = DungeonRegions.get().endRun(player.getUUID());
        ended.ifPresent(run -> {
            if (DungeonRegions.get().runs().stream().noneMatch(other -> other.region() == run.region())) {
                DungeonBuildQueue.cancel(run.region());
            }
            ServerLevel target = player.server.getLevel(run.returnLevel());
            if (target == null) {
                target = player.server.overworld();
            }
            player.teleportTo(target, run.returnX(), run.returnY(), run.returnZ(), run.returnYRot(), run.returnXRot());
        });
        return ended;
    }

    private static ServerLevel dungeonLevel(ServerPlayer player) {
        ServerLevel dungeon = player.server.getLevel(DungeonDimension.DUNGEON);
        if (dungeon == null) {
            throw new IllegalStateException("The dungeon dimension is missing");
        }
        return dungeon;
    }
}
