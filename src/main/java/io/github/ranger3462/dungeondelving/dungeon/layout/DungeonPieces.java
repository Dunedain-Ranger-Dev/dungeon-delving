package io.github.ranger3462.dungeondelving.dungeon.layout;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The structure files available for each kind of dungeon piece, found by file name prefix: {@code start_},
 * {@code common_}, {@code objective_}, {@code tunnel_} and {@code cap_}.
 * <p>
 * Pieces come from {@code data/dungeondelving/structure/dungeon/} in the mod and data packs, plus any exported into
 * the current world with {@code /dungeondelving room export}. Placeholder pieces (names ending in
 * {@code _placeholder}) are only used for a kind that has no real pieces yet, so hand-built rooms take over as soon
 * as they are added.
 */
public final class DungeonPieces {
    public enum Kind {
        START("start_"), COMMON("common_"), OBJECTIVE("objective_"), TUNNEL("tunnel_"), CAP("cap_");

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
                case START, COMMON, OBJECTIVE -> new Vec3i(ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
                case TUNNEL -> new Vec3i(TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
                case CAP -> new Vec3i(WALL_LENGTH, WALL_HEIGHT, WALL_THICKNESS);
            };
        }

        public boolean isRoom() {
            return this == START || this == COMMON || this == OBJECTIVE;
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
    private final Map<Kind, List<ResourceLocation>> pools;

    private DungeonPieces(Map<ResourceLocation, Kind> all, Map<Kind, List<ResourceLocation>> pools) {
        this.all = all;
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
        all.forEach((template, kind) -> (template.getPath().endsWith(PLACEHOLDER_SUFFIX) ? placeholders : real).get(kind).add(template));

        Map<Kind, List<ResourceLocation>> pools = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            List<ResourceLocation> chosen = real.get(kind).isEmpty() ? placeholders.get(kind) : real.get(kind);
            if (chosen.isEmpty()) {
                throw new IllegalStateException("No dungeon pieces found for " + kind + " (expected " + RESOURCE_FOLDER + "/" + kind.prefix + "*.nbt)");
            }
            pools.put(kind, List.copyOf(chosen));
        }
        return new DungeonPieces(all, pools);
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

    /** Every piece found, placeholders included, with its kind. */
    public Map<ResourceLocation, Kind> all() {
        return all;
    }

    /** The pieces generation picks from for a kind: real pieces if there are any, otherwise placeholders. */
    public List<ResourceLocation> pool(Kind kind) {
        return pools.get(kind);
    }

    public ResourceLocation pick(Kind kind, RandomSource random) {
        List<ResourceLocation> pool = pools.get(kind);
        return pool.get(random.nextInt(pool.size()));
    }
}
