package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Handles what happens to a run outside of the gate and exit: dying, respawning, logging back in, and the run timer.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonLifecycle {
    private static final int TICKS_PER_SECOND = 20;
    /** How often run times are written to disk, so a crash loses at most this much. */
    private static final int SAVE_INTERVAL_TICKS = 60 * TICKS_PER_SECOND;

    private DungeonLifecycle() {}

    /**
     * Dying in a dungeon fails the run. Dungeon ores found this run are lost and gear is damaged; everything else is held and given
     * back on respawn, so nothing the player brought is left behind in the dungeon.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != DungeonDimension.DUNGEON) {
            return;
        }
        DungeonRegions regions = DungeonRegions.get();
        if (regions.run(player.getUUID()).isEmpty()) {
            return;
        }
        int lost = DungeonLoot.applyFailurePenalty(player, Config.FAILURE_GEAR_DAMAGE.get());
        DungeonKeyItem.removeAll(player); // Before the inventory is held, so keys don't come back on respawn.
        ListTag held = player.getInventory().save(new ListTag());
        player.getInventory().clearContent();
        regions.holdInventory(player.getUUID(), held);
        DungeonTravel.end(player);
        player.sendSystemMessage(Component.literal("You died in the dungeon. " + DungeonTravel.penaltyText(lost)
                + " Everything else comes back when you respawn.").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DungeonRegions.get().takeHeldInventory(player.getUUID()).ifPresent(held -> {
            player.getInventory().load(held);
            player.inventoryMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("Your items are back.").withStyle(ChatFormatting.GOLD));
        });
    }

    /** Anyone who logs in inside the dungeon dimension without an active run is sent to world spawn. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != DungeonDimension.DUNGEON) {
            return;
        }
        if (DungeonRegions.get().run(player.getUUID()).isEmpty()) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("That dungeon is gone, so you've been sent back to spawn.").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Counts run time while players are in their dungeon, shows the time left, and fails runs that run out. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DungeonRegions regions = DungeonRegions.get();
        if (regions.runs().isEmpty()) {
            return;
        }
        int limitTicks = Config.RUN_TIME_LIMIT_SECONDS.get() * TICKS_PER_SECOND;
        List<ServerPlayer> outOfTime = new ArrayList<>();
        for (DungeonRun run : regions.runs()) {
            ServerPlayer player = server.getPlayerList().getPlayer(run.player());
            if (player == null || !player.isAlive() || player.level().dimension() != DungeonDimension.DUNGEON) {
                continue;
            }
            int ticks = regions.tick(run.player());
            if (limitTicks > 0 && ticks % TICKS_PER_SECOND == 0) {
                int secondsLeft = Math.max(0, (limitTicks - ticks) / TICKS_PER_SECOND);
                ChatFormatting color = secondsLeft <= 60 ? ChatFormatting.RED : ChatFormatting.GOLD;
                player.displayClientMessage(Component.literal("Time left: " + secondsLeft / 60 + ":" + String.format("%02d", secondsLeft % 60))
                        .withStyle(color), true);
                if (ticks >= limitTicks) {
                    outOfTime.add(player);
                }
            }
        }
        outOfTime.forEach(player -> DungeonTravel.fail(player, "Time's up!"));
        if (server.getTickCount() % SAVE_INTERVAL_TICKS == 0) {
            regions.saveNow();
        }
    }
}
