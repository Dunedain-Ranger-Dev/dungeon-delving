package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuildQueue;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Moves players into and out of dungeon runs. A run ends one of two ways: an exit, where the player keeps their
 * dungeon loot, or a failure (death or running out of time), where the loot is lost and their gear is damaged.
 */
public final class DungeonTravel {
    /** Where players arrive, relative to the start room's center: a few blocks south of the exit block. */
    private static final int ARRIVAL_OFFSET = 3;

    private DungeonTravel() {}

    /** The result of entering a dungeon: the run, and the layout being built for it. */
    public record Entry(DungeonRun run, DungeonLayout layout) {}

    /**
     * Starts a run in a fresh region: generates a layout, places the start room and its exit right away, queues the
     * rest to be built over the next few ticks, and sends the player into the start room.
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
        dungeon.setBlock(center.above(), ModRegistries.DUNGEON_EXIT.get().defaultBlockState(), Block.UPDATE_ALL);
        DungeonBuildQueue.add(region, placements.subList(1, placements.size()));

        DungeonRun run = new DungeonRun(player.getUUID(), region, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        player.teleportTo(dungeon, center.getX() + 0.5, center.getY() + 1, center.getZ() + ARRIVAL_OFFSET + 0.5, 180.0f, 0.0f);
        return new Entry(run, layout);
    }

    /** Ends the player's run successfully: they keep their dungeon loot and go back to where they entered from. */
    public static Optional<DungeonRun> exit(ServerPlayer player) {
        Optional<DungeonRun> ended = end(player);
        ended.ifPresent(run -> {
            DungeonLoot.claim(player);
            returnPlayer(player, run);
            player.sendSystemMessage(Component.literal("You made it out with your loot.").withStyle(ChatFormatting.GREEN));
        });
        return ended;
    }

    /**
     * Fails the player's run while they're still alive (e.g. time ran out): their dungeon loot is lost, their gear is
     * damaged, and they're sent back to where they entered from.
     */
    public static Optional<DungeonRun> fail(ServerPlayer player, String reason) {
        Optional<DungeonRun> ended = end(player);
        ended.ifPresent(run -> {
            int lost = DungeonLoot.applyFailurePenalty(player, Config.FAILURE_GEAR_DAMAGE.get());
            returnPlayer(player, run);
            player.sendSystemMessage(Component.literal(reason + " " + penaltyText(lost)).withStyle(ChatFormatting.RED));
        });
        return ended;
    }

    /** What a failed run cost the player, for chat. */
    public static String penaltyText(int lostStacks) {
        String loot = lostStacks == 0 ? "You had no dungeon loot to lose" : "You lost " + lostStacks + " stack" + (lostStacks == 1 ? "" : "s")
                + " of dungeon loot";
        return Config.FAILURE_GEAR_DAMAGE.get() > 0 ? loot + ", and your gear was damaged." : loot + ".";
    }

    /** Ends the player's run, stopping any building still queued for its region. */
    public static Optional<DungeonRun> end(ServerPlayer player) {
        Optional<DungeonRun> ended = DungeonRegions.get().endRun(player.getUUID());
        ended.ifPresent(run -> {
            if (DungeonRegions.get().runs().stream().noneMatch(other -> other.region() == run.region())) {
                DungeonBuildQueue.cancel(run.region());
            }
        });
        return ended;
    }

    private static void returnPlayer(ServerPlayer player, DungeonRun run) {
        ServerLevel target = player.server.getLevel(run.returnLevel());
        if (target == null) {
            target = player.server.overworld();
        }
        player.teleportTo(target, run.returnX(), run.returnY(), run.returnZ(), run.returnYRot(), run.returnXRot());
    }

    private static ServerLevel dungeonLevel(ServerPlayer player) {
        ServerLevel dungeon = player.server.getLevel(DungeonDimension.DUNGEON);
        if (dungeon == null) {
            throw new IllegalStateException("The dungeon dimension is missing");
        }
        return dungeon;
    }
}
