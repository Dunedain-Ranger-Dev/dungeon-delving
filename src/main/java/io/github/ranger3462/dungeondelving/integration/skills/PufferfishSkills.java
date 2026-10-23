package io.github.ranger3462.dungeondelving.integration.skills;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.character.Characters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.Experience;
import net.puffish.skillsmod.api.SkillsAPI;

/**
 * Pufferfish's Skills, which runs the skill trees (DD-80): each skill is one of its categories, with its own
 * experience, levels and points. Only loaded when Pufferfish's Skills is installed, so nothing outside this package
 * may refer to its classes; go through {@link io.github.ranger3462.dungeondelving.character.SkillLevels}.
 */
public final class PufferfishSkills {
    private PufferfishSkills() {}

    /** Called from the mod constructor when Pufferfish's Skills is loaded. */
    public static void register() {
        // Each new skill point means a skill levelled up, which may raise the character level.
        SkillsAPI.registerNewPointEvent((player, category) -> Characters.checkLevel(player));
    }

    private static Optional<Experience> experience(ResourceLocation category) {
        return SkillsAPI.getCategory(category).flatMap(c -> c.getExperience());
    }

    /** A player's level in a skill, counting from 0, or empty if there's no such skill (or it has no levels). */
    public static Optional<Integer> level(ServerPlayer player, ResourceLocation category) {
        return experience(category).map(experience -> experience.getLevel(player));
    }

    /** A player's total experience in a skill. */
    public static Optional<Integer> total(ServerPlayer player, ResourceLocation category) {
        return experience(category).map(experience -> experience.getTotal(player));
    }

    /** Adds to a player's total experience in a skill, if it exists. */
    public static void addTotal(ServerPlayer player, ResourceLocation category, int amount) {
        experience(category).ifPresent(experience -> experience.addTotal(player, amount));
    }

    /** Sets a player's total experience in a skill, if it exists. */
    public static void setTotal(ServerPlayer player, ResourceLocation category, int total) {
        experience(category).ifPresent(experience -> experience.setTotal(player, total));
    }
}
