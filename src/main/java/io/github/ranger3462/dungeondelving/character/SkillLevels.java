package io.github.ranger3462.dungeondelving.character;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * A player's skill levels, for the character level (DD-80). With Pufferfish's Skills these are its categories (the
 * professions, and the RPG Series' class and weapon trees), counting from 0; the crafting skills are mirrored into
 * their categories. Without it, only the crafting skills count. The character level averages the best few skills,
 * RuneScape-style, so a crafter levels as well as a fighter.
 */
public final class SkillLevels {
    /** Skills that count toward the character level by default. */
    public static final List<String> DEFAULT_SKILLS = List.of("dungeondelving:mining", "dungeondelving:woodcutting", "dungeondelving:excavation",
            "dungeondelving:farming", "dungeondelving:fishing", "dungeondelving:forging", "dungeondelving:leatherworking", "dungeondelving:weaving",
            "skill_tree_rpgs:class_skills", "skill_tree_rpgs:weapon_skills");

    private SkillLevels() {}

    public static boolean pufferfish() {
        return ModList.get().isLoaded("puffish_skills");
    }

    /** The player's level in each skill that counts, from 0. */
    public static List<Integer> levels(ServerPlayer player) {
        List<Integer> levels = new ArrayList<>();
        if (pufferfish()) {
            for (String id : Config.CHARACTER_LEVEL_SKILLS.get()) {
                ResourceLocation category = ResourceLocation.tryParse(id);
                if (category != null) {
                    PufferfishSkills.level(player, category).ifPresent(levels::add);
                }
            }
        } else {
            for (CraftSkill skill : CraftSkill.values()) {
                levels.add(CraftingSkills.level(player, skill) - 1);
            }
        }
        return levels;
    }

    /**
     * The character level for these skill levels: 1 plus the average of the best {@code characterLevelTopSkills}
     * of them (missing ones count as 0), rounded down.
     */
    public static int characterLevel(List<Integer> levels, int top) {
        int count = Math.max(1, top);
        int sum = levels.stream().sorted(Comparator.reverseOrder()).limit(count).mapToInt(Integer::intValue).sum();
        return 1 + sum / count;
    }

    /** Copies a crafting skill's XP into its Pufferfish category, so the tree's levels and points follow it. */
    public static void mirror(ServerPlayer player, CraftSkill skill, int xp) {
        if (pufferfish()) {
            PufferfishSkills.setTotal(player, DungeonDelving.id(skill.id), xp);
        }
    }
}
