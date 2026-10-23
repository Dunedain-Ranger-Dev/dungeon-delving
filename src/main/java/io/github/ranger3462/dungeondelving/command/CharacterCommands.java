package io.github.ranger3462.dungeondelving.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.Characters;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /character}: shows the player's character level, ability points and scores. Operators can also
 * {@code /character points add <n>} (bonus points) and {@code /character respec} (take every point back), for testing.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class CharacterCommands {
    private CharacterCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("character")
                .executes(CharacterCommands::show)
                .then(Commands.literal("points").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("add").then(Commands.argument("points", IntegerArgumentType.integer(1, 100))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Characters.addBonusPoints(player, IntegerArgumentType.getInteger(context, "points"));
                                    Feedback.tell(context.getSource(), "You now have " + Characters.available(player) + " ability points to spend.");
                                    return 1;
                                }))))
                .then(Commands.literal("respec").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Characters.respec(player);
                    Feedback.tell(context.getSource(), "Ability points taken back: " + Characters.available(player) + " to spend.");
                    return 1;
                })));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Feedback.tell(context.getSource(), "Character level " + Characters.level(player) + ", " + Characters.available(player)
                + " ability points to spend (skill levels: " + SkillLevels.levels(player) + ")");
        for (AbilityScore score : AbilityScore.values()) {
            AttributeInstance attribute = player.getAttribute(score.attribute());
            Feedback.detail(context.getSource(), score.abbreviation + " " + (attribute == null ? "?" : Math.round(attribute.getValue()))
                    + " (+" + Characters.data(player).allocated(score) + " allocated)");
        }
        return 1;
    }
}
