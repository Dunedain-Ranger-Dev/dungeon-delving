package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonBag;
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonBagItem;
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
import net.minecraft.world.item.ItemStack;
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
     * rest and the bedrock shell to be built over the next few ticks, and sends the player into the start room.
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
        DungeonBuildQueue.addShell(region, DungeonBuilder.shell(layout, center));

        DungeonRun run = new DungeonRun(player.getUUID(), region, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        regions.startBag(player.getUUID(), new DungeonBag(DungeonBag.slotsForLevel(player.getData(ModRegistries.BAG_LEVEL))));
        player.teleportTo(dungeon, center.getX() + 0.5, center.getY() + 1, center.getZ() + ARRIVAL_OFFSET + 0.5, 180.0f, 0.0f);
        giveBagItem(player);
        return new Entry(run, layout);
    }

    /**
     * Ends the player's run successfully: they go back to where they entered from, their bag is emptied into their
     * inventory (anything that doesn't fit drops at their feet), and their dungeon loot becomes ordinary items.
     */
    public static Optional<DungeonRun> exit(ServerPlayer player) {
        Optional<DungeonBag> bag = DungeonRegions.get().takeBag(player.getUUID());
        Optional<DungeonRun> ended = end(player);
        ended.ifPresent(run -> {
            returnPlayer(player, run);
            int stacks = bag.map(b -> emptyIntoInventory(player, b)).orElse(0);
            DungeonLoot.claim(player);
            player.sendSystemMessage(Component.literal(stacks == 0 ? "You made it out, though your bag was empty."
                    : "You made it out with " + stacks + " stack" + (stacks == 1 ? "" : "s") + " of loot.").withStyle(ChatFormatting.GREEN));
        });
        return ended;
    }

    /**
     * Fails the player's run while they're still alive (e.g. time ran out): their dungeon loot is lost, their gear is
     * damaged, and they're sent back to where they entered from.
     */
    public static Optional<DungeonRun> fail(ServerPlayer player, String reason) {
        int bagged = DungeonRegions.get().takeBag(player.getUUID()).map(bag -> bag.takeAll().size()).orElse(0);
        Optional<DungeonRun> ended = end(player);
        ended.ifPresent(run -> {
            int lost = bagged + DungeonLoot.applyFailurePenalty(player, Config.FAILURE_GEAR_DAMAGE.get());
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

    /** Ends the player's run, stopping any building still queued for its region and taking back the bag item. */
    public static Optional<DungeonRun> end(ServerPlayer player) {
        DungeonBagItem.removeAll(player.getInventory());
        DungeonBagItem.removeAll(player.getEnderChestInventory());
        Optional<DungeonRun> ended = DungeonRegions.get().endRun(player.getUUID());
        ended.ifPresent(run -> {
            if (DungeonRegions.get().runs().stream().noneMatch(other -> other.region() == run.region())) {
                DungeonBuildQueue.cancel(run.region());
            }
        });
        return ended;
    }

    /** Moves everything from a bag into the player's inventory, dropping what doesn't fit. Returns the stack count. */
    public static int emptyIntoInventory(ServerPlayer player, DungeonBag bag) {
        int stacks = 0;
        for (ItemStack stack : bag.takeAll()) {
            stacks++;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
        return stacks;
    }

    private static void giveBagItem(ServerPlayer player) {
        ItemStack bag = new ItemStack(ModRegistries.DUNGEON_BAG.get());
        if (!player.getInventory().add(bag)) {
            player.drop(bag, false); // Inventory full: drop it where the player can pick it back up.
        }
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
