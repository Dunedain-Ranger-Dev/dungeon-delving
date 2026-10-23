package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuildQueue;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
import io.github.ranger3462.dungeondelving.gate.RunTuning;
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
    public record Entry(DungeonRun run, DungeonLayout layout, LayoutType type, String faction) {}

    /**
     * Starts a run in a fresh region: generates a layout, places the start room and its exit right away, queues the
     * rest and the bedrock shell to be built over the next few ticks, and sends the player into the start room.
     */
    public static Entry enter(ServerPlayer player, int roomCount, int tier) {
        return enter(player, roomCount, tier, LayoutType.roll(player.getRandom()));
    }

    /** Starts a run with a given layout type. */
    public static Entry enter(ServerPlayer player, int roomCount, int tier, LayoutType type) {
        return enter(player, roomCount, tier, type, DungeonFactions.pick(tier, player.getRandom()));
    }

    /** Starts a run with a given layout type, held by a given faction. */
    public static Entry enter(ServerPlayer player, int roomCount, int tier, LayoutType type, String faction) {
        return enter(player, roomCount, tier, type, faction, RunTuning.NONE);
    }

    /** Starts a run with a given layout type and faction, changed by the dungeon stones tuned into it. */
    public static Entry enter(ServerPlayer player, int roomCount, int tier, LayoutType type, String faction, RunTuning tuning) {
        tier = DungeonRun.clampTier(tier);
        ServerLevel dungeon = dungeonLevel(player);
        DungeonRegions regions = DungeonRegions.get();
        int region = regions.allocate();
        BlockPos center = DungeonDimension.regionCenter(region);

        DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon.getRandom(), roomCount, DungeonProgress.keysFor(tier), type);
        int challengeRooms = (int) layout.rooms().values().stream().filter(room -> room.kind() == DungeonLayout.RoomKind.CHALLENGE).count();
        List<BlockPos> door = DungeonBuilder.bossDoor(layout, center);
        String boss = DungeonBosses.pick(tier, faction, dungeon.getRandom());
        regions.startProgress(region, new DungeonProgress(tier, boss, Math.min(DungeonProgress.keysFor(tier), challengeRooms), door,
                DungeonBuilder.bossRoomCenter(layout, center).orElse(null)).withFaction(faction));

        DungeonPieces pieces = DungeonPieces.load(player.server);
        List<Placement> placements = DungeonBuilder.plan(layout, pieces, center, dungeon.getRandom(), boss, tuning);
        DungeonMarkers.Rolls rolls = DungeonMarkers.Rolls.fromConfig(tier, region, faction);
        DungeonBuilder.place(dungeon, placements.getFirst(), rolls);
        dungeon.setBlock(center.above(), ModRegistries.DUNGEON_EXIT.get().defaultBlockState(), Block.UPDATE_ALL);
        DungeonBuildQueue.add(region, placements.subList(1, placements.size()), rolls);
        DungeonBuildQueue.addBlocks(region, door, ModRegistries.BOSS_DOOR.get().defaultBlockState(), "boss door");
        DungeonBuildQueue.addShell(region, DungeonBuilder.shell(layout, center));

        DungeonRun run = new DungeonRun(player.getUUID(), region, tier, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        player.teleportTo(dungeon, center.getX() + 0.5, center.getY() + 1, center.getZ() + ARRIVAL_OFFSET + 0.5, 180.0f, 0.0f);
        return new Entry(run, layout, type, faction);
    }

    /**
     * Ends the player's run successfully: they go back to where they entered from, and the dungeon ores they found
     * become ordinary items.
     */
    public static Optional<DungeonRun> exit(ServerPlayer player) {
        Optional<DungeonRun> ended = end(player);
        ended.ifPresent(run -> {
            returnPlayer(player, run);
            int stacks = DungeonLoot.claim(player);
            player.sendSystemMessage(Component.literal(stacks == 0 ? "You made it out."
                    : "You made it out with " + stacks + " stack" + (stacks == 1 ? "" : "s") + " of dungeon ore.").withStyle(ChatFormatting.GREEN));
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
        String loot = lostStacks == 0 ? "You had no dungeon ore to lose" : "You lost " + lostStacks + " stack" + (lostStacks == 1 ? "" : "s")
                + " of dungeon ore";
        return Config.FAILURE_GEAR_DAMAGE.get() > 0 ? loot + ", and your gear was damaged." : loot + ".";
    }

    /** Ends the player's run, stopping any building still queued for its region. */
    public static Optional<DungeonRun> end(ServerPlayer player) {
        DungeonKeyItem.removeAll(player);
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
