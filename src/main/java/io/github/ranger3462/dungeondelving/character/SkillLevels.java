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
 * A player's skill levels, for the character level (DD-80). With Pufferfish's Skills these are its categories (our
 * professions, combat styles, armor, magic schools and Beastmastery, and the RPG Series' class tree), counting from
 * 0; the crafting skills are mirrored into their categories. Without it, only the crafting skills count. The character level averages the best few skills,
 * RuneScape-style, so a crafter levels as well as a fighter.
 */
public final class SkillLevels {
    /** Skills that count toward the character level by default: all of ours, and the RPG Series' class tree for now. */
    public static final List<String> DEFAULT_SKILLS = List.of(
            // Professions (DD-80)
            "dungeondelving:mining", "dungeondelving:woodcutting", "dungeondelving:excavation", "dungeondelving:farming", "dungeondelving:fishing",
            "dungeondelving:forging", "dungeondelving:leatherworking", "dungeondelving:weaving",
            // Combat styles, armor, magic schools and Beastmastery (DD-81)
            "dungeondelving:one_handed", "dungeondelving:two_handed", "dungeondelving:polearms", "dungeondelving:daggers", "dungeondelving:unarmed",
            "dungeondelving:archery", "dungeondelving:crossbows", "dungeondelving:dual_wielding", "dungeondelving:blocking",
            "dungeondelving:light_armor", "dungeondelving:medium_armor", "dungeondelving:heavy_armor", "dungeondelving:mage_armor",
            "dungeondelving:fire_magic", "dungeondelving:frost_magic", "dungeondelving:lightning_magic", "dungeondelving:arcane_magic",
            "dungeondelving:holy_magic", "dungeondelving:earth_magic", "dungeondelving:water_magic", "dungeondelving:air_magic",
            "dungeondelving:necromancy", "dungeondelving:beastmastery",
            "skill_tree_rpgs:class_skills");
    /** Earlier defaults: a config still holding one of these was never edited, so it moves to the new list. */
    public static final List<List<String>> OLD_DEFAULT_SKILLS = List.of(List.of("dungeondelving:mining", "dungeondelving:woodcutting",
            "dungeondelving:excavation", "dungeondelving:farming", "dungeondelving:fishing", "dungeondelving:forging", "dungeondelving:leatherworking",
            "dungeondelving:weaving", "skill_tree_rpgs:class_skills", "skill_tree_rpgs:weapon_skills"),
            // Before Blood Magic was folded into Necromancy.
            List.of("dungeondelving:mining", "dungeondelving:woodcutting", "dungeondelving:excavation", "dungeondelving:farming",
                    "dungeondelving:fishing", "dungeondelving:forging", "dungeondelving:leatherworking", "dungeondelving:weaving",
                    "dungeondelving:one_handed", "dungeondelving:two_handed", "dungeondelving:polearms", "dungeondelving:daggers",
                    "dungeondelving:unarmed", "dungeondelving:archery", "dungeondelving:crossbows", "dungeondelving:dual_wielding",
                    "dungeondelving:blocking", "dungeondelving:light_armor", "dungeondelving:medium_armor", "dungeondelving:heavy_armor",
                    "dungeondelving:mage_armor", "dungeondelving:fire_magic", "dungeondelving:frost_magic", "dungeondelving:lightning_magic",
                    "dungeondelving:arcane_magic", "dungeondelving:holy_magic", "dungeondelving:earth_magic", "dungeondelving:water_magic",
                    "dungeondelving:air_magic", "dungeondelving:blood_magic", "dungeondelving:necromancy", "dungeondelving:beastmastery",
                    "skill_tree_rpgs:class_skills"));

    private SkillLevels() {}

    public static boolean pufferfish() {
        return ModList.get().isLoaded("puffish_skills");
    }

    /** The player's level in each counting skill (from 0), by skill id. Pufferfish's Skills only; empty without it. */
    public static java.util.Map<ResourceLocation, Integer> levelsById(ServerPlayer player) {
        java.util.Map<ResourceLocation, Integer> levels = new java.util.LinkedHashMap<>();
        if (pufferfish()) {
            for (String id : Config.CHARACTER_LEVEL_SKILLS.get()) {
                ResourceLocation category = ResourceLocation.tryParse(id);
                if (category != null) {
                    PufferfishSkills.level(player, category).ifPresent(level -> levels.put(category, level));
                }
            }
        }
        return levels;
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
