package io.github.ranger3462.dungeondelving.command;

import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.Characters;
import io.github.ranger3462.dungeondelving.character.SkillGroup;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /character}: shows the player's character level, ability points and scores. Operators can also
 * {@code /character points add <n>} (bonus points), {@code /character respec} (take every point back), and set a race or class,
 * for testing.
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
                .then(Commands.literal("race").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reset").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Characters.setRace(player, null);
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new io.github.ranger3462.dungeondelving.character.RacePayloads.Open());
                            Feedback.tell(context.getSource(), "Race cleared: pick a new one.");
                            return 1;
                        }))
                        .then(Commands.argument("race", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(io.github.ranger3462.dungeondelving.character.Race.values()).map(race -> race.id), builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "race");
                                    var race = io.github.ranger3462.dungeondelving.character.Race.byId(id);
                                    if (race.isEmpty()) {
                                        Feedback.fail(context.getSource(), "Unknown race " + id);
                                        return 0;
                                    }
                                    Characters.setRace(player, race.get());
                                    Feedback.tell(context.getSource(), "You are now a " + race.get().displayName().getString() + ".");
                                    return 1;
                                })))
                .then(Commands.literal("class").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("class", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(io.github.ranger3462.dungeondelving.character.PlayerClass.values()).map(c -> c.id), builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "class");
                                    var chosen = io.github.ranger3462.dungeondelving.character.PlayerClass.byId(id);
                                    if (chosen.isEmpty()) {
                                        Feedback.fail(context.getSource(), "Unknown class " + id);
                                        return 0;
                                    }
                                    Characters.setClass(player, chosen.get());
                                    Feedback.tell(context.getSource(), "You are now a " + chosen.get().displayName().getString() + ".");
                                    return 1;
                                })))
                .then(Commands.literal("respec").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Characters.respec(player);
                    Feedback.tell(context.getSource(), "Ability points taken back: " + Characters.available(player) + " to spend.");
                    return 1;
                })));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        int available = Characters.available(player);
        Feedback.send(source, SkillReport.header("Character"));
        Feedback.send(source, Component.literal("Race ").withStyle(ChatFormatting.GRAY).append(Characters.data(player).raceOf()
                .map(race -> race.displayName().copy().withStyle(ChatFormatting.WHITE)).orElse(Component.literal("none yet").withStyle(ChatFormatting.DARK_GRAY))));
        Feedback.send(source, Component.literal("Class ").withStyle(ChatFormatting.GRAY).append(Characters.data(player).classOf()
                .map(c -> c.displayName().copy().withStyle(ChatFormatting.WHITE)).orElse(Component.literal("none yet (read a Tome of Calling)")
                        .withStyle(ChatFormatting.DARK_GRAY))));
        Feedback.send(source, Component.literal("Level ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(Characters.level(player))).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("   ·   ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(available + " ability point" + (available == 1 ? "" : "s") + " to spend")
                        .withStyle(available > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(available > 0 ? " (press J)" : "").withStyle(ChatFormatting.DARK_GRAY)));

        Feedback.send(source, Component.literal("Ability scores").withStyle(ChatFormatting.YELLOW));
        AbilityScore[] scores = AbilityScore.values();
        for (int i = 0; i < scores.length; i += 3) {
            MutableComponent line = Component.literal("  ");
            for (int j = i; j < Math.min(scores.length, i + 3); j++) {
                AbilityScore score = scores[j];
                AttributeInstance attribute = player.getAttribute(score.attribute());
                long value = attribute == null ? Math.round(AbilityScore.BASE) : Math.round(attribute.getValue());
                int allocated = Characters.data(player).allocated(score);
                if (j > i) {
                    line.append(Component.literal("   ·   ").withStyle(ChatFormatting.DARK_GRAY));
                }
                line.append(Component.literal(score.abbreviation + " ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(String.valueOf(value)).withStyle(value > AbilityScore.BASE ? ChatFormatting.AQUA : ChatFormatting.GRAY))
                        .append(Component.literal(allocated > 0 ? " (+" + allocated + ")" : "").withStyle(ChatFormatting.DARK_GREEN))
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                score.displayName().copy().append("\n").append(Component.translatable("attribute.dungeondelving." + score.id + ".description"))))));
            }
            Feedback.send(source, line);
        }

        if (SkillLevels.pufferfish()) {
            int top = Config.CHARACTER_LEVEL_TOP_SKILLS.get();
            var best = SkillLevels.levelsById(player).entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed()).limit(top).toList();
            Feedback.send(source, Component.literal("Your best " + top + " skills set your level").withStyle(ChatFormatting.YELLOW));
            if (best.isEmpty()) {
                Feedback.send(source, Component.literal("  None yet: mine, fight, craft or cast to level a skill.").withStyle(ChatFormatting.GRAY));
            } else {
                MutableComponent line = Component.literal("  ");
                for (int i = 0; i < best.size(); i++) {
                    if (i > 0) {
                        line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    line.append(SkillGroup.name(best.get(i).getKey()).copy().withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" " + best.get(i).getValue()).withStyle(ChatFormatting.AQUA));
                }
                Feedback.send(source, line);
            }
            Feedback.send(source, Component.literal("All skills: /skills").withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }
}
