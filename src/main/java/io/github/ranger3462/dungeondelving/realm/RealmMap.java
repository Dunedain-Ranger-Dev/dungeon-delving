package io.github.ranger3462.dungeondelving.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The realm menu's data (realm menu, see client.RealmScreen): every node with its stats, links and troubles, and how
 * the kingdom and each nation stand, sent to players when they log in, when they open the menu, and whenever the realm
 * changes (checked every {@link #PUSH_INTERVAL} ticks).
 * <p>
 * A node's <b>warnings</b> are what's bad for the kingdom: a raid coming, ruins, damage, low morale, being outmatched by
 * a neighbouring stronghold, or a town lost to a nation. Its <b>notes</b> are just how it stands: an upgrade under way, a
 * march on it, a capital rallying.
 */
public final class RealmMap {
    public static final int PUSH_INTERVAL = 100;
    /** Morale below this is a warning. */
    public static final int LOW_MORALE = 5;
    private static CompoundTag lastSent;

    private RealmMap() {}

    /** Client to server: send me the realm (on opening the menu). */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(DungeonDelving.id("realm_map_request"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Request payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                send(player);
            }
        }
    }

    /** Server to client: the realm, as {@link #build} makes it, with the server's clock ({@code now}) for countdowns. */
    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(DungeonDelving.id("realm_map"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Sync::data, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The realm as the menu shows it (without the clock, so two builds can be compared). */
    public static CompoundTag build(Realm realm) {
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        for (RealmNode node : realm.nodes()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", node.id);
            entry.putString("name", node.name());
            entry.putString("type", node.type.id());
            entry.putString("owner", node.owner());
            entry.putString("kind", node.kind());
            entry.putString("dim", node.dimension().location().toString());
            entry.putInt("x", node.pos().getX());
            entry.putInt("y", node.pos().getY());
            entry.putInt("z", node.pos().getZ());
            CompoundTag stats = new CompoundTag();
            node.stats().forEach(stats::putInt);
            entry.put("stats", stats);
            entry.put("warnings", strings(warnings(realm, node)));
            entry.put("notes", strings(notes(realm, node)));
            nodes.add(entry);
        }
        tag.put("nodes", nodes);
        ListTag links = new ListTag();
        for (String[] link : realm.links()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("a", link[0]);
            entry.putString("b", link[1]);
            links.add(entry);
        }
        tag.put("links", links);
        CompoundTag kingdom = new CompoundTag();
        realm.kingdomStats().forEach(kingdom::putInt);
        tag.put("kingdom", kingdom);
        ListTag nations = new ListTag();
        for (String nation : realm.nations()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", nation);
            entry.putInt("military", RealmTurns.military(realm, nation));
            entry.putInt("strongholds", RealmTurns.strongholds(realm, nation).size());
            entry.putInt("towns", RealmTurns.towns(realm, nation).size());
            entry.putBoolean("pushedBack", RealmCapitals.pushedBack(realm, nation));
            nations.add(entry);
        }
        tag.put("nations", nations);
        ListTag raids = new ListTag();
        for (Realm.Raid raid : realm.raids()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("nation", raid.nation());
            entry.putString("from", raid.stronghold());
            entry.putString("to", raid.town());
            entry.putLong("resolveAt", raid.resolveAt());
            raids.add(entry);
        }
        tag.put("raids", raids);
        tag.putInt("turn", realm.turn());
        tag.putLong("lastTurn", realm.lastTurnMillis());
        tag.putLong("turnLength", RealmTurns.turnMillis());
        return tag;
    }

    /** What's bad for the kingdom at a node. */
    public static List<String> warnings(Realm realm, RealmNode node) {
        List<String> warnings = new ArrayList<>();
        if (node.type == RealmNode.Type.TOWN && node.monsters()) {
            warnings.add("Taken by the " + node.owner());
            return warnings;
        }
        if (!node.kingdoms()) {
            return warnings;
        }
        for (Realm.Raid raid : realm.raids()) {
            if (raid.town().equals(node.id) && !RealmNode.KINGDOM.equals(raid.nation())) {
                String from = realm.node(raid.stronghold()).map(RealmNode::name).orElse(raid.stronghold());
                warnings.add("Raid at dusk: the " + raid.nation() + " of " + from);
            }
        }
        if (node.type == RealmNode.Type.TOWN && RealmTiers.tier(node) == 0) {
            warnings.add("In ruins: the next raid that wins takes it");
        }
        if (node.stat("damage") > 0 || node.stat("wrecked") > node.stat("repair")) {
            warnings.add("Damaged: still rebuilding");
        }
        if (node.type == RealmNode.Type.TOWN && node.stat("morale") < LOW_MORALE) {
            warnings.add("Low morale (" + node.stat("morale") + ")");
        }
        int defense = RealmRaids.defense(realm, node);
        Optional<RealmNode> threat = realm.neighbors(node.id).stream().filter(other -> other.type == RealmNode.Type.STRONGHOLD && other.monsters())
                .max(java.util.Comparator.comparingInt(other -> other.stat("military")));
        if (threat.isPresent() && threat.get().stat("military") > defense) {
            warnings.add("Outmatched: " + threat.get().name() + " has " + threat.get().stat("military") + " military against its " + defense + " defense");
        }
        return warnings;
    }

    /** How a node stands, when it's not a warning. */
    public static List<String> notes(Realm realm, RealmNode node) {
        List<String> notes = new ArrayList<>();
        if (RealmTiers.upgrading(node)) {
            int days = node.stat("upgrading");
            notes.add("Upgrading to a " + RealmTiers.name(RealmTiers.tier(node) + 1) + ": " + days + (days == 1 ? " day" : " days") + " left, no support until then");
        }
        for (Realm.Raid raid : realm.raids()) {
            if (raid.town().equals(node.id) && RealmNode.KINGDOM.equals(raid.nation())) {
                notes.add("The kingdom's army marches on it at dusk");
            } else if (raid.town().equals(node.id) && !node.kingdoms()) {
                notes.add("The " + raid.nation() + " march on it at dusk");
            }
        }
        if (node.type == RealmNode.Type.CAPITAL && RealmCapitals.pushedBack(realm, node.owner())) {
            notes.add("Pushed back: rallying (" + node.stat("rally") + "/" + RealmCapitals.RALLY_TURNS + ")");
        }
        return notes;
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private static Sync sync(Realm realm, CompoundTag built) {
        CompoundTag data = built.copy();
        data.putLong("now", System.currentTimeMillis());
        return new Sync(data);
    }

    public static void send(ServerPlayer player) {
        Realm realm = Realm.get(player.server);
        PacketDistributor.sendToPlayer(player, sync(realm, build(realm)));
    }

    /** Sends the realm to everyone if it's changed since it was last sent. */
    static void push(MinecraftServer server) {
        CompoundTag built = build(Realm.get(server));
        if (!built.equals(lastSent)) {
            lastSent = built;
            PacketDistributor.sendToAllPlayers(sync(Realm.get(server), built));
        }
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID)
    public static final class Payloads {
        private Payloads() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(Request.TYPE, Request.STREAM_CODEC, Request::handle);
            // The handler only ever runs on the client, so the client class it names is never loaded on a server.
            event.registrar("1").playToClient(Sync.TYPE, Sync.STREAM_CODEC,
                    (payload, context) -> io.github.ranger3462.dungeondelving.client.RealmMapClient.receive(payload.data()));
        }
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                send(player);
            }
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (event.getServer().getTickCount() % PUSH_INTERVAL == 0) {
                push(event.getServer());
            }
        }
    }
}
