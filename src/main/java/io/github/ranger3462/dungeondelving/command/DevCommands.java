package io.github.ranger3462.dungeondelving.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.vault.VaultDimension;
import io.github.ranger3462.dungeondelving.vault.VaultRegions;
import io.github.ranger3462.dungeondelving.vault.VaultRun;
import io.github.ranger3462.dungeondelving.vault.VaultTravel;
import net.minecraft.ChatFormatting;
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
            fail(context.getSource(), "You are already in a vault. Leave it first.");
            return 0;
        }
        VaultRun run = VaultTravel.enter(player);
        BlockPos center = VaultDimension.regionCenter(run.region());
        tell(context.getSource(), "Entered vault region " + run.region() + " (center " + center.getX() + ", " + center.getY() + ", " + center.getZ() + ")");
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return VaultTravel.leave(player).map(run -> {
            tell(context.getSource(), "Left vault region " + run.region() + ". It will be wiped the next time the world loads.");
            return 1;
        }).orElseGet(() -> {
            fail(context.getSource(), "You are not in a vault.");
            return 0;
        });
    }

    private static int regions(CommandContext<CommandSourceStack> context) {
        VaultRegions regions = VaultRegions.get();
        // "total" counts every region ever made; wiped regions become free and get reused rather than removed.
        tell(context.getSource(), "Vault regions: " + regions.regionsCreated() + " total ("
                + regions.runs().size() + " in use, "
                + regions.freeRegions().size() + " free " + regions.freeRegions() + ", "
                + regions.releasedRegions().size() + " waiting to be wiped " + regions.releasedRegions() + ")");
        return 1;
    }

    /**
     * Messages the player running the command directly. Minecraft hides normal command feedback when a command runs
     * inside a function or command block, so dev functions would otherwise give no response.
     */
    private static void tell(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.GOLD));
    }

    private static void fail(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static void send(CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
        } else {
            source.sendSystemMessage(message);
        }
    }
}
