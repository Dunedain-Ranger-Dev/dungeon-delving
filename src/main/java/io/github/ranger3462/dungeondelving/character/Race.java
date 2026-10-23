package io.github.ranger3462.dungeondelving.character;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

/**
 * The races (DD-82), D&D-flavoured: each gives ability score bonuses, a size, and a trait or two. Picked on first
 * join; changing later takes an Elixir of Rebirth. Passive traits are attribute changes ({@link #traits}); the rest
 * (a dwarf's mining, a half-orc's last stand, a dragonborn's breath) are handled in {@link RaceTraits}.
 */
public enum Race {
    HUMAN("human", Map.of(AbilityScore.STRENGTH, 1, AbilityScore.DEXTERITY, 1, AbilityScore.CONSTITUTION, 1, AbilityScore.INTELLIGENCE, 1,
            AbilityScore.WISDOM, 1, AbilityScore.CHARISMA, 1), 0.0),
    DWARF("dwarf", Map.of(AbilityScore.CONSTITUTION, 2, AbilityScore.STRENGTH, 1), -0.15),
    ELF("elf", Map.of(AbilityScore.DEXTERITY, 2, AbilityScore.INTELLIGENCE, 1), 0.0,
            new Trait("minecraft:generic.safe_fall_distance", 2.0, Operation.ADD_VALUE),
            new Trait("ranged_weapon:velocity", 0.1, Operation.ADD_MULTIPLIED_BASE)),
    HALFLING("halfling", Map.of(AbilityScore.DEXTERITY, 2, AbilityScore.CHARISMA, 1), -0.3,
            new Trait("minecraft:generic.luck", 1.0, Operation.ADD_VALUE)),
    HALF_ORC("half_orc", Map.of(AbilityScore.STRENGTH, 2, AbilityScore.CONSTITUTION, 1), 0.08),
    GNOME("gnome", Map.of(AbilityScore.INTELLIGENCE, 2, AbilityScore.WISDOM, 1), -0.3,
            new Trait("irons_spellbooks:cooldown_reduction", 0.05, Operation.ADD_VALUE),
            new Trait("irons_spellbooks:cast_time_reduction", 0.05, Operation.ADD_VALUE)),
    TIEFLING("tiefling", Map.of(AbilityScore.CHARISMA, 2, AbilityScore.INTELLIGENCE, 1), 0.0,
            new Trait("minecraft:generic.burning_time", -0.5, Operation.ADD_MULTIPLIED_BASE),
            new Trait("irons_spellbooks:fire_magic_resist", 0.15, Operation.ADD_VALUE)),
    DRAGONBORN("dragonborn", Map.of(AbilityScore.STRENGTH, 2, AbilityScore.CHARISMA, 1), 0.1);

    /** A passive trait: an amount added to another attribute (skipped if that mod isn't installed). */
    public record Trait(String attribute, double amount, Operation operation) {}

    public final String id;
    public final Map<AbilityScore, Integer> scores;
    /** Added to the player's size (minecraft:generic.scale), as a fraction of normal: -0.3 is 30% smaller. */
    public final double scale;
    public final List<Trait> traits;

    Race(String id, Map<AbilityScore, Integer> scores, double scale, Trait... traits) {
        this.id = id;
        this.scores = scores;
        this.scale = scale;
        this.traits = List.of(traits);
    }

    public int bonus(AbilityScore score) {
        return scores.getOrDefault(score, 0);
    }

    public Component displayName() {
        return Component.translatable("race.dungeondelving." + id);
    }

    /** What the race's traits do, for the race screen. */
    public Component description() {
        return Component.translatable("race.dungeondelving." + id + ".description");
    }

    public static Optional<Race> byId(String id) {
        return Arrays.stream(values()).filter(race -> race.id.equals(id)).findFirst();
    }
}
