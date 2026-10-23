package io.github.ranger3462.dungeondelving.command;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.character.SkillGroup;
import io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat listings of a player's skills, grouped (professions, combat, armor, magic, other), a few to a line. Levels are
 * the skill menu's (counting from 0); hovering a skill shows its experience toward the next level. Needs Pufferfish's
 * Skills.
 */
final class SkillReport {
    /** Skills per chat line. */
    private static final int PER_LINE = 4;

    private SkillReport() {}

    static Component header(String title) {
        return Component.literal("━━ " + title + " ━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** One skill as "Name level", with its progress on hover. Null if the player has no such skill. */
    static MutableComponent entry(ServerPlayer player, ResourceLocation skill) {
        var progress = PufferfishSkills.progress(player, skill);
        if (progress.isEmpty()) {
            return null;
        }
        var p = progress.get();
        Component hover = Component.literal("Level " + p.level() + ": " + p.current() + "/" + p.required() + " XP to level " + (p.level() + 1));
        return Component.empty().append(SkillGroup.name(skill).copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" " + p.level()).withStyle(p.level() > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /** Sends every skill, group by group. */
    static void sendAll(CommandSourceStack source, ServerPlayer player) {
        for (SkillGroup group : SkillGroup.values()) {
            List<MutableComponent> entries = new ArrayList<>();
            for (ResourceLocation skill : group.skills) {
                MutableComponent entry = entry(player, skill);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (entries.isEmpty()) {
                continue;
            }
            Feedback.send(source, Component.literal(group.title).withStyle(ChatFormatting.YELLOW));
            for (int i = 0; i < entries.size(); i += PER_LINE) {
                MutableComponent line = Component.literal("  ");
                for (int j = i; j < Math.min(entries.size(), i + PER_LINE); j++) {
                    if (j > i) {
                        line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    line.append(entries.get(j));
                }
                Feedback.send(source, line);
            }
        }
    }
}
