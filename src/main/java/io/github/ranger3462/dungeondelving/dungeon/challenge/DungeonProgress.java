package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/**
 * How far a dungeon has got towards its boss: the keys needed and used on the boss door, where the door and the boss
 * are, and what's left to do in each challenge room (hunt targets to kill, relics to offer, waves to fight). One per dungeon region;
 * saved with the regions.
 */
public final class DungeonProgress {
    private final int tier;
    private final String boss;
    private String faction = DungeonFactions.UNDEAD;
    private final int keysNeeded;
    private final List<BlockPos> door;
    private BlockPos bossSpawn;
    private int keysUsed;
    private boolean bossSpawned;
    /** Hunt targets still to kill (spawned or not), by the room they belong to (the room's minimum corner). */
    private final Map<Long, Integer> hunters = new HashMap<>();
    /** Hunt targets that haven't come out of a spawner yet, by room. */
    private final Map<Long, Integer> unspawned = new HashMap<>();
    /** Relic Shards a room's altar still needs, by room. */
    private final Map<Long, Integer> relics = new HashMap<>();
    /** Wave defense rooms not yet beaten, by room. */
    private final Map<Long, WaveRoom> waveRooms = new HashMap<>();

    public DungeonProgress(int tier, int keysNeeded, List<BlockPos> door, BlockPos bossSpawn) {
        this(tier, DungeonBosses.BARROW_KING, keysNeeded, door, bossSpawn);
    }

    public DungeonProgress(int tier, String boss, int keysNeeded, List<BlockPos> door, BlockPos bossSpawn) {
        this.tier = tier;
        this.boss = boss;
        this.keysNeeded = keysNeeded;
        this.door = List.copyOf(door);
        this.bossSpawn = bossSpawn;
    }

    /** Keys the boss door needs in a dungeon of this tier: two per tier. */
    public static int keysFor(int tier) {
        return tier * 2;
    }

    public int tier() {
        return tier;
    }

    /** Who holds this dungeon (a {@link DungeonFactions} id). */
    public String faction() {
        return faction;
    }

    public DungeonProgress withFaction(String faction) {
        this.faction = faction;
        return this;
    }

    /** Which boss guards this dungeon (a {@link DungeonBosses} type id). */
    public String boss() {
        return boss;
    }

    public int keysNeeded() {
        return keysNeeded;
    }

    public int keysUsed() {
        return keysUsed;
    }

    public boolean doorOpen() {
        return keysUsed >= keysNeeded;
    }

    public boolean bossSpawned() {
        return bossSpawned;
    }

    public List<BlockPos> door() {
        return door;
    }

    public BlockPos bossSpawn() {
        return bossSpawn;
    }

    /** Where the boss marker in the boss room was, once that room is built. */
    public void setBossSpawn(BlockPos pos) {
        bossSpawn = pos;
    }

    public void markBossSpawned() {
        bossSpawned = true;
    }

    /**
     * Uses one key on the boss door. Returns false if the door is already open. Check {@link #doorOpen()} afterwards
     * to see whether this key opened it.
     */
    public boolean useKey() {
        if (doorOpen()) {
            return false;
        }
        keysUsed++;
        return true;
    }

    /** Counts a hunt target for a room. It comes out of one of the room's spawners later. */
    public void addHunter(long room) {
        hunters.merge(room, 1, Integer::sum);
        unspawned.merge(room, 1, Integer::sum);
    }

    /** Hunt targets in a room that haven't spawned yet. */
    public int unspawnedHunters(long room) {
        return unspawned.getOrDefault(room, 0);
    }

    /** Counts a hunt target coming out of a spawner. Returns false if the room has none left to spawn. */
    public boolean hunterSpawned(long room) {
        Integer left = unspawned.get(room);
        if (left == null || left <= 0) {
            return false;
        }
        if (left == 1) {
            unspawned.remove(room);
        } else {
            unspawned.put(room, left - 1);
        }
        return true;
    }

    /** Counts a hunt target killed. Returns how many are left in its room, or empty if the room isn't tracked. */
    public Optional<Integer> hunterKilled(long room) {
        Integer left = hunters.get(room);
        if (left == null) {
            return Optional.empty();
        }
        int now = Math.max(0, left - 1);
        if (now == 0) {
            hunters.remove(room);
        } else {
            hunters.put(room, now);
        }
        return Optional.of(now);
    }

    public int huntersLeft(long room) {
        return hunters.getOrDefault(room, 0);
    }

