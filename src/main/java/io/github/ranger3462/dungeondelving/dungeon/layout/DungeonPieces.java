package io.github.ranger3462.dungeondelving.dungeon.layout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * The structure files available for each kind of dungeon piece, found by file name prefix in
 * {@code data/dungeondelving/structure/dungeon/}: {@code start_}, {@code common_}, {@code objective_}, {@code tunnel_}
 * and {@code cap_}. Placeholder pieces (names ending in {@code _placeholder}) are only used for a kind that has no
 * real pieces yet, so hand-built rooms take over as soon as they are added.
 */
public final class DungeonPieces {
    public enum Kind {
        START("start_"), COMMON("common_"), OBJECTIVE("objective_"), TUNNEL("tunnel_"), CAP("cap_");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    private static final String FOLDER = "structure/dungeon";
    private static final String PLACEHOLDER_SUFFIX = "_placeholder";

    private final Map<Kind, List<ResourceLocation>> pools;

    private DungeonPieces(Map<Kind, List<ResourceLocation>> pools) {
        this.pools = pools;
    }

    /** Scans the loaded data packs for dungeon pieces. */
    public static DungeonPieces load(ResourceManager resources) {
        Map<Kind, List<ResourceLocation>> real = new EnumMap<>(Kind.class);
        Map<Kind, List<ResourceLocation>> placeholders = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            real.put(kind, new ArrayList<>());
            placeholders.put(kind, new ArrayList<>());
        }

        resources.listResources(FOLDER, file -> file.getPath().endsWith(".nbt")).keySet().stream().sorted().forEach(file -> {
            if (!file.getNamespace().equals(DungeonDelving.MODID)) {
                return;
            }
            String name = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".nbt".length());
            ResourceLocation template = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "dungeon/" + name);
            for (Kind kind : Kind.values()) {
                if (name.startsWith(kind.prefix)) {
                    (name.endsWith(PLACEHOLDER_SUFFIX) ? placeholders : real).get(kind).add(template);
                }
            }
        });

        Map<Kind, List<ResourceLocation>> pools = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            List<ResourceLocation> chosen = real.get(kind).isEmpty() ? placeholders.get(kind) : real.get(kind);
            if (chosen.isEmpty()) {
                throw new IllegalStateException("No dungeon pieces found for " + kind + " (expected " + FOLDER + "/" + kind.prefix + "*.nbt)");
            }
            pools.put(kind, List.copyOf(chosen));
        }
        return new DungeonPieces(pools);
    }

    public List<ResourceLocation> pool(Kind kind) {
        return pools.get(kind);
    }

    public ResourceLocation pick(Kind kind, RandomSource random) {
        List<ResourceLocation> pool = pools.get(kind);
        return pool.get(random.nextInt(pool.size()));
    }
}
