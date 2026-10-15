package io.github.ranger3462.dungeondelving.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.vault.VaultDimension;
import io.github.ranger3462.dungeondelving.vault.VaultRegions;
import io.github.ranger3462.dungeondelving.vault.VaultRun;
import io.github.ranger3462.dungeondelving.vault.VaultTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only commands for testing vaults before the real entry and exit (DD-15) exist:
 * {@code /dungeondelving vault enter|leave|regions}.
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
                .then(Commands.literal("vault")
                        .then(Commands.literal("enter").executes(DevCommands::enter))
                        .then(Commands.literal("leave").executes(DevCommands::leave))
                        .then(Commands.literal("regions").executes(DevCommands::regions))));
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (VaultRegions.get().run(player.getUUID()).isPresent()) {
            context.getSource().sendFailure(Component.literal("You are already in a vault. Leave it first."));
            return 0;
        }
        VaultRun run = VaultTravel.enter(player);
        BlockPos center = VaultDimension.regionCenter(run.region());
        context.getSource().sendSuccess(() -> Component.literal(
                "Entered vault region " + run.region() + " (center " + center.getX() + ", " + center.getY() + ", " + center.getZ() + ")"), true);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return VaultTravel.leave(player).map(run -> {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Left vault region " + run.region() + ". It will be wiped on the next server start."), true);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.literal("You are not in a vault."));
            return 0;
        });
    }

    private static int regions(CommandContext<CommandSourceStack> context) {
        VaultRegions regions = VaultRegions.get();
        context.getSource().sendSuccess(() -> Component.literal(
                "Vault regions: " + regions.regionsCreated() + " created, " + regions.runs().size() + " in use, "
                        + "free " + regions.freeRegions() + ", waiting to be wiped " + regions.releasedRegions()), false);
        return 1;
    }
}
