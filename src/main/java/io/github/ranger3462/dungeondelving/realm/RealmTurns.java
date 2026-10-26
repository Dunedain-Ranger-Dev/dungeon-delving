package io.github.ranger3462.dungeondelving.realm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The realm's turns (DD-95, docs/design/realm.md). A turn comes every {@link Config#REALM_TURN_MINUTES} real minutes (a
 * Minecraft day by default), on a real-time clock that keeps counting while the server is off; on startup, missed turns
 * are taken, up to {@link Config#REALM_CATCH_UP_TURNS}, and the rest skipped.
 * <p>
 * Each turn, the kingdom and each monster nation that holds a stronghold picks one action, weighted by its situation:
 * <ul>
 * <li>The kingdom: reinforce its borders (border towns gain defense), muster (its army grows), or gather wealth (towns
 * grow richer, and the treasury fills). Threatened borders make reinforcing likelier; a small army, mustering; an empty
 * treasury, gathering.</li>
 * <li>A monster nation: build its military, raid the weakest town its strongholds border, or fortify its strongholds.
 * The stronger it is against its targets, the likelier a raid; it raids one town at a time.</li>
 * </ul>
 * Raids are declared here and resolved half a turn later, at dusk ({@link RealmRaids}). What each side did goes in the
 * realm log; raids are news, told to every player online.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class RealmTurns {
    public enum KingdomAction { REINFORCE, MUSTER, GATHER_WEALTH }

    public enum NationAction { BUILD_MILITARY, RAID, FORTIFY }

    private RealmTurns() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            Realm realm = Realm.get(event.getServer());
            long now = System.currentTimeMillis();
            RandomSource random = event.getServer().overworld().getRandom();
            catchUp(realm, now, random);
            RealmRaids.resolveDue(event.getServer(), realm, now, random);
            tellNews(event.getServer(), realm);
        }
    }

    /** Tells every player online the realm's news (raids declared and resolved). */
    public static void tellNews(MinecraftServer server, Realm realm) {
        for (Realm.LogEntry entry : realm.takeNews()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(entry.text()).withStyle(ChatFormatting.RED), false);
        }
    }

    public static long turnMillis() {
        return Config.REALM_TURN_MINUTES.get() * 60_000L;
    }

    /**
     * Takes the turns that are due by real time {@code now}: none until a turn's length has passed since the last. After
     * a long time away, at most the catch-up cap; the rest are skipped. Returns how many were taken.
     */
    public static int catchUp(Realm realm, long now, RandomSource random) {
        long length = turnMillis();
        if (realm.nodes().isEmpty()) {
            realm.lastTurnMillis = now; // No realm set up in this world: nothing to do, and no backlog when one is.
            return 0;
        }
        if (realm.lastTurnMillis == 0) {
            realm.lastTurnMillis = now; // The clock starts with the realm's first tick.
            realm.setDirty();
            return 0;
        }
        long due = (now - realm.lastTurnMillis) / length;
        if (due <= 0) {
            return 0;
        }
        int cap = Config.REALM_CATCH_UP_TURNS.get();
        if (due > Math.max(1, cap)) {
            long skipped = due - Math.max(1, cap);
            realm.lastTurnMillis += skipped * length;
            realm.record("The realm was quiet for " + skipped + " turns while no one watched.");
            due = Math.max(1, cap);
        }
        for (int i = 0; i < due; i++) {
            long turnTime = realm.lastTurnMillis + length;
            RealmRaids.resolveDue(realm, turnTime, random); // Raids from the turn before resolve before this one starts.
            takeTurn(realm, random, turnTime);
            realm.lastTurnMillis = turnTime;
        }
        realm.setDirty();
        return (int) due;
    }

    /** Milliseconds until the next turn, by real time {@code now}. */
    public static long untilNext(Realm realm, long now) {
        return realm.lastTurnMillis == 0 ? turnMillis() : Math.max(0, realm.lastTurnMillis + turnMillis() - now);
    }

    /** One turn at real time {@code time}: the kingdom acts, then each monster nation. */
    public static void takeTurn(Realm realm, RandomSource random, long time) {
        realm.turn++;
        RealmDamage.fundRepairs(realm);
        kingdomAct(realm, pick(kingdomWeights(realm), random));
        for (String nation : realm.nations()) {
            nationAct(realm, nation, pick(nationWeights(realm, nation), random), time);
        }
    }

    // --- The kingdom ---

    /** The kingdom's towns on the border. */
    public static List<RealmNode> borderTowns(Realm realm) {
        return realm.nodes().stream().filter(node -> node.type == RealmNode.Type.TOWN && realm.onBorder(node)).toList();
    }

    /** The military of every monster stronghold touching the kingdom's border. */
    public static int threat(Realm realm) {
        return borderTowns(realm).stream().flatMap(town -> realm.neighbors(town.id).stream())
                .filter(node -> node.type == RealmNode.Type.STRONGHOLD && node.monsters()).distinct().mapToInt(node -> node.stat("military")).sum();
    }

    public static Map<KingdomAction, Integer> kingdomWeights(Realm realm) {
        List<RealmNode> border = borderTowns(realm);
        int threat = threat(realm);
        int defense = border.stream().mapToInt(town -> town.stat("defense")).sum() + realm.kingdomStat("army");
        Map<KingdomAction, Integer> weights = new LinkedHashMap<>();
        weights.put(KingdomAction.REINFORCE, border.isEmpty() ? 0 : 2 + (threat > defense ? 4 : 0));
        weights.put(KingdomAction.MUSTER, 2 + (realm.kingdomStat("army") < threat / 2 ? 2 : 0));
        weights.put(KingdomAction.GATHER_WEALTH, 2 + (realm.kingdomStat("treasury") < 10 ? 2 : 0));
        return weights;
    }

    public static void kingdomAct(Realm realm, KingdomAction action) {
        switch (action) {
            case REINFORCE -> {
                List<RealmNode> border = borderTowns(realm);
                border.forEach(town -> realm.addStat(town, "defense", 2));
                realm.record("The kingdom reinforced its border: " + names(border) + " each gained 2 defense.");
            }
            case MUSTER -> {
                realm.addKingdomStat("army", 3);
                realm.record("The kingdom mustered troops: its army is now " + realm.kingdomStat("army") + ".");
            }
            case GATHER_WEALTH -> {
                List<RealmNode> towns = realm.nodes().stream().filter(node -> node.type == RealmNode.Type.TOWN).toList();
                towns.forEach(town -> realm.addStat(town, "wealth", 1));
                realm.addKingdomStat("treasury", towns.size());
                realm.record("The kingdom gathered wealth: its treasury is now " + realm.kingdomStat("treasury") + ".");
            }
        }
    }

    // --- Monster nations ---

    /** A nation's strongholds. */
    public static List<RealmNode> strongholds(Realm realm, String nation) {
        return realm.nodes().stream().filter(node -> node.type == RealmNode.Type.STRONGHOLD && nation.equals(node.owner())).toList();
    }

    /** A nation's whole military: every stronghold's. */
    public static int military(Realm realm, String nation) {
        return strongholds(realm, nation).stream().mapToInt(node -> node.stat("military")).sum();
    }

    /** The town a nation would raid: the weakest kingdom town one of its strongholds borders, and from which stronghold. */
    public static Optional<Map.Entry<RealmNode, RealmNode>> raidTarget(Realm realm, String nation) {
        List<Map.Entry<RealmNode, RealmNode>> targets = new ArrayList<>();
        for (RealmNode stronghold : strongholds(realm, nation)) {
            realm.neighbors(stronghold.id).stream().filter(node -> node.type == RealmNode.Type.TOWN)
                    .forEach(town -> targets.add(Map.entry(stronghold, town)));
        }
        return targets.stream().min(Comparator.comparingInt(entry -> entry.getValue().stat("defense")));
    }

    public static Map<NationAction, Integer> nationWeights(Realm realm, String nation) {
        int military = military(realm, nation);
        Optional<Map.Entry<RealmNode, RealmNode>> target = raidTarget(realm, nation);
        boolean raiding = realm.raids.stream().anyMatch(raid -> raid.nation().equals(nation));
        int fortification = strongholds(realm, nation).stream().mapToInt(node -> node.stat("fortification")).min().orElse(0);
        Map<NationAction, Integer> weights = new LinkedHashMap<>();
        int targetDefense = target.map(entry -> entry.getValue().stat("defense")).orElse(0);
        weights.put(NationAction.BUILD_MILITARY, 3 + (target.isPresent() && military < targetDefense ? 2 : 0));
        weights.put(NationAction.RAID, target.isEmpty() || raiding ? 0 : 1 + Math.max(0, (military - targetDefense) / 3));
        weights.put(NationAction.FORTIFY, 1 + (fortification < 5 ? 1 : 0));
        return weights;
    }

    /** A nation's action on the turn at real time {@code time}: a raid declared then resolves half a turn later. */
    public static void nationAct(Realm realm, String nation, NationAction action, long time) {
        List<RealmNode> strongholds = strongholds(realm, nation);
        switch (action) {
            case BUILD_MILITARY -> {
                strongholds.forEach(node -> realm.addStat(node, "military", 2));
                realm.record("The " + nation + " built up their military: " + names(strongholds) + " each gained 2.");
            }
            case FORTIFY -> {
                strongholds.forEach(node -> realm.addStat(node, "fortification", 1));
                realm.record("The " + nation + " fortified " + names(strongholds) + ".");
            }
            case RAID -> raidTarget(realm, nation).ifPresent(target -> {
                realm.raids.add(new Realm.Raid(nation, target.getKey().id, target.getValue().id, realm.turn, time + turnMillis() / 2));
                realm.news("The " + nation + " of " + target.getKey().name() + " will raid " + target.getValue().name() + " at dusk!");
            });
        }
    }

    // --- Shared ---

    /** A weighted random pick; the first action if every weight is zero. */
    static <T> T pick(Map<T, Integer> weights, RandomSource random) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = total <= 0 ? 0 : random.nextInt(total);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    private static String names(List<RealmNode> nodes) {
        return nodes.isEmpty() ? "nowhere" : String.join(", ", nodes.stream().map(RealmNode::name).toList());
    }
}
