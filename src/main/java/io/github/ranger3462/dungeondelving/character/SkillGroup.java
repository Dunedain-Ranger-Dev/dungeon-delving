package io.github.ranger3462.dungeondelving.character;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** How the skill trees are grouped when listed (in /skills and /character). */
public enum SkillGroup {
    PROFESSIONS("Professions", "mining", "woodcutting", "excavation", "farming", "fishing", "forging", "leatherworking", "weaving"),
    COMBAT("Combat", "one_handed", "two_handed", "polearms", "daggers", "unarmed", "archery", "crossbows", "dual_wielding", "blocking"),
    ARMOR("Armor", "light_armor", "medium_armor", "heavy_armor", "mage_armor"),
    MAGIC("Magic", "fire_magic", "frost_magic", "lightning_magic", "arcane_magic", "holy_magic", "earth_magic", "water_magic", "air_magic",
            "necromancy"),
    OTHER("Other", "beastmastery", "skill_tree_rpgs:class_skills");

    public final String title;
    public final List<ResourceLocation> skills;

    SkillGroup(String title, String... skills) {
        this.title = title;
        this.skills = java.util.Arrays.stream(skills).map(id -> id.contains(":") ? ResourceLocation.parse(id)
                : ResourceLocation.fromNamespaceAndPath(io.github.ranger3462.dungeondelving.DungeonDelving.MODID, id)).toList();
    }

    /** A skill's display name (its tree's title). */
    public static Component name(ResourceLocation skill) {
        String fallback = skill.getPath().replace('_', ' ');
        fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
        return Component.translatableWithFallback("skill_category." + skill.getNamespace() + "." + skill.getPath(), fallback);
    }
}
