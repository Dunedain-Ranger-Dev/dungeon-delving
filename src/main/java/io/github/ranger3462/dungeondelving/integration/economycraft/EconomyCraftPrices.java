package io.github.ranger3462.dungeondelving.integration.economycraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.EconomyPaths;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * Dungeon loot in EconomyCraft's shop (DD-93): when the server starts, the sell prices in our
 * {@code data/dungeondelving/economycraft/prices.json} (dungeon metals, shards, goblin weapons, forged gear and the like;
 * sell only) are added to the world's EconomyCraft price list, and it's reloaded. EconomyCraft lists every item at
 * price 0 on its own, so an item is filled in when it's missing or still at 0 to buy and sell, once: the items we've
 * filled are remembered in {@code dungeondelving-prices.json} beside the list, so a price an admin has changed later,
 * zeroed included, is kept. Only loaded when EconomyCraft is installed (see {@code Bank}).
 */
public final class EconomyCraftPrices {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private EconomyCraftPrices() {}

    /** Adds our missing prices to the world's list and reloads EconomyCraft if any were added. Returns how many. */
    public static int addMissing(MinecraftServer server) {
        Optional<JsonObject> ours = ourPrices(server);
        Path file = EconomyPaths.configDir(server).resolve("prices.json");
        Path filledFile = EconomyPaths.configDir(server).resolve("dungeondelving-prices.json");
        if (ours.isEmpty() || !Files.exists(file)) {
            return 0;
        }
        try {
            JsonObject world = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> filled = new HashSet<>();
            if (Files.exists(filledFile)) {
                JsonParser.parseString(Files.readString(filledFile, StandardCharsets.UTF_8)).getAsJsonArray()
                        .forEach(id -> filled.add(id.getAsString()));
            }
            int added = merge(world, ours.get(), filled);
            if (added > 0) {
                Files.writeString(file, GSON.toJson(world), StandardCharsets.UTF_8);
                JsonArray list = new JsonArray();
                filled.stream().sorted().forEach(list::add);
                Files.writeString(filledFile, GSON.toJson(list), StandardCharsets.UTF_8);
                EconomyCraft.reloadFromDisk(server);
                DungeonDelving.LOGGER.info("Added {} Dungeon Delving prices to EconomyCraft", added);
            }
            return added;
        } catch (IOException | RuntimeException e) {
            DungeonDelving.LOGGER.warn("Couldn't add Dungeon Delving prices to EconomyCraft's {}", file, e);
            return 0;
        }
    }

    /**
     * Copies entries from {@code ours} into {@code world} where it has none, or only EconomyCraft's price-0 placeholder
     * and we haven't filled that item before. Filled items are added to {@code filled}. Returns how many.
     */
    public static int merge(JsonObject world, JsonObject ours, Set<String> filled) {
        int added = 0;
        for (Map.Entry<String, JsonElement> entry : ours.entrySet()) {
            String id = entry.getKey();
            if (!world.has(id) || (!filled.contains(id) && unpriced(world.get(id)))) {
                world.add(id, entry.getValue().deepCopy());
                filled.add(id);
                added++;
            }
        }
        return added;
    }

    /** Whether an entry is free to buy and sell, as EconomyCraft lists items it has no price for. */
    private static boolean unpriced(JsonElement entry) {
        if (!entry.isJsonObject()) {
            return false;
        }
        JsonObject price = entry.getAsJsonObject();
        return price.has("unit_buy") && price.get("unit_buy").getAsLong() == 0 && price.has("unit_sell") && price.get("unit_sell").getAsLong() == 0;
    }

    static Optional<JsonObject> ourPrices(MinecraftServer server) {
        Optional<Resource> resource = server.getResourceManager().getResource(DungeonDelving.id("economycraft/prices.json"));
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject prices = JsonParser.parseReader(reader).getAsJsonObject();
            // The list prices other mods' items too (Professions and Skills' materials and gear); leave out any not installed.
            prices.keySet().removeIf(id -> !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)));
            return Optional.of(prices);
        } catch (IOException | RuntimeException e) {
            DungeonDelving.LOGGER.warn("Couldn't read Dungeon Delving's EconomyCraft prices", e);
            return Optional.empty();
        }
    }
}
