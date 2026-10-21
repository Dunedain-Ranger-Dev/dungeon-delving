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
 * Who holds a dungeon: each run rolls one faction (goblins, skeletons, ...), and everything in it comes from that
 * faction. Its spawners spawn only the faction's mobs ({@code <faction>Mobs} config lists, see {@link SpawnerMobs}),
 * and its boss is one of the faction's bosses. A faction can be rolled for a tier only if it has a boss for that tier.
 */
public final class DungeonFactions {
    public static final String GOBLINS = "goblins";
    public static final String SKELETONS = "skeletons";

    public static final List<String> GOBLIN_DEFAULTS = List.of("dungeondelving:goblin=60", "dungeondelving:goblin_archer=25",
            "dungeondelving:goblin_shaman=15");
    public static final List<String> SKELETON_DEFAULTS = List.of("dungeondelving:skeleton_swordsman=50", "minecraft:skeleton=35",
            "minecraft:stray=15@2-4", "minecraft:bogged=15@2-4", "dungeondelving:skeleton_knight=15@3-4", "minecraft:wither_skeleton=15@3-4");

    private static final Map<String, Supplier<List<? extends String>>> MOBS = new LinkedHashMap<>();

    static {
        MOBS.put(GOBLINS, () -> Config.GOBLIN_MOBS.get()); // Lambdas, not method references: Config may still be loading here.
        MOBS.put(SKELETONS, () -> Config.SKELETON_MOBS.get());
    }

    private DungeonFactions() {}

    public static Set<String> ids() {
        return MOBS.keySet();
    }

    /** The mob entries a faction's spawners pick from; empty for an unknown faction. */
    public static List<? extends String> mobs(String faction) {
        Supplier<List<? extends String>> mobs = faction == null ? null : MOBS.get(faction);
        return mobs == null ? List.of() : mobs.get();
    }

    /** Rolls the faction holding a dungeon of this tier: any faction with a boss for the tier, or skeletons if none has. */
    public static String pick(int tier, RandomSource random) {
        List<String> eligible = MOBS.keySet().stream().filter(faction -> DungeonBosses.guards(faction, tier)).toList();
        return eligible.isEmpty() ? SKELETONS : eligible.get(random.nextInt(eligible.size()));
    }
}
