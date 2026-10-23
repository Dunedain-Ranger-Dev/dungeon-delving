package io.github.ranger3462.dungeondelving.command;

import java.util.Optional;

import static io.github.ranger3462.dungeondelving.command.Feedback.fail;
import static io.github.ranger3462.dungeondelving.command.Feedback.tell;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.DungeonTravel;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.testing.BossDoorSpot;
import io.github.ranger3462.dungeondelving.testing.DevGear;
import io.github.ranger3462.dungeondelving.testing.TestersCharm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only commands for testing dungeons without the gate and exit blocks:
 * {@code /dungeondelving dungeon enter [rooms] [tier]|enter tier <tier> [normal|resource|loot]|enter faction <faction> <tier>|keys|leave|fail|regions}. Without a room count, the
 * count is rolled from the tier's {@code roomCounts} range. For quick survival testing there's also {@code enter geared <tier> [faction]}
 * (enter with tier-appropriate gear), {@code boss} (teleport to just outside the boss door) and {@code leave reset} (leave, then
 * clear everything and put a fresh Tester's Charm in a ring slot).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DevCommands {
    private DevCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(DungeonDelving.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("dungeon")
                        .then(Commands.literal("enter")
                                .executes(context -> enter(context, rolledRooms(context, DungeonRun.MIN_TIER), DungeonRun.MIN_TIER))
                                .then(Commands.literal("faction")
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(DungeonFactions.ids(), builder))
                                                .then(Commands.argument("tier", IntegerArgumentType.integer(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER))
                                                        .executes(context -> {
                                                            int tier = IntegerArgumentType.getInteger(context, "tier");
                                                            String faction = StringArgumentType.getString(context, "faction");
                                                            if (!DungeonFactions.ids().contains(faction)) {
                                                                fail(context.getSource(), "Unknown faction " + faction + "; try " + DungeonFactions.ids());
                                                                return 0;
                                                            }
                                                            return enterFaction(context, tier, faction);
                                                        }))))
                                .then(Commands.literal("geared")
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER))
                                                .executes(context -> enterGeared(context, IntegerArgumentType.getInteger(context, "tier"), null))
                                                .then(Commands.argument("faction", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(DungeonFactions.ids(), builder))
                                                        .executes(context -> enterGeared(context, IntegerArgumentType.getInteger(context, "tier"),
                                                                StringArgumentType.getString(context, "faction"))))))
                                .then(Commands.literal("tier")
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER))
                                                .executes(context -> {
                                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                                    return enter(context, rolledRooms(context, tier), tier);
                                                })
                                                .then(Commands.literal("normal").executes(context -> {
                                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                                    return enter(context, rolledRooms(context, tier), tier, LayoutType.NORMAL);
                                                }))
                                                .then(Commands.literal("resource").executes(context -> {
                                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                                    return enter(context, rolledRooms(context, tier), tier, LayoutType.RESOURCE);
                                                }))
                                                .then(Commands.literal("loot").executes(context -> {
                                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                                    return enter(context, rolledRooms(context, tier), tier, LayoutType.LOOT);
                                                }))))
                                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, DungeonLayoutGenerator.maxRooms()))
                                        .executes(context -> enter(context, IntegerArgumentType.getInteger(context, "rooms"), DungeonRun.MIN_TIER))
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER))
                                                .executes(context -> enter(context, IntegerArgumentType.getInteger(context, "rooms"),
                                                        IntegerArgumentType.getInteger(context, "tier"))))))
                        .then(Commands.literal("keys").executes(DevCommands::keys))
                        .then(Commands.literal("invader").executes(DevCommands::invader))
                        .then(Commands.literal("boss").executes(DevCommands::toBossDoor))
                        .then(Commands.literal("leave").executes(DevCommands::leave)
                                .then(Commands.literal("reset").executes(DevCommands::leaveAndReset)))
                        .then(Commands.literal("fail").executes(DevCommands::failRun))
                        .then(Commands.literal("regions").executes(DevCommands::regions))));
    }

    private static int enterFaction(CommandContext<CommandSourceStack> context, int tier, String faction) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (DungeonRegions.get().run(player.getUUID()).isPresent()) {
            fail(context.getSource(), "You are already in a dungeon. Leave it first.");
            return 0;
        }
        DungeonTravel.Entry entry = DungeonTravel.enter(player, rolledRooms(context, tier), tier, LayoutType.roll(player.getRandom()), faction);
        tell(context.getSource(), "Entered tier " + tier + " " + entry.type().id() + " " + faction + " dungeon: " + entry.layout().rooms().size()
                + " rooms, boss " + DungeonRegions.get().progress(entry.run().region()).map(p -> p.boss()).orElse("?"));
        return 1;
    }

    /** Enters a dungeon (of this faction, or a rolled one if null) already equipped for its tier. */
    private static int enterGeared(CommandContext<CommandSourceStack> context, int tier, String faction) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (faction != null && !DungeonFactions.ids().contains(faction)) {
            fail(context.getSource(), "Unknown faction " + faction + "; try " + DungeonFactions.ids());
            return 0;
        }
        int entered = faction == null ? enter(context, rolledRooms(context, tier), tier) : enterFaction(context, tier, faction);
        if (entered > 0) {
            DevGear.equip(player, tier);
            tell(context.getSource(), "Equipped tier " + tier + " gear.");
        }
        return entered;
    }

    /** Teleports the player to just outside their dungeon's boss door, facing it. */
    private static int toBossDoor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<DungeonRun> run = DungeonRegions.get().run(player.getUUID());
        Optional<BossDoorSpot.Spot> spot = run.flatMap(r -> DungeonRegions.get().progress(r.region())).flatMap(BossDoorSpot::find);
        if (run.isEmpty()) {
            fail(context.getSource(), "You aren't in a dungeon.");
            return 0;
        }
        if (spot.isEmpty()) {
            fail(context.getSource(), "This dungeon has no boss door.");
            return 0;
        }
        BlockPos pos = spot.get().pos();
        player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spot.get().facing().toYRot(), 0.0f);
        tell(context.getSource(), "Moved to the boss door. /dungeondelving dungeon keys gives the keys it needs.");
        return 1;
    }

    /** Leaves the dungeon, then clears everything the player has and gives them a fresh Tester's Charm, worn as a ring. */
    private static int leaveAndReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (DungeonTravel.exit(player).isEmpty()) {
            fail(context.getSource(), "You are not in a dungeon.");
            return 0;
        }
        TestersCharm.clearEverything(player);
        boolean worn = TestersCharm.give(player);
        tell(context.getSource(), "Left the dungeon and cleared your inventory. Tester's Charm " + (worn ? "worn in a ring slot." : "put in your inventory."));
        return 1;
    }

    private static int rolledRooms(CommandContext<CommandSourceStack> context, int tier) {
        return RoomCounts.roll(tier, context.getSource().getLevel().getRandom());
    }

    private static int enter(CommandContext<CommandSourceStack> context, int rooms, int tier) throws CommandSyntaxException {
        return enter(context, rooms, tier, null);
    }

    /** Enters a dungeon; a null layout type is rolled as usual. */
    private static int enter(CommandContext<CommandSourceStack> context, int rooms, int tier, LayoutType type) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (DungeonRegions.get().run(player.getUUID()).isPresent()) {
            fail(context.getSource(), "You are already in a dungeon. Leave it first.");
            return 0;
        }
        DungeonTravel.Entry entry = type == null ? DungeonTravel.enter(player, rooms, tier) : DungeonTravel.enter(player, rooms, tier, type);
        DungeonRun run = entry.run();
        BlockPos center = DungeonDimension.regionCenter(run.region());
        tell(context.getSource(), "Entered tier " + run.tier() + " " + entry.type().id() + " " + entry.faction() + " dungeon region " + run.region() + " (center " + center.getX() + ", " + center.getY() + ", " + center.getZ()
                + "): " + entry.layout().rooms().size() + " rooms, " + entry.layout().connections().size() + " tunnels, "
                + DungeonRegions.get().progress(run.region()).map(DungeonProgress::keysNeeded).orElse(0) + " keys to open the boss door");
        return 1;
    }

    /** Gives the player every key their dungeon's boss door still needs, for testing the door and boss. */
    private static int keys(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<DungeonRun> run = DungeonRegions.get().run(player.getUUID());
        Optional<DungeonProgress> progress = run.flatMap(r -> DungeonRegions.get().progress(r.region()));
        if (progress.isEmpty()) {
            fail(context.getSource(), "You aren't in a dungeon.");
            return 0;
        }
        int missing = Math.max(0, progress.get().keysNeeded() - progress.get().keysUsed());
        ItemStack keys = DungeonKeyItem.forRegion(run.get().region());
        keys.setCount(Math.max(1, missing));
        if (!player.getInventory().add(keys)) {
            player.drop(keys, false);
        }
        tell(context.getSource(), "Gave " + Math.max(1, missing) + " Dungeon Key(s). The boss door has " + progress.get().keysUsed() + " of "
                + progress.get().keysNeeded() + ".");
        return 1;
    }

    /** Calls an Apotheosis invader in next to the player, the way dungeon spawners sometimes do. For testing. */
    private static int invader(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!ModList.get().isLoaded("apotheosis")) {
            fail(context.getSource(), "Apotheosis isn't installed.");
            return 0;
        }
        if (player.level().dimension() != DungeonDimension.DUNGEON) {
            fail(context.getSource(), "Invaders only come in dungeons. Enter one first.");
            return 0;
        }
        boolean spawned = io.github.ranger3462.dungeondelving.integration.apotheosis.ApotheosisDungeons.spawnInvader(player.serverLevel(),
                player.blockPosition().relative(player.getDirection(), 4), player);
        if (spawned) {
            tell(context.getSource(), "An invader arrives.");
        } else {
            fail(context.getSource(), "No invader could come for this dungeon's tier.");
        }
        return spawned ? 1 : 0;
    }

    /** Same as using the exit block: ends the run and keeps the loot. */
    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DungeonTravel.exit(player).map(run -> {
            tell(context.getSource(), "Left dungeon region " + run.region() + ". It will be wiped the next time the world loads.");
            return 1;
        }).orElseGet(() -> {
            fail(context.getSource(), "You are not in a dungeon.");
            return 0;
        });
    }

    /** Fails the run the same way running out of time does, for testing without waiting. */
    private static int failRun(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DungeonTravel.fail(player, "Run failed (dev command).").map(run -> 1).orElseGet(() -> {
            fail(context.getSource(), "You are not in a dungeon.");
            return 0;
        });
    }

    private static int regions(CommandContext<CommandSourceStack> context) {
        DungeonRegions regions = DungeonRegions.get();
        // "total" counts every region ever made; wiped regions become free and get reused rather than removed.
        tell(context.getSource(), "Dungeon regions: " + regions.regionsCreated() + " total ("
                + regions.runs().size() + " in use, "
                + regions.freeRegions().size() + " free " + regions.freeRegions() + ", "
                + regions.releasedRegions().size() + " waiting to be wiped " + regions.releasedRegions() + ")");
        return 1;
    }
}
