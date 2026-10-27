package io.github.ranger3462.dungeondelving.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The realm as last sent by the server (realm.RealmMap), for the realm menu and FTB Chunks' map. */
public final class RealmMapClient {
    public record Node(String id, String name, String type, String owner, String kind, String dim, int x, int y, int z, Map<String, Integer> stats,
            List<String> warnings, List<String> notes) {
        public int stat(String stat) {
            return stats.getOrDefault(stat, 0);
        }

        public boolean kingdoms() {
            return "kingdom".equals(owner);
        }

        public boolean monsters() {
            return !"kingdom".equals(owner) && !"none".equals(owner);
        }
    }

    public record Nation(String id, int military, int strongholds, int towns, boolean pushedBack) {}

    public record Raid(String nation, String from, String to, long resolveAt) {}

    public record Data(Map<String, Node> nodes, List<String[]> links, Map<String, Integer> kingdom, List<Nation> nations, List<Raid> raids, int turn,
            long lastTurn, long turnLength, long serverNow, long receivedAt) {
        /** The server's clock now, by how long it's been since this came. */
        public long serverTime() {
            return serverNow + (System.currentTimeMillis() - receivedAt);
        }

        public long untilNextTurn() {
            return lastTurn == 0 ? turnLength : Math.max(0, lastTurn + turnLength - serverTime());
        }

        public long until(Raid raid) {
            return Math.max(0, raid.resolveAt() - serverTime());
        }

        public String name(String id) {
            Node node = nodes.get(id);
            return node == null ? id : node.name();
        }
    }

    private static Data latest;
    private static boolean waypointsPending;

    private RealmMapClient() {}

    public static Data latest() {
        return latest;
    }

    public static void receive(CompoundTag tag) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Tag entry : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag node = (CompoundTag) entry;
            Map<String, Integer> stats = new LinkedHashMap<>();
            CompoundTag statTag = node.getCompound("stats");
            statTag.getAllKeys().forEach(key -> stats.put(key, statTag.getInt(key)));
            nodes.put(node.getString("id"), new Node(node.getString("id"), node.getString("name"), node.getString("type"), node.getString("owner"),
                    node.getString("kind"), node.getString("dim"), node.getInt("x"), node.getInt("y"), node.getInt("z"), stats,
                    strings(node, "warnings"), strings(node, "notes")));
        }
        List<String[]> links = new ArrayList<>();
        for (Tag entry : tag.getList("links", Tag.TAG_COMPOUND)) {
            links.add(new String[] { ((CompoundTag) entry).getString("a"), ((CompoundTag) entry).getString("b") });
        }
        Map<String, Integer> kingdom = new LinkedHashMap<>();
        CompoundTag kingdomTag = tag.getCompound("kingdom");
        kingdomTag.getAllKeys().forEach(key -> kingdom.put(key, kingdomTag.getInt(key)));
        List<Nation> nations = new ArrayList<>();
        for (Tag entry : tag.getList("nations", Tag.TAG_COMPOUND)) {
            CompoundTag nation = (CompoundTag) entry;
            nations.add(new Nation(nation.getString("id"), nation.getInt("military"), nation.getInt("strongholds"), nation.getInt("towns"),
                    nation.getBoolean("pushedBack")));
        }
        List<Raid> raids = new ArrayList<>();
        for (Tag entry : tag.getList("raids", Tag.TAG_COMPOUND)) {
            CompoundTag raid = (CompoundTag) entry;
            raids.add(new Raid(raid.getString("nation"), raid.getString("from"), raid.getString("to"), raid.getLong("resolveAt")));
        }
        latest = new Data(nodes, links, kingdom, nations, raids, tag.getInt("turn"), tag.getLong("lastTurn"), tag.getLong("turnLength"),
                tag.getLong("now"), System.currentTimeMillis());
        waypointsPending = true;
        updateWaypoints();
    }

    private static List<String> strings(CompoundTag tag, String key) {
        List<String> values = new ArrayList<>();
        tag.getList(key, Tag.TAG_STRING).forEach(value -> values.add(value.getAsString()));
        return values;
    }

    /** Puts the realm on FTB Chunks' map, when it's installed and ready (otherwise tries again shortly). */
    private static void updateWaypoints() {
        if (!waypointsPending || latest == null || Minecraft.getInstance().level == null) {
            return;
        }
        if (!ModList.get().isLoaded("ftbchunks")) {
            waypointsPending = false;
            return;
        }
        waypointsPending = !io.github.ranger3462.dungeondelving.integration.ftbchunks.RealmMapIcons.update(latest);
    }

    /** A colour for an owner, as 0xRRGGBB: the kingdom blue, each nation its own, no one grey. */
    public static int color(String owner) {
        return switch (owner) {
            case "kingdom" -> 0x4F8CFF;
            case "none" -> 0x9A9A9A;
            case "goblins" -> 0x6FCF4F;
            case "orcs" -> 0xD0603A;
            case "undead" -> 0xA070E0;
            case "kobolds" -> 0xC9A227;
            case "elementals" -> 0x4FD8E8;
            default -> 0x808080 | (owner.hashCode() & 0x7F7F7F);
        };
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Ticks {
        private Ticks() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (waypointsPending && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 40 == 0) {
                updateWaypoints();
            }
        }
    }
}