    /** Relic Shards a collection room's altar needs in a dungeon of this tier: 4 at tier 1, up to 7 at tier 4. */
    public static int relicsFor(int tier) {
        return 3 + tier;
    }

    /** Gives a room a Relic Altar, needing {@link #relicsFor} shards. A room only gets one, however many altars it has. */
    public void addAltar(long room) {
        relics.putIfAbsent(room, relicsFor(tier));
    }

    /** Relic Shards a room's altar still needs (0 if it has none, or it's done). */
    public int relicsLeft(long room) {
        return relics.getOrDefault(room, 0);
    }

    /**
     * Offers up to {@code count} Relic Shards to a room's altar. Returns how many it took (never more than it still
     * needs); check {@link #relicsLeft} afterwards to see whether that finished it.
     */
    public int offerRelics(long room, int count) {
        int left = relicsLeft(room);
        int taken = Math.min(left, Math.max(0, count));
        if (left - taken <= 0) {
            relics.remove(room);
        } else {
            relics.put(room, left - taken);
        }
        return taken;
    }

    /** Gives a room a wave defense challenge, started from a War Drum. A room only gets one, however many drums it has. */
    public void addWaveRoom(long room, BlockPos drum) {
        waveRooms.putIfAbsent(room, new WaveRoom(drum, WaveRoom.wavesFor(tier)));
    }

    /** A room's wave defense, unless it has none or it's beaten. */
    public Optional<WaveRoom> waveRoom(long room) {
        return Optional.ofNullable(waveRooms.get(room));
    }

    public void removeWaveRoom(long room) {
        waveRooms.remove(room);
    }

    public Map<Long, WaveRoom> waveRooms() {
        return waveRooms;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tier", tier);
        tag.putString("boss", boss);
        tag.putString("faction", faction);
        tag.putInt("keys_needed", keysNeeded);
        tag.putInt("keys_used", keysUsed);
        tag.putBoolean("boss_spawned", bossSpawned);
        tag.put("door", new LongArrayTag(door.stream().mapToLong(BlockPos::asLong).toArray()));
        if (bossSpawn != null) {
            tag.putLong("boss_spawn", bossSpawn.asLong());
        }
        ListTag hunts = new ListTag();
        hunters.forEach((room, left) -> {
            CompoundTag hunt = new CompoundTag();
            hunt.putLong("room", room);
            hunt.putInt("left", left);
            hunts.add(hunt);
        });
        tag.put("hunters", hunts);
        ListTag waiting = new ListTag();
        unspawned.forEach((room, left) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("room", room);
            entry.putInt("left", left);
            waiting.add(entry);
        });
        tag.put("unspawned", waiting);
        ListTag altars = new ListTag();
        relics.forEach((room, left) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("room", room);
            entry.putInt("left", left);
            altars.add(entry);
        });
        tag.put("relics", altars);
        ListTag waves = new ListTag();
        waveRooms.forEach((room, wave) -> {
            CompoundTag entry = wave.save();
            entry.putLong("room", room);
            waves.add(entry);
        });
        tag.put("wave_rooms", waves);
        return tag;
    }

    public static DungeonProgress load(CompoundTag tag) {
        List<BlockPos> door = new ArrayList<>();
        for (long pos : tag.getLongArray("door")) {
            door.add(BlockPos.of(pos));
        }
        String boss = tag.contains("boss") ? tag.getString("boss") : DungeonBosses.BARROW_KING;
        DungeonProgress progress = new DungeonProgress(tag.getInt("tier"), boss, tag.getInt("keys_needed"), door,
                tag.contains("boss_spawn") ? BlockPos.of(tag.getLong("boss_spawn")) : null);
        progress.keysUsed = tag.getInt("keys_used");
        if (tag.contains("faction")) {
            progress.faction = DungeonFactions.current(tag.getString("faction"));
        }
        progress.bossSpawned = tag.getBoolean("boss_spawned");
        for (Tag huntTag : tag.getList("hunters", Tag.TAG_COMPOUND)) {
            CompoundTag hunt = (CompoundTag) huntTag;
            progress.hunters.put(hunt.getLong("room"), hunt.getInt("left"));
        }
        for (Tag entryTag : tag.getList("unspawned", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            progress.unspawned.put(entry.getLong("room"), entry.getInt("left"));
        }
        for (Tag entryTag : tag.getList("relics", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            progress.relics.put(entry.getLong("room"), entry.getInt("left"));
        }
        for (Tag entryTag : tag.getList("wave_rooms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            progress.waveRooms.put(entry.getLong("room"), WaveRoom.load(entry));
        }
        return progress;
    }
}
