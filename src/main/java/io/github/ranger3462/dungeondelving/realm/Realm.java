package io.github.ranger3462.dungeondelving.realm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The realm's map (DD-94, docs/design/realm.md): its nodes, and the links between them. Links make borders: a kingdom
 * node linked to a monster nation's is on the border. Saved with the world (in the overworld's data). Nodes are
 * hand-placed and linked with the /realm commands.
 */
public final class Realm extends SavedData {
    private static final String NAME = "dungeondelving_realm";
    /** Node ids: lowercase letters, digits and underscores. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    private final Map<String, RealmNode> nodes = new LinkedHashMap<>();
    /** Links, each stored once as "a|b" with a before b. */
    private final Set<String> links = new LinkedHashSet<>();

    public static Realm get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Realm::new, Realm::load, null), NAME);
    }

    /** Who can own a node: the kingdom, a monster nation (a dungeon faction), or no one. */
    public static boolean validOwner(String owner) {
        return RealmNode.KINGDOM.equals(owner) || RealmNode.NONE.equals(owner) || DungeonFactions.ids().contains(owner);
    }

    /** The owner a new node of a type starts with, when none is given: towns are the kingdom's; strongholds need a nation. */
    public static Optional<String> defaultOwner(RealmNode.Type type) {
        return switch (type) {
            case TOWN -> Optional.of(RealmNode.KINGDOM);
            case STRONGHOLD -> Optional.empty();
            default -> Optional.of(RealmNode.NONE);
        };
    }

    /** Why a node can't have this owner, if it can't: towns are always the kingdom's, strongholds always a nation's. */
    public static Optional<String> ownerProblem(RealmNode.Type type, String owner) {
        if (!validOwner(owner)) {
            return Optional.of("Unknown owner " + owner + ": use kingdom, none, or a monster nation (" + String.join(", ", DungeonFactions.ids()) + ").");
        }
        if (type == RealmNode.Type.TOWN && !RealmNode.KINGDOM.equals(owner)) {
            return Optional.of("Towns are always the kingdom's.");
        }
        if (type == RealmNode.Type.STRONGHOLD && (RealmNode.KINGDOM.equals(owner) || RealmNode.NONE.equals(owner))) {
            return Optional.of("A stronghold belongs to a monster nation (" + String.join(", ", DungeonFactions.ids()) + ").");
        }
        return Optional.empty();
    }

    // --- Nodes ---

    public Collection<RealmNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Optional<RealmNode> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    /** Adds a node; false if the id is taken. */
    public boolean add(RealmNode node) {
        if (nodes.putIfAbsent(node.id, node) != null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Removes a node and its links; false if there's no such node. */
    public boolean remove(String id) {
        if (nodes.remove(id) == null) {
            return false;
        }
        links.removeIf(link -> ends(link)[0].equals(id) || ends(link)[1].equals(id));
        setDirty();
        return true;
    }

    public void rename(RealmNode node, String name) {
        node.setName(name);
        setDirty();
    }

    public void setOwner(RealmNode node, String owner) {
        node.setOwner(owner);
        setDirty();
    }

    public void setKind(RealmNode node, String kind) {
        node.setKind(kind);
        setDirty();
    }

    public void move(RealmNode node, ResourceKey<Level> dimension, BlockPos pos) {
        node.moveTo(dimension, pos);
        setDirty();
    }

    public void setStat(RealmNode node, String stat, int value) {
        node.setStat(stat, value);
        setDirty();
    }

    // --- Links ---

    private static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static String[] ends(String link) {
        return link.split("\\|", 2);
    }

    /** Links two nodes; false if either is missing, they're the same node, or they're already linked. */
    public boolean link(String a, String b) {
        if (a.equals(b) || !nodes.containsKey(a) || !nodes.containsKey(b) || !links.add(key(a, b))) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean unlink(String a, String b) {
        if (!links.remove(key(a, b))) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean linked(String a, String b) {
        return links.contains(key(a, b));
    }

    /** The nodes linked to one. */
    public List<RealmNode> neighbors(String id) {
        List<RealmNode> neighbors = new ArrayList<>();
        for (String link : links) {
            String[] ends = ends(link);
            String other = ends[0].equals(id) ? ends[1] : ends[1].equals(id) ? ends[0] : null;
            if (other != null && nodes.containsKey(other)) {
                neighbors.add(nodes.get(other));
            }
        }
        return neighbors;
    }

    /** Whether a node is on the border: the kingdom's and linked to a monster nation's, or the other way round. */
    public boolean onBorder(RealmNode node) {
        if (!node.kingdoms() && !node.monsters()) {
            return false;
        }
        return neighbors(node.id).stream().anyMatch(other -> node.kingdoms() ? other.monsters() : other.kingdoms());
    }

    public int linkCount() {
        return links.size();
    }

    // --- Saving ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag nodeList = new ListTag();
        nodes.values().forEach(node -> nodeList.add(node.save()));
        tag.put("nodes", nodeList);
        ListTag linkList = new ListTag();
        links.forEach(link -> linkList.add(StringTag.valueOf(link)));
        tag.put("links", linkList);
        return tag;
    }

    static Realm load(CompoundTag tag, HolderLookup.Provider registries) {
        Realm realm = new Realm();
        for (Tag entry : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            RealmNode.load((CompoundTag) entry).ifPresent(node -> realm.nodes.put(node.id, node));
        }
        for (Tag entry : tag.getList("links", Tag.TAG_STRING)) {
            String[] ends = ends(entry.getAsString());
            if (ends.length == 2 && realm.nodes.containsKey(ends[0]) && realm.nodes.containsKey(ends[1])) {
                realm.links.add(key(ends[0], ends[1]));
            }
        }
        return realm;
    }

    /** A copy through saving and loading, for tests. */
    public Realm reloaded(HolderLookup.Provider registries) {
        return load(save(new CompoundTag(), registries), registries);
    }
}
