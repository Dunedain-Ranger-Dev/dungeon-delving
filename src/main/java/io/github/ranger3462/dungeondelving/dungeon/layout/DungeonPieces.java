package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The structure files available for each kind of dungeon piece, found by file name prefix: {@code start_},
 * {@code common_}, {@code quarry_}, {@code treasure_}, {@code challenge_}, {@code boss_}, {@code tunnel_} and {@code cap_}.
 * <p>
 * Pieces come from {@code data/dungeondelving/structure/dungeon/} in the mod and data packs, plus any exported into
 * the current world with {@code /dungeondelving room export}. Placeholder pieces (names ending in
 * {@code _placeholder}) are only used for a kind that has no real pieces yet, so hand-built rooms take over as soon
 * as they are added. Pieces whose size doesn't match the current spec (for example, built before a spec change) are
 * skipped with a warning.
 */
public final class DungeonPieces {
    public enum Kind {
        START("start_"), COMMON("common_"), QUARRY("quarry_"), TREASURE("treasure_"), CHALLENGE("challenge_"), BOSS("boss_"), TUNNEL("tunnel_"), CAP("cap_");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }

        /** The exact size a piece of this kind must be, before any turning. */
        public Vec3i size() {
            return switch (this) {
                case START, COMMON, QUARRY, TREASURE, CHALLENGE, BOSS -> new Vec3i(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
                case TUNNEL -> new Vec3i(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
                case CAP -> new Vec3i(CAP_WIDTH, CAP_HEIGHT, CAP_DEPTH);
            };
        }

        public boolean isRoom() {
            return size().equals(new Vec3i(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE));
        }

        /** The kind a piece belongs to, from its file name. */
        public static Optional<Kind> of(String fileName) {
            for (Kind kind : values()) {
                if (fileName.startsWith(kind.prefix)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** Where dungeon pieces live, inside a namespace's {@code structure} folder. */
    public static final String FOLDER = "dungeon";
    private static final String RESOURCE_FOLDER = "structure/" + FOLDER;
    private static final String PLACEHOLDER_SUFFIX = "_placeholder";

    private final Map<ResourceLocation, Kind> all;
    private final Map<ResourceLocation, String> skipped;
    private final Map<Kind, List<ResourceLocation>> pools;

    private DungeonPieces(Map<ResourceLocation, Kind> all, Map<ResourceLocation, String> skipped, Map<Kind, List<ResourceLocation>> pools) {
        this.all = all;
        this.skipped = skipped;
        this.pools = pools;
    }

    /** The template id for a piece file name, e.g. {@code common_hall} becomes {@code dungeondelving:dungeon/common_hall}. */
    public static ResourceLocation id(String fileName) {
        return DungeonDelving.id(FOLDER + "/" + fileName);
    }

    /** Finds every dungeon piece in the loaded data packs and in this world's exported pieces. */
    public static DungeonPieces load(MinecraftServer server) {
        Map<ResourceLocation, Kind> all = new TreeMap<>();
        server.getResourceManager().listResources(RESOURCE_FOLDER, file -> file.getPath().endsWith(".nbt")).keySet().forEach(file -> {
            if (file.getNamespace().equals(DungeonDelving.MODID)) {
                String name = file.getPath().substring(RESOURCE_FOLDER.length() + 1, file.getPath().length() - ".nbt".length());
                Kind.of(name).ifPresent(kind -> all.put(id(name), kind));
            }
        });
        exportedNames(server).forEach(name -> Kind.of(name).ifPresent(kind -> all.put(id(name), kind)));

        Map<Kind, List<ResourceLocation>> real = new EnumMap<>(Kind.class);
        Map<Kind, List<ResourceLocation>> placeholders = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            real.put(kind, new ArrayList<>());
            placeholders.put(kind, new ArrayList<>());
        }
        Map<ResourceLocation, String> skipped = new TreeMap<>();
        all.forEach((template, kind) -> {
            Optional<Vec3i> size = server.getStructureManager().get(template).map(t -> t.getSize());
            if (size.isEmpty() || !size.get().equals(kind.size())) {
                String reason = size.map(s -> "is " + sizeText(s) + " but " + kind + " pieces must be " + sizeText(kind.size()))
                        .orElse("couldn't be loaded");
                skipped.put(template, reason);
                DungeonDelving.LOGGER.warn("Skipping dungeon piece {}: it {}", template, reason);
                return;
            }
            (template.getPath().endsWith(PLACEHOLDER_SUFFIX) ? placeholders : real).get(kind).add(template);
        });

        Map<Kind, List<ResourceLocation>> pools = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            List<ResourceLocation> chosen = real.get(kind).isEmpty() ? placeholders.get(kind) : real.get(kind);
            if (chosen.isEmpty()) {
                throw new IllegalStateException("No dungeon pieces found for " + kind + " (expected " + RESOURCE_FOLDER + "/" + kind.prefix + "*.nbt)");
            }
            pools.put(kind, List.copyOf(chosen));
        }
        return new DungeonPieces(all, skipped, pools);
    }

    private static String sizeText(Vec3i size) {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    /** The folder {@code /dungeondelving room export} saves pieces to in the current world. */
    public static Path exportFolder(MinecraftServer server) {
        return server.getStructureManager().createAndValidatePathToGeneratedStructure(id("x"), ".nbt").getParent();
    }

    private static List<String> exportedNames(MinecraftServer server) {
        Path folder = exportFolder(server);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".nbt"))
                    .map(name -> name.substring(0, name.length() - ".nbt".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to list exported dungeon pieces in {}", folder, e);
            return List.of();
        }
    }

    /** Every piece found, placeholders and skipped pieces included, with its kind. */
    public Map<ResourceLocation, Kind> all() {
        return all;
    }

    /** Pieces left out of generation because they don't match the spec size, with the reason. */
    public Map<ResourceLocation, String> skipped() {
        return skipped;
    }

    /** The pieces generation picks from for a kind: real pieces if there are any, otherwise placeholders. */
    public List<ResourceLocation> pool(Kind kind) {
        return pools.get(kind);
    }

    /**
     * A boss room for a boss: one of its own arenas ({@code boss_<boss>_*}) if there are any, otherwise any boss room
     * that isn't another boss's arena.
     */
    public ResourceLocation pickBoss(String boss, Collection<String> bosses, RandomSource random) {
        List<ResourceLocation> pool = pools.get(Kind.BOSS);
        List<ResourceLocation> own = pool.stream().filter(id -> belongsTo(id, boss)).toList();
        List<ResourceLocation> shared = pool.stream().filter(id -> bosses.stream().noneMatch(other -> belongsTo(id, other))).toList();
        List<ResourceLocation> choices = !own.isEmpty() ? own : !shared.isEmpty() ? shared : pool;
        return choices.get(random.nextInt(choices.size()));
    }

    private static boolean belongsTo(ResourceLocation piece, String boss) {
        return piece.getPath().startsWith(FOLDER + "/" + Kind.BOSS.prefix() + boss + "_");
    }

    public ResourceLocation pick(Kind kind, RandomSource random) {
        List<ResourceLocation> pool = pools.get(kind);
        return pool.get(random.nextInt(pool.size()));
    }

    /** A piece of this kind, weighted (e.g. by a run's dungeon stones); falls back to an even pick if every weight is 0. */
    public ResourceLocation pick(Kind kind, RandomSource random, ToDoubleFunction<ResourceLocation> weight) {
        List<ResourceLocation> pool = pools.get(kind);
        double total = pool.stream().mapToDouble(weight).sum();
        if (total <= 0) {
            return pick(kind, random);
        }
        double roll = random.nextDouble() * total;
        for (ResourceLocation piece : pool) {
            roll -= weight.applyAsDouble(piece);
            if (roll < 0) {
                return piece;
            }
        }
        return pool.getLast();
    }
}
