package io.github.ranger3462.dungeondelving.integration.ftbchunks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import dev.ftb.mods.ftbchunks.api.client.waypoint.WaypointManager;
import io.github.ranger3462.dungeondelving.client.RealmMapClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The realm's towns, strongholds, capitals and sites as FTB Chunks waypoints (client only; only loaded with FTB Chunks
 * installed). Ours are named with {@link #PREFIX}, so each update clears the old ones before adding the new, even ones
 * left from an earlier session; they're only rebuilt when a place, its name or its owner changes.
 */
public final class RealmWaypoints {
    public static final String PREFIX = "[Realm] ";
    private static final Set<String> PLACES = Set.of("town", "stronghold", "capital", "site");
    /** Each dimension's places as last put on its map. */
    private static final Map<String, String> APPLIED = new HashMap<>();

    private RealmWaypoints() {}

    /** Brings the waypoints up to date. False if some dimension's map isn't ready yet, to try again later. */
    public static boolean update(RealmMapClient.Data data) {
        Map<String, List<RealmMapClient.Node>> byDim = new LinkedHashMap<>();
        byDim.put(Level.OVERWORLD.location().toString(), new ArrayList<>());
        APPLIED.keySet().forEach(dim -> byDim.put(dim, new ArrayList<>()));
        data.nodes().values().stream().filter(node -> PLACES.contains(node.type()))
                .forEach(node -> byDim.computeIfAbsent(node.dim(), dim -> new ArrayList<>()).add(node));
        boolean done = true;
        for (Map.Entry<String, List<RealmMapClient.Node>> entry : byDim.entrySet()) {
            StringBuilder signature = new StringBuilder();
            entry.getValue().forEach(node -> signature.append(node.name()).append(node.owner()).append(node.x()).append(',').append(node.y()).append(',')
                    .append(node.z()).append(';'));
            if (signature.toString().equals(APPLIED.get(entry.getKey()))) {
                continue;
            }
            Optional<WaypointManager> manager = manager(entry.getKey());
            if (manager.isEmpty()) {
                done = false;
                continue;
            }
            List<Waypoint> ours = new ArrayList<>(manager.get().getAllWaypoints().stream().filter(point -> point.getName().startsWith(PREFIX)).toList());
            ours.forEach(manager.get()::removeWaypoint);
            for (RealmMapClient.Node node : entry.getValue()) {
                manager.get().addWaypointAt(new BlockPos(node.x(), node.y(), node.z()), PREFIX + node.name()).setColor(RealmMapClient.color(node.owner()));
            }
            APPLIED.put(entry.getKey(), signature.toString());
        }
        return done;
    }

    private static Optional<WaypointManager> manager(String dim) {
        ResourceLocation location = ResourceLocation.tryParse(dim);
        return location == null ? Optional.empty() : FTBChunksAPI.clientApi().getWaypointManager(ResourceKey.create(Registries.DIMENSION, location));
    }
}
