package io.github.ranger3462.dungeondelving.character;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What's saved on each player for their character: the points they've put into each ability score, bonus points
 * from items, the highest character level they've been told about (so a level-up is only announced once) (DD-80),
 * and their race, empty until they pick one (DD-82). Points earned aren't saved: they're worked out from the
 * character level, so they can't drift.
 */
public record CharacterData(Map<String, Integer> allocated, int bonusPoints, int announcedLevel, String race) {
    public static final CharacterData EMPTY = new CharacterData(Map.of(), 0, 1, "");

    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("allocated").forGetter(CharacterData::allocated),
            Codec.INT.fieldOf("bonus_points").forGetter(CharacterData::bonusPoints),
            Codec.INT.fieldOf("announced_level").forGetter(CharacterData::announcedLevel),
            // Characters saved before races (DD-82) have none.
            Codec.STRING.optionalFieldOf("race", "").forGetter(CharacterData::race)).apply(instance, CharacterData::new));

    public int allocated(AbilityScore score) {
        return allocated.getOrDefault(score.id, 0);
    }

    public int spent() {
        return allocated.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** The player's race, or empty if they haven't picked one yet. */
    public Optional<Race> raceOf() {
        return Race.byId(race);
    }

    public CharacterData withAllocated(AbilityScore score, int points) {
        Map<String, Integer> next = new HashMap<>(allocated);
        next.put(score.id, Math.max(0, points));
        return new CharacterData(Map.copyOf(next), bonusPoints, announcedLevel, race);
    }

    public CharacterData withBonusPoints(int points) {
        return new CharacterData(allocated, Math.max(0, points), announcedLevel, race);
    }

    public CharacterData withAnnouncedLevel(int level) {
        return new CharacterData(allocated, bonusPoints, level, race);
    }

    /** A race, or none ("") to pick again. */
    public CharacterData withRace(String race) {
        return new CharacterData(allocated, bonusPoints, announcedLevel, race);
    }

    /** Every allocated point taken back (a respec). Bonus points stay. */
    public CharacterData respec() {
        return new CharacterData(Map.of(), bonusPoints, announcedLevel, race);
    }
}
