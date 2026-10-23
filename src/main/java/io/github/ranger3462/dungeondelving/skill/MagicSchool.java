package io.github.ranger3462.dungeondelving.skill;

import java.util.Map;
import java.util.Optional;

/**
 * The magic schools (DD-81), each its own skill tree that levels from casting its spells, so a player can master frost
 * and still be a fire apprentice. Spell Engine (the RPG Series) and Iron's Spells both feed them, by their own school
 * ids.
 */
public enum MagicSchool {
    FIRE("fire_magic"),
    FROST("frost_magic"),
    LIGHTNING("lightning_magic"),
    ARCANE("arcane_magic"),
    HOLY("holy_magic"),
    EARTH("earth_magic"),
    WATER("water_magic"),
    AIR("air_magic"),
    /** Soul and eldritch magic, and blood magic too. */
    NECROMANCY("necromancy");

    /** Experience for each spell cast: a base, plus some for its level (Iron's) or for a full cast (Spell Engine). */
    public static final int CAST_XP = 4;

    private static final Map<String, MagicSchool> SCHOOLS = Map.ofEntries(
            Map.entry("spell_power:fire", FIRE), Map.entry("irons_spellbooks:fire", FIRE),
            Map.entry("spell_power:frost", FROST), Map.entry("irons_spellbooks:ice", FROST),
            Map.entry("spell_power:lightning", LIGHTNING), Map.entry("irons_spellbooks:lightning", LIGHTNING),
            Map.entry("spell_power:arcane", ARCANE), Map.entry("irons_spellbooks:ender", ARCANE), Map.entry("irons_spellbooks:evocation", ARCANE),
            Map.entry("spell_power:healing", HOLY), Map.entry("spell_power:holy", HOLY), Map.entry("irons_spellbooks:holy", HOLY),
            Map.entry("spell_power:earth", EARTH), Map.entry("spell_power:nature", EARTH), Map.entry("irons_spellbooks:nature", EARTH),
            Map.entry("spell_power:water", WATER),
            Map.entry("spell_power:air", AIR),
            Map.entry("spell_power:blood", NECROMANCY), Map.entry("irons_spellbooks:blood", NECROMANCY),
            Map.entry("spell_power:soul", NECROMANCY), Map.entry("spell_power:unholy", NECROMANCY), Map.entry("irons_spellbooks:eldritch", NECROMANCY));

    public final String skill;

    MagicSchool(String skill) {
        this.skill = skill;
    }

    /** The school a Spell Engine or Iron's Spells school id belongs to. Empty for martial "schools" (melee, ranged) and the Witcher's signs. */
    public static Optional<MagicSchool> byId(String id) {
        return Optional.ofNullable(SCHOOLS.get(id));
    }
}
