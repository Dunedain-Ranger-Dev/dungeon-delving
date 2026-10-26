package io.github.ranger3462.dungeondelving.integration.economycraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.EconomyPaths;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * Dungeon loot in EconomyCraft's shop (DD-93): when the server starts, the sell prices in our
 * {@code data/dungeondelving/economycraft/prices.json} (dungeon metals, shards, goblin weapons, forged gear and the like;
 * sell only) are added to the world's EconomyCraft price list, and it's reloaded. Only items the list doesn't have yet
 * are added, so prices an admin has changed or removed by editing the entry are kept. Only loaded when EconomyCraft is
 * installed (see {@code Bank}).
 */
public final class EconomyCraftPrices {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private EconomyCraftPrices() {}

    /** Adds our missing prices to the world's list and reloads EconomyCraft if any were added. Returns how many. */
    public static int addMissing(MinecraftServer server) {
        Optional<JsonObject> ours = ourPrices(server);
        Path file = EconomyPaths.configDir(server).resolve("prices.json");
        if (ours.isEmpty() || !Files.exists(file)) {
            return 0;
        }
        try {
            JsonObject world = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            int added = merge(world, ours.get());
            if (added > 0) {
                Files.writeString(file, GSON.toJson(world), StandardCharsets.UTF_8);
                EconomyCraft.reloadFromDisk(server);
                DungeonDelving.LOGGER.info("Added {} Dungeon Delving prices to EconomyCraft", added);
            }
            return added;
        } catch (IOException | RuntimeException e) {
            DungeonDelving.LOGGER.warn("Couldn't add Dungeon Delving prices to EconomyCraft's {}", file, e);
            return 0;
        }
    }

    /** Copies entries from {@code ours} that {@code world} doesn't have into it. Returns how many. */
    public static int merge(JsonObject world, JsonObject ours) {
        int added = 0;
        for (Map.Entry<String, JsonElement> entry : ours.entrySet()) {
            if (!world.has(entry.getKey())) {
                world.add(entry.getKey(), entry.getValue().deepCopy());
                added++;
            }
        }
        return added;
    }

    static Optional<JsonObject> ourPrices(MinecraftServer server) {
        Optional<Resource> resource = server.getResourceManager().getResource(DungeonDelving.id("economycraft/prices.json"));
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            DungeonDelving.LOGGER.warn("Couldn't read Dungeon Delving's EconomyCraft prices", e);
            return Optional.empty();
        }
    }
}
