package io.github.ranger3462.dungeondelving.realm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One place in the realm (DD-94, docs/design/realm.md): a town, site, stronghold, battle or event, hand-placed at a
 * position. It has an owner (the kingdom, a monster nation, or no one), a kind for sites and events (a watchtower, a
 * caravan), and stats by name (a town's defense, wealth and morale; a stronghold's military).
 */
public final class RealmNode {
    /** The owner of the kingdom's nodes. Monster nations own theirs by their dungeon faction id (goblins, orcs, undead). */
    public static final String KINGDOM = "kingdom";
    /** No owner: an unheld site, a battle, an event. */
    public static final String NONE = "none";

    public enum Type {
        /** A kingdom settlement: defense, wealth, morale. */
        TOWN(Map.of("defense", 10, "wealth", 10, "morale", 10)),
        /** A place worth holding: its heat rises the longer it's held. */
        SITE(Map.of("heat", 0)),
        /** A monster nation's base: its military and how fortified it is. */
        STRONGHOLD(Map.of("military", 10, "fortification", 5)),
        /** A raid or attack in progress (made by the turns, later). */
        BATTLE(Map.of()),
        /** A caravan, plague, festival and the like (later). */
        EVENT(Map.of());

        /** The stats a new node of this type starts with. */
        public final Map<String, Integer> defaults;

        Type(Map<String, Integer> defaults) {
            this.defaults = defaults;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Type> byId(String id) {
            for (Type type : values()) {
                if (type.id().equals(id)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    public final String id;
    public final Type type;
    private String name;
    private String owner;
    private String kind = "";
    private ResourceKey<Level> dimension;
    private BlockPos pos;
    private final Map<String, Integer> stats = new LinkedHashMap<>();

    public RealmNode(String id, Type type, String owner, ResourceKey<Level> dimension, BlockPos pos) {
        this.id = id;
        this.type = type;
        this.name = id;
        this.owner = owner;
        this.dimension = dimension;
        this.pos = pos.immutable();
        stats.putAll(type.defaults);
    }

    public String name() {
        return name;
    }

    public String owner() {
        return owner;
    }

    public String kind() {
        return kind;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public Map<String, Integer> stats() {
        return stats;
    }

    public int stat(String stat) {
        return stats.getOrDefault(stat, 0);
    }

    void setName(String name) {
        this.name = name;
    }

    void setOwner(String owner) {
        this.owner = owner;
    }

    void setKind(String kind) {
        this.kind = kind;
    }

    void moveTo(ResourceKey<Level> dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos.immutable();
    }

    void setStat(String stat, int value) {
        stats.put(stat, value);
    }

    /** Whether the kingdom holds it. */
    public boolean kingdoms() {
        return KINGDOM.equals(owner);
    }

    /** Whether a monster nation holds it. */
    public boolean monsters() {
        return !KINGDOM.equals(owner) && !NONE.equals(owner);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("type", type.id());
        tag.putString("name", name);
        tag.putString("owner", owner);
        tag.putString("kind", kind);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("pos", pos.asLong());
        CompoundTag statTag = new CompoundTag();
        stats.forEach(statTag::putInt);
        tag.put("stats", statTag);
        return tag;
    }

    static Optional<RealmNode> load(CompoundTag tag) {
        Optional<Type> type = Type.byId(tag.getString("type"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (type.isEmpty() || dimension == null) {
            return Optional.empty();
        }
        RealmNode node = new RealmNode(tag.getString("id"), type.get(), tag.getString("owner"), ResourceKey.create(Registries.DIMENSION, dimension),
                BlockPos.of(tag.getLong("pos")));
        node.name = tag.getString("name");
        node.kind = tag.getString("kind");
        node.stats.clear();
        CompoundTag stats = tag.getCompound("stats");
        for (String stat : stats.getAllKeys()) {
            node.stats.put(stat, stats.getInt(stat));
        }
        return Optional.of(node);
    }
}
