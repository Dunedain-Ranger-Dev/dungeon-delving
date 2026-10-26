package io.github.ranger3462.dungeondelving.command;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.realm.Realm;
import io.github.ranger3462.dungeondelving.realm.RealmNode;
import io.github.ranger3462.dungeondelving.realm.RealmTurns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Building the realm's map by hand (DD-94, docs/design/realm.md), for operators:
 * <ul>
 * <li>{@code /realm}: how many nodes and links there are.</li>
 * <li>{@code /realm list [type]} and {@code /realm info <id>}: what's there.</li>
 * <li>{@code /realm node add <id> <type> [owner]}: a node where you stand. {@code remove}, {@code move} (to where you
 * stand), {@code rename}, {@code owner}, {@code kind} (a site's or event's: watchtower, bridge, caravan...), {@code set}
 * (a stat).</li>
 * <li>{@code /realm link <a> <b>} and {@code /realm unlink <a> <b>}: the links that make borders.</li>
 * <li>{@code /realm clock}: the turn, the kingdom's army and treasury, raids declared, and the time to the next turn.
 * {@code /realm turn [count]}: take turns now. {@code /realm log [count]}: what's happened lately (DD-95).</li>
 * <li>{@code /realm blueprint save <id> <radius> [down] [up]}: snapshot the blocks around a node, to rebuild it from after
 * raids. {@code info}, {@code damage <id> <points>} (as a sacking would), {@code repair <id>} (all of it, now) (DD-97).</li>
 * <li>{@code /realm raid <town>}: the strongest stronghold bordering a town raids it, in 10 seconds: fought if you're
 * there, rolled if not (DD-98).</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class RealmCommands {
    private static final SuggestionProvider<CommandSourceStack> NODES = (context, builder) -> SharedSuggestionProvider.suggest(
            Realm.get(context.getSource().getServer()).nodes().stream().map(node -> node.id), builder);
    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(RealmNode.Type.values()).map(RealmNode.Type::id), builder);
    private static final SuggestionProvider<CommandSourceStack> OWNERS = (context, builder) -> SharedSuggestionProvider.suggest(
            Stream.concat(Stream.of(RealmNode.KINGDOM, RealmNode.NONE), DungeonFactions.ids().stream()), builder);

    private RealmCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("realm").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(RealmCommands::summary)
                .then(Commands.literal("list").executes(context -> list(context, null))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(context -> list(context, StringArgumentType.getString(context, "type")))))
                .then(Commands.literal("info").then(node("id").executes(RealmCommands::info)))
                .then(Commands.literal("clock").executes(RealmCommands::clock))
                .then(Commands.literal("raid").then(node("town").executes(RealmCommands::raid)))
                .then(Commands.literal("blueprint")
                        .then(Commands.literal("save").then(node("id").then(Commands.argument("radius", IntegerArgumentType.integer(1, 48))
                                .executes(context -> saveBlueprint(context, 8, 24))
                                .then(Commands.argument("down", IntegerArgumentType.integer(0, 32))
                                        .executes(context -> saveBlueprint(context, IntegerArgumentType.getInteger(context, "down"), 24))
                                        .then(Commands.argument("up", IntegerArgumentType.integer(0, 64)).executes(context -> saveBlueprint(context,
                                                IntegerArgumentType.getInteger(context, "down"), IntegerArgumentType.getInteger(context, "up"))))))))
                        .then(Commands.literal("info").then(node("id").executes(RealmCommands::blueprintInfo)))
                        .then(Commands.literal("damage").then(node("id").then(Commands.argument("points", IntegerArgumentType.integer(1, 1000))
                                .executes(RealmCommands::damage))))
                        .then(Commands.literal("repair").then(node("id").executes(RealmCommands::repair))))
                .then(Commands.literal("turn").executes(context -> turn(context, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> turn(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("log").executes(context -> log(context, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> log(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("link").then(node("a").then(node("b").executes(context -> link(context, true)))))
                .then(Commands.literal("unlink").then(node("a").then(node("b").executes(context -> link(context, false)))))
                .then(Commands.literal("node")
                        .then(Commands.literal("add").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES).executes(context -> add(context, null))
                                        .then(Commands.argument("owner", StringArgumentType.word()).suggests(OWNERS)
                                                .executes(context -> add(context, StringArgumentType.getString(context, "owner")))))))
                        .then(Commands.literal("remove").then(node("id").executes(RealmCommands::remove)))
                        .then(Commands.literal("move").then(node("id").executes(RealmCommands::move)))
                        .then(Commands.literal("rename").then(node("id").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(RealmCommands::rename))))
                        .then(Commands.literal("owner").then(node("id").then(Commands.argument("owner", StringArgumentType.word()).suggests(OWNERS)
                                .executes(RealmCommands::owner))))
                        .then(Commands.literal("kind").then(node("id").then(Commands.argument("kind", StringArgumentType.word())
                                .executes(RealmCommands::kind))))
                        .then(Commands.literal("set").then(node("id").then(Commands.argument("stat", StringArgumentType.word())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 1_000_000)).executes(RealmCommands::set)))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> node(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests(NODES);
    }

    private static Realm realm(CommandContext<CommandSourceStack> context) {
        return Realm.get(context.getSource().getServer());
    }

    /** The node named by an argument, or a message saying there's none. */
    private static Optional<RealmNode> find(CommandContext<CommandSourceStack> context, String argument) {
        String id = StringArgumentType.getString(context, argument);
        Optional<RealmNode> node = realm(context).node(id);
        if (node.isEmpty()) {
            Feedback.fail(context.getSource(), "There's no realm node " + id + ". /realm list shows them.");
        }
        return node;
    }

    private static int summary(CommandContext<CommandSourceStack> context) {
        Realm realm = realm(context);
        Map<RealmNode.Type, Long> counts = realm.nodes().stream().collect(Collectors.groupingBy(node -> node.type, Collectors.counting()));
        Feedback.tell(context.getSource(), "The realm: " + realm.nodes().size() + " nodes, " + realm.linkCount() + " links.");
        for (RealmNode.Type type : RealmNode.Type.values()) {
            Feedback.detail(context.getSource(), type.id() + ": " + counts.getOrDefault(type, 0L));
        }
        return realm.nodes().size();
    }

    private static int list(CommandContext<CommandSourceStack> context, String typeId) {
        Optional<RealmNode.Type> type = typeId == null ? Optional.empty() : RealmNode.Type.byId(typeId);
        if (typeId != null && type.isEmpty()) {
            Feedback.fail(context.getSource(), "Unknown node type " + typeId + ".");
            return 0;
        }
        Realm realm = realm(context);
        List<RealmNode> shown = realm.nodes().stream().filter(node -> type.isEmpty() || node.type == type.get()).toList();
        Feedback.tell(context.getSource(), shown.isEmpty() ? "No realm nodes yet." : "Realm nodes:");
        for (RealmNode node : shown) {
            Feedback.detail(context.getSource(), node.id + " (" + node.type.id() + ", " + node.owner() + ") at " + node.pos().toShortString()
                    + (realm.onBorder(node) ? ", on the border" : ""));
        }
        return shown.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> found = find(context, "id");
        if (found.isEmpty()) {
            return 0;
        }
        RealmNode node = found.get();
        Realm realm = realm(context);
        CommandSourceStack source = context.getSource();
        Feedback.tell(source, node.name() + " (" + node.id + "): a " + node.type.id() + (node.kind().isEmpty() ? "" : " (" + node.kind() + ")")
                + ", held by " + node.owner() + (realm.onBorder(node) ? ", on the border" : ""));
        Feedback.detail(source, "At " + node.pos().toShortString() + " in " + node.dimension().location());
        if (!node.stats().isEmpty()) {
            Feedback.detail(source, "Stats: " + node.stats().entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")));
        }
        List<RealmNode> neighbors = realm.neighbors(node.id);
        Feedback.detail(source, neighbors.isEmpty() ? "No links." : "Linked to: " + neighbors.stream().map(n -> n.id + " (" + n.owner() + ")")
                .collect(Collectors.joining(", ")));
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context, String ownerArgument) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String typeId = StringArgumentType.getString(context, "type");
        if (!Realm.ID.matcher(id).matches()) {
            Feedback.fail(source, "A node id is up to 32 lowercase letters, digits and underscores.");
            return 0;
        }
        Optional<RealmNode.Type> type = RealmNode.Type.byId(typeId);
        if (type.isEmpty()) {
            Feedback.fail(source, "Unknown node type " + typeId + ": town, site, stronghold, battle or event.");
            return 0;
        }
        Optional<String> owner = ownerArgument != null ? Optional.of(ownerArgument) : Realm.defaultOwner(type.get());
        if (owner.isEmpty()) {
            Feedback.fail(source, "Say which monster nation holds it: /realm node add " + id + " stronghold <" + String.join("|", DungeonFactions.ids()) + ">");
            return 0;
        }
        Optional<String> problem = Realm.ownerProblem(type.get(), owner.get());
        if (problem.isPresent()) {
            Feedback.fail(source, problem.get());
            return 0;
        }
        RealmNode node = new RealmNode(id, type.get(), owner.get(), source.getLevel().dimension(), BlockPos.containing(source.getPosition()));
        if (!realm(context).add(node)) {
            Feedback.fail(source, "There's already a realm node " + id + ".");
            return 0;
        }
        Feedback.tell(source, "Added " + type.get().id() + " " + id + ", held by " + owner.get() + ", at " + node.pos().toShortString() + ".");
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (!realm(context).remove(id)) {
            Feedback.fail(context.getSource(), "There's no realm node " + id + ".");
            return 0;
        }
        Feedback.tell(context.getSource(), "Removed " + id + " and its links.");
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> node = find(context, "id");
        node.ifPresent(found -> {
            CommandSourceStack source = context.getSource();
            realm(context).move(found, source.getLevel().dimension(), BlockPos.containing(source.getPosition()));
            Feedback.tell(source, "Moved " + found.id + " to " + found.pos().toShortString() + ".");
        });
        return node.isPresent() ? 1 : 0;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> node = find(context, "id");
        node.ifPresent(found -> {
            realm(context).rename(found, StringArgumentType.getString(context, "name"));
            Feedback.tell(context.getSource(), found.id + " is now called " + found.name() + ".");
        });
        return node.isPresent() ? 1 : 0;
    }

    private static int owner(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> node = find(context, "id");
        if (node.isEmpty()) {
            return 0;
        }
        String owner = StringArgumentType.getString(context, "owner");
        Optional<String> problem = Realm.ownerProblem(node.get().type, owner);
        if (problem.isPresent()) {
            Feedback.fail(context.getSource(), problem.get());
            return 0;
        }
        realm(context).setOwner(node.get(), owner);
        Feedback.tell(context.getSource(), node.get().id + " is now held by " + owner + ".");
        return 1;
    }

    private static int kind(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> node = find(context, "id");
        node.ifPresent(found -> {
            realm(context).setKind(found, StringArgumentType.getString(context, "kind"));
            Feedback.tell(context.getSource(), found.id + " is a " + found.kind() + ".");
        });
        return node.isPresent() ? 1 : 0;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> node = find(context, "id");
        node.ifPresent(found -> {
            String stat = StringArgumentType.getString(context, "stat");
            int value = IntegerArgumentType.getInteger(context, "value");
            realm(context).setStat(found, stat, value);
            Feedback.tell(context.getSource(), found.id + "'s " + stat + " is now " + value + ".");
        });
        return node.isPresent() ? 1 : 0;
    }

    private static int saveBlueprint(CommandContext<CommandSourceStack> context, int down, int up) {
        Optional<RealmNode> found = find(context, "id");
        if (found.isEmpty()) {
            return 0;
        }
        RealmNode node = found.get();
        net.minecraft.server.level.ServerLevel level = context.getSource().getServer().getLevel(node.dimension());
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos origin = node.pos().offset(-radius, -down, -radius);
        net.minecraft.core.Vec3i size = new net.minecraft.core.Vec3i(radius * 2 + 1, down + up + 1, radius * 2 + 1);
        if (level == null || !level.hasChunksAt(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            Feedback.fail(context.getSource(), "Stand near " + node.id + " so all of it is loaded, then save again.");
            return 0;
        }
        var blueprint = io.github.ranger3462.dungeondelving.realm.Blueprint.capture(context.getSource().getServer(), node.id, level, origin, size);
        Feedback.tell(context.getSource(), "Saved " + node.id + "'s blueprint: " + blueprint.blockCount() + " blocks in a " + size.getX() + "x" + size.getY()
                + "x" + size.getZ() + " box. Raids can only break, and rebuilding only restores, what's in it.");
        return blueprint.blockCount();
    }

    private static int blueprintInfo(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> found = find(context, "id");
        if (found.isEmpty()) {
            return 0;
        }
        RealmNode node = found.get();
        var blueprint = io.github.ranger3462.dungeondelving.realm.Blueprint.of(context.getSource().getServer(), node.id);
        if (blueprint.isEmpty()) {
            Feedback.tell(context.getSource(), node.id + " has no blueprint. /realm blueprint save " + node.id + " <radius> makes one.");
            return 0;
        }
        var size = blueprint.get().size();
        Feedback.tell(context.getSource(), node.id + "'s blueprint: " + blueprint.get().blockCount() + " blocks, " + size.getX() + "x" + size.getY() + "x"
                + size.getZ() + " from " + blueprint.get().origin().toShortString() + ".");
        Feedback.detail(context.getSource(), "Owed damage " + node.stat("damage") + " (x" + io.github.ranger3462.dungeondelving.realm.RealmDamage.BLOCKS_PER_POINT
                + " blocks), wrecked " + node.stat("wrecked") + " blocks, rebuilding " + node.stat("repair") + ".");
        return 1;
    }

    private static int damage(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> found = find(context, "id");
        found.ifPresent(node -> {
            int points = IntegerArgumentType.getInteger(context, "points");
            realm(context).setStat(node, "damage", node.stat("damage") + points);
            Feedback.tell(context.getSource(), node.id + " is owed " + node.stat("damage") + " damage: it's broken when its area is loaded.");
        });
        return found.isPresent() ? 1 : 0;
    }

    private static int repair(CommandContext<CommandSourceStack> context) {
        Optional<RealmNode> found = find(context, "id");
        if (found.isEmpty()) {
            return 0;
        }
        RealmNode node = found.get();
        var blueprint = io.github.ranger3462.dungeondelving.realm.Blueprint.of(context.getSource().getServer(), node.id);
        net.minecraft.server.level.ServerLevel level = context.getSource().getServer().getLevel(node.dimension());
        if (blueprint.isEmpty() || level == null) {
            Feedback.fail(context.getSource(), node.id + " has no blueprint to rebuild from.");
            return 0;
        }
        int rebuilt = io.github.ranger3462.dungeondelving.realm.RealmDamage.rebuild(level, blueprint.get(), Integer.MAX_VALUE);
        Realm realm = realm(context);
        realm.setStat(node, "wrecked", 0);
        realm.setStat(node, "repair", 0);
        realm.setStat(node, "damage", 0);
        Feedback.tell(context.getSource(), "Rebuilt " + rebuilt + " blocks of " + node.id + ".");
        return rebuilt;
    }

    private static int raid(CommandContext<CommandSourceStack> context) {
        String town = StringArgumentType.getString(context, "town");
        Realm realm = realm(context);
        var raid = io.github.ranger3462.dungeondelving.realm.RealmRaids.declare(realm, town, System.currentTimeMillis() + 10_000L);
        if (raid.isEmpty()) {
            Feedback.fail(context.getSource(), "No raid: " + town + " must be a town bordering a monster stronghold, with no raid on it already.");
            return 0;
        }
        RealmTurns.tellNews(context.getSource().getServer(), realm);
        Feedback.tell(context.getSource(), "The " + raid.get().nation() + " raid " + town + " in 10 seconds. Be within "
                + io.github.ranger3462.dungeondelving.realm.RealmLiveRaids.PRESENCE + " blocks of it to fight it.");
        return 1;
    }

    private static int clock(CommandContext<CommandSourceStack> context) {
        Realm realm = realm(context);
        long seconds = RealmTurns.untilNext(realm, System.currentTimeMillis()) / 1000;
        Feedback.tell(context.getSource(), "Turn " + realm.turn() + ". The next in " + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ".");
        Feedback.detail(context.getSource(), "The kingdom: " + realm.kingdomStats().entrySet().stream().map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", ")) + "; the border faces " + RealmTurns.threat(realm) + " monster military.");
        for (String nation : realm.nations()) {
            Feedback.detail(context.getSource(), "The " + nation + ": military " + RealmTurns.military(realm, nation));
        }
        for (Realm.Raid raid : realm.raids()) {
            long left = Math.max(0, raid.resolveAt() - System.currentTimeMillis()) / 1000;
            Feedback.detail(context.getSource(), "Raid declared on turn " + raid.declared() + ": the " + raid.nation() + " of " + raid.stronghold() + " on "
                    + raid.town() + ", at dusk in " + left / 60 + ":" + String.format("%02d", left % 60) + ".");
        }
        return realm.turn();
    }

    private static int turn(CommandContext<CommandSourceStack> context, int count) {
        Realm realm = realm(context);
        int from = realm.turn();
        var random = context.getSource().getLevel().getRandom();
        for (int i = 0; i < count; i++) {
            // Forced turns don't wait for dusk: each one's raids resolve before the next.
            RealmTurns.takeTurn(realm, random, System.currentTimeMillis());
            io.github.ranger3462.dungeondelving.realm.RealmRaids.resolveDue(realm, Long.MAX_VALUE, random);
        }
        RealmTurns.tellNews(context.getSource().getServer(), realm);
        Feedback.tell(context.getSource(), "Took " + count + (count == 1 ? " turn" : " turns") + " (now turn " + realm.turn() + "):");
        // What these turns did, the latest dozen at most.
        List<Realm.LogEntry> taken = realm.log().stream().filter(entry -> entry.turn() > from).toList();
        taken.subList(Math.max(0, taken.size() - 12), taken.size())
                .forEach(entry -> Feedback.detail(context.getSource(), "Turn " + entry.turn() + ": " + entry.text()));
        return realm.turn();
    }

    private static int log(CommandContext<CommandSourceStack> context, int count) {
        List<Realm.LogEntry> log = realm(context).log();
        if (log.isEmpty()) {
            Feedback.tell(context.getSource(), "Nothing has happened in the realm yet.");
            return 0;
        }
        Feedback.tell(context.getSource(), "The realm lately:");
        log.subList(Math.max(0, log.size() - count), log.size())
                .forEach(entry -> Feedback.detail(context.getSource(), "Turn " + entry.turn() + ": " + entry.text()));
        return log.size();
    }

    private static int link(CommandContext<CommandSourceStack> context, boolean linking) {
        String a = StringArgumentType.getString(context, "a");
        String b = StringArgumentType.getString(context, "b");
        Realm realm = realm(context);
        boolean done = linking ? realm.link(a, b) : realm.unlink(a, b);
        if (!done) {
            Feedback.fail(context.getSource(), linking ? "Couldn't link " + a + " and " + b + ": both must exist, be different, and not be linked already."
                    : a + " and " + b + " aren't linked.");
            return 0;
        }
        Feedback.tell(context.getSource(), (linking ? "Linked " : "Unlinked ") + a + " and " + b + ".");
        return 1;
    }
}
