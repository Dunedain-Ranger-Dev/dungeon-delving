package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * Which mobs dungeon spawners spawn, and how often: the {@code spawnerMobs} config, a list of {@code "mob=weight"}
 * entries. A mob can be an entity id ({@code dungeondelving:goblin}) or an entity tag ({@code #dungeondelving:dungeon_mobs},
 * any mob in it). Entries for mobs that aren't installed (e.g. the goblin shaman without Iron's Spells) are skipped.
 */
public final class SpawnerMobs {
    public static final List<String> DEFAULTS = List.of("dungeondelving:goblin=60", "dungeondelving:goblin_archer=25", "dungeondelving:goblin_shaman=15");

    /** One parsed entry: an entity id or tag, and its weight. */
    public record Entry(ResourceLocation id, boolean tag, int weight) {}

    private SpawnerMobs() {}

    /** Reads one {@code "mob=weight"} entry (weight defaults to 1). Empty if it isn't valid. */
    public static Optional<Entry> parse(String text) {
        String[] parts = text.trim().split("\\s*=\\s*");
        if (parts.length < 1 || parts.length > 2 || parts[0].isEmpty()) {
            return Optional.empty();
        }
        boolean tag = parts[0].startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? parts[0].substring(1) : parts[0]);
        if (id == null) {
            return Optional.empty();
        }
        try {
            int weight = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
            return weight >= 0 ? Optional.of(new Entry(id, tag, weight)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** A random spawner mob from the config. */
    public static Optional<Holder<EntityType<?>>> pick(ServerLevel level, RandomSource random) {
        return pick(level, random, Config.SPAWNER_MOBS.get());
    }

    /**
     * A random mob from a list of entries, by weight. Falls back to the {@code dungeondelving:dungeon_mobs} tag if none
     * of the entries can spawn anything.
     */
    public static Optional<Holder<EntityType<?>>> pick(ServerLevel level, RandomSource random, List<? extends String> entries) {
        Registry<EntityType<?>> types = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        List<List<Holder<EntityType<?>>>> choices = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (String text : entries) {
            Optional<Entry> entry = parse(text);
            if (entry.isEmpty() || entry.get().weight() == 0) {
                continue;
            }
            List<Holder<EntityType<?>>> mobs = new ArrayList<>();
            if (entry.get().tag()) {
                types.getTag(TagKey.create(Registries.ENTITY_TYPE, entry.get().id())).ifPresent(tag -> tag.forEach(mobs::add));
            } else {
                types.getHolder(entry.get().id()).ifPresent(mobs::add);
            }
            if (!mobs.isEmpty()) {
                choices.add(mobs);
                weights.add(entry.get().weight());
                total += entry.get().weight();
            }
        }
        if (total == 0) {
            return types.getTag(DungeonMarkers.DUNGEON_MOBS).flatMap(tag -> tag.getRandomElement(random));
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < choices.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                List<Holder<EntityType<?>>> mobs = choices.get(i);
                return Optional.of(mobs.get(random.nextInt(mobs.size())));
            }
        }
        return Optional.empty();
    }
}
