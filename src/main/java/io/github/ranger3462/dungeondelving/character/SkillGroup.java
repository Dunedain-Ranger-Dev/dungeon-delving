package io.github.ranger3462.dungeondelving.character;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** How the skill trees are grouped when listed (in /skills and /character), and each one's icon. */
public enum SkillGroup {
    PROFESSIONS("Professions", "mining", "woodcutting", "excavation", "farming", "fishing", "forging", "leatherworking", "weaving"),
    COMBAT("Combat", "one_handed", "two_handed", "polearms", "daggers", "unarmed", "archery", "crossbows", "dual_wielding", "blocking"),
    ARMOR("Armor", "light_armor", "medium_armor", "heavy_armor", "mage_armor"),
    MAGIC("Magic", "fire_magic", "frost_magic", "lightning_magic", "arcane_magic", "holy_magic", "earth_magic", "water_magic", "air_magic",
            "necromancy"),
    CLASS("Class", "fighter", "archer", "rogue", "mage"),
    OTHER("Other", "beastmastery");

    /** Each skill's icon, the same as its tree's tab (tools/skill_trees.py): an item id, or "texture:" and a texture. */
    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("mining", "minecraft:iron_pickaxe"), Map.entry("woodcutting", "minecraft:iron_axe"), Map.entry("excavation", "minecraft:iron_shovel"),
            Map.entry("farming", "minecraft:iron_hoe"), Map.entry("fishing", "minecraft:fishing_rod"), Map.entry("forging", "dungeondelving:master_anvil"),
            Map.entry("leatherworking", "minecraft:leather"), Map.entry("weaving", "minecraft:string"), Map.entry("one_handed", "minecraft:iron_sword"),
            Map.entry("two_handed", "minecraft:netherite_axe"), Map.entry("polearms", "minecraft:trident"), Map.entry("daggers", "rogues:iron_dagger"),
            Map.entry("unarmed", "minecraft:leather"), Map.entry("archery", "minecraft:bow"), Map.entry("crossbows", "minecraft:crossbow"),
            Map.entry("dual_wielding", "texture:dungeondelving:textures/gui/skill_icons/dual_wielding.png"), Map.entry("blocking", "minecraft:shield"), Map.entry("light_armor", "minecraft:leather_chestplate"),
            Map.entry("medium_armor", "minecraft:chainmail_chestplate"), Map.entry("heavy_armor", "minecraft:iron_chestplate"),
            Map.entry("mage_armor", "minecraft:purple_wool"), Map.entry("fire_magic", "minecraft:blaze_powder"), Map.entry("frost_magic", "minecraft:blue_ice"),
            Map.entry("lightning_magic", "minecraft:lightning_rod"), Map.entry("arcane_magic", "minecraft:amethyst_shard"),
            Map.entry("holy_magic", "minecraft:golden_apple"), Map.entry("earth_magic", "minecraft:moss_block"), Map.entry("water_magic", "minecraft:heart_of_the_sea"),
            Map.entry("air_magic", "minecraft:feather"), Map.entry("necromancy", "minecraft:wither_skeleton_skull"), Map.entry("beastmastery", "minecraft:lead"),
            Map.entry("fighter", "rogues:berserker_armor_head"), Map.entry("archer", "archers:small_quiver"),
            Map.entry("rogue", "rogues:assassin_armor_head"), Map.entry("mage", "elemental_wizards_rpg:elemental_head"));

    public final String title;
    public final List<ResourceLocation> skills;

    SkillGroup(String title, String... skills) {
        this.title = title;
        this.skills = Arrays.stream(skills).map(id -> id.contains(":") ? ResourceLocation.parse(id) : DungeonDelving.id(id)).toList();
    }

    /** Every listed skill, in order. */
    public static List<ResourceLocation> all() {
        return Arrays.stream(values()).flatMap(group -> group.skills.stream()).toList();
    }

    /** A skill's display name (its tree's title). */
    public static Component name(ResourceLocation skill) {
        String fallback = skill.getPath().replace('_', ' ');
        fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
        return Component.translatableWithFallback("skill_category." + skill.getNamespace() + "." + skill.getPath(), fallback);
    }

    /** A skill's icon item id (a book if it has none listed, or it's a texture icon). */
    public static ResourceLocation icon(ResourceLocation skill) {
        String icon = ICONS.getOrDefault(skill.getPath(), "minecraft:book");
        return ResourceLocation.parse(icon.startsWith("texture:") ? "minecraft:book" : icon);
    }

    /** A skill's icon texture, if its icon is a texture rather than an item. */
    public static java.util.Optional<ResourceLocation> iconTexture(ResourceLocation skill) {
        String icon = ICONS.getOrDefault(skill.getPath(), "");
        return icon.startsWith("texture:") ? java.util.Optional.of(ResourceLocation.parse(icon.substring("texture:".length()))) : java.util.Optional.empty();
    }
}
