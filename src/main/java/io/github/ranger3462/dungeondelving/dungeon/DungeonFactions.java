package io.github.ranger3462.dungeondelving.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.util.RandomSource;

/**
 * Who holds a dungeon: each run rolls one faction (undead, goblins, ...), and everything in it comes from that
 * faction. Its spawners spawn only the faction's mobs ({@code <faction>Mobs} config lists, see {@link SpawnerMobs}),
 * and its boss is one of the faction's bosses. Mobs get stronger by tier through tougher variants: each tier has its
 * own mobs. A faction can be rolled only if it's enabled ({@code enabledFactions}) and has a boss for the tier; the
 * MVP enables only the undead (goblins come back in Phase 2, see docs/design/factions.md).
 */
public final class DungeonFactions {
    public static final String GOBLINS = "goblins";
    public static final String UNDEAD = "undead";
    public static final String ORCS = "orcs";
    /** The undead's old name, still found in saved runs. */
    private static final String OLD_SKELETONS = "skeletons";

    /**
     * One set of goblins per tier, like the orcs: goblin warriors and archers at tier 1; the same with shamans at 2;
     * hobgoblin soldiers and hunters with pyros at 3; hobgoblin vanguards and spellbreakers with pyros at 4 (the casters
     * need Iron's Spells).
     */
    public static final List<String> GOBLIN_DEFAULTS = List.of(
            "dungeondelving:goblin=60@1-2", "dungeondelving:goblin_archer=40@1-2", "dungeondelving:goblin_shaman=20@2",
            "dungeondelving:hobgoblin_soldier=50@3", "dungeondelving:hobgoblin_hunter=35@3", "dungeondelving:goblin_pyro=20@3-4",
            "dungeondelving:hobgoblin_vanguard=45@4", "dungeondelving:hobgoblin_spellbreaker=40@4");
    /** Earlier goblin defaults: a config still holding one of these exactly was never edited, so it's moved to the new one. */
    public static final List<List<String>> OLD_GOBLIN_DEFAULTS = List.of(
            List.of("dungeondelving:goblin=60", "dungeondelving:goblin_archer=25", "dungeondelving:goblin_shaman=15"),
            List.of("dungeondelving:goblin=60", "dungeondelving:goblin_archer=30", "dungeondelving:goblin_shaman=12@2-4",
                    "dungeondelving:goblin_pyro=12@2-4", "dungeondelving:hobgoblin_soldier=18@3-4", "dungeondelving:hobgoblin_hunter=14@3-4",
                    "dungeondelving:hobgoblin_spellbreaker=10@4", "dungeondelving:hobgoblin_vanguard=10@4"));
    /** One set of undead per tier (the wither skeleton mage needs Iron's Spells, and is skipped without it). */
    public static final List<String> UNDEAD_DEFAULTS = List.of(
            "dungeondelving:skeleton_swordsman=40@1", "minecraft:skeleton=30@1", "minecraft:zombie=30@1",
            "dungeondelving:stray_swordsman=40@2", "minecraft:stray=30@2", "minecraft:husk=30@2",
            "dungeondelving:bogged_swordsman=40@3", "minecraft:bogged=30@3", "minecraft:drowned=30@3",
            "minecraft:wither_skeleton=40@4", "dungeondelving:wither_skeleton_archer=30@4", "dungeondelving:wither_skeleton_mage=20@4");
    /**
     * One set of orcs per tier (DD-74): scrappers and raiders at tier 1; veterans, raiders and doomsayers at 2; veterans,
     * vagabonds and doomsayers at 3; rampagers, vagabonds and shamans at 4 (the casters need Iron's Spells).
     */
    public static final List<String> ORC_DEFAULTS = List.of(
            "dungeondelving:orc_scrapper=55@1", "dungeondelving:orc_raider=45@1-2",
            "dungeondelving:orc_veteran=45@2-3", "dungeondelving:orc_doomsayer=15@2-3",
            "dungeondelving:orc_vagabond=35@3-4", "dungeondelving:orc_rampager=45@4", "dungeondelving:orc_shaman=15@4");
    public static final List<String> ENABLED_DEFAULTS = List.of(UNDEAD);

    private static final Map<String, Supplier<List<? extends String>>> MOBS = new LinkedHashMap<>();

    static {
        MOBS.put(GOBLINS, () -> Config.GOBLIN_MOBS.get()); // Lambdas, not method references: Config may still be loading here.
        MOBS.put(UNDEAD, () -> Config.UNDEAD_MOBS.get());
        MOBS.put(ORCS, () -> Config.ORC_MOBS.get());
    }

    private DungeonFactions() {}

    public static Set<String> ids() {
        return MOBS.keySet();
    }

    /** A faction id as saved, with old names brought up to date. */
    public static String current(String faction) {
        return OLD_SKELETONS.equals(faction) ? UNDEAD : faction;
    }

    /** The mob entries a faction's spawners pick from; empty for an unknown faction. */
    public static List<? extends String> mobs(String faction) {
        Supplier<List<? extends String>> mobs = faction == null ? null : MOBS.get(current(faction));
        return mobs == null ? List.of() : mobs.get();
    }

    /** Rolls the faction holding a dungeon of this tier: any enabled faction with a boss for the tier, or the undead if none. */
    public static String pick(int tier, RandomSource random) {
        return pick(tier, random, Config.ENABLED_FACTIONS.get());
    }

    public static String pick(int tier, RandomSource random, List<? extends String> enabled) {
        List<String> eligible = MOBS.keySet().stream().filter(enabled::contains).filter(faction -> DungeonBosses.guards(faction, tier)).toList();
        return eligible.isEmpty() ? UNDEAD : eligible.get(random.nextInt(eligible.size()));
    }
}
