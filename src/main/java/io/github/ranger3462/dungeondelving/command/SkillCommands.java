package io.github.ranger3462.dungeondelving.command;

import java.util.Arrays;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /skills}: shows your crafting skill levels. {@code /skills set <skill> <level>} sets one, for testing. */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class SkillCommands {
    private SkillCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("skills")
                .executes(SkillCommands::show)
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(CraftSkill.values()).map(skill -> skill.id), builder))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, CraftingSkills.MAX_LEVEL))
                                        .executes(SkillCommands::set)))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Feedback.tell(context.getSource(), "Crafting skills:");
        for (CraftSkill skill : CraftSkill.values()) {
            int xp = CraftingSkills.xp(player, skill);
            int level = CraftingSkills.levelFor(xp);
            String progress = level >= CraftingSkills.MAX_LEVEL ? "max level"
                    : (xp - CraftingSkills.xpFor(level)) + "/" + (CraftingSkills.xpFor(level + 1) - CraftingSkills.xpFor(level)) + " XP to next";
            Feedback.detail(context.getSource(), skill.displayName().getString() + ": level " + level + " (" + progress + ")");
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CraftSkill> skill = CraftSkill.byId(StringArgumentType.getString(context, "skill"));
        if (skill.isEmpty()) {
            Feedback.fail(context.getSource(), "Unknown skill; try " + Arrays.stream(CraftSkill.values()).map(s -> s.id).toList());
            return 0;
        }
        int level = IntegerArgumentType.getInteger(context, "level");
        CraftingSkills.setLevel(player, skill.get(), level);
        Feedback.tell(context.getSource(), skill.get().displayName().getString() + " set to level " + level);
        return 1;
    }
}
